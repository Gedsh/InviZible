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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.modules;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.ap.InternetSharingChecker;
import pan.alexander.tordnscrypt.utils.apps.InstalledAppNamesStorage;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.portchecker.PortChecker;
import pan.alexander.tordnscrypt.utils.root.RootExecService;
import pan.alexander.tordnscrypt.utils.wakelock.WakeLocksManager;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.TorVersion;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_DISMISS_NOTIFICATION;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_RECOVER_SERVICE;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_RESTART_DNSCRYPT;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_RESTART_ITPD;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_RESTART_TOR;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_RESTART_TOR_FULL;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_START_DNSCRYPT;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_START_ITPD;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_START_TOR;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_DNSCRYPT;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_ITPD;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_SERVICE;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_SERVICE_FOREGROUND;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_TOR;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.CLEAR_IPTABLES_COMMANDS_HASH;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.EXTRA_LOOP;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.SLOWDOWN_LOOP;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.SPEEDUP_LOOP;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.START_ARP_SCANNER;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.STOP_ARP_SCANNER;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_DETECTION;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIX_TTL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.OPERATION_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.RUN_MODULES_WITH_ROOT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

import javax.inject.Inject;
import javax.inject.Named;

public class ModulesService extends Service {
    public static final int DEFAULT_NOTIFICATION_ID = 101102;

    public static volatile boolean serviceIsRunning = false;

    private final static int TIMER_HIGH_SPEED = 1000;
    private final static int TIMER_LOW_SPEED = 30000;

    public static final String DNSCRYPT_KEYWORD = "checkDNSRunning";
    public static final String TOR_KEYWORD = "checkTrRunning";
    public static final String ITPD_KEYWORD = "checkITPDRunning";

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultSharedPreferences;
    @Inject
    public Lazy<ConnectionCheckerInteractor> internetCheckerInteractor;
    @Inject
    public Lazy<ModulesReceiver> modulesReceiver;

    private static WakeLocksManager wakeLocksManager;

    @Inject
    public volatile Lazy<Handler> handler;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CoroutineExecutor executor;
    @Inject
    public Lazy<InstalledAppNamesStorage> installedAppNamesStorage;
    @Inject
    public Lazy<PortChecker> portChecker;

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    private NotificationManager systemNotificationManager;
    private ScheduledExecutorService checkModulesThreadsTimer;
    private ScheduledFuture<?> scheduledFuture;
    private int timerPeriod = TIMER_HIGH_SPEED;
    private ModulesStateLoop checkModulesStateTask;
    private ModulesKiller modulesKiller;
    private UsageStatistic usageStatistic;
    private ArpScanner arpScanner;

    public ModulesService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        systemNotificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        usageStatistic = new UsageStatistic(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            String title = getString(R.string.app_name);
            String message = getString(R.string.notification_text);
            if (usageStatistic.isStatisticAllowed()) {
                title = usageStatistic.getTitle();
                message = usageStatistic.getMessage(System.currentTimeMillis());
            }

            ModulesServiceNotificationManager serviceNotificationManager = new ModulesServiceNotificationManager(
                    this,
                    systemNotificationManager,
                    UsageStatistics.getStartTime()
            );
            serviceNotificationManager.sendNotification(title, message);
        }

        App.getInstance().getDaggerComponent().inject(this);

        serviceIsRunning = true;

        modulesKiller = new ModulesKiller(this, pathVars.get());

        startModulesThreadsTimer();

        if (defaultSharedPreferences.get().getBoolean(ARP_SPOOFING_DETECTION, false)) {
            startArpScanner();
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && Objects.equals(intent.getAction(), ACTION_STOP_SERVICE_FOREGROUND)) {
            stopModulesServiceForeground();
        }

        boolean showNotification;
        if (intent != null) {
            showNotification = intent.getBooleanExtra("showNotification", true);
        } else {
            showNotification = Utils.INSTANCE.isShowNotification(this);
        }

        if (showNotification) {

            String title = getString(R.string.app_name);
            String message = getString(R.string.notification_text);
            if (usageStatistic.isStatisticAllowed()) {
                title = usageStatistic.getTitle();
                message = usageStatistic.getMessage(System.currentTimeMillis());
            }

            ModulesServiceNotificationManager notification = new ModulesServiceNotificationManager(
                    this,
                    systemNotificationManager,
                    UsageStatistics.getStartTime()
            );
            notification.sendNotification(title, message);
            usageStatistic.setServiceNotification(notification);

            if (usageStatistic.isStatisticAllowed()) {
                usageStatistic.startUpdate();
            }
        }

