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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn.service;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_PORT_AGPS1;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_PORT_AGPS2;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_PORT_NTP;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_AGPS;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_CONNECTIVITY_CHECK;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_KERNEL;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_NTP;
import static pan.alexander.tordnscrypt.utils.Constants.DNS_OVER_TLS_PORT;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS_IPv6;
import static pan.alexander.tordnscrypt.utils.Constants.NETWORK_STACK_DEFAULT_UID;
import static pan.alexander.tordnscrypt.utils.Constants.PLAINTEXT_DNS_PORT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_LAN_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_FOR_CLEARNET;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_TO_UNLOCK;
import static pan.alexander.tordnscrypt.vpn.VpnUtils.isIpInLanRange;
import static pan.alexander.tordnscrypt.vpn.service.VpnBuilder.vpnDnsSet;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.iptables.Tethering;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.Constants;
import pan.alexander.tordnscrypt.utils.connectivitycheck.ConnectivityCheckManager;
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
    private final Lazy<ConnectivityCheckManager> connectivityCheckManager;
    private final Lazy<ConnectionCheckerInteractor> connectionCheckerInteractor;

    @SuppressLint("UseSparseArrays")
    final Set<Integer> setUidAllowed = new ConcurrentSkipListSet<>();
    @SuppressLint("UseSparseArrays")
    private final Set<Integer> setUidKnown = new ConcurrentSkipListSet<>();
    @SuppressLint("UseSparseArrays")
    final Map<Integer, Forward> mapForwardPort = new ConcurrentSkipListMap<>();
    private final Map<String, Forward> mapForwardAddress = new ConcurrentSkipListMap<>();
    final Set<String> ipsForTor = new ConcurrentSkipListSet<>();
    private final Set<Integer> uidLanAllowed = new ConcurrentSkipListSet<>();
    final Set<Integer> uidSpecialAllowed = new ConcurrentSkipListSet<>();
    private final Set<Integer> uidSpecialLanAllowed = new ConcurrentSkipListSet<>();

    private final Set<String> connectivityCheckIps = new ConcurrentSkipListSet<>();

    @Inject
    public VpnRulesHolder(@Named(DEFAULT_PREFERENCES_NAME) SharedPreferences defaultPreferences,
                          PreferenceRepository preferenceRepository,
                          PathVars pathVars,
                          Lazy<ConnectivityCheckManager> connectivityCheckManager,
                          Lazy<ConnectionCheckerInteractor> connectionCheckerInteractor
    ) {
        this.defaultPreferences = defaultPreferences;
        this.preferenceRepository = preferenceRepository;
        this.pathVars = pathVars;
        this.connectivityCheckManager = connectivityCheckManager;
        this.connectionCheckerInteractor = connectionCheckerInteractor;
    }

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    public Allowed isAddressAllowed(ServiceVPN vpn, Packet packet) {

        if (packet.saddr == null
                || packet.daddr == null
                || vpn.vpnPreferences == null) {
            return null;
        }

        boolean torIsRunning = modulesStatus.getTorState() == RUNNING
                || modulesStatus.getTorState() == STARTING
                || modulesStatus.getTorState() == RESTARTING;

        boolean fixTTLForPacket = isFixTTLForPacket(packet);

        boolean dnsCryptIsRunning = modulesStatus.getDnsCryptState() == RUNNING
                || modulesStatus.getDnsCryptState() == STARTING
                || modulesStatus.getDnsCryptState() == RESTARTING;

        lock.readLock().lock();

        VpnPreferenceHolder vpnPreferences = vpn.vpnPreferences;

        boolean redirectToTor = false;
        if (torIsRunning) {
            redirectToTor = vpn.isRedirectToTor(packet.uid, packet.daddr, packet.dport);
        }
        if (redirectToTor && packet.protocol != 6) {
            redirectToTor = !vpnPreferences.getSetDirectUdpApps().contains(String.valueOf(packet.uid));
        }

        boolean redirectToProxy = false;
        if (vpnPreferences.getUseProxy()) {
            redirectToProxy = vpn.isRedirectToProxy(packet.uid, packet.daddr, packet.dport);
        }

        boolean networkAvailable = connectionCheckerInteractor.get().getNetworkConnectionResult();

        packet.allowed = false;
        // https://android.googlesource.com/platform/system/core/+/master/include/private/android_filesystem_config.h
        if ((!vpn.canFilter) && isSupported(packet.protocol)) {
            packet.allowed = true;
        } else if (!isSupported(packet.protocol)) {
            logw("Protocol not supported " + packet);
        } else if (packet.dport == DNS_OVER_TLS_PORT
                && vpnPreferences.getIgnoreSystemDNS()
                && (dnsCryptIsRunning || torIsRunning)) {
            logw("Block DNS over TLS " + packet);
        } else if (vpnDnsSet.contains(packet.daddr)
                && packet.dport != PLAINTEXT_DNS_PORT
                && vpnPreferences.getIgnoreSystemDNS()
                && packet.uid != vpnPreferences.getOwnUID()
                && (dnsCryptIsRunning || torIsRunning)) {
            logw("Block DNS over HTTPS " + packet);
        } else if (packet.uid == vpnPreferences.getOwnUID()
                || vpnPreferences.getCompatibilityMode()
                && packet.uid == SPECIAL_UID_KERNEL
                && !fixTTLForPacket) {
            packet.allowed = true;

            if (!vpnPreferences.getCompatibilityMode()) {
                logw("Allowing self " + packet);
            }
        } else if (vpnPreferences.getArpSpoofingDetection()
                && vpnPreferences.getBlockInternetWhenArpAttackDetected()
                && (ArpScanner.getArpAttackDetected()
                || ArpScanner.getDhcpGatewayAttackDetected())) {
            // MITM attack detected
            logw("Block due to mitm attack " + packet);
        } else if (packet.uid == NETWORK_STACK_DEFAULT_UID
                && isIpInLanRange(packet.daddr)) {
            //Allow NetworkStack to connect to LAN to determine connection status
            packet.allowed = true;
        } else if (vpn.reloading) {
            // Reload service
            logi("Block due to reloading " + packet);
        } else if ((modulesStatus.getDnsCryptState() != STOPPED &&
                vpnPreferences.getBlockIPv6DnsCrypt()
                || modulesStatus.getDnsCryptState() == STOPPED &&
                modulesStatus.getTorState() != STOPPED &&
                !vpnPreferences.getUseIPv6Tor()
                || fixTTLForPacket
                //|| packet.dport == PLAINTEXT_DNS_PORT
                //|| (torIsRunning && redirectToTor)
                || (vpnPreferences.getUseProxy() && redirectToProxy))
                && (packet.saddr.contains(":") || packet.daddr.contains(":"))) {
            logi("Block ipv6 " + packet);
        } else if (vpnPreferences.getBlockHttp() && packet.dport == 80
                && !VpnUtils.isIpInSubnet(packet.daddr, vpnPreferences.getTorVirtualAddressNetwork())
                && !packet.daddr.equals(vpnPreferences.getItpdRedirectAddress())
                && !isIpInLanRange(packet.daddr)) {
            logw("Block http " + packet);
        } else if (packet.uid <= 2000 &&
                (!vpnPreferences.getRouteAllThroughTor()
                        || vpnPreferences.getTorTethering()
                        || fixTTLForPacket
                        || vpnPreferences.getCompatibilityMode()) &&
                !setUidKnown.contains(packet.uid)
                && (vpnPreferences.getFixTTL()
                || !torIsRunning && !vpnPreferences.getUseProxy()
                || packet.protocol == 6 && packet.dport == PLAINTEXT_DNS_PORT)) {

            // Allow unknown system traffic
            packet.allowed = true;
            if (!fixTTLForPacket && !vpnPreferences.getCompatibilityMode()) {
                logw("Allowing unknown system " + packet);
            }
        } else if (torIsRunning
                && (packet.protocol != 6 || !networkAvailable)
                && packet.dport != PLAINTEXT_DNS_PORT
                && (redirectToTor || isToTorTraffic(packet))) {
            logw("Disallowing" + (networkAvailable ? " non tcp " : " ") + "traffic to Tor " + packet);
        } else if (vpnPreferences.getUseProxy()
                && packet.protocol != 6
                && packet.dport != PLAINTEXT_DNS_PORT
                && redirectToProxy) {
            logw("Disallowing non tcp traffic to proxy " + packet);
        } else if (vpnPreferences.getFirewallEnabled()
                && isIpInLanRange(packet.daddr)) {
            if (isDestinationInSpecialRange(packet.uid, packet.daddr, packet.dport)) {
                packet.allowed = isSpecialAllowed(
                        uidLanAllowed,
                        uidSpecialLanAllowed,
                        packet.uid,
                        packet.daddr,
                        packet.dport
                );
            } else {
                packet.allowed = uidLanAllowed.contains(packet.uid);
            }
        } else if (vpnPreferences.getFirewallEnabled()
                && isDestinationInSpecialRange(packet.uid, packet.daddr, packet.dport)) {
            packet.allowed = isSpecialAllowed(
                    setUidAllowed,
                    uidSpecialAllowed,
                    packet.uid,
                    packet.daddr,
                    packet.dport
            );
        } else if (vpnPreferences.getFirewallEnabled()) {

            if (setUidAllowed.contains(packet.uid)) {
                packet.allowed = true;
                //logi("Packet " + packet.toString() + " is allowed " + allow);
            } else if (packet.dport == PLAINTEXT_DNS_PORT
                    && packet.uid < 2000 && packet.uid != SPECIAL_UID_KERNEL) {
                //Allow connection check for system apps
                packet.allowed = true;
            } else {
                logw("UID is not allowed or no rules for " + packet);
            }
        } else {
            packet.allowed = true;
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
                if (fwd != null && networkAvailable) {
                    allowed = new Allowed(fwd.raddr, fwd.rport);
                    packet.data = "> " + fwd.raddr + "/" + fwd.rport;
                }
            } else if (mapForwardAddress.containsKey(packet.daddr)) {
                Forward fwd = mapForwardAddress.get(packet.daddr);
                if (fwd != null && networkAvailable) {
                    allowed = new Allowed(fwd.raddr, fwd.rport);
                    packet.data = "> " + fwd.raddr + "/" + fwd.rport;
                }
            } else {
                allowed = new Allowed();
            }
        }

        lock.readLock().unlock();

        if (packet.uid != vpn.vpnPreferences.getOwnUID()) {
            vpn.addUIDtoDNSQueryRawRecords(
                    packet.uid,
                    packet.daddr,
                    packet.dport,
                    packet.saddr,
                    packet.allowed,
                    packet.protocol
            );
        }

        return allowed;
    }

    private boolean isFixTTLForPacket(Packet packet) {
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

        return modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot()
                && (Tethering.apIsOn && packet.saddr.contains(apAddresses)
                || Tethering.usbTetherOn && packet.saddr.contains(usbModemAddresses)
                || Tethering.ethernetOn && packet.saddr.contains(Tethering.addressLocalPC));
    }

    private boolean isSupported(int protocol) {
        return (protocol == 1 /* ICMPv4 */ ||
                protocol == 58 /* ICMPv6 */ ||
                protocol == 6 /* TCP */ ||
                protocol == 17 /* UDP */);
    }

    private boolean isDestinationInSpecialRange(int uid, String destIp, int destPort) {
        return uid == 0 && destPort == PLAINTEXT_DNS_PORT
                || uid == SPECIAL_UID_KERNEL
                || destPort == SPECIAL_PORT_NTP
                || destPort == SPECIAL_PORT_AGPS1
                || destPort == SPECIAL_PORT_AGPS2
                || connectivityCheckIps.contains(destIp);
    }

    private boolean isSpecialAllowed(
            Set<Integer> uidAllowed,
            Set<Integer> specialUidAllowed,
            int uid,
            String destIp,
            int destPort
    ) {
        boolean allow = false;
        if (uid == 0 && destPort == PLAINTEXT_DNS_PORT) {
            allow = true;
        } else if (uid == SPECIAL_UID_KERNEL) {
            allow = specialUidAllowed.contains(SPECIAL_UID_KERNEL);
        } else if (uid == 1000 && destPort == SPECIAL_PORT_NTP) {
            allow = specialUidAllowed.contains(SPECIAL_UID_NTP);
        } else if (destPort == SPECIAL_PORT_AGPS1 || destPort == SPECIAL_PORT_AGPS2) {
            allow = specialUidAllowed.contains(SPECIAL_UID_AGPS);
        } else if (connectivityCheckIps.contains(destIp)) {
            allow = specialUidAllowed.contains(SPECIAL_UID_CONNECTIVITY_CHECK);
        }
        return allow || uidAllowed.contains(uid);
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

    boolean isToTorTraffic(Packet packet) {
        String dport = String.valueOf(packet.dport);
        return (packet.daddr.equals(LOOPBACK_ADDRESS) || packet.daddr.equals(LOOPBACK_ADDRESS_IPv6))
                && (pathVars.getTorSOCKSPort().equals(dport)
                || pathVars.getTorHTTPTunnelPort().equals(dport)
                || pathVars.getTorTransPort().equals(dport));
    }


    void prepareUidAllowed(
            List<String> listAllowed,
            List<Rule> listRule
    ) {
        lock.writeLock().lock();

        setUidAllowed.clear();
        uidSpecialAllowed.clear();
        for (String uid : listAllowed) {
            if (uid != null && uid.matches("\\d+")) {
                setUidAllowed.add(Integer.valueOf(uid));
            } else if (uid != null && uid.matches("-\\d+")) {
                uidSpecialAllowed.add(Integer.valueOf(uid));
            }
        }

        setUidKnown.clear();
        for (Rule rule : listRule) {
            if (rule.uid >= 0) {
                setUidKnown.add(rule.uid);
            }
        }

        uidLanAllowed.clear();
        uidSpecialLanAllowed.clear();
        for (String uid : preferenceRepository.getStringSetPreference(APPS_ALLOW_LAN_PREF)) {
            if (uid != null && uid.matches("\\d+")) {
                uidLanAllowed.add(Integer.valueOf(uid));
            } else if (uid != null && uid.matches("-\\d+")) {
                uidSpecialLanAllowed.add(Integer.valueOf(uid));
            }
        }

        ipsForTor.clear();
        boolean routeAllThroughTor = defaultPreferences.getBoolean(ALL_THROUGH_TOR, true);
        if (routeAllThroughTor) {
            ipsForTor.addAll(preferenceRepository.getStringSetPreference(IPS_FOR_CLEARNET));
        } else {
            ipsForTor.addAll(preferenceRepository.getStringSetPreference(IPS_TO_UNLOCK));
        }

        connectivityCheckIps.clear();
        connectivityCheckIps.addAll(connectivityCheckManager.get().getConnectivityCheckIps());

        lock.writeLock().unlock();
    }

    void prepareForwarding() {
        lock.writeLock().lock();
        mapForwardPort.clear();
        mapForwardAddress.clear();

        ModuleState dnsCryptState = modulesStatus.getDnsCryptState();
        ModuleState torState = modulesStatus.getTorState();
        ModuleState itpdState = modulesStatus.getItpdState();
        ModuleState firewallState = modulesStatus.getFirewallState();

        int ownUID = pathVars.getAppUid();

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
            forwardDnsToDnsCrypt(dnsCryptPort, ownUID);
            if (itpdState == RUNNING) {
                forwardAddressToITPD(itpdHttpPort, ownUID);
            }
        } else if (torState == RUNNING && (torReady || !systemDNSAllowed)) {
            forwardDnsToTor(torDNSPort, ownUID);
        } else if (dnsCryptState != STOPPED) {
            forwardDnsToDnsCrypt(dnsCryptPort, ownUID);
        } else if (torState != STOPPED) {
            forwardDnsToTor(torDNSPort, ownUID);
        } else if (firewallState == STARTING || firewallState == RUNNING) {
            logi("Firewall only operation");
        } else {
            forwardDnsToDnsCrypt(dnsCryptPort, ownUID);
        }

        lock.writeLock().unlock();
    }

    private void forwardDnsToDnsCrypt(int dnsCryptPort, int ownUID) {
        addForwardPortRule(17, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, dnsCryptPort, ownUID);
        addForwardPortRule(6, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, dnsCryptPort, ownUID);
    }

    private void forwardDnsToTor(int torDNSPort, int ownUID) {
        addForwardPortRule(17, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, torDNSPort, ownUID);
        addForwardPortRule(6, PLAINTEXT_DNS_PORT, LOOPBACK_ADDRESS, torDNSPort, ownUID);
    }

    private void forwardAddressToITPD(int itpdHttpPort, int ownUID) {
        addForwardAddressRule(17, "10.191.0.1", LOOPBACK_ADDRESS, itpdHttpPort, ownUID);
        addForwardAddressRule(6, "10.191.0.1", LOOPBACK_ADDRESS, itpdHttpPort, ownUID);
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
        setUidAllowed.clear();
        setUidKnown.clear();
        ipsForTor.clear();
        uidLanAllowed.clear();
        uidSpecialLanAllowed.clear();
        uidSpecialAllowed.clear();
        mapForwardPort.clear();
        mapForwardAddress.clear();
        lock.writeLock().unlock();
    }
}
