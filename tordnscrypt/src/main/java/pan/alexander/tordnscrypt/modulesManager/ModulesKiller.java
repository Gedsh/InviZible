package pan.alexander.tordnscrypt.modulesManager;

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

import static pan.alexander.tordnscrypt.modulesManager.ModulesService.DNSCRYPT_KEYWORD;
import static pan.alexander.tordnscrypt.modulesManager.ModulesService.ITPD_KEYWORD;
import static pan.alexander.tordnscrypt.modulesManager.ModulesService.TOR_KEYWORD;
import static pan.alexander.tordnscrypt.utils.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.RootExecService.DNSCryptRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.I2PDRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.RootExecService.TorRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

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

    public static void stopModulesWithRootIfRunning(Context context, PathVars pathVars) {
        boolean dnsCryptRunning = new PrefManager(context).getBoolPref("DNSCrypt Running");
        boolean torRunning = new PrefManager(context).getBoolPref("Tor Running");
        boolean itpdRunning = new PrefManager(context).getBoolPref("I2PD Running");

        final ModulesKiller modulesKiller = new ModulesKiller(context, pathVars);

        if (dnsCryptRunning) {
            new PrefManager(context).setBoolPref("DNSCrypt Running", false);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    modulesKiller.killWithKillAll("dnscrypt-proxy", null, true);
                }
            }).start();

        }

        if (torRunning) {
            new PrefManager(context).setBoolPref("Tor Running", false);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    modulesKiller.killWithKillAll("tor", null, true);
                }
            }).start();

        }

        if (itpdRunning) {
            new PrefManager(context).setBoolPref("I2PD Running", false);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    modulesKiller.killWithKillAll("i2pd", null, true);
                }
            }).start();
        }
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
    private synchronized boolean killWithKillAll(String module, Thread thread, boolean killWithRoot) {
        boolean result = false;
        if (killWithRoot) {
            String killString = pathVars.busyboxPath + "killall " + module;
            //String killString = "";
            String sleep = pathVars.busyboxPath + "sleep 3";
            String checkString = pathVars.busyboxPath + "pgrep -l " + module;
            List<String> shellResult = Shell.SU.run(new String[]{killString, sleep, checkString});
            if (shellResult == null) {
                result = false;
            } else {
                result = !shellResult.toString().contains(module);
            }

            Log.i(LOG_TAG, "Kill " + module + " with root: result " + result);
        } else if (thread != null) {
            String killString = pathVars.busyboxPath + "killall " + module;
            Shell.SH.run(killString);
            makeDelay(2);
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
                int attempts = 0;
                boolean result = false;

                while (attempts < 3 && !result) {
                    result = killWithKillAll("dnscrypt-proxy", dnsCryptThread, modulesStatus.isUseModulesWithRoot());
                    attempts++;
                }

                if (!result) {
                    if (modulesStatus.isRootAvailable()) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop DNSCrypt. Stop with root method!");
                        killWithKillAll("dnscrypt-proxy", dnsCryptThread, true);
                    } else {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop DNSCrypt. Stop with interrupt thread!");
                        if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
                            dnsCryptThread.interrupt();
                        }
                    }

                    makeDelay(10);
                }

                if (modulesStatus.isUseModulesWithRoot()) {
                    if (!result) {
                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                            modulesStatus.setDnsCryptState(RUNNING);
                        }

                        Log.e(LOG_TAG, "ModulesKiller cannot stop DNSCrypt!");

                    } else {
                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                            modulesStatus.setDnsCryptState(STOPPED);
                        }
                    }
                } else {
                    if (dnsCryptThread != null && dnsCryptThread.isAlive()) {

                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                            modulesStatus.setDnsCryptState(RUNNING);
                        }

                        Log.e(LOG_TAG, "ModulesKiller cannot stop DNSCrypt!");
                    } else {

                        if (modulesStatus.getDnsCryptState() != RESTARTING) {
                            sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                            modulesStatus.setDnsCryptState(STOPPED);
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
                int attempts = 0;
                boolean result = false;

                while (attempts < 3 && !result) {
                    result = killWithKillAll("tor", torThread, modulesStatus.isUseModulesWithRoot());
                    attempts++;
                }

                if (!result) {
                    if (modulesStatus.isRootAvailable()) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop Tor. Stop with root method!");
                        killWithKillAll("tor", torThread, true);
                    } else {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop Tor. Stop with interrupt thread!");
                        if (torThread != null && torThread.isAlive()) {
                            torThread.interrupt();
                        }
                    }

                    makeDelay(10);
                }

                if (modulesStatus.isUseModulesWithRoot()) {
                    if (!result) {
                        if (modulesStatus.getTorState() != RESTARTING) {
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                            modulesStatus.setTorState(RUNNING);
                        }

                        Log.e(LOG_TAG, "ModulesKiller cannot stop Tor!");

                    } else {
                        if (modulesStatus.getTorState() != RESTARTING) {
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                            modulesStatus.setTorState(STOPPED);
                        }
                    }
                } else {
                    if (torThread != null && torThread.isAlive()) {

                        if (modulesStatus.getTorState() != RESTARTING) {
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                            modulesStatus.setTorState(RUNNING);
                        }

                        Log.e(LOG_TAG, "ModulesKiller cannot stop Tor!");
                    } else {

                        if (modulesStatus.getTorState() != RESTARTING) {
                            sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                            modulesStatus.setTorState(STOPPED);
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
                int attempts = 0;
                boolean result = false;

                while (attempts < 3 && !result) {
                    result = killWithKillAll("i2pd", itpdThread, modulesStatus.isUseModulesWithRoot());
                    attempts++;
                }

                if (!result) {
                    if (modulesStatus.isRootAvailable()) {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop I2P. Stop with root method!");
                        killWithKillAll("i2pd", itpdThread, true);
                    } else {
                        Log.w(LOG_TAG, "ModulesKiller cannot stop I2P. Stop with interrupt thread!");
                        if (itpdThread != null && itpdThread.isAlive()) {
                            itpdThread.interrupt();
                        }
                    }

                    makeDelay(3);
                }

                if (modulesStatus.isUseModulesWithRoot()) {
                    if (!result) {
                        if (modulesStatus.getItpdState() != RESTARTING) {
                            sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                            modulesStatus.setItpdState(RUNNING);
                        }

                        Log.e(LOG_TAG, "ModulesKiller cannot stop I2P!");

                    } else {
                        if (modulesStatus.getItpdState() != RESTARTING) {
                            sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                            modulesStatus.setItpdState(STOPPED);
                        }

                    }
                }

                if (itpdThread != null && itpdThread.isAlive()) {

                    if (modulesStatus.getItpdState() != RESTARTING) {
                        sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                        modulesStatus.setItpdState(RUNNING);
                    }

                    Log.e(LOG_TAG, "ModulesKiller cannot stop I2P!");
                } else {

                    if (modulesStatus.getItpdState() != RESTARTING) {
                        sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                        modulesStatus.setItpdState(STOPPED);
                    }
                }
            }
        };
    }
}
