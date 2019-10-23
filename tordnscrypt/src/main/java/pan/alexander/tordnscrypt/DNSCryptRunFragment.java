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
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
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
import pan.alexander.tordnscrypt.utils.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.LOG_TAG;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;


public class DNSCryptRunFragment extends Fragment implements View.OnClickListener {

    BroadcastReceiver br = null;
    RootCommands rootCommands = null;
    Button btnDNSCryptStart = null;
    TextView tvDNSStatus = null;
    ProgressBar pbDNSCrypt = null;
    TextView tvDNSCryptLog = null;
    Timer timer = null;
    String appDataDir;
    String dnsCryptPort;
    String itpdHttpProxyPort;
    public String torSOCKSPort;
    public String torHTTPTunnelPort;
    public String itpdSOCKSPort;
    String torTransPort;
    String dnsCryptFallbackRes;
    String torDNSPort;
    String torVirtAdrNet;
    String dnscryptPath;
    String torPath;
    String itpdPath;
    String obfsPath;
    String busyboxPath;
    String iptablesPath;
    String rejectAddress;
    boolean runDNSCryptWithRoot = false;
    Tethering tethering;
    boolean routeAllThroughTor = true;
    boolean blockHttp = false;


    public DNSCryptRunFragment() {
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
                            RootExecService.DNSCryptRunFragmentMark) &&
                            !action.equals(TOP_BROADCAST))) return;
                    Log.i(LOG_TAG, "DNSCryptRunFragment onReceive");

                    if (action.equals(RootExecService.COMMAND_RESULT)) {


                        RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                        pbDNSCrypt.setIndeterminate(false);

                        if (comResult.getCommands().length == 0) {

                            tvDNSStatus.setText(R.string.wrong);
                            tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorAlert));
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

                        if (sb.toString().contains("DNSCrypt_version")) {
                            String[] strArr = sb.toString().split("DNSCrypt_version");
                            if (strArr.length > 1 && strArr[1].trim().matches("\\d+\\.\\d+\\.\\d+")) {
                                DNSCryptVersion = strArr[1].trim();
                                new PrefManager(getActivity()).setStrPref("DNSCryptVersion", DNSCryptVersion);
                            }
                        }

                        if (sb.toString().toLowerCase().contains(dnscryptPath)
                                && sb.toString().contains("checkDNSRunning")) {
                            /////////////For correct display dnscrypt bootstrap/////////////////////
                            if (sb.toString().contains("DNSCrypt_version")) {
                                tvDNSStatus.setText(R.string.tvDNSRunning);
                                tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorRunning));
                            } else {
                                pbDNSCrypt.setIndeterminate(true);
                            }
                            btnDNSCryptStart.setText(R.string.btnDNSCryptStop);
                            new PrefManager(getActivity()).setBoolPref("DNSCrypt Running", true);
                            displayLog(5000);
                        } else if (!sb.toString().toLowerCase().contains(dnscryptPath)
                                && sb.toString().contains("checkDNSRunning")) {
                            tvDNSStatus.setText(R.string.tvDNSStop);
                            tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStopped));
                            btnDNSCryptStart.setText(R.string.btnDNSCryptStart);
                            pbDNSCrypt.setIndeterminate(false);

                            if ((new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")
                                    && !sb.toString().contains("stopProcess"))
                                    || (!new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")
                                    && sb.toString().contains("startProcess"))) {
                                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                        getActivity(), getText(R.string.helper_dnscrypt_stopped).toString(), "dnscrypt_suddenly_stopped");
                                if (notificationHelper != null) {
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
                                        busyboxPath + "echo 'checkTrRunning'"
                                };
                                rootCommands = new RootCommands(commandsReset);
                                Intent intentReset = new Intent(getActivity(), RootExecService.class);
                                intentReset.setAction(RootExecService.RUN_COMMAND);
                                intentReset.putExtra("Commands", rootCommands);
                                intentReset.putExtra("Mark", RootExecService.TorRunFragmentMark);
                                RootExecService.performAction(getActivity(), intentReset);
                            }

                            new PrefManager(getActivity()).setBoolPref("DNSCryptFallBackResolverRemoved", false);

                            new PrefManager(getActivity()).setBoolPref("DNSCrypt Running", false);

                            if (timer != null) {
                                timer.cancel();
                                timer = null;
                            }

                            tvDNSCryptLog.setText(getText(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);

                            safeStopNoRootService();
                        } else if (sb.toString().contains("Something went wrong!")) {
                            tvDNSStatus.setText(R.string.wrong);
                            tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorAlert));
                        }

                    }

                    if (action.equals(TOP_BROADCAST)) {
                        if (TOP_BROADCAST.contains("TOP_BROADCAST")) {
                            Log.i(LOG_TAG, "DNSCryptRunFragment onReceive TOP_BROADCAST");
                            checkDNSRunning();
                        } else {
                            tvDNSStatus.setText(R.string.wrong);
                            tvDNSStatus.setTextColor(Color.RED);
                            btnDNSCryptStart.setEnabled(false);
                            Log.i(LOG_TAG, "DNSCryptRunFragment onReceive wrong TOP_BROADCAST");
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
                                        if (notificationHelper != null) {
                                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                                        }
                                    }

                                } catch (Exception e) {
                                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                            getActivity(), getText(R.string.verifier_error).toString(), "18");
                                    if (notificationHelper != null) {
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
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_dnscrypt_run, container, false);

        btnDNSCryptStart = view.findViewById(R.id.btnDNSCryptStart);
        btnDNSCryptStart.setOnClickListener(this);

        pbDNSCrypt = view.findViewById(R.id.pbDNSCrypt);

        String currentDNSCryptVersion = new PrefManager(getActivity()).getStrPref("DNSCryptVersion");

        tvDNSCryptLog = view.findViewById(R.id.tvDNSCryptLog);
        tvDNSCryptLog.setText(getText(R.string.tvDNSDefaultLog) + " " + currentDNSCryptVersion);
        tvDNSCryptLog.setMovementMethod(ScrollingMovementMethod.getInstance());

        tvDNSStatus = view.findViewById(R.id.tvDNSStatus);

        if (new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {
            tvDNSStatus.setText(R.string.tvDNSRunning);
            tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorRunning));
            btnDNSCryptStart.setText(R.string.btnDNSCryptStop);


        } else {
            tvDNSStatus.setText(R.string.tvDNSStop);
            tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStopped));
            btnDNSCryptStart.setText(R.string.btnDNSCryptStart);
        }

        if (new PrefManager(getActivity()).getBoolPref("DNSCrypt Installed")) {
            btnDNSCryptStart.setEnabled(true);
        } else {
            tvDNSStatus.setText(getText(R.string.tvDNSNotInstalled));
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        dnsCryptPort = pathVars.dnsCryptPort;
        itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        torTransPort = pathVars.torTransPort;
        dnsCryptFallbackRes = pathVars.dnsCryptFallbackRes;
        torDNSPort = pathVars.torDNSPort;
        torVirtAdrNet = pathVars.torVirtAdrNet;
        dnscryptPath = pathVars.dnscryptPath;
        torPath = pathVars.torPath;
        itpdPath = pathVars.itpdPath;
        obfsPath = pathVars.obfsPath;
        busyboxPath = pathVars.busyboxPath;
        iptablesPath = pathVars.iptablesPath;
        torSOCKSPort = pathVars.torSOCKSPort;
        torHTTPTunnelPort = pathVars.torHTTPTunnelPort;
        itpdSOCKSPort = pathVars.itpdSOCKSPort;
        rejectAddress = pathVars.rejectAddress;


        IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
        IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);
        if (getActivity() != null) {
            getActivity().registerReceiver(br, intentFilterBckgIntSer);
            getActivity().registerReceiver(br, intentFilterTopFrg);
        }

        if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("DNSCrypt Running"))
            displayLog(5000);

        if (getActivity() != null) {
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
                startCommandDNSCrypt = busyboxPath + "nohup " + dnscryptPath + " --config " + appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 &";
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
                    if (notificationHelper != null) {
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
                    if (notificationHelper != null) {
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


                tvDNSStatus.setText(R.string.tvDNSStarting);
                tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStarting));

                if (!runDNSCryptWithRoot) {
                    runDNSCryptNoRoot();
                }
                displayLog(1000);
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsDNS = Arr.ADD2(commandsDNS, commandsTether);
            } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && !new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_dnscrypt).toString(), "dnscrypt");
                if (notificationHelper != null) {
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
                tvDNSStatus.setText(R.string.tvDNSStarting);
                tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStarting));

                if (!runDNSCryptWithRoot) {
                    runDNSCryptNoRoot();
                }
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
                tvDNSStatus.setText(R.string.tvDNSStopping);
                tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStopping));
                String[] commandsTether = tethering.activateTethering(false);
                if (commandsTether != null && commandsTether.length > 0)
                    commandsDNS = Arr.ADD2(commandsDNS, commandsTether);
            } else if (new PrefManager(getActivity()).getBoolPref("Tor Running")
                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.helper_tor).toString(), "tor");
                if (notificationHelper != null) {
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
                tvDNSStatus.setText(R.string.tvDNSStopping);
                tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorStopping));
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

            pbDNSCrypt.setIndeterminate(true);
        }

    }


    private void checkDNSRunning() {
        if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("DNSCrypt Installed")) {
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

            pbDNSCrypt.setIndeterminate(true);
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
        pbDNSCrypt.setIndeterminate(indeterminate);
    }


    private void displayLog(int period) {

        if (timer != null) {
            timer.cancel();
            timer = null;
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

                OwnFileReader logFile = new OwnFileReader(appDataDir + "/logs/DnsCrypt.log");
                final String lastLines = logFile.readLastLines();

                if (++loop > 120) {
                    loop = 0;
                    displayLog(10000);
                }

                getActivity().runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        if (!previousLastLines.contentEquals(lastLines)) {

                            if (lastLines.contains("lowest initial latency")
                                    && new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")
                                    && !tvDNSStatus.getText().equals(getString(R.string.tvDNSRunning))) {
                                tvDNSStatus.setText(R.string.tvDNSRunning);
                                tvDNSStatus.setTextColor(getResources().getColor(R.color.textModuleStatusColorRunning));
                                if (pbDNSCrypt.isIndeterminate())
                                    pbDNSCrypt.setIndeterminate(false);
                            }

                            if ((lastLines.contains("connect: connection refused")
                                    || lastLines.contains("ERROR"))
                                    && !lastLines.contains(" OK ")) {
                                Log.e(LOG_TAG, "DNSCrypt Error: " + lastLines);
                                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                        getActivity(), getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                                if (notificationHelper != null) {
                                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                                }

                            } else if (lastLines.contains("dnscrypt-proxy is ready")
                                    && !new PrefManager(getActivity()).getBoolPref("DNSCryptFallBackResolverRemoved")) {

                                new PrefManager(getActivity()).setBoolPref("DNSCryptFallBackResolverRemoved", true);
                                String[] commandsResolver = {iptablesPath + "iptables -t nat -D tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT || true"};
                                RootCommands commands = new RootCommands(commandsResolver);
                                Intent intent = new Intent(getActivity(), RootExecService.class);
                                intent.setAction(RootExecService.RUN_COMMAND);
                                intent.putExtra("Commands", commands);
                                intent.putExtra("Mark", RootExecService.NullMark);
                                RootExecService.performAction(getActivity(), intent);

                            } else if (lastLines.contains("[CRITICAL]") && lastLines.contains("[FATAL]")) {

                                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                        getActivity(), getText(R.string.helper_dnscrypt_no_internet).toString(), "helper_dnscrypt_no_internet");
                                if (notificationHelper != null) {
                                    notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                                }

                                Log.e(LOG_TAG, "DNSCrypt FATAL Error: " + lastLines);

                                String[] commandsReset = new String[]{
                                        busyboxPath + "killall dnscrypt-proxy",
                                        busyboxPath + "killall tor",
                                        busyboxPath + "killall i2pd",
                                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                                        iptablesPath + "iptables -F tordnscrypt",
                                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true"
                                };
                                rootCommands = new RootCommands(commandsReset);
                                Intent intentReset = new Intent(getActivity(), RootExecService.class);
                                intentReset.setAction(RootExecService.RUN_COMMAND);
                                intentReset.putExtra("Commands", rootCommands);
                                intentReset.putExtra("Mark", RootExecService.NullMark);
                                RootExecService.performAction(getActivity(), intentReset);
                            }

                            tvDNSCryptLog.setText(Html.fromHtml(lastLines));
                            previousLastLines = lastLines;
                        }

                    }
                });

            }
        }, 1, period);

    }

    private void runDNSCryptNoRoot() {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean showNotification = shPref.getBoolean("swShowNotification", true);
        Intent intent = new Intent(getActivity(), NoRootService.class);
        intent.setAction(NoRootService.actionStartDnsCrypt);
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
