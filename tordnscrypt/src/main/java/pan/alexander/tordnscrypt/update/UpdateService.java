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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

import javax.net.ssl.HttpsURLConnection;

import pan.alexander.tordnscrypt.ApplicationExt;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.WakeLocksManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.RootExecService.TopFragmentMark;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;

public class UpdateService extends Service {
    public static final String DOWNLOAD_ACTION = "pan.alexander.tordnscrypt.DOWNLOAD_ACTION";
    public static final String INSTALLATION_REQUEST_ACTION = "pan.alexander.tordnscrypt.INSTALLATION_REQUEST_ACTION";
    private static final String STOP_DOWNLOAD_ACTION = "pan.alexander.tordnscrypt.STOP_DOWNLOAD_ACTION";
    public static final String UPDATE_RESULT = "pan.alexander.tordnscrypt.action.UPDATE_RESULT";
    public static final String UPDATE_CHANNEL_ID = "UPDATE_CHANNEL_INVIZIBLE";
    private NotificationManager notificationManager;
    private static final int UPDATE_CHANNEL_NOTIFICATION_ID = 103104;
    private static final int READTIMEOUT = 60;
    private static final int CONNECTTIMEOUT = 60;
    private final AtomicInteger currentNotificationId = new AtomicInteger(UPDATE_CHANNEL_NOTIFICATION_ID);
    private volatile SparseArray<DownloadThread> sparseArray;
    private boolean allowSendBroadcastAfterUpdate = true;
    private WakeLocksManager wakeLocksManager;

