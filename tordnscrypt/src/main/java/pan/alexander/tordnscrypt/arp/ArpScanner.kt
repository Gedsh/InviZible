package pan.alexander.tordnscrypt.arp
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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.jrummyapps.android.shell.Shell
import pan.alexander.tordnscrypt.AUX_CHANNEL_ID
import pan.alexander.tordnscrypt.MainActivity
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.CachedExecutor
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.vpn.Util
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.util.regex.Pattern

const val mitmAttackWarning = "pan.alexander.tordnscrypt.arp.mitm_attack_warning"
private const val arpFilePath = "/proc/net/arp"
private const val commandArp = "ip neighbour show"
private const val commandRuleShow = "ip rule"
private const val commandRouteShow = "ip route show table %s"
private const val ARP_NOTIFICATION_ID = 110
private const val DHCP_NOTIFICATION_ID = 111
private const val ARP_COMMAND = 100
private const val RULE_COMMAND = 200
private const val ROUTE_SHOW = 300
private val macPattern = Pattern.compile("([0-9a-fA-F]{2}[:]){5}([0-9a-fA-F]{2})")
private val ethTablePattern = Pattern.compile("eth\\d lookup (\\w+)")
private val defaultGatewayPattern = Pattern.compile("default via (([0-9*]{1,3}\\.){3}[0-9*]{1,3})")

