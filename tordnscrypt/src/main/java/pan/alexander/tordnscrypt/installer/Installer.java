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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ModulesVersions;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MAIN_ACTIVITY_RECREATE;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.INSTALLER_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

import javax.inject.Inject;
import javax.inject.Named;

public class Installer implements TopFragment.OnActivityChangeListener {

    @Inject
    public Lazy<PathVars> pathVars;
    @Inject @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public CachedExecutor cachedExecutor;
    @Inject
    public Lazy<ModulesVersions> modulesVersions;

    private Activity activity;
    private MainActivity mainActivity;
    private InstallerReceiver br;
    private static volatile CountDownLatch countDownLatch;
    private final String appDataDir;

    protected static boolean interruptInstallation = false;

    private InstallerUIChanger installerUIChanger;

    public Installer(Activity activity) {
        App.getInstance().getDaggerComponent().inject(this);

        this.activity = activity;
        appDataDir = pathVars.get().getAppDataDir();

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

    protected void extractDNSCrypt() throws Exception {
        if (mainActivity != null) {
            mainActivity.runOnUiThread(installerUIChanger.dnsCryptProgressBarIndeterminate(true));
        }

        Command command = new DNSCryptExtractCommand(activity, appDataDir);
        command.execute();

        command = new BusybExtractCommand(activity, appDataDir);
        command.execute();

        if (mainActivity != null) {
            mainActivity.runOnUiThread(installerUIChanger.dnsCryptProgressBarIndeterminate(false));
            mainActivity.runOnUiThread(installerUIChanger.setDnsCryptInstalledStatus());
        }

        Log.i(LOG_TAG, "Installer: extractDNSCrypt OK");
    }

    protected void extractTor() throws Exception {
        if (mainActivity != null) {
            mainActivity.runOnUiThread(installerUIChanger.torProgressBarIndeterminate(true));
        }

        Command command = new TorExtractCommand(activity, appDataDir);
        command.execute();

        if (mainActivity != null) {
            mainActivity.runOnUiThread(installerUIChanger.torProgressBarIndeterminate(false));
            mainActivity.runOnUiThread(installerUIChanger.setTorInstalledStatus());
        }

        Log.i(LOG_TAG, "Installer: extractTor OK");
    }

    protected void extractITPD() throws Exception {
        if (mainActivity != null) {
            mainActivity.runOnUiThread(installerUIChanger.itpdProgressBarIndeterminate(true));
        }

        Command command = new ITPDExtractCommand(activity, appDataDir);
        command.execute();

        if (mainActivity != null) {
            mainActivity.runOnUiThread(installerUIChanger.itpdProgressBarIndeterminate(false));
            mainActivity.runOnUiThread(installerUIChanger.setItpdInstalledStatus());
        }

        Log.i(LOG_TAG, "Installer: extractITPD OK");
    }

    protected void savePreferencesModulesInstalled(boolean installed) {
        if (activity == null) {
            return;
        }

        PreferenceRepository preferences = preferenceRepository.get();

        if (installed) {
            preferences.setBoolPreference("DNSCrypt Installed", true);
            preferences.setBoolPreference("Tor Installed", true);
            preferences.setBoolPreference("I2PD Installed", true);
        } else {
            preferences.setBoolPreference("DNSCrypt Installed", false);
            preferences.setBoolPreference("Tor Installed", false);
            preferences.setBoolPreference("I2PD Installed", false);
        }

    }

    protected boolean waitUntilAllModulesStopped() {
        countDownLatch = new CountDownLatch(1);
        Log.i(LOG_TAG, "Installer: waitUntilAllModulesStopped");

        boolean result = true;
        try {
            //noinspection ResultOfMethodCallIgnored
            countDownLatch.await(10, TimeUnit.SECONDS);
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
            if (!FileManager.deleteDirSynchronous(activity, app_bin.getAbsolutePath())) {
                throw new IllegalStateException(app_bin.getAbsolutePath() + " delete failed");
            }
        } else if (app_bin.isFile()) {
            if (FileManager.deleteFileSynchronous(activity, app_bin.getParent(), app_bin.getName())) {
                throw new IllegalStateException(app_bin.getAbsolutePath() + " delete failed");
            }
        }

        if (app_data.isDirectory()) {
            if (!FileManager.deleteDirSynchronous(activity, app_data.getAbsolutePath())) {
                throw new IllegalStateException(app_data.getAbsolutePath() + " delete failed");
            }
        } else if (app_data.isFile()) {
            if (FileManager.deleteFileSynchronous(activity, app_data.getParent(), app_data.getName())) {
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
        fixAppDirLinesList(dnsTomlPath, FileManager.readTextFileSynchronous(activity, dnsTomlPath));
        fixAppDirLinesList(torConfPath, FileManager.readTextFileSynchronous(activity, torConfPath));
        fixAppDirLinesList(itpdConfPath, FileManager.readTextFileSynchronous(activity, itpdConfPath));

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
                    && !PathVars.isModulesInstalled(preferenceRepository.get())) {
                lines = prepareDNSCryptForGP(lines);
            }

            FileManager.writeTextFileSynchronous(activity, path, lines);
        } else {
            throw new IllegalStateException("correctAppDir readTextFile return null " + path);
        }
    }

    @SuppressLint("SdCardPath")
    private List<String> prepareDNSCryptForGP(List<String> lines) {

        defaultPreferences.get().edit().putBoolean("require_nofilter", true).apply();

        ArrayList<String> prepared = new ArrayList<>();

        for (String line : lines) {

            if (line.contains("blacklist_file")) {
                line = "";
            } else if (line.contains("whitelist_file")) {
                line = "";
            } else if (line.contains("blocked_names_file")) {
                line = "";
            } else if (line.contains("blocked_ips_file")) {
                line = "";
            } else if (line.matches("(^| )\\{ ?server_name([ =]).+")) {
                line = "";
            } else if (line.matches("(^| )server_names([ =]).+")) {
                line = "server_names = ['uncensoreddns-dk-ipv4', " +
                        "'njalla-doh', " +
                        "'faelix-ch-ipv4', " +
                        "'dns.digitale-gesellschaft.ch', " +
                        "'dnscrypt.ca-1', " +
                        "'sth-doh-se', " +
                        "'libredns', " +
                        "'dnscrypt.eu-nl', " +
                        "'publicarray-au-doh', " +
                        "'scaleway-fr']";
            } else if (line.contains("require_nofilter")) {
                line = "require_nofilter = true";
            }

            if (!line.isEmpty()) {
                prepared.add(line);
            }
        }

        return prepared;
    }

    protected void stopAllRunningModulesWithRootCommand() {
        Log.i(LOG_TAG, "Installer: stopAllRunningModulesWithRootCommand");

        ModulesAux.saveDNSCryptStateRunning(false);
        ModulesAux.saveTorStateRunning(false);
        ModulesAux.saveITPDStateRunning(false);

        String busyboxNative = "";
        if (preferenceRepository.get().getBoolPreference("bbOK")
                && pathVars.get().getBusyboxPath().equals("busybox ")) {
            busyboxNative = "busybox ";
        }

        List<String> commandsInstall = new ArrayList<>(Arrays.asList(
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
                busyboxNative + "pkill -SIGTERM /libdnscrypt-proxy.so 2> /dev/null || true",
                busyboxNative + "pkill -SIGTERM /libtor.so 2> /dev/null || true",
                busyboxNative + "pkill -SIGTERM /libi2pd.so 2> /dev/null || true",
                busyboxNative + "sleep 7 2> /dev/null",
                busyboxNative + "pgrep -l /libdnscrypt-proxy.so 2> /dev/null",
                busyboxNative + "pgrep -l /libtor.so 2> /dev/null",
                busyboxNative + "pgrep -l /libi2pd.so 2> /dev/null",
                busyboxNative + "echo 'checkModulesRunning' 2> /dev/null"
        ));

        RootCommands.execute(activity, commandsInstall, INSTALLER_MARK);
    }

    protected void stopAllRunningModulesWithNoRootCommand() {

        cachedExecutor.submit(() -> {
            ModulesAux.stopModulesIfRunning(activity);

            int counter = 15;

            while (counter > 0) {
                if (activity != null
                        && !ModulesAux.isDnsCryptSavedStateRunning()
                        && !ModulesAux.isTorSavedStateRunning()
                        && !ModulesAux.isITPDSavedStateRunning()) {
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
        RootCommands comResult = new RootCommands(new ArrayList<>(Collections.singletonList(result)));
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult", comResult);
        intent.putExtra("Mark", INSTALLER_MARK);
        LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
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
            LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
        } else {
            modulesVersions.get().refreshVersions(activity);
        }

        preferenceRepository.get().setBoolPreference(MAIN_ACTIVITY_RECREATE, true);
    }

    protected void registerReceiver(Activity activity) {
        br = new InstallerReceiver();
        IntentFilter intentFilter = new IntentFilter(COMMAND_RESULT);
        LocalBroadcastManager.getInstance(activity).registerReceiver(br, intentFilter);

        Log.i(LOG_TAG, "Installer: registerReceiver OK");
    }

    protected void unRegisterReceiver(Activity activity) {
        if (br != null) {
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(br);

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
}
