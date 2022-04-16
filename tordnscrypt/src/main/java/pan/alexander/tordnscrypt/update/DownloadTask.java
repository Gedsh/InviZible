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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

import static pan.alexander.tordnscrypt.update.UpdateService.STOP_DOWNLOAD_ACTION;
import static pan.alexander.tordnscrypt.update.UpdateService.UPDATE_CHANNEL_ID;
import static pan.alexander.tordnscrypt.update.UpdateService.UPDATE_CHANNEL_NOTIFICATION_ID;
import static pan.alexander.tordnscrypt.update.UpdateService.UPDATE_RESULT;
import static pan.alexander.tordnscrypt.utils.AppExtension.getApp;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.TOR_BROWSER_USER_AGENT;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.TOP_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;

public class DownloadTask extends Thread {
    private static final int READ_TIMEOUT = 60;
    private static final int CONNECT_TIMEOUT = 60;

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public PathVars pathVars;

    private final Context context;
    private final UpdateService updateService;
    private final Intent intent;
    private final String cacheDir;

    private boolean allowSendBroadcastAfterUpdate = true;

    int serviceStartId;
    int notificationId;
    long startTime;



    public DownloadTask(UpdateService updateService, Intent intent, int serviceStartId, int notificationId, long startTime) {
        App.getInstance().getDaggerComponent().inject(this);
        this.context = updateService;
        this.updateService = updateService;
        this.intent = intent;
        this.serviceStartId = serviceStartId;
        this.notificationId = notificationId;
        this.startTime = startTime;
        this.cacheDir = pathVars.getCacheDirPath(updateService);
    }