class ArpScanner private constructor(
    context: Context,
    handler: Handler?
) : Shell.OnCommandResultListener {

    private var handler = handler
        set(value) {
            if (handler != null) field = value
        }

    private var arpTableAccessible: Boolean? = null

    @Volatile
    private var session: Shell.Interactive? = null

    @Volatile
    private var scheduledExecutorService: ScheduledExecutorService? = null
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var notSupportedCounter = 10
    private var notSupportedCounterFreeze = false
    private var sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val reentrantLock = ReentrantLock()

    @Volatile
    private var connectionAvailable = false

    @Volatile
    private var cellularActive = false

    @Volatile
    private var wifiActive = false

    @Volatile
    private var ethernetActive = false

    @Volatile
    private var ethernetTable = ""

    @Volatile
    private var defaultGateway = ""

    @Volatile
    private var savedDefaultGateway = ""

    @Volatile
    private var gatewayMac = ""

    @Volatile
    private var savedGatewayMac = ""

    @Volatile
    private var stopping = false

    private var paused = false

    companion object INSTANCE {
        @Volatile
        var arpAttackDetected = false
            private set

        @Volatile
        var dhcpGatewayAttackDetected = false
            private set

        @Volatile
        private var arpScanner: ArpScanner? = null

        fun getInstance(context: Context, handler: Handler?): ArpScanner {
            if (arpScanner == null) {
                synchronized(ArpScanner::class.java) {
                    if (arpScanner == null) {
                        arpScanner = ArpScanner(context, handler)
                    }
                }
            }

            return arpScanner ?: ArpScanner(context, handler)
        }
    }

    fun start(context: Context) {

        if (!sharedPreferences.getBoolean("pref_common_arp_spoofing_detection", false)) {
            return
        }

        cellularActive = Util.isCellularActive(context)
        wifiActive = Util.isWifiActive(context)
        ethernetActive = Util.isEthernetActive(context)

        if (!wifiActive && !ethernetActive && (cellularActive || !connectionAvailable)) {
            return
        }

        if (scheduledExecutorService == null || scheduledExecutorService?.isShutdown == true) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        } else {
            return
        }

        pause(context, false, resetInternalValues = true)

        Log.i(LOG_TAG, "Start ArpScanner")

        scheduledExecutorService?.scheduleWithFixedDelay({

            if (!reentrantLock.tryLock(5, TimeUnit.SECONDS)) {
                TimeUnit.SECONDS.sleep(1)
                return@scheduleWithFixedDelay
            }

            run label@{
                try {

                    if (stopping) {

                        session?.close()

                        if (defaultGateway.isNotEmpty()) {
                            resetInternalValues()
                        }

                        scheduledExecutorService?.let {
                            if (!it.isShutdown) {
                                Log.i(LOG_TAG, "ArpScanner Stopped")
                                it.shutdownNow()
                            }
                        }

                        return@label
                    }

                    if (paused) {
                        return@label
                    }

                    if (arpTableAccessible == null) {
                        arpTableAccessible = isArpTableAccessible()
                    }

                    if (wifiActive) {
                        setDefaultWiFiGateway(context)
                    } else if (ethernetActive) {
                        requestRuleTable()
                    } else if (!cellularActive && connectionAvailable) {
                        setDefaultWiFiGateway(context)
                    }

                    if (savedDefaultGateway.isNotEmpty() && defaultGateway.isNotEmpty()) {

                        if (savedDefaultGateway != defaultGateway) {
                            Log.e(LOG_TAG, "DHCPAttackDetected defaultGateway changed")
                            Log.i(
                                LOG_TAG,
                                "Upstream Network Saved default Gateway:$savedDefaultGateway"
                            )
                            Log.i(
                                LOG_TAG,
                                "Upstream Network Current default Gateway:$defaultGateway"
                            )

                            if (!dhcpGatewayAttackDetected) {
                                sendNotification(
                                    context,
                                    context.getString(R.string.ask_force_close_title),
                                    context.getString(R.string.notification_rogue_dhcp),
                                    DHCP_NOTIFICATION_ID
                                )
                                makeToast(context, R.string.notification_rogue_dhcp)
                                updateMainActivityIcons(context)
                                reloadIptablesWithRootMode(context)
                            }

                            dhcpGatewayAttackDetected = true

                            return@label
                        } else if (dhcpGatewayAttackDetected) {
                            dhcpGatewayAttackDetected = false
                            updateMainActivityIcons(context)
                            reloadIptablesWithRootMode(context)
                        }
                    }

                    setGatewayMac()

                    if (savedGatewayMac.isNotEmpty() && gatewayMac.isNotEmpty()) {

                        if (gatewayMac != savedGatewayMac) {
                            Log.e(LOG_TAG, "ArpAttackDetected")
                            Log.i(
                                LOG_TAG,
                                "Upstream Network Saved default Gateway:$savedDefaultGateway MAC:${savedGatewayMac}"
                            )
                            Log.i(
                                LOG_TAG,
                                "Upstream Network Current default Gateway:$defaultGateway MAC:${gatewayMac}"
                            )


                            if (!arpAttackDetected) {
                                sendNotification(
                                    context,
                                    context.getString(R.string.ask_force_close_title),
                                    context.getString(R.string.notification_arp_spoofing),
                                    ARP_NOTIFICATION_ID
                                )
                                makeToast(context, R.string.notification_arp_spoofing)
                                updateMainActivityIcons(context)
                                reloadIptablesWithRootMode(context)
                            }

                            arpAttackDetected = true

                        } else if (arpAttackDetected) {
                            arpAttackDetected = false
                            updateMainActivityIcons(context)
                            reloadIptablesWithRootMode(context)
                        }
                    }

                    if (notSupportedCounter == 0) {
                        makeToast(context, R.string.toast_arp_detection_not_supported)
                        stop(context)
                    }

                } catch (e: Exception) {
                    if (defaultGateway.isNotEmpty()) {
                        resetInternalValues()
                    }
                    Log.w(
                        LOG_TAG,
                        "ArpScanner executor exception! ${e.message}\n${e.cause}\n${e.stackTrace}"
                    )
                }
            }

            if (reentrantLock.isHeldByCurrentThread && reentrantLock.isLocked) {
                reentrantLock.unlock()
            }

        }, 1, 10, TimeUnit.SECONDS)

        if (!Util.isConnected(context) && !connectionAvailable) {
            pause(context, true, resetInternalValues = true)
        }
    }

    fun reset(context: Context, connectionAvailable: Boolean) {

        val attackDetected = arpAttackDetected || dhcpGatewayAttackDetected

        this.connectionAvailable = connectionAvailable

        if (!sharedPreferences.getBoolean(
                "pref_common_arp_spoofing_detection",
                false
            ) && !attackDetected
        ) {
            return
        }

        cellularActive = Util.isCellularActive(context)
        wifiActive = Util.isWifiActive(context)
        ethernetActive = Util.isEthernetActive(context)

        if (connectionAvailable && (wifiActive || ethernetActive || !cellularActive)) {
            if (scheduledExecutorService?.isShutdown == false) {
                pause(context, false, resetInternalValues = false)

                if (!attackDetected) {
                    resetInternalValues()
                }

                Log.i(LOG_TAG, "ArpScanner reset due to connectivity changed")
            } else {
                start(context)
            }
        } else {
            pause(context, true, resetInternalValues = true)
        }
    }

    private fun resetInternalValues() {

        CachedExecutor.getExecutorService().submit {

            try {

                reentrantLock.lockInterruptibly()

                arpAttackDetected = false
                dhcpGatewayAttackDetected = false

                defaultGateway = ""
                savedDefaultGateway = ""

                gatewayMac = ""
                savedGatewayMac = ""

                ethernetTable = ""

            } catch (e: Exception) {
                Log.w(
                    LOG_TAG,
                    "ArpScanner resetInternalValues exception ${e.message}\n${e.cause}\n${e.stackTrace}"
                )
            } finally {
                if (reentrantLock.isLocked && reentrantLock.isHeldByCurrentThread) {
                    reentrantLock.unlock()
                }
            }
        }

    }

    private fun pause(context: Context, makePause: Boolean, resetInternalValues: Boolean) {
        val attackDetected = arpAttackDetected || dhcpGatewayAttackDetected

        paused = makePause

        if (resetInternalValues) {
            resetInternalValues()
        }

        if (!sharedPreferences.getBoolean(
                "pref_common_arp_spoofing_detection",
                false
            ) && !attackDetected
        ) {
            return
        }

        if (paused) {
            Log.i(LOG_TAG, "ArpScanner is paused")
        } else {
            Log.i(LOG_TAG, "ArpScanner is active")
        }

        if (attackDetected) {
            updateMainActivityIcons(context)
            reloadIptablesWithRootMode(context)
        }
    }

    fun stop(context: Context) {

        try {
            reentrantLock.lockInterruptibly()

            stopping = true

            cellularActive = false
            wifiActive = false
            ethernetActive = false

            val updateIcons = arpAttackDetected || dhcpGatewayAttackDetected

            resetInternalValues()

            if (updateIcons) {
                updateMainActivityIcons(context)
            }

            arpScanner = null
            Log.i(LOG_TAG, "Stopping ArpScanner")
        } catch (e: java.lang.Exception) {
            Log.w(LOG_TAG, "ArpScanner stop exception ${e.message}\n${e.cause}\n${e.stackTrace}")
        }

        if (reentrantLock.isLocked && reentrantLock.isHeldByCurrentThread) {
            reentrantLock.unlock()
        }

    }

    private fun setDefaultWiFiGateway(context: Context) {
        val dhcp = wifiManager.dhcpInfo ?: return
        var ipAddress = dhcp.gateway
        ipAddress =
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) Integer.reverseBytes(ipAddress) else ipAddress
        val ipAddressByte = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
        try {
            val myAddr = InetAddress.getByAddress(ipAddressByte)

            defaultGateway = myAddr.hostAddress.trim()

            if (savedDefaultGateway.isEmpty()) {
                Log.i(LOG_TAG, "ArpScanner defaultGateway is $defaultGateway")
                savedDefaultGateway = defaultGateway
            }
        } catch (e: Exception) {

            if (connectionAvailable && !cellularActive && !wifiActive && !ethernetActive) {
                pause(context, true, resetInternalValues = true)
            } else {

                if (defaultGateway.isNotEmpty()) {
                    resetInternalValues()
                }

                Log.e(LOG_TAG, "ArpScanner error getting default gateway ${e.message} ${e.cause}")
            }
        }
    }

    private fun setGatewayMac() {
        if (arpTableAccessible == null || defaultGateway.isEmpty()) {
            return
        }

        if (arpTableAccessible as Boolean) {
            getArpLineFromFile()
        } else {
            getArpLineFromShell()
        }
    }

    private fun getArpLineFromFile() {

        if (defaultGateway.isEmpty()) {
            return
        }

        try {
            BufferedReader(InputStreamReader(File(arpFilePath).inputStream())).use { bufferedReader ->
                var line = bufferedReader.readLine()
                while (line != null) {
                    if (line.contains("$defaultGateway ")) {

                        gatewayMac = getMacFromLine(line)

                        if (savedGatewayMac.isEmpty() && gatewayMac.isNotBlank()) {
                            val macStared = gatewayMac.substring(0..gatewayMac.length - 7)
                                .replace(Regex("\\w+?"), "*")
                                .plus(gatewayMac.substring(gatewayMac.length - 6))
                            Log.i(LOG_TAG, "ArpScanner gatewayMac is $macStared")
                            savedGatewayMac = gatewayMac
                        }
                        break
                    } else {
                        line = bufferedReader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ArpScanner getArpStringFromFile exception ${e.message} ${e.cause}")
        }
    }

    private fun getArpLineFromShell() {

        try {

            if (session?.isRunning != true) {
                session = openSession()
            }

            session?.addCommand(commandArp, ARP_COMMAND, this)

        } catch (e: Exception) {
            Log.e(LOG_TAG, "ArpScanner getArpStringFromShell exception ${e.message} ${e.cause}")
        }
    }

    private fun openSession(): Shell.Interactive? {
        return Shell.Builder()
            .useSH()
            .setWatchdogTimeout(5)
            .open(this)
    }

    override fun onCommandResult(commandCode: Int, exitCode: Int, output: MutableList<String>?) {
        if (output == null) {
            return
        }

        if (exitCode != 0) {
            Log.w(LOG_TAG, "ArpScanner onCommandResult exitCode:$exitCode commandCode:$commandCode")
        }

        when (commandCode) {
            ARP_COMMAND -> setGatewayMacFromShell(output)
            RULE_COMMAND -> requestDefaultEthernetGateway(output)
            ROUTE_SHOW -> setDefaultEthernetGateway(output)
        }

    }

    private fun setGatewayMacFromShell(lines: MutableList<String>) {
        if (defaultGateway.isEmpty()) {
            return
        }

        var containsNotEmptyLines = false

        for (line: String in lines) {
            if (line.trim().isNotEmpty() && !line.contains("-BOC-")) {
                containsNotEmptyLines = true
            }

            if (line.contains("$defaultGateway ")) {

                gatewayMac = getMacFromLine(line)

                if (savedGatewayMac.isEmpty() && gatewayMac.isNotBlank()) {
                    val macStared = gatewayMac.substring(0..gatewayMac.length - 7)
                        .replace(Regex("\\w+?"), "*")
                        .plus(gatewayMac.substring(gatewayMac.length - 6))
                    Log.i(LOG_TAG, "ArpScanner gatewayMac is $macStared")
                    savedGatewayMac = gatewayMac
                }

                notSupportedCounterFreeze = true

                break
            } else if (getMacFromLine(line).isNotEmpty()) {
                notSupportedCounterFreeze = true
            }
        }

        if (containsNotEmptyLines && !notSupportedCounterFreeze) {
            notSupportedCounter--
        }
    }

    private fun requestRuleTable() {

        if (ethernetTable.isEmpty()) {
            try {

                if (session?.isRunning != true) {
                    session = openSession()
                }

                Log.i(LOG_TAG, "ArpScanner requestRuleTable")

                session?.addCommand(commandRuleShow, RULE_COMMAND, this)

            } catch (e: Exception) {
                Log.e(LOG_TAG, "ArpScanner requestRuleTable exception ${e.message} ${e.cause}")
            }
        } else {
            requestDefaultEthernetGateway()
        }
    }

    private fun requestDefaultEthernetGateway(lines: MutableList<String>) {

        try {
            for (line: String in lines) {
                val matcher = ethTablePattern.matcher(line)

                if (matcher.find()) {

                    ethernetTable = matcher.group(1) ?: ""

                    Log.i(LOG_TAG, "ArpScanner ethTable is $ethernetTable")

                    if (session?.isRunning != true) {
                        session = openSession()
                    }

                    if (ethernetTable.isNotEmpty()) {
                        session?.addCommand(
                            String.format(commandRouteShow, ethernetTable),
                            ROUTE_SHOW,
                            this
                        )
                    }

                    break
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e(
                LOG_TAG,
                "ArpScanner requestDefaultEthernetGateway(lines) exception ${e.message} ${e.cause}"
            )
        }
    }

    private fun requestDefaultEthernetGateway() {
        try {
            if (session?.isRunning != true) {
                session = openSession()
            }

            if (ethernetTable.isNotEmpty()) {
                session?.addCommand(
                    String.format(commandRouteShow, ethernetTable),
                    ROUTE_SHOW,
                    this
                )
            }
        } catch (e: java.lang.Exception) {
            Log.e(
                LOG_TAG,
                "ArpScanner requestDefaultEthernetGateway exception ${e.message} ${e.cause}"
            )
        }
    }

    private fun setDefaultEthernetGateway(lines: MutableList<String>) {

        try {
            for (line: String in lines) {
                val matcher = defaultGatewayPattern.matcher(line)

                if (matcher.find()) {

                    matcher.group(1)?.let { defaultGateway = it }

                    if (savedDefaultGateway.isEmpty()) {
                        Log.i(LOG_TAG, "ArpScanner defaultGateway is $defaultGateway")
                        savedDefaultGateway = defaultGateway
                    }

                    break
                }
            }

        } catch (e: Exception) {
            if (defaultGateway.isNotEmpty()) {
                resetInternalValues()
            }

            Log.e(LOG_TAG, "ArpScanner error getting default gateway ${e.message} ${e.cause}")
        }
    }

    private fun getMacFromLine(line: String): String {
        val matcher = macPattern.matcher(line)

        if (matcher.find()) {
            return matcher.group().trim()
        }

        return ""
    }

    private fun isArpTableAccessible(): Boolean {
        var result = false

        val arp = File(arpFilePath)

        try {
            if (arp.isFile && arp.canRead()) {
                result = true
            }
        } catch (e: Exception) {
        }

        return result
    }

    private fun sendNotification(
        context: Context,
        title: String,
        text: String,
        NOTIFICATION_ID: Int
    ) {

        //These three lines makes Notification to open main activity after clicking on it
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.action = Intent.ACTION_MAIN
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        notificationIntent.putExtra(mitmAttackWarning, true)
        val contentIntent = PendingIntent.getActivity(
            context.applicationContext,
            111,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        var iconResource: Int = context.resources.getIdentifier(
            "ic_arp_attack_notification",
            "drawable",
            context.packageName
        )
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_lock_power_off
        }
        val builder = NotificationCompat.Builder(context, AUX_CHANNEL_ID)
        @Suppress("DEPRECATION")
        builder.setContentIntent(contentIntent)
            .setOngoing(false) //Can be swiped out
            .setSmallIcon(iconResource)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_arp_attack_notification
                )
            )   // большая картинка
            .setContentTitle(title) //Заголовок
            .setContentText(text) // Текст уведомления
            .setPriority(Notification.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(1000))
            .setChannelId(AUX_CHANNEL_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_ALARM)
        }

        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun updateMainActivityIcons(context: Context) {
        handler?.post {
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(mitmAttackWarning))
        }
    }

    private fun makeToast(context: Context, message: Int) {
        handler?.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    private fun reloadIptablesWithRootMode(context: Context) {
        if (!sharedPreferences.getBoolean("pref_common_arp_block_internet", false)) {
            return
        }

        val modulesStatus = ModulesStatus.getInstance()
        if (modulesStatus.mode == OperationMode.ROOT_MODE) {
            modulesStatus.setIptablesRulesUpdateRequested(context, true)
        }
    }

}