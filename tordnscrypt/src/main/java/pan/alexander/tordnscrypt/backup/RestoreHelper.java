package pan.alexander.tordnscrypt.backup;

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
import android.content.SharedPreferences;

import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.zipUtil.ZipFileManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.installer.Installer;

import static pan.alexander.tordnscrypt.backup.BackupFragment.CODE_READ;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

class RestoreHelper extends Installer {
    private final List<String> requiredFiles = Arrays.asList(
            "app_bin/busybox", "app_bin/iptables", "app_bin/ip6tables",

            "app_data/dnscrypt-proxy/blacklist.txt", "app_data/dnscrypt-proxy/cloaking-rules.txt",
            "app_data/dnscrypt-proxy/dnscrypt-proxy.toml", "app_data/dnscrypt-proxy/forwarding-rules.txt",
            "app_data/dnscrypt-proxy/ip-blacklist.txt", "app_data/dnscrypt-proxy/whitelist.txt",

            "app_data/tor/bridges_custom.lst", "app_data/tor/bridges_default.lst",
            "app_data/tor/geoip", "app_data/tor/geoip6", "app_data/tor/tor.conf",

            "app_data/i2pd/i2pd.conf", "app_data/i2pd/tunnels.conf",

            "defaultSharedPref", "sharedPreferences"
    );

    private Activity activity;
    private final String appDataDir;
    private final String cacheDir;
    private String pathBackup;

    RestoreHelper(Activity activity, String appDataDir, String cacheDir, String pathBackup) {
        super(activity);

        this.activity = activity;
        this.appDataDir = appDataDir;
        this.cacheDir = cacheDir;
        this.pathBackup = pathBackup;
    }

    void restoreAll(InputStream inputStream) {

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {

                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    copyData(inputStream);
                }

                if (!isBackupExist()) {
                    throw new IllegalStateException("No file or file is corrupted " + pathBackup + "/InvizibleBackup.zip");
                }

                registerReceiver(activity);

                if (ModulesStatus.getInstance().isUseModulesWithRoot()) {
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

                unRegisterReceiver(activity);

                removeInstallationDirsIfExists();
                createLogsDir();

                extractBackup();
                chmodExtractedDirs();

                correctAppDir();

                SharedPreferences defaultSharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
                String code = saveSomeOldInfo();
                restoreSharedPreferencesFromFile(defaultSharedPref, appDataDir + "/defaultSharedPref");

                SharedPreferences sharedPreferences = activity.getSharedPreferences(PrefManager.getPrefName(), Context.MODE_PRIVATE);
                restoreSharedPreferencesFromFile(sharedPreferences, appDataDir + "/sharedPreferences");

                FileOperations.deleteFile(activity, appDataDir, "defaultSharedPref", "defaultSharedPref");
                FileOperations.deleteFile(activity, appDataDir, "sharedPreferences", "sharedPreferences");

                refreshInstallationParameters();

                restoreOldInfo(code);

                refreshModulesStatus(activity);

            } catch (Exception e) {
                Log.e(LOG_TAG, "Restore fault " + e.getMessage() + " " + e.getCause());

                if (activity instanceof BackupActivity) {
                    try {
                        BackupActivity backupActivity = (BackupActivity) activity;
                        FragmentManager manager = backupActivity.getSupportFragmentManager();
                        BackupFragment fragment = (BackupFragment) manager.findFragmentById(R.id.backupFragment);
                        if (fragment != null) {
                            fragment.closePleaseWaitDialog();
                            fragment.showToast(activity.getString(R.string.wrong));
                        }
                    } catch (Exception ex) {
                        Log.e(LOG_TAG, "RestoreHelper close progress fault " + ex.getMessage() + " " + ex.getCause());
                    }

                }
            }
        });
    }

    private boolean isBackupExist() {

        String path = pathBackup + "/InvizibleBackup.zip";

        File file = new File(path);
        if (!file.isFile()) {
            return false;
        }

        List<String> zipEntries = new ArrayList<>();

        try (FileInputStream fileInputStream = new FileInputStream(pathBackup + "/InvizibleBackup.zip");
             ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {

            ZipEntry zipEntry = zipInputStream.getNextEntry();

            while (zipEntry != null) {
                zipEntries.add(zipEntry.getName());
                zipEntry = zipInputStream.getNextEntry();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "RestoreHelper isBackupExist exception " + e.getMessage() + " " + e.getCause());
        }

        if (zipEntries.containsAll(requiredFiles)) {
            return true;
        } else {
            ArrayList<String> copy = new ArrayList<>(requiredFiles);
            copy.removeAll(zipEntries);
            Log.e(LOG_TAG, "RestoreHelper isBackupExist backup file corrupted " + copy.toString());
        }

        return zipEntries.containsAll(requiredFiles);
    }

    void openFileWithSAF() {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");

        PackageManager packageManager = activity.getPackageManager();
        if (packageManager != null && intent.resolveActivity(packageManager) != null) {
            activity.startActivityForResult(intent, CODE_READ);
        }

    }

    private void copyData(InputStream inputStream) throws Exception {

        if (inputStream == null) {
            return;
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream(cacheDir + "/InvizibleBackup.zip")) {
            byte[] buffer = new byte[8 * 1024];

            for (int len; (len = inputStream.read(buffer)) > 0; ) {
                fileOutputStream.write(buffer, 0, len);
            }
            fileOutputStream.flush();
        } finally {
            inputStream.close();
        }
    }

    private String saveSomeOldInfo() {
        String code = "";
        if (activity.getString(R.string.appVersion).endsWith("o")) {
            code = new PrefManager(activity).getStrPref("registrationCode");
        }
        return code;
    }

    private void restoreOldInfo(String code) {
        if (!code.isEmpty()) {
            new PrefManager(activity).setStrPref("registrationCode", code);
        }
    }

    private void refreshInstallationParameters() {
        new PrefManager(activity).setBoolPref("DNSCrypt Running", false);
        new PrefManager(activity).setBoolPref("Tor Running", false);
        new PrefManager(activity).setBoolPref("I2PD Running", false);
    }

    private void extractBackup() throws Exception {
        ZipFileManager zipFileManager = new ZipFileManager(pathBackup + "/InvizibleBackup.zip");
        zipFileManager.extractZip(appDataDir);

        Log.i(LOG_TAG, "RestoreHelper: extractBackup OK");
    }

    private void restoreSharedPreferencesFromFile(SharedPreferences sharedPref, String src) throws Exception {
        SharedPreferences.Editor editor = sharedPref.edit();
        loadSharedPreferencesFromFile(editor, src);
        editor.apply();

        Log.i(LOG_TAG, "RestoreHelper: sharedPreferences restore " + src + " OK");
    }

    @SuppressWarnings({"unchecked"})
    private void loadSharedPreferencesFromFile(SharedPreferences.Editor prefEdit, String src) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(src))) {
            prefEdit.clear();

            Map<String, ?> entries = (Map<String, ?>) input.readObject();

            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean)
                    prefEdit.putBoolean(key, (Boolean) v);
                else if (v instanceof Float)
                    prefEdit.putFloat(key, (Float) v);
                else if (v instanceof Integer)
                    prefEdit.putInt(key, (Integer) v);
                else if (v instanceof Long)
                    prefEdit.putLong(key, (Long) v);
                else if (v instanceof String)
                    prefEdit.putString(key, ((String) v));
                else if (v instanceof Set) {
                    prefEdit.putStringSet(key, (Set<String>) v);
                }
            }
        }
    }

    void setPathBackup(String pathBackup) {
        this.pathBackup = pathBackup;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }
}
