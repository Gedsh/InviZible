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

import android.app.Service;
import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import dagger.Lazy;
import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

import static pan.alexander.tordnscrypt.modules.ModulesService.DNSCRYPT_KEYWORD;
import static pan.alexander.tordnscrypt.modules.ModulesService.ITPD_KEYWORD;
import static pan.alexander.tordnscrypt.modules.ModulesService.TOR_KEYWORD;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.DNSCRYPT_RUN_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.I2PD_RUN_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.TOR_RUN_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

import javax.inject.Inject;

public class ModulesKiller {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;

    private final Service service;
    private final String appDataDir;
    private final String busyboxPath;
    private final String dnscryptPath;
    private final String torPath;
    private final String itpdPath;

    private final ModulesStatus modulesStatus;

    private final ReentrantLock reentrantLock;

    private static Thread dnsCryptThread;
    private static Thread torThread;
    private static Thread itpdThread;

    ModulesKiller(Service service, PathVars pathVars) {
        App.getInstance().getDaggerComponent().inject(this);
        this.service = service;
        appDataDir = pathVars.getAppDataDir();
        busyboxPath = pathVars.getBusyboxPath();
        dnscryptPath = pathVars.getDNSCryptPath();
        torPath = pathVars.getTorPath();
        itpdPath = pathVars.getITPDPath();
        modulesStatus = ModulesStatus.getInstance();
        reentrantLock = new ReentrantLock();
    }

    public static void stopDNSCrypt(Context context) {
        sendStopIntent(context, ModulesServiceActions.ACTION_STOP_DNSCRYPT);
    }

    public static void stopTor(Context context) {
        sendStopIntent(context, ModulesServiceActions.ACTION_STOP_TOR);
    }

    public static void stopITPD(Context context) {
        sendStopIntent(context, ModulesServiceActions.ACTION_STOP_ITPD);
    }

    private static void sendStopIntent(Context context, String action) {
        ModulesActionSender.INSTANCE.sendIntent(context, action);
    }

