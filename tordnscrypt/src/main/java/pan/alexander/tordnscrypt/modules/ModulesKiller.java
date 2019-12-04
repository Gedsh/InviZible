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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.modules.ModulesService.DNSCRYPT_KEYWORD;
import static pan.alexander.tordnscrypt.modules.ModulesService.ITPD_KEYWORD;
import static pan.alexander.tordnscrypt.modules.ModulesService.TOR_KEYWORD;
import static pan.alexander.tordnscrypt.utils.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.RootExecService.DNSCryptRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.I2PDRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.RootExecService.TorRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

public class ModulesKiller {
    private final Service service;
    private final PathVars pathVars;

    private final ModulesStatus modulesStatus;

    private Thread dnsCryptThread;
    private Thread torThread;
    private Thread itpdThread;

    ModulesKiller(Service service, PathVars pathVars) {
        this.service = service;
        this.pathVars = pathVars;
        modulesStatus = ModulesStatus.getInstance();
    }

    public static void stopDNSCrypt(Context context) {
        sendStopIntent(context, ModulesService.actionStopDnsCrypt);
    }

    public static void stopTor(Context context) {
        sendStopIntent(context, ModulesService.actionStopTor);
    }

    public static void stopITPD(Context context) {
        sendStopIntent(context, ModulesService.actionStopITPD);
    }

