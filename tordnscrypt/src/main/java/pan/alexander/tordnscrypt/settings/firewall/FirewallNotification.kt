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

package pan.alexander.tordnscrypt.settings.firewall

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
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
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.settings.SettingsActivity
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.proxy.CLEARNET_APPS_FOR_PROXY
import pan.alexander.tordnscrypt.utils.Utils.areNotificationsNotAllowed
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*

const val ALLOW_ACTION = "pan.alexander.tordnscrypt.ALLOW_APP_FOR_FIREWALL"
const val DENY_ACTION = "pan.alexander.tordnscrypt.DENY_APP_FOR_FIREWALL"
const val NOTIFICATION_ID = "pan.alexander.tordnscrypt.NOTIFICATION_ID"
const val EXTRA_UID = "pan.alexander.tordnscrypt.EXTRA_UID"

private const val FIREWALL_CHANNEL_ID = "Firewall"

class FirewallNotification : BroadcastReceiver() {

    private val modulesStatus = ModulesStatus.getInstance()
    private val preferenceRepository = App.instance.daggerComponent.getPreferenceRepository()
    private var notificationStartId = 102130
    private var newAppsAreAllowed = false

    companion object {
        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        @JvmStatic
        fun registerFirewallReceiver(context: Context): FirewallNotification {
            val firewallNotification = FirewallNotification()

            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
            intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
            intentFilter.addAction(ALLOW_ACTION)
            intentFilter.addAction(DENY_ACTION)
            intentFilter.addDataScheme("package")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    firewallNotification,
                    intentFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(
                    firewallNotification,
                    intentFilter
                )
            }
            return firewallNotification
        }

        @JvmStatic
        fun unregisterFirewallReceiver(context: Context, receiver: FirewallNotification?) {
            receiver?.let { context.unregisterReceiver(it) }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {

        if (context == null || !preferenceRepository.get().getBoolPreference(FIREWALL_ENABLED)) {
            return
        }

        val notificationManager =
            context.applicationContext?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        newAppsAreAllowed = sharedPreferences.getBoolean(FIREWALL_NO_BLOCK_NEW_APP, false)

        val action = intent?.action ?: return

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> packageAdded(context, intent)
            Intent.ACTION_PACKAGE_REMOVED -> packageRemoved(context, intent)

            ALLOW_ACTION -> {
                if (!newAppsAreAllowed) {
                    addFirewallRule(context, intent.getIntExtra(EXTRA_UID, 0))
                }
                closeNotification(notificationManager, intent.getIntExtra(NOTIFICATION_ID, 0))
            }

            DENY_ACTION -> {
                if (newAppsAreAllowed) {
                    removeFirewallRule(context, intent.getIntExtra(EXTRA_UID, 0))
                }
                closeNotification(notificationManager, intent.getIntExtra(NOTIFICATION_ID, 0))
            }
        }
    }

