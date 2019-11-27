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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.preference.PreferenceManager;
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
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;


public class DNSCryptRunFragment extends Fragment implements View.OnClickListener {

    private BroadcastReceiver br = null;
    private Button btnDNSCryptStart = null;
    private TextView tvDNSStatus = null;
    private ProgressBar pbDNSCrypt = null;
    private TextView tvDNSCryptLog = null;
    private Timer timer = null;
    private String appDataDir;
    private String dnscryptPath;
    private String busyboxPath;
    private boolean routeAllThroughTor = true;

    private OwnFileReader logFile;

    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState;
    private int displayLogPeriod = -1;


    public DNSCryptRunFragment() {
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
                            RootExecService.DNSCryptRunFragmentMark) &&
                            !action.equals(TOP_BROADCAST))) return;
                    Log.i(LOG_TAG, "DNSCryptRunFragment onReceive");

                    if (action.equals(RootExecService.COMMAND_RESULT)) {

                        setProgressBarIndeterminate(false);

                        setStartButtonEnabled(true);

                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        if (comResult.getCommands().length == 0) {
                            setDnsCryptSomethingWrong();
                            return;
                        }

                        StringBuilder sb = new StringBuilder();
                        for (String com : comResult.getCommands()) {
                            Log.i(LOG_TAG, com);
                            sb.append(com).append((char) 10);
                        }

                        if (sb.toString().contains("DNSCrypt_version")) {
                            String[] strArr = sb.toString().split("DNSCrypt_version");
                            if (strArr.length > 1 && strArr[1].trim().matches("\\d+\\.\\d+\\.\\d+")) {
                                DNSCryptVersion = strArr[1].trim();
                                new PrefManager(getActivity()).setStrPref("DNSCryptVersion", DNSCryptVersion);

                                if (!modulesStatus.isUseModulesWithRoot()) {

                                    if (!isSavedDNSStatusRunning()) {
                                        String tvDNSCryptLogText = getText(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion;
                                        tvDNSCryptLog.setText(tvDNSCryptLogText);
                                    }

                                    refreshDNSCryptState();
                                }
                            }
                        }

                        if (sb.toString().toLowerCase().contains(dnscryptPath)
                                && sb.toString().contains("checkDNSRunning")) {

                            setDnsCryptRunning();
                            saveDNSStatusRunning(true);
                            modulesStatus.setDnsCryptState(RUNNING);
                            displayLog(5000);

                        } else if (!sb.toString().toLowerCase().contains(dnscryptPath)
                                && sb.toString().contains("checkDNSRunning")) {
                            if (modulesStatus.getDnsCryptState() == STOPPED) {
                                saveDNSStatusRunning(false);
                            }
                            stopDisplayLog();
                            setDnsCryptStopped();
                            modulesStatus.setDnsCryptState(STOPPED);
                            refreshDNSCryptState();
                        } else if (sb.toString().contains("Something went wrong!")) {
                            setDnsCryptSomethingWrong();
                        }

                    }

                    if (action.equals(TOP_BROADCAST)) {
                        if (TOP_BROADCAST.contains("TOP_BROADCAST")) {
                            Log.i(LOG_TAG, "DNSCryptRunFragment onReceive TOP_BROADCAST");

                            checkDNSVersionWithRoot();
                        }
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Verifier verifier = new Verifier(getActivity());
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
                                    Log.e(LOG_TAG, "DNSCryptRunFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                                            Arrays.toString(e.getStackTrace()));
                                }
                            }
                        });
                        thread.start();
                    }
                }
            }
        };

    }

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_dnscrypt_run, container, false);

        if (getActivity() == null) {
            return view;
        }

        btnDNSCryptStart = view.findViewById(R.id.btnDNSCryptStart);
        btnDNSCryptStart.setOnClickListener(this);

        pbDNSCrypt = view.findViewById(R.id.pbDNSCrypt);

        String currentDNSCryptVersion = new PrefManager(getActivity()).getStrPref("DNSCryptVersion");

        tvDNSCryptLog = view.findViewById(R.id.tvDNSCryptLog);
        tvDNSCryptLog.setText(getText(R.string.tvDNSDefaultLog) + " " + currentDNSCryptVersion);
        tvDNSCryptLog.setMovementMethod(ScrollingMovementMethod.getInstance());

        tvDNSStatus = view.findViewById(R.id.tvDNSStatus);

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
        dnscryptPath = pathVars.dnscryptPath;
        busyboxPath = pathVars.busyboxPath;

        modulesStatus = ModulesStatus.getInstance();

        logFile = new OwnFileReader(getActivity(), appDataDir + "/logs/DnsCrypt.log");

        if (isDNSCryptInstalled()) {
            setDNSCryptInstalled(true);

            if (modulesStatus.getDnsCryptState() == STOPPING){
                setDnsCryptStopping();

                if (logFile != null) {
                    tvDNSCryptLog.setText(Html.fromHtml(logFile.readLastLines()));
                }

                displayLog(1000);
            } else if (isSavedDNSStatusRunning()) {
                setDnsCryptRunning();

                if (logFile != null) {
                    tvDNSCryptLog.setText(Html.fromHtml(logFile.readLastLines()));
                }

                if (modulesStatus.getDnsCryptState() != RESTARTING) {
                    modulesStatus.setDnsCryptState(RUNNING);
                }

                displayLog(1000);

            } else {
                setDnsCryptStopped();
                modulesStatus.setDnsCryptState(STOPPED);
            }

        } else {
            setDNSCryptInstalled(false);
        }


        if (getActivity() != null) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            getActivity().registerReceiver(br, intentFilterBckgIntSer);
            getActivity().registerReceiver(br, intentFilterTopFrg);

            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
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
            Log.e(LOG_TAG, "DNSCryptRunFragment onStop exception " + e.getMessage() + " " + e.getCause());
        }
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


        if (v.getId() == R.id.btnDNSCryptStart) {

            setStartButtonEnabled(false);

            cleanLogFileNoRootMethod();

            boolean rootMode = modulesStatus.getMode() == ROOT_MODE;


            if (new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()) {
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_SHORT).show();
                    setStartButtonEnabled(true);
                    return;
                }

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

                setDnsCryptStarting();

                runDNSCrypt();

                displayLog(1000);
            } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()) {
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_SHORT).show();
                    setStartButtonEnabled(true);
                    return;
                }

                if (rootMode && getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_dnscrypt).toString(), "dnscrypt");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }
                setDnsCryptStarting();

                runDNSCrypt();

                displayLog(1000);
            } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {
                setDnsCryptStopping();
                stopDNSCrypt();
            } else if (new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (rootMode && getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_tor).toString(), "tor");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

                setDnsCryptStopping();
                stopDNSCrypt();
            }

            setProgressBarIndeterminate(true);
        }

    }


    private void checkDNSVersionWithRoot() {
        if (isDNSCryptInstalled() && getActivity() != null) {

            String[] commandsCheck = {
                    busyboxPath + "pgrep -l /dnscrypt-proxy",
                    busyboxPath + "echo 'checkDNSRunning'",
                    busyboxPath + "echo 'DNSCrypt_version'",
                    dnscryptPath + " --version"
            };
            RootCommands rootCommands = new RootCommands(commandsCheck);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.DNSCryptRunFragmentMark);
            RootExecService.performAction(getActivity(), intent);

            setProgressBarIndeterminate(true);
        }
    }

    private void refreshDNSCryptState() {

        if (modulesStatus == null) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getDnsCryptState();

        if (currentModuleState.equals(fixedModuleState) && currentModuleState != STOPPED) {
            return;
        }

        if (currentModuleState == STARTING) {

            displayLog(1000);

        } else if (currentModuleState == RUNNING) {

            setStartButtonEnabled(true);

            saveDNSStatusRunning(true);

            btnDNSCryptStart.setText(R.string.btnDNSCryptStop);

        } else if (currentModuleState == STOPPED) {

            if (isSavedDNSStatusRunning()) {
                setDNSCryptStoppedBySystem();
            } else {
                setDnsCryptStopped();
            }

            setProgressBarIndeterminate(false);

            stopDisplayLog();

            if (getActivity() != null) {
                new PrefManager(getActivity()).setBoolPref("DNSCryptFallBackResolverRemoved", false);
            }

            saveDNSStatusRunning(false);

            setStartButtonEnabled(true);
        }

        fixedModuleState = currentModuleState;
    }

    private void setDnsCryptStarting() {
        setDNSCryptStatus(R.string.tvDNSStarting, R.color.textModuleStatusColorStarting);
    }

    private void setDnsCryptRunning() {
        setDNSCryptStatus(R.string.tvDNSRunning, R.color.textModuleStatusColorRunning);
        btnDNSCryptStart.setText(R.string.btnDNSCryptStop);
    }

    private void setDnsCryptStopping() {
        setDNSCryptStatus(R.string.tvDNSStopping, R.color.textModuleStatusColorStopping);
    }

    private void setDnsCryptStopped() {
        setDNSCryptStatus(R.string.tvDNSStop, R.color.textModuleStatusColorStopped);
        btnDNSCryptStart.setText(R.string.btnDNSCryptStart);
        String tvDNSCryptLogText = getText(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion;
        tvDNSCryptLog.setText(tvDNSCryptLogText);
    }

    private void setDNSCryptStoppedBySystem() {

        setDnsCryptStopped();

        if (getActivity() != null) {

            modulesStatus.setDnsCryptState(STOPPED);

            ModulesAux.requestModulesStatusUpdate(getActivity());

            if (getFragmentManager() != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_dnscrypt_stopped).toString(), "dnscrypt_suddenly_stopped");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }
            }

            Log.e(LOG_TAG, getText(R.string.helper_dnscrypt_stopped).toString());
        }

    }

    private void setDNSCryptInstalled(boolean installed) {
        if (installed) {
            btnDNSCryptStart.setEnabled(true);
        } else {
            tvDNSStatus.setText(getText(R.string.tvDNSNotInstalled));
        }
    }

    private void setDnsCryptSomethingWrong() {
        setDNSCryptStatus(R.string.wrong, R.color.textModuleStatusColorAlert);
        modulesStatus.setDnsCryptState(FAULT);
    }

    private boolean isDNSCryptInstalled() {
        if (getActivity() != null) {
            return new PrefManager(getActivity()).getBoolPref("DNSCrypt Installed");
        }
        return false;
    }

    private boolean isSavedDNSStatusRunning() {
        if (getActivity() != null) {
            return new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");
        }
        return false;
    }

    private void saveDNSStatusRunning(boolean running) {
        if (getActivity() != null) {
            new PrefManager(getActivity()).setBoolPref("DNSCrypt Running", running);
        }
    }

    public void setDNSCryptStatus(int resourceText, int resourceColor) {
        tvDNSStatus.setText(resourceText);
        tvDNSStatus.setTextColor(getResources().getColor(resourceColor));
    }

    public void setStartButtonEnabled(boolean enabled) {
        if (btnDNSCryptStart.isEnabled() && !enabled) {
            btnDNSCryptStart.setEnabled(false);
        } else if (!btnDNSCryptStart.isEnabled() && enabled) {
            btnDNSCryptStart.setEnabled(true);
        }
    }

    public void setProgressBarIndeterminate(boolean indeterminate) {
        if (!pbDNSCrypt.isIndeterminate() && indeterminate) {
            pbDNSCrypt.setIndeterminate(true);
        } else if (pbDNSCrypt.isIndeterminate() && !indeterminate){
            pbDNSCrypt.setIndeterminate(false);
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

                        refreshDNSCryptState();

                        if (!previousLastLines.contentEquals(lastLines)) {

                            dnsCryptStartedSuccessfully(lastLines);

                            dnsCryptStartedWithError(lastLines);

                            tvDNSCryptLog.setText(Html.fromHtml(lastLines));
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

    private void dnsCryptStartedSuccessfully(String lines) {

        if (modulesStatus.getDnsCryptState() == RUNNING
                && lines.contains("lowest initial latency")) {

            if (!modulesStatus.isUseModulesWithRoot()) {
                setProgressBarIndeterminate(false);
            }

            setDnsCryptRunning();

            displayLog(5000);
        }
    }

    private void dnsCryptStartedWithError(String lastLines) {

        if (getActivity() == null) {
            return;
        }

        if ((lastLines.contains("connect: connection refused")
                || lastLines.contains("ERROR"))
                && !lastLines.contains(" OK ")) {
            Log.e(LOG_TAG, "DNSCrypt Error: " + lastLines);

            if (getFragmentManager() != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }
            }

        } else if (lastLines.contains("[CRITICAL]") && lastLines.contains("[FATAL]")) {

            if (getFragmentManager() != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }
            }

            Log.e(LOG_TAG, "DNSCrypt FATAL Error: " + lastLines);

            stopDNSCrypt();
        }
    }

    private void runDNSCrypt() {
        if (getActivity() == null) {
            return;
        }

        ModulesRunner.runDNSCrypt(getActivity());
    }

    private void stopDNSCrypt() {
        if (getActivity() == null) {
            return;
        }

        ModulesKiller.stopDNSCrypt(getActivity());
    }

    private void cleanLogFileNoRootMethod() {
        try {
            File f = new File(appDataDir + "/logs");

            if (f.mkdirs() && f.setReadable(true) && f.setWritable(true))
                Log.i(LOG_TAG, "log dir created");

            PrintWriter writer = new PrintWriter(appDataDir + "/logs/DnsCrypt.log", "UTF-8");
            writer.println(getResources().getString(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);
            writer.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to create dnsCrypt log file " + e.getMessage());
        }
    }

}
