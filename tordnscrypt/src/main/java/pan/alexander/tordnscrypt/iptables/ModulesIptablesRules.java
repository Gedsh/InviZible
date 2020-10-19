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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.vpn.Util;

import static pan.alexander.tordnscrypt.iptables.Tethering.usbModemAddressesRange;
import static pan.alexander.tordnscrypt.iptables.Tethering.vpnInterfaceName;
import static pan.alexander.tordnscrypt.iptables.Tethering.wifiAPAddressesRange;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesDefault;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesOwn;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

public class ModulesIptablesRules extends IptablesRulesSender {

    String iptables = "iptables ";
    String ip6tables = "ip6tables ";
    String busybox = "busybox ";

    public ModulesIptablesRules(Context context) {
        super(context);
    }

    @Override
    public List<String> configureIptables(ModuleState dnsCryptState, ModuleState torState, ModuleState itpdState) {

        iptables = pathVars.getIptablesPath();
        ip6tables = pathVars.getIp6tablesPath();
        busybox = pathVars.getBusyboxPath();

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        runModulesWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        routeAllThroughTor = shPref.getBoolean("pref_fast_all_through_tor", true);
        lan = shPref.getBoolean("Allow LAN", false);
        blockHttp = shPref.getBoolean("pref_fast_block_http", false);
        apIsOn = new PrefManager(context).getBoolPref("APisON");
        modemIsOn = new PrefManager(context).getBoolPref("ModemIsON");
        Set<String> unlockApps = new PrefManager(context).getSetStrPref("unlockApps");
        Set<String> clearnetApps = new PrefManager(context).getSetStrPref("clearnetApps");
        Set<String> clearnetAppsForProxy = new PrefManager(context).getSetStrPref("clearnetAppsForProxy");

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        boolean ttlFix = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE) && !modulesStatus.isUseModulesWithRoot();
        boolean useProxy = shPref.getBoolean("swUseProxy", false);

        boolean arpSpoofingDetection = shPref.getBoolean("pref_common_arp_spoofing_detection", false);
        boolean blockInternetWhenArpAttackDetected = shPref.getBoolean("pref_common_arp_block_internet", false);
        boolean mitmDetected = ArpScanner.INSTANCE.getArpAttackDetected() || ArpScanner.INSTANCE.getDhcpGatewayAttackDetected();

        List<String> commands = new ArrayList<>();

        String appUID = new PrefManager(context).getStrPref("appUID");
        if (runModulesWithRoot) {
            appUID = "0";
        }

        String bypassLanNat = "";
        String bypassLanFilter = "";
        if (lan) {
            StringBuilder nonTorRanges = new StringBuilder();
            for (String address: Util.nonTorList) {
                nonTorRanges.append(address).append(" ");
            }
            nonTorRanges.deleteCharAt(nonTorRanges.lastIndexOf(" "));

            bypassLanNat = "non_tor=\"" + nonTorRanges + "\"; " +
                    "for _lan in $non_tor; do " +
                    iptables + "-t nat -A tordnscrypt_nat_output -d $_lan -j RETURN; " +
                    "done";
            bypassLanFilter = "non_tor=\"" + nonTorRanges + "\"; " +
                    "for _lan in $non_tor; do " +
                    iptables + "-A tordnscrypt -d $_lan -j RETURN; " +
                    "done";
        }

        String kernelBypassNat = "";
        String kernelBypassFilter = "";
        String kernelRedirectNatTCP = "";
        String kernelRejectNonTCP = "";
        if (routeAllThroughTor && (clearnetApps.contains("-1") || (ttlFix && useProxy && clearnetAppsForProxy.contains("-1")))) {
            kernelBypassNat = iptables + "-t nat -A tordnscrypt_nat_output -p all -m owner ! --uid-owner 0:999999999 -j RETURN || true";
            kernelBypassFilter = iptables + "-A tordnscrypt -p all -m owner ! --uid-owner 0:999999999 -j RETURN || true";
        } else if (!routeAllThroughTor && unlockApps.contains("-1") && (!clearnetAppsForProxy.contains("-1") || !useProxy || !ttlFix)) {
            kernelRedirectNatTCP = iptables + "-t nat -A tordnscrypt_nat_output -p tcp -m owner ! --uid-owner 0:999999999 -j REDIRECT --to-port " + pathVars.getTorTransPort() + " || true";
            kernelRejectNonTCP = iptables + "-A tordnscrypt ! -p tcp -m owner ! --uid-owner 0:999999999 -j REJECT || true";
        }

