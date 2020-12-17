package pan.alexander.tordnscrypt.vpn.service;
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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import pan.alexander.tordnscrypt.BootCompleteReceiver;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSQueryLogRecord;
import pan.alexander.tordnscrypt.iptables.Tethering;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ServiceNotification;
import pan.alexander.tordnscrypt.modules.UsageStatisticKt;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.firewall.FirewallFragmentKt;
import pan.alexander.tordnscrypt.settings.firewall.FirewallNotification;
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.VPNCommand;
import pan.alexander.tordnscrypt.vpn.Allowed;
import pan.alexander.tordnscrypt.vpn.Forward;
import pan.alexander.tordnscrypt.vpn.IPUtil;
import pan.alexander.tordnscrypt.vpn.Packet;
import pan.alexander.tordnscrypt.vpn.ResourceRecord;
import pan.alexander.tordnscrypt.vpn.Rule;
import pan.alexander.tordnscrypt.vpn.Usage;
import pan.alexander.tordnscrypt.vpn.Util;

import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;
import static pan.alexander.tordnscrypt.modules.ModulesService.actionStopServiceForeground;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesDefault;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesOwn;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper.reload;

public class ServiceVPN extends VpnService {
    static {
        try {
            System.loadLibrary("invizible");
        } catch (UnsatisfiedLinkError ignored) {
            System.exit(1);
        }
    }

    final static int linesInDNSQueryRawRecords = 500;

    static final String EXTRA_COMMAND = "Command";
    static final String EXTRA_REASON = "Reason";

    NotificationManager notificationManager;
    private static final Object jni_lock = new Object();
    private static long jni_context = 0;

    volatile boolean last_connected = false;
    public volatile boolean last_connected_override = false;

    ParcelFileDescriptor vpn = null;

    private boolean registeredIdleState = false;
    private boolean registeredPackageChanged = false;
    private boolean registeredConnectivityChanged = false;

    private PathVars pathVars;
    private static final int ownUID = Process.myUid();

    private Object networkCallback = null;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    private final ReentrantReadWriteLock rrLock = new ReentrantReadWriteLock(true);
    private final LinkedList<DNSQueryLogRecord> dnsQueryRawRecords = new LinkedList<>();

    private volatile Looper commandLooper;
    private volatile ServiceVPNHandler commandHandler;

    private Thread tunnelThread = null;

    private ModulesStatus modulesStatus;

    public volatile boolean canFilter = true;

    private boolean blockHttp = false;
    private boolean routeAllThroughTor = true;
    private boolean torTethering = false;
    private String torVirtualAddressNetwork = "10.192.0.0/10";
    private final String itpdRedirectAddress = "10.191.0.1";
    private boolean blockIPv6 = false;
    volatile boolean reloading;
    private boolean compatibilityMode;
    private boolean arpSpoofingDetection;
    private boolean blockInternetWhenArpAttackDetected;
    private boolean lan = false;
    private boolean firewallEnabled;
    public static CopyOnWriteArrayList<String> vpnDNS;

    private boolean useProxy = false;
    private Set<String> setBypassProxy;
    private boolean fixTTL;
    private FirewallNotification firewallNotificationReceiver;

    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Boolean> mapUidAllowed = new HashMap<>();
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Integer> mapUidKnown = new HashMap<>();
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Forward> mapForwardPort = new HashMap<>();
    private final Map<String, Forward> mapForwardAddress = new HashMap<>();
    private final Set<String> ipsForTor = new HashSet<>();
    private final Set<Integer> uidLanAllowed = new HashSet<>();
    private final Set<Integer> uidSpecialAllowed = new HashSet<>();

    private final VPNBinder binder = new VPNBinder();

    private native long jni_init(int sdk);

    private native void jni_start(long context, int loglevel);

    private native void jni_run(long context, int tun, boolean fwd53, int rcode, boolean compatibilityMode, boolean canFilterSynchronous);

    private native void jni_stop(long context);

    private native void jni_clear(long context);

    private native int jni_get_mtu();

    private native void jni_socks5_for_tor(String addr, int port, String username, String password);

    private native void jni_socks5_for_proxy(String addr, int port, String username, String password);

    private native void jni_done(long context);

    private static List<InetAddress> getDns(Context context) {
        vpnDNS = new CopyOnWriteArrayList<>();
        List<InetAddress> listDns = new ArrayList<>();
        List<String> sysDns = Util.getDefaultDNS(context);

        // Get custom DNS servers
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        boolean ip6 = prefs.getBoolean("ipv6", false);
        String vpnDns1 = PathVars.getInstance(context).getDNSCryptFallbackRes();
        String vpnDns2 = prefs.getString("dns2", "116.202.176.26");
        Log.i(LOG_TAG, "VPN DNS system=" + TextUtils.join(",", sysDns) + " config=" + vpnDns1 + "," + vpnDns2);

        if (vpnDns1 != null)
            try {
                InetAddress dns = InetAddress.getByName(vpnDns1);
                if (!(dns.isLoopbackAddress() || dns.isAnyLocalAddress()) &&
                        (ip6 || dns instanceof Inet4Address)) {
                    listDns.add(dns);
                    vpnDNS.add(vpnDns1);
                }
            } catch (Throwable ignored) {
            }

        if (vpnDns2 != null)
            try {
                InetAddress dns = InetAddress.getByName(vpnDns2);
                if (!(dns.isLoopbackAddress() || dns.isAnyLocalAddress()) &&
                        (ip6 || dns instanceof Inet4Address)) {
                    listDns.add(dns);
                    vpnDNS.add(vpnDns2);
                }
            } catch (Throwable ex) {
                Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }

        if (listDns.size() == 2)
            return listDns;

        for (String def_dns : sysDns)
            try {
                InetAddress ddns = InetAddress.getByName(def_dns);
                if (!listDns.contains(ddns) &&
                        !(ddns.isLoopbackAddress() || ddns.isAnyLocalAddress()) &&
                        (ip6 || ddns instanceof Inet4Address)) {
                    listDns.add(ddns);
                    vpnDNS.add(def_dns);
                }
            } catch (Throwable ex) {
                Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }

        // Always set DNS servers
        if (listDns.size() == 0)
            try {
                listDns.add(InetAddress.getByName("8.8.8.8"));
                listDns.add(InetAddress.getByName("8.8.4.4"));
                vpnDNS.add("8.8.8.8");
                vpnDNS.add("8.8.4.4");
                if (ip6) {
                    listDns.add(InetAddress.getByName("2001:4860:4860::8888"));
                    listDns.add(InetAddress.getByName("2001:4860:4860::8844"));
                }
            } catch (Throwable ex) {
                Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }

        Log.i(LOG_TAG, "VPN Get DNS=" + TextUtils.join(",", listDns));

        return listDns;
    }

