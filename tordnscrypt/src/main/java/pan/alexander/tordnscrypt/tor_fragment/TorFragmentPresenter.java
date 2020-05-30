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
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.HttpsURLConnection;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.TorRefreshIPsWork;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesDefault;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesOwn;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class TorFragmentPresenter implements TorFragmentPresenterCallbacks {

    public TorFragmentView view;

    private ScheduledFuture<?> scheduledFuture;
    private int mJobId = PreferencesFastFragment.mJobId;
    private int refreshPeriodHours = 12;

    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState = STOPPED;

    private volatile OwnFileReader logFile;
    private int displayLogPeriod = -1;

    private HttpsURLConnection httpsURLConnection;
    private FutureTask<?> checkInetAvailableFutureTask;
    private boolean torLogAutoScroll = true;

    private final ReentrantLock reentrantLock = new ReentrantLock();

    public TorFragmentPresenter(TorFragmentView view) {
        this.view = view;
    }

    public void onStart(Context context) {
        if (context == null || view == null) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(context);
        String appDataDir = pathVars.getAppDataDir();

        modulesStatus = ModulesStatus.getInstance();

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        String refreshPeriod = shPref.getString("pref_fast_site_refresh_interval", "12");
        refreshPeriodHours = Integer.parseInt(refreshPeriod);

        logFile = new OwnFileReader(context, appDataDir + "/logs/Tor.log");

        if (isTorInstalled(context)) {
            setTorInstalled(true);

            if (modulesStatus.getTorState() == STOPPING){
                setTorStopping();

                displayLog(1);
            } else if (isSavedTorStatusRunning(context) || modulesStatus.getTorState() == RUNNING) {
                setTorRunning();

                if (modulesStatus.getTorState() != RESTARTING) {
                    modulesStatus.setTorState(RUNNING);
                }
                displayLog(5);
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
        if (checkInetAvailableFutureTask != null) {
            checkInetAvailableFutureTask.cancel(true);
        }
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
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        if (installed) {
            view.setTorStartButtonEnabled(true);
        } else {
            view.setTorStatus(R.string.tvTorNotInstalled, R.color.textModuleStatusColorAlert);
        }
    }

    private void setTorStarting(Context context, int percents) {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setTorStatus(context.getText(R.string.tvTorConnecting) + " " + percents + "%", R.color.textModuleStatusColorStarting);
    }

    private void setTorStarting() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setTorStatus(R.string.tvTorStarting, R.color.textModuleStatusColorStarting);
    }

    @Override
    public void setTorRunning() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setTorStatus(R.string.tvTorRunning, R.color.textModuleStatusColorRunning);
        view.setStartButtonText(R.string.btnTorStop);
    }

    private void setTorStopping() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setTorStatus(R.string.tvTorStopping, R.color.textModuleStatusColorStopping);
    }

    @Override
    public void setTorStopped(Context context) {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        stopRefreshTorUnlockIPs(context);

        view.setTorStatus(R.string.tvTorStop, R.color.textModuleStatusColorStopped);
        view.setStartButtonText(R.string.btnTorStart);
        view.setTorLogViewText();

        if (context != null) {
            new PrefManager(context).setBoolPref("Tor Ready", false);
        }
    }

    @Override
    public void setTorSomethingWrong() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || modulesStatus == null) {
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

        if (context == null || modulesStatus == null || view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getTorState();

        if ((currentModuleState.equals(fixedModuleState)) && currentModuleState != STOPPED) {
            return;
        }

        if (currentModuleState == STARTING) {

            displayLog(1);

        } else if (currentModuleState == RUNNING) {

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
                DialogFragment notification = NotificationDialogFragment.newInstance(R.string.helper_tor_stopped);
                notification.show(view.getFragmentFragmentManager(), "NotificationDialogFragment");
            }

            Log.e(LOG_TAG, context.getText(R.string.helper_tor_stopped).toString());
        }

    }

    @Override
    public synchronized void displayLog(int period) {

        ScheduledExecutorService timer = TopFragment.getModulesLogsTimer();

        if (timer == null || timer.isShutdown()) {
            new Handler().postDelayed(() -> {

                if (view != null && view.getFragmentActivity() != null && !view.getFragmentActivity().isDestroyed()) {
                    displayLog(period);
                }

            }, 1000);

            return;
        }

        if (period == displayLogPeriod) {
            return;
        }

        displayLogPeriod = period;

        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
        }

        scheduledFuture = timer.scheduleAtFixedRate(new Runnable() {
            int loop = 0;
            String previousLastLines = "";

            @Override
            public void run() {

                try {
                    if (logFile == null) {
                        return;
                    }

                    final String lastLines = logFile.readLastLines();

                    if (++loop > 120) {
                        loop = 0;
                        displayLog(10);
                    }

                    if (view == null || view.getFragmentActivity() == null) {
                        return;
                    }

                    view.getFragmentActivity().runOnUiThread(() -> {

                        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || lastLines == null || lastLines.isEmpty()) {
                            return;
                        }

                        if (!previousLastLines.contentEquals(lastLines) && torLogAutoScroll) {

                            if (!new PrefManager(view.getFragmentActivity()).getBoolPref("Tor Ready")) {
                                torStartedSuccessfully(view.getFragmentActivity(), lastLines);
                            }

                            torStartedWithError(view.getFragmentActivity(), lastLines);

                            view.setTorLogViewText(Html.fromHtml(lastLines));
                            view.scrollTorLogViewToBottom();

                            previousLastLines = lastLines;
                        }

                        refreshTorState(view.getFragmentActivity());

                    });
                } catch (Exception e) {
                    Log.e(LOG_TAG, "TorFragmentPresenter timer run() exception " + e.getMessage() + " " + e.getCause());
                }
            }
        }, 1, period, TimeUnit.SECONDS);

    }

    @Override
    public void stopDisplayLog() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);

            displayLogPeriod = -1;
        }
    }

    private void torStartedSuccessfully(Context context, String lastLines) {

        if (context == null || view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || modulesStatus == null) {
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

        int perc = Integer.parseInt(bootstrapPerc);

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

            displayLog(5);

            view.setTorProgressBarProgress(0);

            boolean torReady = new PrefManager(context).getBoolPref("Tor Ready");

            if (!torReady) {
                checkInternetAvailable();
            }
        }
    }

    private void torStartedWithError(Context context, String lastLines) {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        if (lastLines.contains("Problem bootstrapping.") && !lastLines.contains("Bootstrapped")) {

            Log.e(LOG_TAG, "Problem bootstrapping Tor: " + lastLines);

            if (lastLines.contains("Stuck at 0%") || lastLines.contains("Stuck at 5%")) {

                if (view.getFragmentFragmentManager() != null && !view.getFragmentActivity().isFinishing()) {
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
        if (context == null || view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
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
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setTorStatus(R.string.tvTorInstalling, R.color.textModuleStatusColorInstalling);
    }

    @Override
    public void setTorInstalled() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setTorStatus(R.string.tvTorInstalled, R.color.textModuleStatusColorInstalled);
    }

    @Override
    public void setTorStartButtonEnabled(boolean enabled) {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setTorStartButtonEnabled(enabled);
    }

    @Override
    public void setTorProgressBarIndeterminate(boolean indeterminate) {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
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

        if (context == null || view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || modulesStatus == null) {
            return;
        }

        if (((MainActivity) context).childLockActive) {
            Toast.makeText(context, context.getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return;
        }


        view.setTorStartButtonEnabled(false);

        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || view.getFragmentFragmentManager() == null) {
            return;
        }

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {

            try {
                Verifier verifier = new Verifier(view.getFragmentActivity());
                String appSign = verifier.getApkSignatureZip();
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

        if (!new PrefManager(Objects.requireNonNull(context)).getBoolPref("Tor Running") &&
                new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                view.setTorStartButtonEnabled(true);
                return;
            }

            setTorStarting();

            runTor(context);

            displayLog(1);
        } else if (!new PrefManager(context).getBoolPref("Tor Running") &&
                !new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                view.setTorStartButtonEnabled(true);
                return;
            }

            setTorStarting();

            runTor(context);

            displayLog(1);
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

        if (activity == null || view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean throughTorUpdate = spref.getBoolean("pref_fast through_tor_update", false);
        boolean autoUpdate = spref.getBoolean("pref_fast_auto_update", true)
                && !appVersion.startsWith("l") && !appVersion.endsWith("p") && !appVersion.startsWith("f");
        String lastUpdateResult = new PrefManager(activity).getStrPref("LastUpdateResult");
        if (autoUpdate &&
                (throughTorUpdate || lastUpdateResult.isEmpty() || lastUpdateResult.equals(activity.getString(R.string.update_check_warning_menu)))) {
            FragmentManager fm = activity.getSupportFragmentManager();
            TopFragment topFragment = (TopFragment) fm.findFragmentByTag("topFragmentTAG");
            if (topFragment != null) {
                topFragment.checkUpdates();
            }
        }
    }

    private void runTor(Context context) {

        if (context == null || view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        ModulesRunner.runTor(context);
    }

    private void stopTor(Context context) {
        if (context == null || view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        ModulesKiller.stopTor(context);
    }

    private void checkInternetAvailable() {
        checkInetAvailableFutureTask = new FutureTask<>(() -> {

            try {

                if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
                    return null;
                }

                reentrantLock.lock();

                Context context = view.getFragmentActivity();

                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1",
                        Integer.parseInt(PathVars.getInstance(context).getTorHTTPTunnelPort())));

                URL url = new URL("https://www.torproject.org/");

                httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);

                httpsURLConnection.setConnectTimeout(1000 * 60 * 15);
                httpsURLConnection.setReadTimeout(1000 * 60 * 15);
                httpsURLConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9.0.1; " +
                        "Mi Mi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Mobile Safari/537.36");
                httpsURLConnection.connect();

                Log.i(LOG_TAG, "Tor connection is available. Tor ready.");

                boolean useDefaultBridges = new PrefManager(context).getBoolPref("useDefaultBridges");
                boolean useOwnBridges = new PrefManager(context).getBoolPref("useOwnBridges");
                boolean bridgesSnowflakeDefault = new PrefManager(context).getStrPref("defaultBridgesObfs").equals(snowFlakeBridgesDefault);
                boolean bridgesSnowflakeOwn = new PrefManager(context).getStrPref("ownBridgesObfs").equals(snowFlakeBridgesOwn);

                boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                        && !modulesStatus.isUseModulesWithRoot();

                new PrefManager(context).setBoolPref("Tor Ready", true);

                if (useDefaultBridges && bridgesSnowflakeDefault || useOwnBridges && bridgesSnowflakeOwn) {
                    if (modulesStatus != null && modulesStatus.getMode() == ROOT_MODE) {
                        new PrefManager(context).setBoolPref("DNSCryptSystemDNSAllowed", false);
                        ModulesIptablesRules.denySystemDNS(context);
                    }

                    if (modulesStatus != null && modulesStatus.getMode() == VPN_MODE || fixTTL) {
                        new PrefManager(context).setBoolPref("DNSCryptSystemDNSAllowed", false);
                        ServiceVPNHelper.reload("Tor Deny system DNS", context);
                    }
                }

                startRefreshTorUnlockIPs(context);

                /////////////////Check Updates///////////////////////////////////////////////
                if (view != null && view.getFragmentActivity() != null && view.getFragmentActivity() instanceof MainActivity) {
                    checkInvizibleUpdates((MainActivity)view.getFragmentActivity());
                }

            } catch (Exception e) {
                Log.w(LOG_TAG, "TorFragmentPresenter Internet Not Connected. " + e.getMessage() + " " + e.getCause());
            } finally {

                if (httpsURLConnection != null) {
                    httpsURLConnection.disconnect();
                }

                if (reentrantLock.isLocked()) {
                    reentrantLock.unlock();
                }
            }

            return null;
        });

        if (view != null && view.getFragmentActivity() != null
                && !new PrefManager(view.getFragmentActivity()).getBoolPref("Tor Ready")) {
            CachedExecutor.INSTANCE.getExecutorService().submit(checkInetAvailableFutureTask);
        }
    }

    public void torLogAutoScrollingAllowed(boolean allowed) {
        torLogAutoScroll = allowed;
    }
}
