package pan.alexander.tordnscrypt.tor_fragment;

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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.TorRefreshIPsWork;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.TorVersion;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

public class TorFragmentPresenter implements TorFragmentPresenterCallbacks {

    public TorFragmentView view;

    private Timer timer = null;
    private String appDataDir;
    private int mJobId = PreferencesFastFragment.mJobId;
    private int refreshPeriodHours = 12;

    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState = STOPPED;

    private volatile OwnFileReader logFile;
    private int displayLogPeriod = -1;

    public TorFragmentPresenter(TorFragmentView view) {
        this.view = view;
    }

    public void onStart(Context context) {
        if (context == null || view == null) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(context);
        appDataDir = pathVars.getAppDataDir();

        modulesStatus = ModulesStatus.getInstance();

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        String refreshPeriod = shPref.getString("pref_fast_site_refresh_interval", "12");
        refreshPeriodHours = Integer.parseInt(refreshPeriod);

        logFile = new OwnFileReader(context, appDataDir + "/logs/Tor.log");

        if (isTorInstalled(context)) {
            setTorInstalled(true);

            if (modulesStatus.getTorState() == STOPPING){
                setTorStopping();

                if (logFile != null) {
                    view.setTorLogViewText(Html.fromHtml(logFile.readLastLines()));
                }

                displayLog(1000);
            } else if (isSavedTorStatusRunning(context) || modulesStatus.getTorState() == RUNNING) {
                setTorRunning();

                if (logFile != null) {
                    view.setTorLogViewText(Html.fromHtml(logFile.readLastLines()));
                }

                if (modulesStatus.getTorState() != RESTARTING) {
                    modulesStatus.setTorState(RUNNING);
                }
                displayLog(1000);
            } else {
                setTorStopped(context);
                modulesStatus.setTorState(STOPPED);
            }
        } else {
            setTorInstalled(false);
        }
    }

    public void onStop() {
        stopDisplayLog();
        view = null;
    }

    @Override
    public boolean isTorInstalled(Context context) {
        if (context != null) {
            return new PrefManager(context).getBoolPref("Tor Installed");
        }
        return false;
    }

    private void setTorInstalled(boolean installed) {
        if (installed) {
            view.setTorStartButtonEnabled(true);
        } else {
            view.setTorStatus(R.string.tvTorNotInstalled, R.color.textModuleStatusColorAlert);
        }
    }

    private void setTorStarting(Context context, int percents) {
        view.setTorStatus(context.getText(R.string.tvTorConnecting) + " " + percents + "%", R.color.textModuleStatusColorStarting);
    }

    private void setTorStarting() {
        view.setTorStatus(R.string.tvTorStarting, R.color.textModuleStatusColorStarting);
    }

    @Override
    public void setTorRunning() {
        if (view == null) {
            return;
        }

        view.setTorStatus(R.string.tvTorRunning, R.color.textModuleStatusColorRunning);
        view.setStartButtonText(R.string.btnTorStop);
    }

    private void setTorStopping() {
        if (view == null) {
            return;
        }

        view.setTorStatus(R.string.tvTorStopping, R.color.textModuleStatusColorStopping);
    }

    @Override
    public void setTorStopped(Context context) {
        if (view == null) {
            return;
        }

        stopRefreshTorUnlockIPs(context);

        view.setTorStatus(R.string.tvTorStop, R.color.textModuleStatusColorStopped);
        view.setStartButtonText(R.string.btnTorStart);
        view.setTorLogViewText();

        if (context != null) {
            new PrefManager(Objects.requireNonNull(context)).setBoolPref("Tor Ready", false);
        }
    }

    @Override
    public void setTorSomethingWrong() {
        if (view == null || modulesStatus == null) {
            return;
        }

        view.setTorStatus(R.string.wrong, R.color.textModuleStatusColorAlert);
        modulesStatus.setTorState(FAULT);
    }

    @Override
    public boolean isSavedTorStatusRunning(Context context) {
        if (context != null) {
            return new PrefManager(context).getBoolPref("Tor Running");
        }
        return false;
    }

    @Override
    public void saveTorStatusRunning(Context context, boolean running) {
        if (context != null) {
            new PrefManager(context).setBoolPref("Tor Running", running);
        }
    }

    @Override
    public void refreshTorState(Context context) {

        if (context == null || modulesStatus == null || view == null) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getTorState();

        if ((currentModuleState.equals(fixedModuleState)) && currentModuleState != STOPPED) {
            return;
        }

        if (currentModuleState == STARTING) {

            displayLog(1000);

        } else if (currentModuleState == RUNNING && view.getFragmentActivity() != null) {

            ServiceVPNHelper.prepareVPNServiceIfRequired(view.getFragmentActivity(), modulesStatus);

            view.setTorStartButtonEnabled(true);

            saveTorStatusRunning(context, true);

            view.setStartButtonText(R.string.btnTorStop);

        } else if (currentModuleState == STOPPED) {

            stopDisplayLog();

            if (isSavedTorStatusRunning(context)) {
                setTorStoppedBySystem(context);
            } else {
                setTorStopped(context);
            }

            view.setTorProgressBarIndeterminate(false);

            saveTorStatusRunning(context, false);

            view.setTorStartButtonEnabled(true);
        }

        fixedModuleState = currentModuleState;
    }