    BuilderVPN getBuilder(List<String> listAllowed, List<Rule> listRule) {
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        //boolean ip6 = prefs.getBoolean("ipv6", true);
        boolean ip6 = true;
        boolean subnet = prefs.getBoolean("VPN subnet", true);
        lan = prefs.getBoolean("Allow LAN", false);
        boolean apIsOn = new PrefManager(this).getBoolPref("APisON");
        boolean modemIsOn = new PrefManager(this).getBoolPref("ModemIsON");
        useProxy = prefs.getBoolean("swUseProxy", false);

        boolean torIsRunning = modulesStatus.getTorState() == RUNNING;

        // Build VPN service
        BuilderVPN builder = new BuilderVPN(this);
        builder.setSession(getString(R.string.app_name));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(Util.isMeteredNetwork(this));
        }


        // VPN address
        String vpn4 = prefs.getString("vpn4", "10.1.10.1");
        if (vpn4 == null) {
            vpn4 = "10.1.10.1";
        }
        Log.i(LOG_TAG, "VPN Using VPN4=" + vpn4);
        builder.addAddress(vpn4, 32);
        if (ip6) {
            String vpn6 = prefs.getString("vpn6", "fd00:1:fd00:1:fd00:1:fd00:1");
            Log.i(LOG_TAG, "VPN Using VPN6=" + vpn6);
            builder.addAddress(vpn6, 128);
        }

        // DNS address
        for (InetAddress dns : getDns(this)) {
            Log.i(LOG_TAG, "VPN Using DNS=" + dns);
            builder.addDnsServer(dns);
        }

        fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();

        // Subnet routing
        if (subnet /*&& Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP*/) {
            // Exclude IP ranges
            List<IPUtil.CIDR> listExclude = new ArrayList<>();
            listExclude.add(new IPUtil.CIDR("127.0.0.0", 8)); // localhost

            if (!torIsRunning && (apIsOn || modemIsOn) && !fixTTL) {
                // USB tethering 192.168.42.x
                // Wi-Fi tethering 192.168.43.x
                listExclude.add(new IPUtil.CIDR("192.168.42.0", 23));
                // Bluetooth tethering 192.168.44.x
                listExclude.add(new IPUtil.CIDR("192.168.44.0", 24));
                // Wi-Fi direct 192.168.49.x
                listExclude.add(new IPUtil.CIDR("192.168.49.0", 24));
            }

            // Broadcast
            listExclude.add(new IPUtil.CIDR("224.0.0.0", 4));

            Collections.sort(listExclude);

            try {
                InetAddress start = InetAddress.getByName("0.0.0.0");
                for (IPUtil.CIDR exclude : listExclude) {
                    //Log.i(LOG_TAG, "Exclude " + exclude.getStart().getHostAddress() + "..." + exclude.getEnd().getHostAddress());
                    for (IPUtil.CIDR include : IPUtil.toCIDR(start, IPUtil.minus1(exclude.getStart())))
                        try {
                            builder.addRoute(include.address, include.prefix);
                        } catch (Throwable ex) {
                            Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    start = IPUtil.plus1(exclude.getEnd());
                }
                String end = (lan ? "255.255.255.254" : "255.255.255.255");
                for (IPUtil.CIDR include : IPUtil.toCIDR("224.0.0.0", end))
                    try {
                        builder.addRoute(include.address, include.prefix);
                    } catch (Throwable ex) {
                        Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    }
            } catch (UnknownHostException ex) {
                Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }
        } else if (fixTTL) {
            // USB tethering 192.168.42.x
            // Wi-Fi tethering 192.168.43.x
            builder.addRoute("192.168.42.0", 23);
        } else {
            builder.addRoute("0.0.0.0", 0);
        }

        if (ip6) {
            builder.addRoute("::", 0);
            //builder.addRoute("2000::", 3); // unicast
        }

        // MTU
        int mtu = jni_get_mtu();
        Log.i(LOG_TAG, "VPN MTU=" + mtu);
        builder.setMtu(mtu);

        // Add list of allowed applications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            try {
                builder.addDisallowedApplication(getPackageName());
                //Log.i(LOG_TAG, "VPN Not routing " + getPackageName());
            } catch (PackageManager.NameNotFoundException ex) {
                Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }

            if (fixTTL) {
                builder.setFixTTL(true);

                if (!useProxy) {
                    for (Rule rule : listRule) {
                        try {
                            //Log.i(LOG_TAG, "VPN Not routing " + rule.packageName);
                            builder.addDisallowedApplication(rule.packageName);
                        } catch (PackageManager.NameNotFoundException ex) {
                            Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    }
                } else {
                    try {
                        builder.addDisallowedApplication(getPackageName());
                    } catch (PackageManager.NameNotFoundException ex) {
                        Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    }
                }

            }

        }

        // Build configure intent
        Intent configure = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, configure, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setConfigureIntent(pi);

