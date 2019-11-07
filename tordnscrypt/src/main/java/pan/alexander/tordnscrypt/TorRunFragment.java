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
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
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

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.modulesStarter.ModulesRunner;
import pan.alexander.tordnscrypt.utils.modulesStarter.ModulesStarterService;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Tethering;
import pan.alexander.tordnscrypt.utils.TorRefreshIPsWork;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesStatus;

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
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.UPDATING;


public class TorRunFragment extends Fragment implements View.OnClickListener {

    BroadcastReceiver br = null;
    RootCommands rootCommands = null;
    Button btnTorStart = null;
    TextView tvTorStatus = null;
    ProgressBar pbTor = null;
    TextView tvTorLog = null;
    Timer timer = null;
    String appDataDir;
    String dnsCryptPort;
    String itpdHttpProxyPort;
    public String torSOCKSPort;
    public String torHTTPTunnelPort;
    public String itpdSOCKSPort;
    String torTransPort;
    String torDNSPort;
    String torVirtAdrNet;
    String dnscryptPath;
    String torPath;
    String itpdPath;
    String obfsPath;
    String busyboxPath;
    String iptablesPath;
    String rejectAddress;
    int mJobId = PreferencesFastFragment.mJobId;
    int refreshPeriodHours = 12;
    boolean runTorWithRoot = false;
    Tethering tethering;
    boolean routeAllThroughTor = true;
    boolean blockHttp = false;

    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState = STOPPED;
    private ModuleState currentModuleState;

    private OwnFileReader logFile;


    public TorRunFragment() {
        // Required empty public constructor
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


                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        if (comResult.getCommands().length == 0) {
                            setTorSomethingWrong();
                            setProgressBarIndeterminate(false);
                            return;
                        }

                        lockDrawer(false);

                        StringBuilder sb = new StringBuilder();
                        for (String com : comResult.getCommands()) {
                            Log.i(LOG_TAG, com);
                            sb.append(com).append((char) 10);
                        }

                        if (sb.toString().contains("Tor_version")) {
                            String[] strArr = sb.toString().split("Tor_version");
                            if (strArr.length > 1) {
                                String[] verArr = strArr[1].trim().split(" ");
                                if (verArr.length > 2 && verArr[1].contains("version")) {
                                    TorVersion = verArr[2].trim();
                                    new PrefManager(getActivity()).setStrPref("TorVersion", TorVersion);
                                    setProgressBarIndeterminate(false);

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
                            /////////////For correct display tor bootstrap/////////////////////
                            saveTorStatusRunning(true);
                            setProgressBarIndeterminate(false);
                            startRefreshTorUnlockIPs();
                        } else if (!sb.toString().toLowerCase().contains(torPath)
                                && sb.toString().contains("checkTrRunning")) {
                            if (modulesStatus.getTorState() == STOPPING) {
                                saveTorStatusRunning(false);
                            }
                            stopDisplayLog();
                            setTorStopped();
                            modulesStatus.setTorState(STOPPED);
                            refreshTorState();
                            pbTor.setProgress(0);
                        } else if (sb.toString().contains("Something went wrong!")) {
                            setTorSomethingWrong();
                            setProgressBarIndeterminate(false);
                        }

                    }

                    if (action.equals(TopFragment.TOP_BROADCAST)) {
                        if (TopFragment.TOP_BROADCAST.contains("TOP_BROADCAST")) {
                            Log.i(LOG_TAG, "TorRunFragment onReceive TOP_BROADCAST");

                            checkTorVersion();
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
        // Inflate the layout for this fragment
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
        dnsCryptPort = pathVars.dnsCryptPort;
        itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        torSOCKSPort = pathVars.torSOCKSPort;
        torHTTPTunnelPort = pathVars.torHTTPTunnelPort;
        itpdSOCKSPort = pathVars.itpdSOCKSPort;
        torTransPort = pathVars.torTransPort;
        torDNSPort = pathVars.torDNSPort;
        torVirtAdrNet = pathVars.torVirtAdrNet;
        dnscryptPath = pathVars.dnscryptPath;
        torPath = pathVars.torPath;
        itpdPath = pathVars.itpdPath;
        obfsPath = pathVars.obfsPath;
        busyboxPath = pathVars.busyboxPath;
        iptablesPath = pathVars.iptablesPath;
        rejectAddress = pathVars.rejectAddress;

        modulesStatus = ModulesStatus.getInstance();

        logFile = new OwnFileReader(appDataDir + "/logs/Tor.log");

        if (isTorInstalled()) {
            setTorInstalled(true);

            if (isSavedTorStatusRunning()) {
                setTorRunning();

                if (logFile != null) {
                    tvTorLog.setText(Html.fromHtml(logFile.readLastLines()));
                }

                modulesStatus.setTorState(RUNNING);
                displayLog(1000);
            } else {
                setTorStopped();
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
            if (refreshPeriod != null) {
                refreshPeriodHours = Integer.parseInt(refreshPeriod);
            }

            runTorWithRoot = shPref.getBoolean("swUseModulesRoot", false);
            routeAllThroughTor = shPref.getBoolean("pref_fast_all_through_tor", true);
            blockHttp = shPref.getBoolean("pref_fast_block_http", false);
        }

        /////////////////////////// HOTSPOT/////////////////////////////////////////////
        tethering = new Tethering(getActivity());

    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            stopDisplayLog();
            if (br != null) Objects.requireNonNull(getActivity()).unregisterReceiver(br);
        } catch (Exception e) {
            e.printStackTrace();
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

        if (RootExecService.lockStartStop) {
            Toast.makeText(getActivity(), getText(R.string.please_wait), Toast.LENGTH_SHORT).show();
            return;
        }

        lockDrawer(true);

        if (((MainActivity) getActivity()).childLockActive) {
            Toast.makeText(getActivity(), getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return;
        }


        if (v.getId() == R.id.btnTorStart) {
            String[] commandsTor = new String[]{"echo 'Something went wrong!'"};
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

            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Verifier verifier = new Verifier(getActivity());
                        String appSign = verifier.getApkSignatureZipModern();
                        String appSignAlt = verifier.getApkSignature();
                        if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                    getActivity(), getText(R.string.verifier_error).toString(), "15");
                            if (notificationHelper != null) {
                                if (getFragmentManager() != null) {
                                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                                }
                            }
                        }

                    } catch (Exception e) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(), getText(R.string.verifier_error).toString(), "18");
                        if (notificationHelper != null) {
                            if (getFragmentManager() != null) {
                                notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                            }
                        }
                        Log.e(TopFragment.LOG_TAG, "TorRunFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                                Arrays.toString(e.getStackTrace()));
                    }
                }
            });
            thread.start();

