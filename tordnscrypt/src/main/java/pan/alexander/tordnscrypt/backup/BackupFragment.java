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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Environment;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitProgressDialog;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.ExternalStoragePermissions;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnBinaryFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.moveBinaryFile;


public class BackupFragment extends Fragment implements View.OnClickListener, OnBinaryFileOperationsCompleteListener {

    private EditText etFilePath = null;
    private String pathBackup = null;
    private String appDataDir;
    private DialogFragment progress;

    private BackupHelper backupHelper;
    private RestoreHelper restoreHelper;

    public BackupFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_backup, container, false);

        view.findViewById(R.id.btnRestoreBackup).setOnClickListener(this);
        Button btnSaveBackup = view.findViewById(R.id.btnSaveBackup);
        btnSaveBackup.setOnClickListener(this);
        btnSaveBackup.requestFocus();
        etFilePath = view.findViewById(R.id.etPathBackup);
        PathVars pathVars = PathVars.getInstance(view.getContext());
        pathBackup = pathVars.getDefaultBackupPath();
        etFilePath.setText(pathBackup);
        etFilePath.setOnClickListener(this);
        appDataDir = pathVars.getAppDataDir();

        return view;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        FileOperations.setOnFileOperationCompleteListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (restoreHelper != null) {
            restoreHelper.setActivity(getActivity());
        }

        if (backupHelper != null) {
            backupHelper.setContext(getActivity());
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnRestoreBackup:
                restoreHelper = new RestoreHelper(getActivity(), appDataDir, pathBackup);
                restoreBackup();
                break;
            case R.id.btnSaveBackup:
                backupHelper = new BackupHelper(getActivity(), appDataDir, pathBackup);
                saveBackup();
                break;
            case R.id.etPathBackup:
                selectBackupPath();
                break;
        }

    }

    private void restoreBackup() {
        ExternalStoragePermissions permissions = new ExternalStoragePermissions(getActivity());
        if (!permissions.isWritePermissions()) {
            permissions.requestReadWritePermissions();
            return;
        }

        openPleaseWaitDialog();

        restoreHelper.restoreAll();
    }

    private void saveBackup() {
        ExternalStoragePermissions permissions = new ExternalStoragePermissions(getActivity());
        if (!permissions.isWritePermissions()) {
            permissions.requestReadWritePermissions();
            return;
        }

        openPleaseWaitDialog();

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
        FilePickerDialog dial = new FilePickerDialog(getActivity(), properties);
        dial.setTitle(R.string.backupFolder);
        dial.setDialogSelectionListener(files -> {
            pathBackup = files[0];
            etFilePath.setText(pathBackup);

            if (backupHelper != null) {
                backupHelper.setPathBackup(pathBackup);
            }

            if (restoreHelper != null) {
                restoreHelper.setPathBackup(pathBackup);
            }
        });
        dial.show();
    }

    private void openPleaseWaitDialog() {
        if (getActivity() != null && getFragmentManager() != null) {
            try {
                progress = PleaseWaitProgressDialog.getInstance();
                progress.show(getFragmentManager(), "PleaseWaitProgressDialog");
            } catch (Exception ex) {
                Log.e(LOG_TAG, "BackupFragment open progress fault " + ex.getMessage() + " " +ex.getCause());
            }
        }
    }

    void closePleaseWaitDialog() {
        if (getActivity() != null) {
            try {
                progress.dismiss();
            } catch (Exception ex) {
                Log.e(LOG_TAG, "BackupFragment close progress fault " + ex.getMessage() + " " +ex.getCause());
            }
        }
    }

    void showToast(final String text) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        FileOperations.deleteOnFileOperationCompleteListener();
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag) {
        if (currentFileOperation == moveBinaryFile && tag.equals("InvizibleBackup.zip")) {
            closePleaseWaitDialog();
            showToast("Backup OK");
        } else if (currentFileOperation == deleteFile && tag.equals("sharedPreferences")) {
            closePleaseWaitDialog();
            showToast("Restore OK");
        }
    }
}
