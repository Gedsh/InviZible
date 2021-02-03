package pan.alexander.tordnscrypt.modules

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

var savedTitle = ""
var savedMessage = ""
var startTime = 0L

class UsageStatistic(private val context: Context) {

    var serviceNotification: ServiceNotification? = null

    private var timer: ScheduledExecutorService? = null
    private val modulesStatus = ModulesStatus.getInstance()

    private val uid = Process.myUid()

    private var scheduledFuture: ScheduledFuture<*>? = null
    private var updatePeriod = 0
    private var counter = 0

    private var savedMode = OperationMode.UNDEFINED
    private var startRX = 0L
    private var startTX = 0L
    private var savedTime = 0L
    private var savedRX = 0L
    private var savedTX = 0L

    init {
        initModulesLogsTimer()
        startTime = System.currentTimeMillis()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @JvmOverloads
    fun startUpdate(period: Int = 3) {

        if (period == updatePeriod) {
            return
        }

        updatePeriod = period

        initModulesLogsTimer()

        if (scheduledFuture != null && scheduledFuture?.isCancelled == false) {
            scheduledFuture?.cancel(false)
        }



        scheduledFuture = timer?.scheduleWithFixedDelay({

            try {

                val currentTime = System.currentTimeMillis()

                val title = getTitle()

                val message = getMessage(currentTime)

                if (title != savedTitle || message != savedMessage) {
                    serviceNotification?.updateNotification(title, message)
                    savedTitle = title
                    savedMessage = message
                }

                if (counter > 100) {
                    startUpdate(5)
                    counter = -1
                } else if (counter >= 0) {
                    counter++
                }
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "UsageStatistics exception " + exception.message + " " + exception.cause)
            }

        }, 1, period.toLong(), TimeUnit.SECONDS)
    }

    fun stopUpdate() {
        if (timer != null && timer?.isShutdown == false) {
            timer?.shutdown()
        }

        startRX = 0
        startTX = 0

        savedTitle = ""
        savedMessage = ""
    }

    @Synchronized
    fun getTitle(): String {
        var title = ""

        if (modulesStatus.torState == ModuleState.RUNNING) {
            title += "TOR"
        }

        if (modulesStatus.dnsCryptState == ModuleState.RUNNING) {
            title += " & DNSCRYPT"
        }

        if (modulesStatus.itpdState == ModuleState.RUNNING) {
            title += " & I2P"
        }

        if (title.isEmpty()) {
            title = context.getString(R.string.app_name)
        }

        return title.removePrefix(" & ")
    }

    @Synchronized
    fun getMessage(currentTime: Long): String {

        if (uid == -1) {
            return context.getString(R.string.notification_text)
        }

        val mode = modulesStatus.mode ?: return context.getString(R.string.notification_text)

        if (savedMode != mode) {
            savedMode = mode

            startRX = TrafficStats.getTotalRxBytes() - TrafficStats.getUidRxBytes(uid)
            startTX = TrafficStats.getTotalTxBytes() - TrafficStats.getUidTxBytes(uid)
        }

        val timePeriod = (currentTime - savedTime) / 1000L
        val currentRX = TrafficStats.getTotalRxBytes() - TrafficStats.getUidRxBytes(uid) - startRX
        val currentTX = TrafficStats.getTotalTxBytes() - TrafficStats.getUidTxBytes(uid) - startTX

        val message = "▼ ${getReadableSpeedString(currentRX - savedRX, timePeriod)} ${humanReadableByteCountBin(currentRX)}  " +
                "▲ ${getReadableSpeedString(currentTX - savedTX, timePeriod)} ${humanReadableByteCountBin(currentTX)}"

        savedRX = currentRX
        savedTX = currentTX
        savedTime = currentTime

        return message
    }

    fun isStatisticAllowed(): Boolean {
        return TrafficStats.getTotalRxBytes() != TrafficStats.UNSUPPORTED.toLong()
                && TrafficStats.getTotalTxBytes() != TrafficStats.UNSUPPORTED.toLong()
    }

    private fun initModulesLogsTimer() {
        if (timer == null || timer?.isShutdown == true) {
            timer = Executors.newSingleThreadScheduledExecutor()
        }
    }

    private fun getReadableSpeedString(fileSizeInBytes: Long, timePeriod: Long): String {

        var i = 0
        val byteUnits = arrayListOf("b/s", " kb/s", " Mb/s", " Tb/s", " Pb/s", " Eb/s", " Zb/s", " Yb/s")

        if (timePeriod == 0L || fileSizeInBytes < 0) {
            return "0 ${byteUnits[i]}"
        }

        var fileSize = fileSizeInBytes.toDouble() * 8.0

        while (fileSize / timePeriod > 1000.0) {
            fileSize /= 1000.0
            i++
        }

        return "${(fileSize / timePeriod).roundToInt()} ${byteUnits[i]}"
    }

    private fun humanReadableByteCountBin(bytes: Long): String {
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else abs(bytes)
        if (absB < 1024) {
            return "$bytes B"
        }
        var value = absB
        val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= java.lang.Long.signum(bytes).toLong()
        return String.format("%.1f %ciB", value / 1024.0, ci.current())
    }

}