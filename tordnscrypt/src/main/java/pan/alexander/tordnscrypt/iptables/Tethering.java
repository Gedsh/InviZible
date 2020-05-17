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
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Set;

import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.Arr;
import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

public class Tethering {
    private Context context;

    public static volatile boolean usbTetherOn = false;

    static final String wifiAPAddressesRange = "192.168.43.0/24";
    static final String usbModemAddressesRange = "192.168.42.0/24";
    private static final String addressVPN = "10.1.10.1";

    private static String vpnInterfaceName = "tun0";
    private static String wifiAPInterfaceName = "wlan0";
    private static String usbModemInterfaceName = "rndis0";

    private PathVars pathVars;
    private String iptables;
    private String ip6tables;
    private String appDataDir;
    private String busybox;
    private boolean apIsOn = false;

    Tethering(Context context) {
        this.context = context;
        pathVars = PathVars.getInstance(context);
        iptables = pathVars.getIptablesPath();
        ip6tables = pathVars.getIp6tablesPath();
        appDataDir = pathVars.getAppDataDir();
        busybox = pathVars.getBusyboxPath();
    }

    String[] activateTethering(boolean privacyMode) {

        if (context == null) {
            return new String[]{""};
        }

        setInterfaceNames();

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        boolean dnsCryptRunning = modulesStatus.getDnsCryptState() == RUNNING;
        boolean torRunning = modulesStatus.getTorState() == RUNNING;
        boolean itpdRunning = modulesStatus.getItpdState() == RUNNING;


        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean torTethering = shPref.getBoolean("pref_common_tor_tethering", false) && torRunning;
        boolean itpdTethering = shPref.getBoolean("pref_common_itpd_tethering", false) && itpdRunning;
        boolean routeAllThroughTorTether = shPref.getBoolean("pref_common_tor_route_all", false);
        boolean blockHotspotHttp = shPref.getBoolean("pref_common_block_http", false);
        boolean ttlFix = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE) && !modulesStatus.isUseModulesWithRoot();
        apIsOn = new PrefManager(context).getBoolPref("APisON");

        String torSitesBypassPreroutingTCP = "";
        String torSitesBypassForwardTCP = "";
        String torSitesBypassPreroutingUDP = "";
        String torSitesBypassForwardUDP = "";
        if (routeAllThroughTorTether) {
            torSitesBypassPreroutingTCP = busybox + "cat " + appDataDir + "/app_data/tor/clearnet_tether 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_prerouting -p tcp -d $var1 -j ACCEPT; done";
            torSitesBypassForwardTCP = busybox + "cat " + appDataDir + "/app_data/tor/clearnet_tether 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt_forward -p tcp -d $var1 -j ACCEPT; done";
            torSitesBypassPreroutingUDP = busybox + "cat " + appDataDir + "/app_data/tor/clearnet_tether 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_prerouting -p udp -d $var1 -j ACCEPT; done";
            torSitesBypassForwardUDP = busybox + "cat " + appDataDir + "/app_data/tor/clearnet_tether 2> /dev/null | while read var1; do " + iptables + "-A tordnscrypt_forward -p udp -d $var1 -j ACCEPT; done";

        }

        String blockHttpRuleForwardTCP = "";
        String blockHttpRuleForwardUDP = "";
        String blockHttpRulePreroutingTCPwifi = "";
        String blockHttpRulePreroutingUDPwifi = "";
        String blockHttpRulePreroutingTCPusb = "";
        String blockHttpRulePreroutingUDPusb = "";
        if (blockHotspotHttp) {
            blockHttpRuleForwardTCP = iptables + "-A tordnscrypt_forward -p tcp --dport 80 -j REJECT";
            blockHttpRuleForwardUDP = iptables + "-A tordnscrypt_forward -p udp --dport 80 -j REJECT";
            blockHttpRulePreroutingTCPwifi = iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp ! -d " + wifiAPAddressesRange + " --dport 80 -j RETURN";
            blockHttpRulePreroutingUDPwifi = iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p udp ! -d " + wifiAPAddressesRange + " --dport 80 -j RETURN";
            blockHttpRulePreroutingTCPusb = iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp ! -d " + usbModemAddressesRange + " --dport 80 -j RETURN";
            blockHttpRulePreroutingUDPusb = iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p udp ! -d " + usbModemAddressesRange + " --dport 80 -j RETURN";
        }


