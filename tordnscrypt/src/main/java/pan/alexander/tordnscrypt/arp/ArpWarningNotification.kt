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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.arp

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import pan.alexander.tordnscrypt.AUX_CHANNEL_ID
import pan.alexander.tordnscrypt.MainActivity
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.utils.Utils.areNotificationsNotAllowed
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import javax.inject.Inject

private const val PENDING_INTENT_REQUEST_CODE = 111

class ArpWarningNotification @Inject constructor(
    private val context: Context
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun send(
        @StringRes title: Int,
        @StringRes text: Int,
        NOTIFICATION_ID: Int
    ) {

        if (areNotificationsNotAllowed(notificationManager)) {
            return
        }

        val contentIntent = getContentIntent()

        val iconResource = getIconResource()

        val builder = NotificationCompat.Builder(context, AUX_CHANNEL_ID)
        @Suppress("DEPRECATION")
        builder.setContentIntent(contentIntent)
            .setOngoing(false)
            .setSmallIcon(iconResource)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setPriority(Notification.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(1000))
            .setChannelId(AUX_CHANNEL_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_ALARM)
                .setLargeIcon(
                    BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.ic_arp_attack_notification
                    )
                )
        }

        val notification = builder.build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getContentIntent(): PendingIntent {
        val notificationIntent = Intent(context, MainActivity::class.java)
        notificationIntent.action = Intent.ACTION_MAIN
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        notificationIntent.putExtra(MITM_ATTACK_WARNING, true)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(
                context.applicationContext,
                PENDING_INTENT_REQUEST_CODE,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            @Suppress("UnspecifiedImmutableFlag")
            PendingIntent.getActivity(
                context.applicationContext,
                PENDING_INTENT_REQUEST_CODE,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

    private fun getIconResource(): Int {
        var iconResource: Int = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.resources.getIdentifier(
                    "ic_arp_attack_notification",
                    "drawable",
                    context.packageName
                )
            } else {
                context.resources.getIdentifier(
                    "ic_service_notification",
                    "drawable",
                    context.packageName
                )
            }
        } catch (e: Exception) {
            loge("ArpWarningNotification getIconResource", e)
            android.R.drawable.ic_lock_power_off
        }

        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_lock_power_off
        }

        return iconResource
    }

}
