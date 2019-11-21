package pan.alexander.tordnscrypt.utils.modulesManager;
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
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesStatus;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class ModulesService extends Service {
    public static final String actionDismissNotification= "pan.alexander.tordnscrypt.action.DISMISS_NOTIFICATION";

    static final String actionStartDnsCrypt = "pan.alexander.tordnscrypt.action.START_DNSCRYPT";
    static final String actionStartTor = "pan.alexander.tordnscrypt.action.START_TOR";
    static final String actionStartITPD = "pan.alexander.tordnscrypt.action.START_ITPD";
    static final String actionStopDnsCrypt = "pan.alexander.tordnscrypt.action.STOP_DNSCRYPT";
    static final String actionStopTor = "pan.alexander.tordnscrypt.action.STOP_TOR";
    static final String actionStopITPD = "pan.alexander.tordnscrypt.action.STOP_ITPD";
    static final String actionRestartDnsCrypt = "pan.alexander.tordnscrypt.action.RESTART_DNSCRYPT";
    static final String actionRestartTor = "pan.alexander.tordnscrypt.action.RESTART_TOR";
    static final String actionRestartITPD = "pan.alexander.tordnscrypt.action.RESTART_ITPD";
    static final String actionRecoverService= "pan.alexander.tordnscrypt.action.RECOVER_SERVICE";

    static final String DNSCRYPT_KEYWORD = "checkDNSRunning";
    static final String TOR_KEYWORD = "checkTrRunning";
    static final String ITPD_KEYWORD = "checkITPDRunning";
    public static final int DEFAULT_NOTIFICATION_ID = 101;
    private static PowerManager.WakeLock wakeLock = null;

    private PathVars pathVars;
    private Handler mHandler;
    private NotificationManager notificationManager;

    private Timer checkModulesThreadsTimer;
    private CheckModulesStateTimerTask checkModulesStateTask;
    private ModulesStatus modulesStatus;
    private ModulesKiller modulesKiller;

    public ModulesService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        modulesStatus = ModulesStatus.getInstance();

        if (!modulesStatus.isUseModulesWithRoot()) {
            startModulesThreadsTimer();
        }

        startPowerWakelock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        pathVars = new PathVars(getApplicationContext());
        mHandler = new Handler();

        modulesKiller = new ModulesKiller(this, pathVars);

        String action = intent.getAction();

        boolean showNotification = intent.getBooleanExtra("showNotification",true);

        if (action == null) {
            stopService(startId);
            return START_NOT_STICKY;
        }


        if (showNotification) {
            ServiceNotification notification = new ServiceNotification(this, notificationManager);
            notification.sendNotification(getText(R.string.notification_text).toString(),getString(R.string.app_name),getText(R.string.notification_text).toString());
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
            case actionStopDnsCrypt:
                stopDNSCrypt(startId);
                break;
            case actionStopTor:
                stopTor(startId);
                break;
            case actionStopITPD:
                stopITPD(startId);
                break;
            case actionRestartDnsCrypt:
                restartDNSCrypt(startId);
                break;
            case actionRestartTor:
                restartTor(startId);
                break;
            case actionRestartITPD:
                restartITPD(startId);
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
            ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(getApplicationContext(), mHandler, pathVars);
            Thread dnsCryptThread = new Thread(modulesStarterHelper.getDNSCryptStarterRunnable());
            dnsCryptThread.setDaemon(false);
            try {
                //new experiment
                dnsCryptThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                Log.e(LOG_TAG, "ModulesService startDNSCrypt exception " + e.getMessage() + " " + e.getCause());
            }
            dnsCryptThread.start();

            modulesKiller.setDnsCryptThread(dnsCryptThread);

            if (checkModulesStateTask != null) {
                checkModulesStateTask.setDnsCryptThread(dnsCryptThread);
            }

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
            ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(getApplicationContext(), mHandler, pathVars);
            Thread torThread = new Thread(modulesStarterHelper.getTorStarterRunnable());
            torThread.setDaemon(false);
            try {
                //new experiment
                torThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                Log.e(LOG_TAG, "ModulesService startTor exception " + e.getMessage() + " " + e.getCause());
            }
            torThread.start();

            modulesKiller.setTorThread(torThread);

            if (checkModulesStateTask != null) {
                checkModulesStateTask.setTorThread(torThread);
            }

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
            ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(getApplicationContext(), mHandler, pathVars);
            Thread itpdThread = new Thread(modulesStarterHelper.getITPDStarterRunnable());
            itpdThread.setDaemon(false);
            try {
                //new experiment
                itpdThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                Log.e(LOG_TAG, "ModulesService startITPD exception " + e.getMessage() + " " + e.getCause());
            }
            itpdThread.start();

            modulesKiller.setItpdThread(itpdThread);

            if (checkModulesStateTask != null) {
                checkModulesStateTask.setItpdThread(itpdThread);
            }

            if (modulesStatus.isUseModulesWithRoot()) {
                stopService(startId);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "I2PD was unable to startRefreshModulesStatus: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopDNSCrypt(int startId) {
        new Thread(modulesKiller.getDNSCryptKillerRunnable()).start();

        if (modulesStatus.isUseModulesWithRoot()) {
            stopService(startId);
        }
    }

    private void stopTor(int startId) {
        new Thread(modulesKiller.getTorKillerRunnable()).start();

        if (modulesStatus.isUseModulesWithRoot()) {
            stopService(startId);
        }
    }

    private void stopITPD(int startId) {
        new Thread(modulesKiller.getITPDKillerRunnable()).start();

        if (modulesStatus.isUseModulesWithRoot()) {
            stopService(startId);
        }
    }

    private void restartDNSCrypt(final int startId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    modulesStatus.setDnsCryptState(RESTARTING);

                    Thread killerThread = new Thread(modulesKiller.getDNSCryptKillerRunnable());
                    killerThread.start();
                    killerThread.join();

                    startDNSCrypt(startId);

                    makeDelay();
                    modulesStatus.setDnsCryptState(RESTARTED);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "ModulesService restartDNSCrypt join interrupted!");
                }

            }
        }).start();
    }

    private void restartTor(final int startId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    modulesStatus.setTorState(RESTARTING);

                    Thread killerThread = new Thread(modulesKiller.getTorKillerRunnable());
                    killerThread.start();
                    killerThread.join();

                    startTor(startId);

                    makeDelay();
                    modulesStatus.setTorState(RESTARTED);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "ModulesService restartTor join interrupted!");
                }

            }
        }).start();
    }

    private void restartITPD(final int startId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    modulesStatus.setItpdState(RESTARTING);

                    Thread killerThread = new Thread(modulesKiller.getITPDKillerRunnable());
                    killerThread.start();
                    killerThread.join();

                    startITPD(startId);

                    makeDelay();
                    modulesStatus.setItpdState(RESTARTED);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "ModulesService restartITPD join interrupted!");
                }

            }
        }).start();
    }

    private void dismissNotification(int startId) {
        notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
        stopForeground(true);
        stopSelf(startId);
    }

    private void startModulesThreadsTimer() {
        checkModulesThreadsTimer = new Timer();
        checkModulesStateTask = new CheckModulesStateTimerTask();
        checkModulesThreadsTimer.schedule(checkModulesStateTask, 1, 1000);
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

    private void makeDelay() {
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "ModulesService makeDelay interrupted! " + e.getMessage() + " " + e.getCause());
        }
    }
}
