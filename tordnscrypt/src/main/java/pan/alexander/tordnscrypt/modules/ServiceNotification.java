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
import androidx.core.app.NotificationCompat;

import pan.alexander.tordnscrypt.MainActivity;

import static pan.alexander.tordnscrypt.ApplicationExtKt.ANDROID_CHANNEL_ID;
import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;

public class ServiceNotification {
    //public static final String ANDROID_CHANNEL_ID = "InviZible";
    //public static final String ANDROID_CHANNEL_NAME = "NOTIFICATION_CHANNEL_INVIZIBLE";
    private final Service modulesService;
    private final NotificationManager notificationManager;

    ServiceNotification(Service modulesService, NotificationManager notificationManager) {
        this.modulesService = modulesService;
        this.notificationManager = notificationManager;
    }

    void sendNotification(String Title, String Text) {

        if (modulesService == null) {
            return;
        }

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel notificationChannel = new NotificationChannel
                    (ANDROID_CHANNEL_ID, ANDROID_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription("Protect InviZible Pro");
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            notificationManager.createNotificationChannel(notificationChannel);
        }*/

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(modulesService, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(modulesService.getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int iconResource = modulesService.getResources().getIdentifier("ic_service_notification", "drawable", modulesService.getPackageName());
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_menu_view;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(modulesService, ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(iconResource)
                //.setLargeIcon(BitmapFactory.decodeResource(res, R.drawable.large))   // большая картинка
                //.setTicker(Ticker)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                //.setStyle(new NotificationCompat.BigTextStyle().bigText(Text))
                //.setWhen(System.currentTimeMillis())
                //new experiment
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        Notification notification = builder.build();

        modulesService.startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }
}
