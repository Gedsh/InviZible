package pan.alexander.tordnscrypt.settings;
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
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.FileOperations;
import pan.alexander.tordnscrypt.utils.NotificationHelper;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Tethering;
import pan.alexander.tordnscrypt.utils.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

/**
 * A simple {@link Fragment} subclass.
 */
public class UnlockTorIpsFrag extends Fragment{

    RecyclerView rvListHostip;
    RecyclerView.Adapter rvAdapter;
    ArrayList<HostIP> unlockHostIP;
    String appDataDir;
    Boolean isChanged = false;
    String dnsCryptPort;
    String itpdHttpProxyPort;
    String torTransPort;
    String torDNSPort;
    String torVirtAdrNet;
    String busyboxPath;
    String iptablesPath;
    String rejectAddress;
    String torSOCKSPort;
    String torHTTPTunnelPort;
    String itpdSOCKSPort;
    boolean torTethering = false;
    boolean routeAllThroughTorDevice = true;
    boolean routeAllThroughTorTether = false;
    boolean runModulesWithRoot = false;
    boolean blockHttp = false;

    private String unlockHostsStr;
    private String unlockIPsStr;
    String deviceOrTether = "";


    public UnlockTorIpsFrag() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_preferences_tor_ips, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        dnsCryptPort = pathVars.dnsCryptPort;
        itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        torTransPort = pathVars.torTransPort;
        torDNSPort = pathVars.torDNSPort;
        torVirtAdrNet = pathVars.torVirtAdrNet;
        busyboxPath = pathVars.busyboxPath;
        iptablesPath = pathVars.iptablesPath;
        torSOCKSPort = pathVars.torSOCKSPort;
        torHTTPTunnelPort = pathVars.torHTTPTunnelPort;
        itpdSOCKSPort = pathVars.itpdSOCKSPort;
        rejectAddress = pathVars.rejectAddress;



        ////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////Reverse logic when route all through Tor!///////////////////
        //////////////////////////////////////////////////////////////////////////////////
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        torTethering = shPref.getBoolean("pref_common_tor_tethering",false);
        routeAllThroughTorDevice = shPref.getBoolean("pref_fast_all_through_tor",true);
        routeAllThroughTorTether = shPref.getBoolean("pref_common_tor_route_all",false);
        runModulesWithRoot = shPref.getBoolean("swUseModulesRoot",false);
        blockHttp = shPref.getBoolean("pref_fast_block_http",false);

        deviceOrTether = this.getArguments().getString("deviceOrTether");

        ArrayList<String> unlockHosts;
        ArrayList<String> unlockIPs;

        if (deviceOrTether==null)
            return;

        if (deviceOrTether.equals("device")) {
            if (!routeAllThroughTorDevice) {
                Objects.requireNonNull(getActivity()).setTitle(R.string.pref_tor_unlock);
                unlockHostsStr = "unlockHosts";
                unlockIPsStr = "unlockIPs";
            } else {
                Objects.requireNonNull(getActivity()).setTitle(R.string.pref_tor_clearnet);
                unlockHostsStr = "clearnetHosts";
                unlockIPsStr = "clearnetIPs";
            }
        } else if (deviceOrTether.equals("tether")) {
            if (!routeAllThroughTorTether) {
                Objects.requireNonNull(getActivity()).setTitle(R.string.pref_tor_unlock);
                unlockHostsStr = "unlockHostsTether";
                unlockIPsStr = "unlockIPsTether";
            } else {
                Objects.requireNonNull(getActivity()).setTitle(R.string.pref_tor_clearnet);
                unlockHostsStr = "clearnetHostsTether";
                unlockIPsStr = "clearnetIPsTether";
            }
        }


        Set<String> setUnlockHosts = new PrefManager(Objects.requireNonNull(getActivity())).getSetStrPref(unlockHostsStr);
        unlockHosts = new ArrayList<>(setUnlockHosts);
        Set<String> setUnlockIPs = new PrefManager(Objects.requireNonNull(getActivity())).getSetStrPref(unlockIPsStr);
        unlockIPs = new ArrayList<>(setUnlockIPs);



