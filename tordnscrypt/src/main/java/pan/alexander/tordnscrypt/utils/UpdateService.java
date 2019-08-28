package pan.alexander.tordnscrypt.utils;
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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
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
import java.net.URL;
import java.util.Objects;
import java.util.zip.CRC32;

import javax.net.ssl.HttpsURLConnection;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class UpdateService extends Service {
    private final String ANDROID_CHANNEL_ID = "InviZible";
    private NotificationManager notificationManager;
    private static final int DEFAULT_NOTIFICATION_ID = 103;
    private static final int READTIMEOUT = 60;
    private static final int CONNECTTIMEOUT = 60;
    private String appDataDir;
    private String storageDir;
    private String busyboxPath;
    private String dnscryptPath;
    private String torPath;
    private String itpdPath;
    private String iptablesPath;
    public static final String DOWNLOAD_ACTION = "pan.alexander.tordnscrypt.DOWNLOAD_ACTION";
    private static final String STOP_DOWNLOAD_ACTION = "pan.alexander.tordnscrypt.STOP_DOWNLOAD_ACTION";
    private int currentNotificationId = DEFAULT_NOTIFICATION_ID -1;
    private SparseArray<DownloadThread> sparseArray;

    public UpdateService() {
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        PathVars pathVars = new PathVars(getApplicationContext());
        sparseArray = new SparseArray<>();
        appDataDir = pathVars.appDataDir;
        storageDir = pathVars.storageDir;
        busyboxPath = pathVars.busyboxPath;
        dnscryptPath = pathVars.dnscryptPath;
        torPath = pathVars.torPath;
        itpdPath = pathVars.itpdPath;
        iptablesPath = pathVars.iptablesPath;
        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action == null) {
            stopSelf();
        } else if (action.equals(DOWNLOAD_ACTION)) {
            currentNotificationId +=1;
            DownloadThread downloadThread = new DownloadThread(intent,startId,currentNotificationId);
            sparseArray.put(startId,downloadThread);
            downloadThread.startDownloadThread();
        } else if (action.equals(STOP_DOWNLOAD_ACTION)) {
            int serviceId = intent.getIntExtra("ServiceStartId",0);
            DownloadThread downloadThread = sparseArray.get(serviceId);
            if (downloadThread!=null){
                downloadThread.sendNotification(getText(R.string.update_interrupt_notification).toString(),getString(R.string.app_name),getText(R.string.update_interrupt_notification).toString());
                downloadThread.interruptDownload = true;
                sparseArray.delete(serviceId);
            }
        }
        return START_NOT_STICKY;
    }



    private class DownloadThread {
        Intent intent;
        int serviceStartId;
        int notificationId;
        long startTime = System.currentTimeMillis();
        boolean interruptDownload = false;

        DownloadThread(Intent intent, int serviceStartId, int notificationId) {
            this.intent = intent;
            this.serviceStartId = serviceStartId;
            this.notificationId = notificationId;
        }

        void startDownloadThread() {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                NotificationChannel notificationChannel = new NotificationChannel
                        (ANDROID_CHANNEL_ID, "NOTIFICATION_CHANNEL_INVIZIBLE", NotificationManager.IMPORTANCE_LOW);
                notificationChannel.setDescription("Update InviZible Pro");
                notificationChannel.enableLights(false);
                notificationChannel.enableVibration(false);
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                assert notificationManager != null;
                notificationManager.createNotificationChannel(notificationChannel);

                sendNotification(getText(R.string.update_notification).toString(),getString(R.string.app_name),getText(R.string.update_notification).toString());

            } else {
                sendNotification(getText(R.string.update_notification).toString(),getString(R.string.app_name),getText(R.string.update_notification).toString());
            }

            Thread downloadThread = new Thread(downloadUpdateWork);
            downloadThread.setDaemon(false);
            downloadThread.start();


        }

        Runnable downloadUpdateWork = new Runnable() {
            @Override
            public void run() {
                String urlToDownload = intent.getStringExtra("url");
                String fileToDownload = intent.getStringExtra("file");
                String hash = intent.getStringExtra("hash");
                try {

                    //create url and connect
                    URL url = new URL(urlToDownload);
                    HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
                    con.setConnectTimeout(1000 * CONNECTTIMEOUT);
                    con.setReadTimeout(1000 * READTIMEOUT);
                    con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36");
                    con.connect();

                    // this will be useful so that you can show a typical 0-100% progress bar
                    int fileLength = con.getContentLength();

                    // download the file
                    InputStream input = new BufferedInputStream(con.getInputStream());

                    File f = new File(storageDir + "/download");

                    if (f.mkdirs() && f.setReadable(true) && f.setWritable(true))
                        Log.i(LOG_TAG, "download dir created");

                    String path = storageDir + "/download/" + fileToDownload;
                    OutputStream output = new FileOutputStream(path);

                    byte[] data = new byte[1024];
                    long total = 0;
                    int count;
                    int percent = 0;
                    while ((count = input.read(data)) != -1) {
                        total += count;

                        if (interruptDownload){
                            Log.w(LOG_TAG,"Download was interrupted by user " + fileToDownload);
                            break;
                        }


                        int currentPercent = (int) (total * 100 / fileLength);
                        if (currentPercent-percent >= 5) {
                            percent = currentPercent;
                            String notification = getText(R.string.update_notification).toString() +
                                    " " + fileToDownload;

                            updateNotification(getText(R.string.update_notification).toString(), getString(R.string.app_name), notification, percent);
                        }

                        output.write(data, 0, count);
                    }

                    // close streams
                    output.flush();
                    output.close();
                    input.close();

                    if (Objects.requireNonNull(crc32(new File(path))).equalsIgnoreCase(hash)){
                        new PrefManager(getApplicationContext()).setStrPref("LastUpdateResult",
                                getApplicationContext().getText(R.string.update_installed).toString());

                        stopRunningModules(fileToDownload);

                        if (fileToDownload.contains("InviZible")){
                            File file = new File(path);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                Uri apkUri = FileProvider.getUriForFile(getApplicationContext(), getApplicationContext().getPackageName() + ".fileprovider", file);
                                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                                intent.setData(apkUri);
                                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                getApplicationContext().startActivity(intent);
                            } else {
                                Uri apkUri = Uri.fromFile(file);
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                getApplicationContext().startActivity(intent);
                            }
                        } else {
                            FileOperations.moveBinaryFile(getApplicationContext(),storageDir + "/download/",fileToDownload,appDataDir+"/app_bin/","executable");
                            runPreviousStoppedModules(fileToDownload);
                        }

                    } else {
                        FileOperations.deleteFile(getApplicationContext(),storageDir + "/download/", fileToDownload,"ignored");
                        Log.w(LOG_TAG,"UpdateService file hashes mismatch " + fileToDownload);
                    }

                } catch (Exception e) {
                    new PrefManager(getApplicationContext()).setStrPref("LastUpdateResult",
                            getApplicationContext().getText(R.string.update_check_fault).toString());
                    Log.e(LOG_TAG,"UpdateService failed to download file " + urlToDownload + " " + e.getMessage());
                } finally {
                    sparseArray.delete(serviceStartId);
                    if (currentNotificationId == DEFAULT_NOTIFICATION_ID) {
                        stopForeground(true);
                        notificationManager.cancel(notificationId);
                        stopSelf();
                    } else {
                        notificationManager.cancel(notificationId);
                        currentNotificationId -=1;
                    }

                }

            }
        };

        void sendNotification(String Ticker, String Title, String Text) {

            //These three lines makes Notification to open main activity after clicking on it
            Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
            notificationIntent.setAction(Intent.ACTION_MAIN);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            Intent stopDownloadIntent = new Intent(getApplicationContext(), UpdateService.class);
            stopDownloadIntent.setAction(STOP_DOWNLOAD_ACTION);
            stopDownloadIntent.putExtra("ServiceStartId",serviceStartId);
            PendingIntent stopDownloadPendingIntent = PendingIntent.getService(getApplicationContext(), notificationId, stopDownloadIntent,PendingIntent.FLAG_UPDATE_CURRENT);

            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),ANDROID_CHANNEL_ID);
            builder.setContentIntent(contentIntent)
                    .setOngoing(true)   //Can't be swiped out
                    .setSmallIcon(R.drawable.ic_update)
                    .setTicker(Ticker)
                    .setContentTitle(Title) //Заголовок
                    .setContentText(Text) // Текст уведомления
                    .setOnlyAlertOnce(true)
                    .setWhen(startTime)
                    .setUsesChronometer(true)
                    .addAction(R.drawable.ic_stop,getText(R.string.cancel_download),stopDownloadPendingIntent);

            Notification notification = builder.build();

            startForeground(notificationId, notification);
        }

        void updateNotification(String Ticker, String Title, String Text, int percent) {

            //These three lines makes Notification to open main activity after clicking on it
            Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
            notificationIntent.setAction(Intent.ACTION_MAIN);
            notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Intent stopDownloadIntent = new Intent(getApplicationContext(), UpdateService.class);
            stopDownloadIntent.setAction(STOP_DOWNLOAD_ACTION);
            stopDownloadIntent.putExtra("ServiceStartId",serviceStartId);
            PendingIntent stopDownloadPendingIntent = PendingIntent.getService(getApplicationContext(), notificationId, stopDownloadIntent,PendingIntent.FLAG_UPDATE_CURRENT);



            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(),ANDROID_CHANNEL_ID);
            builder.setContentIntent(contentIntent)
                    .setOngoing(true)   //Can't be swiped out
                    .setSmallIcon(R.drawable.ic_update)
                    .setTicker(Ticker)
                    .setContentTitle(Title) //Заголовок
                    .setContentText(Text) // Текст уведомления
                    .setOnlyAlertOnce(true)
                    .setWhen(startTime-(System.currentTimeMillis()-startTime))
                    .setUsesChronometer(true)
                    .addAction(R.drawable.ic_stop,getText(R.string.cancel_download),stopDownloadPendingIntent);

            int PROGRESS_MAX = 100;
            builder.setProgress(PROGRESS_MAX, percent, false);

            Notification notification = builder.build();
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
                if(read == -1) {
                    break;
                }
                bout.write(readBuffer, 0, read);
            } while(true);

            crc.update(bout.toByteArray());

            is.close();
            bout.close();

            return String.format("%08X", crc.getValue());
        } catch (IOException e) {
            Log.e(LOG_TAG, "crc32() IOException " + e.getMessage());
        }
        return null;
    }

    void stopRunningModules(String fileName) {
        String[] commandsStop = new String[0];
        if (fileName.contains("dnscrypt-proxy")) {
            boolean dnsCryptRunning = new PrefManager(getApplicationContext()).getBoolPref("DNSCrypt Running");
            if (dnsCryptRunning) {
                commandsStop = new String[]{
                        busyboxPath + "killall dnscrypt-proxy"
                };
            }
        } else if (fileName.contains("tor")){
            boolean torRunning = new PrefManager(getApplicationContext()).getBoolPref("Tor Running");
            if (torRunning) {
                while (sparseArray.size() > 1) {
                    try {
                        Thread.sleep(5000);
                    } catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
                commandsStop = new String[]{
                        busyboxPath + "killall tor"
                };
            }
        } else if (fileName.contains("i2pd")){
            boolean itpdRunning = new PrefManager(getApplicationContext()).getBoolPref("I2PD Running");
            if (itpdRunning) {
                commandsStop = new String[]{
                        busyboxPath + "killall i2pd"
                };
            }
        } else if (fileName.contains("InviZible")){
            commandsStop = new String[]{
                    iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                    iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                    iptablesPath + "iptables -F tordnscrypt",
                    iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                    busyboxPath + "sleep 1",
                    busyboxPath + "killall dnscrypt-proxy",
                    busyboxPath + "killall tor",
                    busyboxPath + "killall i2pd"
            };
        }

        if (commandsStop.length!=0) {
            RootCommands rootCommands = new RootCommands(commandsStop);
            Intent intent = new Intent(getApplicationContext(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(getApplicationContext(), intent);

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void runPreviousStoppedModules(String fileName) {
        String[] commandsRun = new String[0];
        if (fileName.contains("dnscrypt-proxy")) {
            boolean dnsCryptRunning = new PrefManager(getApplicationContext()).getBoolPref("DNSCrypt Running");
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean runDNSCryptWithRoot = shPref.getBoolean("swUseModulesRoot",false);
            if (dnsCryptRunning && runDNSCryptWithRoot) {
                commandsRun = new String[]{
                        busyboxPath+ "nohup " + dnscryptPath+" --config "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 &"
                };
            } else if (dnsCryptRunning) {
                runDNSCryptNoRoot();
            }
        } else if (fileName.contains("tor")){
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean rnTorWithRoot = shPref.getBoolean("swUseModulesRoot",false);
            boolean torRunning = new PrefManager(getApplicationContext()).getBoolPref("Tor Running");
            if (torRunning && rnTorWithRoot) {
                commandsRun = new String[]{
                        torPath + " -f " + appDataDir + "/app_data/tor/tor.conf"
                };
            } else if (torRunning) {
                runTorNoRoot();
            }
        } else if (fileName.contains("i2pd")){
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean rnI2PDWithRoot = shPref.getBoolean("swUseModulesRoot",false);
            boolean itpdRunning = new PrefManager(getApplicationContext()).getBoolPref("I2PD Running");
            if (itpdRunning && rnI2PDWithRoot) {
                commandsRun = new String[]{
                        itpdPath + " --conf " + appDataDir + "/app_data/i2pd/i2pd.conf --datadir " + appDataDir + "/i2pd_data &"
                };
            } else if (itpdRunning) {
                runITPDNoRoot();
            }
        }

        if (commandsRun.length!=0) {
            RootCommands rootCommands = new RootCommands(commandsRun);
            Intent intent = new Intent(getApplicationContext(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.SettingsActivityMark);
            RootExecService.performAction(getApplicationContext(), intent);
        }
    }

    private void runDNSCryptNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(getApplicationContext(), NoRootService.class);
        intent.setAction(NoRootService.actionStartDnsCrypt);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(intent);
        } else {
            getApplicationContext().startService(intent);
        }
    }
    private void runTorNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(getApplicationContext(), NoRootService.class);
        intent.setAction(NoRootService.actionStartTor);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(intent);
        } else {
            getApplicationContext().startService(intent);
        }
    }
    private void runITPDNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean showNotification = shPref.getBoolean("swShowNotification",true);
        Intent intent = new Intent(getApplicationContext(), NoRootService.class);
        intent.setAction(NoRootService.actionStartITPD);
        intent.putExtra("showNotification",showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(intent);
        } else {
            getApplicationContext().startService(intent);
        }
    }



}
