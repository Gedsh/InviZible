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

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;
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
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

class ModulesStarterHelper {

    private final ModulesService service;
    private final Handler handler;
    private final PathVars pathVars;

    private ModulesStatus modulesStatus;

    ModulesStarterHelper(ModulesService service, Handler handler, PathVars pathVars) {
        this.service = service;
        this.handler = handler;
        this.pathVars = pathVars;
        this.modulesStatus = ModulesStatus.getInstance();
    }

    Runnable getDNSCryptStarterRunnable() {
        return () -> {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            String dnsCmdString;
            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {

                dnsCmdString = pathVars.busyboxPath + "nohup " + pathVars.dnscryptPath
                        + " -config " + pathVars.appDataDir
                        + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml -pidfile "+ pathVars.appDataDir
                        + "/dnscrypt-proxy.pid >/dev/null 2>&1 &";
                String waitString = pathVars.busyboxPath + "sleep 3";
                String checkIfModuleRunning = pathVars.busyboxPath + "pgrep -l /libdnscrypt-proxy.so";

                shellResult = Shell.SU.run(dnsCmdString, waitString, checkIfModuleRunning);

                new PrefManager(service).setBoolPref("DNSCryptStartedWithRoot", true);

                if (shellResult.getStdout().contains(pathVars.dnscryptPath)) {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                } else {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                }

            } else {
                dnsCmdString = pathVars.dnscryptPath + " -config " + pathVars.appDataDir
                        + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml -pidfile " + pathVars.appDataDir + "/dnscrypt-proxy.pid";
                new PrefManager(service).setBoolPref("DNSCryptStartedWithRoot", false);

                shellResult = Shell.SH.run(dnsCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getDnsCryptState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getDnsCryptState() != STOPPING && modulesStatus.getDnsCryptState() != STOPPED) {

                    handler.post(() -> Toast.makeText(service, "DNSCrypt Module Fault: "
                            + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr()
                            + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());
                }

                if (modulesStatus.getDnsCryptState() == STARTING) {
                    if (modulesStatus.isRootAvailable()) {
                        forceStopModulesWithRootMethod();
                    } else {
                        forceStopModulesWithService();
                    }
                }

                modulesStatus.setDnsCryptState(STOPPED);

                sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");

                Log.e(LOG_TAG, "Error DNSCrypt: "
                        + shellResult.exitCode + " ERR=" + shellResult.getStderr()
                        + " OUT=" + shellResult.getStdout());
            }
        };
    }

    Runnable getTorStarterRunnable() {
        return () -> {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            String torCmdString;
            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {

                correctTorConfRunAsDaemon(service, pathVars.appDataDir, true);

                torCmdString = pathVars.torPath + " -f "
                        + pathVars.appDataDir + "/app_data/tor/tor.conf -pidfile " + pathVars.appDataDir + "/tor.pid";
                String waitString = pathVars.busyboxPath + "sleep 3";
                String checkIfModuleRunning = pathVars.busyboxPath + "pgrep -l /libtor.so";

                shellResult = Shell.SU.run(torCmdString, waitString, checkIfModuleRunning);

                new PrefManager(service).setBoolPref("TorStartedWithRoot", true);

                if (shellResult.getStdout().contains(pathVars.torPath)) {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                } else {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                }

            } else {
                correctTorConfRunAsDaemon(service, pathVars.appDataDir, false);
                torCmdString = pathVars.torPath + " -f "
                        + pathVars.appDataDir + "/app_data/tor/tor.conf -pidfile " + pathVars.appDataDir + "/tor.pid";
                new PrefManager(service).setBoolPref("TorStartedWithRoot", false);

                shellResult = Shell.SH.run(torCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getTorState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getTorState() != STOPPING && modulesStatus.getTorState() != STOPPED) {
                    handler.post(() -> Toast.makeText(service, "Tor Module Fault: " + shellResult.exitCode
                            + "\n\n ERR = " + shellResult.getStderr()
                            + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());


                }

                if (modulesStatus.getTorState() == STARTING) {
                    if (modulesStatus.isRootAvailable()) {
                        forceStopModulesWithRootMethod();
                    } else {
                        forceStopModulesWithService();
                    }

                    //Try to update Selinux context and UID once again
                    if (shellResult.exitCode == 1) {
                        modulesStatus.setContextUIDUpdateRequested(true);
                    }
                }

                modulesStatus.setTorState(STOPPED);

                sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");

                Log.e(LOG_TAG, "Error Tor: " + shellResult.exitCode
                        + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
            }
        };
    }

    Runnable getITPDStarterRunnable() {
        return () -> {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            String itpdCmdString;

            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {
                correctITPDConfRunAsDaemon(service, pathVars.appDataDir, true);

                Shell.SU.run(pathVars.busyboxPath + "mkdir -p " + pathVars.appDataDir + "/i2pd_data",
                        "cd " + pathVars.appDataDir + "/app_data/i2pd",
                        pathVars.busyboxPath + "cp -R certificates " + pathVars.appDataDir + "/i2pd_data");

                itpdCmdString = pathVars.itpdPath + " --conf " + pathVars.appDataDir
                        + "/app_data/i2pd/i2pd.conf --datadir " + pathVars.appDataDir
                        + "/i2pd_data --pidfile " + pathVars.appDataDir + "/i2pd.pid &";
                String waitString = pathVars.busyboxPath + "sleep 3";
                String checkIfModuleRunning = pathVars.busyboxPath + "pgrep -l /libi2pd.so";

                shellResult = Shell.SU.run(itpdCmdString, waitString, checkIfModuleRunning);

                new PrefManager(service).setBoolPref("ITPDStartedWithRoot", true);

                if (shellResult.getStdout().contains(pathVars.itpdPath)) {
                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                } else {
                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                }

            } else {
                correctITPDConfRunAsDaemon(service, pathVars.appDataDir, false);
                itpdCmdString = pathVars.itpdPath + " --conf " + pathVars.appDataDir
                        + "/app_data/i2pd/i2pd.conf --datadir " + pathVars.appDataDir
                        + "/i2pd_data --pidfile " + pathVars.appDataDir + "/i2pd.pid";
                new PrefManager(service).setBoolPref("ITPDStartedWithRoot", false);

                shellResult = Shell.SH.run(itpdCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getItpdState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getItpdState() != STOPPING && modulesStatus.getItpdState() != STOPPED) {
                    handler.post(() -> Toast.makeText(service, "Purple I2P Module Fault: "
                            + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr()
                            + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());
                }

                if (modulesStatus.getItpdState() == STARTING) {
                    if (modulesStatus.isRootAvailable()) {
                        forceStopModulesWithRootMethod();
                    } else {
                        forceStopModulesWithService();
                    }
                }

                modulesStatus.setItpdState(STOPPED);

                sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");

                Log.e(LOG_TAG, "Error ITPD: " + shellResult.exitCode + " ERR="
                        + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
            }
        };
    }

    private void correctTorConfRunAsDaemon(Context context, String appDataDir, boolean runAsDaemon) {
        String path = appDataDir + "/app_data/tor/tor.conf";
        List<String> lines = FileOperations.readTextFileSynchronous(context, path);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("RunAsDaemon")) {
                if (runAsDaemon && lines.get(i).contains("0")) {
                    lines.set(i, "RunAsDaemon 1");
                    FileOperations.writeTextFileSynchronous(context, path, lines);
                } else if (!runAsDaemon && lines.get(i).contains("1")) {
                    lines.set(i, "RunAsDaemon 0");
                    FileOperations.writeTextFileSynchronous(context, path, lines);
                }
                return;
            }
        }
    }

    private void correctITPDConfRunAsDaemon(Context context, String appDataDir, boolean runAsDaemon) {
        String path = appDataDir + "/app_data/i2pd/i2pd.conf";
        List<String> lines = FileOperations.readTextFileSynchronous(context, path);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("daemon")) {
                if (runAsDaemon && lines.get(i).contains("false")) {
                    lines.set(i, "daemon = true");
                    FileOperations.writeTextFileSynchronous(context, path, lines);
                } else if (!runAsDaemon && lines.get(i).contains("true")) {
                    lines.set(i, "daemon = false");
                    FileOperations.writeTextFileSynchronous(context, path, lines);
                }
                return;
            }
        }
    }

    private void sendResultIntent(int moduleMark, String moduleKeyWord, String binaryPath) {
        RootCommands comResult = new RootCommands(new String[]{moduleKeyWord, binaryPath});
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult", comResult);
        intent.putExtra("Mark", moduleMark);
        service.sendBroadcast(intent);
    }

    private void forceStopModulesWithService() {

        if (modulesStatus.isUseModulesWithRoot()) {
            return;
        }

        Log.e(LOG_TAG, "FORCE CLOSE ALL NO ROOT METHOD");

        modulesStatus.setUseModulesWithRoot(true);
        modulesStatus.setDnsCryptState(STOPPED);
        modulesStatus.setTorState(STOPPED);
        modulesStatus.setItpdState(STOPPED);

        handler.postDelayed(() -> Toast.makeText(service, R.string.top_fragment_address_already_in_use, Toast.LENGTH_LONG).show(), 5000);

        handler.postDelayed(() -> System.exit(0), 10000);
    }

    private void forceStopModulesWithRootMethod() {

        Log.e(LOG_TAG, "FORCE CLOSE ALL ROOT METHOD");

        ModulesKiller.forceCloseApp(new PathVars(service));

        handler.postDelayed(() -> Toast.makeText(service, R.string.top_fragment_address_already_in_use, Toast.LENGTH_LONG).show(), 5000);

        handler.postDelayed(() -> System.exit(0), 10000);
    }
}