            String startCommandTor = "";
            String killall = busyboxPath + "killall tor";
            String appUID = new PrefManager(getActivity()).getStrPref("appUID");
            String restoreUID = busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/tor_data";
            String restoreSEContext = "restorecon -R " + appDataDir + "/tor_data";
            if (runTorWithRoot) {
                //startCommandTor = torPath + " -f " + appDataDir + "/app_data/tor/tor.conf";
                killall = busyboxPath + "killall tor";
                restoreUID = busyboxPath + "chown -R 0.0 " + appDataDir + "/tor_data";
                restoreSEContext = "";
                appUID = "0";
            }

            String torSitesBypassNatTCP = "";
            String torSitesBypassFilterTCP = "";
            String torSitesBypassNatUDP = "";
            String torSitesBypassFilterUDP = "";
            String torAppsBypassNatTCP = "";
            String torAppsBypassNatUDP = "";
            String torAppsBypassFilterTCP = "";
            String torAppsBypassFilterUDP = "";
            if (routeAllThroughTor) {
                torSitesBypassNatTCP = busyboxPath + "cat " + appDataDir + "/app_data/tor/clearnet | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j RETURN; done";
                torSitesBypassFilterTCP = busyboxPath + "cat " + appDataDir + "/app_data/tor/clearnet | while read var1; do " + iptablesPath + "iptables -A tordnscrypt -p tcp -d $var1 -j RETURN; done";
                torSitesBypassNatUDP = busyboxPath + "cat " + appDataDir + "/app_data/tor/clearnet | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d $var1 -j RETURN; done";
                torSitesBypassFilterUDP = busyboxPath + "cat " + appDataDir + "/app_data/tor/clearnet | while read var1; do " + iptablesPath + "iptables -A tordnscrypt -p udp -d $var1 -j RETURN; done";
                torAppsBypassNatTCP = busyboxPath + "cat " + appDataDir + "/app_data/tor/clearnetApps | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassNatUDP = busyboxPath + "cat " + appDataDir + "/app_data/tor/clearnetApps | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassFilterTCP = busyboxPath + "cat " + appDataDir + "/app_data/tor/clearnetApps | while read var1; do " + iptablesPath + "iptables -A tordnscrypt -p tcp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassFilterUDP = busyboxPath + "cat " + appDataDir + "/app_data/tor/clearnetApps | while read var1; do " + iptablesPath + "iptables -A tordnscrypt -p udp -m owner --uid-owner $var1 -j RETURN; done";
            }

