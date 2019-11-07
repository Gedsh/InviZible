package pan.alexander.tordnscrypt.utils.modulesStarter;
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
import android.support.v4.app.NotificationCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesStatus;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.UPDATED;

public class ModulesStarterService extends Service {
    PathVars pathVars;
    Handler mHandler;
    public static final String actionStartDnsCrypt = "pan.alexander.tordnscrypt.action.START_DNSCRYPT";
    public static final String actionStartTor = "pan.alexander.tordnscrypt.action.START_TOR";
    public static final String actionStartITPD = "pan.alexander.tordnscrypt.action.START_ITPD";
    public static final String actionDismissNotification= "pan.alexander.tordnscrypt.action.DISMISS_NOTIFICATION";
    public static final String actionRecoverService= "pan.alexander.tordnscrypt.action.RECOVER_SERVICE";
    public final String ANDROID_CHANNEL_ID = "InviZible";
    private NotificationManager notificationManager;
    public static final int DEFAULT_NOTIFICATION_ID = 101;
    private static PowerManager.WakeLock wakeLock = null;

    private Thread dnsCryptThread;
    private Thread torThread;
    private Thread itpdThread;

    private Timer checkModulesThreadsTimer;
    private ModulesStatus modulesStatus;

    public ModulesStarterService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        pathVars = new PathVars(getApplicationContext());

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        modulesStatus = ModulesStatus.getInstance();

        if (!modulesStatus.isUseModulesWithRoot()) {
            startModulesThreadsTimer();
        }

        startPowerWakelock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        mHandler = new Handler();

        String action = intent.getAction();

        boolean showNotification = intent.getBooleanExtra("showNotification",true);

        if (action == null) {
            stopService(startId);
            return START_NOT_STICKY;
        }


        if (showNotification) {
            sendNotification(getText(R.string.notification_text).toString(),getString(R.string.app_name),getText(R.string.notification_text).toString());
        }


        switch (action) {
            case actionStartDnsCrypt:
                startDNSCrypt(startId);
                break;
            case actionStartTor:
                startTor(startId);
                break;
            case actionStartITPD:
                startITPD(startId);
                break;
            case actionDismissNotification:
                dismissNotification(startId);
                break;
            case actionRecoverService:
                setAllModulesStateStopped();
                break;
        }