        String torSitesBypassNat = "";
        String torSitesBypassFilter = "";
        String torAppsBypassNat = "";
        String torAppsBypassFilter = "";
        if (routeAllThroughTor) {
            torSitesBypassNat = busybox + "cat " + appDataDir + "/app_data/tor/clearnet 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p all -d $var1 -j RETURN; done";
            torSitesBypassFilter = busybox + "cat " + appDataDir + "/app_data/tor/clearnet 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt -p all -d $var1 -j RETURN; done";
            torAppsBypassNat = busybox + "cat " + appDataDir + "/app_data/tor/clearnetApps 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p all -m owner --uid-owner $var1 -j RETURN; done";
            torAppsBypassFilter = busybox + "cat " + appDataDir + "/app_data/tor/clearnetApps 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt -p all -m owner --uid-owner $var1 -j RETURN; done";
        }

        String blockHttpRuleFilterAll = "";
        String blockHttpRuleNatTCP = "";
        String blockHttpRuleNatUDP = "";
        if (blockHttp) {
            blockHttpRuleFilterAll = iptables + "-A tordnscrypt -d +" + rejectAddress + " -j REJECT";
            blockHttpRuleNatTCP = iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 80 -j DNAT --to-destination " + rejectAddress;
            blockHttpRuleNatUDP = iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 80 -j DNAT --to-destination " + rejectAddress;
        }

        String unblockHOTSPOT = iptables + "-D FORWARD -j DROP 2> /dev/null || true";
        String blockHOTSPOT = iptables + "-I FORWARD -j DROP";
        if (apIsOn || modemIsOn) {
            blockHOTSPOT = "";
        }

        boolean dnsCryptSystemDNSAllowed = new PrefManager(context).getBoolPref("DNSCryptSystemDNSAllowed");

        //These rules will be removed after DNSCrypt and Tor are bootstrapped
        String dnsCryptSystemDNSAllowedNat = "";
        String dnsCryptSystemDNSAllowedFilter = "";
        String dnsCryptRootDNSAllowedNat = "";
        String dnsCryptRootDNSAllowedFilter = "";
        if (dnsCryptSystemDNSAllowed) {
            dnsCryptSystemDNSAllowedFilter = iptables + "-A tordnscrypt -p udp --dport 53 -m owner --uid-owner " + appUID + " -j ACCEPT";
            dnsCryptSystemDNSAllowedNat = iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -m owner --uid-owner " + appUID + " -j ACCEPT";
            if (!runModulesWithRoot) {
                dnsCryptRootDNSAllowedNat = iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -m owner --uid-owner 0 -j ACCEPT";
                dnsCryptRootDNSAllowedFilter = iptables + "-A tordnscrypt -p udp --dport 53 -m owner --uid-owner 0 -j ACCEPT";
            }
        }

        boolean torReady = new PrefManager(context).getBoolPref("Tor Ready");
        boolean useDefaultBridges = new PrefManager(context).getBoolPref("useDefaultBridges");
        boolean useOwnBridges = new PrefManager(context).getBoolPref("useOwnBridges");
        boolean bridgesSnowflakeDefault = new PrefManager(context).getStrPref("defaultBridgesObfs").equals(snowFlakeBridgesDefault);
        boolean bridgesSnowflakeOwn = new PrefManager(context).getStrPref("ownBridgesObfs").equals(snowFlakeBridgesOwn);

