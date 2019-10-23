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
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
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

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.NoRootService;
import pan.alexander.tordnscrypt.utils.NotificationHelper;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Tethering;

import static pan.alexander.tordnscrypt.TopFragment.ITPDVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;


/**
 * A simple {@link Fragment} subclass.
 */
public class ITPDRunFragment extends Fragment implements View.OnClickListener {

    BroadcastReceiver br = null;
    RootCommands rootCommands = null;
    Button btnITPDStart = null;
    TextView tvITPDStatus = null;
    ProgressBar pbITPD = null;
    TextView tvITPDLog = null;
    TextView tvITPDinfoLog = null;
    Timer timer = null;
    String appDataDir;
    String mediaDir;
    String dnsCryptPort;
    String itpdHttpProxyPort;
    String dnsCryptFallbackRes;
    String dnscryptPath;
    String torPath;
    String itpdPath;
    String obfsPath;
    String busyboxPath;
    String iptablesPath;
    String torTransPort;
    Boolean runI2PDWithRoot = false;
    Tethering tethering;

    public ITPDRunFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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


                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        pbITPD.setIndeterminate(false);

                        if (comResult.getCommands().length == 0) {

                            tvITPDStatus.setText(R.string.wrong);
                            tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorAlert));
                            return;
                        }

                        DrawerLayout mDrawerLayout = getActivity().findViewById(R.id.drawer_layout);
                        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

                        StringBuilder sb = new StringBuilder();
                        for (String com : comResult.getCommands()) {
                            Log.i(LOG_TAG, com);
                            sb.append(com).append((char) 10);
                        }

                        //TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
                        //commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);

                        if (sb.toString().contains("ITPD_version")) {
                            String[] strArr = sb.toString().split("ITPD_version");
                            if (strArr.length > 1) {
                                String[] verArr = strArr[1].trim().split(" ");
                                if (verArr.length > 2 && verArr[1].contains("version")) {
                                    ITPDVersion = verArr[2].trim();
                                    new PrefManager(getActivity()).setStrPref("ITPDVersion", ITPDVersion);
                                }
                            }

                        }

                        if (sb.toString().toLowerCase().contains(itpdPath)
                                && sb.toString().contains("checkITPDRunning")) {
                            tvITPDStatus.setText(R.string.tvITPDRunning);
                            tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorRunning));
                            btnITPDStart.setText(R.string.btnITPDStop);
                            new PrefManager(Objects.requireNonNull(getActivity())).setBoolPref("I2PD Running", true);
                            displayLog();
                        } else if (!sb.toString().toLowerCase().contains(itpdPath)
                                && sb.toString().contains("checkITPDRunning")) {
                            tvITPDStatus.setText(R.string.tvITPDStop);
                            tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStopped));
                            btnITPDStart.setText(R.string.btnITPDStart);
                            if (timer != null) {
                                timer.cancel();
                                timer = null;
                            }
                            tvITPDLog.setText(getText(R.string.tvITPDDefaultLog) + " " + TopFragment.ITPDVersion);
                            if (timer != null) timer.cancel();
                            if (tvITPDinfoLog != null)
                                tvITPDinfoLog.setText("");

                            if ((new PrefManager(getActivity()).getBoolPref("I2PD Running")
                                    && !sb.toString().contains("stopProcess"))
                                    || (!new PrefManager(getActivity()).getBoolPref("I2PD Running")
                                    && sb.toString().contains("startProcess"))) {
                                Log.e(LOG_TAG, getText(R.string.helper_itpd_stopped).toString());
                                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                        getActivity(), getText(R.string.helper_itpd_stopped).toString(), "itpd_suddenly_stopped");
                                if (notificationHelper != null) {
                                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                                }
                            }

                            new PrefManager(Objects.requireNonNull(getActivity())).setBoolPref("I2PD Running", false);

                            safeStopNoRootService();
                        } else if (sb.toString().contains("Something went wrong!")) {
                            tvITPDStatus.setText(R.string.wrong);
                            tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorAlert));
                        }

                    }

                    if (action.equals(TOP_BROADCAST)) {
                        if (TopFragment.TOP_BROADCAST.contains("TOP_BROADCAST")) {
                            checkITPDRunning();
                            Log.i(LOG_TAG, "ITPDRunFragment onReceive TOP_BROADCAST");
                        } else {
                            tvITPDStatus.setText(R.string.wrong);
                            tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorAlert));
                            btnITPDStart.setEnabled(false);
                            Log.i(LOG_TAG, "ITPDRunFragment onReceive wrong TOP_BROADCAST");
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
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_itpd_run, container, false);

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

        if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running")) {
            tvITPDStatus.setText(R.string.tvITPDRunning);
            tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorRunning));
            btnITPDStart.setText(R.string.btnITPDStop);

        } else {
            tvITPDStatus.setText(R.string.tvITPDStop);
            tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStopped));
            btnITPDStart.setText(R.string.btnITPDStart);
        }

        if (new PrefManager(getActivity()).getBoolPref("I2PD Installed")) {
            btnITPDStart.setEnabled(true);
        } else {
            tvITPDStatus.setText(getText(R.string.tvITPDNotInstalled));
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        mediaDir = pathVars.mediaDir;
        dnsCryptPort = pathVars.dnsCryptPort;
        itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        dnsCryptFallbackRes = pathVars.dnsCryptFallbackRes;
        dnscryptPath = pathVars.dnscryptPath;
        torPath = pathVars.torPath;
        itpdPath = pathVars.itpdPath;
        obfsPath = pathVars.obfsPath;
        busyboxPath = pathVars.busyboxPath;
        iptablesPath = pathVars.iptablesPath;
        torTransPort = pathVars.torTransPort;

        IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
        IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

        if (getActivity() != null) {
            getActivity().registerReceiver(br, intentFilterBckgIntSer);
            getActivity().registerReceiver(br, intentFilterTopFrg);

            if (new PrefManager(getActivity()).getBoolPref("I2PD Running"))
                displayLog();

            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            runI2PDWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        }

        /////////////////////////// HOTSPOT///////////////////////////////////
        tethering = new Tethering(getActivity());
    }


    @Override
    public void onStop() {
        super.onStop();

        try {
            if (timer != null) timer.cancel();
            if (br != null) Objects.requireNonNull(getActivity()).unregisterReceiver(br);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        if (RootExecService.lockStartStop) {
            Toast.makeText(getActivity(), getText(R.string.please_wait), Toast.LENGTH_SHORT).show();
            return;
        }

        DrawerLayout mDrawerLayout = getActivity().findViewById(R.id.drawer_layout);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        if (((MainActivity) getActivity()).childLockActive) {
            Toast.makeText(getActivity(), getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return;
        }


        if (v.getId() == R.id.btnITPDStart) {

            String[] commandsI2PD = new String[]{"echo 'Something went wrong!'"};
            try {
                File f = new File(appDataDir + "/logs");

                if (f.mkdirs() && f.setReadable(true) && f.setWritable(true))
                    Log.i(TopFragment.LOG_TAG, "log dir created");

                PrintWriter writer = new PrintWriter(appDataDir + "/logs/i2pd.log", "UTF-8");
                writer.println(" ");
                writer.close();
            } catch (IOException e) {
                Log.e(TopFragment.LOG_TAG, "Unable to create i2pd log file " + e.getMessage());
            }

            String startCommandI2PD = "";
            String killall = busyboxPath + "killall i2pd";
            String appUID = new PrefManager(getActivity()).getStrPref("appUID");
            String restoreUID = busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/i2pd_data";
            String restoreSEContext = "restorecon -R " + appDataDir + "/i2pd_data";
            if (runI2PDWithRoot) {
                startCommandI2PD = itpdPath + " --conf " + appDataDir + "/app_data/i2pd/i2pd.conf --datadir " + appDataDir + "/i2pd_data &";
                killall = busyboxPath + "killall i2pd";
                restoreUID = busyboxPath + "chown -R 0.0 " + appDataDir + "/i2pd_data";
                restoreSEContext = "";
            }

            if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running")
                    && new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_tor_itpd).toString(), "tor_itpd");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }

                commandsI2PD = new String[]{
                        killall,
                        busyboxPath + "mkdir -p " + appDataDir + "/i2pd_data",
                        "cd " + appDataDir + "/app_data/i2pd",
                        busyboxPath + "cp -R certificates " + appDataDir + "/i2pd_data",
                        restoreUID,
                        restoreSEContext,
                        startCommandI2PD,
                        busyboxPath + "sleep 7",
                        busyboxPath + "pgrep -l /i2pd",
                        busyboxPath + "echo 'checkITPDRunning'",
                        busyboxPath + "echo 'startProcess'"
                };

                tvITPDStatus.setText(R.string.tvITPDStarting);
                tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStarting));

                if (!runI2PDWithRoot) {
                    runITPDNoRoot();
                }
                displayLog();
                String[] commandsTether = tethering.activateTethering(true);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsI2PD = Arr.ADD2(commandsI2PD, commandsTether);
            } else if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running") &&
                    !new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_itpd).toString(), "itpd");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }

                commandsI2PD = new String[]{
                        killall,
                        busyboxPath + "mkdir -p " + appDataDir + "/i2pd_data",
                        "cd " + appDataDir + "/app_data/i2pd",
                        busyboxPath + "cp -R certificates " + appDataDir + "/i2pd_data",
                        restoreUID,
                        restoreSEContext,
                        startCommandI2PD,
                        busyboxPath + "sleep 7",
                        busyboxPath + "pgrep -l /i2pd",
                        busyboxPath + "echo 'checkITPDRunning'",
                        busyboxPath + "echo 'startProcess'"
                };

                tvITPDStatus.setText(R.string.tvITPDStarting);
                tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStarting));

                if (!runI2PDWithRoot) {
                    runITPDNoRoot();
                }
                displayLog();
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsI2PD = Arr.ADD2(commandsI2PD, commandsTether);
            } else if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running") &&
                    !new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_dnscrypt_itpd).toString(), "dnscrypt_itpd");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }

                commandsI2PD = new String[]{
                        killall,
                        busyboxPath + "mkdir -p " + appDataDir + "/i2pd_data",
                        "cd " + appDataDir + "/app_data/i2pd",
                        busyboxPath + "cp -R certificates " + appDataDir + "/i2pd_data",
                        restoreUID,
                        restoreSEContext,
                        busyboxPath + "echo 'Beginning of log' > " + appDataDir + "/logs/i2pd.log",
                        startCommandI2PD,
                        busyboxPath + "sleep 7",
                        busyboxPath + "pgrep -l /i2pd",
                        busyboxPath + "echo 'checkITPDRunning'",
                        busyboxPath + "echo 'startProcess'"
                };

                tvITPDStatus.setText(R.string.tvITPDStarting);
                tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStarting));

                if (!runI2PDWithRoot) {
                    runITPDNoRoot();
                }
                displayLog();
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsI2PD = Arr.ADD2(commandsI2PD, commandsTether);
            } else if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running") &&
                    new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_dnscrypt_tor_itpd).toString(), "dnscrypt_tor_itpd");
                if (notificationHelper != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }

                commandsI2PD = new String[]{
                        killall,
                        busyboxPath + "mkdir -p " + appDataDir + "/i2pd_data",
                        "cd " + appDataDir + "/app_data/i2pd",
                        busyboxPath + "cp -R certificates " + appDataDir + "/i2pd_data",
                        restoreUID,
                        restoreSEContext,
                        busyboxPath + "echo 'Beginning of log' > " + appDataDir + "/logs/i2pd.log",
                        startCommandI2PD,
                        busyboxPath + "sleep 7",
                        busyboxPath + "pgrep -l /i2pd",
                        busyboxPath + "echo 'checkITPDRunning'",
                        busyboxPath + "echo 'startProcess'"};

                tvITPDStatus.setText(R.string.tvITPDStarting);
                tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStarting));

                if (!runI2PDWithRoot) {
                    runITPDNoRoot();
                }
                displayLog();
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsI2PD = Arr.ADD2(commandsI2PD, commandsTether);
            } else if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Running")) {
                commandsI2PD = new String[]{
                        busyboxPath + "killall i2pd",
                        busyboxPath + "sleep 7",
                        busyboxPath + "pgrep -l /i2pd",
                        busyboxPath + "echo 'checkITPDRunning'",
                        busyboxPath + "echo 'stopProcess'"};

                tvITPDStatus.setText(R.string.tvITPDStopping);
                tvITPDStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStopping));
                OwnFileReader ofr = new OwnFileReader(appDataDir + "/logs/i2pd.log");
                ofr.shortenToToLongFile();
            }


            rootCommands = new RootCommands(commandsI2PD);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.I2PDRunFragmentMark);
            RootExecService.performAction(getActivity(), intent);

            pbITPD.setIndeterminate(true);
        }

    }


    private void checkITPDRunning() {
        if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Installed")) {
            String[] commandsCheck = {
                    busyboxPath + "pgrep -l /i2pd",
                    busyboxPath + "echo 'checkITPDRunning'",
                    busyboxPath + "echo 'ITPD_version'",
                    itpdPath + " --version"};
            rootCommands = new RootCommands(commandsCheck);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.I2PDRunFragmentMark);
            RootExecService.performAction(getActivity(), intent);

            pbITPD.setIndeterminate(true);
        }
    }

    public void setITPDStatus(int resourceText, int resourceColor) {
        tvITPDStatus.setText(resourceText);
        tvITPDStatus.setTextColor(getResources().getColor(resourceColor));
    }

    public void setStartButtonEnabled(boolean enabled) {
        btnITPDStart.setEnabled(enabled);
    }

    public void setProgressBarIndeterminate(boolean indeterminate) {
        pbITPD.setIndeterminate(indeterminate);
    }

    private void displayLog() {

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        timer = new Timer();


        timer.schedule(new TimerTask() {
            String htmlData = getResources().getString(R.string.tvITPDDefaultLog) + " " + ITPDVersion;
            String previousLastLines = "";

            @Override
            public void run() {

                OwnFileReader logFile = new OwnFileReader(appDataDir + "/logs/i2pd.log");
                final String lastLines = logFile.readLastLines();

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
                        return;
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


                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if (tvITPDinfoLog != null)
                            tvITPDinfoLog.setText(Html.fromHtml(lastLines));
                        previousLastLines = lastLines;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            tvITPDLog.setText(Html.fromHtml(htmlData, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            tvITPDLog.setText(Html.fromHtml(htmlData));
                        }
                    }
                });
            }
        }, 0, 10000);

    }

    private void runITPDNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showNotification = shPref.getBoolean("swShowNotification", true);
        Intent intent = new Intent(getActivity(), NoRootService.class);
        intent.setAction(NoRootService.actionStartITPD);
        intent.putExtra("showNotification", showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getActivity().startForegroundService(intent);
        } else {
            getActivity().startService(intent);
        }
    }

    private void safeStopNoRootService() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean rnDNSCryptWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        boolean rnTorWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        boolean rnI2PDWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");
        boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");
        boolean itpdRunning = new PrefManager(getActivity()).getBoolPref("I2PD Running");
        boolean canSafeStopService = true;
        if (!rnDNSCryptWithRoot && dnsCryptRunning) {
            canSafeStopService = false;
        } else if (!rnTorWithRoot && torRunning) {
            canSafeStopService = false;
        } else if (!rnI2PDWithRoot && itpdRunning) {
            canSafeStopService = false;
        }
        if (canSafeStopService) {
            Intent intent = new Intent(getActivity(), NoRootService.class);
            getActivity().stopService(intent);
        }
    }
}
