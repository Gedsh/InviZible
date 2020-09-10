package pan.alexander.tordnscrypt.help;

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import androidx.fragment.app.DialogFragment;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.zipUtil.ZipFileManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class HelpActivityReceiver extends BroadcastReceiver {
    private Handler mHandler;
    private String appDataDir;
    private String cacheDir;
    private String info;
    private String pathToSaveLogs;
    private DialogFragment progressDialog;

    public HelpActivityReceiver(Handler mHandler, String appDataDir, String cacheDir, String pathToSaveLogs) {
        this.mHandler = mHandler;
        this.appDataDir = appDataDir;
        this.cacheDir = cacheDir;
        this.pathToSaveLogs = pathToSaveLogs;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(LOG_TAG, "BackupActivity onReceive");

        if (!isBroadcastMatch(intent)) {
            return;
        }

        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

        if (comResult != null && comResult.getCommands().size() == 0) {
            closeProgressDialog();
            showSomethingWrongToast(context);
            return;
        }

        CachedExecutor.INSTANCE.getExecutorService().submit(saveLogs(context, comResult));
    }

    Runnable saveLogs(final Context context, final RootCommands comResult) {
        return () -> {

            if (isRootMethodWroteLogs(comResult)) {
                deleteRootExecLog(context);
                saveLogsMethodOne(context);
            } else {
                saveLogsMethodTwo(context);
                deleteRootExecLog(context);
            }

            if (isLogsExist()) {
                FileOperations.moveBinaryFile(context, cacheDir
                        + "/logs", "InvizibleLogs.txt", pathToSaveLogs, "InvizibleLogs.txt");
            } else {
                closeProgressDialog();
                showSomethingWrongToast(context);
                Log.e(LOG_TAG, "Collect logs alternative method fault");
            }
        };
    }

    private void saveLogsMethodOne(Context context) {
        try {
            ZipFileManager zipFileManager = new ZipFileManager(cacheDir + "/logs/InvizibleLogs.txt");
            zipFileManager.createZip(context, cacheDir + "/logs_dir");
            FileOperations.deleteDirSynchronous(context,cacheDir + "/logs_dir");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Create zip file for first method failed  " + e.getMessage() + " " + e.getCause());
        }
    }

    private void saveLogsMethodTwo(Context context) {

        String logsDirPath = cacheDir + "/logs_dir";
        File logsDir = new File(logsDirPath);

        if (!logsDir.isDirectory()) {
            if (!logsDir.mkdirs()) {
                throw new IllegalStateException("HelpActivityReceiver unablr to create logs dir");
            }
        }

        try {
            FileOperations.copyFolderSynchronous(context, appDataDir + "/logs", logsDirPath);
            FileOperations.copyFolderSynchronous(context, appDataDir + "/shared_prefs", logsDirPath);

            Process process = Runtime.getRuntime().exec("logcat -d");
            StringBuilder log = new StringBuilder();
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    log.append(line);
                    log.append(System.lineSeparator());
                }
            }

            try (FileWriter out = new FileWriter(logsDirPath + "/logcat.log")) {
                out.write(log.toString());
            }

            process.destroy();

            try (FileWriter out = new FileWriter(logsDirPath + "/device_info.log")) {
                if (info != null) {
                    out.write(info);
                }
            }

            saveLogsMethodOne(context);

        } catch (Exception e) {
            Log.e(LOG_TAG, "Collect logs alternative method fault " + e.getMessage() + " " + e.getCause());
            showSomethingWrongToast(context);
        }
    }

    private void deleteRootExecLog(Context context) {
        File file = new File(appDataDir + "/logs/RootExec.log");
        if (new PrefManager(context).getBoolPref("swRootCommandsLog") && file.isFile()) {
            FileOperations.deleteFileSynchronous(context, appDataDir + "/logs", "RootExec.log");
        }
    }

    private boolean isBroadcastMatch(Intent intent) {
        if (intent == null) {
            return false;
        }

        String action = intent.getAction();

        if ((action == null) || (action.equals(""))) {
            return false;
        }

        if (!action.equals(RootExecService.COMMAND_RESULT)) {
            return false;
        }

        return intent.getIntExtra("Mark", 0) == RootExecService.HelpActivityMark;
    }

    private void closeProgressDialog() {
        mHandler.post(() -> {
            if (progressDialog != null) {
                progressDialog.dismiss();
                progressDialog = null;
            }

        });
    }

    private void showSomethingWrongToast(final Context context) {
        mHandler.post(() -> Toast.makeText(context, R.string.wrong, Toast.LENGTH_LONG).show());
    }

    private boolean isRootMethodWroteLogs(RootCommands comResult) {
        if (comResult == null) {
            return false;
        }

        File invizibleLogs = new File(cacheDir + "/logs_dir");

        return comResult.getCommands().toString().contains("Logs Saved")
                && invizibleLogs.exists()
                && invizibleLogs.list() != null
                && Objects.requireNonNull(invizibleLogs.list()).length > 0;
    }

    private boolean isLogsExist() {
        File invizibleLogs = new File(cacheDir + "/logs/InvizibleLogs.txt");
        return invizibleLogs.exists();
    }

    public void setPathToSaveLogs(String pathToSaveLogs) {
        this.pathToSaveLogs = pathToSaveLogs;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public void setProgressDialog(DialogFragment progressDialog) {
        this.progressDialog = progressDialog;
    }
}
