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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class NoRootService extends Service {
    PathVars pathVars;
    String dnscryptPath;
    String appDataDir;
    String torPath;
    String itpdPath;
    Handler mHandler;
    public static final String actionStartDnsCrypt = "pan.alexander.tordnscrypt.action.START_DNSCRYPT";
    public static final String actionStartTor = "pan.alexander.tordnscrypt.action.START_TOR";
    public static final String actionStartITPD = "pan.alexander.tordnscrypt.action.START_ITPD";
    public static final String actionDismissNotification= "pan.alexander.tordnscrypt.action.DISMISS_NOTIFICATION";
    public final String ANDROID_CHANNEL_ID = "InviZible";
    private NotificationManager notificationManager;
    public static final int DEFAULT_NOTIFICATION_ID = 101;
    private static PowerManager.WakeLock wakeLock = null;

    public NoRootService() {
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout"})
    @Override
    public void onCreate() {
        super.onCreate();

        pathVars = new PathVars(getApplicationContext());
        appDataDir = pathVars.appDataDir;
        dnscryptPath = pathVars.dnscryptPath;
        torPath = pathVars.torPath;
        itpdPath = pathVars.itpdPath;

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sharedPreferences.getBoolean("swWakelock", false)) {
            final String TAG = "AudioMix";
            if (wakeLock == null) {
                wakeLock = ((PowerManager) Objects.requireNonNull(getSystemService(Context.POWER_SERVICE))).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                wakeLock.acquire();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mHandler = new Handler();

        String action = intent.getAction();
        boolean showNotification = intent.getBooleanExtra("showNotification",false);
        if (action == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel notificationChannel = new NotificationChannel
                    (ANDROID_CHANNEL_ID, "NOTIFICATION_CHANNEL_INVIZIBLE", NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription("Protect InviZible Pro");
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);

            sendNotification(getText(R.string.notification_text).toString(),getString(R.string.app_name),getText(R.string.notification_text).toString());

        } else if (showNotification){
            sendNotification(getText(R.string.notification_text).toString(),getString(R.string.app_name),getText(R.string.notification_text).toString());
        }

        switch (action) {
            case actionStartDnsCrypt:
                try {
                    Thread thread = new Thread(startDNSCrypt);
                    thread.setDaemon(false);
                    try {
                        //new experiment
                        thread.setPriority(Thread.NORM_PRIORITY);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    thread.start();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "DnsCrypt was unable to startRefreshModulesStatus: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }

                break;
            case actionStartTor:
                try {
                    Thread thread = new Thread(startTor);
                    thread.setDaemon(false);
                    try {
                        //new experiment
                        thread.setPriority(Thread.NORM_PRIORITY);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    thread.start();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Tor was unable to startRefreshModulesStatus: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }

                break;
            case actionStartITPD:
                try {
                    Thread thread = new Thread(startITPD);
                    thread.setDaemon(false);
                    try {
                        //new experiment
                        thread.setPriority(Thread.NORM_PRIORITY);
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                    thread.start();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "I2PD was unable to startRefreshModulesStatus: " + e.getMessage());
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }

                break;

            case actionDismissNotification:
                notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                stopForeground(true);
                stopSelf(startId);
                break;
        }

        return START_REDELIVER_INTENT;

    }

    public void sendNotification(String Ticker, String Title, String Text) {

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(R.drawable.ic_visibility_off_white_24dp)
                //.setLargeIcon(BitmapFactory.decodeResource(res, R.drawable.large))   // большая картинка
                .setTicker(Ticker)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                .setWhen(System.currentTimeMillis())
                //new experiment
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true);

        Notification notification = builder.build();

        startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (wakeLock != null) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    Runnable startDNSCrypt = new Runnable() {
        @Override
        public void run() {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


            String dnsCmdString = dnscryptPath+" --config "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml";
            final CommandResult shellResult = Shell.run(dnsCmdString);
            if (!shellResult.isSuccessful()) {
                Log.e(LOG_TAG,"Error DNSCrypt: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(NoRootService.this,"Error DNSCrypt: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };

    Runnable startTor = new Runnable() {
        @Override
        public void run() {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String torCmdString = torPath+" -f "+appDataDir+"/app_data/tor/tor.conf";
            final CommandResult shellResult = Shell.run(torCmdString);
            if (!shellResult.isSuccessful()) {
                Log.e(LOG_TAG,"Error Tor: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(NoRootService.this,"Error Tor: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };

    Runnable startITPD = new Runnable() {
        @Override
        public void run() {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            String itpdCmdString = itpdPath+" --conf "+appDataDir+"/app_data/i2pd/i2pd.conf --datadir "+appDataDir+"/i2pd_data";
            final CommandResult shellResult = Shell.run(itpdCmdString);
            if (!shellResult.isSuccessful()) {
                Log.e(LOG_TAG,"Error ITPD: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(NoRootService.this,"Error ITPD: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };
}
