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
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;
import java.util.Objects;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class BackupActivity extends LangAppCompatActivity implements View.OnClickListener {

    EditText etFilePath = null;
    String pathBackup = null;
    ProgressBar pbBackup = null;
    BroadcastReceiver br = null;
    private boolean flagSave = false;
    private boolean flagRestore = false;
    String appDataDir;
    String busyboxPath;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }


        setContentView(R.layout.activity_backup);
        findViewById(R.id.btnRestoreBackup).setOnClickListener(this);
        Button btnSaveBackup = findViewById(R.id.btnSaveBackup);
        btnSaveBackup.setOnClickListener(this);
        btnSaveBackup.requestFocus();
        pbBackup = findViewById(R.id.pbBackup);
        pbBackup.setVisibility(View.INVISIBLE);
        etFilePath=findViewById(R.id.etPathBackup);
        PathVars pathVars = new PathVars(this);
        pathBackup = pathVars.pathBackup;
        etFilePath.setText(pathBackup);
        etFilePath.setOnClickListener(this);
        appDataDir = pathVars.appDataDir;
        busyboxPath = pathVars.busyboxPath;

        br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(LOG_TAG,"BackupActivity onReceive");

                if (intent != null) {
                    final String action = intent.getAction();
                    if ((action == null) || (action.equals("") || intent.getIntExtra("Mark",0)!=
                            RootExecService.BackupActivityMark)) return;

                    if(action.equals(RootExecService.COMMAND_RESULT)){
                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        if(comResult.getCommands().length == 0){
                            Toast.makeText(context,R.string.wrong,Toast.LENGTH_LONG).show();
                            return;
                        }


                        StringBuilder sb = new StringBuilder();
                        for (String com:comResult.getCommands()){
                            Log.i(LOG_TAG,com);
                            sb.append(com).append((char)10);
                        }

                        TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
                        commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);


                        pbBackup.setVisibility(View.INVISIBLE);
                        pbBackup.setIndeterminate(false);

                        if(flagSave) Toast.makeText(context,"Backup OK",Toast.LENGTH_SHORT).show();
                        if(flagRestore) Toast.makeText(context,"Restore OK",Toast.LENGTH_SHORT).show();

                    }
                }
            }
        };

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnRestoreBackup:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                String[] commandsRestore = {
                        busyboxPath+ "killall dnscrypt-proxy",
                        busyboxPath+ "killall tor",
                        busyboxPath+ "killall i2pd",
                        busyboxPath+ "sleep 5",
                        "cd "+appDataDir,
                        //busyboxPath+ "mkdir -p cache",
                        //busyboxPath+ "chmod -R 755 cache",
                        busyboxPath+ "sleep 1",
                        "app_bin/gnutar -xvzpf "+pathBackup+"/Backup.arch app_bin app_data",
                        busyboxPath+ "sleep 3",
                        "restorecon -R "+appDataDir,
                };
                RootCommands rootCommands = new RootCommands(commandsRestore );
                Intent intent = new Intent(this, RootExecService.class);
                intent.setAction(RootExecService.RUN_COMMAND);
                intent.putExtra("Commands",rootCommands);
                intent.putExtra("Mark", RootExecService.BackupActivityMark);
                RootExecService.performAction(this,intent);
                flagRestore = true;
                flagSave = false;

                pbBackup.setVisibility(View.VISIBLE);
                pbBackup.setIndeterminate(true);

                break;
            case R.id.btnSaveBackup:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                String[] commandsSave = {
                        "cd "+appDataDir,
                        //busyboxPath+ "mkdir -p cache",
                        //busyboxPath+ "chmod -R 755 cache",
                        busyboxPath+ "sleep 1",
                        busyboxPath+ "mkdir -p "+pathBackup,
                        "app_bin/gnutar -cvzpf "+pathBackup+"/Backup.arch app_bin app_data/dnscrypt-proxy" +
                                " app_data/tor/bridges_custom.lst app_data/tor/bridges_default.lst " +
                                "app_data/tor/geoip app_data/tor/geoip6 app_data/tor/tor.conf app_data/i2pd",
                        busyboxPath+ "sleep 3"
                };
                rootCommands = new RootCommands(commandsSave);
                intent = new Intent(this, RootExecService.class);
                intent.setAction(RootExecService.RUN_COMMAND);
                intent.putExtra("Commands",rootCommands);
                intent.putExtra("Mark", RootExecService.BackupActivityMark);
                RootExecService.performAction(this,intent);
                flagSave = true;
                flagRestore = false;

                pbBackup.setVisibility(View.VISIBLE);
                pbBackup.setIndeterminate(true);

                break;
            case R.id.etPathBackup:

                DialogProperties properties = new DialogProperties();
                properties.selection_mode = DialogConfigs.SINGLE_MODE;
                properties.selection_type = DialogConfigs.DIR_SELECT;
                properties.root = new File(Environment.getExternalStorageDirectory().toURI());
                properties.error_dir = new File(Environment.getExternalStorageDirectory().toURI());
                properties.offset = new File(Environment.getExternalStorageDirectory().toURI());
                properties.extensions = new String[]{"arch:"};
                FilePickerDialog dial = new FilePickerDialog(this,properties);
                dial.setTitle(R.string.backupFolder);
                dial.setDialogSelectionListener(new DialogSelectionListener() {
                    @Override
                    public void onSelectedFilePaths(String[] files) {
                        pathBackup = files[0];
                        etFilePath.setText(pathBackup);
                    }
                });
                dial.show();
                break;
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter(RootExecService.COMMAND_RESULT);
        this.registerReceiver(br,intentFilter);
        setTitle(R.string.drawer_menu_backup);
    }

    @Override
    public void onStop() {
        super.onStop();
        this.unregisterReceiver(br);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {// API 5+ solution
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
