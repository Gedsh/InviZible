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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.dialogs.InstallAppDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NewUpdateDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.dialogs.UpdateModulesDialogFragment;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.RootCheckingProgressDialog;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Registration;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.installer.Installer;
import pan.alexander.tordnscrypt.utils.modulesManager.ModulesRunner;
import pan.alexander.tordnscrypt.utils.modulesManager.ModulesService;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesStatus;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesVersions;
import pan.alexander.tordnscrypt.utils.update.UpdateCheck;
import pan.alexander.tordnscrypt.utils.update.UpdateService;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;


public class TopFragment extends Fragment {

    public static String DNSCryptVersion = "2.0.27";
    public static String TorVersion = "4.1.5";
    public static String ITPDVersion = "2.28.0";

    public static String appProcVersion = "armv7a";
    public static String appVersion = "lite";

    public static String verSU = "";
    public static String verBB = "";

    public static boolean debug = false;
    public static String TOP_BROADCAST = "pan.alexander.tordnscrypt.action.TOP_BROADCAST";
    public static String wrongSign;
    public static String appSign;

    private static DialogFragment dialogInterface;
    private static boolean rootIsAvailable = false;
    private static String suVersion = "";
    private static List<String> suResult = null;
    private static List<String> bbResult = null;

    UpdateCheck updateCheck;

    private Timer timer;
    private BroadcastReceiver br;
    private OnActivityChangeListener onActivityChangeListener;

    public interface OnActivityChangeListener {
        void OnActivityChange(MainActivity mainActivity);
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
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        appVersion = getString(R.string.appVersion);
        appProcVersion = getString(R.string.appProcVersion);

        registerReceiver();

        RootChecker rootChecker = new RootChecker();
        rootChecker.execute();

        if (onActivityChangeListener != null && getActivity() instanceof MainActivity) {
            onActivityChangeListener.OnActivityChange((MainActivity) getActivity());
        }
    }