    private fun packageAdded(context: Context?, intent: Intent) {

        logi("FirewallNotification packageAdded received intent $intent")

        val uid = intent.getIntExtra(Intent.EXTRA_UID, 0)
        val packageManager = context?.packageManager

        if (uid == 0 || packageManager == null
            || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        ) {
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
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packages[0],
                    PackageManager.PackageInfoFlags.of(0)
                ).applicationInfo
            } else {
                packageManager.getPackageInfo(packages[0], 0).applicationInfo
            }
            system = ((applicationInfo?.flags ?: 1) and ApplicationInfo.FLAG_SYSTEM) != 0
            val pInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    applicationInfo?.packageName ?: "Undefined package",
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                packageManager.getPackageInfo(
                    applicationInfo?.packageName ?: "Undefined package",
                    PackageManager.GET_PERMISSIONS
                )
            }
            if (pInfo.requestedPermissions != null) {
                for (permInfo in pInfo.requestedPermissions) {
                    if (permInfo == Manifest.permission.INTERNET) {
                        useInternet = true
                        break
                    }
                }
            }
            label = applicationInfo?.let {
                packageManager.getApplicationLabel(it).toString()
            } ?: "Undefined label"
        } catch (e: Exception) {
            useInternet = true
            loge("FirewallNotification packageAdded", e)
        }

        if (label.isBlank()) {
            try {
                label = packageManager.getNameForUid(uid) ?: ""
            } catch (e: Exception) {
                loge("FirewallNotification packageAdded", e)
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

        sendNotification(context, uid, notificationStartId + uid, message, title, message)

        val preferences = preferenceRepository.get()

        val appsNewlyInstalled = preferences.getStringSetPreference(APPS_NEWLY_INSTALLED)
        preferences.setStringSetPreference(APPS_NEWLY_INSTALLED, appsNewlyInstalled.apply {
            add(uid.toString())
        })

        if (newAppsAreAllowed) {
            addFirewallRule(context, uid)
        }

        logi("FirewallNotification package added UID $uid")

        excludeTorSelfContainingAppFromTorIfNeeded(context, packages[0], uid)
    }

    private fun packageRemoved(context: Context, intent: Intent) {
        logi("FirewallNotification packageRemoved received intent $intent")

        val uid = intent.getIntExtra(Intent.EXTRA_UID, 0)

        if (intent.getBooleanExtra(Intent.EXTRA_DATA_REMOVED, false)) {
            removeFirewallRule(context, uid)
            removeTorRulesForUid(uid)
        }

        logi("FirewallNotification package removed UID $uid")
    }

    private fun addFirewallRule(context: Context?, uid: Int) {
        if (uid > 0) {

            val preferences = preferenceRepository.get()

            val appsAllowLan = preferences.getStringSetPreference(APPS_ALLOW_LAN_PREF)
            val appsAllowWifi = preferences.getStringSetPreference(APPS_ALLOW_WIFI_PREF)
            val appsAllowGsm = preferences.getStringSetPreference(APPS_ALLOW_GSM_PREF)
            val appsAllowRoaming = preferences.getStringSetPreference(APPS_ALLOW_ROAMING)
            val appsAllowVpn = preferences.getStringSetPreference(APPS_ALLOW_VPN)


            preferences.setStringSetPreference(
                APPS_ALLOW_LAN_PREF,
                appsAllowLan.apply { add(uid.toString()) })
            preferences.setStringSetPreference(
                APPS_ALLOW_WIFI_PREF,
                appsAllowWifi.apply { add(uid.toString()) })
            preferences.setStringSetPreference(
                APPS_ALLOW_GSM_PREF,
                appsAllowGsm.apply { add(uid.toString()) })
            preferences.setStringSetPreference(
                APPS_ALLOW_ROAMING,
                appsAllowRoaming.apply { add(uid.toString()) })
            if (modulesStatus.isRootAvailable) {
                preferences.setStringSetPreference(
                    APPS_ALLOW_VPN,
                    appsAllowVpn.apply { add(uid.toString()) })
            }

            if (context != null) {
                modulesStatus.setIptablesRulesUpdateRequested(context, true)
            }

            logi("FirewallNotification addFirewallRule UID $uid")
        }
    }

    private fun excludeTorSelfContainingAppFromTorIfNeeded(
        context: Context,
        pack: String,
        uid: Int
    ) {
        val packetsWithOwnTor = context.resources.getStringArray(R.array.contains_own_tor)
        if (packetsWithOwnTor.contains(pack)) {
            preferenceRepository.get().apply {
                setStringSetPreference(
                    CLEARNET_APPS,
                    getStringSetPreference(CLEARNET_APPS) + uid.toString()
                )
            }
        }
    }

    private fun removeFirewallRule(context: Context?, uid: Int) {
        if (uid > 0) {

            val preferences = preferenceRepository.get()

            val appsAllowLan = preferences.getStringSetPreference(APPS_ALLOW_LAN_PREF)
            val appsAllowWifi = preferences.getStringSetPreference(APPS_ALLOW_WIFI_PREF)
            val appsAllowGsm = preferences.getStringSetPreference(APPS_ALLOW_GSM_PREF)
            val appsAllowRoaming = preferences.getStringSetPreference(APPS_ALLOW_ROAMING)
            val appsAllowVpn = preferences.getStringSetPreference(APPS_ALLOW_VPN)


            preferences.setStringSetPreference(
                APPS_ALLOW_LAN_PREF,
                appsAllowLan.apply { remove(uid.toString()) })
            preferences.setStringSetPreference(
                APPS_ALLOW_WIFI_PREF,
                appsAllowWifi.apply { remove(uid.toString()) })
            preferences.setStringSetPreference(
                APPS_ALLOW_GSM_PREF,
                appsAllowGsm.apply { remove(uid.toString()) })
            preferences.setStringSetPreference(
                APPS_ALLOW_ROAMING,
                appsAllowRoaming.apply { remove(uid.toString()) })
            if (modulesStatus.isRootAvailable) {
                preferences.setStringSetPreference(
                    APPS_ALLOW_VPN,
                    appsAllowVpn.apply { remove(uid.toString()) })
            }

            if (context != null) {
                modulesStatus.setIptablesRulesUpdateRequested(context, true)
            }

            logi("FirewallNotification removeFirewallRule UID $uid")
        }
    }

    private fun removeTorRulesForUid(uid: Int) {
        val preferences = preferenceRepository.get()
        val torAppPreferenceKeys = listOf(UNLOCK_APPS, CLEARNET_APPS, CLEARNET_APPS_FOR_PROXY)
        for (preferenceKey in torAppPreferenceKeys) {
            val uids = preferences.getStringSetPreference(preferenceKey)
            if (uids.contains(uid.toString())) {
                preferences.setStringSetPreference(
                    preferenceKey,
                    uids.apply { remove(uid.toString()) }
                )
            }
        }
    }

    private fun closeNotification(notificationManager: NotificationManager?, notificationId: Int) {
        if (notificationId > 0) {
            notificationManager?.cancel(notificationId)
        }
    }

    private fun sendNotification(
        context: Context?,
        uid: Int,
        notificationId: Int,
        ticker: String,
        title: String,
        message: String
    ) {

        if (context == null) {
            return
        }

        val notificationManager =
            context.applicationContext?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (areNotificationsNotAllowed(notificationManager)) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createFirewallChannel(context)
        }

        val notificationIntent = Intent(context, SettingsActivity::class.java)
        notificationIntent.action = "firewall"
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        val contentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                context,
                1025,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            @Suppress("UnspecifiedImmutableFlag")
            PendingIntent.getActivity(
                context, 1025,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val allowApp = Intent(context, FirewallNotification::class.java)
        allowApp.action = ALLOW_ACTION
        allowApp.putExtra(NOTIFICATION_ID, notificationId)
        allowApp.putExtra(EXTRA_UID, uid)
        val allowPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getBroadcast(
                context,
                notificationId,
                allowApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            @Suppress("UnspecifiedImmutableFlag")
            PendingIntent.getBroadcast(
                context,
                notificationId,
                allowApp,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val denyApp = Intent(context, FirewallNotification::class.java)
        denyApp.action = DENY_ACTION
        denyApp.putExtra(NOTIFICATION_ID, notificationId)
        denyApp.putExtra(EXTRA_UID, uid)
        val denyPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getBroadcast(
                context,
                notificationId,
                denyApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            @Suppress("UnspecifiedImmutableFlag")
            PendingIntent.getBroadcast(
                context,
                notificationId,
                denyApp,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val builder = NotificationCompat.Builder(context, FIREWALL_CHANNEL_ID)
        builder.setContentIntent(contentIntent)
            .setOngoing(false)
            .setSmallIcon(R.drawable.ic_firewall)
            .setTicker(ticker)
            .setContentTitle(title)
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(300))
            .setChannelId(FIREWALL_CHANNEL_ID)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(
                R.drawable.ic_done_white,
                context.getText(R.string.allow),
                allowPendingIntent
            )
            .addAction(
                R.drawable.ic_close_white,
                context.getText(R.string.deny),
                denyPendingIntent
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setCategory(Notification.CATEGORY_REMINDER)
        }

        val notification = builder.build()
        notificationManager.notify(notificationId, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createFirewallChannel(context: Context) {
        val notificationManager =
            ContextCompat.getSystemService(context, NotificationManager::class.java)
        val channel = NotificationChannel(
            FIREWALL_CHANNEL_ID,
            context.getString(R.string.notification_channel_firewall),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        channel.description = ""
        channel.enableLights(true)
        channel.enableVibration(true)
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        channel.setShowBadge(true)
        notificationManager?.createNotificationChannel(channel)
    }
}
