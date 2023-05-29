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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn.service;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.Constants.G_DNG_41;
import static pan.alexander.tordnscrypt.utils.Constants.G_DNS_42;
import static pan.alexander.tordnscrypt.utils.Constants.G_DNS_61;
import static pan.alexander.tordnscrypt.utils.Constants.G_DNS_62;
import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX;
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
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_BLOCK_IPv6;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.BYPASS_LAN;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.COMPATIBILITY_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_ENABLED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_ADDRESS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_USE_IPV6;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_PROXY;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
        boolean blockIPv6DnsCrypt = prefs.getBoolean(DNSCRYPT_BLOCK_IPv6, false);
        boolean useIPv6Tor = prefs.getBoolean(TOR_USE_IPV6, true);
        boolean apIsOn = preferenceRepository.get().getBoolPreference(PreferenceKeys.WIFI_ACCESS_POINT_IS_ON);
        boolean modemIsOn = preferenceRepository.get().getBoolPreference(PreferenceKeys.USB_MODEM_IS_ON);
        boolean firewallEnabled = preferenceRepository.get().getBoolPreference(FIREWALL_ENABLED);
        boolean useProxy = prefs.getBoolean(USE_PROXY, false);
        if (useProxy && (prefs.getString(PROXY_ADDRESS, "").isEmpty()
                || prefs.getString(PROXY_PORT, "").isEmpty())) {
            useProxy = false;
        }
        boolean compatibilityMode;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            compatibilityMode = true;
        } else {
            compatibilityMode = defaultPreferences.get().getBoolean(COMPATIBILITY_MODE, false);
        }

        boolean torIsRunning = modulesStatus.getTorState() == RUNNING
                || modulesStatus.getTorState() == STARTING
                || modulesStatus.getTorState() == RESTARTING;

        // Build VPN service
        BuilderVPN builder = new BuilderVPN(vpn);
        builder.setSession(context.getString(R.string.app_name));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false);
        }

        // VPN address
        String vpn4 = prefs.getString("vpn4", "10.1.10.1");
        if (vpn4 == null) {
            vpn4 = "10.1.10.1";
        }
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

        boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();


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

        //if (!firewallEnabled || lan) {
            listExclude.add(new IPUtil.CIDR("224.0.0.0", 4)); // Broadcast
        //}
        // Subnet routing
        if (!listExclude.isEmpty()) {

            // Exclude IP ranges
            Collections.sort(listExclude);

            try {
                InetAddress start = InetAddress.getByName(META_ADDRESS);
                for (IPUtil.CIDR exclude : listExclude) {
                    //Log.i(LOG_TAG, "Exclude " + exclude.getStart().getHostAddress() + "..." + exclude.getEnd().getHostAddress());
                    for (IPUtil.CIDR include : IPUtil.toCIDR(start, IPUtil.minus1(exclude.getStart())))
                        try {
                            builder.addRoute(include.address, include.prefix);
                        } catch (Throwable ex) {
                            loge("VPNBuilder", ex, true);
                        }
                    start = IPUtil.plus1(exclude.getEnd());
                }
                String end = (lan ? "255.255.255.254" : "255.255.255.255");
                for (IPUtil.CIDR include : IPUtil.toCIDR("224.0.0.0", end))
                    try {
                        builder.addRoute(include.address, include.prefix);
                    } catch (Throwable ex) {
                        loge("VPNBuilder", ex, true);
                    }
            } catch (UnknownHostException ex) {
                loge("VPNBuilder", ex, true);
            }
        } else {
            builder.addRoute(META_ADDRESS, 0);
        }

        if (lan && (!(blockIPv6DnsCrypt && modulesStatus.getDnsCryptState() != STOPPED)
                || useIPv6Tor && modulesStatus.getDnsCryptState() == STOPPED
                && modulesStatus.getTorState() != STOPPED)) {
            //TODO bypass lan addresses
            builder.addRoute("::", 0);
        } else {
            builder.addRoute("::", 0);
        }

        // MTU
        int mtu = vpn.jni_get_mtu();
        logi("VPN MTU=" + mtu);
        builder.setMtu(mtu);

        // Add list of allowed applications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            try {
                builder.addDisallowedApplication(context.getPackageName());
                //Log.i(LOG_TAG, "VPN Not routing " + getPackageName());
            } catch (PackageManager.NameNotFoundException ex) {
                loge("VPNBuilder", ex, true);
            }

            if (fixTTL) {
                builder.setFixTTL(true);

                if (!useProxy) {
                    for (Rule rule : listRule) {
                        try {
                            //Log.i(LOG_TAG, "VPN Not routing " + rule.packageName);
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

        builder.setConfigureIntent(getConfigureIntent());

        return builder;
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
                && modulesStatus.getTorState() != STOPPED);

        // Get custom DNS servers
        List<String> dnscryptBootstrapResolversIPv4 = new ArrayList<>();
        List<String> dnscryptBootstrapResolversIPv6 = new ArrayList<>();
        String resolvers = App.getInstance().getDaggerComponent().getPathVars().get().getDNSCryptFallbackRes();
        for (String resolver: resolvers.split(", ?")) {
            if (resolver.matches(IPv4_REGEX)) {
                dnscryptBootstrapResolversIPv4.add(resolver);
            } else if (resolver.matches(IPv6_REGEX)) {
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

        if (vpnDns1.equals(QUAD_DNS_41) || vpnDns1.equals(QUAD_DNS_42)) {
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
