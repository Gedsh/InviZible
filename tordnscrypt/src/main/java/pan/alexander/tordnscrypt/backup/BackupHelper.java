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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.content.SharedPreferences;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.zipUtil.ZipFileManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

class BackupHelper {
    private Context context;
    private String pathBackup;
    private String appDataDir;

    BackupHelper(Context context, String appDataDir, String pathBackup) {
        this.context = context;
        this.appDataDir = appDataDir;
        this.pathBackup = pathBackup;
    }

    void saveAll() {
        Runnable save = () -> {
            try {
                SharedPreferences defaultSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
                saveSharedPreferencesToFile(defaultSharedPref, appDataDir + "/cache/defaultSharedPref");

                SharedPreferences sharedPreferences = context.getSharedPreferences(PrefManager.getPrefName(), Context.MODE_PRIVATE);
                saveSharedPreferencesToFile(sharedPreferences, appDataDir + "/cache/sharedPreferences");

                compressAllToZip(appDataDir + "/cache/InvizibleBackup.zip",
                        appDataDir + "/app_bin", appDataDir + "/app_data",
                        appDataDir + "/cache/defaultSharedPref", appDataDir + "/cache/sharedPreferences");

                File backup = new File(appDataDir + "/cache/InvizibleBackup.zip");
                if (!backup.isFile() || backup.length() == 0) {
                    throw new IllegalStateException("Backup file not exist " + backup.getAbsolutePath());
                }

                FileOperations.moveBinaryFile(context, appDataDir + "/cache", "InvizibleBackup.zip", pathBackup, "InvizibleBackup.zip");

                FileOperations.deleteFile(context, appDataDir, "cache/defaultSharedPref", "ignored");
                FileOperations.deleteFile(context, appDataDir, "cache/sharedPreferences", "ignored");
            } catch (Exception e) {
                Log.e(LOG_TAG, "BackupHelper saveAllToInternalDir fault " + e.getMessage() + " " +e.getCause());

                if (context instanceof BackupActivity) {
                    try {
                        BackupActivity backupActivity = (BackupActivity)context;
                        FragmentManager manager = backupActivity.getSupportFragmentManager();
                        BackupFragment fragment = (BackupFragment) manager.findFragmentById(R.id.backupFragment);
                        if (fragment != null) {
                            fragment.closePleaseWaitDialog();
                            fragment.showToast(context.getString(R.string.wrong));
                        }
                    } catch (Exception ex) {
                        Log.e(LOG_TAG, "BackupHelper close progress fault " + ex.getMessage() + " " +ex.getCause());
                    }
                }

            }
        };

        Thread thread = new Thread(save);
        thread.start();
    }

    private void compressAllToZip(String outputFilePath, String ... inputSources) throws Exception {
        ZipFileManager zipFileManager = new ZipFileManager(outputFilePath);
        zipFileManager.createZip(inputSources);
    }

    private void saveSharedPreferencesToFile(SharedPreferences pref, String dst) {
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(dst))){
            output.writeObject(pref.getAll());
        } catch (Exception e) {
            Log.e(LOG_TAG, "saveSharedPreferencesToFile fault " + e.getMessage() + " " + e.getCause());
        }
    }

    void setPathBackup(String pathBackup) {
        this.pathBackup = pathBackup;
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
