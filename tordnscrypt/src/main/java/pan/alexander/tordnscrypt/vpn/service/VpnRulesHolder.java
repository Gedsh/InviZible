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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn.service;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_PORT_AGPS1;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_PORT_AGPS2;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_PORT_NTP;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_AGPS;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_KERNEL;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_NTP;
import static pan.alexander.tordnscrypt.utils.Constants.DNS_OVER_TLS_PORT;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.PLAINTEXT_DNS_PORT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_LAN_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_FOR_CLEARNET;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_TO_UNLOCK;
import static pan.alexander.tordnscrypt.vpn.service.VpnBuilder.vpnDnsSet;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Process;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.inject.Inject;
import javax.inject.Named;

import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.iptables.Tethering;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.Constants;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.vpn.Allowed;
import pan.alexander.tordnscrypt.vpn.Forward;
import pan.alexander.tordnscrypt.vpn.Packet;
import pan.alexander.tordnscrypt.vpn.Rule;
import pan.alexander.tordnscrypt.vpn.VpnUtils;

public class VpnRulesHolder {

    final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    private final SharedPreferences defaultPreferences;
    private final PreferenceRepository preferenceRepository;
    private final PathVars pathVars;

    @SuppressLint("UseSparseArrays")
    final Map<Integer, Boolean> mapUidAllowed = new HashMap<>();
    @SuppressLint("UseSparseArrays")
    final Map<Integer, Integer> mapUidKnown = new HashMap<>();
    @SuppressLint("UseSparseArrays")
    final Map<Integer, Forward> mapForwardPort = new HashMap<>();
    final Map<String, Forward> mapForwardAddress = new HashMap<>();
    final Set<String> ipsForTor = new HashSet<>();
    final Set<Integer> uidLanAllowed = new HashSet<>();
    final Set<Integer> uidSpecialAllowed = new HashSet<>();

