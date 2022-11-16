package pan.alexander.tordnscrypt.modules;

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

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_DETECTION;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_ENABLED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.GSM_ON_REQUESTED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.KILL_SWITCH;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REFRESH_RULES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.WIFI_ACCESS_POINT_IS_ON;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.WIFI_ON_REQUESTED;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.NULL_MARK;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper.reload;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import dagger.Lazy;
import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor;
import pan.alexander.tordnscrypt.domain.connection_checker.OnInternetConnectionCheckedListener;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.firewall.FirewallNotification;
import pan.alexander.tordnscrypt.utils.ap.InternetSharingChecker;
import pan.alexander.tordnscrypt.utils.apps.InstalledAppNamesStorage;
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys;
import pan.alexander.tordnscrypt.utils.privatedns.PrivateDnsProxyManager;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

public class ModulesReceiver extends BroadcastReceiver implements OnInternetConnectionCheckedListener {

    public static final String VPN_REVOKE_ACTION = "pan.alexander.tordnscrypt.VPN_REVOKE_ACTION";
    public static final String VPN_REVOKED_EXTRA = "pan.alexander.tordnscrypt.VPN_REVOKED_EXTRA";

    private static final String AP_STATE_FILTER_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED";
    private static final String TETHER_STATE_FILTER_ACTION = "android.net.conn.TETHER_STATE_CHANGED";
    private static final String SHUTDOWN_FILTER_ACTION = "android.intent.action.ACTION_SHUTDOWN";
    private static final String REBOOT_FILTER_ACTION = "android.intent.action.REBOOT";
    private static final String POWER_OFF_FILTER_ACTION = "android.intent.action.QUICKBOOT_POWEROFF";
    private static final String SCREEN_ON_ACTION = "android.intent.action.SCREEN_ON";
    private static final String SCREEN_OFF_ACTION = "android.intent.action.SCREEN_OFF";

    private final static int DELAY_BEFORE_CHECKING_INTERNET_SHARING_SEC = 5;
    private final static int DELAY_BEFORE_UPDATING_IPTABLES_RULES_SEC = 5;
    private final static int DELAY_BEFORE_STARTING_VPN_SEC = 1;
    private final static String EXTRA_ACTIVE_TETHER = "tetherArray";

    private final Lazy<PreferenceRepository> preferenceRepository;
    private final Lazy<SharedPreferences> defaultPreferences;
    private final Lazy<ConnectionCheckerInteractor> connectionCheckerInteractor;
    private final Provider<InternetSharingChecker> internetSharingChecker;
    private final CachedExecutor cachedExecutor;
    private final Lazy<Handler> handler;
    private final Lazy<TorRestarterReconnector> torRestarterReconnector;
    private final Lazy<InstalledAppNamesStorage> installedAppNamesStorage;

    private Context context;
    private volatile Object commonNetworkCallback;
    private volatile BroadcastReceiver vpnConnectivityReceiver;
    private volatile FirewallNotification firewallNotificationReceiver;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private volatile OperationMode savedOperationMode = UNDEFINED;
    private volatile boolean commonReceiversRegistered = false;
    private volatile boolean rootReceiversRegistered = false;
    private volatile boolean rootVpnReceiverRegistered = false;
    private volatile boolean lock = false;
    private volatile Future<?> checkTetheringTask;
    private volatile boolean vpnRevoked = false;

    private static final int CHECK_INTERNET_CONNECTION_DELAY_SEC = 30;
    private final AtomicBoolean isCheckingInternetConnection = new AtomicBoolean(false);

    @Inject
    public ModulesReceiver(
            Lazy<PreferenceRepository> preferenceRepository,
            @Named(DEFAULT_PREFERENCES_NAME) Lazy<SharedPreferences> defaultSharedPreferences,
            Lazy<ConnectionCheckerInteractor> connectionCheckerInteractor,
            Provider<InternetSharingChecker> internetSharingChecker,
            CachedExecutor cachedExecutor,
            Lazy<Handler> handler,
            Lazy<TorRestarterReconnector> torRestarterReconnector,
            Lazy<InstalledAppNamesStorage> installedAppNamesStorage
    ) {
        this.preferenceRepository = preferenceRepository;
        this.defaultPreferences = defaultSharedPreferences;
        this.connectionCheckerInteractor = connectionCheckerInteractor;
        this.internetSharingChecker = internetSharingChecker;
        this.cachedExecutor = cachedExecutor;
        this.handler = handler;
        this.torRestarterReconnector = torRestarterReconnector;
        this.installedAppNamesStorage = installedAppNamesStorage;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null) {
            return;
        }