        if (intent != null && Objects.equals(intent.getAction(), ACTION_STOP_SERVICE_FOREGROUND)) {
            stopModulesServiceForeground(startId);
            return START_NOT_STICKY;
        }

        if (intent == null) {
            intent = new Intent(ACTION_RECOVER_SERVICE);
        }

        String action = intent.getAction();

        if (action == null) {
            stopService(startId);
            return START_NOT_STICKY;
        }

        manageWakelocks();

        switch (action) {
            case ACTION_START_DNSCRYPT -> startDNSCrypt();
            case ACTION_START_TOR -> startTor();
            case ACTION_START_ITPD -> startITPD();
            case ACTION_STOP_DNSCRYPT -> stopDNSCrypt();
            case ACTION_STOP_TOR -> stopTor();
            case ACTION_STOP_ITPD -> stopITPD();
            case ACTION_RESTART_DNSCRYPT -> restartDNSCrypt();
            case ACTION_RESTART_TOR -> restartTor();
            case ACTION_RESTART_TOR_FULL -> restartTorFull();
            case ACTION_RESTART_ITPD -> restartITPD();
            case ACTION_DISMISS_NOTIFICATION -> dismissNotification(startId);
            case ACTION_RECOVER_SERVICE -> recoverAppState();
            case SPEEDUP_LOOP -> speedupTimer();
            case SLOWDOWN_LOOP -> slowdownTimer();
            case EXTRA_LOOP -> makeExtraLoop();
            case ACTION_STOP_SERVICE -> {
                stopModulesService();
                return START_NOT_STICKY;
            }
            case START_ARP_SCANNER -> startArpScanner();
            case STOP_ARP_SCANNER -> stopArpScanner();
            case CLEAR_IPTABLES_COMMANDS_HASH -> clearIptablesCommandsSavedHash();
        }

        setBroadcastReceiver();

        return START_STICKY;

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

                cleanLogFileNoRootMethod(pathVars.get().getAppDataDir() + "/logs/DnsCrypt.log",
                        ModulesService.this.getResources().getString(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);

                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(
                        ModulesService.this.getApplicationContext(), handler.get()
                );
                Thread dnsCryptThread = new Thread(modulesStarterHelper.getDNSCryptStarterRunnable());
                dnsCryptThread.setName("DNSCryptThread");
                dnsCryptThread.setDaemon(false);
                try {
                    dnsCryptThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    loge("ModulesService startDNSCrypt", e);
                }
                dnsCryptThread.start();

                changeDNSCryptStatus(dnsCryptThread);

            } catch (Exception e) {
                loge("DnsCrypt was unable to start", e);
                if (handler != null) {
                    handler.get().post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                }
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
            loge("checkPreviouslyRunningDNSCryptModule exception", e);
        }