        String torSystemDNSAllowedNat = "";
        String torSystemDNSAllowedFilter = "";
        String torRootDNSAllowedNat = "";
        String torRootDNSAllowedFilter = "";
        if (!torReady && (useDefaultBridges && bridgesSnowflakeDefault || useOwnBridges && bridgesSnowflakeOwn)) {
            torSystemDNSAllowedFilter = iptables + "-A tordnscrypt -p udp --dport 53 -m owner --uid-owner " + appUID + " -j ACCEPT";
            torSystemDNSAllowedNat = iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -m owner --uid-owner " + appUID + " -j ACCEPT";
            if (!runModulesWithRoot) {
                torRootDNSAllowedNat = iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -m owner --uid-owner 0 -j ACCEPT";
                torRootDNSAllowedFilter = iptables + "-A tordnscrypt -p udp --dport 53 -m owner --uid-owner 0 -j ACCEPT";
            }
        }

        String proxyAppsBypassNat = "";
        String proxyAppsBypassFilter = "";
        if (ttlFix && useProxy) {
            proxyAppsBypassNat = busybox + "cat " + appDataDir + "/app_data/tor/clearnetAppsForProxy 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p all -m owner --uid-owner $var1 -j RETURN; done";
            proxyAppsBypassFilter = busybox + "cat " + appDataDir + "/app_data/tor/clearnetAppsForProxy 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt -p all -m owner --uid-owner $var1 -j RETURN; done";
        }

