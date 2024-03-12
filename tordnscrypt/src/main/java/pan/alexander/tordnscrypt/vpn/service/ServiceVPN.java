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

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.Keep;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.BootCompleteReceiver;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.arp.DNSRebindProtection;
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor;
import pan.alexander.tordnscrypt.domain.connection_checker.OnInternetConnectionCheckedListener;
import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionData;
import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionProtocol;
import pan.alexander.tordnscrypt.domain.connection_records.entities.DnsRecord;
import pan.alexander.tordnscrypt.domain.connection_records.entities.PacketRecord;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ModulesServiceNotificationManager;
import pan.alexander.tordnscrypt.modules.UsageStatistics;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.enums.VPNCommand;
import pan.alexander.tordnscrypt.vpn.Allowed;
import pan.alexander.tordnscrypt.vpn.Packet;
import pan.alexander.tordnscrypt.vpn.ResourceRecord;
import pan.alexander.tordnscrypt.vpn.Rule;
import pan.alexander.tordnscrypt.vpn.Usage;
import pan.alexander.tordnscrypt.vpn.VpnUtils;

import static java.net.IDN.ALLOW_UNASSIGNED;
import static java.net.IDN.toUnicode;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.modules.ModulesReceiver.VPN_REVOKED_EXTRA;
import static pan.alexander.tordnscrypt.modules.ModulesReceiver.VPN_REVOKE_ACTION;
import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;
import static pan.alexander.tordnscrypt.modules.ModulesServiceActions.ACTION_STOP_SERVICE_FOREGROUND;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_PORT_NTP;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_KERNEL;
import static pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.SPECIAL_UID_NTP;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.NETWORK_STACK_DEFAULT_UID;
import static pan.alexander.tordnscrypt.utils.Constants.PLAINTEXT_DNS_PORT;
import static pan.alexander.tordnscrypt.utils.Constants.TOR_VIRTUAL_ADDR_NETWORK_IPV6;
import static pan.alexander.tordnscrypt.utils.bootcomplete.BootCompleteManager.ALWAYS_ON_VPN;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper.reload;
import static pan.alexander.tordnscrypt.vpn.service.VpnBuilder.vpnDnsSet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

public class ServiceVPN extends VpnService implements OnInternetConnectionCheckedListener {
    static {
        try {
            System.loadLibrary("invizible");
        } catch (UnsatisfiedLinkError ignored) {
            System.exit(1);
        }
    }

    public final static int LINES_IN_DNS_QUERY_RAW_RECORDS = 512;

    static final String EXTRA_COMMAND = "Command";
    static final String EXTRA_REASON = "Reason";

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public Lazy<ConnectionCheckerInteractor> connectionCheckerInteractor;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    public Lazy<CachedExecutor> cachedExecutor;
    @Inject
    public Provider<VpnPreferenceHolder> vpnPreferenceHolder;
    volatile VpnPreferenceHolder vpnPreferences;
    @Inject
    public Lazy<VpnRulesHolder> vpnRulesHolder;

    NotificationManager notificationManager;
    private static final Object jni_lock = new Object();
    private static volatile long jni_context = 0;
    private volatile long service_jni_context = 0;

    private volatile boolean savedInternetAvailable = false;

    volatile ParcelFileDescriptor vpn = null;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final ConcurrentHashMap<ConnectionData, Long> connectionDataRecords = new ConcurrentHashMap<>(
            16,
            0.75f,
            2
    );

    private volatile Looper commandLooper;
    private volatile ServiceVPNHandler commandHandler;

    private volatile Thread tunnelThread = null;

    public volatile boolean canFilter = true;

    volatile boolean reloading;

    private final Set<String> dnsRebindHosts = new HashSet<>();

    private final VPNBinder binder = new VPNBinder();

    @Keep
    private native long jni_init(int sdk);

    @Keep
    private native void jni_start(long context, int loglevel);

    @Keep
    private native void jni_run(long context, int tun, boolean fwd53, int rcode, boolean compatibilityMode, boolean canFilterSynchronous);

    @Keep
    private native void jni_stop(long context);

    @Keep
    private native void jni_clear(long context);

    @Keep
    native int jni_get_mtu();