    private void setTorStoppedBySystem(Context context) {

        setTorStopped(context);

        if (context != null && modulesStatus != null) {

            modulesStatus.setTorState(STOPPED);

            ModulesAux.requestModulesStatusUpdate(context);

            if (view.getFragmentFragmentManager() != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        context, context.getText(R.string.helper_tor_stopped).toString(), "tor_suddenly_stopped");
                if (notificationHelper != null) {
                    notificationHelper.show(view.getFragmentFragmentManager(), NotificationHelper.TAG_HELPER);
                }
            }

            Log.e(LOG_TAG, context.getText(R.string.helper_tor_stopped).toString());
        }

    }

    @Override
    public void displayLog(int period) {

        if (period == displayLogPeriod) {
            return;
        }

        displayLogPeriod = period;

        if (timer != null) {
            timer.purge();
            timer.cancel();
        }

        timer = new Timer();

        timer.schedule(new TimerTask() {
            int loop = 0;
            String previousLastLines = "";

            @Override
            public void run() {

                if (logFile == null) {
                    return;
                }

                final String lastLines = logFile.readLastLines();

                if (++loop > 120) {
                    loop = 0;
                    displayLog(10000);
                }

                if (view == null || view.getFragmentActivity() == null) {
                    return;
                }

                view.getFragmentActivity().runOnUiThread(() -> {

                    if (view == null || view.getFragmentActivity() == null || lastLines == null || lastLines.isEmpty()) {
                        return;
                    }

                    if (!previousLastLines.contentEquals(lastLines)) {

                        if (!new PrefManager(view.getFragmentActivity()).getBoolPref("Tor Ready")) {
                            torStartedSuccessfully(view.getFragmentActivity(), lastLines);
                        }

                        torStartedWithError(view.getFragmentActivity(), lastLines);
                        view.setTorLogViewText(Html.fromHtml(lastLines));
                        previousLastLines = lastLines;
                    }

                    refreshTorState(view.getFragmentActivity());

                });
            }
        }, 1000, period);

    }

    @Override
    public void stopDisplayLog() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;

            displayLogPeriod = -1;
        }
    }

    private void torStartedSuccessfully(Context context, String lastLines) {

        if (context == null || view == null || modulesStatus == null) {
            return;
        }

        int lastPersIndex = lastLines.lastIndexOf("%");

        if (lastPersIndex < 16 || new PrefManager(context).getBoolPref("Tor Ready")) {
            return;
        }

        String bootstrapPerc = lastLines.substring(lastPersIndex - 16, lastPersIndex);

        if (!bootstrapPerc.contains("Bootstrapped ")) {
            return;
        }

        bootstrapPerc = bootstrapPerc.substring(bootstrapPerc.lastIndexOf(" ") + 1);

        if (bootstrapPerc.isEmpty() || !bootstrapPerc.matches("\\d+")) {
            return;
        }

        int perc = Integer.valueOf(bootstrapPerc);

        if (0 <= perc && perc < 100) {

            if (modulesStatus.getTorState() == STOPPED || modulesStatus.getTorState() == STOPPING) {
                return;
            }

            view.setTorProgressBarIndeterminate(false);

            view.setTorProgressBarProgress(perc);

            setTorStarting(context, perc);

        } else if (modulesStatus.getTorState() == RUNNING) {

            view.setTorProgressBarIndeterminate(false);

            setTorRunning();

            displayLog(5000);

            view.setTorProgressBarProgress(0);

            new PrefManager(Objects.requireNonNull(context)).setBoolPref("Tor Ready", true);

            /////////////////Check Updates///////////////////////////////////////////////
            if (view != null && view.getFragmentActivity() != null && view.getFragmentActivity() instanceof MainActivity) {
                checkInvizibleUpdates((MainActivity)view.getFragmentActivity());
            }
        }
    }

    private void torStartedWithError(Context context, String lastLines) {
        if (view == null) {
            return;
        }

        if (lastLines.contains("Problem bootstrapping.") && !lastLines.contains("Bootstrapped")) {

            Log.e(LOG_TAG, "Problem bootstrapping Tor: " + lastLines);

            if (lastLines.contains("Stuck at 0%") || lastLines.contains("Stuck at 5%")) {

                if (view.getFragmentFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, context.getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                    if (notificationHelper != null) {
                        notificationHelper.show(view.getFragmentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

            } else {

                if (view.getFragmentFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, context.getText(R.string.helper_tor_use_bridges).toString(), "helper_tor_use_bridges");
                    if (notificationHelper != null) {
                        notificationHelper.show(view.getFragmentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }
            }

        }
    }

    @Override
    public void startRefreshTorUnlockIPs(Context context) {
        if (context == null) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours == 0) {
            TorRefreshIPsWork torRefreshIPsWork = new TorRefreshIPsWork(context, null);
            torRefreshIPsWork.refreshIPs();
        } else {
            ComponentName jobService = new ComponentName(context, GetIPsJobService.class);
            JobInfo.Builder getIPsJobBuilder;
            getIPsJobBuilder = new JobInfo.Builder(mJobId, jobService);
            getIPsJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            getIPsJobBuilder.setPeriodic(refreshPeriodHours * 60 * 60 * 1000);

            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

            if (jobScheduler != null) {
                jobScheduler.schedule(getIPsJobBuilder.build());
            }
        }
    }

    @Override
    public void setTorInstalling() {
        if (view == null) {
            return;
        }

        view.setTorStatus(R.string.tvTorInstalling, R.color.textModuleStatusColorInstalling);
    }

    @Override
    public void setTorInstalled() {
        if (view == null) {
            return;
        }

        view.setTorStatus(R.string.tvTorInstalled, R.color.textModuleStatusColorInstalled);
    }

    @Override
    public void setTorStartButtonEnabled(boolean enabled) {
        if (view == null) {
            return;
        }

        view.setTorStartButtonEnabled(enabled);
    }

    @Override
    public void setTorProgressBarIndeterminate(boolean indeterminate) {
        if (view == null) {
            return;
        }

        view.setTorProgressBarIndeterminate(indeterminate);
    }

    private void stopRefreshTorUnlockIPs(Context context) {

        if (context == null || modulesStatus == null || !modulesStatus.isRootAvailable()) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours == 0) {
            return;
        }
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        if (shPref.getBoolean("swAutostartTor", false)) return;

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.cancel(mJobId);
        }
    }

    public void startButtonOnClick(Context context) {

        if (context == null || view == null || modulesStatus == null) {
            return;
        }

        if (((MainActivity) context).childLockActive) {
            Toast.makeText(context, context.getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return;
        }


        view.setTorStartButtonEnabled(false);

        //cleanLogFileNoRootMethod(context);

        Thread thread = new Thread(() -> {

            if (view == null || view.getFragmentActivity() == null || view.getFragmentFragmentManager() == null) {
                return;
            }

            try {
                Verifier verifier = new Verifier(view.getFragmentActivity());
                String appSign = verifier.getApkSignatureZipModern();
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    if (view.getFragmentFragmentManager() != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                view.getFragmentActivity(), view.getFragmentActivity().getText(R.string.verifier_error).toString(), "15");
                        if (notificationHelper != null) {
                            notificationHelper.show(view.getFragmentFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                }

            } catch (Exception e) {
                if (view.getFragmentFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            view.getFragmentActivity(), view.getFragmentActivity().getText(R.string.verifier_error).toString(), "18");
                    if (notificationHelper != null) {
                        notificationHelper.show(view.getFragmentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }
                Log.e(LOG_TAG, "TorRunFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });
        thread.start();

        if (!new PrefManager(Objects.requireNonNull(context)).getBoolPref("Tor Running") &&
                new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                view.setTorStartButtonEnabled(true);
                return;
            }


            startRefreshTorUnlockIPs(context);

            setTorStarting();

            runTor(context);

            displayLog(1000);
        } else if (!new PrefManager(context).getBoolPref("Tor Running") &&
                !new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                view.setTorStartButtonEnabled(true);
                return;
            }

            startRefreshTorUnlockIPs(context);

            setTorStarting();

            runTor(context);

            displayLog(1000);
        } else if (new PrefManager(Objects.requireNonNull(context)).getBoolPref("Tor Running") &&
                new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            stopRefreshTorUnlockIPs(context);

            setTorStopping();
            stopTor(context);
        } else if (new PrefManager(Objects.requireNonNull(context)).getBoolPref("Tor Running") &&
                !new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            stopRefreshTorUnlockIPs(context);

            setTorStopping();
            stopTor(context);
        }

        view.setTorProgressBarIndeterminate(true);

    }

    private void checkInvizibleUpdates(MainActivity activity) {

        if (activity == null) {
            return;
        }

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean throughTorUpdate = spref.getBoolean("pref_fast through_tor_update", false);
        if (throughTorUpdate) {
            FragmentManager fm = activity.getSupportFragmentManager();
            TopFragment topFragment = (TopFragment) fm.findFragmentByTag("topFragmentTAG");
            if (topFragment != null) {
                topFragment.checkUpdates();
            }
        }
    }

    private void runTor(Context context) {

        if (context == null) {
            return;
        }

        ModulesRunner.runTor(context);
    }

    private void stopTor(Context context) {
        if (context == null) {
            return;
        }

        ModulesKiller.stopTor(context);
    }

    private void cleanLogFileNoRootMethod(Context context) {
        try {
            File d = new File(appDataDir + "/logs");

            if (d.mkdirs() && d.setReadable(true) && d.setWritable(true))
                Log.i(LOG_TAG, "log dir created");

            PrintWriter writer = new PrintWriter(appDataDir + "/logs/Tor.log", "UTF-8");
            writer.println(context.getResources().getString(R.string.tvTorDefaultLog) + " " + TorVersion);
            writer.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to create Tor log file " + e.getMessage());
        }
    }
}
