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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn.service;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.Constants.C_DNS_41;
import static pan.alexander.tordnscrypt.utils.Constants.C_DNS_42;
import static pan.alexander.tordnscrypt.utils.Constants.C_DNS_61;
import static pan.alexander.tordnscrypt.utils.Constants.C_DNS_62;
import static pan.alexander.tordnscrypt.utils.Constants.DEFAULT_PROXY_PORT;
import static pan.alexander.tordnscrypt.utils.Constants.G_DNG_41;
import static pan.alexander.tordnscrypt.utils.Constants.G_DNS_42;
import static pan.alexander.tordnscrypt.utils.Constants.G_DNS_61;
import static pan.alexander.tordnscrypt.utils.Constants.G_DNS_62;
import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX_NO_CAPTURING;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_41;
import static pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_42;
import static pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_61;
import static pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_62;
import static pan.alexander.tordnscrypt.utils.Constants.VPN_DNS_2;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_BYPASS_VPN;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_BLOCK_IPv6;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.BYPASS_LAN;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.COMPATIBILITY_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_ENABLED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_ADDRESS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_USE_IPV6;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_PROXY;
import static pan.alexander.tordnscrypt.vpn.VpnUtils.multicastIPv6;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.IpPrefix;
import android.os.Build;
import android.text.TextUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractor;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker;
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys;
import pan.alexander.tordnscrypt.vpn.IPUtil;
import pan.alexander.tordnscrypt.vpn.Rule;
import pan.alexander.tordnscrypt.vpn.VpnUtils;

public class VpnBuilder {

    public static volatile ConcurrentSkipListSet<String> vpnDnsSet;

    private final Context context;
    private final Lazy<DnsInteractor> dnsInteractor;
    private final Lazy<SharedPreferences> defaultPreferences;
    private final Lazy<PreferenceRepository> preferenceRepository;

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    @Inject
    public VpnBuilder(
            Context context,
            Lazy<DnsInteractor> dnsInteractor,
            @Named(DEFAULT_PREFERENCES_NAME)
            Lazy<SharedPreferences> defaultPreferences,
            Lazy<PreferenceRepository> preferenceRepository
    ) {
        this.context = context;
        this.dnsInteractor = dnsInteractor;
        this.defaultPreferences = defaultPreferences;
        this.preferenceRepository = preferenceRepository;
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    BuilderVPN getBuilder(ServiceVPN vpn, List<String> listAllowed, List<Rule> listRule) {
        SharedPreferences prefs = defaultPreferences.get();
        boolean lan = prefs.getBoolean(BYPASS_LAN, true);
        boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();

        // Build VPN service
        BuilderVPN builder = new BuilderVPN(vpn);
        builder.setSession(context.getString(R.string.app_name));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        // VPN address
        String vpn4 = prefs.getString("vpn4", "10.1.10.1");
        logi("VPN Using VPN4=" + vpn4);
        builder.addAddress(vpn4, 32);

        String vpn6 = prefs.getString("vpn6", "fd00:1:fd00:1:fd00:1:fd00:1");
        logi("VPN Using VPN6=" + vpn6);
        builder.addAddress(vpn6, 128);

        // DNS address
        for (InetAddress dns : getDns()) {
            logi("VPN Using DNS=" + dns);
            builder.addDnsServer(dns);
        }

        addIPv4Routes(builder, lan, fixTTL);
        addIPv6Routes(builder, lan);

        // MTU
        int mtu = vpn.jni_get_mtu();
        logi("VPN MTU=" + mtu);
        builder.setMtu(mtu);

        // Add list of allowed applications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            manageAppsTunneling(builder, listRule, fixTTL);
        }

        builder.setConfigureIntent(getConfigureIntent());

        return builder;
    }

    private void addIPv4Routes(BuilderVPN builder, boolean lan, boolean fixTTL) {
        boolean firewallEnabled = preferenceRepository.get().getBoolPreference(FIREWALL_ENABLED);
        boolean apIsOn = preferenceRepository.get().getBoolPreference(PreferenceKeys.WIFI_ACCESS_POINT_IS_ON);
        boolean modemIsOn = preferenceRepository.get().getBoolPreference(PreferenceKeys.USB_MODEM_IS_ON);
        boolean compatibilityMode;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            compatibilityMode = true;
        } else {
            compatibilityMode = defaultPreferences.get().getBoolean(COMPATIBILITY_MODE, false);
        }
        boolean torIsRunning = modulesStatus.getTorState() == RUNNING
                || modulesStatus.getTorState() == STARTING
                || modulesStatus.getTorState() == RESTARTING;

