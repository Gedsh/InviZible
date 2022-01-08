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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.vpn.service;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REFRESH_RULES;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper.reload;

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
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.net.InetAddress;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor;
import pan.alexander.tordnscrypt.settings.firewall.FirewallNotification;
import pan.alexander.tordnscrypt.utils.privatedns.PrivateDnsProxyManager;

public class VpnReceiver {

    private final Lazy<ConnectionCheckerInteractor> connectionCheckerInteractor;
    private final Lazy<SharedPreferences> defaultPreferences;

    private boolean registeredIdleState = false;
    private boolean registeredPackageChanged = false;
    private boolean registeredConnectivityChanged = false;
    private Object networkCallback = null;
    private FirewallNotification firewallNotificationReceiver;

    @Inject
    public VpnReceiver(
            Lazy<ConnectionCheckerInteractor> connectionCheckerInteractor,
            @Named(DEFAULT_PREFERENCES_NAME)
            Lazy<SharedPreferences> defaultPreferences
    ) {
        this.connectionCheckerInteractor = connectionCheckerInteractor;
        this.defaultPreferences = defaultPreferences;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    void listenIdleStateChanged(ServiceVPN vpn) {
        IntentFilter ifIdle = new IntentFilter();
        ifIdle.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        vpn.registerReceiver(idleStateReceiver, ifIdle);
        registeredIdleState = true;
    }

    void unlistenIdleStateChanged(ServiceVPN vpn) {
        if (registeredIdleState) {
            vpn.unregisterReceiver(idleStateReceiver);
            registeredIdleState = false;
        }
    }

    void listenAddRemoveApp(ServiceVPN vpn) {
        IntentFilter ifPackage = new IntentFilter();
        ifPackage.addAction(Intent.ACTION_PACKAGE_ADDED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_REMOVED);
        ifPackage.addDataScheme("package");
        vpn.registerReceiver(packageChangedReceiver, ifPackage);

        registerFirewallReceiver(vpn);

        registeredPackageChanged = true;
    }

    void unlistenAddRemoveApp(ServiceVPN vpn) {
        if (registeredPackageChanged) {
            vpn.unregisterReceiver(packageChangedReceiver);
            registeredPackageChanged = false;

            unregisterFirewallReceiver(vpn);
        }
    }

    private void registerFirewallReceiver(ServiceVPN vpn) {
        firewallNotificationReceiver = FirewallNotification.Companion.registerFirewallReceiver(vpn);
    }

    private void unregisterFirewallReceiver(ServiceVPN vpn) {
        FirewallNotification.Companion.unregisterFirewallReceiver(vpn, firewallNotificationReceiver);
    }

    private final BroadcastReceiver idleStateReceiver = new BroadcastReceiver() {
        @Override
        @TargetApi(Build.VERSION_CODES.M)
        public void onReceive(Context context, Intent intent) {
            logi("VPN Received " + intent);

            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                logi("VPN device idle=" + pm.isDeviceIdleMode());
            }

            // Reload rules when coming from idle mode
            if (pm != null && !pm.isDeviceIdleMode()) {
                setInternetAvailable(false);
                reload("VPN idle state changed", context);
            }
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
            logi("VPN Received " + intent);
            setInternetAvailable(false);
            reload("Connectivity changed", context);
        }
    };

    private final BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logi("VPN Received " + intent);

            try {
                if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                    reload("VPN Package added", context);
                } else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                    reload("VPN Package deleted", context);
                }
            } catch (Throwable ex) {
                loge(ex.toString() + "\n" + Log.getStackTraceString(ex));
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    void listenNetworkChanges(ServiceVPN vpn) {
        // Listen for network changes
        logi("VPN Starting listening to network changes");
        ConnectivityManager cm = (ConnectivityManager) vpn.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        /*builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }*/

        ConnectivityManager.NetworkCallback nc = new ConnectivityManager.NetworkCallback() {
            private Boolean last_connected = null;
            private List<InetAddress> last_dns = null;
            private int last_network = 0;

            @Override
            public void onAvailable(@NonNull Network network) {
                logi("VPN Available network=" + network);
                connectionCheckerInteractor.get().checkNetworkConnection();
                last_connected = isNetworkAvailable();

                if (!last_connected) {
                    last_connected = true;
                    setInternetAvailable(true);
                }

                reload("Network available", vpn);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && last_network != network.hashCode()) {
                    PrivateDnsProxyManager.INSTANCE.checkPrivateDNSAndProxy(
                            vpn, null
                    );
                }

                last_network = network.hashCode();
            }

            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, LinkProperties linkProperties) {
                // Make sure the right DNS servers are being used
                List<InetAddress> dns = linkProperties.getDnsServers();
                SharedPreferences prefs = defaultPreferences.get();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? !same(last_dns, dns)
                        : prefs.getBoolean(REFRESH_RULES, false)) {
                    logi("VPN Changed link properties=" + linkProperties +
                            "DNS cur=" + TextUtils.join(",", dns) +
                            "DNS prv=" + (last_dns == null ? null : TextUtils.join(",", last_dns)));
                    last_dns = dns;
                    logi("VPN Changed link properties=" + linkProperties);

                    if (network.hashCode() != last_network) {
                        last_network = network.hashCode();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            PrivateDnsProxyManager.INSTANCE.checkPrivateDNSAndProxy(
                                    vpn, linkProperties
                            );
                        }

                        setInternetAvailable(false);
                        reload("VPN Link properties changed", vpn);
                    }
                }
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                connectionCheckerInteractor.get().checkNetworkConnection();
                if (isNetworkAvailable() && (last_connected == null || !last_connected)) {
                    last_connected = true;
                    setInternetAvailable(false);
                    reload("VPN Connected state changed", vpn);
                }

                last_network = network.hashCode();

                logi("VPN Changed capabilities=" + network);
            }

            @Override
            public void onLost(@NonNull Network network) {
                logi("VPN Lost network=" + network);
                connectionCheckerInteractor.get().checkNetworkConnection();
                last_connected = isNetworkAvailable();

                setInternetAvailable(false);

                reload("Network lost", vpn);

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

    void unlistenNetworkChanges(ServiceVPN vpn) {
        if (networkCallback != null) {
            unregisterNetworkChanges(vpn);
            networkCallback = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unregisterNetworkChanges(ServiceVPN vpn) {
        ConnectivityManager cm = (ConnectivityManager) vpn.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            cm.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) networkCallback);
        }
    }

    void listenConnectivityChanges(ServiceVPN vpn) {
        // Listen for connectivity updates
        logi("VPN Starting listening to connectivity changes");
        IntentFilter ifConnectivity = new IntentFilter();
        ifConnectivity.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        vpn.registerReceiver(connectivityChangedReceiver, ifConnectivity);
        registeredConnectivityChanged = true;
    }

    void unlistenConnectivityChanges(ServiceVPN vpn) {
        if (registeredConnectivityChanged) {
            vpn.unregisterReceiver(connectivityChangedReceiver);
            registeredConnectivityChanged = false;
        }
    }

    private boolean isNetworkAvailable() {
        return connectionCheckerInteractor.get().getNetworkConnectionResult();
    }

    private void setInternetAvailable(boolean available) {
        connectionCheckerInteractor.get().setInternetConnectionResult(available);
    }
}
