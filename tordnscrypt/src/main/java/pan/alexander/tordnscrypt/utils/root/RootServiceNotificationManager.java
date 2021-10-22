package pan.alexander.tordnscrypt.utils.root;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;

public class RootServiceNotificationManager {
    private final Service service;
    private final NotificationManager notificationManager;

    public static final String ROOT_CHANNEL_ID = "ROOT_COMMANDS_INVIZIBLE";
    public static final int DEFAULT_NOTIFICATION_ID = 102;

    public RootServiceNotificationManager(Service service, NotificationManager notificationManager) {
        this.service = service;
        this.notificationManager = notificationManager;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void createNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel
                (ROOT_CHANNEL_ID, service.getString(R.string.notification_channel_root), NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription("");
        notificationChannel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        notificationChannel.enableLights(false);
        notificationChannel.enableVibration(false);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(notificationChannel);

        sendNotification(service.getString(R.string.notification_temp_text), "");
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    void sendNotification(String Title, String Text) {

        Intent notificationIntent = new Intent(service, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            contentIntent = PendingIntent.getActivity(
                    service.getApplicationContext(),
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            contentIntent = PendingIntent.getActivity(
                    service.getApplicationContext(),
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        int iconResource = service.getResources().getIdentifier(
                "ic_service_notification",
                "drawable",
                service.getPackageName()
        );
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_menu_view;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, ROOT_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(false)
                .setSmallIcon(iconResource)
                .setContentTitle(Title)
                .setContentText(Text)
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setChannelId(ROOT_CHANNEL_ID)
                .setProgress(100, 100, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_PROGRESS);
        }

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(DEFAULT_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            service.startForeground(DEFAULT_NOTIFICATION_ID, notification);
        }
    }
}