        unlockHostIP = new ArrayList<>();
        rvListHostip = getActivity().findViewById(R.id.rvTorIPs);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        rvListHostip.setLayoutManager(mLayoutManager);


        GetHostIP getHostIP = new GetHostIP(unlockHosts,unlockIPs);
        getHostIP.execute();


        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Verifier verifier = new Verifier(getActivity());
                    String appSignAlt = verifier.getApkSignature();
                    if (!verifier.decryptStr(wrongSign,appSign,appSignAlt).equals(TOP_BROADCAST)) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(),getText(R.string.verifier_error).toString(),"123");
                        if (notificationHelper != null) {
                            notificationHelper.show(getFragmentManager(),NotificationHelper.TAG_HELPER);
                        }
                    }

                } catch (Exception e) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(),getText(R.string.verifier_error).toString(),"168");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(),NotificationHelper.TAG_HELPER);
                    }
                    Log.e(LOG_TAG,"UnlockTorIpsFrag fault "+e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                            Arrays.toString(e.getStackTrace()));
                }
            }
        });
        thread.start();

        FloatingActionButton floatingbtnAddTorIPs = getActivity().findViewById(R.id.floatingbtnAddTorIPs);
        floatingbtnAddTorIPs.setAlpha(0.8f);
        floatingbtnAddTorIPs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addHostIPDialog();
            }
        });

    }

    @Override
    public void onStop() {
        super.onStop();

        if (unlockHostIP==null || !isChanged) return;

        List<String> ipsToUnlock = new LinkedList<>();
        for (int i = 0;i<unlockHostIP.size();i++) {
            if (unlockHostIP.get(i).active) {
                String[] arr = unlockHostIP.get(i).IP.split(", ");
                for (String ip:arr) {
                    if (ip.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}"))
                        ipsToUnlock.add(ip);
                }
            }
        }

        if (!isChanged) return;

        String[] commandsSaveIPs = null;
        //////////////////////////////////////////////////////////////////////////////////////
        //////////////When open this fragment to add sites for internal applications/////////
        /////////////////////////////////////////////////////////////////////////////////////
        if (deviceOrTether.equals("device")) {
            String torSitesBypassNatTCP = "";
            String torSitesBypassFilterTCP = "";
            String torSitesBypassNatUDP = "";
            String torSitesBypassFilterUDP = "";
            String torAppsBypassNatTCP = "";
            String torAppsBypassNatUDP = "";
            String torAppsBypassFilterTCP = "";
            String torAppsBypassFilterUDP = "";
            if (routeAllThroughTorDevice) {
                torSitesBypassNatTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j RETURN; done";
                torSitesBypassFilterTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet | while read var1; do "+iptablesPath+"iptables -A tordnscrypt -p tcp -d $var1 -j RETURN; done";
                torSitesBypassNatUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p udp -d $var1 -j RETURN; done";
                torSitesBypassFilterUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet | while read var1; do "+iptablesPath+"iptables -A tordnscrypt -p udp -d $var1 -j RETURN; done";
                torAppsBypassNatTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnetApps | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassNatUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnetApps | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p udp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassFilterTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnetApps | while read var1; do "+iptablesPath+"iptables -A tordnscrypt -p tcp -m owner --uid-owner $var1 -j RETURN; done";
                torAppsBypassFilterUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnetApps | while read var1; do "+iptablesPath+"iptables -A tordnscrypt -p udp -m owner --uid-owner $var1 -j RETURN; done";
            }

            String blockHttpRuleFilterAll = "";
            String blockHttpRuleNatTCP = "";
            String blockHttpRuleNatUDP = "";
            if (blockHttp) {
                blockHttpRuleFilterAll = iptablesPath + "iptables -A tordnscrypt -d +"+ rejectAddress +" -j REJECT";
                blockHttpRuleNatTCP = iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 80 -j DNAT --to-destination "+rejectAddress;
                blockHttpRuleNatUDP = iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 80 -j DNAT --to-destination "+rejectAddress;
            }

            String appUID = new PrefManager(getActivity()).getStrPref("appUID");
            if (runModulesWithRoot) {
                appUID ="0";
            }
            if( new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running") &&
                    new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")){
                if (!routeAllThroughTorDevice) {
                    FileOperations.writeToTextFile(getActivity(),appDataDir+"/app_data/tor/unlock",ipsToUnlock,"ignored");
                    Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
                    commandsSaveIPs = new String[] {
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath+ "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d "+torVirtAdrNet+" -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            busyboxPath+ "cat "+appDataDir+"/app_data/tor/unlock | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port "+torTransPort+"; done",
                            busyboxPath+ "cat "+appDataDir+"/app_data/tor/unlockApps | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port "+torTransPort+"; done"};
                } else {
                    FileOperations.writeToTextFile(getActivity(),appDataDir+"/app_data/tor/clearnet",ipsToUnlock,"ignored");
                    Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
                    commandsSaveIPs = new String[] {
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath+ "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath+ "iptables -F tordnscrypt",
                            iptablesPath+ "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath+ "sleep 1",
                            "TOR_UID="+ appUID,
                            iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",

                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:"+itpdHttpProxyPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+dnsCryptPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            torSitesBypassNatTCP,
                            torSitesBypassNatUDP,
                            torAppsBypassNatTCP,
                            torAppsBypassNatUDP,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                            iptablesPath+ "iptables -N tordnscrypt",
                            iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torHTTPTunnelPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torHTTPTunnelPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdHttpProxyPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdHttpProxyPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torTransPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j RETURN",
                            blockHttpRuleFilterAll,
                            torSitesBypassFilterTCP,
                            torSitesBypassFilterUDP,
                            torAppsBypassFilterTCP,
                            torAppsBypassFilterUDP,
                            iptablesPath+ "iptables -A tordnscrypt -j REJECT",
                            iptablesPath+ "iptables -I OUTPUT -j tordnscrypt"};
                }

            } else if (new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Running")){
                if (!routeAllThroughTorDevice) {
                    FileOperations.writeToTextFile(getActivity(),appDataDir+"/app_data/tor/unlock",ipsToUnlock,"ignored");
                    Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
                } else {
                    FileOperations.writeToTextFile(getActivity(),appDataDir+"/app_data/tor/clearnet",ipsToUnlock,"ignored");
                    Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
                    commandsSaveIPs = new String[] {
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath+ "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath+ "iptables -F tordnscrypt",
                            iptablesPath+ "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath+ "sleep 1",
                            "TOR_UID="+ appUID,
                            iptablesPath+ "iptables -t nat -N tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:"+ torDNSPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:"+ torDNSPort,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            blockHttpRuleNatTCP,
                            blockHttpRuleNatUDP,
                            torSitesBypassNatTCP,
                            torSitesBypassNatUDP,
                            torAppsBypassNatTCP,
                            torAppsBypassNatUDP,
                            iptablesPath+ "iptables -t nat -A tordnscrypt_nat_output -p tcp -j DNAT --to-destination 127.0.0.1:"+torTransPort,
                            iptablesPath+ "iptables -N tordnscrypt",
                            iptablesPath+ "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torHTTPTunnelPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torHTTPTunnelPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdSOCKSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+itpdHttpProxyPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+itpdHttpProxyPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torTransPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j RETURN",
                            iptablesPath+ "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j RETURN",
                            blockHttpRuleFilterAll,
                            torSitesBypassFilterTCP,
                            torSitesBypassFilterUDP,
                            torAppsBypassFilterTCP,
                            torAppsBypassFilterUDP,
                            iptablesPath+ "iptables -A tordnscrypt -j REJECT",
                            iptablesPath+ "iptables -I OUTPUT -j tordnscrypt"};
                }

            } else {
                if (!routeAllThroughTorDevice) {
                    FileOperations.writeToTextFile(getActivity(),appDataDir+"/app_data/tor/unlock",ipsToUnlock,"ignored");
                    Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
                } else {
                    FileOperations.writeToTextFile(getActivity(),appDataDir + "/app_data/tor/clearnet",ipsToUnlock,"ignored");
                    Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
                }
            }
            //////////////////////////////////////////////////////////////////////////////////////
            //////////////When open this fragment to add sites for external tether devices/////////
            /////////////////////////////////////////////////////////////////////////////////////
        } else if (deviceOrTether.equals("tether")) {
            if (!routeAllThroughTorTether) {
                FileOperations.writeToTextFile(getActivity(),appDataDir + "/app_data/tor/unlock_tether",ipsToUnlock,"ignored");
                Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
            } else {
                FileOperations.writeToTextFile(getActivity(),appDataDir + "/app_data/tor/clearnet_tether",ipsToUnlock,"ignored");
                Toast.makeText(getActivity(),getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
            }
        }



        if (commandsSaveIPs==null)
            return;

        boolean privacyMode = !new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("DNSCrypt Running");
        if (torTethering) {
            Tethering tethering = new Tethering(getActivity());
            String[] commandsTether = tethering.activateTethering(privacyMode);
            if (commandsTether!=null && commandsTether.length>0)
                commandsSaveIPs = Arr.ADD2(commandsSaveIPs, commandsTether);
        }

        RootCommands rootCommands  = new RootCommands(commandsSaveIPs);
        Intent intent = new Intent(getActivity(), RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands",rootCommands);
        intent.putExtra("Mark", RootExecService.SettingsActivityMark);
        RootExecService.performAction(getActivity(),intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public class HostIP {
        boolean active;
        boolean inputHost;
        boolean inputIP;
        String host;
        String IP;

        HostIP(String host, String IP, boolean inputHost, boolean inputIP, boolean active) {
            this.host = host;
            this.IP = IP;
            this.inputHost = inputHost;
            this.inputIP = inputIP;
            this.active = active;
        }
    }

    public class HostIPAdapter extends RecyclerView.Adapter<UnlockTorIpsFrag.HostIPAdapter.HostIPViewHolder> {

        ArrayList<HostIP> unlockHostIP;
        LayoutInflater lInflater = (LayoutInflater)Objects.requireNonNull(getActivity()).getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        HostIPAdapter(ArrayList<HostIP> unlockHostIP) {
            this.unlockHostIP = unlockHostIP;
        }

        @NonNull
        @Override
        public HostIPAdapter.HostIPViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = lInflater.inflate(R.layout.item_tor_ips, parent, false);
            return new HostIPViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HostIPAdapter.HostIPViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return unlockHostIP.size();
        }

        HostIP getItem(int position) {
            return unlockHostIP.get(position);
        }

        void delItem(int position) {
            isChanged = true;
            if (getItem(position).inputIP){
                Set<String> ipSet;
                ipSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);

                if (getItem(position).active) {
                    ipSet.remove(getItem(position).IP);
                } else {
                    ipSet.remove("#"+getItem(position).IP);
                }
                new PrefManager(getActivity()).setSetStrPref(unlockIPsStr,ipSet);

            } else if (getItem(position).inputHost) {
                Set<String> hostSet;
                hostSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);

                if (getItem(position).active) {
                    hostSet.remove(getItem(position).host);
                } else {
                    hostSet.remove("#"+getItem(position).host);
                }
                new PrefManager(getActivity()).setSetStrPref(unlockHostsStr,hostSet);

            }
            unlockHostIP.remove(position);
            rvAdapter.notifyItemRemoved(position);
        }

        void setActive(int position, boolean active){
            HostIP hip = unlockHostIP.get(position);
            hip.active = active;
        }

        class HostIPViewHolder extends RecyclerView.ViewHolder {

            TextView tvTorItemHost;
            TextView tvTorItemIP;
            Switch swTorItem;
            ImageButton imbtnTorItem;
            LinearLayout llHostIP;

            HostIPViewHolder(View itemView) {
                super(itemView);

                tvTorItemHost = itemView.findViewById(R.id.tvTorItemHost);
                tvTorItemIP = itemView.findViewById(R.id.tvTorItemIP);
                swTorItem = itemView.findViewById(R.id.swTorItem);
                swTorItem.setOnCheckedChangeListener(onCheckedChangeListener);
                imbtnTorItem = itemView.findViewById(R.id.imbtnTorItem);
                imbtnTorItem.setOnClickListener(onClickListener);
                llHostIP = itemView.findViewById(R.id.llHostIP);
                llHostIP.setOnClickListener(onClickListener);
                llHostIP.setFocusable(true);
                llHostIP.setOnFocusChangeListener(onFocusChangeListener);
                llHostIP.setBackgroundColor(getResources().getColor(R.color.colorFirst));
            }

            void bind(int position){
                if (!getItem(position).host.isEmpty()) {
                    tvTorItemHost.setText(getItem(position).host);
                    tvTorItemHost.setVisibility(View.VISIBLE);
                } else {
                    tvTorItemHost.setVisibility(View.GONE);
                }

                if (getItem(position).active) {
                    tvTorItemIP.setText(getItem(getAdapterPosition()).IP);
                } else {
                    tvTorItemIP.setText(getText(R.string.pref_tor_unlock_disabled));
                }
                swTorItem.setChecked(getItem(position).active);
                llHostIP.setEnabled(getItem(position).active);
            }

            View.OnClickListener onClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (v.getId()) {
                        case R.id.imbtnTorItem:
                            delItem(getAdapterPosition());
                            break;
                        case R.id.llHostIP:
                            editHostIPDialog(getAdapterPosition());
                            break;
                    }

                }
            };

            CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setActive(getAdapterPosition(),isChecked);
                    llHostIP.setEnabled(isChecked);
                    isChanged = true;
                    if (isChecked) {
                        tvTorItemIP.setText(getItem(getAdapterPosition()).IP);

                        if (getItem(getAdapterPosition()).inputIP) {
                            Set<String> ipsSet;
                            ipsSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
                            String oldIP = getItem(getAdapterPosition()).IP;
                            ipsSet.remove("#"+oldIP);
                            ipsSet.add(oldIP.replace("#",""));
                            new PrefManager(getActivity()).setSetStrPref(unlockIPsStr,ipsSet);

                        } else if (getItem(getAdapterPosition()).inputHost) {
                            Set<String> hostsSet;
                            hostsSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
                            String oldHost = getItem(getAdapterPosition()).host;
                            hostsSet.remove("#"+oldHost);
                            hostsSet.add(oldHost.replace("#",""));
                            new PrefManager(getActivity()).setSetStrPref(unlockHostsStr,hostsSet);
                        }

                    } else {
                        tvTorItemIP.setText(getText(R.string.pref_tor_unlock_disabled));

                        if (getItem(getAdapterPosition()).inputIP) {
                            Set<String> ipsSet;
                            ipsSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
                            String oldIP = getItem(getAdapterPosition()).IP;
                            ipsSet.remove(oldIP);
                            ipsSet.add("#" + oldIP);
                            new PrefManager(getActivity()).setSetStrPref(unlockIPsStr,ipsSet);
                        } else if (getItem(getAdapterPosition()).inputHost) {
                            Set<String> hostsSet;
                            hostsSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
                            String oldHost = getItem(getAdapterPosition()).host;
                            hostsSet.remove(oldHost);
                            hostsSet.add("#" + oldHost);
                            new PrefManager(getActivity()).setSetStrPref(unlockHostsStr,hostsSet);
                        }
                    }
                }
            };

            View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean inFocus) {
                    if (inFocus) {
                        view.setBackgroundColor(getResources().getColor(R.color.colorSecond));
                    } else {
                        view.setBackgroundColor(getResources().getColor(R.color.colorFirst));
                    }
                }
            };

            void editHostIPDialog(final int position) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme);
                builder.setTitle(R.string.pref_tor_unlock_edit);

                LayoutInflater inflater = getActivity().getLayoutInflater();
                @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
                final EditText input = inputView.findViewById(R.id.etForDialog);

                String oldHost = "";
                String oldIP = "";

                if (unlockHostIP.get(position).inputHost) {
                    oldHost = unlockHostIP.get(position).host;
                    input.setText(oldHost,TextView.BufferType.EDITABLE);
                } else if (unlockHostIP.get(position).inputIP) {
                    oldIP = unlockHostIP.get(position).IP;
                    input.setText(oldIP,TextView.BufferType.EDITABLE);
                }
                builder.setView(inputView);

                final String finalOldIP = oldIP;
                final String finalOldHost = oldHost;
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        isChanged = true;
                        if (input.getText().toString().matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")) {
                            String host = getText(R.string.please_wait).toString();
                            unlockHostIP.set(position,new HostIP(host,input.getText().toString(),false,true,true));
                            Set<String> ipsSet;
                            ipsSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
                            ipsSet.remove(finalOldIP);
                            ipsSet.add(input.getText().toString());
                            new PrefManager(getActivity()).setSetStrPref(unlockIPsStr,ipsSet);
                        } else {
                            String IP = getText(R.string.please_wait).toString();
                            String host = input.getText().toString();
                            if (!host.startsWith("http")) host = "https://" + host;
                            unlockHostIP.set(position,new HostIP(host,IP,true,false,true));
                            Set<String> hostsSet;
                            hostsSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
                            hostsSet.remove(finalOldHost);
                            hostsSet.add(host);
                            new PrefManager(getActivity()).setSetStrPref(unlockHostsStr,hostsSet);
                        }
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                getHostOrIp(position,false,true);
                            }
                        });

                        thread.start();
                        rvAdapter.notifyItemChanged(position);
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

                AlertDialog view  = builder.show();
                Objects.requireNonNull(view.getWindow()).getDecorView().setBackgroundColor(Color.TRANSPARENT);
            }

        }
    }

    void addHostIPDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme);

        if (deviceOrTether.equals("device")) {
            if (!routeAllThroughTorDevice) {
                builder.setTitle(R.string.pref_tor_unlock);
            } else {
                builder.setTitle(R.string.pref_tor_clearnet);
            }
        } else if (deviceOrTether.equals("tether")) {
            if (!routeAllThroughTorTether) {
                builder.setTitle(R.string.pref_tor_unlock);
            } else {
                builder.setTitle(R.string.pref_tor_clearnet);
            }
        }


        LayoutInflater inflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
        final EditText input = inputView.findViewById(R.id.etForDialog);

        builder.setView(inputView);

        builder.setCancelable(false);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                /*InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }*/
                isChanged = true;
                if (input.getText().toString().matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")) {
                    unlockHostIP.add(new HostIP(getText(R.string.please_wait).toString(),input.getText().toString(),false,true,true));
                    Set<String> ipsSet;
                    ipsSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
                    ipsSet.add(input.getText().toString());
                    new PrefManager(getActivity()).setSetStrPref(unlockIPsStr,ipsSet);
                } else {
                    String host = input.getText().toString();
                    if (!host.startsWith("http")) host = "https://" + host;
                    unlockHostIP.add(new HostIP(host,getText(R.string.please_wait).toString(),true,false,true));
                    Set<String> hostsSet;
                    hostsSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
                    hostsSet.add(host);
                    new PrefManager(getActivity()).setSetStrPref(unlockHostsStr,hostsSet);
                }
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        getHostOrIp(unlockHostIP.size()-1,true,false);
                    }
                });

                thread.start();
                rvAdapter.notifyItemChanged(unlockHostIP.size()-1);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog view  = builder.show();
        Objects.requireNonNull(view.getWindow()).getDecorView().setBackgroundColor(Color.TRANSPARENT);
    }

    @SuppressLint("StaticFieldLeak")
    class GetHostIP extends AsyncTask<Void,Integer,Void> {

        ArrayList<String> unlockHosts;
        ArrayList<String> unlockIPs;

        GetHostIP (ArrayList<String> unlockHosts, ArrayList<String> unlockIPs) {
            this.unlockHosts = unlockHosts;
            this.unlockIPs = unlockIPs;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (!unlockHosts.isEmpty()) {
                for (String host:unlockHosts) {
                    String hostClear = host.replace("#","");
                    unlockHostIP.add(new HostIP(hostClear,getText(R.string.please_wait).toString(),true,false,!host.trim().startsWith("#")));
                }
            }
            if (!unlockIPs.isEmpty()) {
                for (String IPs:unlockIPs) {
                    String IPsClear = IPs.replace("#","");
                    unlockHostIP.add(new HostIP(getText(R.string.please_wait).toString(),IPsClear,false,true,!IPs.trim().startsWith("#")));
                }
            }

            rvAdapter = new HostIPAdapter(unlockHostIP);
            rvListHostip.setAdapter(rvAdapter);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i=0;i<unlockHostIP.size();i++){
               getHostOrIp(i,false,false);
               publishProgress(i);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            rvAdapter.notifyItemChanged(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }


    }

    void getHostOrIp(final int position, final boolean addHostIP, final boolean editHostIP) {
        boolean active = unlockHostIP.get(position).active;
        if (unlockHostIP.get(position).inputHost){
            String host = unlockHostIP.get(position).host;
            try {
                InetAddress[] addresses = InetAddress.getAllByName(new URL(host).getHost());
                StringBuilder sb = new StringBuilder();
                for (InetAddress address:addresses) {
                    sb.append(address.getHostAddress()).append(", ");
                }
                String ip = sb.substring(0,sb.length()-2);
                unlockHostIP.set(position,new HostIP(host,ip,true,false,active));

                Objects.requireNonNull(getActivity()).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (addHostIP) {
                            rvAdapter.notifyItemChanged(position);
                            rvListHostip.scrollToPosition(position);
                        } else if (editHostIP) {
                            rvAdapter.notifyItemChanged(position);
                        }

                    }
                });
            } catch (UnknownHostException | MalformedURLException e) {
                String ip = getString(R.string.pref_fast_unlock_host_wrong);
                unlockHostIP.set(position,new HostIP(host,ip,true,false,active));
                e.printStackTrace();

                Objects.requireNonNull(getActivity()).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (addHostIP) {
                            rvAdapter.notifyItemChanged(position);
                            rvListHostip.scrollToPosition(position);
                        } else if (editHostIP) {
                            rvAdapter.notifyItemChanged(position);
                        }
                    }
                });
            }
        } else if (unlockHostIP.get(position).inputIP) {
            String IP = unlockHostIP.get(position).IP;
            String host = "";
            try {
                URL url = new URL("http://" +IP);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setInstanceFollowRedirects(false);
                con.setRequestMethod ("GET");  //OR  huc.setRequestMethod ("HEAD");
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9.0.1; " +
                        "Mi Mi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Mobile Safari/537.36");
                con.setConnectTimeout(500);
                con.connect();

                // HTML-Code from a website
                int responseCode = con.getResponseCode();

                // HTTP 200 OK
                if (responseCode == 200) {
                    InetAddress addr = InetAddress.getByName(IP);
                    host = addr.getHostName();
                    if (host.equals(IP)) host = "";
                }

                // HTTP 301 oder 302 redirect
                else if (responseCode == 301 || responseCode == 302) {
                    host = con.getHeaderField("Location");
                }
                con.disconnect();
                unlockHostIP.set(position,new HostIP(host,IP,false,true,active));

                Objects.requireNonNull(getActivity()).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (addHostIP) {
                            rvAdapter.notifyDataSetChanged();
                            rvListHostip.scrollToPosition(position);
                        } else if (editHostIP) {
                            rvAdapter.notifyItemChanged(position);
                        }

                    }
                });
            } catch (IOException e) {
                host = "";
                unlockHostIP.set(position,new HostIP(host,IP,false,true,active));
                e.printStackTrace();

                Objects.requireNonNull(getActivity()).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (addHostIP) {
                            rvAdapter.notifyDataSetChanged();
                            rvListHostip.scrollToPosition(position);
                        } else if (editHostIP) {
                            rvAdapter.notifyItemChanged(position);
                        }
                    }
                });
            }
        }
    }

}
