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

import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.modulesStarter.ModulesRunner;
import pan.alexander.tordnscrypt.utils.modulesStarter.ModulesStarterService;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Tethering;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesStatus;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.LOG_TAG;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.UPDATING;


public class DNSCryptRunFragment extends Fragment implements View.OnClickListener {

    private BroadcastReceiver br = null;
    private RootCommands rootCommands = null;
    private Button btnDNSCryptStart = null;
    private TextView tvDNSStatus = null;
    private ProgressBar pbDNSCrypt = null;
    private TextView tvDNSCryptLog = null;
    private Timer timer = null;
    private String appDataDir;
    private String dnsCryptPort;
    private String itpdHttpProxyPort;
    private String torSOCKSPort;
    private String torHTTPTunnelPort;
    private String itpdSOCKSPort;
    private String torTransPort;
    private String dnsCryptFallbackRes;
    private String torDNSPort;
    private String torVirtAdrNet;
    private String dnscryptPath;
    private String busyboxPath;
    private String iptablesPath;
    private String rejectAddress;
    private boolean runDNSCryptWithRoot = false;
    private Tethering tethering;
    private boolean routeAllThroughTor = true;
    private boolean blockHttp = false;

    private OwnFileReader logFile;

    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState;


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


                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        if (comResult.getCommands().length == 0) {
                            setDnsCryptSomethingWrong();
                            setProgressBarIndeterminate(false);
                            return;
                        }

                        lockDrawer(false);

                        StringBuilder sb = new StringBuilder();
                        for (String com : comResult.getCommands()) {
                            Log.i(LOG_TAG, com);
                            sb.append(com).append((char) 10);
                        }

                        //TopFragment.NotificationDialogFragment commandResult = TopFragment.NotificationDialogFragment.newInstance(sb.toString());
                        //commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);

                        if (sb.toString().contains("DNSCrypt_version")) {
                            String[] strArr = sb.toString().split("DNSCrypt_version");
                            if (strArr.length > 1 && strArr[1].trim().matches("\\d+\\.\\d+\\.\\d+")) {
                                DNSCryptVersion = strArr[1].trim();
                                new PrefManager(getActivity()).setStrPref("DNSCryptVersion", DNSCryptVersion);
                                setProgressBarIndeterminate(false);

                                if (!modulesStatus.isUseModulesWithRoot()) {

                                    if (!isSavedDNSStatusRunning()) {
                                        String tvTorLogText = getText(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion;
                                        tvDNSCryptLog.setText(tvTorLogText);
                                    }

                                    refreshDNSCryptState();
                                }
                            }
                        }

                        if (sb.toString().toLowerCase().contains(dnscryptPath)
                                && sb.toString().contains("checkDNSRunning")) {
                            setDnsCryptRunning();
                            saveDNSStatusRunning(true);
                        } else if (!sb.toString().toLowerCase().contains(dnscryptPath)
                                && sb.toString().contains("checkDNSRunning")) {
                            if (modulesStatus.getDnsCryptState() == STOPPING) {
                                saveDNSStatusRunning(false);
                            }
                            stopDisplayLog();
                            setDnsCryptStopped();
                            modulesStatus.setDnsCryptState(STOPPED);
                            refreshDNSCryptState();
                            setProgressBarIndeterminate(false);
                        } else if (sb.toString().contains("Something went wrong!")) {
                            setDnsCryptSomethingWrong();
                            setProgressBarIndeterminate(false);
                        }

                    }

