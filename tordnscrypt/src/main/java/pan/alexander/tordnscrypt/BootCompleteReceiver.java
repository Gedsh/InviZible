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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;

import java.util.Objects;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment;
import pan.alexander.tordnscrypt.utils.ApManager;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Tethering;
import pan.alexander.tordnscrypt.utils.modulesManager.ModulesKiller;
import pan.alexander.tordnscrypt.utils.modulesManager.ModulesRestarter;
import pan.alexander.tordnscrypt.utils.modulesManager.ModulesRunner;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class BootCompleteReceiver extends BroadcastReceiver {
    private final int mJobId = PreferencesFastFragment.mJobId;

    private Context context;
    private SharedPreferences shPref;
    private String appDataDir;
    private int refreshPeriodHours = 12;

    @Override
    public void onReceive(final Context context, Intent intent) {

        final String BOOT_COMPLETE = "android.intent.action.BOOT_COMPLETED";
        this.context = context.getApplicationContext();

        final PathVars pathVars = new PathVars(context.getApplicationContext());
        appDataDir = pathVars.appDataDir;

        final boolean tethering_autostart;
        final boolean routeAllThroughTor;
        final boolean blockHttp;
        final String dnsCryptPort = pathVars.dnsCryptPort;
        final String itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        final String torTransPort = pathVars.torTransPort;
        final String dnsCryptFallbackRes = pathVars.dnsCryptFallbackRes;
        final String torDNSPort = pathVars.torDNSPort;
        final String torVirtAdrNet = pathVars.torVirtAdrNet;
        final String busyboxPath = pathVars.busyboxPath;
        final String iptablesPath = pathVars.iptablesPath;
        final String rejectAddress = pathVars.rejectAddress;
        final String torSOCKSPort = pathVars.torSOCKSPort;
        final String torHTTPTunnelPort = pathVars.torHTTPTunnelPort;
        final String itpdSOCKSPort = pathVars.itpdSOCKSPort;
        final Tethering tethering = new Tethering(context);

        shPref = PreferenceManager.getDefaultSharedPreferences(this.context);
        String refreshPeriod = shPref.getString("pref_fast_site_refresh_interval", "12");
        if (refreshPeriod != null && !refreshPeriod.isEmpty()) {
            refreshPeriodHours = Integer.parseInt(refreshPeriod);
        }


        if (Objects.requireNonNull(intent.getAction()).equalsIgnoreCase(BOOT_COMPLETE)) {

            new PrefManager(context).setBoolPref("APisON", false);

            final SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
            tethering_autostart = shPref.getBoolean("pref_common_tethering_autostart", false);
            routeAllThroughTor = shPref.getBoolean("pref_fast_all_through_tor", true);
            blockHttp = shPref.getBoolean("pref_fast_block_http", false);

            boolean autoStartDNSCrypt = shPref.getBoolean("swAutostartDNS", false);
            boolean autoStartTor = shPref.getBoolean("swAutostartTor", false);
            boolean autoStartITPD = shPref.getBoolean("swAutostartITPD", false);

            if (autoStartITPD) {
                shortenTooLongITPDLog();
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

            if (tethering_autostart) {
                startHOTSPOT();
            }

            String appUIDTor = new PrefManager(context).getStrPref("appUID");
            if (isStartModulesWithRoot()) {
                appUIDTor = "0";
            }

            String[] commandsStart = null;

            if (autoStartDNSCrypt && autoStartTor && autoStartITPD) {

                startStopRestartModules(true, true, true);

                if (!routeAllThroughTor) {
                    commandsStart = new String[]{
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
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
                            iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                            blockHttpRuleFilterAll,
                            iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                            busyboxPath + "cat " + appDataDir + "/app_data/tor/unlock | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                            busyboxPath + "cat " + appDataDir + "/app_data/tor/unlockApps | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                            busyboxPath + "sleep 10",
                            iptablesPath + "iptables -t nat -D tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT",
                    };

                } else {
                    commandsStart = new String[]{
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath + "sleep 1",
                            "TOR_UID=" + appUIDTor,
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
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --syn -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            iptablesPath + "iptables -N tordnscrypt",
                            iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
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
                            iptablesPath + "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j ACCEPT",
                            blockHttpRuleFilterAll,
                            torSitesBypassFilterTCP,
                            torSitesBypassFilterUDP,
                            torAppsBypassFilterTCP,
                            torAppsBypassFilterUDP,
                            iptablesPath + "iptables -A tordnscrypt -j REJECT",
                            iptablesPath + "iptables -I OUTPUT -j tordnscrypt"};
                }

                startRefreshTorUnlockIPs(context);
                if (tethering_autostart) {
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether != null && commandsTether.length > 0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }

            } else if (autoStartDNSCrypt && autoStartTor) {

                startStopRestartModules(true, true, false);

                if (!routeAllThroughTor) {
                    commandsStart = new String[]{
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
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
                            iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                            iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                            blockHttpRuleFilterAll,
                            iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                            busyboxPath + "cat " + appDataDir + "/app_data/tor/unlock | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                            busyboxPath + "cat " + appDataDir + "/app_data/tor/unlockApps | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                            busyboxPath + "sleep 10",
                            iptablesPath + "iptables -t nat -D tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT"
                    };
                } else {
                    commandsStart = new String[]{
                            "ip6tables -D OUTPUT -j DROP || true",
                            "ip6tables -I OUTPUT -j DROP",
                            iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                            iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                            iptablesPath + "iptables -F tordnscrypt",
                            iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                            busyboxPath + "sleep 1",
                            "TOR_UID=" + appUIDTor,
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
                            iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --syn -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                            iptablesPath + "iptables -N tordnscrypt",
                            iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
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
                            iptablesPath + "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j ACCEPT",
                            blockHttpRuleFilterAll,
                            torSitesBypassFilterTCP,
                            torSitesBypassFilterUDP,
                            torAppsBypassFilterTCP,
                            torAppsBypassFilterUDP,
                            iptablesPath + "iptables -A tordnscrypt -j REJECT",
                            iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                            busyboxPath + "sleep 10",
                            iptablesPath + "iptables -t nat -D tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT"
                    };
                }

                startRefreshTorUnlockIPs(context);
                if (tethering_autostart) {
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether != null && commandsTether.length > 0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if (autoStartDNSCrypt && !autoStartITPD) {

                startStopRestartModules(true, false, false);

                commandsStart = new String[]{
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
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
                        iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                        blockHttpRuleFilterAll,
                        iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                        busyboxPath + "sleep 10",
                        iptablesPath + "iptables -t nat -D tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT"
                };

                stopRefreshTorUnlockIPs(context);
                if (tethering_autostart) {
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether != null && commandsTether.length > 0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if (!autoStartDNSCrypt && autoStartTor && !autoStartITPD) {

                startStopRestartModules(false, true, false);

                commandsStart = new String[]{
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",
                        "TOR_UID=" + appUIDTor,
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
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --syn -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                        iptablesPath + "iptables -N tordnscrypt",
                        iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
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
                        iptablesPath + "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        torSitesBypassFilterTCP,
                        torSitesBypassFilterUDP,
                        torAppsBypassFilterTCP,
                        torAppsBypassFilterUDP,
                        iptablesPath + "iptables -A tordnscrypt -j REJECT",
                        iptablesPath + "iptables -I OUTPUT -j tordnscrypt"};

                startRefreshTorUnlockIPs(context);
                if (tethering_autostart) {
                    String[] commandsTether = tethering.activateTethering(true);
                    if (commandsTether != null && commandsTether.length > 0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if (!autoStartDNSCrypt && !autoStartTor && autoStartITPD) {

                startStopRestartModules(false, false, true);

                commandsStart = new String[]{
                        "ip6tables -I OUTPUT -j DROP",
                        busyboxPath + "sleep 1",
                };

                stopRefreshTorUnlockIPs(context);
                if (tethering_autostart) {
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether != null && commandsTether.length > 0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if (!autoStartDNSCrypt && autoStartTor) {

                startStopRestartModules(false, true, true);

                commandsStart = new String[]{
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",
                        "TOR_UID=" + appUIDTor,
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
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --syn -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                        iptablesPath + "iptables -N tordnscrypt",
                        iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
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
                        iptablesPath + "iptables -A tordnscrypt -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        torSitesBypassFilterTCP,
                        torSitesBypassFilterUDP,
                        torAppsBypassFilterTCP,
                        torAppsBypassFilterUDP,
                        iptablesPath + "iptables -A tordnscrypt -j REJECT",
                        iptablesPath + "iptables -I OUTPUT -j tordnscrypt"};


                startRefreshTorUnlockIPs(context);
                if (tethering_autostart) {
                    String[] commandsTether = tethering.activateTethering(true);
                    if (commandsTether != null && commandsTether.length > 0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            } else if (autoStartDNSCrypt) {

                startStopRestartModules(true, false, true);

                commandsStart = new String[]{
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
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
                        iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                        blockHttpRuleFilterAll,
                        iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                        busyboxPath + "sleep 10",
                        iptablesPath + "iptables -t nat -D tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -j ACCEPT"
                };

                stopRefreshTorUnlockIPs(context);
                if (tethering_autostart) {
                    String[] commandsTether = tethering.activateTethering(false);
                    if (commandsTether != null && commandsTether.length > 0)
                        commandsStart = Arr.ADD2(commandsStart, commandsTether);
                }
            }

            if (commandsStart != null && new PrefManager(this.context).getBoolPref("rootIsAvailable")) {
                RootCommands rootCommands = new RootCommands(commandsStart);
                Intent intentCommands = new Intent(context, RootExecService.class);
                intentCommands.setAction(RootExecService.RUN_COMMAND);
                intentCommands.putExtra("Commands", rootCommands);
                intentCommands.putExtra("Mark", RootExecService.BootBroadcastMark);
                RootExecService.performAction(context, intentCommands);
            }
        }
    }

    private void shortenTooLongITPDLog() {
        OwnFileReader ofr = new OwnFileReader(context, appDataDir + "/logs/i2pd.log");
        ofr.shortenToToLongFile();
    }

    private void startHOTSPOT() {

        new PrefManager(context).setBoolPref("APisON", true);

        try {
            ApManager apManager = new ApManager(context);
            if (!apManager.configApState()) {
                Intent intent_tether = new Intent(Intent.ACTION_MAIN, null);
                intent_tether.addCategory(Intent.CATEGORY_LAUNCHER);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.TetherSettings");
                intent_tether.setComponent(cn);
                intent_tether.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent_tether);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "BootCompleteReceiver ApManager exception " + e.getMessage() + " " + e.getCause());
        }
    }

    private void startStopRestartModules(boolean autoStartDNSCrypt, boolean autoStartTor, boolean autoStartITPD) {

        saveModulesStateRunning(autoStartDNSCrypt, autoStartTor, autoStartITPD);

        if (autoStartDNSCrypt) {
            if (isDnsCryptSavedStateRunning()) {
                restartDNSCrypt();
            } else {
                runDNSCrypt();
            }
        } else {
            if (isDnsCryptSavedStateRunning()) {
                stopDNSCrypt();
            }
        }

        if (autoStartTor) {
            if (isTorSavedStateRunning()) {
                restartTor();
            } else {
                runTor();
            }
        } else {
            if (isTorSavedStateRunning()) {
                stopTor();
            }
        }

        if (autoStartITPD) {
            if (isITPDSavedStateRunning()) {
                restartITPD();
            } else {
                runITPD();
            }
        } else {
            stopITPD();
        }

    }

    private void saveModulesStateRunning(boolean saveDNSCryptRunning, boolean saveTorRunning, boolean saveITPDRunning) {
        new PrefManager(context).setBoolPref("DNSCrypt Running", saveDNSCryptRunning);
        new PrefManager(context).setBoolPref("Tor Running", saveTorRunning);
        new PrefManager(context).setBoolPref("I2PD Running", saveITPDRunning);
    }

    private void runDNSCrypt() {
        ModulesRunner.runDNSCrypt(context);
    }

    private void runTor() {
        ModulesRunner.runTor(context);
    }

    private void runITPD() {
        ModulesRunner.runITPD(context);
    }

    private void stopDNSCrypt() {
        ModulesKiller.stopDNSCrypt(context);
    }

    private void stopTor() {
        ModulesKiller.stopTor(context);
    }

    private void stopITPD() {
        ModulesKiller.stopITPD(context);
    }

    private void restartDNSCrypt() {
        ModulesRestarter.restartDNSCrypt(context);
    }

    private void restartTor() {
        ModulesRestarter.restartTor(context);
    }

    private void restartITPD() {
        ModulesRestarter.restartITPD(context);
    }

    private boolean isDnsCryptSavedStateRunning() {
        return new PrefManager(context).getBoolPref("DNSCrypt Running");
    }

    private boolean isTorSavedStateRunning() {
        return new PrefManager(context).getBoolPref("Tor Running");
    }

    private boolean isITPDSavedStateRunning() {
        return new PrefManager(context).getBoolPref("I2PD Running");
    }

    private boolean isStartModulesWithRoot() {
        return shPref.getBoolean("swUseModulesRoot", false);
    }

    private void startRefreshTorUnlockIPs(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours == 0) {
            return;
        }
        ComponentName jobService = new ComponentName(context, GetIPsJobService.class);
        JobInfo.Builder getIPsJobBuilder = new JobInfo.Builder(mJobId, jobService);
        getIPsJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        getIPsJobBuilder.setPeriodic(refreshPeriodHours * 60 * 60 * 1000);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (jobScheduler != null) {
            jobScheduler.schedule(getIPsJobBuilder.build());
        }
    }

    private void stopRefreshTorUnlockIPs(Context context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler != null) {
            jobScheduler.cancel(mJobId);
        }
    }
}
