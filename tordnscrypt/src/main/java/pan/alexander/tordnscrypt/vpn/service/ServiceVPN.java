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
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
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

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper.reload;

@SuppressWarnings("unused")
public class ServiceVPN extends VpnService {
    static {
        try {
            System.loadLibrary("invizible");
        } catch (UnsatisfiedLinkError ignored) {
            System.exit(1);
        }
    }

    static final String EXTRA_COMMAND = "Command";
    static final String EXTRA_REASON = "Reason";

    private static NotificationManager notificationManager;
    private static final Object jni_lock = new Object();
    private static long jni_context = 0;

    boolean last_connected = false;

    ParcelFileDescriptor vpn = null;

    private boolean registeredIdleState = false;
    private boolean registeredPackageChanged = false;
    private boolean registeredConnectivityChanged = false;

    private PathVars pathVars;
    private static int ownUID = Process.myUid();

    private Object networkCallback = null;
    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    private volatile Looper commandLooper;
    private volatile ServiceVPNHandler commandHandler;

    private Thread tunnelThread = null;

    private ModulesStatus modulesStatus;

    private boolean canFilter = true;

    private boolean filterUDP = true;
    private boolean blockHttp = false;
    private boolean routeAllThroughInviZible = true;

    @SuppressLint("UseSparseArrays")
    private Map<Integer, Boolean> mapUidAllowed = new HashMap<>();
    @SuppressLint("UseSparseArrays")
    private Map<Integer, Integer> mapUidKnown = new HashMap<>();
    @SuppressLint("UseSparseArrays")
    private Map<Integer, Forward> mapForwardPort = new HashMap<>();
    private Map<String, Forward> mapForwardAddress = new HashMap<>();

    private VPNBinder binder = new VPNBinder();
    private LinkedList<ResourceRecord> resourceRecords = new LinkedList<>();

    private native long jni_init(int sdk);

    private native void jni_start(long context, int loglevel);

    private native void jni_run(long context, int tun, boolean fwd53, int rcode);

    private native void jni_stop(long context);

    private native void jni_clear(long context);

    private native int jni_get_mtu();

    private native void jni_socks5(String addr, int port, String username, String password);

    private native void jni_done(long context);

    private static List<InetAddress> getDns(Context context) {
        List<InetAddress> listDns = new ArrayList<>();
        List<String> sysDns = Util.getDefaultDNS(context);

        // Get custom DNS servers
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean ip6 = prefs.getBoolean("ipv6", false);
        String vpnDns1 = prefs.getString("dns", "127.0.0.1");
        String vpnDns2 = prefs.getString("dns2", "127.0.0.1");
        Log.i(LOG_TAG, "VPN DNS system=" + TextUtils.join(",", sysDns) + " config=" + vpnDns1 + "," + vpnDns2);

        if (vpnDns1 != null)
            try {
                InetAddress dns = InetAddress.getByName(vpnDns1);
                if (!(dns.isLoopbackAddress() || dns.isAnyLocalAddress()) &&
                        (ip6 || dns instanceof Inet4Address))
                    listDns.add(dns);
            } catch (Throwable ignored) {
            }

        if (vpnDns2 != null)
            try {
                InetAddress dns = InetAddress.getByName(vpnDns2);
                if (!(dns.isLoopbackAddress() || dns.isAnyLocalAddress()) &&
                        (ip6 || dns instanceof Inet4Address))
                    listDns.add(dns);
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
                        (ip6 || ddns instanceof Inet4Address))
                    listDns.add(ddns);
            } catch (Throwable ex) {
                Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }

        // Remove local DNS servers when not routing LAN
        int count = listDns.size();
        boolean lan = prefs.getBoolean("lan", false);

