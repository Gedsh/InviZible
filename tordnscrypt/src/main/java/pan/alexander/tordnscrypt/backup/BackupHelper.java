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
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.zipUtil.ZipFileManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.backup.BackupFragment.CODE_WRITE;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

class BackupHelper {
    private Activity activity;
    private String pathBackup;
    private final String appDataDir;
    private final String cacheDir;

    BackupHelper(Activity activity, String appDataDir, String cacheDir, String pathBackup) {
        this.activity = activity;
        this.appDataDir = appDataDir;
        this.cacheDir = cacheDir;
        this.pathBackup = pathBackup;
    }

    void saveAll() {

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                SharedPreferences defaultSharedPref = PreferenceManager.getDefaultSharedPreferences(activity);
                saveSharedPreferencesToFile(defaultSharedPref, cacheDir + "/defaultSharedPref");

                SharedPreferences sharedPreferences = activity.getSharedPreferences(PrefManager.getPrefName(), Context.MODE_PRIVATE);
                saveSharedPreferencesToFile(sharedPreferences, cacheDir + "/sharedPreferences");

                compressAllToZip(cacheDir + "/InvizibleBackup.zip",
                        appDataDir + "/app_bin", appDataDir + "/app_data",
                        cacheDir + "/defaultSharedPref", cacheDir + "/sharedPreferences");

                File backup = new File(cacheDir + "/InvizibleBackup.zip");
                if (!backup.isFile() || backup.length() == 0) {
                    throw new IllegalStateException("Backup file not exist " + backup.getAbsolutePath());
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    saveFile();
                } else {
                    saveFileWithSAF();
                }

                FileOperations.deleteFile(activity, cacheDir, "/defaultSharedPref", "ignored");
                FileOperations.deleteFile(activity, cacheDir, "/sharedPreferences", "ignored");
            } catch (Exception e) {
                Log.e(LOG_TAG, "BackupHelper saveAllToInternalDir fault " + e.getMessage() + " " + e.getCause());

                showError();

                FileOperations.deleteFile(activity, cacheDir, "/InvizibleBackup.zip", "ignored");
            }
        });
    }

    private void compressAllToZip(String outputFilePath, String... inputSources) throws Exception {
        ZipFileManager zipFileManager = new ZipFileManager(outputFilePath);
        zipFileManager.createZip(activity, inputSources);
    }

    private void saveSharedPreferencesToFile(SharedPreferences pref, String dst) {
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(dst))) {
            output.writeObject(pref.getAll());
        } catch (Exception e) {
            Log.e(LOG_TAG, "saveSharedPreferencesToFile fault " + e.getMessage() + " " + e.getCause());
        }
    }

    void saveFile() {
        FileOperations.moveBinaryFile(activity, cacheDir, "InvizibleBackup.zip", pathBackup, "InvizibleBackup.zip");
    }

    void saveFileWithSAF() {

        if (activity == null || activity.isFinishing()) {
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yy", Locale.getDefault());
        String currentDate = format.format(new Date());

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "IZBackup" + currentDate + ".zip");

        PackageManager packageManager = activity.getPackageManager();
        if (packageManager != null && intent.resolveActivity(packageManager) != null) {
            activity.startActivityForResult(intent, CODE_WRITE);
        }
    }

    void copyData(OutputStream outputStream) {

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try (FileInputStream fileInputStream = new FileInputStream(cacheDir + "/InvizibleBackup.zip")) {
                byte[] buffer = new byte[8 * 1024];

                for (int len; (len = fileInputStream.read(buffer)) > 0; ) {
                    outputStream.write(buffer, 0, len);
                }
                outputStream.flush();
            } catch (Exception e) {
                Log.e(LOG_TAG, "BackupHelper copyData fault " + e.getMessage() + " " + e.getCause());

                showError();

                FileOperations.deleteFile(activity, cacheDir, "/InvizibleBackup.zip", "ignored");
            } finally {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }

        });
    }

    private void showError() {
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
                Log.e(LOG_TAG, "BackupHelper close progress fault " + ex.getMessage()
                        + " " + ex.getCause() + " " + Arrays.toString(ex.getStackTrace()));
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