    private void sendResultIntent(int moduleMark, String moduleKeyWord, String binaryPath) {
        RootCommands comResult = new RootCommands(new ArrayList<>(Arrays.asList(moduleKeyWord, binaryPath)));
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult", comResult);
        intent.putExtra("Mark", moduleMark);
        LocalBroadcastManager.getInstance(service).sendBroadcast(intent);
    }

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            loge("Modules killer makeDelay interrupted!", e);
        }
    }

    void setDnsCryptThread(Thread dnsCryptThread) {
        ModulesKiller.dnsCryptThread = dnsCryptThread;
    }

    void setTorThread(Thread torThread) {
        ModulesKiller.torThread = torThread;
    }

    void setItpdThread(Thread itpdThread) {
        ModulesKiller.itpdThread = itpdThread;
    }

    Thread getDnsCryptThread() {
        return dnsCryptThread;
    }

    Thread getTorThread() {
        return torThread;
    }

    Thread getItpdThread() {
        return itpdThread;
    }

    Runnable getDNSCryptKillerRunnable() {
        return () -> {

            if (modulesStatus.getDnsCryptState() != RESTARTING) {
                modulesStatus.setDnsCryptState(STOPPING);
            }

            reentrantLock.lock();

            try {
                String dnsCryptPid = readPidFile(appDataDir + "/dnscrypt-proxy.pid");

                boolean moduleStartedWithRoot = preferenceRepository.get()
                        .getBoolPreference("DNSCryptStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                boolean result = doThreeAttemptsToStopModule(dnscryptPath, dnsCryptPid, dnsCryptThread, moduleStartedWithRoot);

                if (!result) {

                    if (rootIsAvailable) {
                        logw("ModulesKiller cannot stop DNSCrypt. Stop with root method!");
                        result = killModule(dnscryptPath, dnsCryptPid, dnsCryptThread, true, "SIGKILL", 10);
                    }

                    if (!moduleStartedWithRoot && !result) {
                        logw("ModulesKiller cannot stop DNSCrypt. Stop with interrupt thread!");

                        makeDelay(5);

                        result = stopModuleWithInterruptThread(dnsCryptThread);
                    }
                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            ModulesAux.saveDNSCryptStateRunning(true);
                            makeDelay(1);
                            sendResultIntent(DNSCRYPT_RUN_FRAGMENT_MARK, DNSCRYPT_KEYWORD, dnscryptPath);
                        }

                        modulesStatus.setDnsCryptState(RUNNING);

                        loge("ModulesKiller cannot stop DNSCrypt!");

                    } else {
                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            ModulesAux.saveDNSCryptStateRunning(false);
                            modulesStatus.setDnsCryptState(STOPPED);
                            makeDelay(1);
                            sendResultIntent(DNSCRYPT_RUN_FRAGMENT_MARK, DNSCRYPT_KEYWORD, "");
                        }
                    }
                } else {
                    if (dnsCryptThread != null && dnsCryptThread.isAlive()) {

                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            ModulesAux.saveDNSCryptStateRunning(true);
                            makeDelay(1);
                            sendResultIntent(DNSCRYPT_RUN_FRAGMENT_MARK, DNSCRYPT_KEYWORD, dnscryptPath);
                        }

                        modulesStatus.setDnsCryptState(RUNNING);

                        loge("ModulesKiller cannot stop DNSCrypt!");
                    } else {

                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            ModulesAux.saveDNSCryptStateRunning(false);
                            modulesStatus.setDnsCryptState(STOPPED);
                            makeDelay(1);
                            sendResultIntent(DNSCRYPT_RUN_FRAGMENT_MARK, DNSCRYPT_KEYWORD, "");
                        }
                    }
                }
            } catch (Exception e){
                loge("ModulesKiller getDNSCryptKillerRunnable", e);
            } finally {
                reentrantLock.unlock();
            }

        };
    }


    Runnable getTorKillerRunnable() {
        return () -> {

            if (modulesStatus.getTorState() != RESTARTING) {
                modulesStatus.setTorState(STOPPING);
            }

            reentrantLock.lock();

            try {
                String torPid = readPidFile(appDataDir + "/tor.pid");

                boolean moduleStartedWithRoot = preferenceRepository.get()
                        .getBoolPreference("TorStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                boolean result = doThreeAttemptsToStopModule(torPath, torPid, torThread, moduleStartedWithRoot);

                if (!result) {

                    if (rootIsAvailable) {
                        logw("ModulesKiller cannot stop Tor. Stop with root method!");
                        result = killModule(torPath, torPid, torThread, true, "SIGKILL", 10);
                    }

                    if (!moduleStartedWithRoot && !result) {
                        logw("ModulesKiller cannot stop Tor. Stop with interrupt thread!");

                        makeDelay(5);

                        result = stopModuleWithInterruptThread(torThread);
                    }

                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getTorState() != RESTARTING) {
                            ModulesAux.saveTorStateRunning(true);
                            makeDelay(1);
                            sendResultIntent(TOR_RUN_FRAGMENT_MARK, TOR_KEYWORD, torPath);
                        }

                        modulesStatus.setTorState(RUNNING);

                        loge("ModulesKiller cannot stop Tor!");

                    } else {
                        if (modulesStatus.getTorState() != RESTARTING) {
                            ModulesAux.saveTorStateRunning(false);
                            modulesStatus.setTorState(STOPPED);
                            makeDelay(1);
                            sendResultIntent(TOR_RUN_FRAGMENT_MARK, TOR_KEYWORD, "");
                        }
                    }
                } else {
                    if (torThread != null && torThread.isAlive()) {

                        if (modulesStatus.getTorState() != RESTARTING) {
                            ModulesAux.saveTorStateRunning(true);
                            makeDelay(1);
                            sendResultIntent(TOR_RUN_FRAGMENT_MARK, TOR_KEYWORD, torPath);
                        }

                        modulesStatus.setTorState(RUNNING);

                        loge("ModulesKiller cannot stop Tor!");
                    } else {

                        if (modulesStatus.getTorState() != RESTARTING) {
                            ModulesAux.saveTorStateRunning(false);
                            modulesStatus.setTorState(STOPPED);
                            makeDelay(1);
                            sendResultIntent(TOR_RUN_FRAGMENT_MARK, TOR_KEYWORD, "");
                        }
                    }
                }
            } catch (Exception e){
                loge("ModulesKiller getTorKillerRunnable", e);
            } finally {
                reentrantLock.unlock();
            }

        };
    }

    Runnable getITPDKillerRunnable() {
        return () -> {

            if (modulesStatus.getItpdState() != RESTARTING) {
                modulesStatus.setItpdState(STOPPING);
            }

            reentrantLock.lock();

            try {
                String itpdPid = readPidFile(appDataDir + "/i2pd.pid");

                boolean moduleStartedWithRoot = preferenceRepository.get()
                        .getBoolPreference("ITPDStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                boolean result = doThreeAttemptsToStopModule(itpdPath, itpdPid, itpdThread, moduleStartedWithRoot);

                if (!result) {

                    if (rootIsAvailable) {
                        logw("ModulesKiller cannot stop I2P. Stop with root method!");
                        result = killModule(itpdPath, itpdPid, itpdThread, true, "SIGKILL", 10);
                    }

                    if (!moduleStartedWithRoot && !result) {
                        logw("ModulesKiller cannot stop I2P. Stop with interrupt thread!");

                        makeDelay(5);

                        result = stopModuleWithInterruptThread(itpdThread);
                    }
                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getItpdState() != RESTARTING) {
                            ModulesAux.saveITPDStateRunning(true);
                            makeDelay(1);
                            sendResultIntent(I2PD_RUN_FRAGMENT_MARK, ITPD_KEYWORD, itpdPath);
                        }

                        modulesStatus.setItpdState(RUNNING);

                        loge("ModulesKiller cannot stop I2P!");

                    } else {
                        if (modulesStatus.getItpdState() != RESTARTING) {
                            ModulesAux.saveITPDStateRunning(false);
                            modulesStatus.setItpdState(STOPPED);
                            makeDelay(1);
                            sendResultIntent(I2PD_RUN_FRAGMENT_MARK, ITPD_KEYWORD, "");
                        }

                    }
                }

                if (itpdThread != null && itpdThread.isAlive()) {

                    if (modulesStatus.getItpdState() != RESTARTING) {
                        ModulesAux.saveITPDStateRunning(true);
                        makeDelay(1);
                        sendResultIntent(I2PD_RUN_FRAGMENT_MARK, ITPD_KEYWORD, itpdPath);
                    }

                    modulesStatus.setItpdState(RUNNING);

                    loge("ModulesKiller cannot stop I2P!");
                } else {

                    if (modulesStatus.getItpdState() != RESTARTING) {
                        ModulesAux.saveITPDStateRunning(false);
                        modulesStatus.setItpdState(STOPPED);
                        makeDelay(1);
                        sendResultIntent(I2PD_RUN_FRAGMENT_MARK, ITPD_KEYWORD, "");
                    }
                }
            } catch (Exception e){
                loge("ModulesKiller getITPDKillerRunnable", e);
            } finally {
                reentrantLock.unlock();
            }

        };
    }

    private boolean killModule(String module, String pid, Thread thread, boolean killWithRoot, String signal, int delaySec) {
        boolean result = false;

        if (module.contains("/")) {
            module = module.substring(module.lastIndexOf("/"));
        }

        List<String> preparedCommands = prepareKillCommands(module, pid, signal, killWithRoot);

        if ((thread == null || !thread.isAlive()) && modulesStatus.isRootAvailable()
                || killWithRoot) {

            String sleep = busyboxPath + "sleep " + delaySec;
            String checkString = busyboxPath + "pgrep -l " + module;

            List<String> commands = new ArrayList<>(preparedCommands);
            commands.add(sleep);
            commands.add(checkString);

            List<String> shellResult = killWithSU(module, commands);

            if (shellResult != null) {
                result = !shellResult.toString().toLowerCase().contains(module.toLowerCase().trim());
            }

            if (shellResult != null) {
                logi("Kill " + module + " with root: result " + result + "\n" + shellResult);
            } else {
                logi("Kill " + module + " with root: result false");
            }
        } else {

            if (!pid.isEmpty()) {
                killWithPid(signal, pid, delaySec);
            }

            if (thread != null) {
                result = !thread.isAlive();
            }

            List<String> shellResult = null;
            if (!result) {
                shellResult = killWithSH(module, preparedCommands, delaySec);

                if (thread != null) {
                    result = !thread.isAlive();
                }
            }

            if (shellResult != null) {
                logi("Kill " + module + " without root: result " + result + "\n" + shellResult);
            } else {
                logi("Kill " + module + " without root: result " + result);
            }
        }

        return result;
    }

    private void killWithPid(String signal, String pid, int delay) {
        try {
            if (signal.isEmpty()) {
                android.os.Process.sendSignal(Integer.parseInt(pid), 15);
            } else {
                android.os.Process.killProcess(Integer.parseInt(pid));
            }
            makeDelay(delay);
        } catch (Exception e) {
            loge("ModulesKiller killWithPid", e);
        }
    }

    @SuppressWarnings("deprecation")
    private List<String> killWithSH(String module, List<String> commands, int delay) {
        List<String> shellResult = null;
        try {
            shellResult = Shell.SH.run(commands);
            makeDelay(delay);
        } catch (Exception e) {
            loge("Kill " + module + " without root", e);
        }
        return shellResult;
    }

    @SuppressWarnings("deprecation")
    private List<String> killWithSU(String module, List<String> commands) {
        List<String> shellResult = null;
        try {
            shellResult = Shell.SU.run(commands);
        } catch (Exception e) {
            loge("Kill " + module + " with root", e);
        }
        return shellResult;
    }

    //kill default signal SIGTERM - 15, SIGKILL -9, SIGQUIT - 3
    private List<String> prepareKillCommands(String module, String pid, String signal, boolean killWithRoot) {
        List<String> result;

        if (pid.isEmpty() || killWithRoot) {
            String killStringToyBox = "toybox pkill " + module + " || true";
            String killString = "pkill " + module + " || true";
            String killStringBusybox = busyboxPath + "pkill " + module + " || true";
            String killAllStringBusybox = busyboxPath + "kill $(pgrep " + module + ") || true";
            if (!signal.isEmpty()) {
                killStringToyBox = "toybox pkill -" + signal + " " + module + " || true";
                killString = "pkill -" + signal + " " + module + " || true";
                killStringBusybox = busyboxPath + "pkill -" + signal + " " + module + " || true";
                killAllStringBusybox = busyboxPath + "kill -s " + signal + " $(pgrep " + module + ") || true";
            }

            result = new ArrayList<>(Arrays.asList(
                    killStringBusybox,
                    killAllStringBusybox,
                    killStringToyBox,
                    killString
            ));
        } else {
            String killAllStringToolBox = "toolbox kill " + pid + " || true";
            String killStringToyBox = "toybox kill " + pid + " || true";
            String killString = "kill " + pid + " || true";
            String killStringBusyBox = busyboxPath + "kill " + pid + " || true";
            if (!signal.isEmpty()) {
                killAllStringToolBox = "toolbox kill -s " + signal + " " + pid + " || true";
                killStringToyBox = "toybox kill -s " + signal + " " + pid + " || true";
                killString = "kill -s " + signal + " " + pid + " || true";
                killStringBusyBox = busyboxPath + "kill -s " + signal + " " + pid + " || true";
            }

            result = new ArrayList<>(Arrays.asList(
                    killStringBusyBox,
                    killAllStringToolBox,
                    killStringToyBox,
                    killString
            ));
        }

        return result;
    }

    private boolean doThreeAttemptsToStopModule(String modulePath, String pid, Thread thread, boolean moduleStartedWithRoot) {
        boolean result = false;
        int attempts = 0;
        while (attempts < 3 && !result) {
            if (attempts < 2) {
                result = killModule(modulePath, pid, thread, moduleStartedWithRoot, "", attempts + 2);
            } else {
                result = killModule(modulePath, pid, thread, moduleStartedWithRoot, "SIGKILL", attempts + 1);
            }

            attempts++;
        }
        return result;
    }

    private boolean stopModuleWithInterruptThread(Thread thread) {
        boolean result = false;
        int attempts = 0;

        try {
            while (attempts < 3 && !result) {
                if (thread != null && thread.isAlive()) {
                    thread.interrupt();
                    makeDelay(3);
                }

                if (thread != null) {
                    result = !thread.isAlive();
                }

                attempts++;
            }
        } catch (Exception e) {
            loge("Kill with interrupt thread", e);
        }

        return result;
    }

    private String readPidFile(String path) {
        String pid = "";

        File file = new File(path);
        if (file.isFile()) {
            List<String> lines = FileManager.readTextFileSynchronous(service, path);

            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    pid = line.trim();
                    break;
                }
            }
        }
        return pid;
    }

    @SuppressWarnings("deprecation")
    public static void forceCloseApp(PathVars pathVars) {
        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        if (modulesStatus.isRootAvailable()) {

            String iptablesPath = pathVars.getIptablesPath();
            String ip6tablesPath = pathVars.getIp6tablesPath();
            String busyboxPath = pathVars.getBusyboxPath();

            modulesStatus.setUseModulesWithRoot(true);
            modulesStatus.setDnsCryptState(STOPPED);
            modulesStatus.setTorState(STOPPED);
            modulesStatus.setItpdState(STOPPED);

            final String[] commands = new String[]{
                    ip6tablesPath + "-D OUTPUT -j DROP 2> /dev/null || true",
                    ip6tablesPath + "-I OUTPUT -j DROP",
                    iptablesPath + "-t nat -F tordnscrypt_nat_output 2> /dev/null",
                    iptablesPath + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                    iptablesPath + "-F tordnscrypt 2> /dev/null",
                    iptablesPath + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",
                    iptablesPath + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                    iptablesPath + "-F tordnscrypt_forward 2> /dev/null",
                    iptablesPath + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                    iptablesPath + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                    busyboxPath + "killall -s SIGKILL libdnscrypt-proxy.so || true",
                    busyboxPath + "killall -s SIGKILL libtor.so || true",
                    busyboxPath + "killall -s SIGKILL libi2pd.so || true"
            };

            new Thread(() -> Shell.SU.run(commands)).start();
        }
    }
}