    @Override
    public void onResume() {

        super.onResume();

        if (getActivity() != null) {
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean runModulesWithRoot = shPref.getBoolean("swUseModulesRoot", false);

            ModulesStatus.getInstance().setUseModulesWithRoot(runModulesWithRoot);

            if (!runModulesWithRoot && haveModulesSavedStateRunning() && !isModulesStarterServiceRunning()) {
                startModulesStarterServiceIfStoppedBySystem();
                Log.e(LOG_TAG, "ModulesService stopped by system!");
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

        closePleaseWaitDialog();

        if (updateCheck != null && updateCheck.context != null)
            updateCheck.context = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unRegisterReceiver();

        stopInstallationTimer();

        removeOnActivityChangeListener();
    }

    //Check if root available
    @SuppressLint("StaticFieldLeak")
    private class RootChecker extends AsyncTask<Void, Void, Void> {
        private boolean suAvailable = false;

        @Override
        protected void onPreExecute() {
            openPleaseWaitDialog();
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

            try {
                Verifier verifier = new Verifier(getActivity());
                appSign = verifier.getApkSignatureZipModern();
                String appSignAlt = verifier.getApkSignature();
                verifier.encryptStr(TOP_BROADCAST, appSign, appSignAlt);
                wrongSign = getString(R.string.encoded).trim();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    if (getFragmentManager() != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(), getText(R.string.verifier_error).toString(), "1112");
                        if (notificationHelper != null) {
                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                }

            } catch (Exception e) {
                if (getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.verifier_error).toString(), "2235");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }
                Log.e(LOG_TAG, "Top Fragment comparator fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {

            closePleaseWaitDialog();

            try {

                setSUInfo(suResult, suVersion);
                setBBinfo(bbResult);

                ModulesStatus.getInstance().setRootAvailable(rootIsAvailable);

                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }

                if (!isModulesInstalled(getActivity())) {
                    actionModulesNotInstalled();
                } else {

                    if (coreUpdateReady()) {
                        return;
                    }

                    refreshModulesVersions();

                    stopInstallationTimer();

                    ////////////////////////////CHECK UPDATES///////////////////////////////////////////
                    checkUpdates();

                    /////////////////////////////DONATION////////////////////////////////////////////
                    showDonDialog();
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "RootChecker onPostExecute " + e.getMessage() + " " + e.getCause());
            }
        }
    }

    private void showDonDialog() {
        if (appVersion.endsWith("e")) {
            Handler handler = new Handler();
            Runnable performRegistration = new Runnable() {
                @Override
                public void run() {
                    Registration registration = new Registration(getActivity());
                    registration.showDonateDialog();
                }
            };
            handler.postDelayed(performRegistration, 5000);
        }
    }

    private void refreshModulesVersions() {

        if (getActivity() == null) {
            return;
        }

        if (ModulesStatus.getInstance().isUseModulesWithRoot()) {
            Intent intent = new Intent(TOP_BROADCAST);
            getActivity().sendBroadcast(intent);
            Log.i(LOG_TAG, "TopFragment Send TOP_BROADCAST");
        } else {
            ModulesVersions.getInstance().refreshVersions(getActivity());
        }
    }

    private boolean coreUpdateReady() {

        if (getActivity() == null) {
            return false;
        }

        String currentDNSCryptVersionStr = new PrefManager(getActivity()).getStrPref("DNSCryptVersion");
        String currentTorVersionStr = new PrefManager(getActivity()).getStrPref("TorVersion");
        String currentITPDVersionStr = new PrefManager(getActivity()).getStrPref("ITPDVersion");
        if (!(currentDNSCryptVersionStr.isEmpty() && currentTorVersionStr.isEmpty() && currentITPDVersionStr.isEmpty())) {
            int currentDNSCryptVersion = Integer.parseInt(currentDNSCryptVersionStr.replaceAll("\\D+", ""));
            int currentTorVersion = Integer.parseInt(currentTorVersionStr.replaceAll("\\D+", ""));
            int currentITPDVersion = Integer.parseInt(currentITPDVersionStr.replaceAll("\\D+", ""));

            if ((currentDNSCryptVersion < Integer.parseInt(DNSCryptVersion.replaceAll("\\D+", ""))
                    || currentTorVersion < Integer.parseInt(TorVersion.replaceAll("\\D+", ""))
                    || currentITPDVersion < Integer.parseInt(ITPDVersion.replaceAll("\\D+", ""))
                    && !new PrefManager(getActivity()).getBoolPref("UpdateNotAllowed"))) {
                if (getFragmentManager() != null) {
                    DialogFragment updateCore = UpdateModulesDialogFragment.getInstance();
                    updateCore.show(getFragmentManager(), "UpdateModulesDialogFragment");
                }
                return true;
            }
        }

        return false;
    }

    private void actionModulesNotInstalled() {

        if (getActivity() == null) {
            return;
        }

        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences_common, true);
        PreferenceManager.setDefaultValues(Objects.requireNonNull(getActivity()), R.xml.preferences_dnscrypt, true);
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences_dnscrypt_servers, true);
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences_fast, true);
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences_tor, true);
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences_i2pd, true);

        if (getFragmentManager() != null) {
            DialogFragment installAll = InstallAppDialogFragment.getInstance();
            installAll.show(getFragmentManager(), "InstallAppDialogFragment");
        }

        //For core update purposes
        new PrefManager(getActivity()).setStrPref("DNSCryptVersion", DNSCryptVersion);
        new PrefManager(getActivity()).setStrPref("TorVersion", TorVersion);
        new PrefManager(getActivity()).setStrPref("ITPDVersion", ITPDVersion);
        new PrefManager(getActivity()).setStrPref("DNSCrypt Servers", "");
        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = sPref.edit();
        editor.putBoolean("pref_common_tor_tethering", false);
        editor.putBoolean("pref_common_itpd_tethering", false);
        editor.apply();
    }

    private void setSUInfo(List<String> fSuResult, String fSuVersion) {
        if (getActivity() == null) {
            return;
        }

        if (fSuResult != null && fSuResult.size() != 0
                && fSuResult.toString().toLowerCase().contains("uid=0")
                && fSuResult.toString().toLowerCase().contains("gid=0")) {

            rootIsAvailable = true;
            new PrefManager(getActivity()).setBoolPref("rootIsAvailable", true);

            if (fSuVersion != null && fSuVersion.length() != 0) {
                verSU = "Root is available." + (char) 10 +
                        "Super User Version: " + fSuVersion + (char) 10 +
                        fSuResult.get(0);
            } else {
                verSU = "Root is available." + (char) 10 +
                        "Super User Version: Unknown" +
                        fSuResult.get(0);
            }
            Log.i(LOG_TAG, "Root manager version " + verSU);
        } else {
            rootIsAvailable = false;
            new PrefManager(getActivity()).setBoolPref("rootIsAvailable", false);
        }
    }

    private void setBBinfo(List<String> fBbResult) {

        if (getActivity() == null) {
            return;
        }

        if (fBbResult != null && fBbResult.size() != 0) {
            verBB = fBbResult.get(0);
        } else {
            new PrefManager(getActivity()).setBoolPref("bbOK", false);
            return;
        }

        if (verBB.toLowerCase().contains("not found")) {
            new PrefManager(getActivity()).setBoolPref("bbOK", false);
        } else {
            new PrefManager(getActivity()).setBoolPref("bbOK", true);

            Log.i(LOG_TAG, "BusyBox is available " + verBB);
        }
    }

    public void startInstallation() {

        stopInstallationTimer();

        timer = new Timer();

        timer.schedule(new TimerTask() {
            int loop = 0;

            @Override
            public void run() {

                Log.i(LOG_TAG, "TopFragment Timer loop = " + loop);

                if (++loop > 15) {
                    stopInstallationTimer();
                    Log.w(LOG_TAG, "TopFragment Timer cancel, loop > 10");
                }


                if (getActivity() instanceof MainActivity) {
                    Installer installer = new Installer(getActivity());
                    installer.installModules();
                    Log.i(LOG_TAG, "TopFragment Timer startRefreshModulesStatus Modules Installation");
                    if (timer != null) timer.cancel();
                }
            }
        }, 3000, 1000);
    }

    private void stopInstallationTimer() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;
        }
    }

    public void checkUpdates() {

        if (getActivity() == null) {
            return;
        }

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean autoUpdate = spref.getBoolean("pref_fast_auto_update", true)
                && !appVersion.startsWith("l");
        if (autoUpdate) {
            boolean throughTorUpdate = spref.getBoolean("pref_fast through_tor_update", false);
            boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");
            boolean torReady = new PrefManager(getActivity()).getBoolPref("Tor Ready");
            if (!throughTorUpdate || (torRunning && torReady)) {
                long updateTimeCurrent = System.currentTimeMillis();
                String updateTimeLastStr = new PrefManager(getActivity()).getStrPref("updateTimeLast");
                if (!updateTimeLastStr.isEmpty()) {
                    long updateTimeLast = Long.parseLong(updateTimeLastStr);
                    int UPDATES_CHECK_INTERVAL_HOURS = 24;
                    if (updateTimeCurrent - updateTimeLast > 1000 * 60 * 60 * UPDATES_CHECK_INTERVAL_HOURS)
                        checkNewVer();
                } else {
                    checkNewVer();
                }
            }
        }
    }

    public void checkNewVer() {

        if (getActivity() == null) {
            return;
        }

        Handler handler = new Handler();

        new PrefManager(getActivity()).setStrPref("LastUpdateResult", "");

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null)
                    return;
                new PrefManager(getActivity()).setStrPref("updateTimeLast", String.valueOf(System.currentTimeMillis()));

                updateCheck = new UpdateCheck(getActivity());
                try {
                    updateCheck.requestUpdateData("https://invizible.net", appSign);
                } catch (Exception e) {
                    if (getActivity() != null) {
                        new PrefManager(getActivity()).setStrPref("LastUpdateResult", getText(R.string.update_fault).toString());
                        if (MainActivity.modernDialog != null)
                            ((MainActivity) getActivity()).showUpdateMessage(getText(R.string.update_fault).toString());
                    }
                    Log.e(LOG_TAG, "TopFragment Failed to requestUpdate() " + e.getMessage() + " " + e.getCause());
                }
            }
        };


        handler.postDelayed(runnable, 1000);

    }

    public void downloadUpdate(String fileName, String updateStr, String message, String hash) {
        if (getActivity() == null)
            return;

        closeMainActivityModernDialog();

        new PrefManager(getActivity()).setStrPref("LastUpdateResult", getActivity().getText(R.string.update_found).toString());

        if (getFragmentManager() != null) {
            DialogFragment newUpdateDialogFragment = NewUpdateDialogFragment.newInstance(message, updateStr, fileName, hash);
            newUpdateDialogFragment.show(getFragmentManager(), NewUpdateDialogFragment.TAG_NOT_FRAG);
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

    private boolean isModulesInstalled(Context context) {
        return new PrefManager(context).getBoolPref("DNSCrypt Installed")
                && new PrefManager(context).getBoolPref("Tor Installed")
                && new PrefManager(context).getBoolPref("I2PD Installed");
    }

    private void startModulesStarterServiceIfStoppedBySystem() {

        if (getActivity() == null) {
            return;
        }

        ModulesRunner.recoverService(getActivity());
    }

    private boolean isModulesStarterServiceRunning() {

        if (getActivity() == null) {
            return false;
        }

        ActivityManager manager = (ActivityManager) getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (ModulesService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean haveModulesSavedStateRunning() {

        if (getActivity() == null) {
            return false;
        }

        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");
        boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");
        boolean itpdRunning = new PrefManager(getActivity()).getBoolPref("I2PD Running");

        return dnsCryptRunning || torRunning || itpdRunning;
    }

    private void openPleaseWaitDialog() {
        try {
            if (getFragmentManager() != null) {
                dialogInterface = RootCheckingProgressDialog.getInstance();
                dialogInterface.show(getFragmentManager(), "rootCheckingProgressDialog");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "RootChecker openPleaseWaitDialog fault " + e.getMessage() + " " + e.getCause());
        }
    }

    private void closePleaseWaitDialog() {
        try {
            if (dialogInterface != null) {
                dialogInterface.dismiss();
                dialogInterface = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "RootChecker openPleaseWaitDialog fault " + e.getMessage() + " " + e.getCause());
        }
    }

    private void receiverOnReceive(Intent intent) {

        if (getActivity() == null || !isBroadcastMatch(intent)) {
            return;
        }

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showUpdateResultMessage();
        }

        refreshModulesVersions();
    }

    private boolean isBroadcastMatch(Intent intent) {
        if (intent == null) {
            return false;
        }

        String action = intent.getAction();

        if ((action == null) || (action.equals(""))) {
            return false;
        }

        if (!action.equals(UpdateService.UPDATE_RESULT)) {
            return false;
        }

        return intent.getIntExtra("Mark", 0) == RootExecService.TopFragmentMark;
    }

    protected void registerReceiver() {

        if (getActivity() == null) {
            return;
        }

        br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                receiverOnReceive(intent);
            }
        };
        IntentFilter intentFilter = new IntentFilter(UpdateService.UPDATE_RESULT);
        getActivity().registerReceiver(br, intentFilter);
    }

    protected void unRegisterReceiver() {
        try {
            if (br != null && getActivity() != null) {
                getActivity().unregisterReceiver(br);
            }
        } catch (Exception ignored) {
        }

    }
}
