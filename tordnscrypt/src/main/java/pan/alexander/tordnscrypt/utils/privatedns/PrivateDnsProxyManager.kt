package pan.alexander.tordnscrypt.utils.privatedns

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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import pan.alexander.tordnscrypt.AUX_CHANNEL_ID
import pan.alexander.tordnscrypt.MainActivity
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.Utils.areNotificationsNotAllowed
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.vpn.VpnUtils

const val DISABLE_PRIVATE_DNS_NOTIFICATION = 167
const val DISABLE_PROXY_NOTIFICATION = 168

object PrivateDnsProxyManager {
    @RequiresApi(Build.VERSION_CODES.P)
    fun checkPrivateDNSAndProxy(context: Context, linkProperties: LinkProperties?) {
        try {
            if (ModulesStatus.getInstance().mode == OperationMode.PROXY_MODE) {
                return
            }

            var localLinkProperties = linkProperties

            if (localLinkProperties == null) {
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                localLinkProperties =
                    connectivityManager.getLinkProperties(connectivityManager.activeNetwork)

                Log.i(LOG_TAG, "LinkProperties $localLinkProperties")
            }


            // localLinkProperties.privateDnsServerName == null - Opportunistic mode ("Automatic")
            if (VpnUtils.isPrivateDns(context) || localLinkProperties?.isPrivateDnsActive == true) {
                sendNotification(
                    context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.helper_dnscrypt_private_dns),
                    DISABLE_PRIVATE_DNS_NOTIFICATION
                )
            }

            if (localLinkProperties?.httpProxy != null) {

                if (NetworkChecker.isWifiActive(context)) {
                    sendNotification(
                        context,
                        context.getString(R.string.app_name),
                        context.getString(R.string.helper_dnscrypt_proxy_wifi),
                        DISABLE_PROXY_NOTIFICATION
                    )
                } else if (NetworkChecker.isCellularActive(context)) {
                    sendNotification(
                        context,
                        context.getString(R.string.app_name),
                        context.getString(R.string.helper_dnscrypt_proxy_gsm),
                        DISABLE_PROXY_NOTIFICATION
                    )
                }

            }
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "AuxNotificationSender checkPrivateDNSAndProxy exception ${e.message} ${e.cause}"
            )
        }
    }

    private fun sendNotification(
        context: Context,
        title: String,
        text: String,
        NOTIFICATION_ID: Int
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (areNotificationsNotAllowed(notificationManager)) {
            return
        }

        var notificationIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)

        val packageManager: PackageManager = context.packageManager
        if (notificationIntent.resolveActivity(packageManager) == null) {
            notificationIntent = Intent(context, MainActivity::class.java)
        }

        val contentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                context.applicationContext,
                165,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            @Suppress("UnspecifiedImmutableFlag")
            PendingIntent.getActivity(
                context.applicationContext,
                165,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        var iconResource: Int =
            context.resources.getIdentifier("ic_aux_notification", "drawable", context.packageName)
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_dialog_alert
        }
        val builder = NotificationCompat.Builder(context, AUX_CHANNEL_ID)
        @Suppress("DEPRECATION")
        builder.setContentIntent(contentIntent)
            .setOngoing(false) //Can be swiped out
            .setSmallIcon(iconResource)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_aux_notification
                )
            )
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(Notification.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setLights(Color.YELLOW, 1000, 1000)
            .setChannelId(AUX_CHANNEL_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_ALARM)
        }

        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