        List<IPUtil.CIDR> listExclude = new ArrayList<>();
        if (!firewallEnabled || compatibilityMode || fixTTL) {
            listExclude.add(new IPUtil.CIDR("127.0.0.0", 8)); // localhost
        }

        if (!torIsRunning && (apIsOn || modemIsOn) && !fixTTL) {
            // USB tethering 192.168.42.x
            // Wi-Fi tethering 192.168.43.x
            listExclude.add(new IPUtil.CIDR("192.168.42.0", 23));
            // Bluetooth tethering 192.168.44.x
            listExclude.add(new IPUtil.CIDR("192.168.44.0", 24));
            // Wi-Fi direct 192.168.49.x
            listExclude.add(new IPUtil.CIDR("192.168.49.0", 24));
        }

        if (lan) {
            listExclude.add(new IPUtil.CIDR("224.0.0.0", 4)); // Multicast
        }

        Collections.sort(listExclude);

        if (!listExclude.isEmpty()) {
            try {
                InetAddress start = InetAddress.getByName(META_ADDRESS);
                for (IPUtil.CIDR exclude : listExclude) {
                    for (IPUtil.CIDR include : IPUtil.toCIDR(start, IPUtil.minus1(exclude.getStart())))
                        try {
                            builder.addRoute(include.address, include.prefix);
                        } catch (Throwable ex) {
                            loge("VPNBuilder addIPv4Routes", ex, true);
                        }
                    start = IPUtil.plus1(exclude.getEnd());
                }
                String end = (lan ? "255.255.255.254" : "255.255.255.255");
                for (IPUtil.CIDR include : IPUtil.toCIDR(lan ? "240.0.0.0" : "224.0.0.0", end))
                    try {
                        builder.addRoute(include.address, include.prefix);
                    } catch (Throwable ex) {
                        loge("VPNBuilder addIPv4Routes", ex, true);
                    }
            } catch (UnknownHostException ex) {
                loge("VPNBuilder addIPv4Routes", ex, true);
            }
        } else {
            builder.addRoute(META_ADDRESS, 0);
        }
    }

    private void addIPv6Routes(BuilderVPN builder, boolean lan) {
        SharedPreferences prefs = defaultPreferences.get();
        boolean blockIPv6DnsCrypt = prefs.getBoolean(DNSCRYPT_BLOCK_IPv6, false);
        boolean useIPv6Tor = prefs.getBoolean(TOR_USE_IPV6, true);
        boolean captivePortal = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            captivePortal = NetworkChecker.isCaptivePortalDetected(context);
        }

        if (lan && (!(blockIPv6DnsCrypt && modulesStatus.getDnsCryptState() != STOPPED)
                || useIPv6Tor && modulesStatus.getDnsCryptState() == STOPPED
                && modulesStatus.getTorState() != STOPPED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && captivePortal) {
                try {
                    builder.addRoute("::", 0);
                    for (String line: multicastIPv6) {
                        String address;
                        int prefix;
                        if (line.contains("/")) {
                            address = line.substring(0, line.indexOf("/"));
                            prefix = Integer.parseInt(line.substring(line.indexOf("/") + 1));
                        } else {
                            address = line;
                            prefix = 128;
                        }
                        try {
                            builder.excludeRoute(new IpPrefix(InetAddress.getByName(address), prefix));
                        } catch (Exception e) {
                            loge("VPNBuilder addIPv6Routes", e);
                        }
                    }
                } catch (Exception e) {
                    loge("VPNBuilder addIPv6Routes", e);
                }
            } else {
                //https://datatracker.ietf.org/doc/html/rfc4291
                //Exclude "ff00::/8" Multicast
                final List<String> multicastExcluded = new ArrayList<>(Arrays.asList(
                        "::/1",
                        "8000::/2",
                        "c000::/3",
                        "e000::/4",
                        "f000::/5",
                        "f800::/6",
                        "fc00::/7",
                        "fe00::/8"
                ));
                for (String route : multicastExcluded) {
                    String[] address = route.split("/");
                    try {
                        builder.addRoute(address[0], Integer.parseInt(address[1]));
                    } catch (Exception e) {
                        loge("VPNBuilder addIPv6Routes", e);
                    }
                }
            }
        } else {
            builder.addRoute("::", 0);
        }
    }

    private void manageAppsTunneling(BuilderVPN builder, List<Rule> listRule, boolean fixTTL) {
        SharedPreferences prefs = defaultPreferences.get();
        Set<String> setVpnBypassApps = preferenceRepository.get().getStringSetPreference(APPS_BYPASS_VPN);
        boolean useProxy = prefs.getBoolean(USE_PROXY, false);
        if (useProxy && (prefs.getString(PROXY_ADDRESS, LOOPBACK_ADDRESS).isEmpty()
                || prefs.getString(PROXY_PORT, DEFAULT_PROXY_PORT).isEmpty())) {
            useProxy = false;
        }
        try {
            builder.addDisallowedApplication(context.getPackageName());
            for (String pack: setVpnBypassApps) {
                builder.addDisallowedApplication(pack);
                logi("VPN Not routing " + pack);
            }
        } catch (PackageManager.NameNotFoundException ex) {
            loge("VPNBuilder", ex, true);
        }

        if (fixTTL) {
            builder.setFixTTL(true);

            if (!useProxy) {
                for (Rule rule : listRule) {
                    try {
                        //logi("VPN Not routing " + rule.packageName);
                        builder.addDisallowedApplication(rule.packageName);
                    } catch (PackageManager.NameNotFoundException ex) {
                        loge("VPNBuilder", ex, true);
                    }
                }
            } else {
                try {
                    builder.addDisallowedApplication(context.getPackageName());
                } catch (PackageManager.NameNotFoundException ex) {
                    loge("VPNBuilder", ex, true);
                }
            }

        }
    }

    private List<InetAddress> getDns() {
        String vpnDns1;

        if (vpnDnsSet == null) {
            vpnDnsSet = new ConcurrentSkipListSet<>();
        }

        List<InetAddress> listDns = new ArrayList<>();
        List<String> sysDns = VpnUtils.getDefaultDNS(context);

        SharedPreferences prefs = defaultPreferences.get();
        boolean blockIPv6DnsCrypt = prefs.getBoolean(DNSCRYPT_BLOCK_IPv6, false);
        boolean useIPv6Tor = prefs.getBoolean(TOR_USE_IPV6, true);

        boolean ip6 = (!blockIPv6DnsCrypt && modulesStatus.getDnsCryptState() != STOPPED
                || useIPv6Tor && modulesStatus.getDnsCryptState() == STOPPED
                && modulesStatus.getTorState() != STOPPED
                || modulesStatus.getDnsCryptState() == STOPPED
                && modulesStatus.getTorState() == STOPPED
                && modulesStatus.getFirewallState() == RUNNING);

        // Get custom DNS servers
        List<String> dnscryptBootstrapResolversIPv4 = new ArrayList<>();
        List<String> dnscryptBootstrapResolversIPv6 = new ArrayList<>();
        String resolvers = App.getInstance().getDaggerComponent().getPathVars().get().getDNSCryptFallbackRes();
        for (String resolver: resolvers.split(", ?")) {
            if (resolver.matches(IPv4_REGEX)) {
                dnscryptBootstrapResolversIPv4.add(resolver);
            } else if (resolver.matches(IPv6_REGEX_NO_CAPTURING)) {
                dnscryptBootstrapResolversIPv6.add(resolver);
            }
        }

        if (dnscryptBootstrapResolversIPv4.isEmpty()) {
            vpnDns1 = QUAD_DNS_41;
        } else {
            vpnDns1 = dnscryptBootstrapResolversIPv4.get(0);
        }

        if (vpnDns1 != null) {
            try {
                InetAddress dns = InetAddress.getByName(vpnDns1);
                if (!(dns.isLoopbackAddress() || dns.isAnyLocalAddress()) &&
                        (ip6 || dns instanceof Inet4Address)) {
                    listDns.add(dns);
                    vpnDnsSet.add(vpnDns1);
                }
            } catch (Throwable ignored) {
            }
        }

        if (vpnDnsSet.size() == 1 && !QUAD_DNS_41.equals(vpnDns1) && !QUAD_DNS_42.equals(vpnDns1)) {
            try {
                if (vpnDns1 != null) {
                    String name = dnsInteractor.get().reverseResolve(vpnDns1);
                    if (!name.isEmpty()) {
                        vpnDnsSet.addAll(dnsInteractor.get().resolveDomain("https://" + name, ip6));
                    }
                }
                logi("VPNBuilder vpnDnsSet " + vpnDnsSet);
            } catch (Exception e) {
                logw("VPNBuilder getDns", e);
            }
        }

        String vpnDns2 = VPN_DNS_2;
        if (ip6 && !dnscryptBootstrapResolversIPv6.isEmpty()) {
            vpnDns2 = dnscryptBootstrapResolversIPv6.get(0);
        } else if (dnscryptBootstrapResolversIPv4.size() > 1) {
            vpnDns2 = dnscryptBootstrapResolversIPv4.get(1);
        }

        try {
            InetAddress dns = InetAddress.getByName(vpnDns2);
            if (!(dns.isLoopbackAddress() || dns.isAnyLocalAddress()) &&
                    (ip6 || dns instanceof Inet4Address)) {
                listDns.add(dns);
                vpnDnsSet.add(VPN_DNS_2);
            }
        } catch (Throwable ex) {
            loge("VPNBuilder", ex, true);
        }

        if (vpnDns1 == null) {
            vpnDns1 = QUAD_DNS_41;
        }

        vpnDnsSet.add(G_DNG_41);
        vpnDnsSet.add(G_DNS_42);
        vpnDnsSet.add(G_DNS_61);
        vpnDnsSet.add(G_DNS_62);

        vpnDnsSet.add(C_DNS_41);
        vpnDnsSet.add(C_DNS_42);
        vpnDnsSet.add(C_DNS_61);
        vpnDnsSet.add(C_DNS_62);

        if (vpnDns1.equals(QUAD_DNS_41) || vpnDns1.equals(QUAD_DNS_42)
                || vpnDns1.equals(QUAD_DNS_61) || vpnDns1.equals(QUAD_DNS_62)
                || vpnDns2.equals(QUAD_DNS_41) || vpnDns2.equals(QUAD_DNS_42)
                || vpnDns2.equals(QUAD_DNS_61) || vpnDns2.equals(QUAD_DNS_62)
        ) {
            vpnDnsSet.add(QUAD_DNS_41);
            vpnDnsSet.add(QUAD_DNS_42);
            vpnDnsSet.add(QUAD_DNS_61);
            vpnDnsSet.add(QUAD_DNS_62);
        }

        logi("VPN DNS system=" + TextUtils.join(",", sysDns) + " config=" + vpnDns1 + "," + vpnDns2);

        if (listDns.size() == 2) {
            return listDns;
        }

        for (String def_dns : sysDns)
            try {
                InetAddress ddns = InetAddress.getByName(def_dns);
                if (!listDns.contains(ddns) &&
                        !(ddns.isLoopbackAddress() || ddns.isAnyLocalAddress()) &&
                        (ip6 || ddns instanceof Inet4Address)) {
                    listDns.add(ddns);
                    vpnDnsSet.add(def_dns);
                }
            } catch (Throwable ex) {
                loge("VPNBuilder", ex, true);
            }

        // Always set DNS servers
        if (listDns.size() == 0)
            try {
                listDns.add(InetAddress.getByName(G_DNG_41));
                listDns.add(InetAddress.getByName(G_DNS_42));
                if (ip6) {
                    listDns.add(InetAddress.getByName(G_DNS_61));
                    listDns.add(InetAddress.getByName(G_DNS_62));
                }
            } catch (Throwable ex) {
                loge("VPNBuilder", ex, true);
            }

        logi("VPN Get DNS=" + TextUtils.join(",", listDns));

        return listDns;
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private PendingIntent getConfigureIntent() {
        Intent configure = new Intent(context, MainActivity.class);
        PendingIntent pi;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pi = PendingIntent.getActivity(
                    context,
                    0,
                    configure,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            pi = PendingIntent.getActivity(
                    context,
                    0,
                    configure,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }
        return pi;
    }

}
