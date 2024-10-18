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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.modules;

import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Context.RECEIVER_NOT_EXPORTED;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;

import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

public class ModulesServiceNotificationManager extends BroadcastReceiver {

    private final static String ANDROID_CHANNEL_ID = "InviZible";
    private final static String STOP_ALL_ACTION = "pan.alexander.tordnscrypt.NOTIFICATION_STOP_ALL_ACTION";
    private final static int STOP_ALL_ACTION_CODE = 1120;
    private static volatile ModulesServiceNotificationManager instance;

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    public ModulesServiceNotificationManager() {
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private PendingIntent getContentIntent(Context context) {
        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            contentIntent = PendingIntent.getActivity(
                    context.getApplicationContext(),
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            contentIntent = PendingIntent.getActivity(
                    context.getApplicationContext(),
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return contentIntent;
    }

    private PendingIntent getStopIntent(Context context) {

        Intent intent = new Intent(context, ModulesServiceNotificationManager.class);
        intent.setAction(STOP_ALL_ACTION);

        PendingIntent stopIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            stopIntent = PendingIntent.getBroadcast(
                    context.getApplicationContext(),
                    STOP_ALL_ACTION_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            stopIntent = PendingIntent.getBroadcast(
                    context.getApplicationContext(),
                    STOP_ALL_ACTION_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
        }

        return stopIntent;
    }

    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                ANDROID_CHANNEL_ID,
                context.getString(R.string.notification_channel_services),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        channel.setDescription("");
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        channel.setShowBadge(false);
        getNotificationManager(context).createNotificationChannel(channel);
    }

    private NotificationManager getNotificationManager(Context context) {
        return (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
    }

    private int getSmallIcon(Context context) {

        int iconResource = android.R.drawable.ic_menu_view;

        try {
            iconResource = context.getResources().getIdentifier(
                    "ic_service_notification",
                    "drawable",
                    context.getPackageName()
            );
            if (iconResource == 0) {
                iconResource = android.R.drawable.ic_menu_view;
            }
        } catch (Exception e) {
            loge("ModulesServiceNotificationManager getSmallIcon", e);
        }

        return iconResource;
    }


    public synchronized void sendNotification(Service service, String title, String text, long startTime) {

        getNotificationManager(service).cancel(DEFAULT_NOTIFICATION_ID);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, ANDROID_CHANNEL_ID);
        builder.setContentIntent(getContentIntent(service))
                .setOngoing(true)
                .setSmallIcon(getSmallIcon(service))
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setChannelId(ANDROID_CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAnyModuleRunning()) {
            builder.addAction(
                    R.drawable.ic_close_white,
                    service.getText(R.string.main_fragment_button_stop),
                    getStopIntent(service)
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE);
        }

        if (startTime != 0) {
            builder.setWhen(startTime)
                    .setUsesChronometer(true);
        }

        Notification notification = builder.build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(DEFAULT_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
            } else {
                service.startForeground(DEFAULT_NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            loge("ModulesServiceNotificationManager sendNotification", e);
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    public void updateNotification(Context context, String title, String text, long startTime) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ANDROID_CHANNEL_ID);
        builder.setContentIntent(getContentIntent(context))
                .setOngoing(true)
                .setSmallIcon(getSmallIcon(context))
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(Notification.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setChannelId(ANDROID_CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAnyModuleRunning()) {
            builder.addAction(
                    R.drawable.ic_close_white,
                    context.getText(R.string.main_fragment_button_stop),
                    getStopIntent(context)
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_SERVICE);
        }

        if (startTime != 0) {
            builder.setWhen(startTime)
                    .setUsesChronometer(true);
        }

        Notification notification = builder.build();

        getNotificationManager(context).notify(DEFAULT_NOTIFICATION_ID, notification);
    }

    private boolean isAnyModuleRunning() {
        return modulesStatus.getDnsCryptState() == ModuleState.RUNNING
                || modulesStatus.getTorState() == ModuleState.RUNNING
                || modulesStatus.getItpdState() == ModuleState.RUNNING
                || modulesStatus.getFirewallState() == ModuleState.RUNNING;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    public static ModulesServiceNotificationManager getManager(Context context) {
        if (instance == null) {
            synchronized (ModulesServiceNotificationManager.class) {
                if (instance == null) {
                    instance = new ModulesServiceNotificationManager();
                    IntentFilter filter = new IntentFilter(STOP_ALL_ACTION);
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            context.getApplicationContext().registerReceiver(
                                    instance,
                                    filter,
                                    RECEIVER_NOT_EXPORTED
                            );
                        } else {
                            context.getApplicationContext().registerReceiver(
                                    instance,
                                    filter
                            );
                        }
                    } catch (Exception e) {
                        loge("ModulesServiceNotificationManager getNotificationManager", e);
                    }
                    return instance;
                }
            }
        }
        return instance;
    }

    public static void stopManager(Context context) {
        if (instance != null) {
            synchronized (ModulesServiceNotificationManager.class) {
                if (instance != null) {
                    try {
                        context.getApplicationContext().unregisterReceiver(instance);
                    } catch (Exception e) {
                        loge("ModulesServiceNotificationManager stopNotificationManager", e);
                    }
                    instance = null;
                }
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context != null && intent != null && STOP_ALL_ACTION.equals(intent.getAction())) {
            stopServices(context);
        }
    }

    private void stopServices(Context context) {
        ModulesAux.stopModulesIfRunning(context);
    }
}