    @Keep
    private native void jni_socks5_for_tor(String addr, int port, String username, String password, int dnsPort);

    @Keep
    private native void jni_socks5_for_proxy(String addr, int port, String username, String password);

    @Keep
    private native void jni_done(long context);

    synchronized void startNative(final ParcelFileDescriptor vpn, List<String> listAllowed) {

        vpnPreferences = vpnPreferenceHolder.get();

        // Prepare rules
        vpnRulesHolder.get().prepareUidAllowed(listAllowed, commandHandler.getAppsList());
        vpnRulesHolder.get().prepareForwarding();

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        if ((modulesStatus.getTorState() == RUNNING
                || modulesStatus.getTorState() == STARTING
                || modulesStatus.getTorState() == RESTARTING)) {
            jni_socks5_for_tor(
                    LOOPBACK_ADDRESS,
                    vpnPreferences.getTorSOCKSPort(),
                    "",
                    "",
                    vpnPreferences.getTorDNSPort()
            );
        } else {
            jni_socks5_for_tor("", 0, "", "", 0);
        }

        if (vpnPreferences.getUseProxy()) {
            jni_socks5_for_proxy(
                    vpnPreferences.getProxyAddress(),
                    vpnPreferences.getProxyPort(),
                    "",
                    ""
            );
        } else {
            jni_socks5_for_proxy("", 0, "", "");
        }

        if (tunnelThread == null) {
            logi("VPN Starting tunnel thread context=" + jni_context);
            jni_start(jni_context, vpnPreferences.getNativeLogLevel());

            tunnelThread = new Thread(() -> {
                try {
                    logi("VPN Running tunnel context=" + jni_context);
                    boolean canFilterSynchronous = true;
                    if (vpnPreferences.getCompatibilityMode() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        canFilterSynchronous = VpnUtils.canFilter();
                    }
                    jni_run(
                            jni_context,
                            vpn.getFd(),
                            vpnRulesHolder.get().mapForwardPort.containsKey(PLAINTEXT_DNS_PORT),
                            vpnPreferences.getDnsBlockedResponseCode(),
                            vpnPreferences.getCompatibilityMode(),
                            canFilterSynchronous
                    );
                    logi("VPN Tunnel exited");
                    tunnelThread = null;
                } catch (Exception e) {
                    handler.get().post(() ->
                            Toast.makeText(
                                    ServiceVPN.this,
                                    e.getMessage() + " " + e.getCause(),
                                    Toast.LENGTH_LONG
                            ).show());
                    loge("ServiceVPN startNative exception", e);
                }

            });

            tunnelThread.setName("VPN tunnel thread");
            tunnelThread.start();

            logi("VPN Started tunnel thread");
        }
    }

    synchronized void stopNative() {
        logi("VPN Stop native");

        if (tunnelThread != null) {
            logi("VPN Stopping tunnel thread");

            jni_stop(jni_context);

            Thread thread = tunnelThread;
            while (thread != null && thread.isAlive()) {
                try {
                    logi("VPN Joining tunnel thread context=" + jni_context);
                    thread.join();
                } catch (InterruptedException e) {
                    logi("VPN Joined tunnel interrupted");
                }
                thread = tunnelThread;
            }
            tunnelThread = null;

            synchronized (jni_lock) {
                jni_clear(jni_context);
            }

            logi("VPN Stopped tunnel thread");
        }
    }

    // Called from native code
    @Keep
    public void nativeExit(String reason) {
        logw("VPN Native exit reason=" + reason);
        if (reason != null) {
            SharedPreferences prefs = defaultPreferences.get();
            prefs.edit().putBoolean(VPN_SERVICE_ENABLED, false).apply();
        }
    }

    // Called from native code
    @Keep
    public void nativeError(int error, String message) {
        loge("VPN Native error " + error + ": " + message);
    }

    // Called from native code
    @Keep
    public void logPacket(Packet packet) {
        //logi("VPN Log packet " + packet.toString());
    }

