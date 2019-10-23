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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
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

import pan.alexander.tordnscrypt.LangAppCompatActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.fileOperations.ExternalStoragePermissions;
import pan.alexander.tordnscrypt.utils.fileOperations.FileOperations;
import pan.alexander.tordnscrypt.utils.fileOperations.OnBinaryFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.moveBinaryFile;

public class BackupActivity extends LangAppCompatActivity implements View.OnClickListener, OnBinaryFileOperationsCompleteListener {

    EditText etFilePath = null;
    String pathBackup = null;
    ProgressBar pbBackup = null;
    String appDataDir;
    String busyboxPath;
    DialogInterface progress;

    private BackupHelper backupHelper;
    private RestoreHelper restoreHelper;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);


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

        backupHelper = new BackupHelper(this, appDataDir, pathBackup);
        restoreHelper = new RestoreHelper(this, appDataDir, pathBackup);

        FileOperations.setOnFileOperationCompleteListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btnRestoreBackup:
                restoreBackup();
                break;
            case R.id.btnSaveBackup:
                saveBackup();
                break;
            case R.id.etPathBackup:
                selectBackupPath();
                break;
        }

    }

    private void restoreBackup() {
        ExternalStoragePermissions permissions = new ExternalStoragePermissions(this);
        if (!permissions.isWritePermissions()) {
            permissions.requestReadWritePermissions();
            return;
        }

        progress = FileOperations.fileOperationProgressDialog(this);

        restoreHelper.restoreAll();
    }

    private void saveBackup() {
        ExternalStoragePermissions permissions = new ExternalStoragePermissions(this);
        if (!permissions.isWritePermissions()) {
            permissions.requestReadWritePermissions();
            return;
        }

        progress = FileOperations.fileOperationProgressDialog(this);

        backupHelper.saveAll();
    }

    private void selectBackupPath() {
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
                backupHelper.setPathBackup(pathBackup);
                restoreHelper.setPathBackup(pathBackup);
            }
        });
        dial.show();
    }

    void closeProgress() {
        progress.dismiss();
    }

    void showToast(final Activity activity, final String text) {
        if (activity == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        setTitle(R.string.drawer_menu_backup);
    }

    @Override
    public void onStop() {
        super.onStop();

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
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag) {
        if (currentFileOperation == moveBinaryFile && tag.equals("InvizibleBackup.zip")) {
            closeProgress();
            showToast(this, "Backup OK");
        } else if (currentFileOperation == deleteFile && tag.equals("sharedPreferences")) {
            closeProgress();
            showToast(this, "Restore OK");
        }
    }
}