    private static void sendStopIntent(Context context, String action) {
        Intent intent = new Intent(context, ModulesService.class);
        intent.setAction(action);
        intent.putExtra("showNotification", isShowNotification(context));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static boolean isShowNotification(Context context) {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        return shPref.getBoolean("swShowNotification", true);
    }

    private void sendResultIntent(int moduleMark, String moduleKeyWord, String binaryPath) {
        RootCommands comResult = new RootCommands(new String[]{moduleKeyWord, binaryPath});
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult", comResult);
        intent.putExtra("Mark", moduleMark);
        service.sendBroadcast(intent);
    }

    private void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Modules killer makeDelay interrupted! " + e.getMessage() + " " + e.getCause());
        }
    }

    void setDnsCryptThread(Thread dnsCryptThread) {
        this.dnsCryptThread = dnsCryptThread;
    }

    void setTorThread(Thread torThread) {
        this.torThread = torThread;
    }

    void setItpdThread(Thread itpdThread) {
        this.itpdThread = itpdThread;
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
        return new Runnable() {
            @Override
            public void run() {

                String dnsCryptPid = readPidFile(pathVars.appDataDir + "/dnscrypt-proxy.pid");

                if (modulesStatus.getDnsCryptState() != RESTARTING) {
                    modulesStatus.setDnsCryptState(STOPPING);
                }

                boolean moduleStartedWithRoot = new PrefManager(service).getBoolPref("DNSCryptStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                boolean result = doThreeAttemptsToStopModule(pathVars.dnscryptPath, dnsCryptPid, dnsCryptThread, moduleStartedWithRoot);

                if (!result) {

                    if (!moduleStartedWithRoot) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop DNSCrypt. Stop with interrupt thread!");

                        makeDelay(5);

                        result = stopModuleWithInterruptThread(dnsCryptThread);
                    }

                    if (rootIsAvailable && !result) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop DNSCrypt. Stop with root method!");
                        result = killModule(pathVars.dnscryptPath, dnsCryptPid, dnsCryptThread, true, "SIGKILL", 10);
                    }
                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("DNSCrypt Running", true);
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                        }

                        modulesStatus.setDnsCryptState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop DNSCrypt!");

                    } else {
                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("DNSCrypt Running", false);
                            modulesStatus.setDnsCryptState(STOPPED);
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                        }
                    }
                } else {
                    if (dnsCryptThread != null && dnsCryptThread.isAlive()) {

                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("DNSCrypt Running", true);
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                        }

                        modulesStatus.setDnsCryptState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop DNSCrypt!");
                    } else {

                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("DNSCrypt Running", false);
                            modulesStatus.setDnsCryptState(STOPPED);
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                        }
                    }
                }

            }
        };
    }


    Runnable getTorKillerRunnable() {
        return new Runnable() {
            @Override
            public void run() {

                String torPid = readPidFile(pathVars.appDataDir + "/tor.pid");

                if (modulesStatus.getTorState() != RESTARTING) {
                    modulesStatus.setTorState(STOPPING);
                }

                boolean moduleStartedWithRoot = new PrefManager(service).getBoolPref("TorStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                boolean result = doThreeAttemptsToStopModule(pathVars.torPath, torPid, torThread, moduleStartedWithRoot);

                if (!result) {

                    if (!moduleStartedWithRoot) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop Tor. Stop with interrupt thread!");

                        makeDelay(5);

                        result = stopModuleWithInterruptThread(torThread);
                    }

                    if (rootIsAvailable && !result) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop Tor. Stop with root method!");
                        result = killModule(pathVars.torPath, torPid, torThread, true, "SIGKILL", 10);
                    }
                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getTorState() != RESTARTING) {
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                            new PrefManager(service).setBoolPref("Tor Running", true);
                        }

                        modulesStatus.setTorState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop Tor!");

                    } else {
                        if (modulesStatus.getTorState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("Tor Running", false);
                            modulesStatus.setTorState(STOPPED);
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                        }
                    }
                } else {
                    if (torThread != null && torThread.isAlive()) {

                        if (modulesStatus.getTorState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("Tor Running", true);
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                        }

                        modulesStatus.setTorState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop Tor!");
                    } else {

                        if (modulesStatus.getTorState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("Tor Running", false);
                            modulesStatus.setTorState(STOPPED);
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                        }
                    }
                }
            }
        };
    }

    Runnable getITPDKillerRunnable() {
        return new Runnable() {
            @Override
            public void run() {

                String itpdPid = readPidFile(pathVars.appDataDir + "/i2pd.pid");

                if (modulesStatus.getItpdState() != RESTARTING) {
                    modulesStatus.setItpdState(STOPPING);
                }

                boolean moduleStartedWithRoot = new PrefManager(service).getBoolPref("ITPDStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                boolean result = doThreeAttemptsToStopModule(pathVars.itpdPath, itpdPid, itpdThread, moduleStartedWithRoot);

                if (!result) {
                    if (!moduleStartedWithRoot) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop I2P. Stop with interrupt thread!");

                        makeDelay(5);

                        result = stopModuleWithInterruptThread(itpdThread);
                    }

                    if (rootIsAvailable && !result) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop I2P. Stop with root method!");
                        result = killModule(pathVars.itpdPath, itpdPid, itpdThread, true, "SIGKILL", 10);
                    }
                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getItpdState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("I2PD Running", true);
                            sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                        }

                        modulesStatus.setItpdState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop I2P!");

                    } else {
                        if (modulesStatus.getItpdState() != RESTARTING) {
                            new PrefManager(service).setBoolPref("I2PD Running", false);
                            modulesStatus.setItpdState(STOPPED);
                            sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                        }

                    }
                }

                if (itpdThread != null && itpdThread.isAlive()) {

                    if (modulesStatus.getItpdState() != RESTARTING) {
                        new PrefManager(service).setBoolPref("I2PD Running", true);
                        sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                    }

                    modulesStatus.setItpdState(RUNNING);

                    Log.e(LOG_TAG, "ModulesKiller cannot stop I2P!");
                } else {

                    if (modulesStatus.getItpdState() != RESTARTING) {
                        new PrefManager(service).setBoolPref("I2PD Running", false);
                        modulesStatus.setItpdState(STOPPED);
                        sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                    }
                }
            }
        };
    }

    private synchronized boolean killModule(String module, String pid, Thread thread, boolean killWithRoot, String signal, int delaySec) {
        boolean result = false;

        if (module.contains("/")) {
            module = module.substring(module.lastIndexOf("/"));
        }

        String[] preparedCommands = prepareKillCommands(module, pid, signal);

        if (thread == null || killWithRoot) {
            String sleep = pathVars.busyboxPath + "sleep " + delaySec;
            String checkString = pathVars.busyboxPath + "pgrep -l " + module;

            String[] commands = Arr.ADD2(preparedCommands, new String[]{sleep, checkString});

            List<String> shellResult = killWithSU(module, commands);

            if (shellResult != null) {
                result = !shellResult.toString().contains(module.toLowerCase().trim());
            }

            if (shellResult != null) {
                Log.i(LOG_TAG, "Kill " + module + " with root: result " + result + "\n" + shellResult.toString());
            } else {
                Log.i(LOG_TAG, "Kill " + module + " with root: result false");
            }
        } else {
            killWithPid(signal, pid, delaySec);
            result = !thread.isAlive();

            List<String> shellResult = null;
            if (!result) {
                shellResult = killWithSH(module, preparedCommands, delaySec);
                result = !thread.isAlive();
            }

            if (shellResult != null) {
                Log.i(LOG_TAG, "Kill " + module + " without root: result " + result + "\n" + shellResult.toString());
            } else {
                Log.i(LOG_TAG, "Kill " + module + " without root: result " + result);
            }
        }

        return result;
    }

    private void killWithPid(String signal, String pid, int delay) {
        try {
            if (signal.isEmpty()) {
                android.os.Process.sendSignal(Integer.valueOf(pid), 15);
            } else {
                android.os.Process.killProcess(Integer.valueOf(pid));
            }
            makeDelay(delay);
        } catch (Exception e) {
            Log.e(LOG_TAG, "ModulesKiller killWithPid exception " + e.getMessage() + " " + e.getCause());
        }
    }

    @SuppressWarnings("deprecation")
    private List<String> killWithSH(String module, String[] commands, int delay) {
        List<String> shellResult = null;
        try {
            shellResult = Shell.SH.run(commands);
            makeDelay(delay);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Kill " + module + " without root exception " + e.getMessage() + " " + e.getCause());
        }
        return shellResult;
    }

    @SuppressWarnings("deprecation")
    private List<String> killWithSU(String module, String[] commands) {
        List<String> shellResult = null;
        try {
            shellResult = Shell.SU.run(commands);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Kill " + module + " with root exception " + e.getMessage() + " " + e.getCause());
        }
        return shellResult;
    }

    private String[] prepareKillCommands(String module, String pid, String signal) {
        String[] result;

        if (pid.isEmpty()) {
            String killStringBusybox = pathVars.busyboxPath + "pkill " + module;
            String killAllStringBusybox = pathVars.busyboxPath + "kill $(pgrep " + module + ")";
            String killStringToyBox = "toybox pkill " + module;
            String killString = "pkill " + module;
            if (!signal.isEmpty()) {
                killStringBusybox = pathVars.busyboxPath + "pkill -" + signal + " " + module;
                killAllStringBusybox = pathVars.busyboxPath + "kill -s " + signal + " $(pgrep " + module + ")";
                killStringToyBox = "toybox pkill -" + signal + " " + module;
                killString = "pkill -" + signal + " " + module;
            }

            result = new String[]{
                    killStringBusybox,
                    killAllStringBusybox,
                    killStringToyBox,
                    killString
            };
        } else {
            String killStringBusyBox = pathVars.busyboxPath + "kill " + pid;
            String killAllStringToolBox = "toolbox kill " + pid;
            String killStringToyBox = "toybox kill " + pid;
            String killString = "kill " + pid;
            if (!signal.isEmpty()) {
                killStringBusyBox = pathVars.busyboxPath + "kill -s " + signal + " " + pid;
                killAllStringToolBox = "toollbox kill -s " + signal + " " + pid;
                killStringToyBox = "toybox kill -s " + signal + " " + pid;
                killString = "kill -s " + signal + " " + pid;
            }

            result = new String[]{
                    killStringBusyBox,
                    killAllStringToolBox,
                    killStringToyBox,
                    killString
            };
        }

        return result;
    }

    private boolean doThreeAttemptsToStopModule(String modulePath, String pid, Thread thread, boolean moduleStartedWithRoot) {
        boolean result = false;
        int attempts = 0;
        while (attempts < 3 && !result) {
            if (attempts < 2) {
                result = killModule(modulePath, pid, thread, moduleStartedWithRoot, "", attempts + 1);
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
            Log.e(LOG_TAG, "Kill with interrupt thread exception " + e.getMessage() + " " + e.getCause());
        }

        return result;
    }

    private String readPidFile(String path) {
        String pid = "";

        File file = new File(path);
        if (file.isFile()) {
            List<String> lines = FileOperations.readTextFileSynchronous(service, path);

            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    pid = line.trim();
                    break;
                }
            }
        }
        return pid;
    }
}
