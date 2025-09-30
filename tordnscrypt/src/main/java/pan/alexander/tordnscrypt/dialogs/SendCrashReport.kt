/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.dialogs

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.help.Utils
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor
import pan.alexander.tordnscrypt.utils.integrity.Verifier
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALWAYS_SHOW_HELP_MESSAGES
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CRASH_REPORT
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import javax.inject.Inject

@Keep
class SendCrashReport : ExtendedDialogFragment() {

    @Inject
    lateinit var preferenceRepository: dagger.Lazy<PreferenceRepository>
    @Inject
    lateinit var pathVars: dagger.Lazy<PathVars>
    @Inject
    lateinit var executor: CoroutineExecutor
    @Inject
    lateinit var verifier: dagger.Lazy<Verifier>

    private var activityManager: ActivityManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        App.instance.daggerComponent.inject(this)
        super.onCreate(savedInstanceState)

        activityManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    override fun assignBuilder(): AlertDialog.Builder? {
        if (activity == null || requireActivity().isFinishing) {
            return null
        }

        val builder = AlertDialog.Builder(requireActivity())
        builder.setMessage(getString(R.string.dialog_send_crash_report))
                .setTitle(R.string.helper_dialog_title)
                .setPositiveButton(R.string.ok) { _, _ ->
                    if (activity != null && activity?.isFinishing == false) {
                        executor.submit("SendCrashReport assignBuilder") {

                            val ctx = activity as Context

                            try {
                                if (activity != null && activity?.isFinishing == false) {

                                    val logsDirPath = createLogsDir(ctx)

                                    val memoryInfo = ActivityManager.MemoryInfo().also {
                                        activityManager?.getMemoryInfo(it)
                                    }

                                    val info = Utils.collectInfo(
                                        verifier.get().appSignature,
                                        pathVars.get().appVersion,
                                        pathVars.get().appProcVersion,
                                        Utils.getAppVersion(ctx, pathVars.get(), preferenceRepository.get()),
                                        memoryInfo
                                    )

                                    if (saveLogCat(logsDirPath)) {
                                        sendCrashEmail(ctx, info, File("$logsDirPath/logcat.log"))
                                    }

                                }
                            } catch (exception: Exception) {
                                loge("SendCrashReport", exception)
                            }


                        }
                    }
                }
                .setNeutralButton(R.string.cancel) { _, _ ->
                    dismiss()
                }
                .setNegativeButton(R.string.dont_show) { _, _ ->
                    if (activity != null) {
                        preferenceRepository.get().setBoolPreference("never_send_crash_reports", true)
                    }

                    dismiss()
                }
        return builder
    }

    private fun createLogsDir(context: Context): String? {

        val cacheDir: String
        try {
            cacheDir = context.cacheDir?.canonicalPath
                ?: (pathVars.get().appDataDir + "/cache")
        } catch (e: Exception) {
            logw("SendCrashReport cannot get cache dir", e)
            return null
        }

        val logDirPath = "$cacheDir/logs"
        val dir = File(logDirPath)
        if (!dir.isDirectory && !dir.mkdirs()) {
            loge("SendCrashReport cannot create logs dir")
            return null
        }

        return logDirPath
    }

    private fun saveLogCat(logsDirPath: String?): Boolean {

        if (logsDirPath == null) {
            return false
        }

        val process = Runtime.getRuntime().exec("logcat -d")
        val log = StringBuilder()
        BufferedReader(InputStreamReader(process.inputStream)).use { bufferedReader ->
            var line = bufferedReader.readLine()
            while (line != null) {
                log.append(line)
                log.append("\n")
                line = bufferedReader.readLine()
            }
        }

        FileWriter("$logsDirPath/logcat.log").use { out -> out.write(log.toString()) }

        process.destroy()

        return File("$logsDirPath/logcat.log").isFile
    }

    private fun sendCrashEmail(context: Context, info: String, logCat: File) {

        val text = preferenceRepository.get().getStringPreference(CRASH_REPORT)
        if (text.isNotEmpty()) {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", logCat)
            if (uri != null) {
                Utils.sendMail(context, info + "\n\n" + text, uri)
            }
            preferenceRepository.get().setStringPreference(CRASH_REPORT, "")
        }
    }

    companion object {
        fun getCrashReportDialog(context: Context): SendCrashReport? {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val preferenceRepository = App.instance.daggerComponent.getPreferenceRepository()
            if (!preferenceRepository.get().getBoolPreference("never_send_crash_reports")
                    || sharedPreferences.getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false)) {
                return SendCrashReport()
            }
            return null
        }
    }
}