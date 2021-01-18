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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.cardview.widget.CardView;
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
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitProgressDialog;
import pan.alexander.tordnscrypt.help.Utils;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.ExternalStoragePermissions;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnBinaryFileOperationsCompleteListener;

import static android.app.Activity.RESULT_OK;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.moveBinaryFile;


public class BackupFragment extends Fragment implements View.OnClickListener, OnBinaryFileOperationsCompleteListener {

    final static int CODE_READ = 10;
    final static int CODE_WRITE = 20;

    private LinearLayoutCompat llFragmentBackup;
    private CardView cardRules;
    private EditText etFilePath;
    private String pathBackup;
    private String cacheDir;
    private String appDataDir;
    private DialogFragment progress;

    private BackupHelper backupHelper;
    private RestoreHelper restoreHelper;

    public BackupFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        FileOperations.setOnFileOperationCompleteListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_backup, container, false);

        Button btnRestoreBackup = view.findViewById(R.id.btnRestoreBackup);
        btnRestoreBackup.setOnClickListener(this);

        Button btnSaveBackup = view.findViewById(R.id.btnSaveBackup);
        btnSaveBackup.setOnClickListener(this);
        btnSaveBackup.requestFocus();

        llFragmentBackup = view.findViewById(R.id.llFragmentBackup);

        cardRules = view.findViewById(R.id.cardRules);

        hideSelectionEditTextIfRequired(getActivity());

        etFilePath = view.findViewById(R.id.etPathBackup);

        PathVars pathVars = PathVars.getInstance(view.getContext());
        pathBackup = pathVars.getDefaultBackupPath();
        cacheDir = pathVars.getCacheDirPath(view.getContext());

        etFilePath.setText(pathBackup);
        etFilePath.setOnClickListener(this);

        appDataDir = pathVars.getAppDataDir();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();

        if (activity == null) {
            return;
        }

        if (restoreHelper != null) {
            restoreHelper.setActivity(activity);
        }

        if (backupHelper != null) {
            backupHelper.setActivity(activity);
        }
    }

    @Override
    public void onClick(View v) {
        Activity activity = getActivity();

        if (activity == null) {
            return;
        }

        int id = v.getId();
        if (id == R.id.btnRestoreBackup) {
            restoreHelper = new RestoreHelper(activity, appDataDir, cacheDir, pathBackup);
            restoreBackup();
        } else if (id == R.id.btnSaveBackup) {
            backupHelper = new BackupHelper(activity, appDataDir, cacheDir, pathBackup);
            saveBackup();
        } else if (id == R.id.etPathBackup) {
            selectBackupPath(activity);
        }

    }

    private void restoreBackup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ExternalStoragePermissions permissions = new ExternalStoragePermissions(getActivity());
            if (!permissions.isWritePermissions()) {
                permissions.requestReadWritePermissions();
                return;
            }

            openPleaseWaitDialog();

            restoreHelper.restoreAll(null);
        } else {
            restoreHelper.openFileWithSAF();
        }

    }

    private void saveBackup() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ExternalStoragePermissions permissions = new ExternalStoragePermissions(getActivity());
            if (!permissions.isWritePermissions()) {
                permissions.requestReadWritePermissions();
                return;
            }
        }

        openPleaseWaitDialog();

        backupHelper.saveAll();
    }

    private void selectBackupPath(Activity activity) {

        if (activity == null || activity.isFinishing()) {
            return;
        }

        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.DIR_SELECT;
        properties.root = new File(Environment.getExternalStorageDirectory().getPath());
        properties.error_dir = new File(PathVars.getInstance(activity).getCacheDirPath(activity));
        properties.offset = new File(Environment.getExternalStorageDirectory().getPath());
        properties.extensions = null;

        FilePickerDialog dial = new FilePickerDialog(activity, properties);
        dial.setTitle(R.string.backupFolder);
        dial.setDialogSelectionListener(files -> setBackupPath(files[0]));
        dial.show();
    }

    private void setBackupPath(String path) {
        pathBackup = path;

        etFilePath.setText(path);

        if (backupHelper != null) {
            backupHelper.setPathBackup(path);
        }

        if (restoreHelper != null) {
            restoreHelper.setPathBackup(path);
        }
    }

    private void openPleaseWaitDialog() {
        if (getActivity() != null && isAdded()) {
            try {
                progress = PleaseWaitProgressDialog.getInstance();
                progress.show(getParentFragmentManager(), "PleaseWaitProgressDialog");
            } catch (Exception ex) {
                Log.e(LOG_TAG, "BackupFragment open progress fault " + ex.getMessage() + " " + ex.getCause());
            }
        }
    }

    void closePleaseWaitDialog() {
        Activity activity = getActivity();

        if (activity == null || activity.isFinishing() || progress == null) {
            return;
        }

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                while (progress != null) {
                    if (progress.isStateSaved()) {
                        TimeUnit.SECONDS.sleep(1);
                    } else {
                        progress.dismiss();
                        progress = null;
                        break;
                    }
                }
            } catch (Exception ex) {
                if (!activity.isFinishing() && progress != null && !progress.isStateSaved()) {
                    progress.dismiss();
                    progress = null;
                }
                Log.e(LOG_TAG, "BackupFragment close progress fault " + ex.getMessage() + " " + ex.getCause());
            }
        });
    }

    void showToast(final String text) {
        Activity activity = getActivity();

        if (activity == null) {
            return;
        }

        activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        FileOperations.deleteOnFileOperationCompleteListener(this);

        progress = null;
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag) {
        if (currentFileOperation == moveBinaryFile && tag.equals("InvizibleBackup.zip")) {
            closePleaseWaitDialog();
            if (fileOperationResult) {
                showToast(getString(R.string.backupSaved));
            } else {
                showToast(getString(R.string.wrong));
            }

        } else if (currentFileOperation == deleteFile && tag.equals("sharedPreferences")) {
            closePleaseWaitDialog();
            if (fileOperationResult) {
                showToast(getString(R.string.backupRestored));
            } else {
                showToast(getString(R.string.wrong));
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void onResultActivity(Context context, int requestCode, int resultCode, @Nullable Intent data) {

        try {

            final ContentResolver contentResolver = context.getContentResolver();

            if (resultCode != RESULT_OK) {
                throw new IllegalStateException("result " + resultCode);
            }

            final Uri uri = data != null ? data.getData() : null;

            if (uri == null) {
                throw new IllegalStateException("missing URI?");
            }

            if (requestCode == CODE_READ) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                restoreHelper.restoreAll(contentResolver.openInputStream(uri));

                openPleaseWaitDialog();
            } else if (requestCode == CODE_WRITE) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                backupHelper.copyData(contentResolver.openOutputStream(uri));

                closePleaseWaitDialog();
                showToast(getString(R.string.backupSaved));
            }
        } catch (Exception e) {
            closePleaseWaitDialog();
            showToast(getString(R.string.wrong));
            Log.e(LOG_TAG, "BackupFragment onResultActivity exception " + e.getMessage() +" " + e.getCause());
        }
    }

    private void hideSelectionEditTextIfRequired(Activity activity) {
        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            boolean logsDirAccessible = false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                logsDirAccessible = Utils.INSTANCE.isLogsDirAccessible();
            }
            if (activity != null && !activity.isFinishing() && !logsDirAccessible && cardRules != null) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && cardRules != null && llFragmentBackup != null) {
                        cardRules.setVisibility(View.GONE);
                        llFragmentBackup.setPadding(0, pan.alexander.tordnscrypt.utils.Utils.INSTANCE.dips2pixels(10, activity), 0, 0);
                        pathBackup = cacheDir;
                    }
                });
            }
        });
    }
}
