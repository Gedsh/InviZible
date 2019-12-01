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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;

import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import pan.alexander.tordnscrypt.BuildConfig;
import pan.alexander.tordnscrypt.LangAppCompatActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitProgressDialog;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.ExternalStoragePermissions;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnBinaryFileOperationsCompleteListener;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.moveBinaryFile;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class HelpActivity extends LangAppCompatActivity implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener, OnBinaryFileOperationsCompleteListener {

    private EditText etLogsPath;
    private HelpActivityReceiver br;
    private String appDataDir;
    private String busyboxPath;
    private String pathToSaveLogs;
    private String iptablesPath;
    private String appUID;
    private static DialogFragment dialogFragment;
    private ModulesStatus modulesStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        etLogsPath = findViewById(R.id.etLogsPath);
        etLogsPath.setOnClickListener(this);
        Button btnSaveLogs = findViewById(R.id.btnSaveLogs);
        btnSaveLogs.setOnClickListener(this);
        btnSaveLogs.requestFocus();
        SwitchCompat swRootCommandsLog = findViewById(R.id.swRootCommandsLog);
        swRootCommandsLog.setChecked(new PrefManager(this).getBoolPref("swRootCommandsLog"));
        swRootCommandsLog.setOnCheckedChangeListener(this);

        Handler mHandler = new Handler();

        PathVars pathVars = new PathVars(this);
        appDataDir = pathVars.appDataDir;
        busyboxPath = pathVars.busyboxPath;
        pathToSaveLogs = pathVars.pathBackup;
        iptablesPath = pathVars.iptablesPath;
        appUID = new PrefManager(this).getStrPref("appUID");

        br = new HelpActivityReceiver(mHandler, appDataDir, pathToSaveLogs);

        modulesStatus = ModulesStatus.getInstance();

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (modulesStatus.isRootAvailable()) {
            IntentFilter intentFilter = new IntentFilter(RootExecService.COMMAND_RESULT);
            this.registerReceiver(br, intentFilter);
        }

        setTitle(R.string.drawer_menu_help);

        etLogsPath.setText(pathToSaveLogs);

        FileOperations.setOnFileOperationCompleteListener(this);
    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {

            case R.id.btnSaveLogs:
                if (!isWriteExternalStoragePermissions()) {
                    requestWriteExternalStoragePermissions();
                    return;
                }

                String info = collectInfo();
                br.setInfo(info);

                dialogFragment = PleaseWaitProgressDialog.getInstance();
                dialogFragment.show(getSupportFragmentManager(), "PleaseWaitProgressDialog");
                br.setProgressDialog(dialogFragment);

                if (modulesStatus.isRootAvailable()) {
                    collectLogsMethodOne(info);
                } else {
                    new Thread(br.saveLogs(getApplicationContext(), null)).start();
                }
                break;
            case R.id.etLogsPath:
                chooseOutputFolder();
                break;
        }
    }

    private void chooseOutputFolder() {
        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.DIR_SELECT;
        properties.root = new File(Environment.getExternalStorageDirectory().toURI());
        properties.error_dir = new File(Environment.getExternalStorageDirectory().toURI());
        properties.offset = new File(Environment.getExternalStorageDirectory().toURI());
        FilePickerDialog dial = new FilePickerDialog(this, properties);
        dial.setTitle(R.string.backupFolder);
        dial.setDialogSelectionListener(new DialogSelectionListener() {
            @Override
            public void onSelectedFilePaths(String[] files) {
                pathToSaveLogs = files[0];
                etLogsPath.setText(pathToSaveLogs);
                br.setPathToSaveLogs(pathToSaveLogs);
            }
        });
        dial.show();
    }

    private void requestWriteExternalStoragePermissions() {
        ExternalStoragePermissions permissions = new ExternalStoragePermissions(this);
        permissions.requestReadWritePermissions();
    }

    private boolean isWriteExternalStoragePermissions() {
        ExternalStoragePermissions permissions = new ExternalStoragePermissions(this);
        return permissions.isWritePermissions();
    }

    private void collectLogsMethodOne (String info) {
        int pid = android.os.Process.myPid();

        String[] logcatCommands = {
                "cd " + appDataDir,
                busyboxPath + "rm -rf logs_dir",
                busyboxPath + "mkdir -m 655 -p logs_dir",
                busyboxPath + "cp -R logs logs_dir",
                "logcat -d | grep " + pid + " > logs_dir/logcat.log",
                iptablesPath + "iptables -L > logs_dir/filter.log",
                iptablesPath + "iptables -t nat -L > logs_dir/nat.log",
                busyboxPath + "cp -R shared_prefs logs_dir",
                busyboxPath + "sleep 1",
                busyboxPath + "echo \"" + info + "\" > logs_dir/device_info.log",
                "restorecon -R logs_dir",
                busyboxPath + "chown -R " + appUID + "." + appUID + " logs_dir",
                busyboxPath + "chmod -R 755 logs_dir",
                busyboxPath + "echo 'Logs Saved'"
        };
        RootCommands rootCommands = new RootCommands(logcatCommands);
        Intent intent = new Intent(this, RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands", rootCommands);
        intent.putExtra("Mark", RootExecService.HelpActivityMark);
        RootExecService.performAction(this, intent);
    }

    private String collectInfo() {
        String info;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            info = "BRAND " + Build.BRAND + (char) 10 +
                    "MODEL " + Build.MODEL + (char) 10 +
                    "MANUFACTURER " + Build.MANUFACTURER + (char) 10 +
                    "PRODUCT " + Build.PRODUCT + (char) 10 +
                    "DEVICE " + Build.DEVICE + (char) 10 +
                    "BOARD " + Build.BOARD + (char) 10 +
                    "HARDWARE " + Build.HARDWARE + (char) 10 +
                    "SUPPORTED_ABIS " + Arrays.toString(Build.SUPPORTED_ABIS) + (char) 10 +
                    "SUPPORTED_32_BIT_ABIS " + Arrays.toString(Build.SUPPORTED_32_BIT_ABIS) + (char) 10 +
                    "SUPPORTED_64_BIT_ABIS " + Arrays.toString(Build.SUPPORTED_64_BIT_ABIS) + (char) 10 +
                    "SDK_INT " + Build.VERSION.SDK_INT + (char) 10 +
                    "APP_VERSION_CODE " + BuildConfig.VERSION_CODE + (char) 10 +
                    "APP_VERSION_NAME " + BuildConfig.VERSION_NAME + (char) 10 +
                    "APP_PROC_VERSION " + TopFragment.appProcVersion + (char) 10 +
                    "APP_VERSION " + TopFragment.appVersion + (char) 10 +
                    "DNSCRYPT_INTERNAL_VERSION " + TopFragment.DNSCryptVersion + (char) 10 +
                    "TOR_INTERNAL_VERSION " + TopFragment.TorVersion + (char) 10 +
                    "I2PD_INTERNAL_VERSION " + TopFragment.ITPDVersion + (char) 10 +
                    "SIGN_VERSION " + TopFragment.appSign;
        } else {
            info = "BRAND " + Build.BRAND + (char) 10 +
                    "MODEL " + Build.MODEL + (char) 10 +
                    "MANUFACTURER " + Build.MANUFACTURER + (char) 10 +
                    "PRODUCT " + Build.PRODUCT + (char) 10 +
                    "DEVICE " + Build.DEVICE + (char) 10 +
                    "BOARD " + Build.BOARD + (char) 10 +
                    "HARDWARE " + Build.HARDWARE + (char) 10 +
                    "SDK_INT " + Build.VERSION.SDK_INT + (char) 10 +
                    "APP_VERSION_CODE " + BuildConfig.VERSION_CODE + (char) 10 +
                    "APP_VERSION_NAME " + BuildConfig.VERSION_NAME + (char) 10 +
                    "APP_PROC_VERSION " + TopFragment.appProcVersion + (char) 10 +
                    "APP_VERSION " + TopFragment.appVersion + (char) 10 +
                    "DNSCRYPT_INTERNAL_VERSION " + TopFragment.DNSCryptVersion + (char) 10 +
                    "TOR_INTERNAL_VERSION " + TopFragment.TorVersion + (char) 10 +
                    "I2PD_INTERNAL_VERSION " + TopFragment.ITPDVersion + (char) 10 +
                    "SIGN_VERSION " + TopFragment.appSign;
        }

        return info;
    }


    @Override
    public void onStop() {
        super.onStop();

        if (modulesStatus.isRootAvailable()) {
            this.unregisterReceiver(br);
        }

        FileOperations.deleteOnFileOperationCompleteListener();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {// API 5+ solution
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean newValue) {
        if (compoundButton.getId() == R.id.swRootCommandsLog) {
            new PrefManager(this).setBoolPref("swRootCommandsLog", newValue);

            if (!newValue) {
                FileOperations.deleteFile(getApplicationContext(), appDataDir + "/logs", "RootExec.log", "RootExec.log");
            }
        }
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag) {
        if (currentFileOperation == deleteFile && !fileOperationResult) {
            Log.e(LOG_TAG, "Unable to delete file " + path);
        } else if (currentFileOperation == moveBinaryFile) {

            if (dialogFragment != null) {
                dialogFragment.dismiss();
                dialogFragment = null;
            }

            if (fileOperationResult) {

                DialogFragment commandResult =
                        NotificationDialogFragment.newInstance(getText(R.string.help_activity_logs_saved).toString()
                                + " " + pathToSaveLogs);
                commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
            } else {
                DialogFragment commandResult =
                        NotificationDialogFragment.newInstance(getText(R.string.help_activity_logs_saved).toString()
                                + " " + appDataDir + "/logs/InvizibleLogs.txt");
                commandResult.show(getSupportFragmentManager(), "NotificationDialogFragment");
            }
        }
    }
}
