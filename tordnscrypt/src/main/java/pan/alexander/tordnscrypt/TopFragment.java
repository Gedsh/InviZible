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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dagger.Lazy;
import kotlinx.coroutines.Job;
import pan.alexander.tordnscrypt.dialogs.AgreementDialog;
import pan.alexander.tordnscrypt.dialogs.AskAccelerateDevelop;
import pan.alexander.tordnscrypt.dialogs.AskRestoreDefaultsDialog;
import pan.alexander.tordnscrypt.dialogs.NewUpdateDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.dialogs.SendCrashReport;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.CheckUpdatesDialog;
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.installer.Installer;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesStarterHelper;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ModulesVersions;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.update.UpdateCheck;
import pan.alexander.tordnscrypt.update.UpdateService;
import pan.alexander.tordnscrypt.utils.enums.ModuleName;
import pan.alexander.tordnscrypt.dialogs.Registration;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.notification.NotificationPermissionDialog;
import pan.alexander.tordnscrypt.utils.notification.NotificationPermissionManager;

import static pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated;
import static pan.alexander.tordnscrypt.dialogs.AskRestoreDefaultsDialog.MODULE_NAME_ARG;
import static pan.alexander.tordnscrypt.utils.Utils.shortenTooLongConjureLog;
import static pan.alexander.tordnscrypt.utils.Utils.shortenTooLongSnowflakeLog;
import static pan.alexander.tordnscrypt.utils.Utils.shortenTooLongWebTunnelLog;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.AGREEMENT_ACCEPTED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CRASH_REPORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_SERVERS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIX_TTL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ITPD_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ITPD_TETHERING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.NOTIFICATIONS_REQUEST_BLOCKED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.OPERATION_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.RUN_MODULES_WITH_ROOT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.TOP_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;

import javax.inject.Inject;