        if (arpSpoofingDetection && blockInternetWhenArpAttackDetected && mitmDetected) {
            commands = new ArrayList<>(Arrays.asList(
                    "TOR_UID=" + appUID,
                    iptables + "-I OUTPUT -j DROP",
                    ip6tables + "-D OUTPUT -j DROP 2> /dev/null || true",
                    ip6tables + "-D OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT 2> /dev/null || true",
                    ip6tables + "-I OUTPUT -j DROP",
                    ip6tables + "-I OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT",
                    iptables + "-t nat -F tordnscrypt_nat_output 2> /dev/null",
                    iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                    iptables + "-F tordnscrypt 2> /dev/null",
                    iptables + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",
                    busybox + "sleep 1",
                    iptables + "-N tordnscrypt 2> /dev/null",
                    iptables + "-A tordnscrypt -m owner ! --uid-owner $TOR_UID -j REJECT",
                    iptables + "-I OUTPUT -j tordnscrypt",
                    unblockHOTSPOT,
                    blockHOTSPOT,
                    iptables + "-D OUTPUT -j DROP 2> /dev/null || true"
            ));
        } else if (dnsCryptState == RUNNING && torState == RUNNING) {

            if (!routeAllThroughTor) {


                commands = new ArrayList<>(Arrays.asList(
                        "TOR_UID=" + appUID,
                        iptables + "-I OUTPUT -j DROP",
                        ip6tables + "-D OUTPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-D OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT 2> /dev/null || true",
                        ip6tables + "-I OUTPUT -j DROP",
                        ip6tables + "-I OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-t nat -F tordnscrypt_nat_output 2> /dev/null",
                        iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                        iptables + "-F tordnscrypt 2> /dev/null",
                        iptables + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_nat_output 2> /dev/null",
                        iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptables + "-t nat -A tordnscrypt_nat_output -p all -d 127.0.0.1/32 -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                        dnsCryptSystemDNSAllowedNat,
                        dnsCryptRootDNSAllowedNat,
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        iptables + "-N tordnscrypt 2> /dev/null",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getDNSCryptPort() + " -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getDNSCryptPort() + " -j ACCEPT",
                        dnsCryptSystemDNSAllowedFilter,
                        dnsCryptRootDNSAllowedFilter,
                        iptables + "-A tordnscrypt -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        blockHttpRuleFilterAll,
                        proxyAppsBypassNat,
                        bypassLanNat,
                        //Redirect TCP sites to Tor
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        //Redirect TCP apps to Tor
                        busybox + "cat " + appDataDir + "/app_data/tor/unlockApps 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        kernelRedirectNatTCP,
                        proxyAppsBypassFilter,
                        bypassLanFilter,
                        //Block all except TCP for Tor sites
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt ! -p tcp -d $var1 -j REJECT; done",
                        //Block all except TCP for Tor apps
                        busybox + "cat " + appDataDir + "/app_data/tor/unlockApps 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt ! -p tcp -m owner --uid-owner $var1 -j REJECT; done",
                        kernelRejectNonTCP,
                        iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptables + "-I OUTPUT -j tordnscrypt",
                        unblockHOTSPOT,
                        blockHOTSPOT,
                        iptables + "-D OUTPUT -j DROP 2> /dev/null || true"
                ));
            } else {

                commands = new ArrayList<>(Arrays.asList(
                        "TOR_UID=" + appUID,
                        iptables + "-I OUTPUT -j DROP",
                        ip6tables + "-D OUTPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-D OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT 2> /dev/null || true",
                        ip6tables + "-I OUTPUT -j DROP",
                        ip6tables + "-I OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-t nat -F tordnscrypt_nat_output 2> /dev/null",
                        iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                        iptables + "-F tordnscrypt 2> /dev/null",
                        iptables + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_nat_output 2> /dev/null",
                        iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptables + "-t nat -A tordnscrypt_nat_output -p all -d 127.0.0.1/32 -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                        dnsCryptSystemDNSAllowedNat,
                        dnsCryptRootDNSAllowedNat,
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        torSitesBypassNat,
                        torAppsBypassNat,
                        kernelBypassNat,
                        proxyAppsBypassNat,
                        bypassLanNat,
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        iptables + "-N tordnscrypt 2> /dev/null",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getDNSCryptPort() + " -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getDNSCryptPort() + " -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p all -j RETURN",
                        iptables + "-A tordnscrypt -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-A tordnscrypt -m owner --uid-owner $TOR_UID -j RETURN",
                        dnsCryptSystemDNSAllowedFilter,
                        dnsCryptRootDNSAllowedFilter,
                        blockHttpRuleFilterAll,
                        iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        torSitesBypassFilter,
                        torAppsBypassFilter,
                        kernelBypassFilter,
                        proxyAppsBypassFilter,
                        bypassLanFilter,
                        iptables + "-A tordnscrypt -j REJECT",
                        iptables + "-I OUTPUT -j tordnscrypt",
                        unblockHOTSPOT,
                        blockHOTSPOT,
                        iptables + "-D OUTPUT -j DROP 2> /dev/null || true"
                ));
            }

            List<String> commandsTether = tethering.activateTethering(false);
            if (commandsTether.size() > 0)
                commands.addAll(commandsTether);
        } else if (dnsCryptState == RUNNING && torState == STOPPED) {

            commands = new ArrayList<>(Arrays.asList(
                    "TOR_UID=" + appUID,
                    iptables + "-I OUTPUT -j DROP",
                    ip6tables + "-D OUTPUT -j DROP 2> /dev/null || true",
                    ip6tables + "-D OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT 2> /dev/null || true",
                    ip6tables + "-I OUTPUT -j DROP",
                    ip6tables + "-I OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT",
                    iptables + "-t nat -F tordnscrypt_nat_output 2> /dev/null",
                    iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                    iptables + "-F tordnscrypt 2> /dev/null",
                    iptables + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",
                    busybox + "sleep 1",
                    iptables + "-t nat -N tordnscrypt_nat_output 2> /dev/null",
                    iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                    iptables + "-t nat -A tordnscrypt_nat_output -p all -d 127.0.0.1/32 -j RETURN",
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp -d 10.191.0.1 -j DNAT --to-destination 127.0.0.1:" + pathVars.getITPDHttpProxyPort(),
                    dnsCryptSystemDNSAllowedNat,
                    dnsCryptRootDNSAllowedNat,
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                    iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                    iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getDNSCryptPort(),
                    blockHttpRuleNatTCP,
                    blockHttpRuleNatUDP,
                    iptables + "-N tordnscrypt 2> /dev/null",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getDNSCryptPort() + " -j ACCEPT",
                    iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getDNSCryptPort() + " -j ACCEPT",
                    dnsCryptSystemDNSAllowedFilter,
                    dnsCryptRootDNSAllowedFilter,
                    iptables + "-A tordnscrypt -p udp -d " + pathVars.getDNSCryptFallbackRes() + " --dport 53 -m owner --uid-owner $TOR_UID -j ACCEPT",
                    blockHttpRuleFilterAll,
                    iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                    iptables + "-I OUTPUT -j tordnscrypt",
                    unblockHOTSPOT,
                    blockHOTSPOT,
                    iptables + "-D OUTPUT -j DROP 2> /dev/null || true"
            ));

            List<String> commandsTether = tethering.activateTethering(false);
            if (commandsTether.size() > 0)
                commands.addAll(commandsTether);
        } else if (dnsCryptState == STOPPED && torState == STOPPED) {

            commands = new ArrayList<>(Arrays.asList(
                    "TOR_UID=" + appUID,
                    ip6tables + "-D OUTPUT -j DROP 2> /dev/null || true",
                    ip6tables + "-D OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT 2> /dev/null || true",
                    iptables + "-t nat -F tordnscrypt_nat_output 2> /dev/null || true",
                    iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                    iptables + "-F tordnscrypt 2> /dev/null || true",
                    iptables + "-A tordnscrypt -j RETURN 2> /dev/null || true",
                    iptables + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",
                    unblockHOTSPOT
            ));

            List<String> commandsTether = tethering.activateTethering(false);
            if (commandsTether.size() > 0)
                commands.addAll(commandsTether);
        } else if (dnsCryptState == STOPPED && torState == RUNNING) {

            if (!routeAllThroughTor) {
                commands = new ArrayList<>(Arrays.asList(
                        "TOR_UID=" + appUID,
                        iptables + "-I OUTPUT -j DROP",
                        ip6tables + "-D OUTPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-D OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT 2> /dev/null || true",
                        ip6tables + "-I OUTPUT -j DROP",
                        ip6tables + "-I OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-t nat -F tordnscrypt_nat_output 2> /dev/null",
                        iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                        iptables + "-F tordnscrypt 2> /dev/null",
                        iptables + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_nat_output 2> /dev/null",
                        iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptables + "-t nat -A tordnscrypt_nat_output -p all -d 127.0.0.1/32 -j RETURN",
                        torSystemDNSAllowedNat,
                        torRootDNSAllowedNat,
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorDNSPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorDNSPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        iptables + "-N tordnscrypt 2> /dev/null",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getTorDNSPort() + " -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorDNSPort() + " -j ACCEPT",
                        torSystemDNSAllowedFilter,
                        torRootDNSAllowedFilter,
                        blockHttpRuleFilterAll,
                        proxyAppsBypassNat,
                        bypassLanNat,
                        //Redirect TCP sites to Tor
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        //Redirect TCP apps to Tor
                        busybox + "cat " + appDataDir + "/app_data/tor/unlockApps 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -m owner --uid-owner $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        kernelRedirectNatTCP,
                        //Bypass proxy apps
                        proxyAppsBypassFilter,
                        bypassLanFilter,
                        //Block all except TCP for Tor sites
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt ! -p tcp -d $var1 -j REJECT; done",
                        //Block all except TCP for Tor apps
                        busybox + "cat " + appDataDir + "/app_data/tor/unlockApps 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt ! -p tcp -m owner --uid-owner $var1 -j REJECT; done",
                        kernelRejectNonTCP,
                        iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptables + "-I OUTPUT -j tordnscrypt",
                        unblockHOTSPOT,
                        blockHOTSPOT,
                        iptables + "-D OUTPUT -j DROP 2> /dev/null || true"
                ));
            } else {
                commands = new ArrayList<>(Arrays.asList(
                        "TOR_UID=" + appUID,
                        iptables + "-I OUTPUT -j DROP",
                        ip6tables + "-D OUTPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-D OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT 2> /dev/null || true",
                        ip6tables + "-I OUTPUT -j DROP",
                        ip6tables + "-I OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT",
                        iptables + "-t nat -F tordnscrypt_nat_output 2> /dev/null",
                        iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                        iptables + "-F tordnscrypt 2> /dev/null",
                        iptables + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",
                        iptables + "-t nat -N tordnscrypt_nat_output 2> /dev/null",
                        iptables + "-t nat -I OUTPUT -j tordnscrypt_nat_output",
                        iptables + "-t nat -A tordnscrypt_nat_output -p all -d 127.0.0.1/32 -j RETURN",
                        torSystemDNSAllowedNat,
                        torRootDNSAllowedNat,
                        iptables + "-t nat -A tordnscrypt_nat_output -p udp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorDNSPort(),
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp --dport 53 -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorDNSPort(),
                        //busybox + "cat " + appDataDir + "/app_data/tor/bridgesIP 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        iptables + "-t nat -A tordnscrypt_nat_output -m owner --uid-owner $TOR_UID -j RETURN",
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        blockHttpRuleNatTCP,
                        blockHttpRuleNatUDP,
                        torSitesBypassNat,
                        torAppsBypassNat,
                        kernelBypassNat,
                        proxyAppsBypassNat,
                        bypassLanNat,
                        iptables + "-t nat -A tordnscrypt_nat_output -p tcp -j DNAT --to-destination 127.0.0.1:" + pathVars.getTorTransPort(),
                        iptables + "-N tordnscrypt 2> /dev/null",
                        torSystemDNSAllowedFilter,
                        torRootDNSAllowedFilter,
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport " + pathVars.getTorDNSPort() + " -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport " + pathVars.getTorDNSPort() + " -j ACCEPT",
                        iptables + "-A tordnscrypt -d 127.0.0.1/32 -p all -j RETURN",
                        iptables + "-A tordnscrypt -m owner --uid-owner $TOR_UID -j RETURN",
                        blockHttpRuleFilterAll,
                        iptables + "-A tordnscrypt -m state --state ESTABLISHED,RELATED -j RETURN",
                        torSitesBypassFilter,
                        torAppsBypassFilter,
                        kernelBypassFilter,
                        proxyAppsBypassFilter,
                        bypassLanFilter,
                        iptables + "-A tordnscrypt -j REJECT",
                        iptables + "-I OUTPUT -j tordnscrypt",
                        unblockHOTSPOT,
                        blockHOTSPOT,
                        iptables + "-D OUTPUT -j DROP 2> /dev/null || true"
                ));

            }


            List<String> commandsTether = tethering.activateTethering(true);
            if (commandsTether.size() > 0)
                commands.addAll(commandsTether);
        } else if (itpdState == RUNNING) {
            commands = tethering.activateTethering(false);
        }

