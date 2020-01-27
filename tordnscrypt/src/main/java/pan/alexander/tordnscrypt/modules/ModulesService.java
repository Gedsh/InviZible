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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
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
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class ModulesService extends Service {
    public static final String actionDismissNotification = "pan.alexander.tordnscrypt.action.DISMISS_NOTIFICATION";
    public static final int DEFAULT_NOTIFICATION_ID = 101;

    public static final String actionStopService = "pan.alexander.tordnscrypt.action.STOP_SERVICE";

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
    static final String actionRecoverService = "pan.alexander.tordnscrypt.action.RECOVER_SERVICE";

    static final String DNSCRYPT_KEYWORD = "checkDNSRunning";
    static final String TOR_KEYWORD = "checkTrRunning";
    static final String ITPD_KEYWORD = "checkITPDRunning";

    private static PowerManager.WakeLock wakeLock = null;

    ModulesBroadcastReceiver modulesBroadcastReceiver;

    private final Handler mHandler = new Handler();
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    private PathVars pathVars;
    private NotificationManager notificationManager;
    private Timer checkModulesThreadsTimer;
    private ModulesStateLoop checkModulesStateTask;
    private ModulesKiller modulesKiller;

    public ModulesService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        pathVars = new PathVars(this);

        modulesKiller = new ModulesKiller(this, pathVars);

        startModulesThreadsTimer();

        startPowerWakelock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        String action = intent.getAction();

        boolean showNotification = intent.getBooleanExtra("showNotification", true);

        if (action == null) {
            stopService(startId);
            return START_NOT_STICKY;
        }


        if (showNotification) {
            ServiceNotification notification = new ServiceNotification(this, notificationManager);
            notification.sendNotification(getText(R.string.notification_text).toString(), getString(R.string.app_name), getText(R.string.notification_text).toString());
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
            case actionStopService:
                stopModulesService();
                break;
        }

        setBroadcastReceiver();

        return START_REDELIVER_INTENT;

    }

    private void startDNSCrypt() {

        if (modulesStatus.getDnsCryptState() == STOPPED) {
            modulesStatus.setDnsCryptState(STARTING);
        }

        if (!modulesStatus.isUseModulesWithRoot()) {
            Thread previousDnsCryptThread = modulesKiller.getDnsCryptThread();

            try {
                if (previousDnsCryptThread != null && previousDnsCryptThread.isAlive()) {
                    Log.w(LOG_TAG, "ModulesService previous DNSCrypt thread is alive! Try stop!");
                    previousDnsCryptThread.interrupt();
                    ModulesKiller.stopDNSCrypt(this);
                    Toast.makeText(this, getText(R.string.please_wait), Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "ModulesService previous DNSCrypt thread interrupt exception "
                        + e.getMessage() + e.getCause() + "\nStop service");
                System.exit(0);
            }

        }

        new Thread(() -> {

            try {

                if (!stopPreviouslyRunningDNSCryptModule()) {
                    return;
                }


                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(ModulesService.this, mHandler, pathVars);
                Thread dnsCryptThread = new Thread(modulesStarterHelper.getDNSCryptStarterRunnable());
                dnsCryptThread.setDaemon(false);
                try {
                    dnsCryptThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    Log.e(LOG_TAG, "ModulesService startDNSCrypt exception " + e.getMessage() + " " + e.getCause());
                }
                dnsCryptThread.start();

                changeDNSCryptStatus(dnsCryptThread);

            } catch (Exception e) {
                Log.e(LOG_TAG, "DnsCrypt was unable to start " + e.getMessage());
                mHandler.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }

        }).start();
    }

    private boolean stopPreviouslyRunningDNSCryptModule() {
        boolean result = false;

        try {
            if (isDnsCryptSavedStateRunning() && modulesStatus.getDnsCryptState() != RESTARTING) {

                Thread killerThread = new Thread(modulesKiller.getDNSCryptKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getDnsCryptState() != RUNNING) {
                    result = true;
                }

            } else {
                result = true;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "DnsCrypt was unable to stop previously running module " + e.getMessage());
        }

        return result;
    }

    private void changeDNSCryptStatus(final Thread dnsCryptThread) {

        makeDelay(2);

        if (modulesStatus == null) {
            return;
        }

        if (modulesStatus.isUseModulesWithRoot() || dnsCryptThread.isAlive()) {
            modulesStatus.setDnsCryptState(RUNNING);

            if (modulesKiller != null && !modulesStatus.isUseModulesWithRoot()) {
                modulesKiller.setDnsCryptThread(dnsCryptThread);
            }

            if (checkModulesStateTask != null && !modulesStatus.isUseModulesWithRoot()) {
                checkModulesStateTask.setDnsCryptThread(dnsCryptThread);
            }
        } else {
            modulesStatus.setDnsCryptState(STOPPED);
        }
    }

    private void startTor() {

        if (modulesStatus.getTorState() == STOPPED) {
            modulesStatus.setTorState(STARTING);
        }

        if (!modulesStatus.isUseModulesWithRoot()) {
            Thread previousTorThread = modulesKiller.getTorThread();

            try {
                if (previousTorThread != null && previousTorThread.isAlive()) {
                    Log.w(LOG_TAG, "ModulesService previous Tor thread is alive! Try stop!");
                    previousTorThread.interrupt();
                    ModulesKiller.stopTor(this);
                    Toast.makeText(this, getText(R.string.please_wait), Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "ModulesService previous Tor thread interrupt exception "
                        + e.getMessage() + e.getCause() + "\nStop service");
                System.exit(0);
            }

        }

        new Thread(() -> {
            try {
                if (!stopPreviouslyRunningTorModule()) {
                    return;
                }

                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(ModulesService.this, mHandler, pathVars);
                Thread torThread = new Thread(modulesStarterHelper.getTorStarterRunnable());
                torThread.setDaemon(false);
                try {
                    torThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    Log.e(LOG_TAG, "ModulesService startTor exception " + e.getMessage() + " " + e.getCause());
                }
                torThread.start();

                changeTorStatus(torThread);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Tor was unable to startRefreshModulesStatus: " + e.getMessage());
                mHandler.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }

        }).start();

    }

    private boolean stopPreviouslyRunningTorModule() {
        boolean result = false;

        try {
            if (isTorSavedStateRunning() && modulesStatus.getTorState() != RESTARTING) {

                Thread killerThread = new Thread(modulesKiller.getTorKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getTorState() != RUNNING) {
                    result = true;
                }

            } else {
                result = true;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Tor was unable to stop previously running module " + e.getMessage());
        }

        return result;
    }

    private void changeTorStatus(final Thread torThread) {

        makeDelay(2);

        if (modulesStatus == null) {
            return;
        }

        if (modulesStatus.isUseModulesWithRoot() || torThread.isAlive()) {
            modulesStatus.setTorState(RUNNING);

            if (modulesKiller != null && !modulesStatus.isUseModulesWithRoot()) {
                modulesKiller.setTorThread(torThread);
            }

            if (checkModulesStateTask != null && !modulesStatus.isUseModulesWithRoot()) {
                checkModulesStateTask.setTorThread(torThread);
            }
        } else {
            modulesStatus.setTorState(STOPPED);
        }
    }

    private void startITPD() {

        if (modulesStatus.getItpdState() == STOPPED) {
            modulesStatus.setItpdState(STARTING);
        }

        if (!modulesStatus.isUseModulesWithRoot()) {
            Thread previousITPDThread = modulesKiller.getItpdThread();

            try {
                if (previousITPDThread != null && previousITPDThread.isAlive()) {
                    Log.w(LOG_TAG, "ModulesService previous ITPD thread is alive! Try stop!");
                    previousITPDThread.interrupt();
                    ModulesKiller.stopITPD(this);
                    Toast.makeText(this, getText(R.string.please_wait), Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "ModulesService previous ITPD thread interrupt exception "
                        + e.getMessage() + e.getCause() + "\nStop service");
                System.exit(0);
            }

        }

        new Thread(() -> {
            try {
                if (!stopPreviouslyRunningITPDModule()) {
                    return;
                }

                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(ModulesService.this, mHandler, pathVars);
                Thread itpdThread = new Thread(modulesStarterHelper.getITPDStarterRunnable());
                itpdThread.setDaemon(false);
                try {
                    itpdThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    Log.e(LOG_TAG, "ModulesService startITPD exception " + e.getMessage() + " " + e.getCause());
                }
                itpdThread.start();

                changeITPDStatus(itpdThread);
            } catch (Exception e) {
                Log.e(LOG_TAG, "I2PD was unable to startRefreshModulesStatus: " + e.getMessage());
                mHandler.post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
            }

        }).start();
    }

    private boolean stopPreviouslyRunningITPDModule() {
        boolean result = false;

        try {
            if (isITPDSavedStateRunning() && modulesStatus.getItpdState() != RESTARTING) {

                Thread killerThread = new Thread(modulesKiller.getITPDKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getItpdState() != RUNNING) {
                    result = true;
                }

            } else {
                result = true;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "I2PD was unable to stop previously running module " + e.getMessage());
        }

        return result;
    }

    private void changeITPDStatus(final Thread itpdThread) {

        makeDelay(3);

        if (modulesStatus == null) {
            return;
        }

        if (modulesStatus.isUseModulesWithRoot() || itpdThread.isAlive()) {
            modulesStatus.setItpdState(RUNNING);

            if (modulesKiller != null && !modulesStatus.isUseModulesWithRoot()) {
                modulesKiller.setItpdThread(itpdThread);
            }

            if (checkModulesStateTask != null && !modulesStatus.isUseModulesWithRoot()) {
                checkModulesStateTask.setItpdThread(itpdThread);
            }
        } else {
            modulesStatus.setItpdState(STOPPED);
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

        if (modulesStatus.getDnsCryptState() != RUNNING) {
            return;
        }


        new Thread(() -> {
            try {
                modulesStatus.setDnsCryptState(RESTARTING);

                Thread killerThread = new Thread(modulesKiller.getDNSCryptKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getDnsCryptState() != RUNNING) {
                    startDNSCrypt();
                }

            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "ModulesService restartDNSCrypt join interrupted!");
            }

        }).start();
    }

    private void restartTor() {

        if (modulesStatus.getTorState() != RUNNING) {
            return;
        }

        new Thread(() -> {
            try {
                modulesStatus.setTorState(RESTARTING);

                makeDelay(5);

                ModulesRestarter modulesRestarter = new ModulesRestarter();
                modulesRestarter.getTorRestarterRunnable(this).run();

                modulesStatus.setTorState(RUNNING);

            } catch (Exception e) {
                Log.e(LOG_TAG, "ModulesService restartTor exception " + e.getMessage() + " " + e.getCause());
            }

        }).start();
    }

    private void restartITPD() {

        if (modulesStatus.getItpdState() != RUNNING) {
            return;
        }

        new Thread(() -> {
            try {
                modulesStatus.setItpdState(RESTARTING);

                Thread killerThread = new Thread(modulesKiller.getITPDKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getItpdState() != RUNNING) {
                    startITPD();
                }

            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "ModulesService restartITPD join interrupted!");
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

    private void stopVPNServiceIfRunning() {
        OperationMode operationMode = modulesStatus.getMode();
        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (operationMode == VPN_MODE && prefs.getBoolean("VPNServiceEnabled", false)) {
            ServiceVPNHelper.stop("ModulesService is destroyed", this);
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

        stopVPNServiceIfRunning();

        unregisterModulesBroadcastReceiver();

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

    private void stopModulesService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        stopSelf();
    }

    private void setBroadcastReceiver() {
        if (modulesStatus.getMode() == ROOT_MODE
                && !modulesStatus.isUseModulesWithRoot()
                && modulesBroadcastReceiver == null) {
            modulesBroadcastReceiver = new ModulesBroadcastReceiver(this);
            modulesBroadcastReceiver.registerReceivers();
        } else if (modulesStatus.getMode() != ROOT_MODE
                && modulesBroadcastReceiver != null) {
            unregisterModulesBroadcastReceiver();
            modulesBroadcastReceiver = null;
        }

    }

    private void unregisterModulesBroadcastReceiver() {
        if (modulesBroadcastReceiver != null) {
            try {
                modulesBroadcastReceiver.unregisterReceivers();
            } catch (Exception e) {
                Log.i(LOG_TAG, "ModulesService unregister receiver exception " + e.getMessage());
            }
        }
    }

    private boolean isDnsCryptSavedStateRunning() {
        return new PrefManager(this).getBoolPref("DNSCrypt Running");
    }

    private boolean isTorSavedStateRunning() {
        return new PrefManager(this).getBoolPref("Tor Running");
    }

    private boolean isITPDSavedStateRunning() {
        return new PrefManager(this).getBoolPref("I2PD Running");
    }

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "ModulesService makeDelay interrupted! " + e.getMessage() + " " + e.getCause());
        }
    }

}