    @Inject
    public VpnRulesHolder(@Named(DEFAULT_PREFERENCES_NAME) SharedPreferences defaultPreferences,
                          PreferenceRepository preferenceRepository,
                          PathVars pathVars
    ) {
        this.defaultPreferences = defaultPreferences;
        this.preferenceRepository = preferenceRepository;
        this.pathVars = pathVars;
    }

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    public Allowed isAddressAllowed(ServiceVPN vpn, Packet packet) {

        if (packet.saddr == null || packet.daddr == null || vpn.vpnPreferences == null) {
            return null;
        }

        boolean torIsRunning = modulesStatus.getTorState() == RUNNING
                || modulesStatus.getTorState() == STARTING
                || modulesStatus.getTorState() == RESTARTING;

        String apAddresses = Constants.STANDARD_AP_INTERFACE_RANGE;
        if (Tethering.wifiAPAddressesRange.contains(".")) {
            apAddresses = Tethering.wifiAPAddressesRange
                    .substring(0, Tethering.wifiAPAddressesRange.lastIndexOf("."));
        }

        String usbModemAddresses = Constants.STANDARD_USB_MODEM_INTERFACE_RANGE;
        if (Tethering.usbModemAddressesRange.contains(".")) {
            usbModemAddresses = Tethering.usbModemAddressesRange
                    .substring(0, Tethering.usbModemAddressesRange.lastIndexOf("."));
        }

        boolean fixTTLForPacket = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot()
                && (Tethering.apIsOn && packet.saddr.contains(apAddresses)
                || Tethering.usbTetherOn && packet.saddr.contains(usbModemAddresses)
                || Tethering.ethernetOn && packet.saddr.contains(Tethering.addressLocalPC));

        if (packet.uid != vpn.vpnPreferences.getOwnUID()) {
            vpn.addUIDtoDNSQueryRawRecords(packet.uid, packet.daddr, packet.dport, packet.saddr);
        }

        lock.readLock().lock();

        VpnPreferenceHolder vpnPreferences = vpn.vpnPreferences;

        boolean redirectToTor = false;
        if (torIsRunning) {
            redirectToTor = vpn.isRedirectToTor(packet.uid, packet.daddr, packet.dport);
        }

        boolean redirectToProxy = false;
        if (vpnPreferences.getUseProxy()) {
            redirectToProxy = vpn.isRedirectToProxy(packet.uid, packet.daddr, packet.dport);
        }

        packet.allowed = false;
        // https://android.googlesource.com/platform/system/core/+/master/include/private/android_filesystem_config.h
        if ((!vpn.canFilter) && isSupported(packet.protocol)) {
            packet.allowed = true;
        } else if (packet.dport == DNS_OVER_TLS_PORT
                && vpnPreferences.getIgnoreSystemDNS()) {
            logw("Block DNS over TLS " + packet);
        } else if (vpnDnsSet.contains(packet.daddr)
                && packet.dport != PLAINTEXT_DNS_PORT
                && vpnPreferences.getIgnoreSystemDNS()) {
            logw("Block DNS over HTTPS " + packet);
        } else if ((packet.uid == vpnPreferences.getOwnUID()
                || vpnPreferences.getCompatibilityMode()
                && packet.uid == SPECIAL_UID_KERNEL
                && !fixTTLForPacket)
                && isSupported(packet.protocol)) {
            packet.allowed = true;

            if (!vpnPreferences.getCompatibilityMode()) {
                logw("Allowing self " + packet);
            }
        } else if (vpnPreferences.getArpSpoofingDetection()
                && vpnPreferences.getBlockInternetWhenArpAttackDetected()
                && (ArpScanner.Companion.getArpAttackDetected()
                || ArpScanner.Companion.getDhcpGatewayAttackDetected())) {
            // MITM attack detected
            logw("Block due to mitm attack " + packet);
        } else if (vpn.reloading) {
            // Reload service
            logi("Block due to reloading " + packet);
        } else if ((vpnPreferences.getBlockIPv6() || fixTTLForPacket
                || packet.dport == PLAINTEXT_DNS_PORT
                || (torIsRunning && redirectToTor)
                || (vpnPreferences.getUseProxy() && redirectToProxy))
                && (packet.saddr.contains(":") || packet.daddr.contains(":"))) {
            logi("Block ipv6 " + packet);
        } else if (vpnPreferences.getBlockHttp() && packet.dport == 80
                && !VpnUtils.isIpInSubnet(packet.daddr, vpnPreferences.getTorVirtualAddressNetwork())
                && !packet.daddr.equals(vpnPreferences.getItpdRedirectAddress())) {
            logw("Block http " + packet);
        } else if (packet.uid <= 2000 &&
                (!vpnPreferences.getRouteAllThroughTor()
                        || vpnPreferences.getTorTethering()
                        || fixTTLForPacket
                        || vpnPreferences.getCompatibilityMode()) &&
                !mapUidKnown.containsKey(packet.uid)
                && (vpnPreferences.getFixTTL()
                || !torIsRunning && !vpnPreferences.getUseProxy()
                || packet.protocol == 6 && packet.dport == PLAINTEXT_DNS_PORT)
                && isSupported(packet.protocol)) {

            // Allow unknown system traffic
            packet.allowed = true;
            if (!fixTTLForPacket && !vpnPreferences.getCompatibilityMode()) {
                logw("Allowing unknown system " + packet);
            }
        } else if (torIsRunning
                && packet.protocol != 6
                && packet.dport != PLAINTEXT_DNS_PORT
                && redirectToTor) {
            logw("Disallowing non tcp traffic to Tor " + packet);
        } else if (vpnPreferences.getUseProxy()
                && packet.protocol != 6
                && packet.dport != PLAINTEXT_DNS_PORT
                && redirectToProxy) {
            logw("Disallowing non tcp traffic to proxy " + packet);
        } else if (vpnPreferences.getFirewallEnabled()
                && isIpInLanRange(packet.daddr)
                && isSupported(packet.protocol)) {
            packet.allowed = uidLanAllowed.contains(packet.uid);
        } else if (vpnPreferences.getFirewallEnabled()
                && isDestinationInSpecialRange(packet.uid, packet.dport)
                && isSupported(packet.protocol)) {
            packet.allowed = isSpecialAllowed(packet.uid, packet.dport);
        } else {

            if (mapUidAllowed.containsKey(packet.uid)) {
                Boolean allow = mapUidAllowed.get(packet.uid);
                if (allow != null && isSupported(packet.protocol)) {
                    packet.allowed = allow;
                    //Log.i(LOG_TAG, "Packet " + packet.toString() + " is allowed " + allow);
                }
            } else if (packet.dport == PLAINTEXT_DNS_PORT
                    && packet.uid < 2000 && packet.uid != SPECIAL_UID_KERNEL
                    && isSupported(packet.protocol)) {
                //Allow connection check for system apps
                packet.allowed = true;
            } else {
                logw("UID is not allowed or no rules for " + packet);
            }
        }

        Allowed allowed = null;
        if (packet.allowed) {
            if (packet.uid == vpnPreferences.getOwnUID()
                    && (packet.dport != PLAINTEXT_DNS_PORT || vpnPreferences.getCompatibilityMode())
                    || vpnPreferences.getCompatibilityMode()
                    && isPacketAllowedForCompatibilityMode(packet, fixTTLForPacket)) {
                allowed = new Allowed();
            } else if (mapForwardPort.containsKey(packet.dport)) {
                Forward fwd = mapForwardPort.get(packet.dport);
                if (fwd != null) {
                    allowed = new Allowed(fwd.raddr, fwd.rport);
                    packet.data = "> " + fwd.raddr + "/" + fwd.rport;
                }
            } else if (mapForwardAddress.containsKey(packet.daddr)) {
                Forward fwd = mapForwardAddress.get(packet.daddr);
                if (fwd != null) {
                    allowed = new Allowed(fwd.raddr, fwd.rport);
                    packet.data = "> " + fwd.raddr + "/" + fwd.rport;
                }
            } else {
                allowed = new Allowed();
            }
        }

        lock.readLock().unlock();

        return allowed;
    }

