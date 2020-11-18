package pan.alexander.tordnscrypt.settings.firewall

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

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import pan.alexander.tordnscrypt.FIREWALL_CHANNEL_ID
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.SettingsActivity
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.PrefManager
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG

const val ALLOW_ACTION = "pan.alexander.tordnscrypt.ALLOW_APP_FOR_FIREWALL"
const val DENY_ACTION = "pan.alexander.tordnscrypt.DENY_APP_FOR_FIREWALL"
const val NOTIFICATION_ID = "pan.alexander.tordnscrypt.NOTIFICATION_ID"
const val EXTRA_UID = "pan.alexander.tordnscrypt.EXTRA_UID"

class FirewallNotification : BroadcastReceiver() {


    private val modulesStatus = ModulesStatus.getInstance()
    private var notificationStartId = 102130

    companion object {
        fun registerFirewallReceiver(context: Context): FirewallNotification {
            val firewallNotification = FirewallNotification()

            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
            intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
            intentFilter.addAction(ALLOW_ACTION)
            intentFilter.addAction(DENY_ACTION)
            intentFilter.addDataScheme("package")
            context.registerReceiver(firewallNotification, intentFilter)
            return firewallNotification
        }

        fun unregisterFirewallReceiver(context: Context, receiver: FirewallNotification?) {
            receiver?.let { context.unregisterReceiver(it) }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {

        if (context == null || !PrefManager(context).getBoolPref("FirewallEnabled")) {
            return
        }
        val action = intent?.action ?: return

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> packageAdded(context, intent)
            Intent.ACTION_PACKAGE_REMOVED -> packageRemoved(context, intent)
            ALLOW_ACTION -> addFirewallRule(context, intent.getIntExtra(NOTIFICATION_ID, 0),
                    intent.getIntExtra(EXTRA_UID, 0))
            DENY_ACTION -> closeNotification(context, intent.getIntExtra(NOTIFICATION_ID, 0))
        }
    }

    private fun packageAdded(context: Context?, intent: Intent) {

        Log.i(LOG_TAG, "FirewallNotification packageAdded received intent $intent")

        val uid = intent.getIntExtra(Intent.EXTRA_UID, 0)
        val packageManager = context?.packageManager

        if (uid == 0 || packageManager == null
                || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            return
        }

        val packages = packageManager.getPackagesForUid(uid)

        if (packages.isNullOrEmpty()) {
            return
        }

        var label = ""
        var useInternet = false
        var system = true


        try {
            val applicationInfo = packageManager.getPackageInfo(packages[0], 0).applicationInfo
            system = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val pInfo: PackageInfo = packageManager.getPackageInfo(applicationInfo.packageName, PackageManager.GET_PERMISSIONS)
            if (pInfo.requestedPermissions != null) {
                for (permInfo in pInfo.requestedPermissions) {
                    if (permInfo == Manifest.permission.INTERNET) {
                        useInternet = true
                        break
                    }
                }
            }
            label = packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            useInternet = true
            Log.e(LOG_TAG, "FirewallNotification packageAdded exception  ${e.message}\n${e.cause}")
        }

        if (label.isBlank()) {
            try {
                label = packageManager.getNameForUid(uid) ?: ""
            } catch (e: Exception){
                Log.e(LOG_TAG, "FirewallNotification packageAdded exception  ${e.message}\n${e.cause}")
            }
        }

        if (system || !useInternet || label.isBlank()) {
            return
        }

        val title = try {
            String.format(context.getString(R.string.firewall_notification_allow_app_title), label)
        } catch (ignored: Exception) {
            label
        }

        val message = context.getString(R.string.firewall_notification_allow_app)

        sendNotification(context, uid, notificationStartId++, message,title, message)

        val appsNewlyInstalled = PrefManager(context).getSetStrPref(APPS_NEWLY_INSTALLED)
        PrefManager(context).setSetStrPref(APPS_NEWLY_INSTALLED, appsNewlyInstalled.apply { add(uid.toString()) })

        Log.i(LOG_TAG, "FirewallNotification package added UID $uid")
    }

    private fun packageRemoved(context: Context?, intent: Intent) {
        Log.i(LOG_TAG, "FirewallNotification packageRemoved received intent $intent")

        val uid = intent.getIntExtra(Intent.EXTRA_UID, 0)

        if (uid > 0 && intent.getBooleanExtra(Intent.EXTRA_DATA_REMOVED, false)) {
            val appsAllowLan = PrefManager(context).getSetStrPref(APPS_ALLOW_LAN_PREF)
            val appsAllowWifi = PrefManager(context).getSetStrPref(APPS_ALLOW_WIFI_PREF)
            val appsAllowGsm = PrefManager(context).getSetStrPref(APPS_ALLOW_GSM_PREF)
            val appsAllowRoaming = PrefManager(context).getSetStrPref(APPS_ALLOW_ROAMING)
            val appsAllowVpn = PrefManager(context).getSetStrPref(APPS_ALLOW_VPN)


            PrefManager(context).setSetStrPref(APPS_ALLOW_LAN_PREF, appsAllowLan.apply { remove(uid.toString()) })
            PrefManager(context).setSetStrPref(APPS_ALLOW_WIFI_PREF, appsAllowWifi.apply { remove(uid.toString()) })
            PrefManager(context).setSetStrPref(APPS_ALLOW_GSM_PREF, appsAllowGsm.apply { remove(uid.toString()) })
            PrefManager(context).setSetStrPref(APPS_ALLOW_ROAMING, appsAllowRoaming.apply { remove(uid.toString()) })
            if (modulesStatus.isRootAvailable) {
                PrefManager(context).setSetStrPref(APPS_ALLOW_VPN, appsAllowVpn.apply { remove(uid.toString()) })
            }

            if (context != null) {
                modulesStatus.setIptablesRulesUpdateRequested(context, true)
            }

            Log.i(LOG_TAG, "FirewallNotification package removed UID $uid")
        }
    }

    private fun addFirewallRule(context: Context?, notificationId: Int, uid: Int) {
        if (uid > 0) {
            val appsAllowLan = PrefManager(context).getSetStrPref(APPS_ALLOW_LAN_PREF)
            val appsAllowWifi = PrefManager(context).getSetStrPref(APPS_ALLOW_WIFI_PREF)
            val appsAllowGsm = PrefManager(context).getSetStrPref(APPS_ALLOW_GSM_PREF)
            val appsAllowRoaming = PrefManager(context).getSetStrPref(APPS_ALLOW_ROAMING)
            val appsAllowVpn = PrefManager(context).getSetStrPref(APPS_ALLOW_VPN)


            PrefManager(context).setSetStrPref(APPS_ALLOW_LAN_PREF, appsAllowLan.apply { add(uid.toString()) })
            PrefManager(context).setSetStrPref(APPS_ALLOW_WIFI_PREF, appsAllowWifi.apply { add(uid.toString()) })
            PrefManager(context).setSetStrPref(APPS_ALLOW_GSM_PREF, appsAllowGsm.apply { add(uid.toString()) })
            PrefManager(context).setSetStrPref(APPS_ALLOW_ROAMING, appsAllowRoaming.apply { add(uid.toString()) })
            if (modulesStatus.isRootAvailable) {
                PrefManager(context).setSetStrPref(APPS_ALLOW_VPN, appsAllowVpn.apply { add(uid.toString()) })
            }

            if (context != null) {
                modulesStatus.setIptablesRulesUpdateRequested(context, true)
            }
        }

        if (notificationId > 0) {
            val notificationManager = context?.applicationContext?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.cancel(notificationId)
        }
    }

    private fun closeNotification(context: Context?, notificationId: Int) {
        if (notificationId > 0) {
            val notificationManager = context?.applicationContext?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.cancel(notificationId)
        }
    }

    private fun sendNotification(context: Context?, uid: Int, notificationId: Int, ticker: String, title: String, message: String) {

        if (context == null) {
            return
        }

        //These three lines makes Notification to open main activity after clicking on it
        val notificationIntent = Intent(context, SettingsActivity::class.java)
        notificationIntent.action = "firewall"
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val contentIntent = PendingIntent.getActivity(context, 1025, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val allowApp = Intent(context, FirewallNotification::class.java)
        allowApp.action = ALLOW_ACTION
        allowApp.putExtra(NOTIFICATION_ID, notificationId)
        allowApp.putExtra(EXTRA_UID, uid)
        val allowPendingIntent = PendingIntent.getBroadcast(context, notificationId, allowApp, PendingIntent.FLAG_UPDATE_CURRENT)

        val denyApp = Intent(context, FirewallNotification::class.java)
        denyApp.action = DENY_ACTION
        denyApp.putExtra(NOTIFICATION_ID, notificationId)
        val denyPendingIntent = PendingIntent.getBroadcast(context, notificationId, denyApp, PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(context, FIREWALL_CHANNEL_ID)
        builder.setContentIntent(contentIntent)
                .setOngoing(false) //Can be swiped out
                .setSmallIcon(R.drawable.ic_firewall)
                .setTicker(ticker)
                .setContentTitle(title) //Заголовок
                .setContentText(message) // Текст уведомления
                .setOnlyAlertOnce(true)
                //.setWhen(startTime)
                //.setUsesChronometer(true)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(300))
                .setChannelId(FIREWALL_CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(R.drawable.ic_done_white_24dp, context.getText(R.string.allow), allowPendingIntent)
                .addAction(R.drawable.ic_baseline_close_24, context.getText(R.string.deny), denyPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setCategory(Notification.CATEGORY_REMINDER)
        }

        val notification = builder.build()
        val notificationManager = context.applicationContext?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        notificationManager?.notify(notificationId, notification)
    }
}
