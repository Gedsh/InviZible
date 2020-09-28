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

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.modules.ModulesService.DNSCRYPT_KEYWORD;
import static pan.alexander.tordnscrypt.modules.ModulesService.ITPD_KEYWORD;
import static pan.alexander.tordnscrypt.modules.ModulesService.TOR_KEYWORD;
import static pan.alexander.tordnscrypt.utils.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.RootExecService.DNSCryptRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.I2PDRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.RootExecService.TopFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.TorRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

public class ModulesStarterHelper {

    public final static String ASK_FORCE_CLOSE = "pan.alexander.tordnscrypt.AskForceClose";
    public final static String MODULE_NAME = "pan.alexander.tordnscrypt.ModuleName";

    private final ModulesService service;
    private final Handler handler;
    private String appDataDir;
    private String busyboxPath;
    private String dnscryptPath;
    private String torPath;
    private String torConfPath;
    private String obfsPath;
    private String itpdPath;

    private ModulesStatus modulesStatus;

    ModulesStarterHelper(ModulesService service, Handler handler, PathVars pathVars) {
        this.service = service;
        this.handler = handler;
        appDataDir = pathVars.getAppDataDir();
        busyboxPath = pathVars.getBusyboxPath();
        dnscryptPath = pathVars.getDNSCryptPath();
        torPath = pathVars.getTorPath();
        torConfPath = pathVars.getTorConfPath();
        obfsPath = pathVars.getObfsPath();
        itpdPath = pathVars.getITPDPath();
        this.modulesStatus = ModulesStatus.getInstance();
    }

    Runnable getDNSCryptStarterRunnable() {
        return () -> {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            String dnsCmdString;
            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {

                dnsCmdString = busyboxPath + "nohup " + dnscryptPath
                        + " -config " + appDataDir
                        + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml -pidfile " + appDataDir
                        + "/dnscrypt-proxy.pid >/dev/null 2>&1 &";
                String waitString = busyboxPath + "sleep 3";
                String checkIfModuleRunning = busyboxPath + "pgrep -l /libdnscrypt-proxy.so";

                shellResult = Shell.SU.run(dnsCmdString, waitString, checkIfModuleRunning);

                new PrefManager(service).setBoolPref("DNSCryptStartedWithRoot", true);

                if (shellResult.getStdout().contains(dnscryptPath)) {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, dnscryptPath);
                } else {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                }

            } else {
                dnsCmdString = dnscryptPath + " -config " + appDataDir
                        + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml -pidfile " + appDataDir + "/dnscrypt-proxy.pid";
                new PrefManager(service).setBoolPref("DNSCryptStartedWithRoot", false);

                shellResult = Shell.SH.run(dnsCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getDnsCryptState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getDnsCryptState() != STOPPING && modulesStatus.getDnsCryptState() != STOPPED) {

                    if (appVersion.startsWith("b")) {
                        handler.post(() -> Toast.makeText(service, "DNSCrypt Module Fault: "
                                + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr()
                                + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());
                    }

                    sendAskForceCloseBroadcast(service, "DNSCrypt");
                }

                Log.e(LOG_TAG, "Error DNSCrypt: "
                        + shellResult.exitCode + " ERR=" + shellResult.getStderr()
                        + " OUT=" + shellResult.getStdout());

                modulesStatus.setDnsCryptState(STOPPED);

                ModulesAux.makeModulesStateExtraLoop(service);

                sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
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

                List<String> lines = correctTorConfRunAsDaemon(service, true);

                correctObfsModulePath(lines);

                torCmdString = torPath + " -f "
                        + appDataDir + "/app_data/tor/tor.conf -pidfile " + appDataDir + "/tor.pid";
                String waitString = busyboxPath + "sleep 3";
                String checkIfModuleRunning = busyboxPath + "pgrep -l /libtor.so";

                shellResult = Shell.SU.run(torCmdString, waitString, checkIfModuleRunning);

                new PrefManager(service).setBoolPref("TorStartedWithRoot", true);

                if (shellResult.getStdout().contains(torPath)) {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, torPath);
                } else {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                }

            } else {

                List<String> lines = correctTorConfRunAsDaemon(service, false);

                useTorSchedulerVanilla(lines);

                correctObfsModulePath(lines);

                torCmdString = torPath + " -f "
                        + appDataDir + "/app_data/tor/tor.conf -pidfile " + appDataDir + "/tor.pid";
                new PrefManager(service).setBoolPref("TorStartedWithRoot", false);

                shellResult = Shell.SH.run(torCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getTorState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getTorState() != STOPPING && modulesStatus.getTorState() != STOPPED) {
                    if (appVersion.startsWith("b")) {
                        handler.post(() -> Toast.makeText(service, "Tor Module Fault: " + shellResult.exitCode
                                + "\n\n ERR = " + shellResult.getStderr()
                                + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());
                    }

                    sendAskForceCloseBroadcast(service, "Tor");

                    //Try to update Selinux context and UID once again
                    if (shellResult.exitCode == 1) {
                        modulesStatus.setContextUIDUpdateRequested(true);
                        ModulesAux.makeModulesStateExtraLoop(service);
                    }
                }

                Log.e(LOG_TAG, "Error Tor: " + shellResult.exitCode
                        + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                modulesStatus.setTorState(STOPPED);

                ModulesAux.makeModulesStateExtraLoop(service);

                sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
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
                correctITPDConfRunAsDaemon(service, appDataDir, true);

                Shell.SU.run(busyboxPath + "mkdir -p " + appDataDir + "/i2pd_data",
                        "cd " + appDataDir + "/app_data/i2pd",
                        busyboxPath + "cp -R certificates " + appDataDir + "/i2pd_data");

                itpdCmdString = itpdPath + " --conf " + appDataDir
                        + "/app_data/i2pd/i2pd.conf --datadir " + appDataDir
                        + "/i2pd_data --pidfile " + appDataDir + "/i2pd.pid &";
                String waitString = busyboxPath + "sleep 3";
                String checkIfModuleRunning = busyboxPath + "pgrep -l /libi2pd.so";

                shellResult = Shell.SU.run(itpdCmdString, waitString, checkIfModuleRunning);

                new PrefManager(service).setBoolPref("ITPDStartedWithRoot", true);

                if (shellResult.getStdout().contains(itpdPath)) {
                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, itpdPath);
                } else {
                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                }

            } else {
                correctITPDConfRunAsDaemon(service, appDataDir, false);
                itpdCmdString = itpdPath + " --conf " + appDataDir
                        + "/app_data/i2pd/i2pd.conf --datadir " + appDataDir
                        + "/i2pd_data --pidfile " + appDataDir + "/i2pd.pid";
                new PrefManager(service).setBoolPref("ITPDStartedWithRoot", false);

                shellResult = Shell.SH.run(itpdCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getItpdState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getItpdState() != STOPPING && modulesStatus.getItpdState() != STOPPED) {
                    if (appVersion.startsWith("b")) {
                        handler.post(() -> Toast.makeText(service, "Purple I2P Module Fault: "
                                + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr()
                                + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());
                    }

                    sendAskForceCloseBroadcast(service, "I2P");
                }

                Log.e(LOG_TAG, "Error ITPD: " + shellResult.exitCode + " ERR="
                        + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                modulesStatus.setItpdState(STOPPED);

                ModulesAux.makeModulesStateExtraLoop(service);

                sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
            }
        };
    }

    private List<String> correctTorConfRunAsDaemon(Context context, boolean runAsDaemon) {

        List<String> lines = FileOperations.readTextFileSynchronous(context, torConfPath);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("RunAsDaemon")) {
                if (runAsDaemon && lines.get(i).contains("0")) {
                    lines.set(i, "RunAsDaemon 1");
                    FileOperations.writeTextFileSynchronous(context, torConfPath, lines);
                } else if (!runAsDaemon && lines.get(i).contains("1")) {
                    lines.set(i, "RunAsDaemon 0");
                    FileOperations.writeTextFileSynchronous(context, torConfPath, lines);
                }
                return lines;
            }
        }

        return lines;
    }

