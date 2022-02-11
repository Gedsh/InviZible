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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import dagger.Lazy;
import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.dialogs.AgreementDialog;
import pan.alexander.tordnscrypt.dialogs.AskAccelerateDevelop;
import pan.alexander.tordnscrypt.dialogs.AskForceClose;
import pan.alexander.tordnscrypt.dialogs.NewUpdateDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.dialogs.SendCrashReport;
import pan.alexander.tordnscrypt.dialogs.UpdateModulesDialogFragment;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.CheckUpdatesDialog;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.RootCheckingProgressDialog;
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
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.dialogs.Registration;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

import static pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated;
import static pan.alexander.tordnscrypt.utils.Utils.shortenTooLongSnowflakeLog;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIX_TTL;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ITPD_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.OPERATION_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.RUN_MODULES_WITH_ROOT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.TOP_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;

import javax.inject.Inject;


public class TopFragment extends Fragment {

    public static String DNSCryptVersion = "2.0.36";
    public static String TorVersion = "4.2.5";
    public static String ITPDVersion = "2.29.0";

    public static String appProcVersion = "armv7a";
    public static String appVersion = "lite";

    static String verSU = "";
    static String verBB = "";

    public static boolean debug = false;
    public static String TOP_BROADCAST = "pan.alexander.tordnscrypt.action.TOP_BROADCAST";
    public static String wrongSign;
    public static String appSign;


    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    private RootChecker rootChecker;
    private AlertDialog rootCheckingDialog;
    private boolean rootIsAvailable = false;
    private boolean rootIsAvailableSaved = false;
    private static String suVersion = "";
    private static List<String> suResult = null;
    private static List<String> bbResult = null;

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CachedExecutor cachedExecutor;
    @Inject
    public Lazy<ModulesVersions> modulesVersions;
    @Inject
    public Lazy<ConnectionCheckerInteractor> connectionChecker;

    private OperationMode mode = UNDEFINED;
    private boolean runModulesWithRoot = false;

    public CheckUpdatesDialog checkUpdatesDialog;
    Future<?> updateCheckTask;

    private ScheduledFuture<?> scheduledFuture;
    private BroadcastReceiver br;
    private OnActivityChangeListener onActivityChangeListener;

    private Handler handler;

    private static volatile ScheduledExecutorService timer;

    public static float logsTextSize = 0f;

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

        appVersion = getString(R.string.appVersion);
        appProcVersion = getString(R.string.appProcVersion);

        Context context = getActivity();

