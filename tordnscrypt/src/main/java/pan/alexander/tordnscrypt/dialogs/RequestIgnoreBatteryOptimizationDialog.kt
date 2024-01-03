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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.dialogs

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import javax.inject.Inject

class RequestIgnoreBatteryOptimizationDialog : ExtendedDialogFragment() {

    @Inject
    lateinit var preferenceRepository: dagger.Lazy<PreferenceRepository>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        App.instance.daggerComponent.inject(this)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun assignBuilder(): AlertDialog.Builder? {

        val activity = activity
        if (activity == null || activity.isFinishing) {
            return null
        }

        val builder = AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme)

        builder.setTitle(R.string.helper_dialog_title)
        builder.setMessage(R.string.pref_common_notification_helper)

        builder.setPositiveButton(R.string.ok) { _, _ ->
            context?.let {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    try {
                        it.startActivity(this)
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "Requesting ignore battery optimization failed ${e.message}")
                    }
                }
            }
        }

        builder.setNeutralButton(R.string.dont_show) { _, _ ->
            preferenceRepository.get().setBoolPreference(
                PreferenceKeys.DO_NOT_SHOW_IGNORE_BATTERY_OPTIMIZATION_DIALOG, true
            )
        }

        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        return builder
    }

    companion object {
        @JvmStatic
        fun getInstance(
            context: Context,
            preferenceRepository: PreferenceRepository
        ): DialogFragment? {
            val pref = PreferenceManager.getDefaultSharedPreferences(context)
            val packageName = context.packageName
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || pm?.isIgnoringBatteryOptimizations(packageName) == true
                || (preferenceRepository.getBoolPreference(PreferenceKeys.DO_NOT_SHOW_IGNORE_BATTERY_OPTIMIZATION_DIALOG)
                        && !pref.getBoolean(PreferenceKeys.ALWAYS_SHOW_HELP_MESSAGES, false))
            ) {
                return null
            }
            return RequestIgnoreBatteryOptimizationDialog()
        }

        const val TAG = "RequestIgnoreBatteryOptimizationDialog"
    }
}
