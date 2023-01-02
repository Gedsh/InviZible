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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.backup;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitProgressDialog;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.filemanager.ExternalStoragePermissions;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnBinaryFileOperationsCompleteListener;

import static android.app.Activity.RESULT_OK;
import static pan.alexander.tordnscrypt.proxy.ProxyFragmentKt.CLEARNET_APPS_FOR_PROXY;
import static pan.alexander.tordnscrypt.settings.tor_apps.UnlockTorAppsFragment.CLEARNET_APPS;
import static pan.alexander.tordnscrypt.settings.tor_apps.UnlockTorAppsFragment.UNLOCK_APPS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_GSM_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_LAN_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_ROAMING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_VPN;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_WIFI_PREF;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.deleteFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.moveBinaryFile;

import javax.inject.Inject;


public class BackupFragment extends Fragment implements View.OnClickListener,
        DialogInterface.OnClickListener,
        OnBinaryFileOperationsCompleteListener {

    @Inject
    public Lazy<PathVars> pathVarsLazy;
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public CachedExecutor cachedExecutor;

    final static Set<String> TAGS_TO_CONVERT = new HashSet<>(Arrays.asList(
            APPS_ALLOW_LAN_PREF,
            APPS_ALLOW_WIFI_PREF,
            APPS_ALLOW_GSM_PREF,
            APPS_ALLOW_ROAMING,
            APPS_ALLOW_VPN,
            CLEARNET_APPS_FOR_PROXY,
            UNLOCK_APPS,
            CLEARNET_APPS
    ));

    final static int CODE_READ = 10;
    final static int CODE_WRITE = 20;

    private LinearLayoutCompat llCardBackup;
    private EditText etFilePath;
    private String pathBackup;
    private String cacheDir;
    private String appDataDir;
    private DialogFragment progress;

    private ResetHelper resetHelper;
    private BackupHelper backupHelper;
    private RestoreHelper restoreHelper;

    private boolean logsDirAccessible;

    public BackupFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        FileManager.setOnFileOperationCompleteListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_backup, container, false);

        Button btnResetSettings = view.findViewById(R.id.btnResetSettings);
        btnResetSettings.setOnClickListener(this);

        Button btnRestoreBackup = view.findViewById(R.id.btnRestoreBackup);
        btnRestoreBackup.setOnClickListener(this);

        Button btnSaveBackup = view.findViewById(R.id.btnSaveBackup);
        btnSaveBackup.setOnClickListener(this);
        btnSaveBackup.requestFocus();

        llCardBackup = view.findViewById(R.id.llCardBackup);

        hideSelectionEditTextIfRequired(getActivity());

        etFilePath = view.findViewById(R.id.etPathBackup);

        PathVars pathVars = pathVarsLazy.get();
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

        if (resetHelper != null) {
            resetHelper.setActivity(activity);
        }
    }

    @Override
    public void onClick(View v) {
        Activity activity = getActivity();

        if (activity == null) {
            return;
        }

        int id = v.getId();
        if (id == R.id.btnResetSettings) {
            showAreYouSureDialog(activity, R.string.btnResetSettings, () -> resetSettings(activity));
        } else if (id == R.id.btnRestoreBackup) {
            showAreYouSureDialog(activity, R.string.btnRestoreBackup, () -> restoreBackup(activity));
        } else if (id == R.id.btnSaveBackup) {
            saveBackup(activity);
        } else if (id == R.id.etPathBackup) {
            selectBackupPath(activity);
        }

    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

    }

    private void resetSettings(Activity activity) {

        openPleaseWaitDialog();

        resetHelper = new ResetHelper(activity, this);
        resetHelper.resetSettings();
    }

    private void restoreBackup(Activity activity) {

        restoreHelper = new RestoreHelper(
                activity, appDataDir, cacheDir, pathBackup
        );

        ExternalStoragePermissions permissions = new ExternalStoragePermissions(activity);
        if (!permissions.isReadPermissions()) {
            permissions.requestReadPermissions();
            return;
        }

        if (logsDirAccessible) {

            openPleaseWaitDialog();

            restoreHelper.restoreAll(null, logsDirAccessible);
        } else {
            restoreHelper.openFileWithSAF();
        }

    }

    private void saveBackup(Activity activity) {

        if (logsDirAccessible) {
            ExternalStoragePermissions permissions = new ExternalStoragePermissions(activity);
            if (!permissions.isWritePermissions()) {
                permissions.requestReadWritePermissions();
                return;
            }
        }

        openPleaseWaitDialog();

        backupHelper = new BackupHelper(activity, appDataDir, cacheDir, pathBackup);
        backupHelper.saveAll(logsDirAccessible);
    }

    private void selectBackupPath(Activity activity) {

        if (activity == null || activity.isFinishing()) {
            return;
        }

        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = DialogConfigs.DIR_SELECT;
        properties.root = new File(Environment.getExternalStorageDirectory().getPath());
        properties.error_dir = new File(pathVarsLazy.get().getCacheDirPath(activity));
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

        cachedExecutor.submit(() -> {
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

        FileManager.deleteOnFileOperationCompleteListener(this);

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
                restoreHelper.restoreAll(contentResolver.openInputStream(uri), logsDirAccessible);

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
            Log.e(LOG_TAG, "BackupFragment onResultActivity exception " + e.getMessage() + " " + e.getCause());
        }
    }

    private void showAreYouSureDialog(Activity activity, int title, Runnable action) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle(title);
        builder.setMessage(R.string.areYouSure);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> action.run());
        builder.setNegativeButton(getText(R.string.cancel), (dialog, i) -> dialog.cancel());
        builder.show();
    }

    private void hideSelectionEditTextIfRequired(Activity activity) {
        cachedExecutor.submit(() -> {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                logsDirAccessible = Utils.INSTANCE.isLogsDirAccessible();
            }

            if (activity != null && !activity.isFinishing() && !logsDirAccessible && llCardBackup != null) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && llCardBackup != null) {
                        llCardBackup.setVisibility(View.GONE);
                        pathBackup = cacheDir;
                    }
                });
            }
        });
    }
}
