package pan.alexander.tordnscrypt.installer;

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
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ModulesVersions;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.RootExecService.InstallerMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class Installer implements TopFragment.OnActivityChangeListener {
    private Activity activity;
    private MainActivity mainActivity;
    private InstallerReceiver br;
    private static CountDownLatch countDownLatch;
    private String appDataDir;
    private PathVars pathVars;
    protected static boolean interruptInstallation = false;

    private InstallerUIChanger installerUIChanger;

    public Installer(Activity activity) {
        this.activity = activity;

        pathVars = PathVars.getInstance(activity);
        appDataDir = pathVars.getAppDataDir();

        if (activity instanceof MainActivity) {
            mainActivity = (MainActivity) activity;
            installerUIChanger = new InstallerUIChanger(mainActivity);
        }


    }

    public void installModules() {

        try {
            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            setOnMainActivityChangeListener();

            mainActivity.runOnUiThread(installerUIChanger.lockDrawerMenu(true));
            mainActivity.runOnUiThread(installerUIChanger.setModulesStatusTextInstalling());
            mainActivity.runOnUiThread(installerUIChanger.setModulesStartButtonsDisabled());
            mainActivity.runOnUiThread(installerUIChanger.startModulesProgressBarIndeterminate());


            registerReceiver(activity);

            if (ModulesStatus.getInstance().isRootAvailable()
                    && ModulesStatus.getInstance().isUseModulesWithRoot()) {
                stopAllRunningModulesWithRootCommand();
            } else {
                stopAllRunningModulesWithNoRootCommand();
            }

            if (!waitUntilAllModulesStopped()) {
                throw new IllegalStateException("Unexpected interruption");
            }

            if (interruptInstallation) {
                throw new IllegalStateException("Installation interrupted");
            }

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            unRegisterReceiver(activity);

            removeInstallationDirsIfExists();
            createLogsDir();

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            mainActivity.runOnUiThread(installerUIChanger.stopModulesProgressBarIndeterminate());

            extractDNSCrypt();

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            extractTor();

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            extractITPD();

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            chmodExtractedDirs();

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            mainActivity.runOnUiThread(installerUIChanger.startModulesProgressBarIndeterminate());

            correctAppDir();

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            mainActivity.runOnUiThread(installerUIChanger.stopModulesProgressBarIndeterminate());
            mainActivity.runOnUiThread(installerUIChanger.setModulesStartButtonsEnabled());
            mainActivity.runOnUiThread(installerUIChanger.lockDrawerMenu(false));


            savePreferencesModulesInstalled(true);

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            refreshModulesStatus(activity);

            TimeUnit.SECONDS.sleep(1);

            if (mainActivity == null || mainActivity.isFinishing()) {
                throw new IllegalStateException("Installer: MainActivity is null, interrupt installation");
            }

            mainActivity.runOnUiThread(installerUIChanger.showDialogAfterInstallation());


        } catch (Exception e) {
            Log.e(LOG_TAG, "Installation fault " + e.getMessage() + " " + e.getCause());

            savePreferencesModulesInstalled(false);

            if (mainActivity != null && !mainActivity.isFinishing()) {
                try {
                    mainActivity.runOnUiThread(installerUIChanger.setModulesStatusTextError());
                    mainActivity.runOnUiThread(installerUIChanger.setModulesStartButtonsDisabled());
                    mainActivity.runOnUiThread(installerUIChanger.stopModulesProgressBarIndeterminate());
                    mainActivity.runOnUiThread(installerUIChanger.lockDrawerMenu(false));
                } catch (Exception ignored){}
            }
        }


    }

    private void extractDNSCrypt() throws Exception {
        mainActivity.runOnUiThread(installerUIChanger.dnsCryptProgressBarIndeterminate(true));

        Command command = new DNSCryptExtractCommand(activity, appDataDir);
        command.execute();

        mainActivity.runOnUiThread(installerUIChanger.dnsCryptProgressBarIndeterminate(false));
        mainActivity.runOnUiThread(installerUIChanger.setDnsCryptInstalledStatus());

        Log.i(LOG_TAG, "Installer: extractDNSCrypt OK");
    }

    private void extractTor() throws Exception {
        mainActivity.runOnUiThread(installerUIChanger.torProgressBarIndeterminate(true));

        Command command = new TorExtractCommand(activity, appDataDir);
        command.execute();

        mainActivity.runOnUiThread(installerUIChanger.torProgressBarIndeterminate(false));
        mainActivity.runOnUiThread(installerUIChanger.setTorInstalledStatus());

        Log.i(LOG_TAG, "Installer: extractTor OK");
    }

    private void extractITPD() throws Exception {

        mainActivity.runOnUiThread(installerUIChanger.itpdProgressBarIndeterminate(true));

        Command command = new ITPDExtractCommand(activity, appDataDir);
        command.execute();

        mainActivity.runOnUiThread(installerUIChanger.itpdProgressBarIndeterminate(false));
        mainActivity.runOnUiThread(installerUIChanger.setItpdInstalledStatus());

        Log.i(LOG_TAG, "Installer: extractITPD OK");
    }

    private void savePreferencesModulesInstalled(boolean installed) {
        if (activity == null) {
            return;
        }

        if (installed) {
            new PrefManager(activity).setBoolPref("DNSCrypt Installed", true);
            new PrefManager(activity).setBoolPref("Tor Installed", true);
            new PrefManager(activity).setBoolPref("I2PD Installed", true);
        } else {
            new PrefManager(activity).setBoolPref("DNSCrypt Installed", false);
            new PrefManager(activity).setBoolPref("Tor Installed", false);
            new PrefManager(activity).setBoolPref("I2PD Installed", false);
        }

    }

    protected boolean waitUntilAllModulesStopped() {
        countDownLatch = new CountDownLatch(1);
        Log.i(LOG_TAG, "Installer: waitUntilAllModulesStopped");

        boolean result = true;
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Installer CountDownLatch interrupted");
            result = false;
        }

        return result;
    }

    static void continueInstallation(boolean interruptInstallation) {
        if (countDownLatch != null) {
            countDownLatch.countDown();
            countDownLatch = null;
            Installer.interruptInstallation = interruptInstallation;
        }
    }

    protected void removeInstallationDirsIfExists() {
        File app_bin = new File(appDataDir + "/app_bin");
        File app_data = new File(appDataDir + "/app_data");


        if (app_bin.isDirectory()) {
            if (!FileOperations.deleteDirSynchronous(activity, app_bin.getAbsolutePath())) {
                throw new IllegalStateException(app_bin.getAbsolutePath() + " delete failed");
            }
        } else if (app_bin.isFile()) {
            if (FileOperations.deleteFileSynchronous(activity, app_bin.getParent(), app_bin.getName())) {
                throw new IllegalStateException(app_bin.getAbsolutePath() + " delete failed");
            }
        }

        if (app_data.isDirectory()) {
            if (!FileOperations.deleteDirSynchronous(activity, app_data.getAbsolutePath())) {
                throw new IllegalStateException(app_data.getAbsolutePath() + " delete failed");
            }
        } else if (app_data.isFile()) {
            if (FileOperations.deleteFileSynchronous(activity, app_data.getParent(), app_data.getName())) {
                throw new IllegalStateException(app_data.getAbsolutePath() + " delete failed");
            }
        }

        Log.i(LOG_TAG, "Installer: removeInstallationDirsIfExists OK");
    }

    protected void chmodExtractedDirs() {
        ChmodCommand.dirChmod(appDataDir + "/app_bin", true);
        ChmodCommand.dirChmod(appDataDir + "/app_data", false);

        Log.i(LOG_TAG, "Installer: chmodExtractedDirs OK");
    }

    protected void correctAppDir() {
        String dnsTomlPath = appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml";
        String torConfPath = appDataDir + "/app_data/tor/tor.conf";
        String itpdConfPath = appDataDir + "/app_data/i2pd/i2pd.conf";
        fixAppDirLinesList(dnsTomlPath, FileOperations.readTextFileSynchronous(activity, dnsTomlPath));
        fixAppDirLinesList(torConfPath, FileOperations.readTextFileSynchronous(activity, torConfPath));
        fixAppDirLinesList(itpdConfPath, FileOperations.readTextFileSynchronous(activity, itpdConfPath));

        Log.i(LOG_TAG, "Installer: correctAppDir OK");
    }

    @SuppressLint("SdCardPath")
    private void fixAppDirLinesList(String path, List<String> lines) {
        if (lines != null) {
            String line;
            for (int i = 0; i < lines.size(); i++) {
                line = lines.get(i);
                if (line.contains("/data/user/0/pan.alexander.tordnscrypt")) {
                    line = line.replaceAll("/data/user/0/pan.alexander.tordnscrypt.*?/", appDataDir + "/");
                    lines.set(i, line);
                }
            }

            if (activity != null
                    && activity.getText(R.string.package_name).toString().contains(".gp")
                    && path.contains("dnscrypt-proxy.toml")
                    && !PathVars.isModulesInstalled(activity)) {
                lines = prepareDNSCryptForGP(lines);
            }

            FileOperations.writeTextFileSynchronous(activity, path, lines);
        } else {
            throw new IllegalStateException("correctAppDir readTextFile return null " + path);
        }
    }

    @SuppressLint("SdCardPath")
    private List<String> prepareDNSCryptForGP(List<String> lines) {
        ArrayList<String> prepared = new ArrayList<>();

        for (String line : lines) {
            /*if (line.contains("block_unqualified")) {
                line = "block_unqualified = false";
            } else if (line.contains("block_undelegated")) {
                line = "block_undelegated = false";
            } else */

            if (line.contains("blacklist_file")) {
                line = "";
            } else if (line.contains("whitelist_file")) {
                line = "";
            } else if (line.matches("(^| )\\{ ?server_name([ =]).+")) {
                line = "";
            } else if (line.matches("(^| )server_names([ =]).+")) {
                line = "server_names = ['ams-dnscrypt-nl', " +
                        "'ams-doh-nl', " +
                        "'cs-swe', " +
                        "'dns.digitale-gesellschaft.ch', " +
                        "'doh-fi-snopyta', " +
                        "'doh-ibksturm', " +
                        "'libredns', " +
                        "'opennic-R4SAS', " +
                        "'publicarray-au', " +
                        "'scaleway-fr']";
            }

            if (!line.isEmpty()) {
                prepared.add(line);
            }
        }

        return prepared;
    }

    protected void stopAllRunningModulesWithRootCommand() {
        Log.i(LOG_TAG, "Installer: stopAllRunningModulesWithRootCommand");

        new PrefManager(activity).setBoolPref("DNSCrypt Running", false);
        new PrefManager(activity).setBoolPref("Tor Running", false);
        new PrefManager(activity).setBoolPref("I2PD Running", false);

        String busyboxNative = "";
        if (new PrefManager(activity).getBoolPref("bbOK") && pathVars.getBusyboxPath().equals("busybox ")) {
            busyboxNative = "busybox ";
        }

        String[] commandsInstall = {
                "ip6tables -D OUTPUT -j DROP 2> /dev/null || true",
                "ip6tables -I OUTPUT -j DROP 2> /dev/null",
                "iptables -t nat -F tordnscrypt_nat_output 2> /dev/null",
                "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                "iptables -F tordnscrypt 2> /dev/null",
                "iptables -D OUTPUT -j tordnscrypt 2> /dev/null || true",
                "iptables -t nat -F tordnscrypt_prerouting 2> /dev/null",
                "iptables -F tordnscrypt_forward 2> /dev/null",
                "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                "iptables -D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                busyboxNative + "pkill -SIGTERM /libdnscrypt-proxy.so 2> /dev/null",
                busyboxNative + "pkill -SIGTERM /libtor.so 2> /dev/null",
                busyboxNative + "pkill -SIGTERM /libi2pd.so 2> /dev/null",
                busyboxNative + "sleep 7 2> /dev/null",
                busyboxNative + "pgrep -l /libdnscrypt-proxy.so 2> /dev/null",
                busyboxNative + "pgrep -l /libtor.so 2> /dev/null",
                busyboxNative + "pgrep -l /libi2pd.so 2> /dev/null",
                busyboxNative + "echo 'checkModulesRunning' 2> /dev/null"
        };

        RootCommands rootCommands = new RootCommands(commandsInstall);
        Intent intent = new Intent(activity, RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands", rootCommands);
        intent.putExtra("Mark", InstallerMark);
        RootExecService.performAction(activity, intent);
    }

    protected void stopAllRunningModulesWithNoRootCommand() {

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            ModulesAux.stopModulesIfRunning(activity);

            int counter = 15;

            while (counter > 0) {
                if (activity != null
                        && !isDnsCryptSavedStateRunning()
                        && !isTorSavedStateRunning()
                        && !isITPDSavedStateRunning()) {
                    sendModulesStopResult("checkModulesRunning");
                    break;
                } else {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                        counter--;
                    } catch (InterruptedException ignored) {
                        counter = 0;
                        break;
                    }
                }
            }

            if (counter <= 0) {
                sendModulesStopResult("");
            }

        });


    }

    private void sendModulesStopResult(String result) {
        RootCommands comResult = new RootCommands(new String[]{result});
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult", comResult);
        intent.putExtra("Mark", InstallerMark);
        activity.sendBroadcast(intent);
    }

    protected void createLogsDir() {
        File logDir = new File(appDataDir + "/logs");
        if (!logDir.isDirectory()) {
            if (logDir.mkdir()) {
                ChmodCommand.dirChmod(logDir.getAbsolutePath(), false);
            } else {
                throw new IllegalStateException("Installer Create log dir failed");
            }
        }

        Log.i(LOG_TAG, "Installer: createLogsDir OK");
    }

    protected void refreshModulesStatus(Activity activity) {
        if (ModulesStatus.getInstance().isRootAvailable()
                && ModulesStatus.getInstance().isUseModulesWithRoot()) {
            Intent intent = new Intent(TOP_BROADCAST);
            activity.sendBroadcast(intent);
        } else {
            ModulesVersions.getInstance().refreshVersions(activity);
        }

        new PrefManager(activity).setBoolPref("refresh_main_activity", true);
    }

    protected void registerReceiver(Activity activity) {
        br = new InstallerReceiver();
        IntentFilter intentFilter = new IntentFilter(COMMAND_RESULT);
        activity.registerReceiver(br, intentFilter);

        Log.i(LOG_TAG, "Installer: registerReceiver OK");
    }

    protected void unRegisterReceiver(Activity activity) {
        if (br != null) {
            activity.unregisterReceiver(br);

            Log.i(LOG_TAG, "Installer: unregisterReceiver OK");
        }
    }

    private void setOnMainActivityChangeListener() {
        FragmentManager manager = mainActivity.getSupportFragmentManager();
        TopFragment topFragment = (TopFragment) manager.findFragmentById(R.id.Topfrg);
        if (topFragment != null) {
            topFragment.setOnActivityChangeListener(this);
        }
    }

    @Override
    public void onActivityChange(MainActivity mainActivity) {
        this.activity = mainActivity;
        this.mainActivity = mainActivity;
        installerUIChanger.setMainActivity(mainActivity);
    }

    private boolean isDnsCryptSavedStateRunning() {
        return new PrefManager(activity).getBoolPref("DNSCrypt Running");
    }

    private boolean isTorSavedStateRunning() {
        return new PrefManager(activity).getBoolPref("Tor Running");
    }

    private boolean isITPDSavedStateRunning() {
        return new PrefManager(activity).getBoolPref("I2PD Running");
    }
}