        return builder;
    }

    void startNative(final ParcelFileDescriptor vpn, List<String> listAllowed, List<Rule> listRule) {

        pathVars = PathVars.getInstance(this);
        torVirtualAddressNetwork = pathVars.getTorVirtAdrNet();

        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);

        blockHttp = prefs.getBoolean("pref_fast_block_http", false);
        routeAllThroughTor = prefs.getBoolean("pref_fast_all_through_tor", true);
        torTethering = prefs.getBoolean("pref_common_tor_tethering", false);
        blockIPv6 = prefs.getBoolean("block_ipv6", true);
        arpSpoofingDetection = prefs.getBoolean("pref_common_arp_spoofing_detection", false);
        blockInternetWhenArpAttackDetected = prefs.getBoolean("pref_common_arp_block_internet", false);
        firewallEnabled = new PrefManager(this).getBoolPref("FirewallEnabled");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            compatibilityMode = true;
        } else {
            compatibilityMode = prefs.getBoolean("swCompatibilityMode", false);
        }

        useProxy = prefs.getBoolean("swUseProxy", false);
        String proxyAddress = prefs.getString("ProxyServer", "");
        if (proxyAddress == null) {
            proxyAddress = "";
        }
        String proxyPortStr = prefs.getString("ProxyPort", "");
        int proxyPort = 0;
        if (proxyPortStr != null && proxyPortStr.matches("\\d+")) {
            proxyPort = Integer.parseInt(proxyPortStr);
        }
        setBypassProxy = new PrefManager(this).getSetStrPref("clearnetAppsForProxy");

        // Prepare rules
        prepareUidAllowed(listAllowed, listRule);
        prepareForwarding();

        int prio = 5;
        String prioStr = prefs.getString("loglevel", Integer.toString(Log.ERROR));
        if (prioStr != null) {
            prio = Integer.parseInt(prioStr);
        }

        int rcode = 3;
        String rcodeStr = prefs.getString("rcode", "3");
        if (rcodeStr != null) {
            rcode = Integer.parseInt(rcodeStr);
        }
        int finalRcode = rcode;

        int torSOCKSPort = 9050;

        try {
            torSOCKSPort = Integer.parseInt(pathVars.getTorSOCKSPort());
        } catch (Exception e) {
            Log.e(LOG_TAG, "VPN SOCKS Parse Exception " + e.getMessage() + " " + e.getCause());
        }

        fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();

        if (modulesStatus.getTorState() == RUNNING && !fixTTL) {
            jni_socks5_for_tor("127.0.0.1", torSOCKSPort, "", "");
        } else {
            jni_socks5_for_tor("", 0, "", "");
        }

        if (useProxy && !proxyAddress.isEmpty() && proxyPort != 0) {
            jni_socks5_for_proxy(proxyAddress, proxyPort, "", "");
        } else {
            jni_socks5_for_proxy("", 0, "", "");
            useProxy = false;
        }

        if (tunnelThread == null) {
            Log.i(LOG_TAG, "VPN Starting tunnel thread context=" + jni_context);
            jni_start(jni_context, prio);

            tunnelThread = new Thread(() -> {
                try {
                    Log.i(LOG_TAG, "VPN Running tunnel context=" + jni_context);
                    boolean canFilterSynchronous = true;
                    if (compatibilityMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        canFilterSynchronous = Util.canFilter();
                    }
                    jni_run(jni_context, vpn.getFd(), mapForwardPort.containsKey(53), finalRcode, compatibilityMode, canFilterSynchronous);
                    Log.i(LOG_TAG, "VPN Tunnel exited");
                    tunnelThread = null;
                } catch (Exception e) {
                    Toast.makeText(ServiceVPN.this, e.getMessage() + " " + e.getCause(), Toast.LENGTH_LONG).show();
                    Log.e(LOG_TAG, "ServiceVPN startNative exception " + e.getMessage() + " " + e.getCause());
                }

            });

            tunnelThread.start();

            Log.i(LOG_TAG, "VPN Started tunnel thread");
        }
    }

    void stopNative() {
        Log.i(LOG_TAG, "VPN Stop native");

        if (tunnelThread != null) {
            Log.i(LOG_TAG, "VPN Stopping tunnel thread");

            jni_stop(jni_context);

            Thread thread = tunnelThread;
            while (thread != null && thread.isAlive()) {
                try {
                    Log.i(LOG_TAG, "VPN Joining tunnel thread context=" + jni_context);
                    thread.join();
                } catch (InterruptedException e) {
                    Log.i(LOG_TAG, "VPN Joined tunnel interrupted");
                }
                thread = tunnelThread;
            }
            tunnelThread = null;

            jni_clear(jni_context);

            Log.i(LOG_TAG, "VPN Stopped tunnel thread");
        }
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

    private void prepareUidAllowed(List<String> listAllowed, List<Rule> listRule) {
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
        for (String uid : new PrefManager(this).getSetStrPref(FirewallFragmentKt.APPS_ALLOW_LAN_PREF)) {
            if (uid != null && uid.matches("\\d+")) {
                uidLanAllowed.add(Integer.valueOf(uid));
            }
        }

        ipsForTor.clear();
        if (routeAllThroughTor) {
            ipsForTor.addAll(new PrefManager(this).getSetStrPref("ipsForClearNet"));
        } else {
            ipsForTor.addAll(new PrefManager(this).getSetStrPref("ipsToUnlock"));
        }

        lock.writeLock().unlock();
    }

    private void prepareForwarding() {
        lock.writeLock().lock();
        mapForwardPort.clear();
        mapForwardAddress.clear();

        ModuleState dnsCryptState = modulesStatus.getDnsCryptState();
        ModuleState torState = modulesStatus.getTorState();
        ModuleState itpdState = modulesStatus.getItpdState();

        int dnsCryptPort = 5354;
        int torDNSPort = 5400;
        int itpdHttpPort = 4444;
        try {
            dnsCryptPort = Integer.parseInt(pathVars.getDNSCryptPort());
            torDNSPort = Integer.parseInt(pathVars.getTorDNSPort());
            itpdHttpPort = Integer.parseInt(pathVars.getITPDHttpProxyPort());
        } catch (Exception e) {
            Log.e(LOG_TAG, "VPN Redirect Ports Parse Exception " + e.getMessage() + " " + e.getCause());
        }

        boolean torReady = new PrefManager(this).getBoolPref("Tor Ready");
        boolean useDefaultBridges = new PrefManager(this).getBoolPref("useDefaultBridges");
        boolean useOwnBridges = new PrefManager(this).getBoolPref("useOwnBridges");
        boolean bridgesSnowflakeDefault = new PrefManager(this).getStrPref("defaultBridgesObfs").equals(snowFlakeBridgesDefault);
        boolean bridgesSnowflakeOwn = new PrefManager(this).getStrPref("ownBridgesObfs").equals(snowFlakeBridgesOwn);
        boolean dnsCryptSystemDNSAllowed = new PrefManager(this).getBoolPref("DNSCryptSystemDNSAllowed");

        if (dnsCryptState == RUNNING && !dnsCryptSystemDNSAllowed) {
            addForwardPortRule(17, 53, "127.0.0.1", dnsCryptPort, ownUID);
            addForwardPortRule(6, 53, "127.0.0.1", dnsCryptPort, ownUID);

            if (itpdState == RUNNING) {
                addForwardAddressRule(17, "10.191.0.1", "127.0.0.1", itpdHttpPort, ownUID);
                addForwardAddressRule(6, "10.191.0.1", "127.0.0.1", itpdHttpPort, ownUID);
            }
        } else if (torState == RUNNING
                && (torReady || !(useDefaultBridges && bridgesSnowflakeDefault || useOwnBridges && bridgesSnowflakeOwn))) {
            addForwardPortRule(17, 53, "127.0.0.1", torDNSPort, ownUID);
            addForwardPortRule(6, 53, "127.0.0.1", torDNSPort, ownUID);
        }

        lock.writeLock().unlock();
    }

    private void addForwardPortRule(int protocol, int dport, String raddr, int rport, int ruid) {
        Forward fwd = new Forward();
        fwd.protocol = protocol;
        fwd.dport = dport;
        fwd.raddr = raddr;
        fwd.rport = rport;
        fwd.ruid = ruid;
        mapForwardPort.put(fwd.dport, fwd);
        Log.i(LOG_TAG, "VPN Forward " + fwd);
    }

    private void addForwardAddressRule(int protocol, String daddr, String raddr, int rport, int ruid) {
        Forward fwd = new Forward();
        fwd.protocol = protocol;
        fwd.daddr = daddr;
        fwd.raddr = raddr;
        fwd.rport = rport;
        fwd.ruid = ruid;
        mapForwardAddress.put(fwd.daddr, fwd);
        Log.i(LOG_TAG, "VPN Forward " + fwd);
    }

    // Called from native code
    public void nativeExit(String reason) {
        Log.w(LOG_TAG, "VPN Native exit reason=" + reason);
        if (reason != null) {
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean("VPNServiceEnabled", false).apply();
        }
    }

    // Called from native code
    public void nativeError(int error, String message) {
        Log.e(LOG_TAG, "VPN Native error " + error + ": " + message);
    }

    // Called from native code
    private void logPacket(Packet packet) {
        //Log.i(LOG_TAG, "VPN Log packet " + packet.toString());
    }

    // Called from native code
    public void dnsResolved(ResourceRecord rr) {

        try {

            rrLock.writeLock().lockInterruptibly();

            DNSQueryLogRecord lastRecord = dnsQueryRawRecords.isEmpty() ? null : dnsQueryRawRecords.getLast();
            DNSQueryLogRecord newRecord = new DNSQueryLogRecord(rr.QName, rr.AName, rr.CName, rr.HInfo, rr.Rcode, "", rr.Resource, -1000);

            if (!newRecord.equals(lastRecord)) {
                dnsQueryRawRecords.add(newRecord);

                if (dnsQueryRawRecords.size() > linesInDNSQueryRawRecords) {
                    dnsQueryRawRecords.removeFirst();
                }
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "ServiseVPN dnsResolved exception " + e.getMessage() + " " + e.getCause());
        } finally {
            if (rrLock.isWriteLockedByCurrentThread()) {
                rrLock.writeLock().unlock();
            }
        }
        //Log.i(LOG_TAG, "VPN DNS resolved " + rr.toString());
    }

    // Called from native code
    public boolean isDomainBlocked(String name) {
        //Log.i(LOG_TAG, " Ask domain is blocked " + name);
        return false;
    }

    // Called from native code
    public boolean isRedirectToTor(int uid, String destAddress, int destPort) {

        if (uid == ownUID || destAddress.equals(itpdRedirectAddress) || destAddress.equals("127.0.0.1")
                || fixTTL || (compatibilityMode && uid == ApplicationData.SPECIAL_UID_KERNEL)) {
            return false;
        }

        if (!destAddress.isEmpty() && Util.isIpInSubnet(destAddress, torVirtualAddressNetwork)) {
            return true;
        }

        if (lan) {
            for (String address : Util.nonTorList) {
                if (Util.isIpInSubnet(destAddress, address)) {
                    return false;
                }
            }
        }

        if (routeAllThroughTor && ipsForTor.contains(destAddress)) {
            return false;
        } else if (ipsForTor.contains(destAddress)) {
            return true;
        }

        if (uid == 1000 && destPort == ApplicationData.SPECIAL_PORT_NTP) {
            return !(uidSpecialAllowed.contains(ApplicationData.SPECIAL_UID_NTP) || mapUidAllowed.containsKey(1000));
        }

        List<Rule> listRule = ServiceVPNHandler.getAppsList();

        if (listRule != null) {
            for (Rule rule : listRule) {
                if (rule.uid == uid) {
                    return rule.apply;
                }
            }
        }

        return routeAllThroughTor;
    }

    // Called from native code
    public boolean isRedirectToProxy(int uid, String destAddress, int destPort) {
        //Log.i(LOG_TAG, "Redirect to proxy " + uid + " " + destAddress + " " + redirect);
        if (uid == ownUID || destAddress.equals(itpdRedirectAddress) || destAddress.equals("127.0.0.1")
                || (fixTTL && !useProxy) || (compatibilityMode && uid == ApplicationData.SPECIAL_UID_KERNEL)) {
            return false;
        }

        if (lan) {
            for (String address : Util.nonTorList) {
                if (Util.isIpInSubnet(destAddress, address)) {
                    return false;
                }
            }
        }

        if (uid == 1000 && destPort == ApplicationData.SPECIAL_PORT_NTP) {
            return !(uidSpecialAllowed.contains(ApplicationData.SPECIAL_UID_NTP) || mapUidAllowed.containsKey(1000));
        }

        return !setBypassProxy.contains(String.valueOf(uid));
    }

    private boolean isIpInLanRange(String destAddress) {
        for (String address : Util.nonTorList) {
            if (Util.isIpInSubnet(destAddress, address)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDestinationInSpecialRange(int uid, int destPort) {
        return uid == 0 && destPort == 53
                || uid == ApplicationData.SPECIAL_UID_KERNEL
                || destPort == ApplicationData.SPECIAL_PORT_NTP
                || destPort == ApplicationData.SPECIAL_PORT_AGPS1
                || destPort == ApplicationData.SPECIAL_PORT_AGPS2;
    }

    private boolean isSpecialAllowed(int uid, int destPort) {
        if (uid == 0 && destPort == 53) {
            return true;
        } else if (uid == ApplicationData.SPECIAL_UID_KERNEL) {
            return uidSpecialAllowed.contains(ApplicationData.SPECIAL_UID_KERNEL);
        } else if (uid == 1000 && destPort == ApplicationData.SPECIAL_PORT_NTP) {
            return uidSpecialAllowed.contains(ApplicationData.SPECIAL_UID_NTP) || mapUidAllowed.containsKey(1000);
        } else if (destPort == ApplicationData.SPECIAL_PORT_AGPS1 || destPort == ApplicationData.SPECIAL_PORT_AGPS2) {
            return uidSpecialAllowed.contains(ApplicationData.SPECIAL_UID_AGPS);
        }
        return false;
    }

    // Called from native code
    @TargetApi(Build.VERSION_CODES.Q)
    public int getUidQ(int version, int protocol, String saddr, int sport, String daddr, int dport) {
        if (protocol != 6 /* TCP */ && protocol != 17 /* UDP */)
            return Process.INVALID_UID;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null)
            return Process.INVALID_UID;

        InetSocketAddress local = new InetSocketAddress(saddr, sport);
        InetSocketAddress remote = new InetSocketAddress(daddr, dport);

        //Log.i(LOG_TAG, "VPN Get uid local=" + local + " remote=" + remote);
        //Log.i(LOG_TAG, "VPN Get uid=" + uid);
        return cm.getConnectionOwnerUid(protocol, local, remote);
    }

    // Called from native code
    public boolean protectSocket(int socket) {
        return protect(socket);
    }

    private boolean isSupported(int protocol) {
        return (protocol == 1 /* ICMPv4 */ ||
                protocol == 58 /* ICMPv6 */ ||
                protocol == 6 /* TCP */ ||
                protocol == 17 /* UDP */);
    }

    // Called from native code
    public Allowed isAddressAllowed(Packet packet) {

        boolean torIsRunning = modulesStatus.getTorState() == RUNNING;

        boolean fixTTLForPacket = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot()
                && (packet.saddr.matches("^192\\.168\\.(42|43)\\.\\d+")
                || Tethering.ethernetOn && packet.saddr.contains(Tethering.addressLocalPC));

        if (packet.uid != ownUID) {
            addUIDtoDNSQueryRawRecords(packet.uid, packet.daddr, packet.dport, packet.saddr);
        }

        lock.readLock().lock();

        boolean redirectToTor = isRedirectToTor(packet.uid, packet.daddr, packet.dport);
        boolean redirectToProxy = isRedirectToProxy(packet.uid, packet.daddr, packet.dport);

        packet.allowed = false;
        // https://android.googlesource.com/platform/system/core/+/master/include/private/android_filesystem_config.h
        if ((!canFilter) && isSupported(packet.protocol)) {
            packet.allowed = true;
        } else if ((packet.uid == ownUID || compatibilityMode && packet.uid == ApplicationData.SPECIAL_UID_KERNEL && !fixTTLForPacket)
                && isSupported(packet.protocol)) {
            // Allow self
            packet.allowed = true;

            if (!compatibilityMode) {
                Log.w(LOG_TAG, "Allowing self " + packet);
            }
        } else if (arpSpoofingDetection && blockInternetWhenArpAttackDetected
                && (ArpScanner.INSTANCE.getArpAttackDetected() || ArpScanner.INSTANCE.getDhcpGatewayAttackDetected())) {
            // MITM attack detected
            Log.w(LOG_TAG, "Block due to mitm attack " + packet);
        } else if (reloading) {
            // Reload service
            Log.i(LOG_TAG, "Block due to reloading " + packet);
        } else if ((blockIPv6 || fixTTLForPacket
                || packet.dport == 53
                || (torIsRunning && redirectToTor)
                || (useProxy && redirectToProxy))
                && (packet.saddr.contains(":") || packet.daddr.contains(":"))) {
            Log.i(LOG_TAG, "Block ipv6 " + packet);
        } else if (blockHttp && packet.dport == 80
                && !Util.isIpInSubnet(packet.daddr, torVirtualAddressNetwork)
                && !packet.daddr.equals(itpdRedirectAddress)) {
            Log.w(LOG_TAG, "Block http " + packet);
        } /*else if (packet.uid < 2000 &&
                !last_connected && !last_connected_override && isSupported(packet.protocol)) {
            // Allow system applications in disconnected state
            packet.allowed = true;
            Log.w(LOG_TAG, "Allowing disconnected system " + packet);
        }*/ else if (packet.uid <= 2000 &&
                (!routeAllThroughTor || torTethering || fixTTLForPacket || compatibilityMode) &&
                !mapUidKnown.containsKey(packet.uid)
                && (fixTTL || !torIsRunning && !useProxy || packet.protocol == 6 && packet.dport == 53)
                && isSupported(packet.protocol)) {

            // Allow unknown system traffic
            packet.allowed = true;
            if (!fixTTLForPacket && !compatibilityMode) {
                Log.w(LOG_TAG, "Allowing unknown system " + packet);
            }
        } else if (torIsRunning && packet.protocol != 6 && packet.dport != 53 && redirectToTor) {
            Log.w(LOG_TAG, "Disallowing non tcp traffic to Tor " + packet);
        } else if (useProxy && packet.protocol != 6 && packet.dport != 53 && redirectToProxy) {
            Log.w(LOG_TAG, "Disallowing non tcp traffic to proxy " + packet);
        } else if (firewallEnabled && isIpInLanRange(packet.daddr) && isSupported(packet.protocol)) {
            packet.allowed = uidLanAllowed.contains(packet.uid);
        } else if (firewallEnabled && isDestinationInSpecialRange(packet.uid, packet.dport) && isSupported(packet.protocol)) {
            packet.allowed = isSpecialAllowed(packet.uid, packet.dport);
        } else {

            if (mapUidAllowed.containsKey(packet.uid)) {
                Boolean allow = mapUidAllowed.get(packet.uid);
                if (allow != null && isSupported(packet.protocol)) {
                    packet.allowed = allow;
                    //Log.i(LOG_TAG, "Packet " + packet.toString() + " is allowed " + allow);
                }
            } else {
                Log.w(LOG_TAG, "UID is not allowed or no rules for " + packet);
            }
        }

        Allowed allowed = null;
        if (packet.allowed) {
            if (packet.uid == ownUID
                    || compatibilityMode && packet.uid == ApplicationData.SPECIAL_UID_KERNEL && !fixTTLForPacket
                    || packet.dport != 53 && !packet.daddr.equals(itpdRedirectAddress)) {
                allowed = new Allowed();
            } else if (mapForwardPort.containsKey(packet.dport)) {
                Forward fwd = mapForwardPort.get(packet.dport);
                if (fwd != null) {
                    if (fwd.ruid == packet.uid) {
                        allowed = new Allowed();
                    } else {
                        allowed = new Allowed(fwd.raddr, fwd.rport);
                        packet.data = "> " + fwd.raddr + "/" + fwd.rport;
                    }
                }
            } else if (mapForwardAddress.containsKey(packet.daddr)) {
                Forward fwd = mapForwardAddress.get(packet.daddr);
                if (fwd != null) {
                    if (fwd.ruid == packet.uid) {
                        allowed = new Allowed();
                    } else {
                        allowed = new Allowed(fwd.raddr, fwd.rport);
                        packet.data = "> " + fwd.raddr + "/" + fwd.rport;
                    }
                }
            } else {
                allowed = new Allowed();
            }
        }

        lock.readLock().unlock();

        return allowed;
    }

    // Called from native code
    public void accountUsage(Usage usage) {
        //Log.i(LOG_TAG, usage.toString());
    }

    private final BroadcastReceiver idleStateReceiver = new BroadcastReceiver() {
        @Override
        @TargetApi(Build.VERSION_CODES.M)
        public void onReceive(Context context, Intent intent) {
            Log.i(LOG_TAG, "VPN Received " + intent);

            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                Log.i(LOG_TAG, "VPN device idle=" + pm.isDeviceIdleMode());
            }

            // Reload rules when coming from idle mode
            if (pm != null && !pm.isDeviceIdleMode())
                reload("VPN idle state changed", ServiceVPN.this);
        }
    };

    private final BroadcastReceiver connectivityChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Filter VPN connectivity changes
            int networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, ConnectivityManager.TYPE_DUMMY);
            if (networkType == ConnectivityManager.TYPE_VPN)
                return;

            // Reload rules
            Log.i(LOG_TAG, "VPN Received " + intent);
            reload("Connectivity changed", ServiceVPN.this);
        }
    };

    private final BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(LOG_TAG, "VPN Received " + intent);

            try {
                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                    reload("VPN Package added", context);
                } else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                    reload("VPN Package deleted", context);
                }
            } catch (Throwable ex) {
                Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void listenNetworkChanges() {
        // Listen for network changes
        Log.i(LOG_TAG, "VPN Starting listening to network changes");
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }

        ConnectivityManager.NetworkCallback nc = new ConnectivityManager.NetworkCallback() {
            private Boolean last_connected = null;
            private List<InetAddress> last_dns = null;
            private int last_network = 0;

            @Override
            public void onAvailable(@NonNull Network network) {
                Log.i(LOG_TAG, "VPN Available network=" + network);
                last_connected = Util.isConnected(ServiceVPN.this);

                if (!last_connected) {
                    last_connected = true;
                    last_connected_override = true;
                }

                reload("Network available", ServiceVPN.this);

                last_network = network.hashCode();
            }

            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, LinkProperties linkProperties) {
                // Make sure the right DNS servers are being used
                List<InetAddress> dns = linkProperties.getDnsServers();
                SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ServiceVPN.this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? !same(last_dns, dns)
                        : prefs.getBoolean("swRefreshRules", false)) {
                    Log.i(LOG_TAG, "VPN Changed link properties=" + linkProperties +
                            "DNS cur=" + TextUtils.join(",", dns) +
                            "DNS prv=" + (last_dns == null ? null : TextUtils.join(",", last_dns)));
                    last_dns = dns;
                    Log.i(LOG_TAG, "VPN Changed link properties=" + linkProperties);

                    if (network.hashCode() != last_network) {
                        last_network = network.hashCode();
                        reload("VPN Link properties changed", ServiceVPN.this);
                    }
                }
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                boolean connected = Util.isConnected(ServiceVPN.this);
                if (connected && (last_connected == null || !last_connected)) {
                    last_connected = true;
                    reload("VPN Connected state changed", ServiceVPN.this);
                }

                last_network = network.hashCode();

                Log.i(LOG_TAG, "VPN Changed capabilities=" + network);
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.i(LOG_TAG, "VPN Lost network=" + network);
                last_connected = Util.isConnected(ServiceVPN.this);

                if (last_connected_override) {
                    last_connected_override = false;
                }

                reload("Network lost", ServiceVPN.this);

                last_network = 0;
            }

            boolean same(List<InetAddress> last, List<InetAddress> current) {
                if (last == null || current == null)
                    return false;
                if (last.size() != current.size())
                    return false;

                for (int i = 0; i < current.size(); i++)
                    if (!last.get(i).equals(current.get(i)))
                        return false;

                return true;
            }
        };
        if (cm != null) {
            cm.registerNetworkCallback(builder.build(), nc);
            networkCallback = nc;
        }
    }

    private void listenConnectivityChanges() {
        // Listen for connectivity updates
        Log.i(LOG_TAG, "VPN Starting listening to connectivity changes");
        IntentFilter ifConnectivity = new IntentFilter();
        ifConnectivity.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityChangedReceiver, ifConnectivity);
        registeredConnectivityChanged = true;
    }

    @Override
    public void onCreate() {
        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        Log.i(LOG_TAG, "VPN Create version=" + Util.getSelfVersionName(this) + "/" + Util.getSelfVersionCode(this));

        Util.canFilterAsynchronous(this);

        if (jni_context != 0) {
            Log.w(LOG_TAG, "VPN Create with context=" + jni_context);
            jni_stop(jni_context);
            synchronized (jni_lock) {
                jni_done(jni_context);
                jni_context = 0;
            }
        }

        // Native init
        jni_context = jni_init(Build.VERSION.SDK_INT);
        Log.i(LOG_TAG, "VPN Created context=" + jni_context);

        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //ServiceVPNNotification notification = new ServiceVPNNotification(this, notificationManager);

            String title = getString(R.string.app_name);
            String message = getString(R.string.notification_text);
            if (!UsageStatisticKt.getSavedTitle().isEmpty() && !UsageStatisticKt.getSavedMessage().isEmpty()) {
                title = UsageStatisticKt.getSavedTitle();
                message = UsageStatisticKt.getSavedMessage();
            }

            ServiceNotification notification = new ServiceNotification(this, notificationManager, UsageStatisticKt.getStartTime());
            notification.sendNotification(title, message);
        }

        HandlerThread commandThread = new HandlerThread(getString(R.string.app_name) + " command", Process.THREAD_PRIORITY_FOREGROUND);
        commandThread.start();

        commandLooper = commandThread.getLooper();

        commandHandler = ServiceVPNHandler.getInstance(commandLooper, this);

        // Listen for idle mode state changes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IntentFilter ifIdle = new IntentFilter();
            ifIdle.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
            registerReceiver(idleStateReceiver, ifIdle);
            registeredIdleState = true;
        }

        // Listen for added/removed applications
        IntentFilter ifPackage = new IntentFilter();
        ifPackage.addAction(Intent.ACTION_PACKAGE_ADDED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_REMOVED);
        ifPackage.addDataScheme("package");
        registerReceiver(packageChangedReceiver, ifPackage);
        registeredPackageChanged = true;

        firewallNotificationReceiver = FirewallNotification.Companion.registerFirewallReceiver(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                listenNetworkChanges();
            } catch (Throwable ex) {
                Log.w(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                listenConnectivityChanges();
            }
        } else {
            listenConnectivityChanges();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        boolean vpnEnabled = prefs.getBoolean("VPNServiceEnabled", false);

        modulesStatus = ModulesStatus.getInstance();

        if (intent != null && Objects.equals(intent.getAction(), actionStopServiceForeground)) {

            try {
                notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                stopForeground(true);
            } catch (Exception e) {
                Log.e(LOG_TAG, "VPNService stop Service foreground1 exception " + e.getMessage() + " " + e.getCause());
            }
        }

        boolean showNotification;
        if (intent != null) {
            showNotification = intent.getBooleanExtra("showNotification", true);
        } else {
            showNotification = Utils.INSTANCE.isShowNotification(this);
        }

        if (showNotification) {
            //ServiceVPNNotification notification = new ServiceVPNNotification(this, notificationManager);

            String title = getString(R.string.app_name);
            String message = getString(R.string.notification_text);
            if (!UsageStatisticKt.getSavedTitle().isEmpty() && !UsageStatisticKt.getSavedMessage().isEmpty()) {
                title = UsageStatisticKt.getSavedTitle();
                message = UsageStatisticKt.getSavedMessage();
            }

            ServiceNotification notification = new ServiceNotification(this, notificationManager, UsageStatisticKt.getStartTime());
            notification.sendNotification(title, message);
        }

        Log.i(LOG_TAG, "VPN Received " + intent);

        if (intent != null && Objects.equals(intent.getAction(), actionStopServiceForeground)) {

            try {
                notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                stopForeground(true);
            } catch (Exception e) {
                Log.e(LOG_TAG, "VPNService stop Service foreground2 exception " + e.getMessage() + " " + e.getCause());
            }

            stopSelf(startId);

            return START_NOT_STICKY;
        }

        // Handle service restart
        if (intent == null) {
            Log.i(LOG_TAG, "VPN OnStart Restart");

            if (vpnEnabled) {
                Intent starterIntent = new Intent(this, BootCompleteReceiver.class);
                starterIntent.setAction(BootCompleteReceiver.ALWAYS_ON_VPN);
                sendBroadcast(starterIntent);
                stopSelf(startId);
                return START_NOT_STICKY;
            } else {
                // Recreate intent
                intent = new Intent(this, ServiceVPN.class);
                intent.putExtra(EXTRA_COMMAND, VPNCommand.STOP);
            }
        }

        VPNCommand cmd = (VPNCommand) intent.getSerializableExtra(EXTRA_COMMAND);

        if (cmd == null) {
            Log.i(LOG_TAG, "VPN OnStart ALWAYS_ON_VPN");

            if (vpnEnabled) {
                Intent starterIntent = new Intent(this, BootCompleteReceiver.class);
                starterIntent.setAction(BootCompleteReceiver.ALWAYS_ON_VPN);
                sendBroadcast(starterIntent);
                stopSelf(startId);
                return START_NOT_STICKY;
            } else {
                intent.putExtra(EXTRA_COMMAND, VPNCommand.STOP);
            }
        }

        String reason = intent.getStringExtra(EXTRA_REASON);
        Log.i(LOG_TAG, "VPN Start intent=" + intent + " command=" + cmd + " reason=" + reason +
                " vpn=" + (vpn != null) + " user=" + (Process.myUid() / 100000));

        commandHandler.queue(intent);

        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        Log.i(LOG_TAG, "VPN Revoke");

        // Disable firewall (will result in stop command)
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("VPNServiceEnabled", false).apply();

        ModulesAux.stopModulesIfRunning(this.getApplicationContext());

        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        //synchronized (this) {

        Log.i(LOG_TAG, "VPN Destroy");
        commandLooper.quit();

        for (VPNCommand command : VPNCommand.values())
            commandHandler.removeMessages(command.ordinal());

        if (registeredIdleState) {
            unregisterReceiver(idleStateReceiver);
            registeredIdleState = false;
        }

        if (registeredPackageChanged) {
            unregisterReceiver(packageChangedReceiver);
            registeredPackageChanged = false;

            FirewallNotification.Companion.unregisterFirewallReceiver(this, firewallNotificationReceiver);
        }

        if (networkCallback != null) {
            unlistenNetworkChanges();
            networkCallback = null;
        }
        if (registeredConnectivityChanged) {
            unregisterReceiver(connectivityChangedReceiver);
            registeredConnectivityChanged = false;
        }

        try {
            if (vpn != null) {
                stopNative();
                commandHandler.stopVPN(vpn);
                vpn = null;
                unPrepare();
            }
        } catch (Throwable ex) {
            Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }

        Log.i(LOG_TAG, "VPN Destroy context=" + jni_context);
        synchronized (jni_lock) {
            jni_done(jni_context);
            jni_context = 0;
        }
        //}

        super.onDestroy();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unlistenNetworkChanges() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            cm.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) networkCallback);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(LOG_TAG, "ServiceVPN onBind");

        String action = null;
        if (intent != null) {
            action = intent.getAction();
        }

        if (VpnService.SERVICE_INTERFACE.equals(action)) {
            return super.onBind(intent);
        }

        return binder;
    }

    public class VPNBinder extends Binder {
        public ServiceVPN getService() {
            return ServiceVPN.this;
        }
    }

    public LinkedList<DNSQueryLogRecord> getDnsQueryRawRecords() {
        return dnsQueryRawRecords;
    }

    public void clearDnsQueryRawRecords() {
        try {
            rrLock.writeLock().lockInterruptibly();

            if (!dnsQueryRawRecords.isEmpty()) {
                dnsQueryRawRecords.clear();
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "ServiseVPN clearDnsQueryRawRecords exception " + e.getMessage() + " " + e.getCause());
        } finally {
            if (rrLock.isWriteLockedByCurrentThread()) {
                rrLock.writeLock().unlock();
            }
        }
    }

    public void lockDnsQueryRawRecordsListForRead(boolean lock) {
        try {
            if (lock) {
                rrLock.readLock().lockInterruptibly();
            } else {
                rrLock.readLock().unlock();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "ServiseVPN lockDnsQueryRawRecordsListForRead exception " + e.getMessage() + " " + e.getCause());
        }
    }

    private void addUIDtoDNSQueryRawRecords(int uid, String destinationAddress, int destinationPort, String sourceAddres) {

        try {

            rrLock.writeLock().lockInterruptibly();

            if (uid != 0 || destinationPort != 53) {
                DNSQueryLogRecord lastRecord = dnsQueryRawRecords.isEmpty() ? null : dnsQueryRawRecords.getLast();
                DNSQueryLogRecord newRecord = new DNSQueryLogRecord("", "", "", "", 0, sourceAddres, destinationAddress, uid);

                if (!newRecord.equals(lastRecord)) {
                    dnsQueryRawRecords.add(newRecord);

                    if (dnsQueryRawRecords.size() > linesInDNSQueryRawRecords) {
                        dnsQueryRawRecords.removeFirst();
                    }
                }
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "ServiseVPN addUIDtoDNSQueryRawRecords exception " + e.getMessage() + " " + e.getCause());
        } finally {
            if (rrLock.isWriteLockedByCurrentThread()) {
                rrLock.writeLock().unlock();
            }
        }

    }


}