    //Disable Tor Kernel-Informed Socket Transport because ioctl() with request SIOCOUTQNSD is denied by android SELINUX policy
    private void useTorSchedulerVanilla(List<String> lines) {
        int indexOfClientOnly = -1;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Schedulers")) {
               return;
            } else if (lines.get(i).contains("ClientOnly")) {
                indexOfClientOnly = i;
            }
        }

        if (indexOfClientOnly > 0) {
            lines.add(indexOfClientOnly, "Schedulers Vanilla");
            FileOperations.writeTextFileSynchronous(service, torConfPath, lines);
        }
    }

    private void correctObfsModulePath(List<String> lines) {
        String savedObfsBinaryPath = new PrefManager(service).getStrPref("ObfsBinaryPath").trim();
        String currentObfsBinaryPath = obfsPath;

        if (!savedObfsBinaryPath.equals(currentObfsBinaryPath)) {

            new PrefManager(service).setStrPref("ObfsBinaryPath", currentObfsBinaryPath);

            boolean useDefaultBridges = new PrefManager(service).getBoolPref("useDefaultBridges");
            boolean useOwnBridges = new PrefManager(service).getBoolPref("useOwnBridges");

            if (useDefaultBridges || useOwnBridges) {

                String line;
                for (int i = 0; i < lines.size(); i++) {
                    line = lines.get(i);
                    if (line.contains("ClientTransportPlugin ") && line.contains("/libobfs4proxy.so")) {
                        line = line.replaceAll("/.+?/libobfs4proxy.so", service.getApplicationInfo().nativeLibraryDir + "/libobfs4proxy.so");
                        lines.set(i, line);
                    } else if (line.contains("ClientTransportPlugin ") && line.contains("/libsnowflake.so")) {
                        line = line.replaceAll("/.+?/libsnowflake.so", service.getApplicationInfo().nativeLibraryDir + "/libsnowflake.so");
                        lines.set(i, line);
                    }
                }

                FileOperations.writeTextFileSynchronous(service, torConfPath, lines);

                Log.i(LOG_TAG, "ModulesService Tor Obfs module path is corrected");
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
        RootCommands comResult = new RootCommands(new ArrayList<>(Arrays.asList(moduleKeyWord, binaryPath)));
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult", comResult);
        intent.putExtra("Mark", moduleMark);
        LocalBroadcastManager.getInstance(service).sendBroadcast(intent);
    }

    private void sendAskForceCloseBroadcast(Context context, String module) {
        Intent intent = new Intent(ASK_FORCE_CLOSE);
        intent.putExtra("Mark", TopFragmentMark);
        intent.putExtra(MODULE_NAME, module);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
