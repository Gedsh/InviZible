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
import pan.alexander.tordnscrypt.settings.PathVars;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class ModulesService extends Service {
    public static final String actionDismissNotification= "pan.alexander.tordnscrypt.action.DISMISS_NOTIFICATION";
    public static final int DEFAULT_NOTIFICATION_ID = 101;

    static final String actionStartDnsCrypt = "pan.alexander.tordnscrypt.action.START_DNSCRYPT";
    static final String actionStartTor = "pan.alexander.tordnscrypt.action.START_TOR";
    static final String actionStartITPD = "pan.alexander.tordnscrypt.action.START_ITPD";
    static final String actionStopDnsCrypt = "pan.alexander.tordnscrypt.action.STOP_DNSCRYPT";
    static final String actionStopTor = "pan.alexander.tordnscrypt.action.STOP_TOR";
    static final String actionStopITPD = "pan.alexander.tordnscrypt.action.STOP_ITPD";
    static final String actionRestartDnsCrypt = "pan.alexander.tordnscrypt.action.RESTART_DNSCRYPT";
    static final String actionRestartTor = "pan.alexander.tordnscrypt.action.RESTART_TOR";
    static final String actionRestartITPD = "pan.alexander.tordnscrypt.action.RESTART_ITPD";
    static final String actionUpdateModulesStatus = "pan.alexander.tordnscrypt.action.UPDATE_MODULES_STATUS";
    static final String actionRecoverService= "pan.alexander.tordnscrypt.action.RECOVER_SERVICE";

    static final String DNSCRYPT_KEYWORD = "checkDNSRunning";
    static final String TOR_KEYWORD = "checkTrRunning";
    static final String ITPD_KEYWORD = "checkITPDRunning";

    private static PowerManager.WakeLock wakeLock = null;

    private PathVars pathVars;
    private Handler mHandler;
    private NotificationManager notificationManager;

    private Timer checkModulesThreadsTimer;
    private ModulesStateLoop checkModulesStateTask;
    private ModulesStatus modulesStatus;
    private ModulesKiller modulesKiller;

    public ModulesService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        pathVars = new PathVars(this);
        mHandler = new Handler();

        modulesKiller = new ModulesKiller(this, pathVars);

        modulesStatus = ModulesStatus.getInstance();

        startModulesThreadsTimer();

        startPowerWakelock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

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
                startDNSCrypt();
                break;
            case actionStartTor:
                startTor();
                break;
            case actionStartITPD:
                startITPD();
                break;
            case actionStopDnsCrypt:
                stopDNSCrypt();
                break;
            case actionStopTor:
                stopTor();
                break;
            case actionStopITPD:
                stopITPD();
                break;
            case actionRestartDnsCrypt:
                restartDNSCrypt();
                break;
            case actionRestartTor:
                restartTor();
                break;
            case actionRestartITPD:
                restartITPD();
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

    private void startDNSCrypt() {
        try {
            modulesStatus.setDnsCryptState(STARTING);

            if (!modulesStatus.isUseModulesWithRoot()) {
                Thread previousDnsCryptThread = modulesKiller.getDnsCryptThread();

                try {
                    if (previousDnsCryptThread != null && previousDnsCryptThread.isAlive()) {
                        Log.w(LOG_TAG, "ModulesService previous DNSCrypt thread is alive! Try interrupt!");
                        previousDnsCryptThread.interrupt();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "ModulesService previous DNSCrypt thread interrupt exception "
                            + e.getMessage() + e.getCause() +"\nStop service");
                    System.exit(0);
                }

            }

            ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(getApplicationContext(), mHandler, pathVars);
            Thread dnsCryptThread = new Thread(modulesStarterHelper.getDNSCryptStarterRunnable());
            dnsCryptThread.setDaemon(false);
            try {
                dnsCryptThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                Log.e(LOG_TAG, "ModulesService startDNSCrypt exception " + e.getMessage() + " " + e.getCause());
            }
            dnsCryptThread.start();

            if (!modulesStatus.isUseModulesWithRoot()) {
                modulesKiller.setDnsCryptThread(dnsCryptThread);
            }

            if (checkModulesStateTask != null && !modulesStatus.isUseModulesWithRoot()) {
                checkModulesStateTask.setDnsCryptThread(dnsCryptThread);
            }

            if (modulesStatus.isUseModulesWithRoot() || dnsCryptThread.isAlive()) {
                modulesStatus.setDnsCryptState(RUNNING);
            } else {
                modulesStatus.setDnsCryptState(STOPPED);
            }


        } catch (Exception e) {
            Log.e(LOG_TAG, "DnsCrypt was unable to startRefreshModulesStatus: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startTor() {
        try {
            modulesStatus.setTorState(STARTING);

            if (!modulesStatus.isUseModulesWithRoot()) {
                Thread previousTorThread = modulesKiller.getTorThread();

                try {
                    if (previousTorThread != null && previousTorThread.isAlive()) {
                        Log.w(LOG_TAG, "ModulesService previous Tor thread is alive! Try interrupt!");
                        previousTorThread.interrupt();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "ModulesService previous Tor thread interrupt exception "
                            + e.getMessage() + e.getCause() +"\nStop service");
                    System.exit(0);
                }

            }

            ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(getApplicationContext(), mHandler, pathVars);
            Thread torThread = new Thread(modulesStarterHelper.getTorStarterRunnable());
            torThread.setDaemon(false);
            try {
                torThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                Log.e(LOG_TAG, "ModulesService startTor exception " + e.getMessage() + " " + e.getCause());
            }
            torThread.start();

            if (!modulesStatus.isUseModulesWithRoot()) {
                modulesKiller.setTorThread(torThread);
            }

            if (checkModulesStateTask != null && !modulesStatus.isUseModulesWithRoot()) {
                checkModulesStateTask.setTorThread(torThread);
            }

            if (modulesStatus.isUseModulesWithRoot() || torThread.isAlive()) {
                modulesStatus.setTorState(RUNNING);
            } else {
                modulesStatus.setTorState(STOPPED);
            }


        } catch (Exception e) {
            Log.e(LOG_TAG, "Tor was unable to startRefreshModulesStatus: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

    }

    private void startITPD() {
        try {
            modulesStatus.setItpdState(STARTING);

            if (!modulesStatus.isUseModulesWithRoot()) {
                Thread previousITPDThread = modulesKiller.getItpdThread();

                try {
                    if (previousITPDThread != null && previousITPDThread.isAlive()) {
                        Log.w(LOG_TAG, "ModulesService previous ITPD thread is alive! Try interrupt!");
                        previousITPDThread.interrupt();
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "ModulesService previous ITPD thread interrupt exception "
                            + e.getMessage() + e.getCause() +"\nStop service");
                    System.exit(0);
                }

            }

            ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(getApplicationContext(), mHandler, pathVars);
            Thread itpdThread = new Thread(modulesStarterHelper.getITPDStarterRunnable());
            itpdThread.setDaemon(false);
            try {
                itpdThread.setPriority(Thread.NORM_PRIORITY);
            } catch (SecurityException e) {
                Log.e(LOG_TAG, "ModulesService startITPD exception " + e.getMessage() + " " + e.getCause());
            }
            itpdThread.start();

            if (!modulesStatus.isUseModulesWithRoot()) {
                modulesKiller.setItpdThread(itpdThread);
            }

            if (checkModulesStateTask != null && !modulesStatus.isUseModulesWithRoot()) {
                checkModulesStateTask.setItpdThread(itpdThread);
            }

            if (modulesStatus.isUseModulesWithRoot() || itpdThread.isAlive()) {
                modulesStatus.setItpdState(RUNNING);
            } else {
                modulesStatus.setItpdState(STOPPED);
            }


        } catch (Exception e) {
            Log.e(LOG_TAG, "I2PD was unable to startRefreshModulesStatus: " + e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopDNSCrypt() {
        new Thread(modulesKiller.getDNSCryptKillerRunnable()).start();
    }

    private void stopTor() {
        new Thread(modulesKiller.getTorKillerRunnable()).start();
    }

    private void stopITPD() {
        new Thread(modulesKiller.getITPDKillerRunnable()).start();
    }

    private void restartDNSCrypt() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    modulesStatus.setDnsCryptState(RESTARTING);

                    Thread killerThread = new Thread(modulesKiller.getDNSCryptKillerRunnable());
                    killerThread.start();
                    killerThread.join();

                    makeDelay();

                    if (modulesStatus.getDnsCryptState() != RUNNING) {
                        startDNSCrypt();
                    }

                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "ModulesService restartDNSCrypt join interrupted!");
                }

            }
        }).start();
    }

    private void restartTor() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    modulesStatus.setTorState(RESTARTING);

                    Thread killerThread = new Thread(modulesKiller.getTorKillerRunnable());
                    killerThread.start();
                    killerThread.join();

                    makeDelay();

                    if (modulesStatus.getTorState() != RUNNING) {
                        startTor();
                    }

                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "ModulesService restartTor join interrupted!");
                }

            }
        }).start();
    }

    private void restartITPD() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    modulesStatus.setItpdState(RESTARTING);

                    Thread killerThread = new Thread(modulesKiller.getITPDKillerRunnable());
                    killerThread.start();
                    killerThread.join();

                    makeDelay();

                    if (modulesStatus.getItpdState() != RUNNING) {
                        startITPD();
                    }

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
        checkModulesStateTask = new ModulesStateLoop(this);
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
