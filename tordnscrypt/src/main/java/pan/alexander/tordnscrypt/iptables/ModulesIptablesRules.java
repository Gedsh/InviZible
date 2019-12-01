package pan.alexander.tordnscrypt.iptables;

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

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;

import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class ModulesIptablesRules extends IptablesRulesSender {

    public ModulesIptablesRules(Context context) {
        super(context);
    }

    @Override
    public String[] configureIptables(ModuleState dnsCryptState, ModuleState torState, ModuleState itpdState) {

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        runModulesWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        routeAllThroughTor = shPref.getBoolean("pref_fast_all_through_tor", true);
        blockHttp = shPref.getBoolean("pref_fast_block_http", false);

        String[] commands = null;

        String appUID = new PrefManager(context).getStrPref("appUID");
        if (runModulesWithRoot) {
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


        if (dnsCryptState == RUNNING && torState == RUNNING) {

            if (!routeAllThroughTor) {


                commands = new String[]{
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",
                        "TOR_UID=" + appUID,
                        iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d " + torVirtAdrNet + " -j DNAT --to-destination 127.0.0.1:" + torTransPort,
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        iptablesPath + "iptables -N tordnscrypt",
                        iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                        iptablesPath + "iptables -A tordnscrypt -p udp -d " + dnsCryptFallbackRes + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                        busyboxPath + "cat " + appDataDir + "/app_data/tor/bridgesIP | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                        busyboxPath + "cat " + appDataDir + "/app_data/tor/unlock | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                        busyboxPath + "cat " + appDataDir + "/app_data/tor/unlockApps | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port " + torTransPort + "; done",
                };
            } else {

                commands = new String[]{
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptablesPath + "iptables -F tordnscrypt",
                        iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                        busyboxPath + "sleep 1",
                        "TOR_UID=" + appUID,
                        iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                        busyboxPath + "cat " + appDataDir + "/app_data/tor/bridgesIP | while read var1; do " + iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + torTransPort + "; done",
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
                        iptablesPath + "iptables -A tordnscrypt -p udp -d " + dnsCryptFallbackRes + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        torSitesBypassFilterTCP,
                        torSitesBypassFilterUDP,
                        torAppsBypassFilterTCP,
                        torAppsBypassFilterUDP,
                        iptablesPath + "iptables -A tordnscrypt -j REJECT",
                        iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
                };
            }

            String[] commandsTether = tethering.activateTethering(false);
            if (commandsTether != null && commandsTether.length > 0)
                commands = Arr.ADD2(commands, commandsTether);
        } else if (dnsCryptState == RUNNING && torState == STOPPED) {

            commands = new String[]{
                    "ip6tables -D OUTPUT -j DROP || true",
                    "ip6tables -I OUTPUT -j DROP",
                    iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                    iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                    iptablesPath + "iptables -F tordnscrypt",
                    iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
                    busyboxPath + "sleep 1",
                    "TOR_UID=" + appUID,
                    iptablesPath + "iptables -t nat -N tordnscrypt_nat_output",
                    iptablesPath + "iptables -t nat -I OUTPUT -j tordnscrypt_nat_output",
                    iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                    iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                    iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                    iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + itpdHttpProxyPort,
                    iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp -d " + dnsCryptFallbackRes + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                    iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                    iptablesPath + "iptables -t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + dnsCryptPort,
                    blockHttpRuleNatTCP,
                    blockHttpRuleNatUDP,
                    iptablesPath + "iptables -N tordnscrypt",
                    iptablesPath + "iptables -A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                    iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                    iptablesPath + "iptables -A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + dnsCryptPort + " -m owner --uid-owner 0 -j ACCEPT",
                    iptablesPath + "iptables -A tordnscrypt -p udp -d " + dnsCryptFallbackRes + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                    blockHttpRuleFilterAll,
                    iptablesPath + "iptables -I OUTPUT -j tordnscrypt",
            };

            String[] commandsTether = tethering.activateTethering(false);
            if (commandsTether != null && commandsTether.length > 0)
                commands = Arr.ADD2(commands, commandsTether);
        } else if (dnsCryptState == STOPPED && torState == STOPPED) {

            commands = new String[]{
                    "ip6tables -D OUTPUT -j DROP || true",
                    iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                    iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                    iptablesPath + "iptables -F tordnscrypt",
                    iptablesPath + "iptables -A tordnscrypt -j RETURN",
                    iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",
            };

            String[] commandsTether = tethering.activateTethering(false);
            if (commandsTether != null && commandsTether.length > 0)
                commands = Arr.ADD2(commands, commandsTether);
        } else if (dnsCryptState == STOPPED && torState == RUNNING) {

            commands = new String[]{
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
                    busyboxPath + "cat "+appDataDir+"/app_data/tor/bridgesIP | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port "+torTransPort+"; done",
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
            };

            String[] commandsTether = tethering.activateTethering(true);
            if (commandsTether != null && commandsTether.length > 0)
                commands = Arr.ADD2(commands, commandsTether);
        } else if (itpdState == RUNNING) {
            commands = tethering.activateTethering(false);
        }

        return commands;
    }

    @Override
    public String[] clearAll() {
        return new String[]{
                "ip6tables -D OUTPUT -j DROP || true",
                iptablesPath + "iptables -t nat -F tordnscrypt_nat_output",
                iptablesPath + "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                iptablesPath + "iptables -F tordnscrypt",
                iptablesPath + "iptables -A tordnscrypt -j RETURN",
                iptablesPath + "iptables -D OUTPUT -j tordnscrypt || true",

                "ip6tables -D INPUT -j DROP || true",
                "ip6tables -D FORWARD -j DROP || true",
                iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                iptablesPath + "iptables -F tordnscrypt_forward",
                iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true"
        };
    }
}
