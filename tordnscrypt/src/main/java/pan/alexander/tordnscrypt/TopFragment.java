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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.dialogs.AgreementDialog;
import pan.alexander.tordnscrypt.dialogs.AskAccelerateDevelop;
import pan.alexander.tordnscrypt.dialogs.AskForceClose;
import pan.alexander.tordnscrypt.dialogs.NewUpdateDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.dialogs.SendCrashReport;
import pan.alexander.tordnscrypt.dialogs.UpdateModulesDialogFragment;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.RootCheckingProgressDialog;
import pan.alexander.tordnscrypt.installer.Installer;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesStarterHelper;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ModulesVersions;
import pan.alexander.tordnscrypt.patches.Patch;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.update.UpdateCheck;
import pan.alexander.tordnscrypt.update.UpdateService;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Registration;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

import static pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesDefault;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesOwn;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;


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

    private RootChecker rootChecker;
    private AlertDialog rootCheckingDialog;
    private boolean rootIsAvailable = false;
    private boolean rootIsAvailableSaved = false;
    private static String suVersion = "";
    private static List<String> suResult = null;
    private static List<String> bbResult = null;

    private OperationMode mode = UNDEFINED;
    private boolean runModulesWithRoot = false;

    UpdateCheck updateCheck;

    private ScheduledFuture<?> scheduledFuture;
    private BroadcastReceiver br;
    private OnActivityChangeListener onActivityChangeListener;

    private Handler handler;

    private static volatile ScheduledExecutorService modulesLogsTimer;

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

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        appVersion = getString(R.string.appVersion);
        appProcVersion = getString(R.string.appProcVersion);

        Context context = getActivity();

        if (context != null) {
            logsTextSize = new PrefManager(context).getFloatPref("LogsTextSize");
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

        registerReceiver();
    }

    @Override
    public void onResume() {

        super.onResume();

        Context context = getActivity();

        if (context != null) {

            if (onActivityChangeListener != null && context instanceof MainActivity) {
                onActivityChangeListener.onActivityChange((MainActivity) context);
            }

            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
            rootIsAvailableSaved = rootIsAvailable = new PrefManager(context).getBoolPref("rootIsAvailable");
            runModulesWithRoot = shPref.getBoolean("swUseModulesRoot", false);

            ModulesStatus.getInstance().setFixTTL(shPref.getBoolean("pref_common_fix_ttl", false));

            String operationMode = new PrefManager(context).getStrPref("OPERATION_MODE");

            if (!operationMode.isEmpty()) {
                mode = OperationMode.valueOf(operationMode);
                ModulesAux.switchModes(context, rootIsAvailable, runModulesWithRoot, mode);
            }

            if (PathVars.isModulesInstalled(context) && appVersion.endsWith("p")) {
                checkAgreement(context);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_top, container, false);
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();

        if (context != null) {
            new PrefManager(context).setFloatPref("LogsTextSize", logsTextSize);
        }

        unRegisterReceiver(context);

        closePleaseWaitDialog();

        if (updateCheck != null && updateCheck.context != null) {
            updateCheck.context = null;
            updateCheck = null;
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        if (context != null && !modulesStatus.isUseModulesWithRoot()
                && (modulesStatus.getDnsCryptState() == RUNNING || modulesStatus.getDnsCryptState() == STOPPED)
                && (modulesStatus.getTorState() == RUNNING || modulesStatus.getTorState() == STOPPED)
                && (modulesStatus.getItpdState() == RUNNING || modulesStatus.getItpdState() == STOPPED)
                && !(modulesStatus.getDnsCryptState() == STOPPED && modulesStatus.getTorState() == STOPPED && modulesStatus.getItpdState() == STOPPED)) {
            ModulesAux.slowdownModulesStateLoopTimer(context);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDestroy() {
        super.onDestroy();

        stopInstallationTimer();

        removeOnActivityChangeListener();

        stopModulesLogsTimer();

        if (rootChecker != null) {

            if (!rootChecker.isCancelled()) {
                rootChecker.cancel(true);
            }

            rootChecker.topFragmentWeakReference.clear();
            rootChecker.topFragmentWeakReference = null;
            rootChecker = null;
        }

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

            CachedExecutor.INSTANCE.startExecutorService();

            initModulesLogsTimer();

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

           shortenTooLongSnowflakeLog(context);

           if (topFragment.handler != null) {
               topFragment.handler.postDelayed(() -> {

                   if (activity.isFinishing()) {
                       return;
                   }

                   if (!topFragment.runModulesWithRoot
                           && haveModulesSavedStateRunning(context)
                           && !isModulesStarterServiceRunning(context)) {
                       startModulesStarterServiceIfStoppedBySystem(context);
                       Log.e(LOG_TAG, "ModulesService stopped by system!");
                   } else {
                       ModulesAux.speedupModulesStateLoopTimer(context);
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
                    if (topFragment.isAdded()) {
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
                    if (notificationHelper != null) {
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

            Activity activity = topFragment.getActivity();

            if (activity == null || activity.isFinishing()) {
                return;
            }

            topFragment.closePleaseWaitDialog();

            try {

                topFragment.setSUInfo(activity, suResult, suVersion);
                topFragment.setBBinfo(activity, bbResult);

                if (topFragment.rootIsAvailable != topFragment.rootIsAvailableSaved || topFragment.mode == UNDEFINED) {
                    ModulesAux.switchModes(activity, topFragment.rootIsAvailable, topFragment.runModulesWithRoot, topFragment.mode);

                    activity.invalidateOptionsMenu();
                }

                if (!PathVars.isModulesInstalled(activity)) {
                    topFragment.actionModulesNotInstalled(activity);
                } else {

                    if (topFragment.coreUpdateReady(activity)) {
                        return;
                    }

                    topFragment.refreshModulesVersions(activity);

                    topFragment.stopInstallationTimer();

                    if (topFragment.checkCrashReport(activity)) {
                        return;
                    }

                    ////////////////////////////CHECK UPDATES///////////////////////////////////////////
                    topFragment.checkUpdates(activity);

                    /////////////////////////////DONATION////////////////////////////////////////////
                    topFragment.showDonDialog(activity);

                    ////////////////////////////PATCH CONFIG of the MODULES if NECESSARY///////////////
                    Patch patch = new Patch(activity);
                    patch.checkPatches();
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "RootChecker onPostExecute " + e.getMessage() + " " + e.getCause());
            }
        }
    }

    private void showDonDialog(Activity activity) {

        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (appVersion.endsWith("e")) {

            Runnable performRegistration = () -> {
                if (isAdded()) {
                    Registration registration = new Registration(activity);
                    registration.showDonateDialog();
                }
            };

            if (handler != null) {
                handler.postDelayed(performRegistration, 5000);
            }
        } else if (appVersion.endsWith("p") && isAdded() && !accelerated) {

            if (!new PrefManager(activity).getBoolPref("Agreement")) {
                return;
            }

            if (handler != null) {
                handler.postDelayed(() -> {
                    DialogFragment accelerateDevelop = AskAccelerateDevelop.getInstance();
                    if (isAdded() && !accelerated) {
                        accelerateDevelop.show(getParentFragmentManager(), "accelerateDevelop");
                    }
                }, 5000);
            }

        }
    }

    private void refreshModulesVersions(Context context) {
        if (ModulesStatus.getInstance().isUseModulesWithRoot()) {
            Intent intent = new Intent(TOP_BROADCAST);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
            Log.i(LOG_TAG, "TopFragment Send TOP_BROADCAST");
        } else {
            ModulesVersions.getInstance().refreshVersions(context);
        }
    }

    private boolean coreUpdateReady(Context context) {

        if (context == null) {
            return false;
        }

        String currentDNSCryptVersionStr = new PrefManager(context).getStrPref("DNSCryptVersion");
        String currentTorVersionStr = new PrefManager(context).getStrPref("TorVersion");
        String currentITPDVersionStr = new PrefManager(context).getStrPref("ITPDVersion");
        if (!(currentDNSCryptVersionStr.isEmpty() && currentTorVersionStr.isEmpty() && currentITPDVersionStr.isEmpty())) {
            int currentDNSCryptVersion = Integer.parseInt(currentDNSCryptVersionStr.replaceAll("\\D+", ""));
            int currentTorVersion = Integer.parseInt(currentTorVersionStr.replaceAll("\\D+", ""));
            int currentITPDVersion = Integer.parseInt(currentITPDVersionStr.replaceAll("\\D+", ""));

            if (((currentDNSCryptVersion < Integer.parseInt(DNSCryptVersion.replaceAll("\\D+", ""))
                    || currentTorVersion < Integer.parseInt(TorVersion.replaceAll("\\D+", ""))
                    || currentITPDVersion < Integer.parseInt(ITPDVersion.replaceAll("\\D+", "")))
                    && !new PrefManager(context).getBoolPref("UpdateNotAllowed"))) {
                if (isAdded()) {
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
        new PrefManager(context).setStrPref("DNSCryptVersion", DNSCryptVersion);
        new PrefManager(context).setStrPref("TorVersion", TorVersion);
        new PrefManager(context).setStrPref("ITPDVersion", ITPDVersion);
        new PrefManager(context).setStrPref("DNSCrypt Servers", "");
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sPref.edit();
        editor.putBoolean("pref_common_tor_tethering", false);
        editor.putBoolean("pref_common_itpd_tethering", false);
        editor.apply();

        startInstallation();
    }

    private void setSUInfo(Context context, List<String> fSuResult, String fSuVersion) {

        if (fSuResult != null && fSuResult.size() != 0
                && fSuResult.toString().toLowerCase().contains("uid=0")
                && fSuResult.toString().toLowerCase().contains("gid=0")) {

            rootIsAvailable = true;
            new PrefManager(context).setBoolPref("rootIsAvailable", true);

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
            new PrefManager(context).setBoolPref("rootIsAvailable", false);
        }
    }

    private void setBBinfo(Context context, List<String> fBbResult) {

        if (fBbResult != null && fBbResult.size() != 0) {
            verBB = fBbResult.get(0);
        } else {
            new PrefManager(context).setBoolPref("bbOK", false);
            return;
        }

        if (verBB.toLowerCase().contains("not found")) {
            new PrefManager(context).setBoolPref("bbOK", false);
        } else {
            new PrefManager(context).setBoolPref("bbOK", true);

            Log.i(LOG_TAG, "BusyBox is available " + verBB);
        }
    }

    private void startInstallation() {

        stopInstallationTimer();

        if (modulesLogsTimer == null || modulesLogsTimer.isShutdown()) {
            initModulesLogsTimer();
        }

        scheduledFuture = modulesLogsTimer.scheduleAtFixedRate(new Runnable() {
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
        }
    }

    private boolean checkCrashReport(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return true;
        }

        if (appVersion.endsWith("p")) {
            return false;
        }

        String crash = new PrefManager(activity).getStrPref("CrashReport");
        if (!crash.isEmpty()) {
            SendCrashReport crashReport = SendCrashReport.Companion.getCrashReportDialog(activity);
            if (crashReport != null && isAdded()) {
                crashReport.show(getParentFragmentManager(), "SendCrashReport");
            }
            return true;
        }

        return false;
    }

    public void checkUpdates(Context context) {

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(context);

        if (!new PrefManager(context).getStrPref("RequiredAppUpdateForQ").isEmpty()) {
            Intent intent = new Intent(context, UpdateService.class);
            intent.setAction(UpdateService.INSTALLATION_REQUEST_ACTION);
            context.startService(intent);
            return;
        }

        boolean autoUpdate = spref.getBoolean("pref_fast_auto_update", true)
                && !appVersion.startsWith("l") && !appVersion.endsWith("p") && !appVersion.startsWith("f");

        if (autoUpdate) {
            boolean throughTorUpdate = spref.getBoolean("pref_fast through_tor_update", false);
            boolean torRunning = new PrefManager(context).getBoolPref("Tor Running");
            boolean torReady = new PrefManager(context).getBoolPref("Tor Ready");
            String lastUpdateResult = new PrefManager(context).getStrPref("LastUpdateResult");
            if (!throughTorUpdate || (torRunning && torReady)) {
                long updateTimeCurrent = System.currentTimeMillis();
                String updateTimeLastStr = new PrefManager(context).getStrPref("updateTimeLast");
                if (!updateTimeLastStr.isEmpty()) {
                    long updateTimeLast = Long.parseLong(updateTimeLastStr);
                    final int UPDATES_CHECK_INTERVAL_HOURS = 24;
                    int interval = 1000 * 60 * 60 * UPDATES_CHECK_INTERVAL_HOURS;
                    if ((updateTimeCurrent - updateTimeLast > interval)
                            || (lastUpdateResult.isEmpty() && ((updateTimeCurrent - updateTimeLast) > 300000))
                            || lastUpdateResult.equals(getString(R.string.update_check_warning_menu)))
                        checkNewVer();
                } else {
                    checkNewVer();
                }
            }
        }
    }

    public void checkNewVer() {

        if (appVersion.endsWith("p") || appVersion.startsWith("f")) {
            return;
        }

        Runnable runnable = () -> {
            Activity activity = getActivity();

            if (activity == null || updateCheck != null) {
                return;
            }

            new PrefManager(activity).setStrPref("LastUpdateResult", "");
            new PrefManager(activity).setStrPref("updateTimeLast", String.valueOf(System.currentTimeMillis()));

            updateCheck = new UpdateCheck(activity);
            try {
                updateCheck.requestUpdateData("https://invizible.net", appSign);
            } catch (Exception e) {
                if (activity instanceof MainActivity) {
                    new PrefManager(activity).setStrPref("LastUpdateResult", getString(R.string.update_fault));
                    if (MainActivity.modernDialog != null)
                        ((MainActivity) activity).showUpdateMessage(getString(R.string.update_fault));
                }
                Log.e(LOG_TAG, "TopFragment Failed to requestUpdate() " + e.getMessage() + " " + e.getCause());
            }
        };

        if (handler != null) {
            handler.postDelayed(runnable, 1000);
        }

    }

    public void downloadUpdate(String fileName, String updateStr, String message, String hash) {
        Context context = getActivity();
        if (context == null)
            return;

        closeMainActivityModernDialog();

        new PrefManager(context).setStrPref("LastUpdateResult", context.getString(R.string.update_found));

        if (isAdded()) {
            DialogFragment newUpdateDialogFragment = NewUpdateDialogFragment.newInstance(message, updateStr, fileName, hash);
            newUpdateDialogFragment.show(getParentFragmentManager(), NewUpdateDialogFragment.TAG_NOT_FRAG);
        }
    }

    private void closeMainActivityModernDialog() {
        try {
            if (MainActivity.modernDialog != null) {
                MainActivity.modernDialog.dismiss();
                MainActivity.modernDialog = null;
            }
        } catch (Exception ignored) {
        }
    }

    private static void startModulesStarterServiceIfStoppedBySystem(Context context) {
        ModulesAux.recoverService(context);
    }

    private static boolean isModulesStarterServiceRunning(Context context) {
        return Utils.INSTANCE.isServiceRunning(context, ModulesService.class);
    }

    private static boolean haveModulesSavedStateRunning(Context context) {

        boolean dnsCryptRunning = new PrefManager(context).getBoolPref("DNSCrypt Running");
        boolean torRunning = new PrefManager(context).getBoolPref("Tor Running");
        boolean itpdRunning = new PrefManager(context).getBoolPref("I2PD Running");

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

        if (activity == null || intent.getAction() == null || !isBroadcastMatch(intent) || !isAdded()) {
            return;
        }

        if (intent.getAction().equals(UpdateService.UPDATE_RESULT) && activity instanceof MainActivity) {
            ((MainActivity) activity).showUpdateResultMessage();
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

        return intent.getIntExtra("Mark", 0) == RootExecService.TopFragmentMark;
    }

    private void registerReceiver() {

        Context context = getActivity();

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
        if (!new PrefManager(context).getBoolPref("Agreement")) {
            AlertDialog.Builder agreementDialogBuilder = AgreementDialog.getDialogBuilder(context);
            if (agreementDialogBuilder != null) {
                agreementDialogBuilder.show();
            }
        }
    }

    private static void initModulesLogsTimer() {
        if (modulesLogsTimer == null || modulesLogsTimer.isShutdown()) {
            modulesLogsTimer = Executors.newScheduledThreadPool(0);
        }
    }

    @Nullable
    public static ScheduledExecutorService getModulesLogsTimer() {
        return modulesLogsTimer;
    }

    private void stopModulesLogsTimer() {
        if (modulesLogsTimer != null && !modulesLogsTimer.isShutdown()) {
            modulesLogsTimer.shutdownNow();
            modulesLogsTimer = null;
        }
    }

    private static void shortenTooLongSnowflakeLog(Context context) {
        try {
            boolean bridgesSnowflakeDefault = new PrefManager(context).getStrPref("defaultBridgesObfs").equals(snowFlakeBridgesDefault);
            boolean bridgesSnowflakeOwn = new PrefManager(context).getStrPref("ownBridgesObfs").equals(snowFlakeBridgesOwn);
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
            boolean showHelperMessages = shPref.getBoolean("pref_common_show_help", false);

            if (showHelperMessages && (bridgesSnowflakeDefault || bridgesSnowflakeOwn)) {
                PathVars pathVars = PathVars.getInstance(context);
                OwnFileReader snowflakeLog = new OwnFileReader(context, pathVars.getAppDataDir() + "/logs/Snowflake.log");
                snowflakeLog.shortenTooTooLongFile();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "TopFragment shortenTooLongSnowflakeLog exception " + e.getMessage() + " " + e.getCause());
        }
    }

}