            String blockHttpRuleFilterAll = "";
            String blockHttpRuleNatTCP = "";
            String blockHttpRuleNatUDP = "";
            if (blockHttp) {
                blockHttpRuleFilterAll = iptablesPath + "iptables -A tordnscrypt -d +" + rejectAddress + " -j REJECT";
                blockHttpRuleNatTCP = iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 80 -j DNAT --to-destination " + rejectAddress;
                blockHttpRuleNatUDP = iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 80 -j DNAT --to-destination " + rejectAddress;
            }

            if (!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running") &&
                    new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {


                startRefreshTorUnlockIPs();

                if (!routeAllThroughTor) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_dnscrypt_tor).toString(), "dnscrypt_tor");
                    if (notificationHelper != null) {
                        if (getFragmentManager() != null) {
                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }

                    commandsTor = new String[]{
                            killall,
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt",
                            busyboxPath + "sleep 1",
                            restoreUID,
                            restoreSEContext,
                            busyboxPath + "echo 'Beginning of log' > " + appDataDir + "/logs/Tor.log",
                            busyboxPath + "sleep 1",
                            startCommandTor,
                            busyboxPath + "sleep 1",
                            iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            iptablesPath + "iptables -N tordnscrypt",
                            iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                            blockHttpRuleFilterAll,
                            iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                            busyboxPath + "cat " + appDataDir + "/app_data/tor/unlock | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                            busyboxPath + "cat " + appDataDir + "/app_data/tor/unlockApps | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                            busyboxPath + "sleep 3",
                            busyboxPath + "pgrep -l /tor",
                            busyboxPath + "echo 'checkTrRunning'",
                            busyboxPath + "echo 'startProcess'"
                    };
                } else {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_dnscrypt_tor_privacy).toString(), "dnscrypt_tor_privacy");
                    if (notificationHelper != null) {
                        if (getFragmentManager() != null) {
                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }

                    commandsTor = new String[]{
                            killall,
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt",
                            busyboxPath + "sleep 1",
                            restoreUID,
                            restoreSEContext,
                            busyboxPath + "echo 'Beginning of log' > " + appDataDir + "/logs/Tor.log",
                            busyboxPath + "sleep 1",
                            startCommandTor,
                            busyboxPath + "sleep 1",
                            "TOR_UID=" + appUID,
                            iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            torSitesBypassNatTCP,
                            torSitesBypassNatUDP,
                            torAppsBypassNatTCP,
                            torAppsBypassNatUDP,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            iptablesPath + "iptables -N tordnscrypt",
                            iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + torSOCKSPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + torSOCKSPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + torHTTPTunnelPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + torHTTPTunnelPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + itpdSOCKSPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + itpdSOCKSPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + itpdHttpProxyPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + itpdHttpProxyPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + torTransPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -j RETURN",
                            iptablesPath + "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j RETURN",
                            blockHttpRuleFilterAll,
                            torSitesBypassFilterTCP,
                            torSitesBypassFilterUDP,
                            torAppsBypassFilterTCP,
                            torAppsBypassFilterUDP,
                            iptablesPath + "iptables -A tordnscrypt -j REJECT",
                            iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                            busyboxPath + "sleep 3",
                            busyboxPath + "pgrep -l /tor",
                            busyboxPath + "echo 'checkTrRunning'",
                            busyboxPath + "echo 'startProcess'"
                    };
                }


                setTorStarting();

                runTor();

                displayLog(1000);
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsTor = Arr.ADD2(commandsTor, commandsTether);
            } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running") &&
                    !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_tor).toString(), "tor");
                if (notificationHelper != null) {
                    if (getFragmentManager() != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

                startRefreshTorUnlockIPs();

                commandsTor = new String[]{
                        killall,
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt",
                        busyboxPath + "sleep 1",
                        restoreUID,
                        restoreSEContext,
                        busyboxPath + "echo 'Beginning of log' > " + appDataDir + "/logs/Tor.log",
                        busyboxPath + "sleep 1",
                        startCommandTor,
                        busyboxPath + "sleep 1",
                        "TOR_UID=" + appUID,
                        iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + torDNSPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + torDNSPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        torSitesBypassNatTCP,
                        torSitesBypassNatUDP,
                        torAppsBypassNatTCP,
                        torAppsBypassNatUDP,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                        iptablesPath + "iptables -N tordnscrypt",
                        iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + torSOCKSPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + torSOCKSPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + torHTTPTunnelPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + torHTTPTunnelPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + itpdSOCKSPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + itpdSOCKSPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + itpdHttpProxyPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + itpdHttpProxyPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + torTransPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + torDNSPort + " -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + torDNSPort + " -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + torDNSPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + torDNSPort + " -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j RETURN",
                        blockHttpRuleFilterAll,
                        torSitesBypassFilterTCP,
                        torSitesBypassFilterUDP,
                        torAppsBypassFilterTCP,
                        torAppsBypassFilterUDP,
                        iptablesPath + "iptables -A tordnscrypt -j REJECT",
                        iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                        busyboxPath + "sleep 3",
                        busyboxPath + "pgrep -l /tor",
                        busyboxPath + "echo 'checkTrRunning'",
                        busyboxPath + "echo 'startProcess'"
                };

                setTorStarting();

                runTor();

                displayLog(1000);
                String[] commandsTether = tethering.activateTethering(true);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsTor = Arr.ADD2(commandsTor, commandsTether);
            } else if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running") &&
                    new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                stopRefreshTorUnlockIPs();

                commandsTor = new String[]{
                        killall,
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt",
                        busyboxPath + "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        iptablesPath + "iptables -N tordnscrypt",
                        iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                        blockHttpRuleFilterAll,
                        iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                        busyboxPath + "sleep 3",
                        busyboxPath + "pgrep -l /tor",
                        busyboxPath + "echo 'checkTrRunning'",
                        busyboxPath + "echo 'stopProcess'"};

                setTorStopping();
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsTor = Arr.ADD2(commandsTor, commandsTether);
            } else if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running") &&
                    !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                stopRefreshTorUnlockIPs();

                commandsTor = new String[]{
                        killall,
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt",
                        busyboxPath + "sleep 3",
                        busyboxPath + "pgrep -l /tor",
                        busyboxPath + "echo 'checkTrRunning'",
                        busyboxPath + "echo 'stopProcess'"};

                setTorStopping();
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsTor = Arr.ADD2(commandsTor, commandsTether);
            }


            rootCommands = new RootCommands(commandsTor);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.TorRunFragmentMark);
            RootExecService.performAction(getActivity(), intent);

            setProgressBarIndeterminate(true);
        }

    }

    private void checkTorVersion() {
        if (isTorInstalled() && getActivity() != null) {

            String[] commandsCheck = {
                    busyboxPath + "pgrep -l /tor",
                    busyboxPath + "echo 'checkTrRunning'",
                    busyboxPath + "echo 'Tor_version'",
                    torPath + " --version"
            };
            rootCommands = new RootCommands(commandsCheck);
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

        currentModuleState = modulesStatus.getTorState();

        //Log.e(LOG_TAG, " Tor state " + currentModuleState + " isFresh " + modulesStatus.isFresh() + " " + fixedModuleState);

        if ((currentModuleState.equals(fixedModuleState)) && currentModuleState != STOPPED) {
            return;
        }

        if (currentModuleState == STARTING) {

            setTorStarting();

            displayLog(1000);

        } else if (currentModuleState == RUNNING) {

            setProgressBarIndeterminate(false);

            setTorRunning();

            displayLog(5000);

        } else if (currentModuleState == STOPPED) {

            if (isSavedTorStatusRunning()) {
                setTorStoppedBySystem();
            } else {
                setTorStopped();
            }

            setProgressBarIndeterminate(false);

            stopDisplayLog();

            saveTorStatusRunning(false);

            safeStopModulesStarterService();
        }

        fixedModuleState = currentModuleState;
    }

    private void setTorStarting() {
        setTorStatus(R.string.tvTorStarting, R.color.textModuleStatusColorStarting);
        modulesStatus.setTorState(STARTING);
    }

    private void setTorRunning() {
        setTorStatus(R.string.tvTorRunning, R.color.textModuleStatusColorRunning);
        btnTorStart.setText(R.string.btnTorStop);
    }

    private void setTorStopping() {
        setTorStatus(R.string.tvTorStopping, R.color.textModuleStatusColorStopping);
        modulesStatus.setTorState(STOPPING);
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

            new PrefManager(getActivity()).setBoolPref("DNSCrypt Running", false);
            modulesStatus.setDnsCryptState(STOPPED);

            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                    getActivity(), getText(R.string.helper_tor_stopped).toString(), "tor_suddenly_stopped");
            if (notificationHelper != null) {
                if (getFragmentManager() != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }
            }

            Log.e(LOG_TAG, getText(R.string.helper_tor_stopped).toString());

            String[] commandsReset = new String[]{
                    busyboxPath + "echo 'stopProcess'",
                    busyboxPath + "killall dnscrypt-proxy",
                    busyboxPath + "killall tor",
                    busyboxPath + "sleep 3",
                    iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                    iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output",
                    iptablesPath + "iptables -F tordnscrypt",
                    iptablesPath + "iptables -D OUTPUT -j tordnscrypt",
                    //busyboxPath + "echo 'checkTrRunning'"
            };
            rootCommands = new RootCommands(commandsReset);
            Intent intentReset = new Intent(getActivity(), RootExecService.class);
            intentReset.setAction(RootExecService.RUN_COMMAND);
            intentReset.putExtra("Commands", rootCommands);
            intentReset.putExtra("Mark", RootExecService.TorRunFragmentMark);
            RootExecService.performAction(getActivity(), intentReset);
        }

    }

    private void setTorInstalled(boolean installed) {
        if (installed) {
            btnTorStart.setEnabled(true);
        } else {
            tvTorStatus.setText(getText(R.string.tvDNSNotInstalled));
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
        btnTorStart.setEnabled(enabled);
    }

    public void setProgressBarIndeterminate(boolean indeterminate) {
        if (!pbTor.isIndeterminate() && indeterminate) {
            pbTor.setIndeterminate(true);
        } else if (pbTor.isIndeterminate() && !indeterminate){
            pbTor.setIndeterminate(false);
        }

    }

    private void lockDrawer(boolean lock) {
        if (getActivity() == null) {
            return;
        }

        if (lock) {
            DrawerLayout mDrawerLayout = getActivity().findViewById(R.id.drawer_layout);
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            DrawerLayout mDrawerLayout = getActivity().findViewById(R.id.drawer_layout);
            mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
    }





    private void displayLog(int period) {

        stopDisplayLog();

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
            timer.cancel();
            timer = null;
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

        if (pbTor.isIndeterminate()) {
            return;
        }

        if (0 <= perc && perc < 100) {

            if (modulesStatus.getTorState() == STOPPED) {
                return;
            }

            pbTor.setProgress(perc);
            setTorStarting();
        } else if (modulesStatus.getTorState() == STARTING || isSavedTorStatusRunning()) {

            if (modulesStatus.getTorState() == RESTARTING
                    || modulesStatus.getTorState() == UPDATING) {
                return;
            }

            pbTor.setProgress(0);
            modulesStatus.setTorState(RUNNING);
            new PrefManager(Objects.requireNonNull(getActivity())).setBoolPref("Tor Ready", true);

            /////////////////Check Updates///////////////////////////////////////////////
            checkInvizibleUpdates();
        }
    }

    private void torStartedWithError(String lastLines) {
        if (lastLines.contains("Problem bootstrapping.") && !lastLines.contains("Bootstrapped")) {

            Log.e(LOG_TAG, "Problem bootstrapping Tor: " + lastLines);

            if (lastLines.contains("Stuck at 0%") || lastLines.contains("Stuck at 5%")) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                if (notificationHelper != null) {
                    if (getFragmentManager() != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }
            } else {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_tor_use_bridges).toString(), "helper_tor_use_bridges");
                if (notificationHelper != null) {
                    if (getFragmentManager() != null) {
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
            if (fm != null) {
                TopFragment topFragment = (TopFragment) fm.findFragmentByTag("topFragmentTAG");
                if (topFragment != null) {
                    topFragment.checkUpdates();
                }
            }
        }
    }

    /*private void checkINetAvailable(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("http://www.google.com/");
                    HttpURLConnection huc =  (HttpURLConnection)  url.openConnection ();
                    huc.setRequestMethod ("GET");  //OR  huc.setRequestMethod ("HEAD");
                    huc.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; " +
                            "Windows NT 6.0; en-US; rv:1.9.1.2) Gecko/20090729 Firefox/3.5.2 (.NET CLR 3.5.30729)");
                    huc.connect () ;
                    int code = huc.getResponseCode();
                    huc.disconnect();
                    if (getActivity()!=null) new PrefManager(getActivity()).setBoolPref("INet Available",code == HttpURLConnection.HTTP_OK);
                } catch (IOException e) {
                    Log.e(LOG_TAG,"checkINetAvailable function fault" + e.toString());
                }
            }
        });

        thread.startRefreshModulesStatus();
    }*/

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

        if (getActivity() == null) {
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

    private void safeStopModulesStarterService() {

        if (getActivity() == null) {
            return;
        }


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
            Intent intent = new Intent(getActivity(), ModulesStarterService.class);
            getActivity().stopService(intent);
        }
    }


}
