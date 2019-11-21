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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.app.FragmentManager;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.Set;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.modulesManager.ModulesStatus;
import pan.alexander.tordnscrypt.utils.zipUtil.ZipFileManager;
import pan.alexander.tordnscrypt.utils.fileOperations.FileOperations;
import pan.alexander.tordnscrypt.installer.Installer;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

class RestoreHelper extends Installer {
    private String appDataDir;
    private String pathBackup;
    private Activity activity;

    RestoreHelper(Activity activity, String appDataDir, String pathBackup) {
        super(activity);

        this.activity = activity;
        this.appDataDir = appDataDir;
        this.pathBackup = pathBackup;
    }

    void restoreAll() {
        Runnable restore = new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isBackupExist()) {
                        throw new IllegalStateException("No file to restore " + pathBackup + "/InvizibleBackup.zip");
                    }

                    if (ModulesStatus.getInstance().isUseModulesWithRoot()) {
                        registerReceiver(activity);

                        stopAllRunningModulesWithRootCommand();

                        if (!waitUntilAllModulesStopped()) {
                            throw new IllegalStateException("Unexpected interruption");
                        }

                        if (interruptInstallation) {
                            throw new IllegalStateException("Installation interrupted");
                        }

                        unRegisterReceiver(activity);

                    } else {
                        stopAllRunningModulesWithNoRootCommand();
                    }

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
                            BackupActivity backupActivity = (BackupActivity)activity;
                            FragmentManager manager = backupActivity.getSupportFragmentManager();
                            BackupFragment fragment = (BackupFragment) manager.findFragmentById(R.id.backupFragment);
                            if (fragment != null) {
                                fragment.closePleaseWaitDialog();
                                fragment.showToast(activity.getString(R.string.wrong));
                            }
                        } catch (Exception ex) {
                            Log.e(LOG_TAG, "RestoreHelper close progress fault " + ex.getMessage() + " " +ex.getCause());
                        }

                    }
                }
            }
        };

        Thread thread = new Thread(restore);
        thread.start();
    }

    private boolean isBackupExist() {
        File file = new File(pathBackup + "/InvizibleBackup.zip");
        return file.isFile();
    }

    private String saveSomeOldInfo() {
        String code = "";
        if (activity.getString(R.string.appVersion).endsWith("o")) {
            code = new PrefManager(activity).getStrPref("registrationCode");
        }
        return code;
    }

    private void restoreOldInfo(String code) {
        new PrefManager(activity).setStrPref("registrationCode", code);
    }

    private void refreshInstallationParameters() {
        PathVars pathVars = new PathVars(activity);
        pathVars.saveAppUID(activity);
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

        Log.i(LOG_TAG, "RestoreHelper: sharedPreferences restore OK");
    }

    @SuppressWarnings({ "unchecked" })
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
