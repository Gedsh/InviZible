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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.modules.ModulesService.DNSCRYPT_KEYWORD;
import static pan.alexander.tordnscrypt.modules.ModulesService.ITPD_KEYWORD;
import static pan.alexander.tordnscrypt.modules.ModulesService.TOR_KEYWORD;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.DNSCryptRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.I2PDRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.TopFragmentMark;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.TorRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

public class ModulesStarterHelper {

    public final static String ASK_FORCE_CLOSE = "pan.alexander.tordnscrypt.AskForceClose";
    public final static String MODULE_NAME = "pan.alexander.tordnscrypt.ModuleName";

    private final Context context;
    private final Handler handler;
    private final String appDataDir;
    private final String busyboxPath;
    private final String dnscryptPath;
    private final String torPath;
    private final String torConfPath;
    private final String obfsPath;
    private final String itpdPath;

    private final ModulesStatus modulesStatus;
    private final Lazy<PreferenceRepository> preferenceRepository;

    ModulesStarterHelper(Context context, Handler handler, PathVars pathVars) {
        this.context = context;
        this.handler = handler;
        appDataDir = pathVars.getAppDataDir();
        busyboxPath = pathVars.getBusyboxPath();
        dnscryptPath = pathVars.getDNSCryptPath();
        torPath = pathVars.getTorPath();
        torConfPath = pathVars.getTorConfPath();
        obfsPath = pathVars.getObfsPath();
        itpdPath = pathVars.getITPDPath();
        this.modulesStatus = ModulesStatus.getInstance();
        preferenceRepository = App.instance.daggerComponent.getPreferenceRepository();
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

                preferenceRepository.get().setBoolPreference("DNSCryptStartedWithRoot", true);

                if (shellResult.getStdout().contains(dnscryptPath)) {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, dnscryptPath);
                } else {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                }

            } else {
                dnsCmdString = dnscryptPath + " -config " + appDataDir
                        + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml -pidfile " + appDataDir + "/dnscrypt-proxy.pid";
                preferenceRepository.get().setBoolPreference("DNSCryptStartedWithRoot", false);

                shellResult = new ProcessStarter().startProcess(dnsCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getDnsCryptState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getDnsCryptState() != STOPPING && modulesStatus.getDnsCryptState() != STOPPED) {

                    if (appVersion.startsWith("b") && handler != null) {
                        handler.post(() -> Toast.makeText(context, "DNSCrypt Module Fault: "
                                + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr()
                                + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());
                    }

                    sendAskForceCloseBroadcast(context, "DNSCrypt");
                }

                Log.e(LOG_TAG, "Error DNSCrypt: "
                        + shellResult.exitCode + " ERR=" + shellResult.getStderr()
                        + " OUT=" + shellResult.getStdout());

                modulesStatus.setDnsCryptState(STOPPED);

                ModulesAux.makeModulesStateExtraLoop(context);

                sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
            }

            Thread.currentThread().interrupt();
        };
    }

    Runnable getTorStarterRunnable() {
        return () -> {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            String torCmdString;
            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {

                List<String> lines = correctTorConfRunAsDaemon(context, true);

                correctObfsModulePath(lines);

                torCmdString = torPath + " -f "
                        + appDataDir + "/app_data/tor/tor.conf -pidfile " + appDataDir + "/tor.pid";
                String waitString = busyboxPath + "sleep 3";
                String checkIfModuleRunning = busyboxPath + "pgrep -l /libtor.so";

                shellResult = Shell.SU.run(torCmdString, waitString, checkIfModuleRunning);

                preferenceRepository.get().setBoolPreference("TorStartedWithRoot", true);

                if (shellResult.getStdout().contains(torPath)) {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, torPath);
                } else {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                }

            } else {

                List<String> lines = correctTorConfRunAsDaemon(context, false);

                useTorSchedulerVanilla(lines);

                correctObfsModulePath(lines);

                torCmdString = torPath + " -f "
                        + appDataDir + "/app_data/tor/tor.conf -pidfile " + appDataDir + "/tor.pid";
                preferenceRepository.get().setBoolPreference("TorStartedWithRoot", false);

                shellResult = new ProcessStarter().startProcess(torCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getTorState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getTorState() != STOPPING && modulesStatus.getTorState() != STOPPED) {
                    if (appVersion.startsWith("b") && handler != null) {
                        handler.post(() -> Toast.makeText(context, "Tor Module Fault: " + shellResult.exitCode
                                + "\n\n ERR = " + shellResult.getStderr()
                                + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());
                    }

                    sendAskForceCloseBroadcast(context, "Tor");

                    //Try to update Selinux context and UID once again
                    if (shellResult.exitCode == 1 && modulesStatus.isRootAvailable()) {
                        modulesStatus.setContextUIDUpdateRequested(true);
                        ModulesAux.makeModulesStateExtraLoop(context);
                    }
                }

                Log.e(LOG_TAG, "Error Tor: " + shellResult.exitCode
                        + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                if (!isActivityActive() && modulesStatus.getTorState() == RUNNING) {
                    System.exit(0);
                }

                modulesStatus.setTorState(STOPPED);

                ModulesAux.makeModulesStateExtraLoop(context);

                sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
            }

            Thread.currentThread().interrupt();
        };
    }

    private boolean isActivityActive() {

        App app = App.Companion.getInstance();

        WeakReference<Activity> activityWeakReference = app.getCurrentActivity();
        if (activityWeakReference == null) {
            return false;
        }

        Activity activity = activityWeakReference.get();
        if (activity == null) {
            return false;
        }

        return !activity.isFinishing();
    }

    Runnable getITPDStarterRunnable() {
        return () -> {
            //new experiment
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

            String itpdCmdString;

            final CommandResult shellResult;
            if (modulesStatus.isUseModulesWithRoot()) {
                correctITPDConfRunAsDaemon(context, appDataDir, true);

                Shell.SU.run(busyboxPath + "mkdir -p " + appDataDir + "/i2pd_data",
                        "cd " + appDataDir + "/app_data/i2pd",
                        busyboxPath + "cp -R certificates " + appDataDir + "/i2pd_data");

                itpdCmdString = itpdPath + " --conf " + appDataDir
                        + "/app_data/i2pd/i2pd.conf --datadir " + appDataDir
                        + "/i2pd_data --pidfile " + appDataDir + "/i2pd.pid &";
                String waitString = busyboxPath + "sleep 3";
                String checkIfModuleRunning = busyboxPath + "pgrep -l /libi2pd.so";

                shellResult = Shell.SU.run(itpdCmdString, waitString, checkIfModuleRunning);

                preferenceRepository.get().setBoolPreference("ITPDStartedWithRoot", true);

                if (shellResult.getStdout().contains(itpdPath)) {
                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, itpdPath);
                } else {
                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                }

            } else {
                correctITPDConfRunAsDaemon(context, appDataDir, false);
                itpdCmdString = itpdPath + " --conf " + appDataDir
                        + "/app_data/i2pd/i2pd.conf --datadir " + appDataDir
                        + "/i2pd_data --pidfile " + appDataDir + "/i2pd.pid";
                preferenceRepository.get().setBoolPreference("ITPDStartedWithRoot", false);

                shellResult = new ProcessStarter().startProcess(itpdCmdString);
            }

            if (!shellResult.isSuccessful()) {

                if (modulesStatus.getItpdState() == RESTARTING) {
                    return;
                }

                if (modulesStatus.getItpdState() != STOPPING && modulesStatus.getItpdState() != STOPPED) {
                    if (appVersion.startsWith("b") && handler != null) {
                        handler.post(() -> Toast.makeText(context, "Purple I2P Module Fault: "
                                + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr()
                                + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show());
                    }

                    sendAskForceCloseBroadcast(context, "I2P");
                }

                Log.e(LOG_TAG, "Error ITPD: " + shellResult.exitCode + " ERR="
                        + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                modulesStatus.setItpdState(STOPPED);

                ModulesAux.makeModulesStateExtraLoop(context);

                sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
            }

            Thread.currentThread().interrupt();
        };
    }

    private List<String> correctTorConfRunAsDaemon(Context context, boolean runAsDaemon) {

        List<String> lines = FileManager.readTextFileSynchronous(context, torConfPath);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("RunAsDaemon")) {
                if (runAsDaemon && lines.get(i).contains("0")) {
                    lines.set(i, "RunAsDaemon 1");
                    FileManager.writeTextFileSynchronous(context, torConfPath, lines);
                } else if (!runAsDaemon && lines.get(i).contains("1")) {
                    lines.set(i, "RunAsDaemon 0");
                    FileManager.writeTextFileSynchronous(context, torConfPath, lines);
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
            FileManager.writeTextFileSynchronous(context, torConfPath, lines);
        }
    }

    private void correctObfsModulePath(List<String> lines) {
        PreferenceRepository preferences = preferenceRepository.get();
        String savedObfsBinaryPath = preferences.getStringPreference("ObfsBinaryPath").trim();
        String currentObfsBinaryPath = obfsPath;

        if (!savedObfsBinaryPath.equals(currentObfsBinaryPath)) {

            preferences.setStringPreference("ObfsBinaryPath", currentObfsBinaryPath);

            boolean useDefaultBridges = preferences.getBoolPreference("useDefaultBridges");
            boolean useOwnBridges = preferences.getBoolPreference("useOwnBridges");

            if (useDefaultBridges || useOwnBridges) {

                String line;
                for (int i = 0; i < lines.size(); i++) {
                    line = lines.get(i);
                    if (line.contains("ClientTransportPlugin ") && line.contains("/libobfs4proxy.so")) {
                        line = line.replaceAll("/.+?/libobfs4proxy.so", context.getApplicationInfo().nativeLibraryDir + "/libobfs4proxy.so");
                        lines.set(i, line);
                    } else if (line.contains("ClientTransportPlugin ") && line.contains("/libsnowflake.so")) {
                        line = line.replaceAll("/.+?/libsnowflake.so", context.getApplicationInfo().nativeLibraryDir + "/libsnowflake.so");
                        lines.set(i, line);
                    }
                }

                FileManager.writeTextFileSynchronous(context, torConfPath, lines);

                Log.i(LOG_TAG, "ModulesService Tor Obfs module path is corrected");
            }
        }
    }

    private void correctITPDConfRunAsDaemon(Context context, String appDataDir, boolean runAsDaemon) {
        String path = appDataDir + "/app_data/i2pd/i2pd.conf";
        List<String> lines = FileManager.readTextFileSynchronous(context, path);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("daemon")) {
                if (runAsDaemon && lines.get(i).contains("false")) {
                    lines.set(i, "daemon = true");
                    FileManager.writeTextFileSynchronous(context, path, lines);
                } else if (!runAsDaemon && lines.get(i).contains("true")) {
                    lines.set(i, "daemon = false");
                    FileManager.writeTextFileSynchronous(context, path, lines);
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
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void sendAskForceCloseBroadcast(Context context, String module) {
        Intent intent = new Intent(ASK_FORCE_CLOSE);
        intent.putExtra("Mark", TopFragmentMark);
        intent.putExtra(MODULE_NAME, module);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }
}