    // Called from native code
    @Keep
    public void dnsResolved(ResourceRecord rr) {

        try {

            addDnsToConnectionRecords(rr);

            String qname = rr.QName;
            String destAddress = rr.Resource;

            if (vpnPreferences.getDnsRebindProtection() && qname != null && destAddress != null) {
                qname = qname.trim();
                destAddress = destAddress.trim();

                if (!qname.isEmpty() && !destAddress.isEmpty()
                        && !qname.endsWith(".onion")
                        && !qname.endsWith(".i2p")
                        && !dnsRebindHosts.contains(qname)) {
                    if (isIpInDNSRebindRange(destAddress)) {
                        dnsRebindHosts.add(qname);
                        DNSRebindProtection.INSTANCE.sendNotification(this, qname);
                        logw("ServiseVPN DNS rebind attack detected " + rr);
                    } else if ((destAddress.equals(META_ADDRESS) || destAddress.equals(LOOPBACK_ADDRESS))
                            && rr.Rcode == 0 && !rr.HInfo.contains("dnscrypt")) {
                        logw("ServiseVPN DNS rebind attack detected " + rr);
                        dnsRebindHosts.add(qname);
                    }
                }
            }

        } catch (Exception e) {
            loge("ServiseVPN dnsResolved exception", e);
        }
    }

    private void addDnsToConnectionRecords(ResourceRecord rr) {

        if (!vpnPreferences.getConnectionLogsEnabled() || reloading) {
            return;
        }

        DnsRecord dnsRecord = new DnsRecord(
                System.currentTimeMillis(),
                rr.QName != null ? toUnicode(rr.QName.trim().toLowerCase(), ALLOW_UNASSIGNED) : "",
                rr.AName != null ? toUnicode(rr.AName.trim().toLowerCase(), ALLOW_UNASSIGNED) : "",
                rr.CName != null ? toUnicode(rr.CName.trim().toLowerCase(), ALLOW_UNASSIGNED) : "",
                rr.HInfo != null ? rr.HInfo.trim() : "",
                rr.Rcode,
                rr.Resource != null ? rr.Resource.trim() : ""
        );

        //Remove entry to update key time
        Long creationTime = connectionDataRecords.remove(dnsRecord);
        //Use value creation time to keep DNS records order
        connectionDataRecords.put(
                dnsRecord,
                creationTime != null ? creationTime : SystemClock.elapsedRealtimeNanos()
        );


        if (connectionDataRecords.size() >= LINES_IN_DNS_QUERY_RAW_RECORDS) {
            freeSpaceInConnectionRecords();
        }
    }

    private void freeSpaceInConnectionRecords() {
        List<ConnectionData> connectionDataList = getSortedConnectionDataByTime();
        for (int i = 0; i < connectionDataList.size() / 3; i++) {
            connectionDataRecords.remove(connectionDataList.get(i));
        }
    }

    private List<ConnectionData> getSortedConnectionDataByTime() {
        List<ConnectionData> connectionDataList = new ArrayList<>(connectionDataRecords.keySet());
        Collections.sort(connectionDataList, (o1, o2) -> (int) (o1.getTime() - o2.getTime()));
        return connectionDataList;
    }

    // Called from native code
    @Keep
    public boolean isDomainBlocked(String name) {

        if (name == null) {
            return true;
        }

        try {
            if (vpnPreferences.getDnsRebindProtection() && dnsRebindHosts.contains(name)) {
                return true;
            }
        } catch (Exception e) {
            loge("ServiseVPN isDomainBlocked exception", e);
        }

        return false;
    }