        if (context != null) {
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
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

            if (PathVars.isModulesInstalled(preferences) && appVersion.endsWith("p")) {
                checkAgreement(context);
            }

            logsTextSize = preferences.getFloatPreference("LogsTextSize");
        }

        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            handler = new Handler(looper);
        }

        rootChecker = new RootChecker(this);
        rootChecker.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onStart() {
        super.onStart();

        Context context = getActivity();
        if (context != null) {
            registerReceiver(context);
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

        if (savedInstanceState != null) {
            startRootCheckerTasksIfStateLeavesUndefined();
        }

        return inflater.inflate(R.layout.fragment_top, container, false);
    }

    private void startRootCheckerTasksIfStateLeavesUndefined() {
        if (modulesStatus.getDnsCryptState() == ModuleState.UNDEFINED
                || modulesStatus.getTorState() == ModuleState.UNDEFINED
                || modulesStatus.getItpdState() == ModuleState.UNDEFINED) {
            //Return if the task is still executing
            if (rootChecker != null) {
                return;
            }

            rootChecker = new RootChecker(this);
            rootChecker.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        saveLogsTextSize();

        unRegisterReceiver(activity);

        closePleaseWaitDialog();

        if (!activity.isChangingConfigurations()) {
            stopInstallationTimer();

            removeOnActivityChangeListener();

            stopTimer();

            cancelRootChecker();

            cancelCheckUpdatesTask();
            dismissCheckUpdatesDialog();

            cancelHandlerTasks();
        }
    }

    private void saveLogsTextSize() {
        preferenceRepository.get().setFloatPreference("LogsTextSize", logsTextSize);
    }

    private void cancelRootChecker() {
        if (rootChecker != null) {

            if (!rootChecker.isCancelled()) {
                rootChecker.cancel(true);
            }

            rootChecker.topFragmentWeakReference.clear();
            rootChecker.topFragmentWeakReference = null;
            rootChecker = null;
        }
    }

    private void cancelHandlerTasks() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
    }

    //Check if root available
    @SuppressWarnings("deprecation")
    private static class RootChecker extends AsyncTask<Void, Void, Void> {

        private WeakReference<TopFragment> topFragmentWeakReference;
        private boolean suAvailable = false;

        RootChecker(TopFragment topFragment) {
            this.topFragmentWeakReference = new WeakReference<>(topFragment);
        }

        @Override
        protected void onPreExecute() {
            if (topFragmentWeakReference == null || topFragmentWeakReference.get() == null) {
                return;
            }

            Activity activity = topFragmentWeakReference.get().getActivity();

            if (activity != null && !activity.isFinishing()) {
                topFragmentWeakReference.get().openPleaseWaitDialog(activity);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        protected Void doInBackground(Void... params) {

            try {
                suAvailable = Shell.SU.available();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Top Fragment doInBackground suAvailable Exception " + e.getMessage() + " " + e.getCause());
            }

            if (suAvailable && suVersion.isEmpty()) {
                try {
                    suVersion = Shell.SU.version(false);
                    suResult = Shell.SU.run("id");
                    bbResult = Shell.SU.run("busybox | head -1");
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Top Fragment doInBackground suParam Exception " + e.getMessage() + " " + e.getCause());
                }
            }

            if (topFragmentWeakReference == null || topFragmentWeakReference.get() == null) {
                return null;
            }

            TopFragment topFragment = topFragmentWeakReference.get();

            Activity activity = topFragment.getActivity();

            if (activity == null || activity.isFinishing()) {
                return null;
            }

            Context context = activity.getApplicationContext();
            Utils.startAppExitDetectService(context);

            PreferenceRepository preferences = topFragment.preferenceRepository.get();

            shortenTooLongSnowflakeLog(context, preferences, topFragment.pathVars.get());

            if (topFragment.handler != null) {
                topFragment.handler.postDelayed(() -> {

                    if (activity.isFinishing()) {
                        return;
                    }

                    if (!topFragment.runModulesWithRoot
                            && haveModulesSavedStateRunning()
                            && !isModulesStarterServiceRunning(context)) {
                        startModulesStarterServiceIfStoppedBySystem(context);
                        Log.e(LOG_TAG, "ModulesService stopped by system!");
                    }
                }, 3000);
            }

            try {
                Verifier verifier = new Verifier(activity);
                appSign = verifier.getApkSignatureZip();
                String appSignAlt = verifier.getApkSignature();
                verifier.encryptStr(TOP_BROADCAST, appSign, appSignAlt);
                wrongSign = topFragment.getString(R.string.encoded).trim();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    if (topFragment.isAdded() && !topFragment.isStateSaved()) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                activity, topFragment.getString(R.string.verifier_error), "1112");
                        if (notificationHelper != null) {
                            notificationHelper.show(topFragment.getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                }

            } catch (Exception e) {
                if (topFragment.isAdded()) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            activity, topFragment.getString(R.string.verifier_error), "2235");
                    if (notificationHelper != null && !topFragment.isStateSaved()) {
                        notificationHelper.show(topFragment.getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }
                Log.e(LOG_TAG, "Top Fragment comparator fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            if (topFragmentWeakReference == null || topFragmentWeakReference.get() == null) {
                return;
            }

            TopFragment topFragment = topFragmentWeakReference.get();

            MainActivity activity = (MainActivity) topFragment.getActivity();

            if (activity == null || activity.isFinishing()) {
                return;
            }

            topFragment.closePleaseWaitDialog();

            try {

                topFragment.setSUInfo(suResult, suVersion);
                topFragment.setBBinfo(bbResult);

                if (topFragment.rootIsAvailable != topFragment.rootIsAvailableSaved || topFragment.mode == UNDEFINED) {
                    ModulesAux.switchModes(topFragment.rootIsAvailable, topFragment.runModulesWithRoot, topFragment.mode);

                    activity.invalidateMenu();
                }

                if (!PathVars.isModulesInstalled(topFragment.preferenceRepository.get())) {
                    topFragment.actionModulesNotInstalled(activity);
                } else {

                    //Currently not used
                    //if (topFragment.coreUpdateReady(activity)) {
                    //    return;
                    //}

                    topFragment.refreshModulesVersions(activity);

                    topFragment.stopInstallationTimer();

                    if (topFragment.checkCrashReport(activity)) {
                        return;
                    }

                    ////////////////////Show message about previous update attempt///////////////////////
                    topFragment.showUpdateResultMessage(activity);

                    ////////////////////////////CHECK UPDATES///////////////////////////////////////////
                    topFragment.checkUpdates(activity);

                    /////////////////////////////DONATION////////////////////////////////////////////
                    topFragment.showDonDialog(activity);

                    topFragment.checkInternetConnectionIfRequired();
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "RootChecker onPostExecute " + e.getMessage() + " " + e.getCause());
            }
        }
    }

    private void showDonDialog(Activity activity) {

        if (activity == null || activity.isFinishing() || isStateSaved()) {
            return;
        }

        if (appVersion.endsWith("e")) {
            if (handler != null) {
                handler.postDelayed(() -> {
                    if (isAdded() && !isStateSaved()) {
                        Registration registration = new Registration(activity);
                        registration.showDonateDialog();
                    }
                }, 5000);
            }
        } else if (appVersion.endsWith("p") && isAdded() && !accelerated) {

            if (!preferenceRepository.get().getBoolPreference("Agreement")) {
                return;
            }

            if (handler != null) {
                handler.postDelayed(() -> {
                    DialogFragment accelerateDevelop = AskAccelerateDevelop.getInstance();
                    if (isAdded() && !isStateSaved() && !accelerated) {
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
            Log.i(LOG_TAG, "TopFragment Send TOP_BROADCAST");
        } else {
            modulesVersions.get().refreshVersions(context);
        }
    }

    private boolean coreUpdateReady(Context context) {

        if (context == null) {
            return false;
        }

        final PreferenceRepository preferences = preferenceRepository.get();

        String currentDNSCryptVersionStr = preferences.getStringPreference("DNSCryptVersion");
        String currentTorVersionStr = preferences.getStringPreference("TorVersion");
        String currentITPDVersionStr = preferences.getStringPreference("ITPDVersion");
        if (!(currentDNSCryptVersionStr.isEmpty() && currentTorVersionStr.isEmpty() && currentITPDVersionStr.isEmpty())) {
            int currentDNSCryptVersion = Integer.parseInt(currentDNSCryptVersionStr.replaceAll("\\D+", ""));
            int currentTorVersion = Integer.parseInt(currentTorVersionStr.replaceAll("\\D+", ""));
            int currentITPDVersion = Integer.parseInt(currentITPDVersionStr.replaceAll("\\D+", ""));

            if (((currentDNSCryptVersion < Integer.parseInt(DNSCryptVersion.replaceAll("\\D+", ""))
                    || currentTorVersion < Integer.parseInt(TorVersion.replaceAll("\\D+", ""))
                    || currentITPDVersion < Integer.parseInt(ITPDVersion.replaceAll("\\D+", "")))
                    && !preferences.getBoolPreference("UpdateNotAllowed"))) {
                if (isAdded() && !isStateSaved()) {
                    DialogFragment updateCore = UpdateModulesDialogFragment.getInstance();
                    updateCore.show(getParentFragmentManager(), "UpdateModulesDialogFragment");
                }
                return true;
            }
        }

        return false;
    }

    private void actionModulesNotInstalled(Context context) {

        PreferenceManager.setDefaultValues(context, R.xml.preferences_common, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_dnscrypt, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_dnscrypt_servers, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_fast, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_tor, true);
        PreferenceManager.setDefaultValues(context, R.xml.preferences_i2pd, true);

        //For core update purposes
        final PreferenceRepository preferences = preferenceRepository.get();
        preferences.setStringPreference("DNSCryptVersion", DNSCryptVersion);
        preferences.setStringPreference("TorVersion", TorVersion);
        preferences.setStringPreference("ITPDVersion", ITPDVersion);
        preferences.setStringPreference("DNSCrypt Servers", "");
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sPref.edit();
        editor.putBoolean(TOR_TETHERING, false);
        editor.putBoolean("pref_common_itpd_tethering", false);
        editor.apply();

        startInstallation();
    }

    private void setSUInfo(List<String> fSuResult, String fSuVersion) {

        final PreferenceRepository preferences = preferenceRepository.get();

        if (fSuResult != null && fSuResult.size() != 0
                && fSuResult.toString().toLowerCase().contains("uid=0")
                && fSuResult.toString().toLowerCase().contains("gid=0")) {

            rootIsAvailable = true;

            preferences.setBoolPreference(ROOT_IS_AVAILABLE, true);

            if (fSuVersion != null && fSuVersion.length() != 0) {
                verSU = "Root is available." + (char) 10 +
                        "Super User Version: " + fSuVersion + (char) 10 +
                        fSuResult.get(0);
            } else {
                verSU = "Root is available." + (char) 10 +
                        "Super User Version: Unknown" +
                        fSuResult.get(0);
            }
            Log.i(LOG_TAG, verSU);
        } else {
            rootIsAvailable = false;
            preferences.setBoolPreference(ROOT_IS_AVAILABLE, false);
        }
    }

    private void setBBinfo(List<String> fBbResult) {

        final PreferenceRepository preferences = preferenceRepository.get();

        if (fBbResult != null && fBbResult.size() != 0) {
            verBB = fBbResult.get(0);
        } else {
            preferences.setBoolPreference("bbOK", false);
            return;
        }

        if (verBB.toLowerCase().contains("not found")) {
            preferences.setBoolPreference("bbOK", false);
        } else {
            preferences.setBoolPreference("bbOK", true);

            Log.i(LOG_TAG, "BusyBox is available " + verBB);
        }
    }

    private void startInstallation() {

        stopInstallationTimer();

        if (timer == null || timer.isShutdown()) {
            initTimer();
        }

        scheduledFuture = timer.scheduleAtFixedRate(new Runnable() {
            int loop = 0;

            @Override
            public void run() {

                Log.i(LOG_TAG, "TopFragment Timer loop = " + loop);

                if (++loop > 15) {
                    stopInstallationTimer();
                    Log.w(LOG_TAG, "TopFragment Timer cancel, loop > 15");
                }

                Activity activity = getActivity();

                if (activity instanceof MainActivity) {
                    Installer installer = new Installer(activity);
                    installer.installModules();
                    Log.i(LOG_TAG, "TopFragment Timer startRefreshModulesStatus Modules Installation");
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

    private boolean checkCrashReport(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return true;
        }

        if (appVersion.endsWith("p")) {
            return false;
        }

        String crash = preferenceRepository.get().getStringPreference("CrashReport");
        if (!crash.isEmpty()) {
            SendCrashReport crashReport = SendCrashReport.Companion.getCrashReportDialog(activity);
            if (crashReport != null && isAdded() && !isStateSaved()) {
                crashReport.show(getParentFragmentManager(), "SendCrashReport");
            }
            return true;
        }

        return false;
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
                && !appVersion.startsWith("l") && !appVersion.endsWith("p") && !appVersion.startsWith("f");

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

        if (appVersion.endsWith("p") || appVersion.startsWith("f")) {
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
            updateCheckTask = updateCheck.requestUpdateData("https://invizible.net", appSign);
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
            Log.e(LOG_TAG, "TopFragment Failed to requestUpdate() " + e.getMessage() + " " + e.getCause());
        }
    }

    public void downloadUpdate(String fileName, String updateStr, String message, String hash) {
        Context context = getActivity();
        if (context == null)
            return;

        cancelCheckUpdatesTask();
        dismissCheckUpdatesDialog();

        preferenceRepository.get().setStringPreference("LastUpdateResult", context.getString(R.string.update_found));

        if (isAdded() && !isStateSaved()) {
            DialogFragment newUpdateDialogFragment = NewUpdateDialogFragment.newInstance(message, updateStr, fileName, hash);
            newUpdateDialogFragment.show(getParentFragmentManager(), NewUpdateDialogFragment.TAG_NOT_FRAG);
        }
    }

    private void dismissCheckUpdatesDialog() {
        if (checkUpdatesDialog != null && checkUpdatesDialog.isAdded()) {
            checkUpdatesDialog.dismiss();
            checkUpdatesDialog = null;
        }
    }

    private void cancelCheckUpdatesTask() {
        if (updateCheckTask != null) {
            if (!updateCheckTask.isDone()) {
                updateCheckTask.cancel(true);
            }
            updateCheckTask = null;
        }
    }

    public void showUpdateResultMessage(Activity activity) {

        if (appVersion.equals("gp") || appVersion.equals("fd")) {
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

    private void openPleaseWaitDialog(Context context) {
        AlertDialog.Builder rootCheckingDialogBuilder = RootCheckingProgressDialog.getBuilder(context);
        rootCheckingDialog = rootCheckingDialogBuilder.show();
    }

    private void closePleaseWaitDialog() {
        if (rootCheckingDialog != null) {
            rootCheckingDialog.dismiss();
            rootCheckingDialog = null;
        }
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
        } else if (intent.getAction().equals(ModulesStarterHelper.ASK_FORCE_CLOSE)) {
            DialogFragment dialogFragment = AskForceClose.getInstance(intent.getStringExtra(ModulesStarterHelper.MODULE_NAME));
            dialogFragment.show(getParentFragmentManager(), "AskForceClose");
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

        if (!action.equals(UpdateService.UPDATE_RESULT) && !action.equals(ModulesStarterHelper.ASK_FORCE_CLOSE)) {
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
        IntentFilter intentFilterForceClose = new IntentFilter(ModulesStarterHelper.ASK_FORCE_CLOSE);
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

    private void checkAgreement(Context context) {
        if (!preferenceRepository.get().getBoolPreference("Agreement")) {
            AlertDialog.Builder agreementDialogBuilder = AgreementDialog.getDialogBuilder(context);
            if (agreementDialogBuilder != null && !isStateSaved()) {
                agreementDialogBuilder.show();
            }
        }
    }

    private static void initTimer() {
        if (timer == null || timer.isShutdown()) {
            timer = Executors.newScheduledThreadPool(0);
        }
    }

    @Nullable
    public static ScheduledExecutorService getTimer() {
        return timer;
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
