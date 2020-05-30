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

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.WakeLocksManager;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.TorVersion;
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

    private final static int TIMER_HIGH_SPEED = 1000;
    private final static int TIMER_LOW_SPEED = 30000;

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
    static final String speedupLoop = "pan.alexander.tordnscrypt.action.SPEEDUP_LOOP";
    static final String slowdownLoop = "pan.alexander.tordnscrypt.action.SLOWDOWN_LOOP";
    static final String extraLoop = "pan.alexander.tordnscrypt.action.MAKE_EXTRA_LOOP";

    static final String DNSCRYPT_KEYWORD = "checkDNSRunning";
    static final String TOR_KEYWORD = "checkTrRunning";
    static final String ITPD_KEYWORD = "checkITPDRunning";

    private static WakeLocksManager wakeLocksManager;

    ModulesBroadcastReceiver modulesBroadcastReceiver;

    private final Handler mHandler = new Handler();
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    private PathVars pathVars;
    private NotificationManager notificationManager;
    private ScheduledExecutorService checkModulesThreadsTimer;
    private ScheduledFuture<?> scheduledFuture;
    private int timerPeriod = TIMER_HIGH_SPEED;
    private ModulesStateLoop checkModulesStateTask;
    private ModulesKiller modulesKiller;

    public ModulesService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        pathVars = PathVars.getInstance(this);

        modulesKiller = new ModulesKiller(this, pathVars);

        startModulesThreadsTimer();

        if (new PrefManager(this).getBoolPref("DNSCryptSystemDNSAllowed")) {
            new Handler().postDelayed(() -> {
                if (new PrefManager(this).getBoolPref("DNSCryptSystemDNSAllowed")) {
                    new PrefManager(this).setBoolPref("DNSCryptSystemDNSAllowed", false);
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(this, true);
                }
            }, 10000);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        boolean showNotification = true;
        if (intent != null) {
            showNotification = intent.getBooleanExtra("showNotification", true);
        }

        if (showNotification) {
            ServiceNotification notification = new ServiceNotification(this, notificationManager);
            notification.sendNotification(getString(R.string.app_name), getText(R.string.notification_text).toString());
        }

        if (intent == null) {
            stopService(startId);
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (action == null) {
            stopService(startId);
            return START_NOT_STICKY;
        }

        manageWakelocks();

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
            case speedupLoop:
                speedupTimer();
                break;
            case slowdownLoop:
                slowdownTimer();
                break;
            case extraLoop:
                makeExtraLoop();
                break;
        }

        setBroadcastReceiver();

        return START_REDELIVER_INTENT;

    }

    private void startDNSCrypt() {

        if (modulesStatus.getDnsCryptState() == STOPPED) {
            modulesStatus.setDnsCryptState(STARTING);
        }

        new Thread(() -> {

            if (!modulesStatus.isUseModulesWithRoot()) {
                Thread previousDnsCryptThread = modulesKiller.getDnsCryptThread();

                if (previousDnsCryptThread != null && previousDnsCryptThread.isAlive()) {
                    changeDNSCryptStatus(previousDnsCryptThread);
                    return;
                }
            }

            try {
                Thread previousDnsCryptThread = checkPreviouslyRunningDNSCryptModule();

                if (previousDnsCryptThread != null && previousDnsCryptThread.isAlive()) {
                    changeDNSCryptStatus(previousDnsCryptThread);
                    return;
                }

                if (stopDNSCryptIfPortIsBusy()) {
                    changeDNSCryptStatus(modulesKiller.getDnsCryptThread());
                    return;
                }

                cleanLogFileNoRootMethod(pathVars.getAppDataDir() + "/logs/DnsCrypt.log",
                        ModulesService.this.getResources().getString(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);

                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(ModulesService.this, mHandler, pathVars);
                Thread dnsCryptThread = new Thread(modulesStarterHelper.getDNSCryptStarterRunnable());
                dnsCryptThread.setName("DNSCryptThread");
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

    private Thread checkPreviouslyRunningDNSCryptModule() {

        if (modulesStatus.isUseModulesWithRoot()) {
            return null;
        }

        Thread result = null;

        try {
            if (modulesStatus.getDnsCryptState() != RESTARTING) {
                result = findThreadByName("DNSCryptThread");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "checkPreviouslyRunningDNSCryptModule exception " + e.getMessage());
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

    private boolean stopDNSCryptIfPortIsBusy() {
        if (!isAvailable(pathVars.getDNSCryptPort())) {
            try {
                modulesStatus.setDnsCryptState(RESTARTING);

                Thread killerThread = new Thread(modulesKiller.getDNSCryptKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getDnsCryptState() == RUNNING) {
                    return true;
                }

                modulesStatus.setDnsCryptState(STARTING);

            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "ModulesService restartDNSCrypt join interrupted!");
            }
        }
        return false;
    }

    private void startTor() {

        if (modulesStatus.getTorState() == STOPPED) {
            modulesStatus.setTorState(STARTING);
        }

        new Thread(() -> {

            if (!modulesStatus.isUseModulesWithRoot()) {
                Thread previousTorThread = modulesKiller.getTorThread();

                if (previousTorThread != null && previousTorThread.isAlive()) {
                    changeTorStatus(previousTorThread);
                    return;
                }
            }

            try {
                Thread previousTorThread = checkPreviouslyRunningTorModule();

                if (previousTorThread != null && previousTorThread.isAlive()) {
                    changeTorStatus(previousTorThread);
                    return;
                }

                if (stopTorIfPortsIsBusy()) {
                    changeTorStatus(modulesKiller.getTorThread());
                    return;
                }

                cleanLogFileNoRootMethod(pathVars.getAppDataDir() + "/logs/Tor.log",
                        ModulesService.this.getResources().getString(R.string.tvTorDefaultLog) + " " + TorVersion);

                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(ModulesService.this, mHandler, pathVars);
                Thread torThread = new Thread(modulesStarterHelper.getTorStarterRunnable());
                torThread.setName("TorThread");
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

    private Thread checkPreviouslyRunningTorModule() {

        if (modulesStatus.isUseModulesWithRoot()) {
            return null;
        }

        Thread result = null;

        try {
            if (modulesStatus.getTorState() != RESTARTING) {
                result = findThreadByName("TorThread");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "checkPreviouslyRunningTorModule exception " + e.getMessage());
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

    private boolean stopTorIfPortsIsBusy() {
        boolean stopRequired = !isAvailable(pathVars.getTorDNSPort())
                || !isAvailable(pathVars.getTorSOCKSPort())
                || !isAvailable(pathVars.getTorTransPort())
                || !isAvailable(pathVars.getTorHTTPTunnelPort());

        if (stopRequired) {
            try {
                modulesStatus.setTorState(RESTARTING);

                Thread killerThread = new Thread(modulesKiller.getTorKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getTorState() == RUNNING) {
                    return true;
                }

                modulesStatus.setTorState(STARTING);

            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "ModulesService restartTor join interrupted!");
            }
        }
        return false;
    }


    private void startITPD() {

        if (modulesStatus.getItpdState() == STOPPED) {
            modulesStatus.setItpdState(STARTING);
        }

        new Thread(() -> {

            if (!modulesStatus.isUseModulesWithRoot()) {
                Thread previousITPDThread = modulesKiller.getItpdThread();

                if (previousITPDThread != null && previousITPDThread.isAlive()) {
                    changeITPDStatus(previousITPDThread);
                    return;
                }
            }

            try {
                Thread previousITPDThread = checkPreviouslyRunningITPDModule();

                if (previousITPDThread != null && previousITPDThread.isAlive()) {
                    changeITPDStatus(previousITPDThread);
                    return;
                }

                if (stopITPDIfPortsIsBusy()) {
                    changeITPDStatus(modulesKiller.getItpdThread());
                    return;
                }

                cleanLogFileNoRootMethod(pathVars.getAppDataDir() + "/logs/i2pd.log", "");

                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(ModulesService.this, mHandler, pathVars);
                Thread itpdThread = new Thread(modulesStarterHelper.getITPDStarterRunnable());
                itpdThread.setName("ITPDThread");
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

    private Thread checkPreviouslyRunningITPDModule() {

        if (modulesStatus.isUseModulesWithRoot()) {
            return null;
        }

        Thread result = null;

        try {
            if (modulesStatus.getItpdState() != RESTARTING) {
                result = findThreadByName("ITPDThread");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "checkPreviouslyRunningITPDModule exception " + e.getMessage());
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

    private boolean stopITPDIfPortsIsBusy() {

        Set<String> itpdTunnelsPorts = new HashSet<>();

        List<String> lines = FileOperations.readTextFileSynchronous(this, pathVars.getAppDataDir() + "/app_data/i2pd/tunnels.conf");
        for (String line : lines) {
            if (line.matches("^port ?= ?\\d+")) {
                String port = line.substring(line.indexOf("=") + 1).trim();
                if (port.matches("\\d+")) {
                    itpdTunnelsPorts.add(port);
                }
            }
        }

        new PrefManager(this).setSetStrPref("ITPDTunnelsPorts", itpdTunnelsPorts);

        boolean stopRequired = false;

        for (String port : itpdTunnelsPorts) {
            if (!isAvailable(port)) {
                stopRequired = true;
            }
        }

        stopRequired = stopRequired ||
                !isAvailable(pathVars.getITPDSOCKSPort())
                || !isAvailable(pathVars.getITPDHttpProxyPort());

        if (stopRequired) {
            try {
                modulesStatus.setItpdState(RESTARTING);

                Thread killerThread = new Thread(modulesKiller.getITPDKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getItpdState() == RUNNING) {
                    return true;
                }

                modulesStatus.setItpdState(STARTING);

            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "ModulesService restartITPD join interrupted!");
            }
        }
        return false;
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
        checkModulesThreadsTimer = Executors.newSingleThreadScheduledExecutor();
        checkModulesStateTask = new ModulesStateLoop(this);
        scheduledFuture = checkModulesThreadsTimer.scheduleWithFixedDelay(checkModulesStateTask, 1, timerPeriod, TimeUnit.MILLISECONDS);
    }

    private void speedupTimer() {
        if (timerPeriod != TIMER_HIGH_SPEED && checkModulesThreadsTimer != null
                && !checkModulesThreadsTimer.isShutdown() && checkModulesStateTask != null) {

            timerPeriod = TIMER_HIGH_SPEED;

            if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(false);
            }

            scheduledFuture = checkModulesThreadsTimer.scheduleWithFixedDelay(checkModulesStateTask, 1, timerPeriod, TimeUnit.MILLISECONDS);

            Log.i(LOG_TAG, "ModulesService speedUPTimer");
        }
    }

    private void slowdownTimer() {
        if (timerPeriod != TIMER_LOW_SPEED && checkModulesThreadsTimer != null
                && !checkModulesThreadsTimer.isShutdown() && checkModulesStateTask != null) {

            timerPeriod = TIMER_LOW_SPEED;

            if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(false);
            }

            scheduledFuture = checkModulesThreadsTimer.scheduleWithFixedDelay(checkModulesStateTask, 1, timerPeriod, TimeUnit.MILLISECONDS);

            Log.i(LOG_TAG, "ModulesService slowDOWNTimer");
        }
    }

    private void makeExtraLoop() {
        ExecutorService executorService = CachedExecutor.INSTANCE.getExecutorService();
        if (timerPeriod != TIMER_HIGH_SPEED && checkModulesStateTask != null && !executorService.isShutdown()) {
            executorService.submit(checkModulesStateTask);
        }
    }

    private void stopModulesThreadsTimer() {
        if (checkModulesThreadsTimer != null && !checkModulesThreadsTimer.isShutdown()) {
            checkModulesThreadsTimer.shutdown();
            checkModulesThreadsTimer = null;
        }
    }

    private void stopVPNServiceIfRunning() {
        OperationMode operationMode = modulesStatus.getMode();
        SharedPreferences prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        if (((operationMode == VPN_MODE) || modulesStatus.isFixTTL()) && prefs.getBoolean("VPNServiceEnabled", false)) {
            ServiceVPNHelper.stop("ModulesService is destroyed", this);
        }
    }

    private void manageWakelocks() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean lock = sharedPreferences.getBoolean("swWakelock", false);

        wakeLocksManager = WakeLocksManager.getInstance();
        wakeLocksManager.managePowerWakelock(this, lock);
        wakeLocksManager.manageWiFiLock(this, lock);
    }

    private void releaseWakelocks() {
        if (wakeLocksManager != null) {
            wakeLocksManager.stopPowerWakelock();
            wakeLocksManager.stopWiFiLock();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {

        releaseWakelocks();

        stopModulesThreadsTimer();

        stopVPNServiceIfRunning();

        unregisterModulesBroadcastReceiver();

        CachedExecutor.INSTANCE.stopExecutorService();

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

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "ModulesService makeDelay interrupted! " + e.getMessage() + " " + e.getCause());
        }
    }

    public Thread findThreadByName(String threadName) {
        Thread currentThread = Thread.currentThread();
        ThreadGroup threadGroup = getRootThreadGroup(currentThread);
        int allActiveThreads = threadGroup.activeCount();
        Thread[] allThreads = new Thread[allActiveThreads];
        threadGroup.enumerate(allThreads);

        for (Thread thread : allThreads) {
            String name = thread.getName();
            //Log.i(LOG_TAG, "Current threads " + name);
            if (name.equals(threadName)) {
                Log.i(LOG_TAG, "Found old module thread " + name);
                return thread;
            }
        }

        return null;
    }

    private ThreadGroup getRootThreadGroup(Thread thread) {
        ThreadGroup rootGroup = thread.getThreadGroup();
        while (rootGroup != null) {
            ThreadGroup parentGroup = rootGroup.getParent();
            if (parentGroup == null) {
                break;
            }
            rootGroup = parentGroup;
        }
        return rootGroup;
    }

    private boolean isAvailable(String portStr) {

        int port = Integer.parseInt(portStr);

        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException ignored) {
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException ignored) {
                }
            }
        }

        return false;
    }

    private void cleanLogFileNoRootMethod(String logFilePath, String text) {
        try {
            File f = new File(pathVars.getAppDataDir() + "/logs");

            if (f.mkdirs() && f.setReadable(true) && f.setWritable(true))
                Log.i(LOG_TAG, "log dir created");

            PrintWriter writer = new PrintWriter(logFilePath, "UTF-8");
            writer.println(text);
            writer.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to create dnsCrypt log file " + e.getMessage());
        }
    }
}
