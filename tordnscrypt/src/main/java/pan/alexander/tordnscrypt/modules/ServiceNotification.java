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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import pan.alexander.tordnscrypt.MainActivity;

import static pan.alexander.tordnscrypt.ApplicationExtKt.ANDROID_CHANNEL_ID;
import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;

public class ServiceNotification {
    private final Service service;
    private final NotificationManager notificationManager;
    private final Long startTime;

    public ServiceNotification(Service service, NotificationManager notificationManager, Long startTime) {
        this.service = service;
        this.notificationManager = notificationManager;
        this.startTime = startTime;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public synchronized void sendNotification(String Title, String Text) {

        if (service == null || notificationManager == null) {
            return;
        }

        notificationManager.cancel(DEFAULT_NOTIFICATION_ID);

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(service, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(service.getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int iconResource = service.getResources().getIdentifier("ic_service_notification", "drawable", service.getPackageName());
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_menu_view;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(iconResource)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setChannelId(ANDROID_CHANNEL_ID)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (startTime != 0) {
            builder.setWhen(startTime)
                    .setUsesChronometer(true);
        }

        Notification notification = builder.build();

        service.startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void updateNotification(String Title, String Text) {
        if (service == null || notificationManager == null) {
            return;
        }

        Intent notificationIntent = new Intent(service, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(service.getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int iconResource = service.getResources().getIdentifier("ic_service_notification", "drawable", service.getPackageName());
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_menu_view;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(iconResource)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setChannelId(ANDROID_CHANNEL_ID)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (startTime != 0) {
            builder.setWhen(startTime)
                    .setUsesChronometer(true);
        }

        Notification notification = builder.build();

        notificationManager.notify(DEFAULT_NOTIFICATION_ID, notification);
    }
}
