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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;

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
    private final Context context;
    private final PathVars pathVars;

    private final ModulesStatus modulesStatus;

    private Thread dnsCryptThread;
    private Thread torThread;
    private Thread itpdThread;

    ModulesKiller(Context context, PathVars pathVars) {
        this.context = context;
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

    @SuppressWarnings("deprecation")
    private synchronized boolean killWithPKill(String module, Thread thread, boolean killWithRoot, String signal, int delaySec) {
        boolean result = false;

        String killString = pathVars.busyboxPath + "pkill -f " + module;
        if (!signal.isEmpty()) {
            killString = pathVars.busyboxPath + "pkill -f -" + signal + " " + module;
        }

        if (thread == null || killWithRoot) {
            String sleep = pathVars.busyboxPath + "sleep " + delaySec;
            String checkString = pathVars.busyboxPath + "pgrep -fl " + module;
            List<String> shellResult = Shell.SU.run(new String[]{killString, sleep, checkString});

            if (shellResult != null) {
                result = !shellResult.toString().contains(module.toLowerCase().trim());
            }

            Log.i(LOG_TAG, "Kill " + module + " with root: result " + result);
        } else {
            Shell.SH.run(killString);
            makeDelay(delaySec);
            result = !thread.isAlive();

            Log.i(LOG_TAG, "Kill " + module + " without root: result " + result);

        }
        return result;
    }

    private void sendResultIntent(int moduleMark, String moduleKeyWord, String binaryPath) {
        RootCommands comResult = new RootCommands(new String[]{moduleKeyWord, binaryPath});
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult", comResult);
        intent.putExtra("Mark", moduleMark);
        context.sendBroadcast(intent);
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

    Runnable getDNSCryptKillerRunnable() {
        return new Runnable() {
            @Override
            public void run() {

                if (modulesStatus.getDnsCryptState() != RESTARTING) {
                    modulesStatus.setDnsCryptState(STOPPING);
                }

                int attempts = 0;
                boolean result = false;
                boolean moduleStartedWithRoot = new PrefManager(context).getBoolPref("DNSCryptStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                while (attempts < 3 && !result) {
                    if (attempts < 2) {
                        result = killWithPKill(pathVars.dnscryptPath, dnsCryptThread, moduleStartedWithRoot,"", attempts + 1);
                    } else {
                        result = killWithPKill(pathVars.dnscryptPath, dnsCryptThread, moduleStartedWithRoot,"SIGKILL", attempts + 1);
                    }

                    attempts++;
                }

                if (!result) {

                    if (!moduleStartedWithRoot) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop DNSCrypt. Stop with interrupt thread!");

                        makeDelay(5);

                        if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
                            dnsCryptThread.interrupt();
                            makeDelay(5);
                        }

                        if (dnsCryptThread != null) {
                            result = !dnsCryptThread.isAlive();
                        }
                    }

                    if (rootIsAvailable && !result) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop DNSCrypt. Stop with root method!");
                        result = killWithPKill(pathVars.dnscryptPath, dnsCryptThread, true, "SIGKILL", 10);
                    }
                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("DNSCrypt Running", true);
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                        }

                        modulesStatus.setDnsCryptState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop DNSCrypt!");

                    } else {
                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("DNSCrypt Running", false);
                            modulesStatus.setDnsCryptState(STOPPED);
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                        }
                    }
                } else {
                    if (dnsCryptThread != null && dnsCryptThread.isAlive()) {

                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("DNSCrypt Running", true);
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                        }

                        modulesStatus.setDnsCryptState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop DNSCrypt!");
                    } else {

                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("DNSCrypt Running", false);
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

                if (modulesStatus.getTorState() != RESTARTING) {
                    modulesStatus.setTorState(STOPPING);
                }

                int attempts = 0;
                boolean result = false;
                boolean moduleStartedWithRoot = new PrefManager(context).getBoolPref("TorStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                while (attempts < 3 && !result) {
                    if (attempts < 2) {
                        result = killWithPKill(pathVars.torPath, torThread, moduleStartedWithRoot, "", attempts + 1);
                    } else {
                        result = killWithPKill(pathVars.torPath, torThread, moduleStartedWithRoot, "SIGKILL", attempts + 1);
                    }

                    attempts++;
                }

                if (!result) {

                    if (!moduleStartedWithRoot) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop Tor. Stop with interrupt thread!");

                        makeDelay(5);

                        if (torThread != null && torThread.isAlive()) {
                            torThread.interrupt();
                            makeDelay(5);
                        }


                        if (torThread != null) {
                            result = !torThread.isAlive();
                        }
                    }

                    if (rootIsAvailable && !result) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop Tor. Stop with root method!");
                        result = killWithPKill(pathVars.torPath, torThread, true, "SIGKILL", 10);
                    }
                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getTorState() != RESTARTING) {
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                            new PrefManager(context).setBoolPref("Tor Running", true);
                        }

                        modulesStatus.setTorState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop Tor!");

                    } else {
                        if (modulesStatus.getTorState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("Tor Running", false);
                            modulesStatus.setTorState(STOPPED);
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                        }
                    }
                } else {
                    if (torThread != null && torThread.isAlive()) {

                        if (modulesStatus.getTorState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("Tor Running", true);
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                        }

                        modulesStatus.setTorState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop Tor!");
                    } else {

                        if (modulesStatus.getTorState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("Tor Running", false);
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

                if (modulesStatus.getItpdState() != RESTARTING) {
                    modulesStatus.setItpdState(STOPPING);
                }

                int attempts = 0;
                boolean result = false;
                boolean moduleStartedWithRoot = new PrefManager(context).getBoolPref("ITPDStartedWithRoot");
                boolean rootIsAvailable = modulesStatus.isRootAvailable();

                while (attempts < 3 && !result) {
                    if (attempts < 2) {
                        result = killWithPKill(pathVars.itpdPath, itpdThread, moduleStartedWithRoot, "", attempts + 1);
                    } else {
                        result = killWithPKill(pathVars.itpdPath, itpdThread, moduleStartedWithRoot, "SIGKILL", attempts + 1);
                    }

                    attempts++;
                }

                if (!result) {
                    if (!moduleStartedWithRoot) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop I2P. Stop with interrupt thread!");

                        makeDelay(5);

                        if (itpdThread != null && itpdThread.isAlive()) {
                            itpdThread.interrupt();
                            makeDelay(5);
                        }

                        if (itpdThread != null) {
                            result = !itpdThread.isAlive();
                        }
                    }

                    if (rootIsAvailable && !result) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop I2P. Stop with root method!");
                        result = killWithPKill(pathVars.itpdPath, itpdThread, true, "SIGKILL", 10);
                    }
                }

                if (moduleStartedWithRoot) {
                    if (!result) {
                        if (modulesStatus.getItpdState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("I2PD Running", true);
                            sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                        }

                        modulesStatus.setItpdState(RUNNING);

                        Log.e(LOG_TAG, "ModulesKiller cannot stop I2P!");

                    } else {
                        if (modulesStatus.getItpdState() != RESTARTING) {
                            new PrefManager(context).setBoolPref("I2PD Running", false);
                            modulesStatus.setItpdState(STOPPED);
                            sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                        }

                    }
                }

                if (itpdThread != null && itpdThread.isAlive()) {

                    if (modulesStatus.getItpdState() != RESTARTING) {
                        new PrefManager(context).setBoolPref("I2PD Running", true);
                        sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                    }

                    modulesStatus.setItpdState(RUNNING);

                    Log.e(LOG_TAG, "ModulesKiller cannot stop I2P!");
                } else {

                    if (modulesStatus.getItpdState() != RESTARTING) {
                        new PrefManager(context).setBoolPref("I2PD Running", false);
                        modulesStatus.setItpdState(STOPPED);
                        sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                    }
                }
            }
        };
    }
}
