package pan.alexander.tordnscrypt.utils;
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
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import pan.alexander.tordnscrypt.settings.PathVars;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class Tethering {
    private Context context;
    private String iptablesPath;
    private String itpdHttpProxyPort;
    private String dnsCryptPort;
    private String appDataDir;
    private String torTransPort;
    private String busyboxPath;
    private String torVirtAdrNet;
    private String torDNSPort;
    private String blockHttpRuleForwardTCP;
    private String blockHttpRuleForwardUDP;
    private boolean blockHotspotHttp;

    public Tethering(Context context) {
        this.context = context;
        PathVars pathVars = new PathVars(context);
        iptablesPath = pathVars.iptablesPath;
        itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        dnsCryptPort = pathVars.dnsCryptPort;
        appDataDir = pathVars.appDataDir;
        torTransPort = pathVars.torTransPort;
        busyboxPath = pathVars.busyboxPath;
        torVirtAdrNet = pathVars.torVirtAdrNet;
        torDNSPort = pathVars.torDNSPort;
    }

    public String[] activateTethering(boolean privacyMode) {

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean torTethering = shPref.getBoolean("pref_common_tor_tethering", false);
        boolean itpdTethering = shPref.getBoolean("pref_common_itpd_tethering", false);
        boolean routeAllThroughTorTether = shPref.getBoolean("pref_common_tor_route_all",false);
        blockHotspotHttp = shPref.getBoolean("pref_common_block_http",false);
        boolean apIsOn = new PrefManager(context).getBoolPref("APisON");

        String hotspotInterfaceName = getHotspotInterfaceName();
        Log.i(LOG_TAG, "HOTSPOT interface name " + hotspotInterfaceName);

        String torSitesBypassPreroutingTCP = "";
        String torSitesBypassForwardTCP = "";
        String torSitesBypassPreroutingUDP = "";
        String torSitesBypassForwardUDP = "";
        if (routeAllThroughTorTether) {
            torSitesBypassPreroutingTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet_tether | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_prerouting -p tcp -d $var1 -j ACCEPT; done";
            torSitesBypassForwardTCP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet_tether | while read var1; do "+iptablesPath+"iptables -A tordnscrypt_forward -p tcp -d $var1 -j ACCEPT; done";
            torSitesBypassPreroutingUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet_tether | while read var1; do "+iptablesPath+"iptables -t nat -A tordnscrypt_prerouting -p udp -d $var1 -j ACCEPT; done";
            torSitesBypassForwardUDP = busyboxPath+ "cat "+appDataDir+"/app_data/tor/clearnet_tether | while read var1; do "+iptablesPath+"iptables -A tordnscrypt_forward -p udp -d $var1 -j ACCEPT; done";

        }

        blockHttpRuleForwardTCP = "";
        blockHttpRuleForwardUDP = "";
        String blockHttpRulePreroutingTCP = "";
        String blockHttpRulePreroutingUDP = "";
        if (blockHotspotHttp) {
            blockHttpRuleForwardTCP = iptablesPath + "iptables -A tordnscrypt_forward -p tcp --dport 80 -j REJECT";
            blockHttpRuleForwardUDP = iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 80 -j REJECT";
            blockHttpRulePreroutingTCP = iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp ! -d 192.168.43.0/24 --dport 80 -j RETURN";
            blockHttpRulePreroutingUDP = iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p udp ! -d 192.168.43.0/24 --dport 80 -j RETURN";
        }

        if (!torTethering && !itpdTethering) {
            if (apIsOn) {
                overrideAFWallDNSrules();
            }
            return null;
        }

        final String[] tetheringCommands;
        if (!privacyMode) {
            if (torTethering && routeAllThroughTorTether && itpdTethering) {
                tetheringCommands = new String[]{
                        "ip6tables -D INPUT -j DROP || true",
                        "ip6tables -I INPUT -j DROP",
                        "ip6tables -D FORWARD -j DROP || true",
                        "ip6tables -I FORWARD -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_prerouting",
                        iptablesPath + "iptables -N tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -A FORWARD -j tordnscrypt_forward",
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -p tcp -d "+torVirtAdrNet+" -j REDIRECT --to-ports "+torTransPort,
                        blockHttpRulePreroutingTCP,
                        blockHttpRulePreroutingUDP,
                        torSitesBypassPreroutingTCP,
                        torSitesBypassPreroutingUDP,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp ! -d 192.168.43.0/24 -j REDIRECT --to-ports "+ torTransPort,
                        iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 53 -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -p tcp --dport 53 -j RETURN",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        torSitesBypassForwardTCP,
                        torSitesBypassForwardUDP,
                        iptablesPath + "iptables -A tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -j REJECT"
                };
            } else if (torTethering && itpdTethering) {
                tetheringCommands = new String[]{
                        "ip6tables -D INPUT -j DROP || true",
                        "ip6tables -I INPUT -j DROP",
                        "ip6tables -D FORWARD -j DROP || true",
                        "ip6tables -I FORWARD -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_prerouting",
                        iptablesPath + "iptables -N tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -A FORWARD -j tordnscrypt_forward",
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -p tcp -d "+torVirtAdrNet+" -j REDIRECT --to-ports "+torTransPort,
                        blockHttpRulePreroutingTCP,
                        blockHttpRulePreroutingUDP,
                        busyboxPath+ "sleep 1",
                        busyboxPath + "cat "+ appDataDir +"/app_data/tor/unlock_tether | while read var1; do "+ iptablesPath +"iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp -d $var1 -j REDIRECT --to-port "+ torTransPort +"; done",
                        iptablesPath + "iptables -A tordnscrypt_forward -p tcp --dport 53 -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 53 -j RETURN",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP
                };
            } else if (itpdTethering){
                tetheringCommands = new String[]{
                        "ip6tables -D INPUT -j DROP || true",
                        "ip6tables -I INPUT -j DROP",
                        "ip6tables -D FORWARD -j DROP || true",
                        "ip6tables -I FORWARD -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_prerouting",
                        iptablesPath + "iptables -N tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -A FORWARD -j tordnscrypt_forward",
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -A tordnscrypt_forward -p tcp --dport 53 -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 53 -j RETURN",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP
                };
            } else if (routeAllThroughTorTether) {
                tetheringCommands = new String[]{
                        "ip6tables -D INPUT -j DROP || true",
                        "ip6tables -I INPUT -j DROP",
                        "ip6tables -D FORWARD -j DROP || true",
                        "ip6tables -I FORWARD -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_prerouting",
                        iptablesPath + "iptables -N tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -A FORWARD -j tordnscrypt_forward",
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -p tcp -d "+torVirtAdrNet+" -j REDIRECT --to-ports "+torTransPort,
                        busyboxPath+ "sleep 1",
                        blockHttpRulePreroutingTCP,
                        blockHttpRulePreroutingUDP,
                        torSitesBypassPreroutingTCP,
                        torSitesBypassPreroutingUDP,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp ! -d 192.168.43.0/24 -j REDIRECT --to-ports "+ torTransPort,
                        iptablesPath + "iptables -A tordnscrypt_forward -p tcp --dport 53 -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 53 -j RETURN",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        torSitesBypassForwardTCP,
                        torSitesBypassForwardUDP,
                        iptablesPath + "iptables -A tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -j REJECT"
                };
            } else {
                tetheringCommands = new String[]{
                        "ip6tables -D INPUT -j DROP || true",
                        "ip6tables -I INPUT -j DROP || true",
                        "ip6tables -D FORWARD -j DROP",
                        "ip6tables -I FORWARD -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_prerouting",
                        iptablesPath + "iptables -N tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -A FORWARD -j tordnscrypt_forward",
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -p tcp -d "+torVirtAdrNet+" -j REDIRECT --to-ports "+torTransPort,
                        blockHttpRulePreroutingTCP,
                        blockHttpRulePreroutingUDP,
                        busyboxPath+ "sleep 1",
                        busyboxPath + "cat "+ appDataDir +"/app_data/tor/unlock_tether | while read var1; do "+ iptablesPath +"iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp -d $var1 -j REDIRECT --to-port "+ torTransPort +"; done",
                        iptablesPath + "iptables -A tordnscrypt_forward -p tcp --dport 53 -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 53 -j RETURN",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP
                };
            }
        } else {
            if (torTethering && itpdTethering) {
                tetheringCommands = new String[]{
                        "ip6tables -D INPUT -j DROP || true",
                        "ip6tables -I INPUT -j DROP",
                        "ip6tables -D FORWARD -j DROP || true",
                        "ip6tables -I FORWARD -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_prerouting",
                        iptablesPath + "iptables -N tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -A FORWARD -j tordnscrypt_forward",
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j ACCEPT",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p udp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -p tcp -d "+torVirtAdrNet+" -j REDIRECT --to-ports "+torTransPort,
                        blockHttpRulePreroutingTCP,
                        blockHttpRulePreroutingUDP,
                        torSitesBypassPreroutingTCP,
                        torSitesBypassPreroutingUDP,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp ! -d 192.168.43.0/24 -j REDIRECT --to-ports "+ torTransPort,
                        iptablesPath + "iptables -A tordnscrypt_forward -p tcp --dport 53 -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 53 -j RETURN",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        torSitesBypassForwardTCP,
                        torSitesBypassForwardUDP,
                        iptablesPath + "iptables -A tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -j REJECT"
                };
            } else if (torTethering) {
                tetheringCommands = new String[]{
                        "ip6tables -D INPUT -j DROP || true",
                        "ip6tables -I INPUT -j DROP",
                        "ip6tables -D FORWARD -j DROP || true",
                        "ip6tables -I FORWARD -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -N tordnscrypt_prerouting",
                        iptablesPath + "iptables -N tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -A PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -A FORWARD -j tordnscrypt_forward",
                        busyboxPath+ "sleep 1",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --dport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --dport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 67 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -p udp -m udp --sport 68 -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 67 -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -p udp -m udp --sport 68 -j ACCEPT",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j RETURN || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j ACCEPT || true",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+torDNSPort+" -j ACCEPT",
                        iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+torDNSPort+" -j ACCEPT",
                        busyboxPath+ "sleep 1",
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -p tcp -d "+torVirtAdrNet+" -j REDIRECT --to-ports "+torTransPort,
                        blockHttpRulePreroutingTCP,
                        blockHttpRulePreroutingUDP,
                        torSitesBypassPreroutingTCP,
                        torSitesBypassPreroutingUDP,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i " + hotspotInterfaceName + " -p tcp ! -d 192.168.43.0/24 -j REDIRECT --to-ports "+ torTransPort,
                        iptablesPath + "iptables -A tordnscrypt_forward -p tcp --dport 53 -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 53 -j RETURN",
                        blockHttpRuleForwardTCP,
                        blockHttpRuleForwardUDP,
                        torSitesBypassForwardTCP,
                        torSitesBypassForwardUDP,
                        iptablesPath + "iptables -A tordnscrypt_forward -m state --state ESTABLISHED,RELATED -j RETURN",
                        iptablesPath + "iptables -A tordnscrypt_forward -j REJECT"
                };
            } else {
                tetheringCommands = new String[]{
                        "ip6tables -D INPUT -j DROP || true",
                        "ip6tables -I INPUT -j DROP",
                        "ip6tables -D FORWARD -j DROP || true",
                        "ip6tables -I FORWARD -j DROP",
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                        busyboxPath + "sleep 1",
                };
            }

        }
        return tetheringCommands;
    }

    private void overrideAFWallDNSrules() {
        String[] tetheringCommands;

        if (blockHotspotHttp) {
            tetheringCommands = new String[]{
                    "ip6tables -D INPUT -j DROP || true",
                    "ip6tables -I INPUT -j DROP",
                    "ip6tables -D FORWARD -j DROP || true",
                    "ip6tables -I FORWARD -j DROP",
                    iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                    iptablesPath + "iptables -F tordnscrypt_forward",
                    iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                    iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                    busyboxPath+ "sleep 1",
                    iptablesPath + "iptables -t nat -N tordnscrypt_prerouting",
                    iptablesPath + "iptables -N tordnscrypt_forward",
                    iptablesPath + "iptables -t nat -A PREROUTING -j tordnscrypt_prerouting",
                    iptablesPath + "iptables -A FORWARD -j tordnscrypt_forward",
                    busyboxPath+ "sleep 1",
                    blockHttpRuleForwardTCP,
                    blockHttpRuleForwardUDP,
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT || true",
                    iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT",
                    iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT"
            };
        } else {
            tetheringCommands = new String[]{
                    iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                    iptablesPath + "iptables -F tordnscrypt_forward",
                    iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting || true",
                    iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward || true",
                    busyboxPath + "sleep 1",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -m owner --uid-owner 0 -j ACCEPT || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j RETURN || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j RETURN || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT || true",
                    iptablesPath+ "iptables -D tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT || true",
                    iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p udp -m udp --dport "+dnsCryptPort+" -j ACCEPT",
                    iptablesPath+ "iptables -I tordnscrypt -d 127.0.0.1/32 -p tcp -m tcp --dport "+dnsCryptPort+" -j ACCEPT"
            };
        }


        if (!tetheringCommands[0].isEmpty()) {
            final String[] tetheringCommandsFinal = tetheringCommands;
            Handler handler = new Handler();
            Runnable runTethering = new Runnable() {
                @Override
                public void run() {
                    RootCommands rootCommands = new RootCommands(tetheringCommandsFinal);
                    Intent intent = new Intent(context, RootExecService.class);
                    intent.setAction(RootExecService.RUN_COMMAND);
                    intent.putExtra("Commands",rootCommands);
                    intent.putExtra("Mark", RootExecService.NullMark);
                    RootExecService.performAction(context,intent);
                }
            };
            handler.postDelayed(runTethering,1000);
        }
    }

    private String getHotspotInterfaceName() {
        String name = "wlan0";
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
                if (intf.isPointToPoint()) {
                    continue;
                }
                if (intf.getHardwareAddress() == null) {
                    continue;
                }

                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (inetAddress.getHostAddress().contains("192.168.43.")) {
                        name = intf.getName();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(LOG_TAG, e.getMessage() + " " + e.getCause());
        }

        return name;
    }
}