    @Override
    public void run() {
        String urlToDownload = intent.getStringExtra("url");
        String fileToDownload = intent.getStringExtra("file");
        String hash = intent.getStringExtra("hash");
        PreferenceRepository preferences = preferenceRepository.get();

        try {

            if (urlToDownload == null || fileToDownload == null || hash == null) {
                throw new IllegalStateException("urlToDownload = " + urlToDownload
                        + " fileToDownload  = " + fileToDownload
                        + " hash = " + hash);
            }

            File outputFile = downloadFile(fileToDownload, urlToDownload);

            boolean checkSum = hash.equalsIgnoreCase(crc32(outputFile));

            if (checkSum) {

                preferences.setStringPreference("LastUpdateResult",
                        context.getString(R.string.update_installed));

                if (fileToDownload.contains("InviZible")) {
                    allowSendBroadcastAfterUpdate = false;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !getApp(context).isAppForeground()) {
                        //Required for androidQ because even if the service is in the foreground we cannot start an activity if no activity is visible
                        preferences.setStringPreference("RequiredAppUpdateForQ", outputFile.getCanonicalPath());
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        installApkForNougatAndHigher(outputFile);
                    } else {
                        installApkLowerNougat(outputFile);
                    }

                    makeDelay(3);
                }

            } else {
                preferences.setStringPreference("LastUpdateResult", context.getString(R.string.update_fault));
                preferences.setStringPreference("UpdateResultMessage", context.getString(R.string.update_fault));
                FileManager.deleteFile(context, cacheDir, fileToDownload, "ignored");
                Log.e(LOG_TAG, "UpdateService file hashes mismatch " + fileToDownload);
            }

        } catch (Exception e) {
            preferences.setStringPreference("LastUpdateResult", context.getString(R.string.update_fault));
            preferences.setStringPreference("UpdateResultMessage", context.getString(R.string.update_fault));
            Log.e(LOG_TAG, "UpdateService failed to download file " + urlToDownload + " " + e.getMessage());
        } finally {
            updateService.sparseArray.delete(serviceStartId);
            if (updateService.currentNotificationId.get() - 1 == UPDATE_CHANNEL_NOTIFICATION_ID) {
                updateService.stopForeground(true);
                updateService.notificationManager.cancel(notificationId);
                sendUpdateResultBroadcast();
                updateService.stopSelf();
            } else {
                updateService.notificationManager.cancel(notificationId);
                updateService.currentNotificationId.getAndDecrement();
            }

        }
    }

    private File downloadFile(String fileToDownload, String urlToDownload) throws IOException {
        long range = 0;

        String path = cacheDir + "/" + fileToDownload;
        File outputFile = new File(path);

        if (outputFile.isFile()) {
            range = outputFile.length();
        } else {
            removeOldApkFileFromPrevUpdate(cacheDir);
            //noinspection ResultOfMethodCallIgnored
            outputFile.createNewFile();
        }


        Proxy proxy = null;
        if (ModulesStatus.getInstance().getTorState() == RUNNING) {
            proxy = new Proxy(
                    Proxy.Type.SOCKS,
                    new InetSocketAddress(
                            LOOPBACK_ADDRESS,
                            Integer.parseInt(pathVars.getTorSOCKSPort()))
            );
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

        con.setConnectTimeout(1000 * CONNECT_TIMEOUT);
        con.setReadTimeout(1000 * READ_TIMEOUT);
        con.setRequestProperty("User-Agent", TOR_BROWSER_USER_AGENT);

        if (range != 0) {
            con.setRequestProperty("Range", "bytes=" + range + "-");
        }

        long fileLength = con.getContentLength();

        try (InputStream input = new BufferedInputStream(con.getInputStream());
             OutputStream output = new FileOutputStream(path, true)) {
            byte[] data = new byte[1024];
            int count;
            int percent = 0;
            while ((count = input.read(data)) != -1) {
                range += count;

                if (Thread.currentThread().isInterrupted()) {
                    Log.w(LOG_TAG, "Download was interrupted by user " + fileToDownload);
                    break;
                }


                int currentPercent = (int) (range * 100 / fileLength);
                if (currentPercent - percent >= 5) {
                    percent = currentPercent;
                    updateNotification(fileToDownload, percent);
                }

                output.write(data, 0, count);
            }
        } finally {
            con.disconnect();
        }

        return outputFile;
    }

    /*private boolean isActivityActive() {

        App app = App.Companion.getInstance();

        WeakReference<Activity> activityWeakReference = app.getCurrentActivity();
        if (activityWeakReference == null) {
            return false;
        }

        Activity activity = activityWeakReference.get();
        if (activity == null) {
            return false;
        }

        return !activity.isFinishing();
    }*/

    private void installApkForNougatAndHigher(File outputFile) {
        Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", outputFile);
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(apkUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager packageManager = context.getPackageManager();
        if (packageManager != null && intent.resolveActivity(packageManager) != null) {
            updateService.startActivity(intent);
        }
    }

    private void installApkLowerNougat(File outputFile) {
        Uri apkUri = Uri.fromFile(outputFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager packageManager = context.getPackageManager();
        if (packageManager != null && intent.resolveActivity(packageManager) != null) {
            updateService.startActivity(intent);
        }
    }

    private void removeOldApkFileFromPrevUpdate(String dirPath) {

        try {
            File dir = new File(dirPath);

            if (dir.listFiles() == null) {
                return;
            }

            for (File file : Objects.requireNonNull(dir.listFiles())) {
                if (file.getName().contains("InviZible")) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to remove old InviZible.apk file during update" + e.getMessage() + " " + e.getCause());
        }
    }

    private void sendUpdateResultBroadcast() {
        if (allowSendBroadcastAfterUpdate) {
            makeDelay(5);

            Intent intent = new Intent(UPDATE_RESULT);
            intent.putExtra("Mark", TOP_FRAGMENT_MARK);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException ignored) {
        }
    }

    private String crc32(File file) {
        CRC32 crc = new CRC32();

        try (InputStream is = new FileInputStream(file);
             ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            byte[] readBuffer = new byte[4 * 1024];
            int read;
            while ((read = is.read(readBuffer)) != -1) {
                bout.write(readBuffer, 0, read);
            }

            crc.update(bout.toByteArray());

            return String.format("%08X", crc.getValue());
        } catch (IOException e) {
            Log.e(LOG_TAG, "crc32() Exception while getting FileInputStream " + e.getMessage());
        }

        return null;
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private void updateNotification(String fileToDownload, int percent) {
        String ticker = context.getString(R.string.update_notification);
        String text = context.getString(R.string.update_notification) +
                " " + fileToDownload;

        Intent notificationIntent = new Intent(updateService, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        Intent stopDownloadIntent = new Intent(updateService, UpdateService.class);
        stopDownloadIntent.setAction(STOP_DOWNLOAD_ACTION);
        stopDownloadIntent.putExtra("ServiceStartId", serviceStartId);

        PendingIntent stopDownloadPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            stopDownloadPendingIntent = PendingIntent.getService(
                    updateService,
                    notificationId,
                    stopDownloadIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            stopDownloadPendingIntent = PendingIntent.getService(
                    updateService,
                    notificationId,
                    stopDownloadIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        PendingIntent contentIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            contentIntent = PendingIntent.getActivity(
                    updateService,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            contentIntent = PendingIntent.getActivity(
                    updateService,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(updateService, UPDATE_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_update)
                .setTicker(ticker)
                .setContentTitle("")
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setWhen(startTime)
                .setUsesChronometer(true)
                .setChannelId(UPDATE_CHANNEL_ID)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(R.drawable.ic_stop, context.getText(R.string.cancel_download), stopDownloadPendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(Notification.CATEGORY_PROGRESS);
        }

        int PROGRESS_MAX = 100;
        builder.setProgress(PROGRESS_MAX, percent, false);

        Notification notification = builder.build();

        synchronized (updateService) {
            updateService.notificationManager.notify(notificationId, notification);
        }
    }
}