                    if (action.equals(TOP_BROADCAST)) {
                        if (TOP_BROADCAST.contains("TOP_BROADCAST")) {
                            Log.i(LOG_TAG, "DNSCryptRunFragment onReceive TOP_BROADCAST");

                            checkDNSVersion();
                        }
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Verifier verifier = new Verifier(getActivity());
                                    String appSignAlt = verifier.getApkSignature();
                                    if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                                getActivity(), getText(R.string.verifier_error).toString(), "15");
                                        if (notificationHelper != null && getFragmentManager() != null) {
                                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                                        }
                                    }

                                } catch (Exception e) {
                                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                            getActivity(), getText(R.string.verifier_error).toString(), "18");
                                    if (notificationHelper != null && getFragmentManager() != null) {
                                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
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
        dnsCryptPort = pathVars.dnsCryptPort;
        itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        torTransPort = pathVars.torTransPort;
        dnsCryptFallbackRes = pathVars.dnsCryptFallbackRes;
        torDNSPort = pathVars.torDNSPort;
        torVirtAdrNet = pathVars.torVirtAdrNet;
        dnscryptPath = pathVars.dnscryptPath;
        busyboxPath = pathVars.busyboxPath;
        iptablesPath = pathVars.iptablesPath;
        torSOCKSPort = pathVars.torSOCKSPort;
        torHTTPTunnelPort = pathVars.torHTTPTunnelPort;
        itpdSOCKSPort = pathVars.itpdSOCKSPort;
        rejectAddress = pathVars.rejectAddress;

        modulesStatus = ModulesStatus.getInstance();

        logFile = new OwnFileReader(appDataDir + "/logs/DnsCrypt.log");

        if (isDNSCryptInstalled()) {
            setDNSCryptInstalled(true);

            if (isSavedDNSStatusRunning()) {
                setDnsCryptRunning();

                if (logFile != null) {
                    tvDNSCryptLog.setText(Html.fromHtml(logFile.readLastLines()));
                }

                modulesStatus.setDnsCryptState(RUNNING);
                displayLog(1000);
            } else {
                setDnsCryptStopped();
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
            runDNSCryptWithRoot = shPref.getBoolean("swUseModulesRoot", false);

            routeAllThroughTor = shPref.getBoolean("pref_fast_all_through_tor", true);
            blockHttp = shPref.getBoolean("pref_fast_block_http", false);
        }

        /////////////////////////// HOTSPOT///////////////////////////////////
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


        if (v.getId() == R.id.btnDNSCryptStart) {
            String[] commandsDNS = new String[]{"echo 'Something went wrong!'"};
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

            String startCommandDNSCrypt = "";
            String killall = busyboxPath + "killall dnscrypt-proxy";
            String appUID = new PrefManager(getActivity()).getStrPref("appUID");
            String restoreUID = busyboxPath + "chown -R " + appUID + "." + appUID + " " + appDataDir + "/app_data/dnscrypt-proxy";
            String restoreSEContext = "restorecon -R " + appDataDir + "/app_data/dnscrypt-proxy";

            if (runDNSCryptWithRoot) {
                //startCommandDNSCrypt = busyboxPath+ "nohup " + dnscryptPath+" --config "+appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 &";
                killall = busyboxPath + "killall dnscrypt-proxy";
                restoreUID = busyboxPath + "chown -R 0.0 " + appDataDir + "/app_data/dnscrypt-proxy";
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


            if (new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                if (!routeAllThroughTor) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_dnscrypt_tor).toString(), "dnscrypt_tor");
                    if (notificationHelper != null && getFragmentManager() != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }

                    commandsDNS = new String[]{
                            killall,
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath + "sleep 1",
                            restoreUID,
                            restoreSEContext,
                            busyboxPath + "echo 'Beginning of log' > " + appDataDir + "/logs/DnsCrypt.log",
                            busyboxPath + "sleep 1",
                            startCommandDNSCrypt,
                            busyboxPath + "sleep 1",
                            iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT",
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
                            busyboxPath + "pgrep -l /dnscrypt-proxy",
                            busyboxPath + "echo 'checkDNSRunning'",
                            busyboxPath + "echo 'startProcess'"
                    };
                } else {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.helper_dnscrypt_tor_privacy).toString(), "dnscrypt_tor_privacy");
                    if (notificationHelper != null && getFragmentManager() != null) {
                        notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                    }

                    commandsDNS = new String[]{
                            killall,
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath + "sleep 1",
                            restoreUID,
                            restoreSEContext,
                            busyboxPath + "echo 'Beginning of log' > " + appDataDir + "/logs/DnsCrypt.log",
                            busyboxPath + "sleep 1",
                            startCommandDNSCrypt,
                            busyboxPath + "sleep 1",
                            "TOR_UID=" + appUID,
                            iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT",
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
                            busyboxPath + "pgrep -l /dnscrypt-proxy",
                            busyboxPath + "echo 'checkDNSRunning'",
                            busyboxPath + "echo 'startProcess'"
                    };
                }


                setDnsCryptStarting();

                runDNSCrypt();

                displayLog(1000);
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsDNS = Arr.ADD2(commandsDNS, commandsTether);
            } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_dnscrypt).toString(), "dnscrypt");
                if (notificationHelper != null && getFragmentManager() != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }

                commandsDNS = new String[]{
                        killall,
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",
                        restoreUID,
                        restoreSEContext,
                        busyboxPath + "echo 'Beginning of log' > " + appDataDir + "/logs/DnsCrypt.log",
                        busyboxPath + "sleep 1",
                        startCommandDNSCrypt,
                        busyboxPath + "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT",
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
                        busyboxPath + "pgrep -l /dnscrypt-proxy",
                        busyboxPath + "echo 'checkDNSRunning'",
                        busyboxPath + "echo 'startProcess'"};
                setDnsCryptStarting();

                runDNSCrypt();

                displayLog(1000);
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsDNS = Arr.ADD2(commandsDNS, commandsTether);
            } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {
                commandsDNS = new String[]{
                        killall,
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -A tordnscrypt -j RETURN",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 3",
                        busyboxPath + "pgrep -l /dnscrypt-proxy",
                        busyboxPath + "echo 'checkDNSRunning'",
                        busyboxPath + "echo 'stopProcess'"};
                setDnsCryptStopping();
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsDNS = Arr.ADD2(commandsDNS, commandsTether);
            } else if (new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_tor).toString(), "tor");
                if (notificationHelper != null && getFragmentManager() != null) {
                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                }

                commandsDNS = new String[]{
                        killall,
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
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
                        busyboxPath + "pgrep -l /dnscrypt-proxy",
                        busyboxPath + "echo 'checkDNSRunning'",
                        busyboxPath + "echo 'stopProcess'"};
                setDnsCryptStopping();
                String[] commandsTether = tethering.activateTethering(true);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsDNS = Arr.ADD2(commandsDNS, commandsTether);
            }
            rootCommands = new RootCommands(commandsDNS);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.DNSCryptRunFragmentMark);
            RootExecService.performAction(getActivity(), intent);

            setProgressBarIndeterminate(true);
        }

    }


    private void checkDNSVersion() {
        if (isDNSCryptInstalled() && getActivity() != null) {

            String[] commandsCheck = {
                    busyboxPath + "pgrep -l /dnscrypt-proxy",
                    busyboxPath + "echo 'checkDNSRunning'",
                    busyboxPath + "echo 'DNSCrypt_version'",
                    dnscryptPath + " --version"
            };
            rootCommands = new RootCommands(commandsCheck);
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

            setDnsCryptStarting();

            displayLog(1000);

        } else if (currentModuleState == RUNNING) {

            setProgressBarIndeterminate(false);

            setDnsCryptRunning();

            displayLog(5000);

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

            safeStopModulesStarterService();
        }

        fixedModuleState = currentModuleState;
    }

    private void setDnsCryptStarting() {
        setDNSCryptStatus(R.string.tvDNSStarting, R.color.textModuleStatusColorStarting);
        modulesStatus.setDnsCryptState(STARTING);
    }

    private void setDnsCryptRunning() {
        setDNSCryptStatus(R.string.tvDNSRunning, R.color.textModuleStatusColorRunning);
        btnDNSCryptStart.setText(R.string.btnDNSCryptStop);
    }

    private void setDnsCryptStopping() {
        setDNSCryptStatus(R.string.tvDNSStopping, R.color.textModuleStatusColorStopping);
        modulesStatus.setDnsCryptState(STOPPING);
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

            new PrefManager(getActivity()).setBoolPref("Tor Running", false);
            modulesStatus.setTorState(STOPPED);

            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                    getActivity(), getText(R.string.helper_dnscrypt_stopped).toString(), "dnscrypt_suddenly_stopped");
            if (notificationHelper != null && getFragmentManager() != null) {
                notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
            }

            Log.e(LOG_TAG, getText(R.string.helper_dnscrypt_stopped).toString());

            String[] commandsReset = new String[]{
                    busyboxPath + "echo 'stopProcess'",
                    busyboxPath + "killall dnscrypt-proxy",
                    busyboxPath + "killall tor",
                    busyboxPath + "sleep 3",
                    iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                    iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                    iptablesPath + "iptables -F tordnscrypt",
                    iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
            };
            rootCommands = new RootCommands(commandsReset);
            Intent intentReset = new Intent(getActivity(), RootExecService.class);
            intentReset.setAction(RootExecService.RUN_COMMAND);
            intentReset.putExtra("Commands", rootCommands);
            intentReset.putExtra("Mark", RootExecService.TorRunFragmentMark);
            RootExecService.performAction(getActivity(), intentReset);
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
        btnDNSCryptStart.setEnabled(enabled);
    }

    public void setProgressBarIndeterminate(boolean indeterminate) {
        if (!pbDNSCrypt.isIndeterminate() && indeterminate) {
            pbDNSCrypt.setIndeterminate(true);
        } else if (pbDNSCrypt.isIndeterminate() && !indeterminate){
            pbDNSCrypt.setIndeterminate(false);
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
            timer.cancel();
            timer = null;
        }
    }

    private void dnsCryptStartedSuccessfully(String lines) {
        if (modulesStatus.getDnsCryptState() == RESTARTING
                || modulesStatus.getDnsCryptState() == UPDATING) {
            return;
        }

        if (modulesStatus.getDnsCryptState() != STARTING
                && !isSavedDNSStatusRunning()) {
            return;
        }

        if (lines.contains("lowest initial latency")) {
            modulesStatus.setDnsCryptState(RUNNING);
        }
    }

    private void dnsCryptStartedWithError(String lastLines) {

        if (getActivity() == null) {
            return;
        }

        String[] commands = null;

        if ((lastLines.contains("connect: connection refused")
                || lastLines.contains("ERROR"))
                && !lastLines.contains(" OK ")) {
            Log.e(LOG_TAG, "DNSCrypt Error: " + lastLines);
            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                    getActivity(), getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
            if (notificationHelper != null && getFragmentManager() != null) {
                notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
            }

        } else if (lastLines.contains("dnscrypt-proxy is ready")
                && !new PrefManager(getActivity()).getBoolPref("DNSCryptFallBackResolverRemoved")) {

            new PrefManager(getActivity()).setBoolPref("DNSCryptFallBackResolverRemoved", true);
            commands = new String[]{iptablesPath + "iptables -t nat -D tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT || true"};

        } else if (lastLines.contains("[CRITICAL]") && lastLines.contains("[FATAL]")) {

            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                    getActivity(), getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
            if (notificationHelper != null && getFragmentManager() != null) {
                notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
            }

            Log.e(LOG_TAG, "DNSCrypt FATAL Error: " + lastLines);

            commands = new String[]{
                    busyboxPath + "killall dnscrypt-proxy",
                    busyboxPath + "killall tor",
                    busyboxPath + "killall i2pd",
                    iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                    iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                    iptablesPath + "iptables -F tordnscrypt",
                    iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true"
            };
        }

        if (commands != null) {
            RootCommands rootCommands = new RootCommands(commands);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.NullMark);
            RootExecService.performAction(getActivity(), intent);
        }
    }

    private void runDNSCrypt() {
        if (getActivity() == null) {
            return;
        }

        ModulesRunner.runDNSCrypt(getActivity());
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
