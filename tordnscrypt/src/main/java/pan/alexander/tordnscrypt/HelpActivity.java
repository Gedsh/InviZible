package pan.alexander.tordnscrypt;
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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Objects;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.FileOperations;
import pan.alexander.tordnscrypt.utils.LangAppCompatActivity;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class HelpActivity extends LangAppCompatActivity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener, FileOperations.OnFileOperationsCompleteListener {

    EditText etLogsPath;
    Button btnSaveLogs;
    Switch swRootCommandsLog;
    BroadcastReceiver br;
    Handler mHandler;
    String appDataDir;
    String busyboxPath;
    String pathToSaveLogs;
    String iptablesPath;
    String appUID;
    DialogInterface dialogInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }

        etLogsPath = findViewById(R.id.etLogsPath);
        etLogsPath.setOnClickListener(this);
        btnSaveLogs = findViewById(R.id.btnSaveLogs);
        btnSaveLogs.setOnClickListener(this);
        swRootCommandsLog = findViewById(R.id.swRootCommandsLog);
        swRootCommandsLog.setChecked(new PrefManager(this).getBoolPref("swRootCommandsLog"));
        swRootCommandsLog.setOnCheckedChangeListener(this);

        mHandler = new Handler();

        br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(LOG_TAG,"BackupActivity onReceive");

                if (intent != null) {
                    final String action = intent.getAction();
                    if ((action == null) || (action.equals("") || intent.getIntExtra("Mark",0)!=
                            RootExecService.HelpActivityMark)) return;

                    if(action.equals(RootExecService.COMMAND_RESULT)){

                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        if(comResult.getCommands().length == 0){
                            if (dialogInterface!=null)
                                dialogInterface.dismiss();
                            Toast.makeText(context,R.string.wrong,Toast.LENGTH_LONG).show();
                            return;
                        }

                        if (new PrefManager(context).getBoolPref("swRootCommandsLog")) {
                            FileOperations.deleteFile(getApplicationContext(),appDataDir + "/logs","RootExec.log", "RootExec.log");
                            Log.e(LOG_TAG,"deleteFile function fault " + appDataDir + "logs/RootExec.log");
                        }

                        File invizibleLogs = new File(appDataDir + "/logs/InvizibleLogs.txt");
                        if (!Arrays.toString(comResult.getCommands()).contains("Logs Saved") || !invizibleLogs.exists()) {
                            Log.e(LOG_TAG,"Collect logs first method fault");
                            try {
                                Process process = Runtime.getRuntime().exec("logcat -d");
                                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                                StringBuilder log = new StringBuilder();
                                String line;
                                while ((line = bufferedReader.readLine()) != null) {
                                    log.append(line);
                                    log.append("\n");
                                }
                                bufferedReader.close();

                                FileWriter out = new FileWriter(appDataDir + "/logs/InvizibleLogs.txt");
                                out.write(log.toString());
                                out.close();

                            } catch (IOException e) {
                                Log.e(LOG_TAG,"Collect logs alternative method fault " + e.getMessage());
                                Toast.makeText(context,R.string.wrong,Toast.LENGTH_LONG).show();
                            }

                        }

                        invizibleLogs = new File(appDataDir + "/logs/InvizibleLogs.txt");
                        if (!invizibleLogs.exists()) {
                            Toast.makeText(context,R.string.wrong,Toast.LENGTH_LONG).show();
                            Log.e(LOG_TAG,"Collect logs alternative method fault");
                            if (dialogInterface!=null)
                                dialogInterface.dismiss();
                            return;
                        }

                        FileOperations.moveBinaryFile(getApplicationContext(),appDataDir
                                + "/logs","InvizibleLogs.txt",pathToSaveLogs,"InvizibleLogs.txt");
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter(RootExecService.COMMAND_RESULT);
        this.registerReceiver(br,intentFilter);
        setTitle(R.string.drawer_menu_help);

        PathVars pathVars = new PathVars(this);
        appDataDir = pathVars.appDataDir;
        busyboxPath = pathVars.busyboxPath;
        pathToSaveLogs = pathVars.pathBackup;
        iptablesPath = pathVars.iptablesPath;
        appUID = new PrefManager(this).getStrPref("appUID");

        etLogsPath.setText(pathToSaveLogs);

        FileOperations.setOnFileOperationCompleteListener(this);
    }

    @Override
    public void onClick(View view) {

        switch (view.getId()) {

            case R.id.btnSaveLogs:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    break;
                }


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
                            "APP_VERSION_CODE " + BuildConfig.VERSION_CODE + (char) 10  +
                            "APP_VERSION_NAME " + BuildConfig.VERSION_NAME + (char) 10  +
                            "APP_PROC_VERSION " + TopFragment.appProcVersion + (char) 10  +
                            "APP_VERSION " + TopFragment.appVersion + (char) 10  +
                            "DNSCRYPT_INTERNAL_VERSION " + TopFragment.DNSCryptVersion + (char) 10  +
                            "TOR_INTERNAL_VERSION " + TopFragment.TorVersion + (char) 10  +
                            "I2PD_INTERNAL_VERSION " + TopFragment.ITPDVersion + (char) 10  +
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
                            "APP_VERSION_CODE " + BuildConfig.VERSION_CODE + (char) 10  +
                            "APP_VERSION_NAME " + BuildConfig.VERSION_NAME + (char) 10  +
                            "APP_PROC_VERSION " + TopFragment.appProcVersion + (char) 10  +
                            "APP_VERSION " + TopFragment.appVersion + (char) 10  +
                            "DNSCRYPT_INTERNAL_VERSION " + TopFragment.DNSCryptVersion + (char) 10  +
                            "TOR_INTERNAL_VERSION " + TopFragment.TorVersion + (char) 10  +
                            "I2PD_INTERNAL_VERSION " + TopFragment.ITPDVersion + (char) 10  +
                            "SIGN_VERSION " + TopFragment.appSign;
                }

                int pid = android.os.Process.myPid();

                String[] logcatCommands = {
                        "cd " + appDataDir,
                        busyboxPath + "mkdir -m 655 -p tmp",
                        busyboxPath + "cp -R logs tmp",
                        "logcat -d | grep " + pid + " > tmp/logcat.log",
                        iptablesPath + "iptables -L > tmp/filter.log",
                        iptablesPath + "iptables -t nat -L > tmp/nat.log",
                        busyboxPath + "cp -R shared_prefs tmp",
                        busyboxPath + "sleep 1",
                        busyboxPath + "echo \"" + info + "\" > tmp/device_info.log",
                        busyboxPath + "mkdir -p " + pathToSaveLogs,
                        "app_bin/gnutar -czf " + "logs/InvizibleLogs.txt tmp",
                        //busyboxPath + "cp -f logs/InvizibleLogs.txt " + pathToSaveLogs,
                        "restorecon logs/InvizibleLogs.txt",
                        busyboxPath+ "chown "+appUID+"."+appUID+" "+"logs/InvizibleLogs.txt",
                        busyboxPath+ "chmod 644 logs/InvizibleLogs.txt",
                        busyboxPath + "rm -rf tmp",
                        busyboxPath + "echo 'Logs Saved'"
                };
                RootCommands rootCommands = new RootCommands(logcatCommands);
                Intent intent = new Intent(this, RootExecService.class);
                intent.setAction(RootExecService.RUN_COMMAND);
                intent.putExtra("Commands",rootCommands);
                intent.putExtra("Mark", RootExecService.HelpActivityMark);
                RootExecService.performAction(this,intent);

                dialogInterface = FileOperations.fileOperationProgressDialog(this);
                break;
            case R.id.etLogsPath:

                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.DIR_SELECT;
                properties.root = new File(Environment.getExternalStorageDirectory().toURI());
                properties.error_dir = new File(Environment.getExternalStorageDirectory().toURI());
                properties.offset = new File(Environment.getExternalStorageDirectory().toURI());
                FilePickerDialog dial = new FilePickerDialog(this,properties);
                dial.setTitle(R.string.backupFolder);
                dial.setDialogSelectionListener(new DialogSelectionListener() {
                    @Override
                    public void onSelectedFilePaths(String[] files) {
                        pathToSaveLogs = files[0];
                        etLogsPath.setText(pathToSaveLogs);
                    }
                });
                dial.show();
                break;
        }
    }



    @Override
    public void onStop() {
        super.onStop();
        if (dialogInterface!=null)
            dialogInterface.dismiss();
        this.unregisterReceiver(br);
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
            new PrefManager(this).setBoolPref("swRootCommandsLog",newValue);

            if (!newValue) {
                FileOperations.deleteFile(getApplicationContext(),appDataDir + "/logs","RootExec.log","RootExec.log");
            }
        }
    }

    @Override
    public void OnFileOperationComplete(String currentFileOperation, String path, String tag) {
        if (currentFileOperation.equals(FileOperations.deleteFileCurrentOperation) && !FileOperations.fileOperationResult) {
            Log.e(LOG_TAG, "Unable to delete file " + path);
        } else if (currentFileOperation.equals(FileOperations.moveBinaryFileCurrentOperation)) {

            if (dialogInterface!=null)
                dialogInterface.dismiss();
            if (FileOperations.fileOperationResult) {

                TopFragment.NotificationDialogFragment commandResult =
                        TopFragment.NotificationDialogFragment.newInstance(getText(R.string.help_activity_logs_saved).toString()
                                + " " + pathToSaveLogs);
                commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
            } else {
                TopFragment.NotificationDialogFragment commandResult =
                        TopFragment.NotificationDialogFragment.newInstance(getText(R.string.help_activity_logs_saved).toString()
                                + " " + appDataDir + "/logs/InvizibleLogs.txt");
                commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
            }
        }
    }
}
