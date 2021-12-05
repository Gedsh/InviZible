package pan.alexander.tordnscrypt.modules;

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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import pan.alexander.tordnscrypt.MainActivity;

import static pan.alexander.tordnscrypt.AppKt.ANDROID_CHANNEL_ID;
import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;

public class ModulesServiceNotificationManager {
    private final Service service;
    private final NotificationManager notificationManager;
    private final Long startTime;
    private final PendingIntent contentIntent;
    private final int iconResource;

    public ModulesServiceNotificationManager(Service service, NotificationManager notificationManager, Long startTime) {
        this.service = service;
        this.notificationManager = notificationManager;
        this.startTime = startTime;
        this.contentIntent = getContentIntent();
        this.iconResource = getIconResource();
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private PendingIntent getContentIntent() {
        Intent notificationIntent = new Intent(service, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            contentIntent = PendingIntent.getActivity(
                    service.getApplicationContext(),
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            contentIntent = PendingIntent.getActivity(
                    service.getApplicationContext(),
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return contentIntent;
    }

    private int getIconResource() {
        int iconResource = service.getResources().getIdentifier("ic_service_notification", "drawable", service.getPackageName());
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_menu_view;
        }
        return iconResource;
    }


    public synchronized void sendNotification(String title, String text) {

        if (service == null || notificationManager == null) {
            return;
        }

        notificationManager.cancel(DEFAULT_NOTIFICATION_ID);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)
                .setSmallIcon(iconResource)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setChannelId(ANDROID_CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE);
        }

        if (startTime != 0) {
            builder.setWhen(startTime)
                    .setUsesChronometer(true);
        }

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(DEFAULT_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            service.startForeground(DEFAULT_NOTIFICATION_ID, notification);
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    public void updateNotification(String title, String text) {
        if (service == null || notificationManager == null) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)
                .setSmallIcon(iconResource)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setChannelId(ANDROID_CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE);
        }

        if (startTime != 0) {
            builder.setWhen(startTime)
                    .setUsesChronometer(true);
        }

        Notification notification = builder.build();

        notificationManager.notify(DEFAULT_NOTIFICATION_ID, notification);
    }
}