        String[] bypassITPDTunnelPorts = new String[]{""};
        Set<String> ports = new PrefManager(context).getSetStrPref("ITPDTunnelsPorts");
        if (ports != null && ports.size() > 0) {
            bypassITPDTunnelPorts = new String[ports.size() * 2];
            int i = 0;
            for (String port : ports) {
                if (!port.isEmpty()) {
                    bypassITPDTunnelPorts[i] = iptables + "-t nat -A tordnscrypt_prerouting -p tcp -m tcp --dport " + port + " -j ACCEPT";
                    i++;
                    bypassITPDTunnelPorts[i] = iptables + "-t nat -A tordnscrypt_prerouting -p udp -m udp --dport " + port + " -j ACCEPT";
                    i++;
                }
            }
        }

        String[] tetheringCommands = new String[]{""};
        boolean tetherIptablesRulesIsClean = new PrefManager(context).getBoolPref("TetherIptablesRulesIsClean");
        boolean ttlFixed = new PrefManager(context).getBoolPref("TTLisFixed");

        if (!torTethering && !itpdTethering && ((!apIsOn && !usbTetherOn) || !dnsCryptRunning)) {

            if (tetherIptablesRulesIsClean) {
                return tetheringCommands;
            }

            new PrefManager(context).setBoolPref("TetherIptablesRulesIsClean", true);

            tetheringCommands = new String[]{
                    ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                    ip6tables + "-I INPUT -j DROP || true",
                    ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                    ip6tables + "-I FORWARD -j DROP",
                    iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                    iptables + "-F tordnscrypt_forward 2> /dev/null",
                    iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                    iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true"
            };

            if (ttlFixed) {
                tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
            }

        } else if (!privacyMode) {

            new PrefManager(context).setBoolPref("TetherIptablesRulesIsClean", false);

            if (!torTethering && !itpdTethering) {
                tetheringCommands = new String[]{
                        iptables + "-I FORWARD -j DROP",
                        ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-I INPUT -j DROP || true",
                        ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                        ip6tables + "-I FORWARD -j DROP",
                        iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-F tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                        iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-N tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptables + "-A FORWARD -j tordnscrypt_forward",
                        busybox + "sleep 1",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busybox + "sleep 1",
                        blockHttpRulePreroutingTCPwifi,
                        blockHttpRulePreroutingUDPwifi,
                        blockHttpRulePreroutingTCPusb,
                        blockHttpRulePreroutingUDPusb,
                        busybox + "sleep 1",
                        iptables + "-A tordnscrypt_forward -p tcp --dport 53 -j ACCEPT",
                        iptables + "-A tordnscrypt_forward -p udp --dport 53 -j ACCEPT",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        iptables + "-D FORWARD -j DROP 2> /dev/null || true"
                };

                if (ttlFix) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, fixTTLCommands());
                } else if (ttlFixed) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
                }

            } else if (torTethering && routeAllThroughTorTether && itpdTethering) {
                tetheringCommands = new String[]{
                        iptables + "-I FORWARD -j DROP",
                        ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-I INPUT -j DROP || true",
                        ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                        ip6tables + "-I FORWARD -j DROP",
                        iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-F tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                        iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-N tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptables + "-A FORWARD -j tordnscrypt_forward",
                        busybox + "sleep 1",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busybox + "sleep 1",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + wifiAPAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + usbModemAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        blockHttpRulePreroutingTCPwifi,
                        blockHttpRulePreroutingUDPwifi,
                        blockHttpRulePreroutingTCPusb,
                        blockHttpRulePreroutingUDPusb,
                        torSitesBypassPreroutingTCP,
                        torSitesBypassPreroutingUDP,
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -m tcp --dport " + pathVars.getTorSOCKSPort() + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -p udp -m udp --dport " + pathVars.getTorSOCKSPort() + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -m tcp --dport " + pathVars.getITPDSOCKSPort() + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -p udp -m udp --dport " + pathVars.getITPDSOCKSPort() + " -j ACCEPT",
                };

                String[] tetheringCommandsPart2 = {
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        iptables + "-A tordnscrypt_forward -p udp --dport 53 -j ACCEPT",
                        iptables + "-A tordnscrypt_forward -p tcp --dport 53 -j ACCEPT",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        torSitesBypassForwardTCP,
                        torSitesBypassForwardUDP,
                        iptables + "-A tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptables + "-A tordnscrypt_forward -j REJECT",
                        iptables + "-D FORWARD -j DROP 2> /dev/null || true"
                };

                tetheringCommands = Arr.ADD3(tetheringCommands, bypassITPDTunnelPorts, tetheringCommandsPart2);

                if (ttlFix) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, fixTTLCommands());
                } else if (ttlFixed) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
                }

            } else if (torTethering && itpdTethering) {
                tetheringCommands = new String[]{
                        iptables + "-I FORWARD -j DROP",
                        ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-I INPUT -j DROP || true",
                        ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                        ip6tables + "-I FORWARD -j DROP",
                        iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-F tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                        iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-N tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptables + "-A FORWARD -j tordnscrypt_forward",
                        busybox + "sleep 1",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busybox + "sleep 1",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + wifiAPAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + usbModemAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        blockHttpRulePreroutingTCPwifi,
                        blockHttpRulePreroutingUDPwifi,
                        blockHttpRulePreroutingTCPusb,
                        blockHttpRulePreroutingUDPusb,
                        busybox + "sleep 1",
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock_tether 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock_tether 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        iptables + "-A tordnscrypt_forward -p tcp --dport 53 -j ACCEPT",
                        iptables + "-A tordnscrypt_forward -p udp --dport 53 -j ACCEPT",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        iptables + "-D FORWARD -j DROP 2> /dev/null || true"
                };

                if (ttlFix) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, fixTTLCommands());
                } else if (ttlFixed) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
                }

            } else if (itpdTethering) {
                tetheringCommands = new String[]{
                        iptables + "-I FORWARD -j DROP",
                        ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-I INPUT -j DROP || true",
                        ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                        ip6tables + "-I FORWARD -j DROP",
                        iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-F tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                        iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-N tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptables + "-A FORWARD -j tordnscrypt_forward",
                        busybox + "sleep 1",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busybox + "sleep 1",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + wifiAPAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + usbModemAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports " + pathVars.getITPDHttpProxyPort(),
                        iptables + "-A tordnscrypt_forward -p tcp --dport 53 -j ACCEPT",
                        iptables + "-A tordnscrypt_forward -p udp --dport 53 -j ACCEPT",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        iptables + "-D FORWARD -j DROP 2> /dev/null || true"
                };

                if (ttlFix) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, fixTTLCommands());
                } else if (ttlFixed) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
                }

            } else if (routeAllThroughTorTether) {
                tetheringCommands = new String[]{
                        iptables + "-I FORWARD -j DROP",
                        ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-I INPUT -j DROP || true",
                        ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                        ip6tables + "-I FORWARD -j DROP",
                        iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-F tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                        iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-N tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptables + "-A FORWARD -j tordnscrypt_forward",
                        busybox + "sleep 1",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + wifiAPAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + usbModemAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        busybox + "sleep 1",
                        blockHttpRulePreroutingTCPwifi,
                        blockHttpRulePreroutingUDPwifi,
                        blockHttpRulePreroutingTCPusb,
                        blockHttpRulePreroutingUDPusb,
                        torSitesBypassPreroutingTCP,
                        torSitesBypassPreroutingUDP,
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -m tcp --dport " + pathVars.getTorSOCKSPort() + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -p udp -m udp --dport " + pathVars.getTorSOCKSPort() + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        iptables + "-A tordnscrypt_forward -p tcp --dport 53 -j ACCEPT",
                        iptables + "-A tordnscrypt_forward -p udp --dport 53 -j ACCEPT",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        torSitesBypassForwardTCP,
                        torSitesBypassForwardUDP,
                        iptables + "-A tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptables + "-A tordnscrypt_forward -j REJECT",
                        iptables + "-D FORWARD -j DROP 2> /dev/null || true"
                };

                if (ttlFix) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, fixTTLCommands());
                } else if (ttlFixed) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
                }

            } else {
                tetheringCommands = new String[]{
                        iptables + "-I FORWARD -j DROP",
                        ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-I INPUT -j DROP || true",
                        ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                        ip6tables + "-I FORWARD -j DROP",
                        iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-F tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                        iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-N tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptables + "-A FORWARD -j tordnscrypt_forward",
                        busybox + "sleep 1",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busybox + "sleep 1",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + wifiAPAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + usbModemAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        blockHttpRulePreroutingTCPwifi,
                        blockHttpRulePreroutingUDPwifi,
                        blockHttpRulePreroutingTCPusb,
                        blockHttpRulePreroutingUDPusb,
                        busybox + "sleep 1",
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock_tether 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        busybox + "cat " + appDataDir + "/app_data/tor/unlock_tether 2> /dev/null | while read var1; do " + iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -d $var1 -j REDIRECT --to-port " + pathVars.getTorTransPort() + "; done",
                        iptables + "-A tordnscrypt_forward -p tcp --dport 53 -j ACCEPT",
                        iptables + "-A tordnscrypt_forward -p udp --dport 53 -j ACCEPT",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        iptables + "-D FORWARD -j DROP 2> /dev/null || true"
                };

                if (ttlFix) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, fixTTLCommands());
                } else if (ttlFixed) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
                }
            }
        } else {

            new PrefManager(context).setBoolPref("TetherIptablesRulesIsClean", false);

            if (torTethering) {
                tetheringCommands = new String[]{
                        iptables + "-I FORWARD -j DROP",
                        ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-I INPUT -j DROP || true",
                        ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                        ip6tables + "-I FORWARD -j DROP",
                        iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-F tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                        iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                        busybox + "sleep 1",
                        iptables + "-t nat -N tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-N tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptables + "-A FORWARD -j tordnscrypt_forward",
                        busybox + "sleep 1",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT 2> /dev/null || true",
                        iptables + "-D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT 2> /dev/null || true",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptables + "-I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busybox + "sleep 1",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + wifiAPAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -d " + usbModemAddressesRange + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -d " + pathVars.getTorVirtAdrNet() + " -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        blockHttpRulePreroutingTCPwifi,
                        blockHttpRulePreroutingUDPwifi,
                        blockHttpRulePreroutingTCPusb,
                        blockHttpRulePreroutingUDPusb,
                        torSitesBypassPreroutingTCP,
                        torSitesBypassPreroutingUDP,
                        iptables + "-t nat -A tordnscrypt_prerouting -p tcp -m tcp --dport " + pathVars.getTorSOCKSPort() + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -p udp -m udp --dport " + pathVars.getTorSOCKSPort() + " -j ACCEPT",
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        iptables + "-t nat -A tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -j REDIRECT --to-ports " + pathVars.getTorTransPort(),
                        iptables + "-A tordnscrypt_forward -p tcp --dport 53 -j ACCEPT",
                        iptables + "-A tordnscrypt_forward -p udp --dport 53 -j ACCEPT",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        torSitesBypassForwardTCP,
                        torSitesBypassForwardUDP,
                        iptables + "-A tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptables + "-A tordnscrypt_forward -j REJECT",
                        iptables + "-D FORWARD -j DROP 2> /dev/null || true"
                };

                if (ttlFix) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, fixTTLCommands());
                } else if (ttlFixed) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
                }

            } else {

                if (tetherIptablesRulesIsClean) {
                    return tetheringCommands;
                }

                new PrefManager(context).setBoolPref("TetherIptablesRulesIsClean", true);

                tetheringCommands = new String[]{
                        ip6tables + "-D INPUT -j DROP 2> /dev/null || true",
                        ip6tables + "-I INPUT -j DROP || true",
                        ip6tables + "-D FORWARD -j DROP 2> /dev/null || true",
                        ip6tables + "-I FORWARD -j DROP",
                        iptables + "-t nat -F tordnscrypt_prerouting 2> /dev/null",
                        iptables + "-F tordnscrypt_forward 2> /dev/null",
                        iptables + "-t nat -D PREROUTING -j tordnscrypt_prerouting 2> /dev/null || true",
                        iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                };

                if (ttlFixed) {
                    tetheringCommands = Arr.ADD2(tetheringCommands, unfixTTLCommands());
                }

            }

        }

        return tetheringCommands;
    }

    private void setInterfaceNames() {
        final String addressesRangeUSB = "192.168.42.";
        final String addressesRangeWiFi = "192.168.43.";

        usbTetherOn = false;

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {

                NetworkInterface intf = en.nextElement();
                if (intf.isLoopback()) {
                    continue;
                }
                if (intf.isVirtual()) {
                    continue;
                }
                if (!intf.isUp()) {
                    continue;
                }

                setVpnInterfaceName(intf);

                if (intf.isPointToPoint()) {
                    continue;
                }
                if (intf.getHardwareAddress() == null) {
                    continue;
                }

                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    String hostAddress = inetAddress.getHostAddress();

                    if (hostAddress.contains(addressesRangeWiFi)) {
                        this.apIsOn = true;
                        wifiAPInterfaceName = intf.getName();
                        Log.i(LOG_TAG, "WiFi AP interface name " + wifiAPInterfaceName);
                    }

                    if (hostAddress.contains(addressesRangeUSB)) {
                        usbTetherOn = true;
                        usbModemInterfaceName = intf.getName();
                        Log.i(LOG_TAG, "USB Modem interface name " + usbModemInterfaceName);
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(LOG_TAG, "Tethering SocketException " + e.getMessage() + " " + e.getCause());
        }

        if (usbTetherOn && !new PrefManager(context).getBoolPref("ModemIsON")) {
            new PrefManager(context).setBoolPref("ModemIsON", true);
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
        } else if (!usbTetherOn && new PrefManager(context).getBoolPref("ModemIsON")) {
            new PrefManager(context).setBoolPref("ModemIsON", false);
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
        }
    }

    private void setVpnInterfaceName(NetworkInterface intf) throws SocketException {

        if (!intf.isPointToPoint()) {
            return;
        }

        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
             enumIpAddr.hasMoreElements(); ) {
            InetAddress inetAddress = enumIpAddr.nextElement();
            String hostAddress = inetAddress.getHostAddress();

            if (hostAddress.contains(addressVPN)) {
                vpnInterfaceName = intf.getName();
                Log.i(LOG_TAG, "VPN interface name " + vpnInterfaceName);
            }
        }
    }

    private String[] fixTTLCommands() {
        new PrefManager(context).setBoolPref("TTLisFixed", true);

        String[] commands = new String[]{
                iptables + "-I FORWARD -j DROP",
                "echo 64 > /proc/sys/net/ipv4/ip_default_ttl 2> /dev/null || true",
                "ip rule delete from " + wifiAPAddressesRange + " lookup 63 2> /dev/null || true",
                "ip rule delete from " + usbModemAddressesRange + " lookup 62 2> /dev/null || true",
                iptables + "-D FORWARD -j tordnscrypt_forward 2> /dev/null || true",
                //iptables + "-t nat -D POSTROUTING -o " + vpnInterfaceName + " -j MASQUERADE || true",
                iptables + "-t nat -D tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -m tcp --dport 53 -j DNAT --to-destination " + pathVars.getDNSCryptFallbackRes() + " 2> /dev/null || true",
                iptables + "-t nat -D tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p udp -m udp --dport 53 -j DNAT --to-destination " + pathVars.getDNSCryptFallbackRes() + " 2> /dev/null || true",
                iptables + "-t nat -D tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -m tcp --dport 53 -j DNAT --to-destination " + pathVars.getDNSCryptFallbackRes() + " 2> /dev/null || true",
                iptables + "-t nat -D tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p udp -m udp --dport 53 -j DNAT --to-destination " + pathVars.getDNSCryptFallbackRes() + " 2> /dev/null || true",
                iptables + "-t nat -I tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p tcp -m tcp --dport 53 -j DNAT --to-destination " + pathVars.getDNSCryptFallbackRes(),
                iptables + "-t nat -I tordnscrypt_prerouting -i " + wifiAPInterfaceName + " -p udp -m udp --dport 53 -j DNAT --to-destination " + pathVars.getDNSCryptFallbackRes(),
                iptables + "-t nat -I tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p tcp -m tcp --dport 53 -j DNAT --to-destination " + pathVars.getDNSCryptFallbackRes(),
                iptables + "-t nat -I tordnscrypt_prerouting -i " + usbModemInterfaceName + " -p udp -m udp --dport 53 -j DNAT --to-destination " + pathVars.getDNSCryptFallbackRes(),
                iptables + "-D tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j RETURN 2> /dev/null && "
                        + iptables + "-I tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j ACCEPT 2> /dev/null || true",
                iptables + "-D tordnscrypt_forward -o !" + vpnInterfaceName + " -j REJECT 2> /dev/null || true",
                iptables + "-I tordnscrypt_forward -o !" + vpnInterfaceName + " -j REJECT",
                iptables + "-A tordnscrypt_forward -p all -j ACCEPT",
                iptables + "-I FORWARD -j tordnscrypt_forward",
                //iptables + "-t nat -I POSTROUTING -o " + vpnInterfaceName + " -j MASQUERADE",
                "ip rule add from " + wifiAPAddressesRange + " lookup 63 2> /dev/null || true",
                "ip rule add from " + usbModemAddressesRange + " lookup 62 2> /dev/null || true",
                "ip route add default dev tun0 scope link table 63 2> /dev/null || true",
                "ip route add default dev tun0 scope link table 62 2> /dev/null || true",
                "ip route add " + wifiAPAddressesRange + " dev " + wifiAPInterfaceName + " scope link table 63 2> /dev/null || true",
                "ip route add " + usbModemAddressesRange + " dev " + usbModemInterfaceName + " scope link table 62 2> /dev/null || true",
                "ip route add broadcast 255.255.255.255 dev " + wifiAPInterfaceName + " scope link table 63 2> /dev/null || true",
                "ip route add broadcast 255.255.255.255 dev " + usbModemInterfaceName + " scope link table 62 2> /dev/null || true",
                iptables + "-D FORWARD -j DROP 2> /dev/null || true"
                //iptables + "-D PREROUTING -t mangle -p udp --dport 53 -j MARK --set-mark 111 || true",
                //iptables + "-A PREROUTING -t mangle -p udp --dport 53 -j MARK --set-mark 111",
                //"ip rule add from " + wifiAPAddressesRange + " fwmark 111 lookup 62"
        };

        if (!usbTetherOn) {
            for (int i = 0; i < commands.length; i++) {
                String command = commands[i];
                if (command.contains(usbModemInterfaceName) || command.contains(usbModemAddressesRange) || command.contains("table 62")) {
                    commands[i] = "";
                }
            }
        } else if (!apIsOn) {
            for (int i = 0; i < commands.length; i++) {
                String command = commands[i];
                if (command.contains(wifiAPInterfaceName) || command.contains(wifiAPAddressesRange) || command.contains("table 63")) {
                    commands[i] = "";
                }
            }
        }

        return commands;
    }

    private String[] unfixTTLCommands() {
        new PrefManager(context).setBoolPref("TTLisFixed", false);

        return new String[]{
                "ip rule delete from " + wifiAPAddressesRange + " lookup 63 2> /dev/null || true",
                "ip rule delete from " + usbModemAddressesRange + " lookup 62 2> /dev/null || true",
                //iptables + "-D tordnscrypt_forward -o !" + vpnInterfaceName + " -j REJECT 2> /dev/null || true",
                //iptables + "-t nat -D POSTROUTING -o " + vpnInterfaceName + " -j MASQUERADE || true"
        };
    }
}
