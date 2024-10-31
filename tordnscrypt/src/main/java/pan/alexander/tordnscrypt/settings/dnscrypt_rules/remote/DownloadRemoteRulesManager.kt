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

package pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.TOR_BROWSER_USER_AGENT
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.web.HttpsConnectionManager
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

private const val READ_TIMEOUT_SEC = 30
private const val CONNECT_TIMEOUT_SEC = 30
private const val ATTEMPTS_TO_DOWNLOAD = 5
private const val TIME_TO_DOWNLOAD_MINUTES = 10
private const val ATTEMPTS_TO_DOWNLOAD_WITHIN_TIME = 20
private const val UPDATE_PROGRESS_INTERVAL_MSEC = 300

class DownloadRemoteRulesManager @Inject constructor(
    private val context: Context,
    private val pathVars: PathVars,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher,
    private val httpsConnectionManager: HttpsConnectionManager
) {

    private val localBroadcastManager by lazy {
        LocalBroadcastManager.getInstance(context)
    }

    suspend fun downloadRules(ruleName: String, url: String, fileName: String): File? =
        withContext(dispatcherIo) {
            var attempts = 0
            val startTime = System.currentTimeMillis()
            var outputFile: File? = null
            var error = ""
            try {
                val path = "${pathVars.getCacheDirPath(context)}/$fileName"
                val oldFile = File(path)
                if (oldFile.isFile) {
                    oldFile.delete()
                }
                outputFile = File(path).apply {
                    createNewFile()
                }
                do {
                    attempts++
                    try {
                        outputFile = tryDownload(ruleName, url, path)
                    } catch (e: IOException) {
                        error = e.message ?: ""
                        logw(
                            "DownloadRulesManager failed to download file $url, attempt $attempts",
                            e
                        )
                    }
                } while (
                    outputFile == null && isActive &&
                    (attempts < ATTEMPTS_TO_DOWNLOAD
                            || System.currentTimeMillis() - startTime < TIME_TO_DOWNLOAD_MINUTES * 60000
                            && attempts < ATTEMPTS_TO_DOWNLOAD_WITHIN_TIME)
                )
            } catch (e: Exception) {
                error = e.message ?: ""
                loge("DownloadRulesManager failed to download file $url", e)
            }
            if (outputFile != null && outputFile.length() > 0) {
                sendDownloadFinishedBroadcast(ruleName, url, outputFile.length())
                logi("Downloading $url was successful")
            } else {
                sendDownloadFailedBroadcast(ruleName, url, error)
            }
            return@withContext outputFile
        }

    private suspend fun tryDownload(ruleName: String, url: String, filePath: String): File? =
        withContext(dispatcherIo) {
            logi("Downloading DNSCrypt rules $url")

            var range: Long = 0
            val file = File(filePath)
            if (file.isFile) {
                range = file.length()
            } else {
                file.createNewFile()
            }

            val connection = httpsConnectionManager.getHttpsUrlConnection(url).apply {
                connectTimeout = CONNECT_TIMEOUT_SEC * 1000
                readTimeout = READ_TIMEOUT_SEC * 1000
                setRequestProperty("User-Agent", TOR_BROWSER_USER_AGENT)
                if (range != 0L) {
                    setRequestProperty("Range", "bytes=$range-")
                }
            }
            val fileLength = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connection.contentLengthLong + range
            } else {
                connection.getContentLength() + range
            }
            connection.inputStream.buffered().use { input ->
                val data = ByteArray(1024)
                file.outputStream().use { output ->
                    var time = System.currentTimeMillis()
                    var count = input.read(data)
                    while (count != -1 && isActive) {
                        range += count
                        val percent = (range * 100 / fileLength).toInt()
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - time > UPDATE_PROGRESS_INTERVAL_MSEC) {
                            time = currentTime
                            sendUpdateProgressBroadcast(ruleName, url, range, percent)
                        }
                        output.write(data, 0, count)
                        count = input.read(data)
                    }
                }
            }
            connection.disconnect()
            return@withContext if (isActive) {
                file
            } else {
                file.delete()
                null
            }
        }

    private fun sendUpdateProgressBroadcast(
        name: String,
        url: String,
        size: Long,
        progress: Int
    ) {
        val intent = Intent(DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_ACTION).apply {
            putExtra(
                DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_DATA,
                DnsRulesDownloadProgress.DownloadProgress(name, url, size, progress)
            )
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun sendDownloadFinishedBroadcast(
        name: String,
        url: String,
        size: Long
    ) {
        val intent = Intent(DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_ACTION).apply {
            putExtra(
                DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_DATA,
                DnsRulesDownloadProgress.DownloadFinished(name, url, size)
            )
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun sendDownloadFailedBroadcast(
        name: String,
        url: String,
        error: String
    ) {
        val intent = Intent(DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_ACTION).apply {
            putExtra(
                DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_DATA,
                DnsRulesDownloadProgress.DownloadFailure(name, url, error)
            )
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    companion object {
        const val DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_ACTION =
            "pan.alexander.tordnscrypt.DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_ACTION"
        const val DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_DATA =
            "pan.alexander.tordnscrypt.DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_DATA"
    }
}
