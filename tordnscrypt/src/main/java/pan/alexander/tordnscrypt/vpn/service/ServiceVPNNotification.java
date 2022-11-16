package pan.alexander.tordnscrypt.vpn.service;
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import pan.alexander.tordnscrypt.MainActivity;

import static pan.alexander.tordnscrypt.AppKt.ANDROID_CHANNEL_ID;
import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;

class ServiceVPNNotification {
    private final Service serviceVPN;
    private final NotificationManager notificationManager;

    ServiceVPNNotification(Service serviceVPN, NotificationManager notificationManager) {
        this.serviceVPN = serviceVPN;
        this.notificationManager = notificationManager;
    }

    void sendNotification(String Title, String Text) {

        if (serviceVPN == null || notificationManager == null) {
            return;
        }

        notificationManager.cancel(DEFAULT_NOTIFICATION_ID);

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel notificationChannel = new NotificationChannel
                    (ANDROID_CHANNEL_ID, ANDROID_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription("Protect VPN");
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager.createNotificationChannel(notificationChannel);
        }*/

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(serviceVPN, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(serviceVPN.getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int iconResource = serviceVPN.getResources().getIdentifier("ic_service_notification", "drawable", serviceVPN.getPackageName());
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_menu_view;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(serviceVPN, ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(iconResource)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setChannelId(ANDROID_CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE);
        }

        Notification notification = builder.build();

        serviceVPN.startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }

}