        // Always set DNS servers
        if (listDns.size() == 0 || listDns.size() < count)
            try {
                listDns.add(InetAddress.getByName("8.8.8.8"));
                listDns.add(InetAddress.getByName("8.8.4.4"));
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

    BuilderVPN getBuilder(List<Rule> listAllowed, List<Rule> listRule) {
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        boolean ip6 = prefs.getBoolean("ipv6", false);
        boolean subnet = prefs.getBoolean("VPN subnet", true);
        boolean tethering = prefs.getBoolean("VPN tethering", true);
        boolean lan = prefs.getBoolean("VPN lan", false);
        boolean apIsOn = new PrefManager(this).getBoolPref("APisON");
        boolean torIsRunning = modulesStatus.getTorState() == RUNNING;

        // Build VPN service
        BuilderVPN builder = new BuilderVPN(this);
        builder.setSession(getString(R.string.app_name));

        // VPN address
        String vpn4 = prefs.getString("vpn4", "10.1.10.1");
        Log.i(LOG_TAG, "VPN Using VPN4=" + vpn4);
        builder.addAddress(vpn4, 32);
        if (ip6) {
            String vpn6 = prefs.getString("vpn6", "fd00:1:fd00:1:fd00:1:fd00:1");
            Log.i(LOG_TAG, "VPN Using VPN6=" + vpn6);
            builder.addAddress(vpn6, 128);
        }

        // DNS address
        for (InetAddress dns : getDns(this)) {
            if (ip6 || dns instanceof Inet4Address) {
                Log.i(LOG_TAG, "VPN Using DNS=" + dns);
                builder.addDnsServer(dns);
            }
        }

        // Subnet routing
        if (subnet) {
            // Exclude IP ranges
            List<IPUtil.CIDR> listExclude = new ArrayList<>();
            listExclude.add(new IPUtil.CIDR("127.0.0.0", 8)); // localhost

            if (tethering && !lan && !torIsRunning && apIsOn) {
                // USB tethering 192.168.42.x
                // Wi-Fi tethering 192.168.43.x
                listExclude.add(new IPUtil.CIDR("192.168.42.0", 23));
                // Bluetooth tethering 192.168.44.x
                listExclude.add(new IPUtil.CIDR("192.168.44.0", 24));
                // Wi-Fi direct 192.168.49.x
                listExclude.add(new IPUtil.CIDR("192.168.49.0", 24));
            }

            if (lan) {
                // https://tools.ietf.org/html/rfc1918
                listExclude.add(new IPUtil.CIDR("10.0.0.0", 8));
                listExclude.add(new IPUtil.CIDR("172.16.0.0", 12));
                listExclude.add(new IPUtil.CIDR("192.168.0.0", 16));
            }

            // Broadcast
            listExclude.add(new IPUtil.CIDR("224.0.0.0", 3));

            Collections.sort(listExclude);

            try {
                InetAddress start = InetAddress.getByName("0.0.0.0");
                for (IPUtil.CIDR exclude : listExclude) {
                    Log.i(LOG_TAG, "Exclude " + exclude.getStart().getHostAddress() + "..." + exclude.getEnd().getHostAddress());
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
        } else
            builder.addRoute("0.0.0.0", 0);

        Log.i(LOG_TAG, "VPN IPv6=" + ip6);
        if (ip6)
            builder.addRoute("2000::", 3); // unicast

        // MTU
        int mtu = jni_get_mtu();
        Log.i(LOG_TAG, "VPN MTU=" + mtu);
        builder.setMtu(mtu);

        // Add list of allowed applications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if (routeAllThroughInviZible) {
                try {
                    builder.addDisallowedApplication(getPackageName());
                } catch (PackageManager.NameNotFoundException ex) {
                    Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }

                for (Rule rule : listRule) {
                    if (!rule.apply) {
                        try {
                            Log.i(LOG_TAG, "VPN Not routing " + rule.packageName);
                            builder.addDisallowedApplication(rule.packageName);
                        } catch (PackageManager.NameNotFoundException ex) {
                            Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    }
                }
            } else {

                boolean applied = false;

                for (Rule rule : listRule) {
                    if (rule.apply) {
                        try {
                            if (rule.uid != ownUID) {
                                Log.i(LOG_TAG, "VPN routing " + rule.packageName);
                                applied = true;
                                builder.addAllowedApplication(rule.packageName);
                            }
                        } catch (PackageManager.NameNotFoundException ex) {
                            Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    }
                }

                if (!applied) {
                    try {
                        builder.addDisallowedApplication(getPackageName());
                    } catch (PackageManager.NameNotFoundException ex) {
                        Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    }

                    for (Rule rule : listRule) {
                        try {
                            Log.i(LOG_TAG, "VPN Not routing " + rule.packageName);
                            builder.addDisallowedApplication(rule.packageName);
                        } catch (PackageManager.NameNotFoundException ex) {
                            Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
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

    void startNative(final ParcelFileDescriptor vpn, List<Rule> listAllowed, List<Rule> listRule) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Prepare rules
        prepareUidAllowed(listAllowed, listRule);
        prepareForwarding();

        int prio = Integer.parseInt(prefs.getString("loglevel", Integer.toString(Log.WARN)));
        final int rcode = Integer.parseInt(prefs.getString("rcode", "3"));

        int torSOCKSPort = 9050;

        try {
            torSOCKSPort = Integer.parseInt(pathVars.torSOCKSPort);
        } catch (Exception e) {
            Log.e(LOG_TAG, "VPN SOCKS Parse Exception " + e.getMessage() + " " + e.getCause());
        }

        if (modulesStatus.getTorState() == RUNNING) {
            jni_socks5("127.0.0.1", torSOCKSPort, "", "");
        } else {
            jni_socks5("", 0, "", "");
        }

        if (tunnelThread == null) {
            Log.i(LOG_TAG, "VPN Starting tunnel thread context=" + jni_context);
            jni_start(jni_context, prio);

            tunnelThread = new Thread(() -> {
                Log.i(LOG_TAG, "VPN Running tunnel context=" + jni_context);
                jni_run(jni_context, vpn.getFd(), mapForwardPort.containsKey(53), rcode);
                Log.i(LOG_TAG, "VPN Tunnel exited");
                tunnelThread = null;
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
                } catch (InterruptedException ignored) {
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
        mapForwardPort.clear();
        mapForwardAddress.clear();
        lock.writeLock().unlock();
    }

    private void prepareUidAllowed(List<Rule> listAllowed, List<Rule> listRule) {
        lock.writeLock().lock();

        mapUidAllowed.clear();
        for (Rule rule : listAllowed)
            mapUidAllowed.put(rule.uid, true);

        mapUidKnown.clear();
        for (Rule rule : listRule)
            mapUidKnown.put(rule.uid, rule.uid);

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
            dnsCryptPort = Integer.parseInt(pathVars.dnsCryptPort);
            torDNSPort = Integer.parseInt(pathVars.torDNSPort);
            itpdHttpPort = Integer.parseInt(pathVars.itpdHttpProxyPort);
        } catch (Exception e) {
            Log.e(LOG_TAG, "VPN Redirect Ports Parse Exception " + e.getMessage() + " " + e.getCause());
        }

        if (dnsCryptState == RUNNING) {
            addForwardPortRule(17, 53, "127.0.0.1", dnsCryptPort, Process.myUid());
            addForwardPortRule(6, 53, "127.0.0.1", dnsCryptPort, Process.myUid());

            if (itpdState == RUNNING) {
                addForwardAddressRule(17, "10.191.0.1", "127.0.0.1", itpdHttpPort, Process.myUid());
                addForwardAddressRule(6, "10.191.0.1", "127.0.0.1", itpdHttpPort, Process.myUid());
            }
        } else if (torState == RUNNING) {
            addForwardPortRule(17, 53, "127.0.0.1", torDNSPort, Process.myUid());
            addForwardPortRule(6, 53, "127.0.0.1", torDNSPort, Process.myUid());
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
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
        if (!resourceRecords.isEmpty() && !rr.equals(resourceRecords.get(resourceRecords.size() - 1))) {

            if (resourceRecords.size() > 200) {
                resourceRecords.removeFirst();
            }

            resourceRecords.add(rr);
        } else if (resourceRecords.isEmpty()) {
            resourceRecords.add(rr);
        }
        //Log.i(LOG_TAG, "VPN DNS resolved " + rr.toString());
    }

    // Called from native code
    public boolean isDomainBlocked(String name) {
        //Log.i(LOG_TAG, " Ask domain is blocked " + name);
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

        Log.i(LOG_TAG, "VPN Get uid local=" + local + " remote=" + remote);
        int uid = cm.getConnectionOwnerUid(protocol, local, remote);
        Log.i(LOG_TAG, "VPN Get uid=" + uid);
        return uid;
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

        lock.readLock().lock();

        packet.allowed = false;
        // https://android.googlesource.com/platform/system/core/+/master/include/private/android_filesystem_config.h
        if (!canFilter) {
            packet.allowed = true;
        } else if (packet.uid == ownUID) {
            // Allow self
            packet.allowed = true;
            Log.w(LOG_TAG, "Allowing self " + packet);
        } else if (blockHttp && packet.dport == 80 && !packet.daddr.matches("^10\\..+")) {
            Log.w(LOG_TAG, "Block http " + packet);
        } else if (packet.protocol == 17 /* UDP */ && !filterUDP) {
            // Allow unfiltered UDP
            packet.allowed = true;
            //Log.i(LOG_TAG, "Allowing UDP " + packet);
        } else if (packet.uid < 2000 &&
                !last_connected && isSupported(packet.protocol)) {
            // Allow system applications in disconnected state
            packet.allowed = true;
            Log.w(LOG_TAG, "Allowing disconnected system " + packet);
        } else if (packet.uid <= 2000 &&
                !routeAllThroughInviZible &&
                !mapUidKnown.containsKey(packet.uid)
                && isSupported(packet.protocol)) {
            // Allow unknown system traffic
            packet.allowed = true;
            Log.w(LOG_TAG, "Allowing unknown system " + packet);
        } else if (routeAllThroughInviZible && torIsRunning
                && packet.protocol != 6 && packet.dport != 53) {
            Log.w(LOG_TAG, "Disallowing non tcp traffic when Tor is running " + packet);
        } else {

            if (mapUidAllowed != null && mapUidAllowed.containsKey(packet.uid)) {
                Boolean allow = mapUidAllowed.get(packet.uid);
                if (allow != null) {
                    packet.allowed = allow;
                    //Log.i(LOG_TAG, "Packet " + packet.toString() + " is allowed " + allow);
                }
            } else {
                Log.w(LOG_TAG, "No rules for " + packet);
            }
        }

        Allowed allowed = null;
        if (packet.allowed) {
            if (packet.daddr.equals("127.0.0.1") || packet.uid == ownUID) {
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

    private BroadcastReceiver idleStateReceiver = new BroadcastReceiver() {
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

    private BroadcastReceiver connectivityChangedReceiver = new BroadcastReceiver() {
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

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(LOG_TAG, "VPN Received " + intent);

            try {
                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                    reload("Package added", context);
                } else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                    reload("Package deleted", context);
                }
            } catch (Throwable ex) {
                Log.e(LOG_TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            }
        }
    };

    @Override
    public void onCreate() {
        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        Log.i(LOG_TAG, "Create version=" + Util.getSelfVersionName(this) + "/" + Util.getSelfVersionCode(this));

        canFilter = Util.canFilter(this);

        if (jni_context != 0) {
            Log.w(LOG_TAG, "Create with context=" + jni_context);
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
            private Boolean last_unmetered = null;
            private String last_generation = null;
            private List<InetAddress> last_dns = null;

            @Override
            public void onAvailable(@NonNull Network network) {
                Log.i(LOG_TAG, "VPN Available network=" + network);
                reload("Network available", ServiceVPN.this);
            }

            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, LinkProperties linkProperties) {
                // Make sure the right DNS servers are being used
                List<InetAddress> dns = linkProperties.getDnsServers();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ServiceVPN.this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? !same(last_dns, dns)
                        : prefs.getBoolean("reload_onconnectivity", true)) {
                    Log.i(LOG_TAG, "VPN Changed link properties=" + linkProperties +
                            "DNS cur=" + TextUtils.join(",", dns) +
                            "DNS prv=" + (last_dns == null ? null : TextUtils.join(",", last_dns)));
                    last_dns = dns;
                    Log.i(LOG_TAG, "VPN Changed link properties=" + linkProperties);
                    reload("VPN Link properties changed", ServiceVPN.this);
                }
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                Log.i(LOG_TAG, "VPN Changed capabilities=" + network);
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.i(LOG_TAG, "VPN Lost network=" + network);
                reload("Network lost", ServiceVPN.this);
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
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        filterUDP = prefs.getBoolean("VPN filter_udp", true);
        blockHttp = prefs.getBoolean("pref_fast_block_http", false);
        routeAllThroughInviZible = prefs.getBoolean("pref_fast_all_through_tor", true);

        pathVars = new PathVars(this);
        modulesStatus = ModulesStatus.getInstance();

        if (intent != null) {
            boolean showNotification = intent.getBooleanExtra("showNotification", true);

            if (showNotification) {
                ServiceVPNNotification notification = new ServiceVPNNotification(this, notificationManager);
                notification.sendNotification(getString(R.string.app_name), getText(R.string.notification_text).toString());
            }
        }


        Log.i(LOG_TAG, "VPN Received " + intent);

        // Handle service restart
        if (intent == null) {
            Log.i(LOG_TAG, "VPN Restart");

            // Recreate intent
            intent = new Intent(this, ServiceVPN.class);
            intent.putExtra(EXTRA_COMMAND, VPNCommand.STOP);
        }

        VPNCommand cmd = (VPNCommand) intent.getSerializableExtra(EXTRA_COMMAND);

        if (cmd == null) {
            intent.putExtra(EXTRA_COMMAND, VPNCommand.STOP);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("VPNServiceEnabled", false).apply();

        ModulesAux.stopModulesIfRunning(this);

        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        synchronized (this) {

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
        }

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
    public IBinder onBind(Intent arg0) {
        Log.i(LOG_TAG, "ServiceVPN onBind");
        return binder;
    }

    public class VPNBinder extends Binder {
        public ServiceVPN getService() {
            return ServiceVPN.this;
        }
    }

    public LinkedList<ResourceRecord> getResourceRecords() {
        return resourceRecords;
    }

    public void clearResourceRecords() {
        if (resourceRecords != null && !resourceRecords.isEmpty()) {
            resourceRecords.clear();
        }
    }
}