    // Called from native code
    @Keep
    public boolean isRedirectToTor(int uid, String destAddress, int destPort) {

        if (destAddress == null) {
            return false;
        }

        if (uid == vpnPreferences.getOwnUID()
                || destAddress.equals(vpnPreferences.getItpdRedirectAddress())
                || destAddress.equals(LOOPBACK_ADDRESS)
                || vpnPreferences.getFixTTL()
                || (vpnPreferences.getCompatibilityMode() && uid == SPECIAL_UID_KERNEL)) {
            return false;
        }

        if (!destAddress.isEmpty()
                && (VpnUtils.isIpInSubnet(destAddress, vpnPreferences.getTorVirtualAddressNetwork())
                || VpnUtils.isIpInSubnet(destAddress, TOR_VIRTUAL_ADDR_NETWORK_IPV6))) {
            return true;
        }

        if (vpnPreferences.getLan() || uid == NETWORK_STACK_DEFAULT_UID) {
            for (String address : VpnUtils.nonTorList) {
                if (VpnUtils.isIpInSubnet(destAddress, address)) {
                    return false;
                }
            }
        }

        if (vpnPreferences.getRouteAllThroughTor()
                && vpnRulesHolder.get().ipsForTor.contains(destAddress)) {
            return false;
        } else if (vpnRulesHolder.get().ipsForTor.contains(destAddress)) {
            return true;
        }

        if (uid == 1000 && destPort == SPECIAL_PORT_NTP) {
            return !(vpnRulesHolder.get().uidSpecialAllowed.contains(SPECIAL_UID_NTP)
                    || vpnRulesHolder.get().mapUidAllowed.containsKey(1000));
        }

        List<Rule> listRule = commandHandler.getAppsList();

        if (listRule != null) {
            for (Rule rule : listRule) {
                if (rule.uid == uid) {
                    return rule.apply;
                }
            }
        }

        return vpnPreferences.getRouteAllThroughTor();
    }

    // Called from native code
    @Keep
    public boolean isRedirectToProxy(int uid, String destAddress, int destPort) {

        if (destAddress == null) {
            return false;
        }

        if (uid == vpnPreferences.getOwnUID()
                || destAddress.equals(vpnPreferences.getItpdRedirectAddress())
                || destAddress.equals(LOOPBACK_ADDRESS)
                || (vpnPreferences.getFixTTL() && !vpnPreferences.getUseProxy())
                || (vpnPreferences.getCompatibilityMode() && uid == SPECIAL_UID_KERNEL)) {
            return false;
        }

        if (vpnPreferences.getLan() || uid == NETWORK_STACK_DEFAULT_UID) {
            for (String address : VpnUtils.nonTorList) {
                if (VpnUtils.isIpInSubnet(destAddress, address)) {
                    return false;
                }
            }
        }

        if (uid == 1000 && destPort == SPECIAL_PORT_NTP) {
            return !(vpnRulesHolder.get().uidSpecialAllowed.contains(SPECIAL_UID_NTP)
                    || vpnRulesHolder.get().mapUidAllowed.containsKey(1000));
        }

        return !vpnPreferences.getSetBypassProxy().contains(String.valueOf(uid));
    }

    private boolean isIpInDNSRebindRange(String destAddress) {
        for (String address : VpnUtils.dnsRebindList) {
            if (VpnUtils.isIpInSubnet(destAddress, address)) {
                return true;
            }
        }
        return false;
    }

    // Called from native code
    @Keep
    @TargetApi(Build.VERSION_CODES.Q)
    public int getUidQ(int version, int protocol, String saddr, int sport, String daddr, int dport) {
        if (saddr == null || daddr == null) {
            return Process.INVALID_UID;
        }

        if (protocol != 6 /* TCP */ && protocol != 17 /* UDP */)
            return Process.INVALID_UID;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null)
            return Process.INVALID_UID;

        InetSocketAddress local = new InetSocketAddress(saddr, sport);
        InetSocketAddress remote = new InetSocketAddress(daddr, dport);