        return START_REDELIVER_INTENT;

    }

    private void startDNSCrypt(int startId) {
        try {
            dnsCryptThread = new Thread(ModulesStarterHelper.getDNSCryptStarterRunnable
                    (getApplicationContext(), pathVars, mHandler, ModulesStatus.getInstance()
                            .isUseModulesWithRoot()));
            dnsCryptThread.setDaemon(false);
            try {
                //new experiment
                dnsCryptThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            dnsCryptThread.start();

            if (modulesStatus.isUseModulesWithRoot()) {
                stopService(startId);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "DnsCrypt was unable to startRefreshModulesStatus: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startTor(int startId) {
        try {
            torThread = new Thread(ModulesStarterHelper.getTorStarterRunnable
                    (getApplicationContext(), pathVars, mHandler, ModulesStatus.getInstance()
                            .isUseModulesWithRoot()));
            torThread.setDaemon(false);
            try {
                //new experiment
                torThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            torThread.start();

            if (modulesStatus.isUseModulesWithRoot()) {
                stopService(startId);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Tor was unable to startRefreshModulesStatus: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

    }

    private void startITPD(int startId) {
        try {
            itpdThread = new Thread(ModulesStarterHelper.getITPDStarterRunnable
                    (getApplicationContext(), pathVars, mHandler, ModulesStatus.getInstance()
                            .isUseModulesWithRoot()));
            itpdThread.setDaemon(false);
            try {
                //new experiment
                itpdThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            itpdThread.start();

            if (modulesStatus.isUseModulesWithRoot()) {
                stopService(startId);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "I2PD was unable to startRefreshModulesStatus: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void dismissNotification(int startId) {
        notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
        stopForeground(true);
        stopSelf(startId);
    }

    private void startModulesThreadsTimer() {
        checkModulesThreadsTimer = new Timer();
        checkModulesThreadsTimer.schedule(task, 1, 1000);
    }

    private void stopModulesThreadsTimer() {
        if (checkModulesThreadsTimer != null) {
            checkModulesThreadsTimer.purge();
            checkModulesThreadsTimer.cancel();
            checkModulesThreadsTimer = null;
        }
    }

    @SuppressLint({"InvalidWakeLockTag", "WakelockTimeout"})
    private void startPowerWakelock() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (sharedPreferences.getBoolean("swWakelock", false)) {
            final String TAG = "AudioMix";
            if (wakeLock == null) {
                wakeLock = ((PowerManager) Objects.requireNonNull(getSystemService(Context.POWER_SERVICE))).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
                wakeLock.acquire();
            }
        }
    }

    private void stopPowerWakelock() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void sendNotification(String Ticker, String Title, String Text) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel
                    (ANDROID_CHANNEL_ID, "NOTIFICATION_CHANNEL_INVIZIBLE", NotificationManager.IMPORTANCE_LOW);
            notificationChannel.setDescription("Protect InviZible Pro");
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            assert notificationManager != null;
            notificationManager.createNotificationChannel(notificationChannel);
        }

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
        stopPowerWakelock();

        stopModulesThreadsTimer();

        super.onDestroy();
    }

    private TimerTask task = new TimerTask() {
        @Override
        public void run() {
            if (modulesStatus == null) {
                return;
            }

            if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
                if (modulesStatus.getDnsCryptState() == STOPPED
                        || modulesStatus.getDnsCryptState() == RESTARTED
                        || modulesStatus.getDnsCryptState() == UPDATED) {

                    modulesStatus.setDnsCryptState(ModuleState.RUNNING);
                }
            } else {
                if (modulesStatus.getDnsCryptState() == RUNNING
                        || modulesStatus.getDnsCryptState() == RESTARTED
                        || modulesStatus.getDnsCryptState() == UPDATED) {

                    modulesStatus.setDnsCryptState(STOPPED);
                }
            }

            if (torThread != null && torThread.isAlive()) {
                if (modulesStatus.getTorState() == STOPPED
                        || modulesStatus.getTorState() == RESTARTED
                        || modulesStatus.getTorState() == UPDATED) {

                    modulesStatus.setTorState(ModuleState.RUNNING);
                }
            } else {
                if (modulesStatus.getTorState() == RUNNING
                        || modulesStatus.getTorState() == RESTARTED
                        || modulesStatus.getTorState() == UPDATED) {

                    modulesStatus.setTorState(STOPPED);
                }
            }

            if (itpdThread != null && itpdThread.isAlive()) {
                if (modulesStatus.getItpdState() == STOPPED
                        || modulesStatus.getItpdState() == RESTARTED
                        || modulesStatus.getItpdState() == UPDATED) {

                    modulesStatus.setItpdState(ModuleState.RUNNING);
                }
            } else {
                if (modulesStatus.getItpdState() == RUNNING
                        || modulesStatus.getItpdState() == RESTARTED
                        || modulesStatus.getItpdState() == UPDATED) {

                    modulesStatus.setItpdState(STOPPED);
                }
            }

            Log.i(LOG_TAG, "DNSCrypt is " + modulesStatus.getDnsCryptState() +
                    " Tor is " + modulesStatus.getTorState() + " I2P is " + modulesStatus.getItpdState());
        }
    };

    private void stopService(int startID) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
            stopForeground(true);
        }

        stopSelf(startID);
    }

    private void setAllModulesStateStopped() {
        modulesStatus.setDnsCryptState(STOPPED);
        modulesStatus.setTorState(STOPPED);
        modulesStatus.setItpdState(STOPPED);
    }
}
