package pan.alexander.tordnscrypt.update;
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

import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import android.util.SparseArray;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.utils.wakelock.WakeLocksManager;

public class UpdateService extends Service {

    public static final String DOWNLOAD_ACTION = "pan.alexander.tordnscrypt.DOWNLOAD_ACTION";
    public static final String INSTALLATION_REQUEST_ACTION = "pan.alexander.tordnscrypt.INSTALLATION_REQUEST_ACTION";
    static final String STOP_DOWNLOAD_ACTION = "pan.alexander.tordnscrypt.STOP_DOWNLOAD_ACTION";
    public static final String UPDATE_RESULT = "pan.alexander.tordnscrypt.action.UPDATE_RESULT";
    public static final String UPDATE_CHANNEL_ID = "UPDATE_CHANNEL_INVIZIBLE";
    static final int UPDATE_CHANNEL_NOTIFICATION_ID = 103104;

    final AtomicInteger currentNotificationId = new AtomicInteger(UPDATE_CHANNEL_NOTIFICATION_ID);

    NotificationManager notificationManager;
    volatile SparseArray<DownloadTask> sparseArray;
    private WakeLocksManager wakeLocksManager = WakeLocksManager.getInstance();
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;

    public UpdateService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        App.instance.daggerComponent.inject(this);

        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sparseArray = new SparseArray<>();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sharedPreferences.getBoolean("swWakelock", false)
                || !wakeLocksManager.isPowerWakeLockHeld() && !wakeLocksManager.isWiFiWakeLockHeld()) {
            wakeLocksManager.managePowerWakelock(this, true);
            wakeLocksManager.manageWiFiLock(this, true);
        } else {
            wakeLocksManager = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            createNotificationChannel();
            sendNotification(0, currentNotificationId.get(), System.currentTimeMillis(), getString(R.string.app_name), getString(R.string.app_name), "");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action == null) {
            sendNotification(startId, currentNotificationId.get(), System.currentTimeMillis(), getString(R.string.app_name), getString(R.string.app_name), "");
            stopForeground(true);
            stopSelf();
        } else if (action.equals(DOWNLOAD_ACTION)) {
            startDownloadAction(intent, startId);
        } else if (action.equals(STOP_DOWNLOAD_ACTION)) {
            stopDownloadAction(intent);
        } else if (action.equals(INSTALLATION_REQUEST_ACTION)) {
            installationRequestAction();
        } else {
            sendNotification(startId, currentNotificationId.get(), System.currentTimeMillis(), getString(R.string.app_name), getString(R.string.app_name), "");
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (wakeLocksManager != null) {
            wakeLocksManager.stopPowerWakelock();
            wakeLocksManager.stopWiFiLock();
        }
    }

    private void startDownloadAction(Intent intent, int startId) {
        long startTime = System.currentTimeMillis();
        int notificationId = currentNotificationId.getAndIncrement();

        DownloadTask downloadTask = new DownloadTask(this, intent, startId, notificationId, startTime);
        sparseArray.put(startId, downloadTask);

        sendNotification(
                startId,
                notificationId,
                startTime,
                getString(R.string.update_notification),
                "",
                getString(R.string.update_notification)
        );

        downloadTask.start();
    }

    private void stopDownloadAction(Intent intent) {
        int serviceId = intent.getIntExtra("ServiceStartId", 0);
        DownloadTask downloadTask = sparseArray.get(serviceId);
        if (downloadTask != null) {
            sendNotification(
                    downloadTask.serviceStartId,
                    downloadTask.notificationId,
                    downloadTask.startTime,
                    getString(R.string.update_interrupt_notification),
                    "",
                    getString(R.string.update_interrupt_notification)
            );
            downloadTask.interrupt();
            sparseArray.delete(serviceId);
        }
    }

    private void installationRequestAction() {
        sendNotification(0, currentNotificationId.get(), System.currentTimeMillis(), getString(R.string.app_name), getString(R.string.app_name), "");

        String path = preferenceRepository.get().getStringPreference("RequiredAppUpdateForQ");

        if (!path.isEmpty()) {

            preferenceRepository.get().setStringPreference("RequiredAppUpdateForQ", "");

            File file = new File(path);

            if (file.isFile()) {
                Uri apkUri = FileProvider.getUriForFile(this, this.getPackageName() + ".fileprovider", file);
                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(apkUri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(intent);
            }
        }

        stopForeground(true);
        stopSelf();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel
                (UPDATE_CHANNEL_ID, getString(R.string.notification_channel_update), NotificationManager.IMPORTANCE_DEFAULT);
        notificationChannel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        notificationChannel.setDescription("");
        notificationChannel.enableLights(false);
        notificationChannel.enableVibration(false);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        notificationManager.createNotificationChannel(notificationChannel);
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    void sendNotification(int serviceStartId, int notificationId, long startTime, String Ticker, String Title, String Text) {

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        Intent stopDownloadIntent = new Intent(this, UpdateService.class);
        stopDownloadIntent.setAction(STOP_DOWNLOAD_ACTION);
        stopDownloadIntent.putExtra("ServiceStartId", serviceStartId);
        PendingIntent stopDownloadPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            stopDownloadPendingIntent = PendingIntent.getService(
                    this,
                    notificationId,
                    stopDownloadIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            stopDownloadPendingIntent = PendingIntent.getService(
                    this,
                    notificationId,
                    stopDownloadIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            contentIntent = PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            contentIntent = PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, UPDATE_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(R.drawable.ic_update)
                .setTicker(Ticker)
                .setContentTitle(Title)
                .setContentText(Text)
                .setOnlyAlertOnce(true)
                .setWhen(startTime)
                .setUsesChronometer(true)
                .setChannelId(UPDATE_CHANNEL_ID)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_PROGRESS);
        }

        if (serviceStartId != 0) {
            builder.addAction(R.drawable.ic_stop, getText(R.string.cancel_download), stopDownloadPendingIntent);
        }

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
        } else {
            startForeground(notificationId, notification);
        }
    }
}