        return cm.getConnectionOwnerUid(protocol, local, remote);
    }

    // Called from native code
    @Keep
    public boolean protectSocket(int socket) {
        return protect(socket);
    }

    // Called from native code
    @Keep
    public Allowed isAddressAllowed(Packet packet) {
        return vpnRulesHolder.get().isAddressAllowed(this, packet);
    }

    // Called from native code
    @Keep
    public void accountUsage(Usage usage) {
        //logi(usage.toString());
    }

    @Override
    public void onCreate() {

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        logi("VPN Create version="
                + VpnUtils.getSelfVersionName(this)
                + "/"
                + VpnUtils.getSelfVersionCode(this)
                + "/"
                + this.hashCode());

        VpnUtils.canFilterAsynchronous(this);

        if (jni_context != 0) {
            logw("VPN Create with context=" + jni_context);
            jni_stop(jni_context);
            synchronized (jni_lock) {
                jni_done(jni_context);
                jni_context = 0;
            }
        }

        // Native init
        jni_context = jni_init(Build.VERSION.SDK_INT);
        service_jni_context = jni_context;
        logi("VPN Created context=" + jni_context);

        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            String title = getString(R.string.app_name);
            String message = getString(R.string.notification_text);
            if (!UsageStatistics.getSavedTitle().isEmpty() && !UsageStatistics.getSavedMessage().isEmpty()) {
                title = UsageStatistics.getSavedTitle();
                message = UsageStatistics.getSavedMessage();
            }

            ModulesServiceNotificationManager notification = new ModulesServiceNotificationManager(
                    this,
                    notificationManager,
                    UsageStatistics.getStartTime()
            );
            notification.sendNotification(title, message);
        }

        App.getInstance().getSubcomponentsManager().modulesServiceSubcomponent().inject(this);

        HandlerThread commandThread = new HandlerThread(
                getString(R.string.app_name) + " command",
                Process.THREAD_PRIORITY_FOREGROUND
        );
        commandThread.start();

        commandLooper = commandThread.getLooper();

        commandHandler = ServiceVPNHandler.getInstance(commandLooper, this);

        connectionCheckerInteractor.get().addListener(this);

        sendRevokeBroadcast(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        SharedPreferences prefs = defaultPreferences.get();
        boolean vpnEnabled = prefs.getBoolean(VPN_SERVICE_ENABLED, false);

        if (intent != null && Objects.equals(intent.getAction(), ACTION_STOP_SERVICE_FOREGROUND)) {

            try {
                notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                stopForeground(true);
            } catch (Exception e) {
                loge("VPNService stop Service foreground1 exception", e);
            }
        }

        boolean showNotification;
        if (intent != null) {
            showNotification = intent.getBooleanExtra("showNotification", true);
        } else {
            showNotification = Utils.INSTANCE.isShowNotification(this);
        }

        if (showNotification) {
            String title = getString(R.string.app_name);
            String message = getString(R.string.notification_text);
            if (!UsageStatistics.getSavedTitle().isEmpty()
                    && !UsageStatistics.getSavedMessage().isEmpty()) {
                title = UsageStatistics.getSavedTitle();
                message = UsageStatistics.getSavedMessage();
            }

            ModulesServiceNotificationManager notification = new ModulesServiceNotificationManager(
                    this,
                    notificationManager,
                    UsageStatistics.getStartTime()
            );
            notification.sendNotification(title, message);
        }

        logi("VPN Received " + intent);

        if (intent != null && Objects.equals(intent.getAction(), ACTION_STOP_SERVICE_FOREGROUND)) {

            try {
                notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                stopForeground(true);
            } catch (Exception e) {
                loge("VPNService stop Service foreground2 exception", e);
            }

            stopSelf(startId);

            return START_NOT_STICKY;
        }

        // Handle service restart
        if (intent == null) {
            logi("VPN OnStart Restart");

            if (vpnEnabled) {
                Intent starterIntent = new Intent(this, BootCompleteReceiver.class);
                starterIntent.setAction(ALWAYS_ON_VPN);
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
            logi("VPN OnStart ALWAYS_ON_VPN");

            if (vpnEnabled) {
                Intent starterIntent = new Intent(this, BootCompleteReceiver.class);
                starterIntent.setAction(ALWAYS_ON_VPN);
                sendBroadcast(starterIntent);
                stopSelf(startId);
                return START_NOT_STICKY;
            } else {
                intent.putExtra(EXTRA_COMMAND, VPNCommand.STOP);
            }
        }

        String reason = intent.getStringExtra(EXTRA_REASON);
        logi("VPN Start intent="
                + intent
                + " command="
                + cmd
                + " reason="
                + reason
                + " vpn="
                + (vpn != null)
                + " user="
                + (pathVars.get().getAppUid() / 100000));

        commandHandler.queue(intent);

        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        logi("VPN Revoke");

        SharedPreferences prefs = defaultPreferences.get();
        prefs.edit().putBoolean(VPN_SERVICE_ENABLED, false).apply();

        sendRevokeBroadcast(true);

        super.onRevoke();
    }

    private void sendRevokeBroadcast(boolean revoked) {
        Intent intent = new Intent(VPN_REVOKE_ACTION);
        intent.putExtra(VPN_REVOKED_EXTRA, revoked);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {

        logi("VPN Destroy " + this.hashCode());

        commandLooper.quit();

        for (VPNCommand command : VPNCommand.values())
            commandHandler.removeMessages(command.ordinal());

        if (vpnDnsSet != null) {
            vpnDnsSet.clear();
        }

        connectionCheckerInteractor.get().removeListener(this);
        handler.get().removeCallbacksAndMessages(null);

        final long localJniContext = service_jni_context;

        cachedExecutor.get().submit(() -> {

            try {
                if (vpn != null) {
                    stopNative();
                    commandHandler.stopVPN(vpn);
                    vpn = null;
                    vpnRulesHolder.get().unPrepare();
                }
            } catch (Throwable ex) {
                loge("VPN Destroy", ex, true);
            }

            synchronized (jni_lock) {
                if (localJniContext == jni_context) {
                    jni_done(jni_context);
                    logi("VPN Destroy context=" + jni_context);
                    jni_context = 0;
                }
            }
        });

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        logi("ServiceVPN onBind");

        String action = null;
        if (intent != null) {
            action = intent.getAction();
        }

        if (VpnService.SERVICE_INTERFACE.equals(action)) {
            return super.onBind(intent);
        }

        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        logi("ServiceVPN onUnbind " + this.hashCode());
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        logi("ServiceVPN onRebind " + this.hashCode());
        super.onRebind(intent);
    }

    @Override
    public void onConnectionChecked(boolean available) {
        if (available) {
            if (!savedInternetAvailable) {
                reload("VPN - Internet is available due to confirmation.", this);
            }
        } else {
            logi("VPN - Internet is not available due to confirmation.");
        }
        savedInternetAvailable = available;
    }

    public boolean isNetworkAvailable() {
        return connectionCheckerInteractor.get().getNetworkConnectionResult();
    }

    public boolean isInternetAvailable() {
        return connectionCheckerInteractor.get().getInternetConnectionResult();
    }

    @Override
    public boolean isActive() {
        return true;
    }

    public class VPNBinder extends Binder {
        public ServiceVPN getService() {
            return ServiceVPN.this;
        }
    }

    public ConcurrentHashMap<ConnectionData, Long> getDnsQueryRawRecords() {
        return connectionDataRecords;
    }

    public void clearDnsQueryRawRecords() {
        cachedExecutor.get().submit(() -> {
            try {
                lock.writeLock().lockInterruptibly();

                if (!connectionDataRecords.isEmpty()) {
                    connectionDataRecords.clear();
                }

            } catch (Exception e) {
                loge("ServiceVPN clearDnsQueryRawRecords", e);
            } finally {
                if (lock.isWriteLockedByCurrentThread()) {
                    lock.writeLock().unlock();
                }
            }
        });
    }

    void addUIDtoDNSQueryRawRecords(
            int uid,
            String destinationAddress,
            int destinationPort,
            String sourceAddress,
            boolean allowed,
            @ConnectionProtocol
            int protocol
    ) {

        if (!vpnPreferences.getConnectionLogsEnabled() || reloading) {
            return;
        }

        try {

            if (uid != 0 || destinationPort != PLAINTEXT_DNS_PORT) {

                PacketRecord packetRecord = new PacketRecord(
                        System.currentTimeMillis(),
                        uid,
                        sourceAddress,
                        destinationAddress,
                        protocol,
                        allowed
                );

                //Remove entry to update key time
                connectionDataRecords.remove(packetRecord);
                connectionDataRecords.put(packetRecord, SystemClock.elapsedRealtimeNanos());

                if (connectionDataRecords.size() > LINES_IN_DNS_QUERY_RAW_RECORDS) {
                    freeSpaceInConnectionRecords();
                }
            }

        } catch (Exception e) {
            loge("ServiceVPN addUIDtoDNSQueryRawRecords", e);
        }

    }

    @Override
    public void onLowMemory() {
        clearDnsQueryRawRecords();
        loge("ServiceVPN low memory");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {

        loge("VPN service task removed " + this.hashCode());

        super.onTaskRemoved(rootIntent);
    }
}
