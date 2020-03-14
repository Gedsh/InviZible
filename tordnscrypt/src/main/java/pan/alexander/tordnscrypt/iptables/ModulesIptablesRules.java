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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;

import static pan.alexander.tordnscrypt.iptables.Tethering.usbModemAddressesRange;
import static pan.alexander.tordnscrypt.iptables.Tethering.wifiAPAddressesRange;
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
        apIsOn = new PrefManager(context).getBoolPref("APisON");
        modemIsOn = new PrefManager(context).getBoolPref("ModemIsON");

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
            torSitesBypassNatTCP = busybox + "cat " + appDataDir + "/app_data/tor/clearnet | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j RETURN; done";
            torSitesBypassFilterTCP = busybox + "cat " + appDataDir + "/app_data/tor/clearnet | while read var1; do " + iptables + "-A tordnscrypt -p tcp -d $var1 -j RETURN; done";
            torSitesBypassNatUDP = busybox + "cat " + appDataDir + "/app_data/tor/clearnet | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p udp -d $var1 -j RETURN; done";
            torSitesBypassFilterUDP = busybox + "cat " + appDataDir + "/app_data/tor/clearnet | while read var1; do " + iptables + "-A tordnscrypt -p udp -d $var1 -j RETURN; done";
            torAppsBypassNatTCP = busybox + "cat " + appDataDir + "/app_data/tor/clearnetApps | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j RETURN; done";
            torAppsBypassNatUDP = busybox + "cat " + appDataDir + "/app_data/tor/clearnetApps | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p udp -m owner --uid-owner $var1 -j RETURN; done";
            torAppsBypassFilterTCP = busybox + "cat " + appDataDir + "/app_data/tor/clearnetApps | while read var1; do " + iptables + "-A tordnscrypt -p tcp -m owner --uid-owner $var1 -j RETURN; done";
            torAppsBypassFilterUDP = busybox + "cat " + appDataDir + "/app_data/tor/clearnetApps | while read var1; do " + iptables + "-A tordnscrypt -p udp -m owner --uid-owner $var1 -j RETURN; done";
        }

        String blockHttpRuleFilterAll = "";
        String blockHttpRuleNatTCP = "";
        String blockHttpRuleNatUDP = "";
        if (blockHttp) {
            blockHttpRuleFilterAll = iptables + "-A tordnscrypt -d +" + rejectAddress + " -j REJECT";
            blockHttpRuleNatTCP = iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 80 -j DNAT --to-destination " + rejectAddress;
            blockHttpRuleNatUDP = iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 80 -j DNAT --to-destination " + rejectAddress;
        }

        String unblockHOTSPOT = iptables + "-D FORWARD -j DROP || true";
        String blockHOTSPOT = iptables + "-I FORWARD -j DROP";
        if (apIsOn || modemIsOn) {
            blockHOTSPOT = "";
        }


        if (dnsCryptState == RUNNING && torState == RUNNING) {

            if (!routeAllThroughTor) {


                commands = new String[]{
                        iptables + "-I OUTPUT -j DROP",
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptables + "-t nat -F tordnscrypt_nat_output",
                        iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptables + "-F tordnscrypt",
                        iptables + "-D OUTPUT -j tordnscrypt || true",
                        busybox + "sleep 1",
                        "TOR_UID=" + appUID,
                        iptables + "-t nat -N tordnscrypt_nat_output",
                        iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        iptables + "-N tordnscrypt",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getDNSCryptPort() + " -m owner --uid-owner 0 -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getDNSCryptPort() + " -m owner --uid-owner 0 -j ACCEPT",
                        iptables + "-A tordnscrypt -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptables + "-I OUTPUT -j tordnscrypt",
                        busybox + "cat " + appDataDir + "/app_data/tor/bridgesIP | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        busybox + "cat " + appDataDir + "/app_data/tor/unlockApps | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        unblockHOTSPOT,
                        blockHOTSPOT,
                        iptables + "-D OUTPUT -j DROP || true"
                };
            } else {

                commands = new String[]{
                        iptables + "-I OUTPUT -j DROP",
                        "ip6tables -D OUTPUT -j DROP || true",
                        "ip6tables -I OUTPUT -j DROP",
                        iptables + "-t nat -F tordnscrypt_nat_output",
                        iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                        iptables + "-F tordnscrypt",
                        iptables + "-D OUTPUT -j tordnscrypt || true",
                        busybox + "sleep 1",
                        "TOR_UID=" + appUID,
                        iptables + "-t nat -N tordnscrypt_nat_output",
                        iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                        busybox + "cat " + appDataDir + "/app_data/tor/bridgesIP | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        iptables + "-t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        torSitesBypassNatTCP,
                        torSitesBypassNatUDP,
                        torAppsBypassNatTCP,
                        torAppsBypassNatUDP,
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        iptables + "-N tordnscrypt",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorSOCKSPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getTorSOCKSPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorHTTPTunnelPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getTorHTTPTunnelPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getITPDSOCKSPort()+ " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getITPDSOCKSPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getITPDHttpProxyPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getITPDHttpProxyPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorTransPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getDNSCryptPort() + " -m owner --uid-owner 0 -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getDNSCryptPort() + " -m owner --uid-owner 0 -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getDNSCryptPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getDNSCryptPort() + " -j RETURN",
                        iptables + "-A tordnscrypt -m owner --uid-owner $TOR_UID -j RETURN",
                        iptables + "-A tordnscrypt -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        torSitesBypassFilterTCP,
                        torSitesBypassFilterUDP,
                        torAppsBypassFilterTCP,
                        torAppsBypassFilterUDP,
                        iptables + "-A tordnscrypt -j REJECT",
                        iptables + "-I OUTPUT -j tordnscrypt",
                        unblockHOTSPOT,
                        blockHOTSPOT,
                        iptables + "-D OUTPUT -j DROP || true"
                };
            }

            String[] commandsTether = tethering.activateTethering(false);
            if (commandsTether != null && commandsTether.length > 0)
                commands = Arr.ADD2(commands, commandsTether);
        } else if (dnsCryptState == RUNNING && torState == STOPPED) {

            commands = new String[]{
                    iptables + "-I OUTPUT -j DROP",
                    "ip6tables -D OUTPUT -j DROP || true",
                    "ip6tables -I OUTPUT -j DROP",
                    iptables + "-t nat -F tordnscrypt_nat_output",
                    iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                    iptables + "-F tordnscrypt",
                    iptables + "-D OUTPUT -j tordnscrypt || true",
                    busybox + "sleep 1",
                    "TOR_UID=" + appUID,
                    iptables + "-t nat -N tordnscrypt_nat_output",
                    iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                    blockHttpRuleNatTCP,
                    blockHttpRuleNatUDP,
                    iptables + "-N tordnscrypt",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getDNSCryptPort() + " -m owner --uid-owner 0 -j ACCEPT",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getDNSCryptPort() + " -m owner --uid-owner 0 -j ACCEPT",
                    iptables + "-A tordnscrypt -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                    blockHttpRuleFilterAll,
                    iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                    iptables + "-I OUTPUT -j tordnscrypt",
                    unblockHOTSPOT,
                    blockHOTSPOT,
                    iptables + "-D OUTPUT -j DROP || true"
            };

            String[] commandsTether = tethering.activateTethering(false);
            if (commandsTether != null && commandsTether.length > 0)
                commands = Arr.ADD2(commands, commandsTether);
        } else if (dnsCryptState == STOPPED && torState == STOPPED) {

            commands = new String[]{
                    "ip6tables -D OUTPUT -j DROP || true",
                    iptables + "-t nat -F tordnscrypt_nat_output",
                    iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                    iptables + "-F tordnscrypt",
                    iptables + "-A tordnscrypt -j RETURN",
                    iptables + "-D OUTPUT -j tordnscrypt || true",
                    unblockHOTSPOT
            };

            String[] commandsTether = tethering.activateTethering(false);
            if (commandsTether != null && commandsTether.length > 0)
                commands = Arr.ADD2(commands, commandsTether);
        } else if (dnsCryptState == STOPPED && torState == RUNNING) {

            commands = new String[]{
                    iptables + "-I OUTPUT -j DROP",
                    iptables + "-t nat -F tordnscrypt_nat_output",
                    iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                    iptables + "-F tordnscrypt",
                    iptables + "-D OUTPUT -j tordnscrypt || true",
                    "TOR_UID=" + appUID,
                    iptables + "-t nat -N tordnscrypt_nat_output",
                    iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 127.0.0.1/32 -j RETURN",
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 127.0.0.1/32 -j RETURN",
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorDNSPort(),
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorDNSPort(),
                    busybox + "cat " + appDataDir + "/app_data/tor/bridgesIP | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                    iptables + "-t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                    blockHttpRuleNatTCP,
                    blockHttpRuleNatUDP,
                    torSitesBypassNatTCP,
                    torSitesBypassNatUDP,
                    torAppsBypassNatTCP,
                    torAppsBypassNatUDP,
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                    iptables + "-N tordnscrypt",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorSOCKSPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getTorSOCKSPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorHTTPTunnelPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getTorHTTPTunnelPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getITPDSOCKSPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getITPDSOCKSPort()  + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getITPDHttpProxyPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getITPDHttpProxyPort()  + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorTransPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getTorDNSPort() + " -m owner --uid-owner 0 -j ACCEPT",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorDNSPort() + " -m owner --uid-owner 0 -j ACCEPT",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getTorDNSPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorDNSPort() + " -j RETURN",
                    iptables + "-A tordnscrypt -m owner --uid-owner $TOR_UID -j RETURN",
                    blockHttpRuleFilterAll,
                    iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                    torSitesBypassFilterTCP,
                    torSitesBypassFilterUDP,
                    torAppsBypassFilterTCP,
                    torAppsBypassFilterUDP,
                    iptables + "-A tordnscrypt -j REJECT",
                    iptables + "-I OUTPUT -j tordnscrypt",
                    unblockHOTSPOT,
                    blockHOTSPOT,
                    iptables + "-D OUTPUT -j DROP || true"
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
        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        if (modulesStatus.isFixTTL()) {
            modulesStatus.setIptablesRulesUpdateRequested(true);
        }

        return new String[]{
                "ip6tables -D OUTPUT -j DROP || true",
                iptables + "-t nat -F tordnscrypt_nat_output",
                iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                iptables + "-F tordnscrypt",
                iptables + "-A tordnscrypt -j RETURN",
                iptables + "-D OUTPUT -j tordnscrypt || true",

                "ip6tables -D INPUT -j DROP || true",
                "ip6tables -D FORWARD -j DROP || true",
                iptables + "-t nat -F tordnscrypt_prerouting",
                iptables + "-F tordnscrypt_forward",
                iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                iptables + "-D FORWARD -j tordnscrypt_forward || true",
                iptables + "-D FORWARD -j DROP || true",

                "ip rule delete from " + wifiAPAddressesRange + " lookup 63",
                "ip rule delete from " + usbModemAddressesRange + " lookup 62",
        };
    }
}