        String action = intent.getAction();

        if (action == null) {
            return;
        }

        Bundle extras = intent.getExtras();
        if (!action.equalsIgnoreCase(SCREEN_ON_ACTION)
                && !action.equalsIgnoreCase(SCREEN_OFF_ACTION)) {
            if (extras != null) {
                logi("ModulesReceiver received " + intent
                        + (extras.isEmpty() ? "" : " " + extras));
            } else {
                logi("ModulesReceiver received " + intent);
            }
        }

        if (this.context == null) {
            logw("ModulesReceiver context is null");
            return;
        }

        PendingResult pendingResult = goAsync();

        cachedExecutor.submit(() -> {
            try {
                intentOnReceive(intent, action);
            } catch (Exception e) {
                loge("ModulesReceiver onReceive", e, true);
            } finally {
                pendingResult.finish();
            }
        });


    }

    private void intentOnReceive(Intent intent, String action) {

        OperationMode mode = modulesStatus.getMode();
        if (savedOperationMode != mode) {
            savedOperationMode = mode;

            unregisterReceivers();

            registerReceivers(context);
        }

        if (action.equalsIgnoreCase(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)) {
            idleStateChanged();
        } else if (action.equalsIgnoreCase(ConnectivityManager.CONNECTIVITY_ACTION)) {
            connectivityStateChanged(intent);
        } else if (action.equalsIgnoreCase(Intent.ACTION_PACKAGE_ADDED)
                || action.equalsIgnoreCase(Intent.ACTION_PACKAGE_REMOVED)) {
            packageChanged(intent);
        } else if (action.equalsIgnoreCase(SCREEN_ON_ACTION)
                || action.equalsIgnoreCase(SCREEN_OFF_ACTION)) {
            interactiveStateChanged(intent);
        } else if (isRootMode() && (action.equalsIgnoreCase(AP_STATE_FILTER_ACTION)
                || action.equalsIgnoreCase(TETHER_STATE_FILTER_ACTION))) {
            checkInternetSharingState(intent);
        } else if (isRootMode() && (action.equalsIgnoreCase(POWER_OFF_FILTER_ACTION)
                || action.equalsIgnoreCase(SHUTDOWN_FILTER_ACTION)
                || action.equalsIgnoreCase(REBOOT_FILTER_ACTION))) {
            powerOFFDetected();
        } else if (isVpnMode() && action.equals(VPN_REVOKE_ACTION)) {
            vpnRevoked(intent.getBooleanExtra(VPN_REVOKED_EXTRA, false));
        }
    }

    void registerReceivers(Context context) {

        if (this.context == null) {
            this.context = context;
        }

        savedOperationMode = modulesStatus.getMode();

        if (!commonReceiversRegistered) {
            registerIdleStateChanged();
            registerConnectivityChanges();
            registerPackageChanged();
            registerInteractiveStateReceiver();
            registerVpnRevokeReceiver();
        }

        if (isRootMode() && !rootReceiversRegistered) {
            registerAPisOn();
            registerUSBModemIsOn();
            registerPowerOFF();
        }

        if (isRootMode() && !modulesStatus.isUseModulesWithRoot()) {
            if (rootVpnReceiverRegistered && modulesStatus.isFixTTL()) {
                unlistenVpnConnectivityChanges();
                rootVpnReceiverRegistered = false;
            } else if (!rootVpnReceiverRegistered && !modulesStatus.isFixTTL()) {
                listenVpnConnectivityChanges();
                rootVpnReceiverRegistered = true;
            }
        } else if (rootVpnReceiverRegistered) {
            unlistenVpnConnectivityChanges();
            rootVpnReceiverRegistered = false;
        }

        if ((isVpnMode() || isRootMode()) && firewallNotificationReceiver == null) {
            registerFirewallReceiver();
        } else if (!(isVpnMode() || isRootMode()) && firewallNotificationReceiver != null) {
            unregisterFirewallReceiver();
        }

    }

    void unregisterReceivers() {

        if (context == null) {
            return;
        }

        if (commonReceiversRegistered) {
            try {
                context.unregisterReceiver(this);
            } catch (Exception e) {
                logw("ModulesReceiver unregisterReceivers", e);
            }
            commonReceiversRegistered = false;
            rootReceiversRegistered = false;
        }

        if (commonNetworkCallback != null) {
            unlistenNetworkChanges();
            commonNetworkCallback = null;
        }

        if (firewallNotificationReceiver != null) {
            unregisterFirewallReceiver();
        }

        if (vpnConnectivityReceiver != null) {
            unlistenVpnConnectivityChanges();
            vpnRevoked = false;
        }
    }

    private void registerIdleStateChanged() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IntentFilter ifIdle = new IntentFilter();
            ifIdle.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
            context.registerReceiver(this, ifIdle);
            commonReceiversRegistered = true;
        }
    }

    private void registerConnectivityChanges() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                listenNetworkChanges();
            } catch (Exception e) {
                logw("ModulesReceiver registerConnectivityChanges", e);
                listenConnectivityChanges();
            }
        } else {
            listenConnectivityChanges();
        }
    }

    private void registerAPisOn() {
        IntentFilter apStateChanged = new IntentFilter();
        apStateChanged.addAction(AP_STATE_FILTER_ACTION);
        context.registerReceiver(this, apStateChanged);
        rootReceiversRegistered = true;
    }

    private void registerUSBModemIsOn() {
        IntentFilter apStateChanged = new IntentFilter();
        apStateChanged.addAction(TETHER_STATE_FILTER_ACTION);
        context.registerReceiver(this, apStateChanged);
        rootReceiversRegistered = true;
    }

    private void registerPowerOFF() {
        IntentFilter powerOFF = new IntentFilter();
        powerOFF.addAction(SHUTDOWN_FILTER_ACTION);
        powerOFF.addAction(POWER_OFF_FILTER_ACTION);
        powerOFF.addAction(REBOOT_FILTER_ACTION);
        context.registerReceiver(this, powerOFF);
        rootReceiversRegistered = true;
    }

    private void registerPackageChanged() {
        IntentFilter ifPackage = new IntentFilter();
        ifPackage.addAction(Intent.ACTION_PACKAGE_ADDED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_REMOVED);
        ifPackage.addDataScheme("package");
        context.registerReceiver(this, ifPackage);
        commonReceiversRegistered = true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void listenNetworkChanges() {

        logi("ModulesReceiver start listening to network changes");

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        if (isVpnMode()) {
            builder.removeTransportType(NetworkCapabilities.TRANSPORT_VPN);
        } else {
            builder.addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }

        ConnectivityManager.NetworkCallback nc = new ConnectivityManager.NetworkCallback() {
            private final Pattern SIGNAL_STRENGTH_PATTERN = Pattern.compile("SignalStrength: ?-(\\d+)");
            private static final int SIGNAL_STRENGTH_THRESHOLD = 20;
            private volatile Boolean last_connected = null;
            private volatile List<InetAddress> last_dns = null;
            private volatile int last_network = 0;
            private volatile int last_signal_strength = 0;

            @Override
            public void onAvailable(@NonNull Network network) {

                last_connected = isNetworkAvailable();

                logi("ModulesReceiver available network=" + network + " connected=" + last_connected);

                if (!last_connected) {
                    last_connected = true;

                    if (isVpnMode() || isRootMode()) {
                        setInternetAvailable(true);
                    }
                }

                if (isVpnMode() && !vpnRevoked) {
                    reload("Network available", context);
                } else if (isRootMode()) {
                    updateIptablesRules(false);
                    resetArpScanner(true);
                } else if (isProxyMode() || vpnRevoked) {
                    resetArpScanner(true);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && last_network != network.hashCode()) {
                    PrivateDnsProxyManager.INSTANCE.checkPrivateDNSAndProxy(
                            context, null
                    );
                }

                last_network = network.hashCode();

            }

            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, LinkProperties linkProperties) {

                logi("ModulesReceiver changed link properties=" + linkProperties);

                // Make sure the right DNS servers are being used
                List<InetAddress> dns = linkProperties.getDnsServers();

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !same(last_dns, dns)) {
                    logi(" DNS cur=" + (dns == null ? null : TextUtils.join(",", dns)) +
                            " DNS prv=" + (last_dns == null ? null : TextUtils.join(",", last_dns)));

                    last_dns = dns;

                    if (isRootMode()) {
                        updateIptablesRules(false);
                    }

                    if (network.hashCode() != last_network) {
                        last_network = network.hashCode();

                        if (isVpnMode() && !vpnRevoked) {
                            setInternetAvailable(false);
                            reload("Link properties changed", context);
                        } else if (isRootMode() || vpnRevoked) {
                            resetArpScanner();
                            checkInternetConnection();
                        } else if (isProxyMode()) {
                            resetArpScanner();
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            PrivateDnsProxyManager.INSTANCE.checkPrivateDNSAndProxy(
                                    context, linkProperties
                            );
                        }
                    }
                }

            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {

                if (isNetworkAvailable() && (last_connected == null || !last_connected)) {

                    last_connected = true;

                    if (isVpnMode()) {
                        if (vpnRevoked) {
                            resetArpScanner();
                            checkInternetConnection();
                        } else {
                            setInternetAvailable(false);
                            reload("Connected state changed", context);
                        }
                        logi("ModulesReceiver changed capabilities=" + network);
                    } else if (isRootMode() && last_network != network.hashCode()) {
                        updateIptablesRules(false);
                        resetArpScanner();
                        checkInternetConnection();
                        logi("ModulesReceiver changed capabilities=" + network);
                    } else if (isProxyMode()) {
                        resetArpScanner();
                        logi("ModulesReceiver changed capabilities=" + network);
                    }
                } else if (modulesStatus.getTorState() == RUNNING
                        && (isVpnMode() || isRootMode())) {
                    Matcher matcher = SIGNAL_STRENGTH_PATTERN.matcher(networkCapabilities.toString());
                    if (matcher.find()) {
                        String signalStrength = matcher.group(1);
                        if (signalStrength != null) {
                            int signal_strength = Integer.parseInt(signalStrength);
                            if (Math.abs(signal_strength - last_signal_strength) > SIGNAL_STRENGTH_THRESHOLD
                                    && isCheckingInternetConnection.compareAndSet(false, true)) {
                                checkInternetConnectionWithDelay();
                                logi("ModulesReceiver changed signal strength. "
                                        + "Network " + network + " "
                                        + "SignalStrength " + signalStrength);
                            }
                            last_signal_strength = signal_strength;
                        }
                    } else if (network.hashCode() != last_network
                            && isCheckingInternetConnection.compareAndSet(false, true)) {
                        checkInternetConnectionWithDelay();
                        logi("ModulesReceiver network has changed. "
                                + "Network " + network);
                    }
                }

                last_network = network.hashCode();

            }

            @Override
            public void onLost(@NonNull Network network) {

                last_connected = false;

                logi("ModulesReceiver lost network=" + network + " connected=false");

                if (isVpnMode() && !vpnRevoked) {
                    setInternetAvailable(false);
                    reload("Network lost", context);
                } else if (isVpnMode() && vpnRevoked) {
                    setInternetAvailable(false);
                    resetArpScanner(false);
                } else if (isRootMode()) {
                    setInternetAvailable(false);
                    updateIptablesRules(false);
                    resetArpScanner(false);
                } else if (isProxyMode()) {
                    resetArpScanner(false);
                }

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
            commonNetworkCallback = nc;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unlistenNetworkChanges() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            cm.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) commonNetworkCallback);
        }
    }

    private void listenConnectivityChanges() {
        logi("ModulesReceiver start listening to connectivity changes");
        IntentFilter ifConnectivity = new IntentFilter();
        ifConnectivity.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(this, ifConnectivity);
        commonReceiversRegistered = true;
    }

    private void registerFirewallReceiver() {
        firewallNotificationReceiver = FirewallNotification.registerFirewallReceiver(context);
    }

    private void unregisterFirewallReceiver() {

        try {
            FirewallNotification.unregisterFirewallReceiver(context, firewallNotificationReceiver);
        } catch (Exception e) {
            logw("ModulesReceiver unregisterFirewallReceiver", e);
        }

        firewallNotificationReceiver = null;
    }

    private void registerVpnRevokeReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(VPN_REVOKE_ACTION);
        context.registerReceiver(this, intentFilter);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void listenVpnConnectivityChanges() {

        logi("ModulesReceiver start listening to vpn connectivity changes");

        vpnConnectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int networkType = intent.getIntExtra(
                        ConnectivityManager.EXTRA_NETWORK_TYPE,
                        ConnectivityManager.TYPE_DUMMY
                );
                if (networkType == ConnectivityManager.TYPE_VPN) {
                    if (isVpnMode()) {
                        checkVpnRestoreAfterRevoke();
                    } else if (isRootMode()
                            && !modulesStatus.isUseModulesWithRoot()
                            && !modulesStatus.isFixTTL()) {
                        connectivityStateChanged(new Intent("VPN connectivity changed"));
                    }
                }
            }
        };

        IntentFilter ifConnectivity = new IntentFilter();
        ifConnectivity.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(vpnConnectivityReceiver, ifConnectivity);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unlistenVpnConnectivityChanges() {

        if (vpnConnectivityReceiver != null) {
            logi("ModulesReceiver stop listening to vpn connectivity changes");
            try {
                context.unregisterReceiver(vpnConnectivityReceiver);
            } catch (Exception e) {
                logw("ModulesReceiver unlistenVpnConnectivityChanges", e);
            } finally {
                vpnConnectivityReceiver = null;
            }
        }
    }

    private void registerInteractiveStateReceiver() {
        IntentFilter screenIntentFilter = new IntentFilter();
        screenIntentFilter.addAction(SCREEN_ON_ACTION);
        screenIntentFilter.addAction(SCREEN_OFF_ACTION);
        context.registerReceiver(this, screenIntentFilter);
        commonReceiversRegistered = true;
    }

    private void idleStateChanged() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            logi("ModulesReceiver device idle=" + pm.isDeviceIdleMode());
        }

        if (pm != null && !pm.isDeviceIdleMode()) {
            if (isVpnMode() && !vpnRevoked) {
                setInternetAvailable(false);
                reload("Idle state changed", context);
            } else if (isVpnMode() && vpnRevoked) {
                resetArpScanner();
                checkInternetConnection();
            } else if (isRootMode()) {
                updateIptablesRules(false);
                resetArpScanner();
                checkInternetConnection();
            } else if (isProxyMode()) {
                resetArpScanner();
            }
        }
    }

    private void connectivityStateChanged(Intent intent) {
        logi("ModulesReceiver connectivityStateChanged received " + intent);

        if (isVpnMode()) {
            // Filter VPN connectivity changes
            int networkType = intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, ConnectivityManager.TYPE_DUMMY);
            if (networkType == ConnectivityManager.TYPE_VPN)
                return;

            if (vpnRevoked) {
                resetArpScanner();
                checkInternetConnection();
            } else {
                setInternetAvailable(false);
                reload("Connectivity changed", context);
            }

        } else if (isRootMode()) {
            updateIptablesRules(false);
            resetArpScanner();
            checkInternetConnection();
        } else if (isProxyMode()) {
            resetArpScanner();
        }
    }

    private void interactiveStateChanged(Intent intent) {

        if (SCREEN_ON_ACTION.equals(intent.getAction())) {

            modulesStatus.setDeviceInteractive(true);

            ConnectionCheckerInteractor interactor = connectionCheckerInteractor.get();
            if (!interactor.getNetworkConnectionResult()) {
                interactor.checkNetworkConnection();
            }
            if (!interactor.getInternetConnectionResult()) {
                interactor.checkInternetConnection();
            } else if (interactor.getNetworkConnectionResult()
                    && modulesStatus.getTorState() == RUNNING
                    && (isVpnMode() || isRootMode())
                    && isCheckingInternetConnection.compareAndSet(false, true)) {
                checkInternetConnectionWithDelay();
            }
        } else if (SCREEN_OFF_ACTION.equals(intent.getAction())) {
            modulesStatus.setDeviceInteractive(false);
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void checkInternetSharingState(Intent intent) {

        if (checkTetheringTask != null && !checkTetheringTask.isDone()) {
            if (TETHER_STATE_FILTER_ACTION.equals(intent.getAction())) {
                checkTetheringTask.cancel(true);
            } else {
                return;
            }
        }

        checkTetheringTask = cachedExecutor.submit(() -> {
            boolean wifiAccessPointOn = false;
            boolean usbTetherOn = false;
            String action = intent.getAction();

            try {

                List<String> tetherList = null;
                Serializable serializable = intent.getSerializableExtra(EXTRA_ACTIVE_TETHER);
                if (serializable instanceof List) {
                    tetherList = (List<String>) intent.getSerializableExtra(EXTRA_ACTIVE_TETHER);
                }

                TimeUnit.SECONDS.sleep(DELAY_BEFORE_CHECKING_INTERNET_SHARING_SEC);

                InternetSharingChecker checker = internetSharingChecker.get();
                if (tetherList != null) {
                    checker.setTetherInterfaceName(tetherList);
                } else if (TETHER_STATE_FILTER_ACTION.equals(action)) {
                    checker.setTetherInterfaceName(null);
                }
                checker.updateData();
                wifiAccessPointOn = checker.isApOn();
                usbTetherOn = checker.isUsbTetherOn();

            } catch (InterruptedException ignored) {
                logi("ModulesReceiver checkInternetSharingState action " + action + " interrupted");
            } catch (Exception e) {
                loge("ModulesReceiver checkInternetSharingState exception", e);
            }

            PreferenceRepository preferences = preferenceRepository.get();

            if (wifiAccessPointOn && !preferences.getBoolPreference(WIFI_ACCESS_POINT_IS_ON)) {
                preferences.setBoolPreference(WIFI_ACCESS_POINT_IS_ON, true);
                modulesStatus.setIptablesRulesUpdateRequested(context, true);
            } else if (!wifiAccessPointOn && preferences.getBoolPreference(WIFI_ACCESS_POINT_IS_ON)) {
                preferences.setBoolPreference(WIFI_ACCESS_POINT_IS_ON, false);
                modulesStatus.setIptablesRulesUpdateRequested(context, true);
            }

            if (usbTetherOn && !preferences.getBoolPreference(PreferenceKeys.USB_MODEM_IS_ON)) {
                preferences.setBoolPreference(PreferenceKeys.USB_MODEM_IS_ON, true);
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
            } else if (!usbTetherOn && preferences.getBoolPreference(PreferenceKeys.USB_MODEM_IS_ON)) {
                preferences.setBoolPreference(PreferenceKeys.USB_MODEM_IS_ON, false);
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
            }

            logi("ModulesReceiver " +
                    "WiFi Access Point state is " + (wifiAccessPointOn ? "ON" : "OFF") + "\n"
                    + " USB modem state is " + (usbTetherOn ? "ON" : "OFF"));
        });
    }

    private void powerOFFDetected() {

        boolean killSwitch = defaultPreferences.get().getBoolean(KILL_SWITCH, false);
        if (killSwitch) {

            List<String> commands = new ArrayList<>(2);
            commands.add("svc wifi disable");
            commands.add("svc data disable");

            boolean networkAvailable = false;

            if (NetworkChecker.isWifiActive(context, true)) {
                networkAvailable = true;
                preferenceRepository.get().setBoolPreference(WIFI_ON_REQUESTED, true);
                logi("Disabling WiFi due to a kill switch");
            }
            if (NetworkChecker.isCellularActive(context, true)) {
                networkAvailable = true;
                preferenceRepository.get().setBoolPreference(GSM_ON_REQUESTED, true);
                logi("Disabling GSM due to a kill switch");
            }

            if (networkAvailable) {
                RootCommands.execute(context, commands, NULL_MARK);
            }
        }

        ModulesAux.saveDNSCryptStateRunning(false);
        ModulesAux.saveTorStateRunning(false);
        ModulesAux.saveITPDStateRunning(false);

        ModulesAux.stopModulesIfRunning(context);
    }

    private void packageChanged(Intent intent) {

        logi("ModulesReceiver packageChanged " + intent);

        if (isVpnMode()) {
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                reload("Package added", context);
            } else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                reload("Package deleted", context);
            }
        } else if (isRootMode()) {

            updateIptablesRules(true);

            if (!modulesStatus.isFixTTL()) {
                installedAppNamesStorage.get().updateAppUidToNames();
            }
        }
    }

    private void vpnRevoked(boolean vpnRevoked) {
        this.vpnRevoked = vpnRevoked;

        if (vpnRevoked) {
            listenVpnConnectivityChanges();
            resetArpScanner();
        } else {
            unlistenVpnConnectivityChanges();
        }
    }

    private void checkVpnRestoreAfterRevoke() {
        handler.get().postDelayed(() -> {
            if (vpnRevoked && !NetworkChecker.isVpnActive(context)) {
                startVPNService();
            }
        }, DELAY_BEFORE_STARTING_VPN_SEC * 1000);
    }

    private void startVPNService() {

        final Intent prepareIntent = VpnService.prepare(context);

        if (prepareIntent != null) {
            return;
        }

        if (!defaultPreferences.get().getBoolean(VPN_SERVICE_ENABLED, false)
                && (modulesStatus.getDnsCryptState() == RUNNING || modulesStatus.getTorState() == RUNNING)) {
            defaultPreferences.get().edit().putBoolean(VPN_SERVICE_ENABLED, true).apply();
            ServiceVPNHelper.start(
                    "ModulesReceiver start VPN service after revoke",
                    context
            );
        }
    }

    private void updateIptablesRules(boolean forceUpdate) {

        boolean refreshRules = defaultPreferences.get().getBoolean(REFRESH_RULES, false);
        boolean fixTTL = modulesStatus.isFixTTL();

        if (!refreshRules && !forceUpdate && !fixTTL && !isFirewallEnabled()) {
            return;
        }

        if (modulesStatus.getMode() == ROOT_MODE
                && !modulesStatus.isUseModulesWithRoot()
                && !lock) {

            cachedExecutor.submit(() -> {
                if (!lock) {

                    lock = true;

                    try {
                        TimeUnit.SECONDS.sleep(DELAY_BEFORE_UPDATING_IPTABLES_RULES_SEC);
                    } catch (InterruptedException e) {
                        logw("ModulesReceiver sleep interruptedException " + e.getMessage());
                    }

                    if (modulesStatus.getMode() == ROOT_MODE && !modulesStatus.isUseModulesWithRoot()) {
                        modulesStatus.setIptablesRulesUpdateRequested(context, true);
                    }

                    lock = false;
                }

            });
        }
    }

    private void resetArpScanner(boolean connectionAvailable) {
        if (defaultPreferences.get().getBoolean(ARP_SPOOFING_DETECTION, false)) {
            try {
                ArpScanner.getArpComponent().get().reset(connectionAvailable);
            } catch (Exception e) {
                loge("ModulesReceiver resetArpScanner", e);
            }
        }
    }

    private void resetArpScanner() {
        if (context != null && defaultPreferences.get().getBoolean(ARP_SPOOFING_DETECTION, false)) {
            ConnectionCheckerInteractor interactor = connectionCheckerInteractor.get();
            interactor.checkNetworkConnection();
            try {
                ArpScanner.getArpComponent().get().reset(interactor.getNetworkConnectionResult());
            } catch (Exception e) {
                loge("ModulesReceiver resetArpScanner", e);
            }
        }
    }

    private void setInternetAvailable(boolean available) {
        ConnectionCheckerInteractor interactor = connectionCheckerInteractor.get();
        interactor.setInternetConnectionResult(available);
        interactor.checkNetworkConnection();
        interactor.checkInternetConnection();
    }

    private void checkInternetConnection() {
        ConnectionCheckerInteractor interactor = connectionCheckerInteractor.get();
        interactor.setInternetConnectionResult(false);
        interactor.checkInternetConnection();
    }

    private void checkInternetConnectionWithDelay() {
        handler.get().postDelayed(() -> {
            checkInternetConnection();
            isCheckingInternetConnection.set(false);
        }, CHECK_INTERNET_CONNECTION_DELAY_SEC * 1000);

    }

    @Override
    @SuppressLint("UnsafeOptInUsageWarning")
    public void onConnectionChecked(boolean available) {

        if (modulesStatus.getTorState() == RUNNING && modulesStatus.isTorReady()) {
            if (available) {
                torRestarterReconnector.get().stopRestarterCounter();
            } else if (isNetworkAvailable()) {
                torRestarterReconnector.get().startRestarterCounter();
            }
        }

        if (isVpnMode()) {
            return;
        }

        if (available) {
            logi("ModulesReceiver - Internet is available due to confirmation.");
        } else {
            logi("ModulesReceiver - Internet is not available due to confirmation.");
        }
    }

    @Override
    public boolean isActive() {
        return true;
    }

    private boolean isVpnMode() {
        return modulesStatus.getMode().equals(VPN_MODE);
    }

    private boolean isRootMode() {
        return modulesStatus.getMode().equals(ROOT_MODE);
    }

    private boolean isProxyMode() {
        return modulesStatus.getMode().equals(PROXY_MODE);
    }

    private boolean isNetworkAvailable() {
        connectionCheckerInteractor.get().checkNetworkConnection();
        return connectionCheckerInteractor.get().getNetworkConnectionResult();
    }

    private boolean isFirewallEnabled() {
        return preferenceRepository.get().getBoolPreference(FIREWALL_ENABLED);
    }
}
