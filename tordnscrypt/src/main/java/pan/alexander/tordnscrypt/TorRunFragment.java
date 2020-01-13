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

import android.annotation.SuppressLint;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.TorRefreshIPsWork;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
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
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;


public class TorRunFragment extends Fragment implements View.OnClickListener {

    private BroadcastReceiver br = null;
    private Button btnTorStart = null;
    private TextView tvTorStatus = null;
    private ProgressBar pbTor = null;
    private TextView tvTorLog = null;
    private Timer timer = null;
    private String appDataDir;
    private String torPath;
    private String busyboxPath;
    private int mJobId = PreferencesFastFragment.mJobId;
    private int refreshPeriodHours = 12;
    private boolean routeAllThroughTor = true;

    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState = STOPPED;

    private OwnFileReader logFile;
    private int displayLogPeriod = -1;


    public TorRunFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        br = new BroadcastReceiver() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onReceive(Context context, Intent intent) {

                if (getActivity() == null) {
                    return;
                }

                if (intent != null) {
                    final String action = intent.getAction();
                    if (action == null || action.equals("") || ((intent.getIntExtra("Mark", 0) !=
                            RootExecService.TorRunFragmentMark) &&
                            !action.equals(TopFragment.TOP_BROADCAST))) return;
                    Log.i(LOG_TAG, "TorRunFragment onReceive");
                    if (action.equals(RootExecService.COMMAND_RESULT)) {

                        setProgressBarIndeterminate(false);

                        setStartButtonEnabled(true);

                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        if (comResult != null && comResult.getCommands().length == 0) {
                            setTorSomethingWrong();
                            return;
                        }

                        StringBuilder sb = new StringBuilder();
                        if (comResult != null) {
                            for (String com : comResult.getCommands()) {
                                Log.i(LOG_TAG, com);
                                sb.append(com).append((char) 10);
                            }
                        }

                        if (sb.toString().contains("Tor_version")) {
                            String[] strArr = sb.toString().split("Tor_version");
                            if (strArr.length > 1) {
                                String[] verArr = strArr[1].trim().split(" ");
                                if (verArr.length > 2 && verArr[1].contains("version")) {
                                    TorVersion = verArr[2].trim();
                                    new PrefManager(getActivity()).setStrPref("TorVersion", TorVersion);

                                    if (!modulesStatus.isUseModulesWithRoot()) {
                                        if (!isSavedTorStatusRunning()) {
                                            String tvTorLogText = getText(R.string.tvTorDefaultLog) + " " + TorVersion;
                                            tvTorLog.setText(tvTorLogText);
                                        }

                                        refreshTorState();
                                    }
                                }
                            }
                        }

                        if (sb.toString().toLowerCase().contains(torPath)
                                && sb.toString().contains("checkTrRunning")) {

                            saveTorStatusRunning(true);
                            modulesStatus.setTorState(RUNNING);
                            btnTorStart.setText(R.string.btnTorStop);
                            startRefreshTorUnlockIPs();
                            displayLog(5000);

                        } else if (!sb.toString().toLowerCase().contains(torPath)
                                && sb.toString().contains("checkTrRunning")) {
                            if (modulesStatus.getTorState() == STOPPED) {
                                saveTorStatusRunning(false);
                            }
                            stopDisplayLog();
                            setTorStopped();
                            modulesStatus.setTorState(STOPPED);
                            refreshTorState();
                            pbTor.setProgress(0);
                        } else if (sb.toString().contains("Something went wrong!")) {
                            setTorSomethingWrong();
                        }

                    }

                    if (action.equals(TopFragment.TOP_BROADCAST)) {
                        if (TopFragment.TOP_BROADCAST.contains("TOP_BROADCAST")) {
                            Log.i(LOG_TAG, "TorRunFragment onReceive TOP_BROADCAST");

                            checkTorVersionWithRoot();
                        }

                    }

                }
            }
        };


    }

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tor_run, container, false);

        if (getActivity() == null) {
            return view;
        }

        btnTorStart = view.findViewById(R.id.btnTorStart);
        btnTorStart.setOnClickListener(this);

        pbTor = view.findViewById(R.id.pbTor);

        String currentTorVersion = new PrefManager(getActivity()).getStrPref("TorVersion");

        tvTorLog = view.findViewById(R.id.tvTorLog);
        tvTorLog.setText(getText(R.string.tvTorDefaultLog) + " " + currentTorVersion);
        tvTorLog.setMovementMethod(ScrollingMovementMethod.getInstance());

        tvTorStatus = view.findViewById(R.id.tvTorStatus);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() == null) {
            return;
        }

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        torPath = pathVars.torPath;
        busyboxPath = pathVars.busyboxPath;

        modulesStatus = ModulesStatus.getInstance();

        logFile = new OwnFileReader(getActivity(), appDataDir + "/logs/Tor.log");

        if (isTorInstalled()) {
            setTorInstalled(true);

            if (modulesStatus.getTorState() == STOPPING){
                setTorStopping();

                if (logFile != null) {
                    tvTorLog.setText(Html.fromHtml(logFile.readLastLines()));
                }

                displayLog(1000);
            } else if (isSavedTorStatusRunning() || modulesStatus.getTorState() == RUNNING) {
                setTorRunning();

                if (logFile != null) {
                    tvTorLog.setText(Html.fromHtml(logFile.readLastLines()));
                }

                if (modulesStatus.getTorState() != RESTARTING) {
                    modulesStatus.setTorState(RUNNING);
                }
                displayLog(1000);
            } else {
                setTorStopped();
                modulesStatus.setTorState(STOPPED);
            }
        } else {
            setTorInstalled(false);
        }


        IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
        IntentFilter intentFilterTopFrg = new IntentFilter(TopFragment.TOP_BROADCAST);

        if (getActivity() != null) {
            getActivity().registerReceiver(br, intentFilterBckgIntSer);
            getActivity().registerReceiver(br, intentFilterTopFrg);

            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String refreshPeriod = shPref.getString("pref_fast_site_refresh_interval", "12");
            refreshPeriodHours = Integer.parseInt(refreshPeriod);

            routeAllThroughTor = shPref.getBoolean("pref_fast_all_through_tor", true);
        }

    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            stopDisplayLog();
            if (br != null) Objects.requireNonNull(getActivity()).unregisterReceiver(br);
        } catch (Exception e) {
            Log.e(LOG_TAG, "TorFragment onStop exception " + e.getMessage() + " " + e.getCause());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    @Override
    public void onClick(View v) {

        if (getActivity() == null) {
            return;
        }

        if (((MainActivity) getActivity()).childLockActive) {
            Toast.makeText(getActivity(), getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return;
        }


        if (v.getId() == R.id.btnTorStart) {

            setStartButtonEnabled(false);

            cleanLogFileNoRootMethod();

            boolean rootMode = modulesStatus.getMode() == ROOT_MODE;

            Thread thread = new Thread(() -> {
                try {
                    Verifier verifier = new Verifier(getActivity());
                    String appSign = verifier.getApkSignatureZipModern();
                    String appSignAlt = verifier.getApkSignature();
                    if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                        if (getFragmentManager() != null) {
                            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                    getActivity(), getText(R.string.verifier_error).toString(), "15");
                            if (notificationHelper != null) {
                                notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                            }
                        }
                    }

                } catch (Exception e) {
                    if (getFragmentManager() != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(), getText(R.string.verifier_error).toString(), "18");
                        if (notificationHelper != null) {
                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                    Log.e(LOG_TAG, "TorRunFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                            Arrays.toString(e.getStackTrace()));
                }
            });
            thread.start();

            if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running") &&
                    new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()|| fixedModuleState == RUNNING) {
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_SHORT).show();
                    setStartButtonEnabled(true);
                    return;
                }


                startRefreshTorUnlockIPs();

                if (!routeAllThroughTor) {
                    if (rootMode && getFragmentManager() != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(), getText(R.string.helper_dnscrypt_tor).toString(), "dnscrypt_tor");
                        if (notificationHelper != null) {
                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                } else {

                    if (rootMode && getFragmentManager() != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(), getText(R.string.helper_dnscrypt_tor_privacy).toString(), "dnscrypt_tor_privacy");
                        if (notificationHelper != null) {
                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                }


                setTorStarting();

                runTor();

                displayLog(1000);
            } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running") &&
                    !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()|| fixedModuleState == RUNNING) {
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_SHORT).show();
                    setStartButtonEnabled(true);
                    return;
                }


                if (rootMode && getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_tor).toString(), "tor");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

                startRefreshTorUnlockIPs();

                setTorStarting();

                runTor();

                displayLog(1000);
            } else if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running") &&
                    new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                stopRefreshTorUnlockIPs();

                setTorStopping();
                stopTor();
            } else if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running") &&
                    !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                stopRefreshTorUnlockIPs();

                setTorStopping();
                stopTor();
            }

            setProgressBarIndeterminate(true);
        }

    }

    private void checkTorVersionWithRoot() {
        if (isTorInstalled() && getActivity() != null) {

            String[] commandsCheck = {
                    busyboxPath + "pgrep -l /tor",
                    busyboxPath + "echo 'checkTrRunning'",
                    busyboxPath + "echo 'Tor_version'",
                    torPath + " --version"
            };
            RootCommands rootCommands = new RootCommands(commandsCheck);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.TorRunFragmentMark);
            RootExecService.performAction(getActivity(), intent);

            setProgressBarIndeterminate(true);
        }
    }

    private void refreshTorState() {

        if (modulesStatus == null) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getTorState();

        if ((currentModuleState.equals(fixedModuleState)) && currentModuleState != STOPPED) {
            return;
        }

        if (currentModuleState == STARTING) {

            displayLog(1000);

        } else if (currentModuleState == RUNNING) {

            ServiceVPNHelper.prepareVPNServiceIfRequired(getActivity(), modulesStatus);

            setStartButtonEnabled(true);

            saveTorStatusRunning(true);

            btnTorStart.setText(R.string.btnTorStop);

        } else if (currentModuleState == STOPPED) {

            if (isSavedTorStatusRunning()) {
                setTorStoppedBySystem();
            } else {
                setTorStopped();
            }

            setProgressBarIndeterminate(false);

            stopDisplayLog();

            saveTorStatusRunning(false);

            setStartButtonEnabled(true);
        }

        fixedModuleState = currentModuleState;
    }

    private void setTorStarting() {
        setTorStatus(R.string.tvTorStarting, R.color.textModuleStatusColorStarting);
    }

    private void setTorRunning() {
        setTorStatus(R.string.tvTorRunning, R.color.textModuleStatusColorRunning);
        btnTorStart.setText(R.string.btnTorStop);
    }

    private void setTorStopping() {
        setTorStatus(R.string.tvTorStopping, R.color.textModuleStatusColorStopping);
    }

    private void setTorStopped() {
        stopRefreshTorUnlockIPs();

        setTorStatus(R.string.tvTorStop, R.color.textModuleStatusColorStopped);
        btnTorStart.setText(R.string.btnTorStart);
        String tvTorLogText = getText(R.string.tvTorDefaultLog) + " " + TorVersion;
        tvTorLog.setText(tvTorLogText);

        if (getActivity() != null) {
            new PrefManager(Objects.requireNonNull(getActivity())).setBoolPref("Tor Ready", false);
        }
    }

    private void setTorStoppedBySystem() {

        setTorStopped();

        if (getActivity() != null) {

            modulesStatus.setTorState(STOPPED);

            ModulesAux.requestModulesStatusUpdate(getActivity());

            if (getFragmentManager() != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_tor_stopped).toString(), "tor_suddenly_stopped");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }
            }

            Log.e(LOG_TAG, getText(R.string.helper_tor_stopped).toString());
        }

    }

    private void setTorInstalled(boolean installed) {
        if (installed) {
            btnTorStart.setEnabled(true);
        } else {
            tvTorStatus.setText(getText(R.string.tvTorNotInstalled));
        }
    }

    private void setTorSomethingWrong() {
        setTorStatus(R.string.wrong, R.color.textModuleStatusColorAlert);
        modulesStatus.setTorState(FAULT);
    }

    private boolean isTorInstalled() {
        if (getActivity() != null) {
            return new PrefManager(getActivity()).getBoolPref("Tor Installed");
        }
        return false;
    }

    private boolean isSavedTorStatusRunning() {
        if (getActivity() != null) {
            return new PrefManager(getActivity()).getBoolPref("Tor Running");
        }
        return false;
    }

    private void saveTorStatusRunning(boolean running) {
        if (getActivity() != null) {
            new PrefManager(getActivity()).setBoolPref("Tor Running", running);
        }
    }

    public void setTorStatus(int resourceText, int resourceColor) {
        tvTorStatus.setText(resourceText);
        tvTorStatus.setTextColor(getResources().getColor(resourceColor));
    }

    public void setStartButtonEnabled(boolean enabled) {
        if (btnTorStart.isEnabled() && !enabled) {
            btnTorStart.setEnabled(false);
        } else if (!btnTorStart.isEnabled() && enabled) {
            btnTorStart.setEnabled(true);
        }
    }

    public void setProgressBarIndeterminate(boolean indeterminate) {
        if (!pbTor.isIndeterminate() && indeterminate) {
            pbTor.setIndeterminate(true);
        } else if (pbTor.isIndeterminate() && !indeterminate) {
            pbTor.setIndeterminate(false);
        }

    }

    private void displayLog(int period) {

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

                if (getActivity() == null) {
                    return;
                }
                final String lastLines = logFile.readLastLines();

                if (++loop > 120) {
                    loop = 0;
                    displayLog(10000);
                }

                getActivity().runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        refreshTorState();

                        if (!previousLastLines.contentEquals(lastLines)) {

                            if (!new PrefManager(getActivity()).getBoolPref("Tor Ready")) {
                                torStartedSuccessfully(lastLines);
                            }

                            torStartedWithError(lastLines);

                            tvTorLog.setText(Html.fromHtml(lastLines));
                            previousLastLines = lastLines;
                        }

                    }
                });
            }
        }, 1, period);

    }

    private void stopDisplayLog() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;

            displayLogPeriod = -1;
        }
    }

    private void torStartedSuccessfully(String lastLines) {

        if (getActivity() == null) {
            return;
        }

        int lastPersIndex = lastLines.lastIndexOf("%");

        if (lastPersIndex < 16 || new PrefManager(getActivity()).getBoolPref("Tor Ready")) {
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

            setProgressBarIndeterminate(false);

            pbTor.setProgress(perc);
            setTorStarting();

        } else if (modulesStatus.getTorState() == RUNNING) {

            setProgressBarIndeterminate(false);

            setTorRunning();

            displayLog(5000);

            pbTor.setProgress(0);

            new PrefManager(Objects.requireNonNull(getActivity())).setBoolPref("Tor Ready", true);

            /////////////////Check Updates///////////////////////////////////////////////
            checkInvizibleUpdates();
        }
    }

    private void torStartedWithError(String lastLines) {
        if (lastLines.contains("Problem bootstrapping.") && !lastLines.contains("Bootstrapped")) {

            Log.e(LOG_TAG, "Problem bootstrapping Tor: " + lastLines);

            if (lastLines.contains("Stuck at 0%") || lastLines.contains("Stuck at 5%")) {

                if (getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

            } else {

                if (getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_tor_use_bridges).toString(), "helper_tor_use_bridges");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }
            }

        }
    }

    private void checkInvizibleUpdates() {

        if (getActivity() == null) {
            return;
        }

        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean throughTorUpdate = spref.getBoolean("pref_fast through_tor_update", false);
        if (throughTorUpdate) {
            FragmentManager fm = getActivity().getSupportFragmentManager();
            TopFragment topFragment = (TopFragment) fm.findFragmentByTag("topFragmentTAG");
            if (topFragment != null) {
                topFragment.checkUpdates();
            }
        }
    }

    private void startRefreshTorUnlockIPs() {
        if (getActivity() == null) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours == 0) {
            TorRefreshIPsWork torRefreshIPsWork = new TorRefreshIPsWork(getActivity(), null);
            torRefreshIPsWork.refreshIPs();
        } else {
            ComponentName jobService = new ComponentName(getActivity(), GetIPsJobService.class);
            JobInfo.Builder getIPsJobBuilder;
            getIPsJobBuilder = new JobInfo.Builder(mJobId, jobService);
            getIPsJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            getIPsJobBuilder.setPeriodic(refreshPeriodHours * 60 * 60 * 1000);

            JobScheduler jobScheduler = (JobScheduler) getActivity().getSystemService(Context.JOB_SCHEDULER_SERVICE);

            if (jobScheduler != null) {
                jobScheduler.schedule(getIPsJobBuilder.build());
            }
        }
    }

    private void stopRefreshTorUnlockIPs() {

        if (getActivity() == null || !modulesStatus.isRootAvailable()) {
            return;
        }

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours == 0) {
            return;
        }
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (shPref.getBoolean("swAutostartTor", false)) return;

        JobScheduler jobScheduler = (JobScheduler) getActivity().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.cancel(mJobId);
        }
    }

    private void runTor() {

        if (getActivity() == null) {
            return;
        }

        ModulesRunner.runTor(getActivity());
    }

    private void stopTor() {
        if (getActivity() == null) {
            return;
        }

        ModulesKiller.stopTor(getActivity());
    }

    private void cleanLogFileNoRootMethod() {
        try {
            File d = new File(appDataDir + "/logs");

            if (d.mkdirs() && d.setReadable(true) && d.setWritable(true))
                Log.i(LOG_TAG, "log dir created");

            PrintWriter writer = new PrintWriter(appDataDir + "/logs/Tor.log", "UTF-8");
            writer.println(getResources().getString(R.string.tvTorDefaultLog) + " " + TorVersion);
            writer.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to create Tor log file " + e.getMessage());
        }
    }

}
