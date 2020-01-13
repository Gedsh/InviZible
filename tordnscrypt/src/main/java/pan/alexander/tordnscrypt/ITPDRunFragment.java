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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.TopFragment.ITPDVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;


/**
 * A simple {@link Fragment} subclass.
 */
public class ITPDRunFragment extends Fragment implements View.OnClickListener {

    private BroadcastReceiver br = null;
    private Button btnITPDStart = null;
    private TextView tvITPDStatus = null;
    private ProgressBar pbITPD = null;
    private TextView tvITPDLog = null;
    private TextView tvITPDinfoLog = null;
    private Timer timer = null;
    private String appDataDir;
    private String itpdPath;
    private String busyboxPath;
    private Boolean runI2PDWithRoot = false;

    private OwnFileReader logFile;

    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState;
    private int displayLogPeriod = -1;

    public ITPDRunFragment() {
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
                            RootExecService.I2PDRunFragmentMark) &&
                            !action.equals(TOP_BROADCAST))) return;
                    Log.i(LOG_TAG, "I2PDFragment onReceive");

                    if (action.equals(RootExecService.COMMAND_RESULT)) {

                        setProgressBarIndeterminate(false);

                        setStartButtonEnabled(true);

                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        if (comResult != null && comResult.getCommands().length == 0) {

                            setITPDSomethingWrong();
                            return;
                        }

                        StringBuilder sb = new StringBuilder();
                        if (comResult != null) {
                            for (String com : comResult.getCommands()) {
                                Log.i(LOG_TAG, com);
                                sb.append(com).append((char) 10);
                            }
                        }

                        if (sb.toString().contains("ITPD_version")) {
                            String[] strArr = sb.toString().split("ITPD_version");
                            if (strArr.length > 1) {
                                String[] verArr = strArr[1].trim().split(" ");
                                if (verArr.length > 2 && verArr[1].contains("version")) {
                                    ITPDVersion = verArr[2].trim();
                                    new PrefManager(getActivity()).setStrPref("ITPDVersion", ITPDVersion);

                                    if (!modulesStatus.isUseModulesWithRoot()) {

                                        if (!isSavedITPDStatusRunning()) {
                                            String tvITPDLogText = getText(R.string.tvITPDDefaultLog) + " " + ITPDVersion;
                                            tvITPDLog.setText(tvITPDLogText);
                                        }

                                        refreshITPDState();
                                    }
                                }
                            }
                        }

                        if (sb.toString().toLowerCase().contains(itpdPath)
                                && sb.toString().contains("checkITPDRunning")) {

                            setITPDRunning();
                            saveITPDStatusRunning(true);
                            modulesStatus.setItpdState(RUNNING);
                            displayLog(10000);

                        } else if (!sb.toString().toLowerCase().contains(itpdPath)
                                && sb.toString().contains("checkITPDRunning")) {
                            if (modulesStatus.getItpdState() == STOPPED) {
                                saveITPDStatusRunning(false);
                            }
                            stopDisplayLog();
                            setITPDStopped();
                            modulesStatus.setItpdState(STOPPED);
                            refreshITPDState();
                        } else if (sb.toString().contains("Something went wrong!")) {
                            setITPDSomethingWrong();
                        }

                    }

                    if (action.equals(TOP_BROADCAST)) {
                        if (TopFragment.TOP_BROADCAST.contains("TOP_BROADCAST")) {
                            checkITPDVersionWithRoot();
                            Log.i(LOG_TAG, "ITPDRunFragment onReceive TOP_BROADCAST");
                        }

                    }
                }
            }
        };

    }


    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_itpd_run, container, false);

        if (getActivity() == null) {
            return view;
        }

        btnITPDStart = view.findViewById(R.id.btnITPDStart);
        btnITPDStart.setOnClickListener(this);

        pbITPD = view.findViewById(R.id.pbITPD);

        tvITPDLog = view.findViewById(R.id.tvITPDLog);

        String currentITPDVersion = new PrefManager(getActivity()).getStrPref("ITPDVersion");

        tvITPDLog.setText(getText(R.string.tvITPDDefaultLog) + " " + currentITPDVersion);
        tvITPDLog.setMovementMethod(ScrollingMovementMethod.getInstance());

        tvITPDinfoLog = view.findViewById(R.id.tvITPDinfoLog);
        if (tvITPDinfoLog != null)
            tvITPDinfoLog.setMovementMethod(ScrollingMovementMethod.getInstance());

        tvITPDStatus = view.findViewById(R.id.tvI2PDStatus);

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
        itpdPath = pathVars.itpdPath;
        busyboxPath = pathVars.busyboxPath;

        modulesStatus = ModulesStatus.getInstance();

        logFile = new OwnFileReader(getActivity(), appDataDir + "/logs/i2pd.log");

        if (isITPDInstalled()) {
            setITPDInstalled(true);

            if (modulesStatus.getItpdState() == STOPPING){
                setITPDStopping();

                if (tvITPDinfoLog != null)
                    tvITPDinfoLog.setText(Html.fromHtml(logFile.readLastLines()));

                displayLog(10000);
            } else if (isSavedITPDStatusRunning() || modulesStatus.getItpdState() == RUNNING) {
                setITPDRunning();

                if (tvITPDinfoLog != null)
                    tvITPDinfoLog.setText(Html.fromHtml(logFile.readLastLines()));

                if (modulesStatus.getItpdState() != RESTARTING) {
                    modulesStatus.setItpdState(RUNNING);
                }

                displayLog(10000);
            } else {
                setITPDStopped();
                modulesStatus.setItpdState(STOPPED);
            }
        } else {
            setITPDInstalled(false);
        }


        IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
        IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

        if (getActivity() != null) {
            getActivity().registerReceiver(br, intentFilterBckgIntSer);
            getActivity().registerReceiver(br, intentFilterTopFrg);

            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            runI2PDWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        }
    }


    @Override
    public void onStop() {
        super.onStop();

        try {
            stopDisplayLog();
            if (br != null) Objects.requireNonNull(getActivity()).unregisterReceiver(br);
        } catch (Exception e) {
            Log.e(LOG_TAG, "ITPDRunFragment onStop exception " + e.getMessage() + " " + e.getCause());
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


        if (v.getId() == R.id.btnITPDStart) {

            setStartButtonEnabled(false);

            cleanLogFileNoRootMethod();

            boolean rootMode = modulesStatus.getMode() == ROOT_MODE;

            if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running")
                    && new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()|| fixedModuleState == RUNNING) {
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_SHORT).show();
                    setStartButtonEnabled(true);
                    return;
                }

                if (rootMode && getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_tor_itpd).toString(), "tor_itpd");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

                copyCertificatesNoRootMethod();

                setITPDStarting();

                runITPD();

                displayLog(1000);
            } else if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running") &&
                    !new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()|| fixedModuleState == RUNNING) {
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_SHORT).show();
                    setStartButtonEnabled(true);
                    return;
                }


                if (rootMode && getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_itpd).toString(), "itpd");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

                copyCertificatesNoRootMethod();

                setITPDStarting();

                runITPD();

                displayLog(1000);
            } else if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running") &&
                    !new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()|| fixedModuleState == RUNNING) {
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_SHORT).show();
                    setStartButtonEnabled(true);
                    return;
                }


                if (rootMode && getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_dnscrypt_itpd).toString(), "dnscrypt_itpd");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

                copyCertificatesNoRootMethod();

                setITPDStarting();

                runITPD();

                displayLog(1000);
            } else if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running") &&
                    new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (modulesStatus.isContextUIDUpdateRequested()|| fixedModuleState == RUNNING) {
                    Toast.makeText(getActivity(), R.string.please_wait, Toast.LENGTH_SHORT).show();
                    setStartButtonEnabled(true);
                    return;
                }

                if (rootMode && getFragmentManager() != null) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_dnscrypt_tor_itpd).toString(), "dnscrypt_tor_itpd");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

                copyCertificatesNoRootMethod();

                setITPDStarting();

                runITPD();

                displayLog(1000);
            } else if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running")) {

                setITPDStopping();

                stopITPD();

                OwnFileReader ofr = new OwnFileReader(getActivity(), appDataDir + "/logs/i2pd.log");

                ofr.shortenToToLongFile();
            }

            setProgressBarIndeterminate(true);
        }

    }


    private void checkITPDVersionWithRoot() {
        if (isITPDInstalled() && getActivity() != null) {
            String[] commandsCheck = {
                    busyboxPath + "pgrep -l /i2pd",
                    busyboxPath + "echo 'checkITPDRunning'",
                    busyboxPath + "echo 'ITPD_version'",
                    itpdPath + " --version"};
            RootCommands rootCommands = new RootCommands(commandsCheck);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.I2PDRunFragmentMark);
            RootExecService.performAction(getActivity(), intent);

            setProgressBarIndeterminate(true);
        }
    }

    private void refreshITPDState() {

        if (modulesStatus == null) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getItpdState();

        if (currentModuleState.equals(fixedModuleState) && currentModuleState != STOPPED) {
            return;
        }


        if (currentModuleState == STARTING) {

            displayLog(1000);

        } else if (currentModuleState == RUNNING) {

            setITPDRunning();

            setStartButtonEnabled(true);

            saveITPDStatusRunning(true);

            setProgressBarIndeterminate(false);

        } else if (currentModuleState == STOPPED) {

            if (isSavedITPDStatusRunning()) {
                setITPDStoppedBySystem();
            } else {
                setITPDStopped();
            }

            stopDisplayLog();

            saveITPDStatusRunning(false);

            setStartButtonEnabled(true);
        }

        fixedModuleState = currentModuleState;
    }

    private void setITPDStarting() {
        setITPDStatus(R.string.tvITPDStarting, R.color.textModuleStatusColorStarting);
    }

    private void setITPDRunning() {
        setITPDStatus(R.string.tvITPDRunning, R.color.textModuleStatusColorRunning);
        btnITPDStart.setText(R.string.btnITPDStop);
    }

    private void setITPDStopping() {
        setITPDStatus(R.string.tvITPDStopping, R.color.textModuleStatusColorStopping);
    }

    private void setITPDStopped() {
        setITPDStatus(R.string.tvITPDStop, R.color.textModuleStatusColorStopped);
        btnITPDStart.setText(R.string.btnITPDStart);
        String tvITPDLogText = getText(R.string.tvITPDDefaultLog) + " " + ITPDVersion;
        tvITPDLog.setText(tvITPDLogText);

        if (tvITPDinfoLog != null)
            tvITPDinfoLog.setText("");
    }

    private void setITPDStoppedBySystem() {

        setITPDStopped();

        if (getActivity() != null) {

            modulesStatus.setItpdState(STOPPED);

            ModulesAux.requestModulesStatusUpdate(getActivity());

            if (getFragmentManager() != null) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_itpd_stopped).toString(), "itpd_suddenly_stopped");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }
            }

            Log.e(LOG_TAG, getText(R.string.helper_itpd_stopped).toString());
        }

    }

    private void setITPDInstalled(boolean installed) {
        if (installed) {
            btnITPDStart.setEnabled(true);
        } else {
            tvITPDStatus.setText(getText(R.string.tvITPDNotInstalled));
        }
    }

    private void setITPDSomethingWrong() {
        setITPDStatus(R.string.wrong, R.color.textModuleStatusColorAlert);
        modulesStatus.setItpdState(FAULT);
    }

    private boolean isITPDInstalled() {
        if (getActivity() != null) {
            return new PrefManager(getActivity()).getBoolPref("I2PD Installed");
        }
        return false;
    }

    private boolean isSavedITPDStatusRunning() {
        if (getActivity() != null) {
            return new PrefManager(getActivity()).getBoolPref("I2PD Running");
        }
        return false;
    }

    private void saveITPDStatusRunning(boolean running) {
        if (getActivity() != null) {
            new PrefManager(getActivity()).setBoolPref("I2PD Running", running);
        }
    }

    public void setITPDStatus(int resourceText, int resourceColor) {
        tvITPDStatus.setText(resourceText);
        tvITPDStatus.setTextColor(getResources().getColor(resourceColor));
    }

    public void setStartButtonEnabled(boolean enabled) {
        if (btnITPDStart.isEnabled() && !enabled) {
            btnITPDStart.setEnabled(false);
        } else if (!btnITPDStart.isEnabled() && enabled) {
            btnITPDStart.setEnabled(true);
        }
    }

    public void setProgressBarIndeterminate(boolean indeterminate) {
        if (!pbITPD.isIndeterminate() && indeterminate) {
            pbITPD.setIndeterminate(true);
        } else if (pbITPD.isIndeterminate() && !indeterminate) {
            pbITPD.setIndeterminate(false);
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

                final String lastLines = logFile.readLastLines();

                final String htmlData = readITPDStatusFromHTML();

                if (++loop > 30) {
                    loop = 0;
                    displayLog(10000);
                }

                if (getActivity() == null)
                    return;

                getActivity().runOnUiThread(() -> {

                    refreshITPDState();

                    if (tvITPDinfoLog != null && !previousLastLines.equals(lastLines)) {
                        tvITPDinfoLog.setText(Html.fromHtml(lastLines));
                        previousLastLines = lastLines;
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        tvITPDLog.setText(Html.fromHtml(htmlData, Html.FROM_HTML_MODE_LEGACY));
                    } else {
                        tvITPDLog.setText(Html.fromHtml(htmlData));
                    }
                });
            }
        }, 0, period);

    }

    private void stopDisplayLog() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;

            displayLogPeriod = -1;
        }
    }

    private String readITPDStatusFromHTML() {
        String htmlData = getResources().getString(R.string.tvITPDDefaultLog) + " " + ITPDVersion;
        try {
            StringBuilder sb = new StringBuilder();

            URL url = new URL("http://127.0.0.1:7070/");
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");  //OR  huc.setRequestMethod ("HEAD");
            huc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9.0.1; " +
                    "Mi Mi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Mobile Safari/537.36");
            huc.connect();
            int code = huc.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                huc.disconnect();
                return htmlData;
            }


            BufferedReader in;
            in = new BufferedReader(
                    new InputStreamReader(
                            url.openStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.contains("<b>Network status:</b>") || inputLine.contains("<b>Tunnel creation success rate:</b>") ||
                        inputLine.contains("<b>Received:</b> ") || inputLine.contains("<b>Sent:</b>") || inputLine.contains("<b>Transit:</b>") ||
                        inputLine.contains("<b>Routers:</b>") || inputLine.contains("<b>Client Tunnels:</b>") || inputLine.contains("<b>Uptime:</b>")) {
                    inputLine = inputLine.replace("<div class=right>", "");
                    inputLine = inputLine.replace("<br>", "<br />");
                    sb.append(inputLine);
                }
            }
            in.close();
            huc.disconnect();
            htmlData = sb.toString();


        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to read I2PD html" + e.toString());
        }

        return htmlData;
    }

    private void runITPD() {

        if (getActivity() == null) {
            return;
        }

        ModulesRunner.runITPD(getActivity());
    }

    private void stopITPD() {

        if (getActivity() == null) {
            return;
        }

        ModulesKiller.stopITPD(getActivity());
    }

    private void cleanLogFileNoRootMethod() {
        try {
            File f = new File(appDataDir + "/logs");

            if (f.mkdirs() && f.setReadable(true) && f.setWritable(true))
                Log.i(LOG_TAG, "log dir created");

            PrintWriter writer = new PrintWriter(appDataDir + "/logs/i2pd.log", "UTF-8");
            writer.println("");
            writer.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Unable to create i2pd log file " + e.getMessage());
        }
    }

    private void copyCertificatesNoRootMethod() {

        if (getActivity() == null || runI2PDWithRoot) {
            return;
        }

        final String certificateSource = appDataDir + "/app_data/i2pd/certificates";
        final String certificateFolder = appDataDir + "/i2pd_data/certificates";
        final String certificateDestination = appDataDir + "/i2pd_data";

        File certificateFolderDir = new File(certificateFolder);

        if (certificateFolderDir.isDirectory() && Objects.requireNonNull(certificateFolderDir.listFiles()).length > 0) {
            return;
        }

        new Thread(() -> {
            FileOperations.copyFolderSynchronous(getActivity(), certificateSource, certificateDestination);
            Log.i(LOG_TAG, "Copy i2p certificates");
        }).start();
    }
}