    public UpdateService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        sparseArray = new SparseArray<>();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        wakeLocksManager = WakeLocksManager.getInstance();
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action == null) {
            sendNotification(startId, currentNotificationId.get(), System.currentTimeMillis(), getString(R.string.app_name), getString(R.string.app_name), "");
            stopForeground(true);
            stopSelf();
        } else if (action.equals(DOWNLOAD_ACTION)) {
            DownloadThread downloadThread = new DownloadThread(intent, startId, currentNotificationId.getAndIncrement());
            sparseArray.put(startId, downloadThread);
            downloadThread.startDownloadThread();
        } else if (action.equals(STOP_DOWNLOAD_ACTION)) {
            int serviceId = intent.getIntExtra("ServiceStartId", 0);
            DownloadThread downloadThread = sparseArray.get(serviceId);
            if (downloadThread != null) {
                sendNotification(downloadThread.serviceStartId, downloadThread.notificationId, downloadThread.startTime, getString(R.string.update_interrupt_notification), "", getString(R.string.update_interrupt_notification));
                downloadThread.thread.interrupt();
                sparseArray.delete(serviceId);
            }
        } else if (action.equals(INSTALLATION_REQUEST_ACTION)) {
            sendNotification(0, currentNotificationId.get(), System.currentTimeMillis(), getString(R.string.app_name), getString(R.string.app_name), "");

            String path = new PrefManager(this).getStrPref("RequiredAppUpdateForQ");

            if (!path.isEmpty()) {

                new PrefManager(this).setStrPref("RequiredAppUpdateForQ", "");

                File file = new File(path);

                if (file.isFile()) {
                    Uri apkUri = FileProvider.getUriForFile(this, this.getPackageName() + ".fileprovider", file);
                    intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setData(apkUri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    this.startActivity(intent);
                }
            }

            stopForeground(true);
            stopSelf();

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

    private class DownloadThread {
        Intent intent;
        int serviceStartId;
        int notificationId;
        long startTime = System.currentTimeMillis();
        Thread thread;

        DownloadThread(Intent intent, int serviceStartId, int notificationId) {
            this.intent = intent;
            this.serviceStartId = serviceStartId;
            this.notificationId = notificationId;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        void startDownloadThread() {

            sendNotification(serviceStartId, notificationId, startTime, getString(R.string.update_notification), "", getString(R.string.update_notification));

            Thread downloadThread = new Thread(downloadUpdateWork);
            downloadThread.setDaemon(false);
            downloadThread.start();


        }

        Runnable downloadUpdateWork = new Runnable() {

            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void run() {
                thread = Thread.currentThread();
                Context context = UpdateService.this;

                String urlToDownload = intent.getStringExtra("url");
                String fileToDownload = intent.getStringExtra("file");
                String hash = intent.getStringExtra("hash");

                try {

                    Proxy proxy = null;
                    if (ModulesStatus.getInstance().getTorState() == RUNNING) {
                        PathVars pathVars = PathVars.getInstance(context);
                        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", Integer.parseInt(pathVars.getTorHTTPTunnelPort())));
                    }

                    //create url and connect
                    URL url = new URL(urlToDownload);

                    HttpsURLConnection con;
                    if (proxy == null) {
                        con = (HttpsURLConnection) url.openConnection();
                    } else {
                        con = (HttpsURLConnection) url.openConnection(proxy);
                    }

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        con.setHostnameVerifier((hostname, session) -> true);
                    }

                    con.setConnectTimeout(1000 * CONNECTTIMEOUT);
                    con.setReadTimeout(1000 * READTIMEOUT);
                    con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9.0.1; " +
                            "Mi Mi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Mobile Safari/537.36");
                    con.connect();

                    // this will be useful so that you can show a typical 0-100% progress bar
                    int fileLength = con.getContentLength();

                    // download the file
                    InputStream input = new BufferedInputStream(con.getInputStream());

                    File cacheDir = getExternalCacheDir();
                    if (cacheDir == null) {
                        cacheDir = getCacheDir();
                    }
                    if (!cacheDir.isDirectory()) {
                        if (cacheDir.mkdirs()) {
                            Log.i(LOG_TAG, "downloadUpdateWork create cache dir success");
                            if (cacheDir.setReadable(true)
                                    && cacheDir.setWritable(true)) {
                                Log.i(LOG_TAG, "downloadUpdateWork chmod cache dir success");
                            } else {
                                Log.e(LOG_TAG, "downloadUpdateWork chmod cache dir failed");
                            }
                        } else {
                            Log.e(LOG_TAG, "downloadUpdateWork create cache dir failed");
                        }
                    }

                    removeOldApkFileFromPrevUpdate(cacheDir);

                    String path = cacheDir + "/" + fileToDownload;
                    OutputStream output = new FileOutputStream(path);

                    byte[] data = new byte[1024];
                    long total = 0;
                    int count;
                    int percent = 0;
                    while ((count = input.read(data)) != -1) {
                        total += count;

                        if (thread.isInterrupted()) {
                            Log.w(LOG_TAG, "Download was interrupted by user " + fileToDownload);
                            break;
                        }


                        int currentPercent = (int) (total * 100 / fileLength);
                        if (currentPercent - percent >= 5) {
                            percent = currentPercent;
                            String notification = getText(R.string.update_notification).toString() +
                                    " " + fileToDownload;

                            updateNotification(serviceStartId, notificationId, startTime, getText(R.string.update_notification).toString(), "", notification, percent);
                        }

                        output.write(data, 0, count);
                    }

                    // close streams
                    output.flush();
                    output.close();
                    input.close();

                    if (Objects.requireNonNull(crc32(new File(path))).equalsIgnoreCase(hash)
                            && !new PrefManager(context).getStrPref("UpdateResultMessage").equals(getString(R.string.update_fault))) {

                        new PrefManager(context).setStrPref("LastUpdateResult",
                                context.getString(R.string.update_installed));

                        if (fileToDownload != null && fileToDownload.contains("InviZible")) {
                            allowSendBroadcastAfterUpdate = false;

                            File file = new File(path);

                            boolean isActivityActive = false;
                            if (context.getApplicationContext() instanceof ApplicationExt) {

                                ApplicationExt applicationExt = (ApplicationExt) context.getApplicationContext();
                                WeakReference<Activity> currentActivity = applicationExt.getCurrentActivity();
                                isActivityActive = currentActivity != null && currentActivity.get() != null
                                        && !currentActivity.get().isFinishing();
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isActivityActive) {
                                //Required for androidQ because even if the service is in the foreground we cannot start an activity if no activity is visible
                                new PrefManager(context).setStrPref("RequiredAppUpdateForQ", path);
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.setData(apkUri);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                PackageManager packageManager = context.getPackageManager();
                                if (packageManager != null && intent.resolveActivity(packageManager) != null) {
                                    context.startActivity(intent);
                                }
                            } else {
                                Uri apkUri = Uri.fromFile(file);
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                PackageManager packageManager = context.getPackageManager();
                                if (packageManager != null && intent.resolveActivity(packageManager) != null) {
                                    context.startActivity(intent);
                                }
                            }

                            makeDelay(3);
                        }

                    } else {
                        FileOperations.deleteFile(context, cacheDir.getPath(), fileToDownload, "ignored");
                        new PrefManager(context).setStrPref("LastUpdateResult", getString(R.string.update_fault));
                        new PrefManager(context).setStrPref("UpdateResultMessage", getString(R.string.update_fault));
                        Log.w(LOG_TAG, "UpdateService file hashes mismatch " + fileToDownload);
                    }

                } catch (Exception e) {
                    new PrefManager(context).setStrPref("LastUpdateResult", getString(R.string.update_fault));
                    new PrefManager(context).setStrPref("UpdateResultMessage", getString(R.string.update_fault));
                    Log.e(LOG_TAG, "UpdateService failed to download file " + urlToDownload + " " + e.getMessage());
                } finally {
                    sparseArray.delete(serviceStartId);
                    if (currentNotificationId.get() - 1 == UPDATE_CHANNEL_NOTIFICATION_ID) {
                        stopForeground(true);
                        notificationManager.cancel(notificationId);
                        sendUpdateResultBroadcast();
                        stopSelf();
                    } else {
                        notificationManager.cancel(notificationId);
                        currentNotificationId.getAndDecrement();
                    }

                }

            }
        };
    }

    private void sendUpdateResultBroadcast() {
        if (allowSendBroadcastAfterUpdate) {
            makeDelay(5);

            Intent intent = new Intent(UPDATE_RESULT);
            intent.putExtra("Mark", TopFragmentMark);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void sendNotification(int serviceStartId, int notificationId, long startTime, String Ticker, String Title, String Text) {

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        Intent stopDownloadIntent = new Intent(this, UpdateService.class);
        stopDownloadIntent.setAction(STOP_DOWNLOAD_ACTION);
        stopDownloadIntent.putExtra("ServiceStartId", serviceStartId);
        PendingIntent stopDownloadPendingIntent = PendingIntent.getService(this, notificationId, stopDownloadIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, UPDATE_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(R.drawable.ic_update)
                .setTicker(Ticker)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                .setOnlyAlertOnce(true)
                .setWhen(startTime)
                .setUsesChronometer(true)
                .setChannelId(UPDATE_CHANNEL_ID)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

        if (serviceStartId != 0) {
            builder.addAction(R.drawable.ic_stop, getText(R.string.cancel_download), stopDownloadPendingIntent);
        }

        Notification notification = builder.build();

        startForeground(notificationId, notification);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void updateNotification(int serviceStartId, int notificationId, long startTime, String Ticker, String Title, String Text, int percent) {

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stopDownloadIntent = new Intent(this, UpdateService.class);
        stopDownloadIntent.setAction(STOP_DOWNLOAD_ACTION);
        stopDownloadIntent.putExtra("ServiceStartId", serviceStartId);
        PendingIntent stopDownloadPendingIntent = PendingIntent.getService(this, notificationId, stopDownloadIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, UPDATE_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(R.drawable.ic_update)
                .setTicker(Ticker)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                .setOnlyAlertOnce(true)
                .setWhen(startTime)
                .setUsesChronometer(true)
                .setChannelId(UPDATE_CHANNEL_ID)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(R.drawable.ic_stop, getText(R.string.cancel_download), stopDownloadPendingIntent);

        int PROGRESS_MAX = 100;
        builder.setProgress(PROGRESS_MAX, percent, false);

        Notification notification = builder.build();

        synchronized (notificationManager) {
            notificationManager.notify(notificationId, notification);
        }
    }

    private String crc32(File file) {
        CRC32 crc = new CRC32();

        InputStream is;
        try {
            is = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "crc32() Exception while getting FileInputStream " + e.getMessage());
            return null;
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        byte[] readBuffer = new byte[4 * 1024];

        try {
            int read;
            do {
                read = is.read(readBuffer, 0, readBuffer.length);
                if (read == -1) {
                    break;
                }
                bout.write(readBuffer, 0, read);
            } while (true);

            crc.update(bout.toByteArray());

            is.close();
            bout.close();

            return String.format("%08X", crc.getValue());
        } catch (IOException e) {
            Log.e(LOG_TAG, "crc32() IOException " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unused")
    private void removeOldApkFileFromPrevUpdate(File dir) {

        try {
            if (dir.listFiles() == null) {
                return;
            }

            for (File file : Objects.requireNonNull(dir.listFiles())) {
                if (file.getName().contains("InviZible")) {
                    boolean result = file.delete();
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to remove old InviZible.apk file during update" + e.getMessage() + " " + e.getCause());
        }
    }

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException ignored) {
        }
    }

}