        return result;
    }

    private void changeDNSCryptStatus(final Thread dnsCryptThread) {

        makeDelay(2);

        if (modulesStatus.isUseModulesWithRoot() || dnsCryptThread.isAlive()) {
            modulesStatus.setDnsCryptState(RUNNING);

            if (modulesKiller != null && !modulesStatus.isUseModulesWithRoot()) {
                modulesKiller.setDnsCryptThread(dnsCryptThread);
            }

            if (checkModulesStateTask != null && !modulesStatus.isUseModulesWithRoot()) {
                checkModulesStateTask.setDnsCryptThread(dnsCryptThread);
            }

            checkInternetConnection();
        } else {
            modulesStatus.setDnsCryptState(STOPPED);
        }
    }

    private boolean stopDNSCryptIfPortIsBusy() {
        PortChecker checker = portChecker.get();
        if (checker.isPortBusy(pathVars.get().getDNSCryptPort())) {
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
                loge("ModulesService restartDNSCrypt join interrupted!", e);
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

                cleanLogFileNoRootMethod(pathVars.get().getAppDataDir() + "/logs/Tor.log",
                        ModulesService.this.getResources().getString(R.string.tvTorDefaultLog) + " " + TorVersion);

                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(
                        ModulesService.this.getApplicationContext(), handler.get()
                );
                Thread torThread = new Thread(modulesStarterHelper.getTorStarterRunnable());
                torThread.setName("TorThread");
                torThread.setDaemon(false);
                try {
                    torThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    loge("ModulesService startTor", e);
                }
                torThread.start();

                changeTorStatus(torThread);
            } catch (Exception e) {
                loge("Tor was unable to startRefreshModulesStatus:", e);
                if (handler != null) {
                    handler.get().post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                }
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
            loge("checkPreviouslyRunningTorModule", e);
        }

        return result;
    }

    private void changeTorStatus(final Thread torThread) {

        makeDelay(2);

        if (modulesStatus.isUseModulesWithRoot() || torThread.isAlive()) {
            modulesStatus.setTorState(RUNNING);

            if (modulesKiller != null && !modulesStatus.isUseModulesWithRoot()) {
                modulesKiller.setTorThread(torThread);
            }

            if (checkModulesStateTask != null && !modulesStatus.isUseModulesWithRoot()) {
                checkModulesStateTask.setTorThread(torThread);
            }

            checkInternetConnection();
        } else {
            modulesStatus.setTorState(STOPPED);
        }
    }

    private boolean stopTorIfPortsIsBusy() {
        PortChecker checker = portChecker.get();
        boolean stopRequired = checker.isPortBusy(pathVars.get().getTorDNSPort())
                || checker.isPortBusy(pathVars.get().getTorSOCKSPort())
                || checker.isPortBusy(pathVars.get().getTorTransPort())
                || checker.isPortBusy(pathVars.get().getTorHTTPTunnelPort());

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
                loge("ModulesService restartTor join interrupted!", e);
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

                cleanLogFileNoRootMethod(pathVars.get().getAppDataDir() + "/logs/i2pd.log", "");

                ModulesStarterHelper modulesStarterHelper = new ModulesStarterHelper(
                        ModulesService.this.getApplicationContext(), handler.get()
                );
                Thread itpdThread = new Thread(modulesStarterHelper.getITPDStarterRunnable());
                itpdThread.setName("ITPDThread");
                itpdThread.setDaemon(false);
                try {
                    itpdThread.setPriority(Thread.NORM_PRIORITY);
                } catch (SecurityException e) {
                    loge("ModulesService startITPD", e);
                }
                itpdThread.start();

                changeITPDStatus(itpdThread);
            } catch (Exception e) {
                loge("I2PD was unable to startRefreshModulesStatus:", e);
                if (handler != null) {
                    handler.get().post(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show());
                }
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
            loge("checkPreviouslyRunningITPDModule", e);
        }

        return result;
    }

    private void changeITPDStatus(final Thread itpdThread) {

        makeDelay(3);

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

        List<String> lines = FileManager.readTextFileSynchronous(this, pathVars.get().getAppDataDir() + "/app_data/i2pd/tunnels.conf");
        for (String line : lines) {
            if (line.matches("^port ?= ?\\d+")) {
                String port = line.substring(line.indexOf("=") + 1).trim();
                if (port.matches("\\d+")) {
                    itpdTunnelsPorts.add(port);
                }
            }
        }

        preferenceRepository.get()
                .setStringSetPreference("ITPDTunnelsPorts", itpdTunnelsPorts);

        PortChecker checker = portChecker.get();
        boolean stopRequired = false;

        for (String port : itpdTunnelsPorts) {
            if (checker.isPortBusy(port)) {
                stopRequired = true;
            }
        }

        stopRequired = stopRequired ||
                checker.isPortBusy(pathVars.get().getITPDSOCKSPort())
                || checker.isPortBusy(pathVars.get().getITPDHttpProxyPort());

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
                loge("ModulesService restartITPD join interrupted!", e);
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
                loge("ModulesService restartDNSCrypt join interrupted!", e);
            }

        }).start();
    }

    private void restartTor() {

        if (modulesStatus.getTorState() != RUNNING) {
            return;
        }

        new Thread(() -> {
            try {

                ModulesRestarter modulesRestarter = new ModulesRestarter();
                modulesRestarter.getTorRestarterRunnable(this).run();

                checkInternetConnection();

            } catch (Exception e) {
                loge("ModulesService restartTor", e);
            }

        }).start();
    }

    private void restartTorFull() {
        if (modulesStatus.getTorState() != RUNNING) {
            return;
        }

        new Thread(() -> {
            try {
                modulesStatus.setTorState(RESTARTING);

                Thread killerThread = new Thread(modulesKiller.getTorKillerRunnable());
                killerThread.start();

                while (killerThread.isAlive()) {
                    killerThread.join();
                }

                makeDelay(5);

                if (modulesStatus.getTorState() != RUNNING) {
                    startTor();
                    checkInternetConnection();
                }

            } catch (InterruptedException e) {
                loge("ModulesService restartTorFull join interrupted!", e);
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

                internetCheckerInteractor.get().setInternetConnectionResult(false);

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
                loge("ModulesService restartITPD join interrupted!", e);
            }

        }).start();
    }

    private void checkInternetConnection() {
        ConnectionCheckerInteractor interactor = internetCheckerInteractor.get();
        interactor.setInternetConnectionResult(false);
        interactor.checkInternetConnection();
    }

    private void dismissNotification(int startId) {
        try {
            systemNotificationManager.cancel(DEFAULT_NOTIFICATION_ID);
            stopForeground(true);
        } catch (Exception e) {
            loge("ModulesService dismissNotification exception", e);
        }

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

            logi("ModulesService speedUPTimer");
        }
    }

    void slowdownTimer() {
        if (timerPeriod != TIMER_LOW_SPEED && checkModulesThreadsTimer != null
                && !checkModulesThreadsTimer.isShutdown() && checkModulesStateTask != null) {

            timerPeriod = TIMER_LOW_SPEED;

            if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
                scheduledFuture.cancel(false);
            }

            scheduledFuture = checkModulesThreadsTimer.scheduleWithFixedDelay(checkModulesStateTask, 1, timerPeriod, TimeUnit.MILLISECONDS);

            logi("ModulesService slowDOWNTimer");
        }
    }

    private void makeExtraLoop() {
        if (timerPeriod != TIMER_HIGH_SPEED && checkModulesStateTask != null) {
            executor.submit("ModulesService makeExtraLoop", () -> {
                    checkModulesStateTask.run();
                    return null;
            });
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
        if (defaultSharedPreferences == null) {
            return;
        }
        SharedPreferences prefs = defaultSharedPreferences.get();
        if (((operationMode == VPN_MODE) || modulesStatus.isFixTTL()) && prefs.getBoolean(VPN_SERVICE_ENABLED, false)) {
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

        unregisterModulesBroadcastReceiver();

        if (usageStatistic != null) {
            usageStatistic.stopUpdate();
        }

        releaseWakelocks();

        if (checkModulesStateTask != null && modulesStatus.getMode() == VPN_MODE) {
            checkModulesStateTask.removeHandlerTasks();
        }

        stopModulesThreadsTimer();

        stopArpScanner();

        stopVPNServiceIfRunning();

        if (handler != null) {
            handler.get().removeCallbacksAndMessages(null);
        }

        InternetSharingChecker.resetTetherInterfaceNames();

        serviceIsRunning = false;

        stopRootExecServiceIfRequired();

        installedAppNamesStorage.get().clearAppUidToNames();

        App.getInstance().getSubcomponentsManager().releaseModulesServiceSubcomponent();

        super.onDestroy();
    }

    private void stopService(int startID) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                systemNotificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                stopForeground(true);
            } catch (Exception e) {
                loge("ModulesService stopService", e);
            }

        }

        stopSelf(startID);
    }

    private void recoverAppState() {

        modulesStatus.setDnsCryptState(STOPPED);
        modulesStatus.setTorState(STOPPED);
        modulesStatus.setItpdState(STOPPED);

        if (modulesStatus.getMode() != null && modulesStatus.getMode() != UNDEFINED) {
            return;
        }

        loge("Restoring application state, possibly after the crash.");

        Utils.startAppExitDetectService(this);

        SharedPreferences defaultPreferences = defaultSharedPreferences.get();
        PreferenceRepository preferences = preferenceRepository.get();

        boolean rootIsAvailable = preferences.getBoolPreference(ROOT_IS_AVAILABLE);
        boolean runModulesWithRoot = defaultPreferences.getBoolean(RUN_MODULES_WITH_ROOT, false);
        modulesStatus.setFixTTL(defaultPreferences.getBoolean(FIX_TTL, false));

        String operationMode = preferences.getStringPreference(OPERATION_MODE);

        if (!operationMode.isEmpty()) {
            OperationMode mode = OperationMode.valueOf(operationMode);
            ModulesAux.switchModes(rootIsAvailable, runModulesWithRoot, mode);
        }

        boolean savedDNSCryptStateRunning = ModulesAux.isDnsCryptSavedStateRunning();
        boolean savedTorStateRunning = ModulesAux.isTorSavedStateRunning();
        boolean savedITPDStateRunning = ModulesAux.isITPDSavedStateRunning();

        if (savedDNSCryptStateRunning && !runModulesWithRoot) {
            modulesStatus.setSystemDNSAllowed(true);
        }

        if (savedDNSCryptStateRunning) {
            startDNSCrypt();
        }

        if (savedTorStateRunning) {
            startTor();
        }

        if (savedITPDStateRunning) {
            startITPD();
        }

        saveModulesStateRunning(
                savedDNSCryptStateRunning,
                savedTorStateRunning,
                savedITPDStateRunning
        );
    }

    private void saveModulesStateRunning(
            boolean saveDNSCryptRunning,
            boolean saveTorRunning,
            boolean saveITPDRunning
    ) {
        ModulesAux.saveDNSCryptStateRunning(saveDNSCryptRunning);
        ModulesAux.saveTorStateRunning(saveTorRunning);
        ModulesAux.saveITPDStateRunning(saveITPDRunning);
    }

    private void stopModulesService() {
        try {
            systemNotificationManager.cancel(DEFAULT_NOTIFICATION_ID);
            stopForeground(true);
        } catch (Exception e) {
            loge("ModulesService stopModulesService", e);
        }

        stopSelf();
    }

    private void stopModulesServiceForeground() {

        try {
            systemNotificationManager.cancel(DEFAULT_NOTIFICATION_ID);
            stopForeground(true);
        } catch (Exception e) {
            loge("ModulesService stopModulesServiceForeground1", e);
        }
    }

    private void stopModulesServiceForeground(int startId) {

        try {
            systemNotificationManager.cancel(DEFAULT_NOTIFICATION_ID);
            stopForeground(true);
        } catch (Exception e) {
            loge("ModulesService stopModulesServiceForeground2", e);
        }

        stopSelf(startId);
    }

    private void setBroadcastReceiver() {
        ModulesReceiver receiver = modulesReceiver.get();
        OperationMode mode = modulesStatus.getMode();
        if ((mode == VPN_MODE || mode == PROXY_MODE
                || mode == ROOT_MODE && !modulesStatus.isUseModulesWithRoot())) {
            receiver.registerReceivers(this);
            internetCheckerInteractor.get().addListener(receiver);
        } else {
            unregisterModulesBroadcastReceiver();
            internetCheckerInteractor.get().removeListener(receiver);
        }

    }

    private void unregisterModulesBroadcastReceiver() {
        try {
            modulesReceiver.get().unregisterReceivers();
        } catch (Exception e) {
            logw("ModulesService unregister receiver", e);
        }
    }

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            loge("ModulesService makeDelay interrupted!", e);
        }
    }

    public Thread findThreadByName(String threadName) {
        Thread currentThread = Thread.currentThread();
        ThreadGroup threadGroup = getRootThreadGroup(currentThread);
        int allActiveThreads = threadGroup.activeCount();
        Thread[] allThreads = new Thread[allActiveThreads];
        threadGroup.enumerate(allThreads);

        for (Thread thread : allThreads) {
            String name = "";
            if (thread != null) {
                name = thread.getName();
            }
            //logi("Current threads " + name);
            if (name.equals(threadName)) {
                logi("Found old module thread " + name);
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

    private void cleanLogFileNoRootMethod(String logFilePath, String text) {
        try {
            File f = new File(pathVars.get().getAppDataDir() + "/logs");

            if (f.mkdirs() && f.setReadable(true) && f.setWritable(true))
                logi("log dir created");

            PrintWriter writer = new PrintWriter(logFilePath, "UTF-8");
            writer.println(text);
            writer.close();
        } catch (IOException e) {
            loge("Unable to create dnsCrypt log file", e);
        }
    }

    private void startArpScanner() {
        try {
            arpScanner = ArpScanner.getArpComponent().get();
            arpScanner.start();
        } catch (Exception e) {
            loge("ModulesService startArpScanner", e);
        }
    }

    private void stopArpScanner() {
        if (arpScanner != null) {
            arpScanner.stop();
            arpScanner = null;
            ArpScanner.releaseArpComponent();
        }
    }

    private void clearIptablesCommandsSavedHash() {
        if (checkModulesStateTask != null) {
            checkModulesStateTask.clearIptablesCommandHash();
        }
    }

    private void stopRootExecServiceIfRequired() {
        Intent intent = new Intent(this, RootExecService.class);
        stopService(intent);
    }
}