public class TopFragment extends Fragment
        implements NotificationPermissionDialog.NotificationPermissionDialogListener,
        AgreementDialog.OnAgreementAcceptedListener {

    public volatile static String DNSCryptVersion = "";
    public volatile static String TorVersion = "";
    public volatile static String ITPDVersion = "";

    static String verSU = "";
    static String verBB = "";

    public static boolean debug = false;
    public static final String TOP_BROADCAST = "pan.alexander.tordnscrypt.action.TOP_BROADCAST";


    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private boolean rootIsAvailable = false;
    private boolean rootIsAvailableSaved = false;

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CoroutineExecutor executor;
    @Inject
    public Lazy<ModulesVersions> modulesVersions;
    @Inject
    public Lazy<ConnectionCheckerInteractor> connectionChecker;
    @Inject
    public Lazy<NotificationPermissionManager> notificationPermissionManager;
    @Inject
    public ViewModelProvider.Factory viewModelFactory;
    @Inject
    public Lazy<Verifier> verifierLazy;
    @Inject
    public Lazy<AskRestoreDefaultsDialog> askRestoreDefaultsDialog;

    private TopFragmentViewModel viewModel;

    private OperationMode mode = UNDEFINED;
    private boolean runModulesWithRoot = false;

    public CheckUpdatesDialog checkUpdatesDialog;
    volatile Job updateCheckTask;

    private ScheduledFuture<?> scheduledFuture;
    private BroadcastReceiver br;
    private OnActivityChangeListener onActivityChangeListener;

    private volatile Handler handler;

    private static volatile ScheduledExecutorService timer;

    public static float logsTextSize = 0f;

    public static volatile boolean initTasksRequired = true;

    public interface OnActivityChangeListener {
        void onActivityChange(MainActivity mainActivity);
    }

    public void setOnActivityChangeListener(OnActivityChangeListener onActivityChangeListener) {
        this.onActivityChangeListener = onActivityChangeListener;
    }

    private void removeOnActivityChangeListener() {
        onActivityChangeListener = null;
    }


    public TopFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);

        super.onCreate(savedInstanceState);

        //noinspection deprecation
        setRetainInstance(true);

        viewModel = new ViewModelProvider(this, viewModelFactory).get(TopFragmentViewModel.class);

    }

    @Override
    public void onStart() {
        super.onStart();

        FragmentActivity activity = getActivity();

        if (activity != null) {
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(activity);
            PreferenceRepository preferences = preferenceRepository.get();
            rootIsAvailableSaved = rootIsAvailable = preferences.getBoolPreference(ROOT_IS_AVAILABLE);
            runModulesWithRoot = shPref.getBoolean(RUN_MODULES_WITH_ROOT, false);

            modulesStatus.setFixTTL(shPref.getBoolean(FIX_TTL, false));
            modulesStatus.setTorReady(preferences.getBoolPreference(TOR_READY_PREF));
            modulesStatus.setDnsCryptReady(preferences.getBoolPreference(DNSCRYPT_READY_PREF));
            modulesStatus.setItpdReady(preferences.getBoolPreference(ITPD_READY_PREF));

            String operationMode = preferences.getStringPreference(OPERATION_MODE);

            if (!operationMode.isEmpty()) {
                mode = OperationMode.valueOf(operationMode);
                ModulesAux.switchModes(rootIsAvailable, runModulesWithRoot, mode);
            }

            if (PathVars.isModulesInstalled(preferences)) {
                checkAgreement();
            }

            logsTextSize = preferences.getFloatPreference("LogsTextSize");
        }

        if (activity != null) {
            registerReceiver(activity);
        }

        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            handler = new Handler(looper);
        }

        if (Build.VERSION.SDK_INT >= 33
                && activity != null
                && !preferenceRepository.get().getBoolPreference(NOTIFICATIONS_REQUEST_BLOCKED)
                && notificationPermissionManager.get().isNotificationPermissionRequestRequired(activity)) {
            registerNotificationsPermissionListener(activity);
        }

        if (isInitTasksRequired() || isRootCheckRequired()) {
            checkRootAvailable();
        }
    }

    @Override
    public void onResume() {

        super.onResume();

        Context context = getActivity();

        if (context != null) {

            if (onActivityChangeListener != null && context instanceof MainActivity) {
                onActivityChangeListener.onActivityChange((MainActivity) context);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        try {
            return inflater.inflate(R.layout.fragment_top, container, false);
        } catch (Exception e) {
            loge("TopFragment onCreateView", e);
            throw e;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        observeRootState();
    }

    private boolean isInitTasksRequired() {
        return initTasksRequired
                || !PathVars.isModulesInstalled(preferenceRepository.get())
                || DNSCryptVersion.isEmpty()
                || TorVersion.isEmpty()
                || ITPDVersion.isEmpty()
                || modulesStatus.getDnsCryptState() == ModuleState.UNDEFINED
                || modulesStatus.getTorState() == ModuleState.UNDEFINED
                || modulesStatus.getItpdState() == ModuleState.UNDEFINED
                || rootIsAvailable != rootIsAvailableSaved
                || mode == UNDEFINED;
    }

    private boolean isRootCheckRequired() {
        return !viewModel.getRootCheckResultSuccess();
    }

    private void checkRootAvailable() {
        viewModel.checkRootAvailable();
    }

    @Override
    public void onStop() {
        super.onStop();

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        saveLogsTextSize();

        if (!activity.isChangingConfigurations()) {
            stopInstallationTimer();

            removeOnActivityChangeListener();

            stopTimer();

            cancelRootChecker();

            cancelCheckUpdatesTask();
            dismissCheckUpdatesDialog();

            cancelHandlerTasks();

            unRegisterReceiver(activity);
        }
    }

    private void saveLogsTextSize() {
        preferenceRepository.get().setFloatPreference("LogsTextSize", logsTextSize);
    }

    private void cancelRootChecker() {
        if (!App.getInstance().isAppForeground()) {
            viewModel.cancelRootChecking();
        }
    }

    private void cancelHandlerTasks() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
    }

    private void observeRootState() {
        viewModel.getRootStateLiveData().observe(getViewLifecycleOwner(), rootState -> {

            if (rootState instanceof RootState.Undefined) {
                return;
            } else if (rootState instanceof RootState.RootAvailable) {
                String suVersion = ((RootState.RootAvailable) rootState).getSuVersion();
                List <String> suResult = ((RootState.RootAvailable) rootState).getSuResult();
                List<String> bbResult = ((RootState.RootAvailable) rootState).getBbResult();

                setSUInfo(suResult, suVersion);
                setBBinfo(bbResult);
            }

            performInitTasksBackgroundWork();
        });
    }

    @WorkerThread
    private void performInitTasksBackgroundWork() {
        executor.submit("TopFragment performInitTasksBackgroundWork", () -> {

            Activity activity = getActivity();

            if (activity == null || activity.isFinishing()) {
                return null;
            }

            Context context = activity.getApplicationContext();

            Utils.startAppExitDetectService(context);

            PreferenceRepository preferences = preferenceRepository.get();

            shortenTooLongSnowflakeLog(context, preferences, pathVars.get());
            shortenTooLongConjureLog(context, preferences, pathVars.get());
            shortenTooLongWebTunnelLog(context, preferences, pathVars.get());

            checkModulesStoppedBySystem(activity);

            checkIntegrity(activity);

            if (handler != null) {
                handler.post(this::performInitTasksMainThreadWork);
            }
            return null;
        });
    }

    private void checkModulesStoppedBySystem(Activity activity) {
        if (handler != null) {
            handler.postDelayed(() -> {

                if (activity.isFinishing()) {
                    return;
                }

                Context context = activity.getApplicationContext();

                if (!runModulesWithRoot
                        && haveModulesSavedStateRunning()
                        && !isModulesStarterServiceRunning(context)) {
                    startModulesStarterServiceIfStoppedBySystem(context);
                    loge("ModulesService stopped by system!");
                }
            }, 3000);
        }
    }

    private void checkIntegrity(Activity activity) {
        try {
            Verifier verifier = verifierLazy.get();
            String appSign = verifier.getAppSignature();
            String appSignAlt = verifier.getApkSignature();
            verifier.encryptStr(TOP_BROADCAST, appSign, appSignAlt);
            String wrongSign = verifier.getWrongSign();
            if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                if (isAdded() && !isStateSaved()) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            activity, getString(R.string.verifier_error), "1112");
                    if (notificationHelper != null && handler != null) {
                        handler.post(() -> notificationHelper.show(getChildFragmentManager(), NotificationHelper.TAG_HELPER));
                    }
                }
            }

        } catch (Exception e) {
            if (isAdded()) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        activity, getString(R.string.verifier_error), "2235");
                if (notificationHelper != null && !isStateSaved() && handler != null) {
                    handler.post(() -> notificationHelper.show(getChildFragmentManager(), NotificationHelper.TAG_HELPER));
                }
            }
            loge("Top Fragment comparator fault ", e, true);
        }
    }

    @UiThread
    private void performInitTasksMainThreadWork() {

        MainActivity activity = (MainActivity) getActivity();

        if (activity == null || activity.isFinishing()) {
            return;
        }

        try {

            if (rootIsAvailable != rootIsAvailableSaved || mode == UNDEFINED) {
                ModulesAux.switchModes(rootIsAvailable, runModulesWithRoot, mode);

                activity.invalidateMenu();
            }

            if (!PathVars.isModulesInstalled(preferenceRepository.get())) {
                actionModulesNotInstalled(activity);
            } else {

                refreshModulesVersions(activity);

                stopInstallationTimer();

                if (!pathVars.get().getAppVersion().endsWith("p")
                        && !preferenceRepository.get().getStringPreference(CRASH_REPORT).isEmpty()) {
                    sendCrashReport(activity);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && !preferenceRepository.get().getBoolPreference(NOTIFICATIONS_REQUEST_BLOCKED)
                        && isAgreementAccepted()
                        && notificationPermissionManager.get().isNotificationPermissionRequestRequired(activity)) {
                    requestNotificationPermissions();
                } else {
                    showUpdateResultMessage(activity);
                    checkUpdates(activity);
                    showDonDialog(activity);
                }

                checkInternetConnectionIfRequired();
            }

            initTasksRequired = false;

        } catch (Exception e) {
            loge("RootChecker onPostExecute", e);
        }
    }

    @RequiresApi(33)
    private void registerNotificationsPermissionListener(FragmentActivity activity) {
        notificationPermissionManager.get().getNotificationPermissionLauncher(activity);

        NotificationPermissionDialog previousDialog =
                (NotificationPermissionDialog) activity.getSupportFragmentManager()
                        .findFragmentByTag("NotificationsPermission");

        NotificationPermissionManager.OnPermissionResultListener listener =
                new NotificationPermissionManager.OnPermissionResultListener() {
                    @Override
                    public void onAllowed() {
                        preferenceRepository.get().setBoolPreference(NOTIFICATIONS_REQUEST_BLOCKED, false);
                        logi("Notifications are allowed");
                    }

                    @Override
                    public void onShowRationale() {
                        if (previousDialog == null && isAdded() && !isStateSaved()) {
                            NotificationPermissionDialog dialog = new NotificationPermissionDialog();
                            dialog.show(getParentFragmentManager(), "NotificationsPermission");
                        }
                    }

                    @Override
                    public void onDenied() {
                        preferenceRepository.get().setBoolPreference(NOTIFICATIONS_REQUEST_BLOCKED, true);
                        logw("Notifications are blocked");
                    }
                };

        notificationPermissionManager.get().setOnPermissionResultListener(listener);
    }

    @RequiresApi(33)
    private void requestNotificationPermissions() {
        if (handler == null) {
            return;
        }

        handler.postDelayed(() -> {
            FragmentActivity activity = getActivity();
            if (activity == null)
                return;

            if (isAdded() && !isStateSaved()) {
                notificationPermissionManager.get().requestNotificationPermission(activity);
            }
        }, 1000);
    }

    @Override
    public void notificationPermissionDialogOkPressed() {
        ActivityResultLauncher<String> launcher = notificationPermissionManager.get().getLauncher();
        if (isAdded() && !isStateSaved() && launcher != null) {
            notificationPermissionManager.get().launchNotificationPermissionSystemDialog(launcher);
        }
    }

    @Override
    public void notificationPermissionDialogDoNotShowPressed() {
        NotificationPermissionManager.OnPermissionResultListener listener =
        notificationPermissionManager.get().getOnPermissionResultListener();
        if (listener != null) {
            listener.onDenied();
        }
    }

    private void showDonDialog(Activity activity) {

        if (!initTasksRequired
                || activity == null
                || activity.isFinishing()
                || isStateSaved()
                || !isAgreementAccepted()) {
            return;
        }

        if (pathVars.get().getAppVersion().endsWith("e")) {
            if (handler != null) {
                handler.postDelayed(() -> {
                    if (isAdded() && !isStateSaved()) {
                        Registration registration = new Registration(activity);
                        registration.showDonateDialog();
                    }
                }, 5000);
            }
        } else if (pathVars.get().getAppVersion().endsWith("p") && isAdded()) {

            if (handler != null) {
                handler.postDelayed(() -> {
                    if (accelerated) {
                        return;
                    }
                    DialogFragment accelerateDevelop = AskAccelerateDevelop.getInstance();
                    accelerateDevelop.setCancelable(false);
                    if (isAdded() && !isStateSaved()) {
                        accelerateDevelop.show(getParentFragmentManager(), "accelerateDevelop");
                    }
                }, 5000);
            }

        }
    }

    private void refreshModulesVersions(Context context) {
        if (modulesStatus.isUseModulesWithRoot() && modulesStatus.getMode() == ROOT_MODE) {
            Intent intent = new Intent(TOP_BROADCAST);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            logi("TopFragment Send TOP_BROADCAST");
        } else {
            modulesVersions.get().refreshVersions(context);
        }
    }

    private void actionModulesNotInstalled(Context context) {

        PreferenceManager.setDefaultValues(context, R.xml.preferences_common, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_dnscrypt, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_fast, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_tor, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_i2pd, true);

        //For core update purposes
        final PreferenceRepository preferences = preferenceRepository.get();
        preferences.setStringPreference("DNSCryptVersion", DNSCryptVersion);
        preferences.setStringPreference("TorVersion", TorVersion);
        preferences.setStringPreference("ITPDVersion", ITPDVersion);
        preferences.setStringPreference(DNSCRYPT_SERVERS, "");
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sPref.edit();
        editor.putBoolean(TOR_TETHERING, false);
        editor.putBoolean(ITPD_TETHERING, false);
        editor.apply();

        startInstallation();
    }

    private void setSUInfo(List<String> fSuResult, String fSuVersion) {
        try {
            final PreferenceRepository preferences = preferenceRepository.get();

            if (fSuResult != null && !fSuResult.isEmpty()
                    && fSuResult.toString().toLowerCase().contains("uid=0")
                    && fSuResult.toString().toLowerCase().contains("gid=0")) {

                rootIsAvailable = true;

                preferences.setBoolPreference(ROOT_IS_AVAILABLE, true);

                if (fSuVersion != null && !fSuVersion.isEmpty()) {
                    verSU = "Root is available." + (char) 10 +
                            "Super User Version: " + fSuVersion + (char) 10 +
                            fSuResult.get(0);
                } else {
                    verSU = "Root is available." + (char) 10 +
                            "Super User Version: Unknown" +
                            fSuResult.get(0);
                }
                logi(verSU);
            } else {
                rootIsAvailable = false;
                preferences.setBoolPreference(ROOT_IS_AVAILABLE, false);
            }
        } catch (Exception e) {
            loge("TopFragment setSUInfo", e);
        }
    }

    private void setBBinfo(List<String> fBbResult) {

        try {
            final PreferenceRepository preferences = preferenceRepository.get();

            if (fBbResult != null && !fBbResult.isEmpty()) {
                verBB = fBbResult.get(0);
            } else {
                preferences.setBoolPreference("bbOK", false);
                return;
            }

            if (verBB.toLowerCase().contains("not found")) {
                preferences.setBoolPreference("bbOK", false);
            } else {
                preferences.setBoolPreference("bbOK", true);

                logi("BusyBox is available " + verBB);
            }
        } catch (Exception e) {
            loge("TopFragment setBBinfo", e);
        }
    }

    private void startInstallation() {

        stopInstallationTimer();

        if (timer == null || timer.isShutdown()) {
            initTimer();
        }

        scheduledFuture = timer.scheduleWithFixedDelay(new Runnable() {
            int loop = 0;

            @Override
            public void run() {

                logi("TopFragment Timer loop = " + loop);

                if (++loop > 15) {
                    stopInstallationTimer();
                    logw("TopFragment Timer cancel, loop > 15");
                }

                Activity activity = getActivity();

                if (activity instanceof MainActivity) {
                    Installer installer = new Installer(activity);
                    installer.installModules();
                    logi("TopFragment Timer startRefreshModulesStatus Modules Installation");
                    stopInstallationTimer();
                }
            }
        }, 3, 1, TimeUnit.SECONDS);
    }

    private void stopInstallationTimer() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }

    private void sendCrashReport(Activity activity) {

        if (activity == null || activity.isFinishing()) {
            return;
        }

        String crash = preferenceRepository.get().getStringPreference(CRASH_REPORT);
        if (!crash.isEmpty()) {
            SendCrashReport crashReport = SendCrashReport.Companion.getCrashReportDialog(activity);
            if (crashReport != null && isAdded() && !isStateSaved()) {
                crashReport.show(getParentFragmentManager(), "SendCrashReport");
            }
        }
    }

    public void checkUpdates(Context context) {

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(context);
        PreferenceRepository preferences = preferenceRepository.get();

        if (!preferences.getStringPreference("RequiredAppUpdateForQ").isEmpty()) {
            Intent intent = new Intent(context, UpdateService.class);
            intent.setAction(UpdateService.INSTALLATION_REQUEST_ACTION);
            context.startService(intent);
            return;
        }

        boolean autoUpdate = spref.getBoolean("pref_fast_auto_update", true)
                && !pathVars.get().getAppVersion().startsWith("l")
                && !pathVars.get().getAppVersion().endsWith("p")
                && !pathVars.get().getAppVersion().startsWith("f");

        if (autoUpdate) {
            boolean throughTorUpdate = spref.getBoolean("pref_fast through_tor_update", false);
            boolean torRunning = modulesStatus.getTorState() == RUNNING;
            boolean torReady = modulesStatus.isTorReady();
            String lastUpdateResult = preferences.getStringPreference("LastUpdateResult");
            if (!throughTorUpdate || (torRunning && torReady)) {
                long updateTimeCurrent = System.currentTimeMillis();
                String updateTimeLastStr = preferences.getStringPreference("updateTimeLast");
                if (!updateTimeLastStr.isEmpty()) {
                    long updateTimeLast = Long.parseLong(updateTimeLastStr);
                    final int UPDATES_CHECK_INTERVAL_HOURS = 24;
                    int interval = 1000 * 60 * 60 * UPDATES_CHECK_INTERVAL_HOURS;
                    if ((updateTimeCurrent - updateTimeLast > interval)
                            || (lastUpdateResult.isEmpty() && ((updateTimeCurrent - updateTimeLast) > 300000))
                            || lastUpdateResult.equals(getString(R.string.update_check_warning_menu)))
                        checkNewVer(context, false);
                } else {
                    checkNewVer(context, false);
                }
            }
        }
    }

    public void checkNewVer(Context context, boolean showProgressDialog) {

        if (pathVars.get().getAppVersion().endsWith("p")
                || pathVars.get().getAppVersion().startsWith("f")) {
            return;
        }

        if (context == null || updateCheckTask != null || isStateSaved()) {
            return;
        }

        PreferenceRepository preferences = preferenceRepository.get();
        preferences.setStringPreference("LastUpdateResult", "");
        preferences.setStringPreference("updateTimeLast", String.valueOf(System.currentTimeMillis()));

        try {
            UpdateCheck updateCheck = new UpdateCheck(this);
            updateCheckTask = updateCheck.requestUpdateData("https://invizible.net");
            if (showProgressDialog && !isStateSaved()) {
                checkUpdatesDialog = new CheckUpdatesDialog();
                checkUpdatesDialog.setCheckUpdatesTask(updateCheckTask);
                checkUpdatesDialog.show(getParentFragmentManager(), "checkUpdatesDialog");
            }
        } catch (Exception e) {
            Activity activity = getActivity();
            if (activity instanceof MainActivity) {
                if (checkUpdatesDialog != null && checkUpdatesDialog.isAdded() && !isStateSaved()) {
                    showUpdateMessage(activity, getString(R.string.update_fault));
                }
            }
            preferences.setStringPreference("LastUpdateResult", getString(R.string.update_fault));
            loge("TopFragment Failed to requestUpdate()", e);
        }
    }

    public void downloadUpdate(String fileName, String updateStr, String message, String hash) {

        if (handler == null) {
            return;
        }

        handler.post(() -> {
            Context context = getActivity();
            if (context == null)
                return;

            cancelCheckUpdatesTask();
            dismissCheckUpdatesDialog();

            preferenceRepository.get().setStringPreference("LastUpdateResult", context.getString(R.string.update_found));

            if (isAdded() && !isStateSaved()) {
                DialogFragment newUpdateDialogFragment = NewUpdateDialogFragment.newInstance(message, updateStr, fileName, hash);
                newUpdateDialogFragment.setCancelable(false);
                newUpdateDialogFragment.show(getParentFragmentManager(), NewUpdateDialogFragment.TAG_NOT_FRAG);
            }
        });
    }

    private void dismissCheckUpdatesDialog() {
        if (checkUpdatesDialog != null && checkUpdatesDialog.isAdded()) {
            checkUpdatesDialog.dismiss();
            checkUpdatesDialog = null;
        }
    }

    private void cancelCheckUpdatesTask() {
        if (updateCheckTask != null && !updateCheckTask.isCompleted()) {
            updateCheckTask.cancel(new CancellationException());
        }
        updateCheckTask = null;
    }

    public void showUpdateResultMessage(Activity activity) {

        if (pathVars.get().getAppVersion().equals("gp")
                || pathVars.get().getAppVersion().equals("fd")) {
            return;
        }

        PreferenceRepository preferences = preferenceRepository.get();
        String updateResultMessage = preferences.getStringPreference("UpdateResultMessage");
        if (!updateResultMessage.isEmpty()) {
            showUpdateMessage(activity, updateResultMessage);

            preferences.setStringPreference("UpdateResultMessage", "");
        }
    }

    public void showUpdateMessage(Activity activity, final String message) {
        if (activity.isFinishing() || handler == null || isStateSaved()) {
            return;
        }

        cancelCheckUpdatesTask();

        handler.post(this::dismissCheckUpdatesDialog);

        handler.postDelayed(() -> {
            if (!activity.isFinishing() && !isStateSaved()) {
                DialogFragment commandResult = NotificationDialogFragment.newInstance(message);
                commandResult.show(getParentFragmentManager(), "NotificationDialogFragment");
            }
        }, 500);

    }

    private static void startModulesStarterServiceIfStoppedBySystem(Context context) {
        ModulesAux.recoverService(context);
    }

    private static boolean isModulesStarterServiceRunning(Context context) {
        return Utils.INSTANCE.isServiceRunning(context, ModulesService.class);
    }

    private static boolean haveModulesSavedStateRunning() {

        boolean dnsCryptRunning = ModulesAux.isDnsCryptSavedStateRunning();
        boolean torRunning = ModulesAux.isTorSavedStateRunning();
        boolean itpdRunning = ModulesAux.isITPDSavedStateRunning();

        return dnsCryptRunning || torRunning || itpdRunning;
    }

    private void receiverOnReceive(Intent intent) {

        Activity activity = getActivity();

        if (activity == null || intent.getAction() == null
                || !isBroadcastMatch(intent)
                || !isAdded() || isStateSaved()) {
            return;
        }

        if (intent.getAction().equals(UpdateService.UPDATE_RESULT)) {
            showUpdateResultMessage(activity);
            refreshModulesVersions(activity);
        } else if (intent.getAction().equals(ModulesStarterHelper.ASK_RESTORE_DEFAULTS)) {
            DialogFragment dialog = askRestoreDefaultsDialog.get();
            Bundle args = new Bundle();
            ModuleName name = (ModuleName) intent.getSerializableExtra(ModulesStarterHelper.MODULE_NAME);
            args.putSerializable(MODULE_NAME_ARG, name);
            dialog.setArguments(args);
            dialog.show(getChildFragmentManager(), "AskRestoreDefaults");
        }
    }

    private boolean isBroadcastMatch(Intent intent) {
        if (intent == null) {
            return false;
        }

        String action = intent.getAction();

        if (action == null || action.isEmpty()) {
            return false;
        }

        if (!action.equals(UpdateService.UPDATE_RESULT) && !action.equals(ModulesStarterHelper.ASK_RESTORE_DEFAULTS)) {
            return false;
        }

        return intent.getIntExtra("Mark", 0) == TOP_FRAGMENT_MARK;
    }

    private void registerReceiver(Context context) {

        if (context == null || br != null) {
            return;
        }

        br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                receiverOnReceive(intent);
            }
        };

        IntentFilter intentFilterUpdate = new IntentFilter(UpdateService.UPDATE_RESULT);
        IntentFilter intentFilterForceClose = new IntentFilter(ModulesStarterHelper.ASK_RESTORE_DEFAULTS);
        LocalBroadcastManager.getInstance(context).registerReceiver(br, intentFilterUpdate);
        LocalBroadcastManager.getInstance(context).registerReceiver(br, intentFilterForceClose);
    }

    private void unRegisterReceiver(Context context) {
        try {
            if (br != null && context != null) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(br);
                br = null;
            }
        } catch (Exception ignored) {
        }

    }

    private void checkAgreement() {
        if (!isAgreementAccepted()) {
            DialogFragment dialog = AgreementDialog.newInstance();
            dialog.setCancelable(false);
            if (isAdded() && !isStateSaved()) {
                dialog.show(getParentFragmentManager(), "AgreementDialog");
            }
        }
    }

    @Override
    public void onAgreementAccepted() {

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !preferenceRepository.get().getBoolPreference(NOTIFICATIONS_REQUEST_BLOCKED)
                && notificationPermissionManager.get().isNotificationPermissionRequestRequired(activity)) {
            requestNotificationPermissions();
        }
    }

    private boolean isAgreementAccepted() {
        return preferenceRepository.get().getBoolPreference(AGREEMENT_ACCEPTED);
    }

    private static void initTimer() {
        if (timer == null || timer.isShutdown()) {
            timer = Executors.newScheduledThreadPool(0);
        }
    }

    private void stopTimer() {
        if (timer != null && !timer.isShutdown()) {
            timer.shutdownNow();
            timer = null;
        }
    }

    private void checkInternetConnectionIfRequired() {
        if (modulesStatus.getDnsCryptState() == RUNNING || modulesStatus.getTorState() == RUNNING) {
            ConnectionCheckerInteractor checker = connectionChecker.get();
            if (!checker.getInternetConnectionResult()) {
                checker.checkInternetConnection();
            }
        }
    }

}