    private boolean isSupported(int protocol) {
        return (protocol == 1 /* ICMPv4 */ ||
                protocol == 58 /* ICMPv6 */ ||
                protocol == 6 /* TCP */ ||
                protocol == 17 /* UDP */);
    }

    private boolean isIpInLanRange(String destAddress) {
        for (String address : VpnUtils.nonTorList) {
            if (VpnUtils.isIpInSubnet(destAddress, address)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDestinationInSpecialRange(int uid, int destPort) {
        return uid == 0 && destPort == PLAINTEXT_DNS_PORT
                || uid == SPECIAL_UID_KERNEL
                || destPort == SPECIAL_PORT_NTP
                || destPort == SPECIAL_PORT_AGPS1
                || destPort == SPECIAL_PORT_AGPS2;
    }

    private boolean isSpecialAllowed(int uid, int destPort) {
        if (uid == 0 && destPort == PLAINTEXT_DNS_PORT) {
            return true;
        } else if (uid == SPECIAL_UID_KERNEL) {
            return uidSpecialAllowed.contains(SPECIAL_UID_KERNEL);
        } else if (uid == 1000 && destPort == SPECIAL_PORT_NTP) {
            return uidSpecialAllowed.contains(SPECIAL_UID_NTP)
                    || mapUidAllowed.containsKey(1000);
        } else if (destPort == SPECIAL_PORT_AGPS1 || destPort == SPECIAL_PORT_AGPS2) {
            return uidSpecialAllowed.contains(SPECIAL_UID_AGPS);
        }
        return false;
    }

    private boolean isPacketAllowedForCompatibilityMode(Packet packet, boolean fixTTLForPacket) {
        ModuleState dnsCryptState = modulesStatus.getDnsCryptState();
        ModuleState torState = modulesStatus.getTorState();
        boolean dnsCryptReady = modulesStatus.isDnsCryptReady();
        boolean torReady = modulesStatus.isTorReady();
        boolean systemDNSAllowed = modulesStatus.isSystemDNSAllowed();

        if (packet.uid == SPECIAL_UID_KERNEL && !fixTTLForPacket
                && (packet.dport != PLAINTEXT_DNS_PORT && packet.dport != 0
                || systemDNSAllowed
                && ((dnsCryptState == RUNNING
                || dnsCryptState == STARTING
                || dnsCryptState == RESTARTING) && !dnsCryptReady
                || (torState == RUNNING
                || torState == STARTING
                || torState == RESTARTING) && !torReady))) {
            logi("Packet will not be redirected due to compatibility mode " + packet);
            return true;
        }

        return false;
    }

    void prepareUidAllowed(
            List<String> listAllowed,
            List<Rule> listRule
    ) {
        lock.writeLock().lock();

        mapUidAllowed.clear();
        uidSpecialAllowed.clear();
        for (String uid : listAllowed) {
            if (uid != null && uid.matches("\\d+")) {
                mapUidAllowed.put(Integer.valueOf(uid), true);
            } else if (uid != null && uid.matches("-\\d+")) {
                uidSpecialAllowed.add(Integer.valueOf(uid));
            }
        }

        mapUidKnown.clear();
        for (Rule rule : listRule) {
            if (rule.uid >= 0) {
                mapUidKnown.put(rule.uid, rule.uid);
            }
        }

        uidLanAllowed.clear();
        for (String uid : preferenceRepository.getStringSetPreference(APPS_ALLOW_LAN_PREF)) {
            if (uid != null && uid.matches("\\d+")) {
                uidLanAllowed.add(Integer.valueOf(uid));
            }
        }

        ipsForTor.clear();
        boolean routeAllThroughTor = defaultPreferences.getBoolean(ALL_THROUGH_TOR, true);
        if (routeAllThroughTor) {
            ipsForTor.addAll(preferenceRepository.getStringSetPreference(IPS_FOR_CLEARNET));
        } else {
            ipsForTor.addAll(preferenceRepository.getStringSetPreference(IPS_TO_UNLOCK));
        }

        lock.writeLock().unlock();
    }

    void prepareForwarding() {
        lock.writeLock().lock();
        mapForwardPort.clear();
        mapForwardAddress.clear();

        ModuleState dnsCryptState = modulesStatus.getDnsCryptState();
        ModuleState torState = modulesStatus.getTorState();
        ModuleState itpdState = modulesStatus.getItpdState();

        int ownUID = Process.myUid();

        int dnsCryptPort = 5354;
        int torDNSPort = 5400;
        int itpdHttpPort = 4444;
        try {
            dnsCryptPort = Integer.parseInt(pathVars.getDNSCryptPort());
            torDNSPort = Integer.parseInt(pathVars.getTorDNSPort());
            itpdHttpPort = Integer.parseInt(pathVars.getITPDHttpProxyPort());
        } catch (Exception e) {
            loge("VPN Redirect Ports Parse Exception", e);
        }

        boolean dnsCryptReady = modulesStatus.isDnsCryptReady();
        boolean torReady = modulesStatus.isTorReady();
        boolean systemDNSAllowed = modulesStatus.isSystemDNSAllowed();

        //If Tor is ready and DNSCrypt is not, app will use Tor Exit node DNS in VPN mode
        if (dnsCryptState == RUNNING && (dnsCryptReady || !systemDNSAllowed)) {
            addForwardPortRule(17, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, dnsCryptPort, ownUID);
            addForwardPortRule(6, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, dnsCryptPort, ownUID);

            if (itpdState == RUNNING) {
                addForwardAddressRule(17, "10.191.0.1", LOOPBACK_ADDRESS, itpdHttpPort, ownUID);
                addForwardAddressRule(6, "10.191.0.1", LOOPBACK_ADDRESS, itpdHttpPort, ownUID);
            }
        } else if (torState == RUNNING && (torReady || !systemDNSAllowed)) {
            addForwardPortRule(17, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, torDNSPort, ownUID);
            addForwardPortRule(6, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, torDNSPort, ownUID);
        } else {
            addForwardPortRule(17, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, dnsCryptPort, ownUID);
            addForwardPortRule(6, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, dnsCryptPort, ownUID);
        }

        lock.writeLock().unlock();
    }

    @SuppressWarnings("SameParameterValue")
    private void addForwardPortRule(int protocol, int dport, String raddr, int rport, int ruid) {
        Forward fwd = new Forward();
        fwd.protocol = protocol;
        fwd.dport = dport;
        fwd.raddr = raddr;
        fwd.rport = rport;
        fwd.ruid = ruid;
        mapForwardPort.put(fwd.dport, fwd);
        logi("VPN Forward " + fwd);
    }

    @SuppressWarnings("SameParameterValue")
    private void addForwardAddressRule(int protocol, String daddr, String raddr, int rport, int ruid) {
        Forward fwd = new Forward();
        fwd.protocol = protocol;
        fwd.daddr = daddr;
        fwd.raddr = raddr;
        fwd.rport = rport;
        fwd.ruid = ruid;
        mapForwardAddress.put(fwd.daddr, fwd);
        logi("VPN Forward " + fwd);
    }

    void unPrepare() {
        lock.writeLock().lock();
        mapUidAllowed.clear();
        mapUidKnown.clear();
        ipsForTor.clear();
        uidLanAllowed.clear();
        uidSpecialAllowed.clear();
        mapForwardPort.clear();
        mapForwardAddress.clear();
        lock.writeLock().unlock();
    }
}