        return commands;
    }

    @Override
    public List<String> clearAll() {
        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        if (modulesStatus.isFixTTL()) {
            modulesStatus.setIptablesRulesUpdateRequested(context, true);
        }

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        runModulesWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        String appUID = new PrefManager(context).getStrPref("appUID");
        if (runModulesWithRoot) {
            appUID = "0";
        }

        return new ArrayList<>(Arrays.asList(
                "TOR_UID=" + appUID,
                ip6tables + "-D OUTPUT -j DROP 2> /dev/null || true",
                ip6tables + "-D OUTPUT -m owner --uid-owner $TOR_UID -j ACCEPT 2> /dev/null || true",
                iptables + "-t nat -F tordnscrypt_nat_output 2> /dev/null || true",
                iptables + "-t nat -D OUTPUT -j tordnscrypt_nat_output 2> /dev/null || true",
                iptables + "-F tordnscrypt 2> /dev/null || true",
                iptables + "-A tordnscrypt -j RETURN 2> /dev/null || true",
                iptables + "-D OUTPUT -j tordnscrypt 2> /dev/null || true",

                ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null || true",
                iptables + "-F tordnscrypt_forward 2> /dev/null || true",
                iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                iptables + "-D FORWARD -j DROP 2> /dev/null || true",

                "ip rule delete from " + wifiAPAddressesRange + " lookup 63 2> /dev/null || true",
                "ip rule delete from " + usbModemAddressesRange + " lookup 62 2> /dev/null || true"
        ));
    }

    @Override
    public void refreshFixTTLRules() {
        String savedVpnInterfaceName = vpnInterfaceName;
        String savedWifiAPInterfaceName = Tethering.wifiAPInterfaceName;
        String savedUsbModemInterfaceName = Tethering.usbModemInterfaceName;

        tethering.setInterfaceNames();

        if (!vpnInterfaceName.equals(savedVpnInterfaceName)
                || !Tethering.wifiAPInterfaceName.equals(savedWifiAPInterfaceName)
                || !Tethering.usbModemInterfaceName.equals(savedUsbModemInterfaceName)) {

            sendToRootExecService(tethering.fixTTLCommands());

            Log.i(LOG_TAG, "ModulesIptablesRules Refresh Fix TTL Rules vpnInterfaceName = " + vpnInterfaceName);
        }
    }

    public static void denySystemDNS(Context context) {

        String iptables = PathVars.getInstance(context).getIptablesPath();

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean runModulesWithRoot = shPref.getBoolean("swUseModulesRoot", false);
        String appUID = new PrefManager(context).getStrPref("appUID");
        if (runModulesWithRoot) {
            appUID = "0";
        }

        List<String> commands = new ArrayList<>(Arrays.asList(
                iptables + "-D tordnscrypt -p udp --dport 53 -m owner --uid-owner " + appUID + " -j ACCEPT 2> /dev/null || true",
                iptables + "-t nat -D tordnscrypt_nat_output -p udp --dport 53 -m owner --uid-owner " + appUID + " -j ACCEPT 2> /dev/null || true"
        ));

        if (!runModulesWithRoot) {
            List<String> commandsNoRunModulesWithRoot = new ArrayList<>(Arrays.asList(
                    iptables + "-D tordnscrypt -p udp --dport 53 -m owner --uid-owner 0 -j ACCEPT 2> /dev/null || true",
                    iptables + "-t nat -D tordnscrypt_nat_output -p udp --dport 53 -m owner --uid-owner 0 -j ACCEPT 2> /dev/null || true"
            ));

            commands.addAll(commandsNoRunModulesWithRoot);
        }

        executeCommands(context, commands);
    }

    public static String blockTethering(Context context) {
        String iptables = PathVars.getInstance(context).getIptablesPath();

        List<String> commands = new ArrayList<>(Collections.singletonList(
                iptables + "-I FORWARD -j DROP"
        ));

        executeCommands(context, commands);

        return vpnInterfaceName;
    }

    public static void allowTethering(Context context, String oldVpnInterfaceName) {
        String iptables = PathVars.getInstance(context).getIptablesPath();


        ArrayList<String> commands;

        if (oldVpnInterfaceName.equals(vpnInterfaceName)) {
            commands = new ArrayList<>(Collections.singletonList(
                    iptables + "-D FORWARD -j DROP 2> /dev/null || true"
            ));
        } else {
            commands = new ArrayList<>(Arrays.asList(
                    iptables + "-D FORWARD -j DROP 2> /dev/null || true",
                    iptables + "-D tordnscrypt_forward -o !" + oldVpnInterfaceName + " -j REJECT 2> /dev/null || true"
            ));
        }

        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            new Handler(looper).postDelayed(() -> executeCommands(context, commands), 1000);
        }
    }

    private static void executeCommands(Context context, List<String> commands) {
        RootCommands rootCommands = new RootCommands(commands);
        Intent intent = new Intent(context, RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands", rootCommands);
        intent.putExtra("Mark", RootExecService.NullMark);
        RootExecService.performAction(context, intent);
    }
}
