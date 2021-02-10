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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

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
import androidx.preference.PreferenceManager;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.iptables.Tethering;
import pan.alexander.tordnscrypt.utils.ApManager;
import pan.alexander.tordnscrypt.utils.AuxNotificationSender;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.vpn.Util;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

public class ModulesBroadcastReceiver extends BroadcastReceiver {

    private final Context context;
    private boolean receiverRegistered = false;
    private Object networkCallback;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private volatile boolean lock = false;
    private static final String apStateFilterAction = "android.net.wifi.WIFI_AP_STATE_CHANGED";
    private static final String tetherStateFilterAction = "android.net.conn.TETHER_STATE_CHANGED";
    private static final String shutdownFilterAction = "android.intent.action.ACTION_SHUTDOWN";
    private static final String powerOFFFilterAction = "android.intent.action.QUICKBOOT_POWEROFF";
    private final ArpScanner arpScanner;

    public ModulesBroadcastReceiver(Context context, ArpScanner arpScanner) {
        this.context = context;
        this.arpScanner = arpScanner;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action == null) {
            return;
        }

        if (action.equalsIgnoreCase(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            Log.i(LOG_TAG, "ModulesBroadcastReceiver Received " + intent);

            idleStateChanged(context);

        } else if (action.equalsIgnoreCase(ConnectivityManager.CONNECTIVITY_ACTION)) {
            connectivityStateChanged(intent);
        } else if (action.equalsIgnoreCase(apStateFilterAction)) {
            apStateChanged();
        } else if (action.equalsIgnoreCase(tetherStateFilterAction)) {
            tetherStateChanged();
        } else if (action.equalsIgnoreCase(powerOFFFilterAction) || action.equalsIgnoreCase(shutdownFilterAction)) {
            powerOFFDetected();
        } else if (action.equalsIgnoreCase(Intent.ACTION_PACKAGE_ADDED) || action.equalsIgnoreCase(Intent.ACTION_PACKAGE_REMOVED)) {
            packageChanged();
        }
    }

    void registerReceivers() {
        registerIdleStateChanged();
        registerConnectivityChanges();
        registerAPisOn();
        registerUSBModemIsOn();
        registerPowerOFF();
        registerPackageChanged();
    }

    void unregisterReceivers() {
        if (context == null) {
            return;
        }

        if (receiverRegistered) {
            context.unregisterReceiver(this);
            receiverRegistered = false;
        }

        if (networkCallback != null) {
            unlistenNetworkChanges();
            networkCallback = null;
        }
    }

    private void registerIdleStateChanged() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IntentFilter ifIdle = new IntentFilter();
            ifIdle.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
            context.registerReceiver(this, ifIdle);
            receiverRegistered = true;
        }
    }

    private void registerConnectivityChanges() {
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

    private void registerAPisOn() {
        IntentFilter apStateChanged = new IntentFilter();
        apStateChanged.addAction(apStateFilterAction);
        context.registerReceiver(this, apStateChanged);
        receiverRegistered = true;
    }

    private void registerUSBModemIsOn() {
        IntentFilter apStateChanged = new IntentFilter();
        apStateChanged.addAction(tetherStateFilterAction);
        context.registerReceiver(this, apStateChanged);
        receiverRegistered = true;
    }

    private void registerPowerOFF() {
        IntentFilter powerOFF = new IntentFilter();
        powerOFF.addAction(shutdownFilterAction);
        powerOFF.addAction(powerOFFFilterAction);
        context.registerReceiver(this, powerOFF);
        receiverRegistered = true;
    }

    private void registerPackageChanged() {
        // Listen for added/removed applications
        IntentFilter ifPackage = new IntentFilter();
        ifPackage.addAction(Intent.ACTION_PACKAGE_ADDED);
        ifPackage.addAction(Intent.ACTION_PACKAGE_REMOVED);
        ifPackage.addDataScheme("package");
        context.registerReceiver(this, ifPackage);
        receiverRegistered = true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void listenNetworkChanges() {
        // Listen for network changes
        Log.i(LOG_TAG, "ModulesBroadcastReceiver Starting listening to network changes");
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }

        ConnectivityManager.NetworkCallback nc = new ConnectivityManager.NetworkCallback() {
            private List<InetAddress> last_dns = null;
            private int last_network = 0;

            @Override
            public void onAvailable(@NonNull Network network) {
                Log.i(LOG_TAG, "ModulesBroadcastReceiver Available network=" + network);
                updateIptablesRules(false);
                resetArpScanner(true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && last_network != network.hashCode()) {
                    AuxNotificationSender.INSTANCE.checkPrivateDNSAndProxy(
                            context, null
                    );
                }

                last_network = network.hashCode();

            }

            @Override
            public void onLinkPropertiesChanged(@NonNull Network network, LinkProperties linkProperties) {
                // Make sure the right DNS servers are being used
                List<InetAddress> dns = linkProperties.getDnsServers();

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !same(last_dns, dns)) {
                    Log.i(LOG_TAG, "ModulesBroadcastReceiver Changed link properties=" + linkProperties +
                            "ModulesBroadcastReceiver cur=" + TextUtils.join(",", dns) +
                            "ModulesBroadcastReceiver prv=" + (last_dns == null ? null : TextUtils.join(",", last_dns)));
                    last_dns = dns;
                    Log.i(LOG_TAG, "ModulesBroadcastReceiver Changed link properties=" + linkProperties);
                    updateIptablesRules(false);
                }

                if (network.hashCode() != last_network) {
                    last_network = network.hashCode();
                    resetArpScanner();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        AuxNotificationSender.INSTANCE.checkPrivateDNSAndProxy(
                                context, linkProperties
                        );
                    }
                }
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                if (last_network != network.hashCode()) {
                    updateIptablesRules(false);
                    resetArpScanner();
                    last_network = network.hashCode();

                    Log.i(LOG_TAG, "ModulesBroadcastReceiver Changed capabilities=" + network);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.i(LOG_TAG, "ModulesBroadcastReceiver Lost network=" + network);
                updateIptablesRules(false);
                resetArpScanner(false);
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
        Log.i(LOG_TAG, "ModulesBroadcastReceiver Starting listening to connectivity changes");
        IntentFilter ifConnectivity = new IntentFilter();
        ifConnectivity.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        context.registerReceiver(this, ifConnectivity);
        receiverRegistered = true;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void idleStateChanged(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            Log.i(LOG_TAG, "ModulesBroadcastReceiver device idle=" + pm.isDeviceIdleMode());
        }

        // Reload rules when coming from idle mode
        if (pm != null && !pm.isDeviceIdleMode()) {
            updateIptablesRules(false);
            resetArpScanner();
        }
    }

    private void connectivityStateChanged(Intent intent) {
        // Reload rules
        Log.i(LOG_TAG, "ModulesBroadcastReceiver connectivityStateChanged Received " + intent);
        updateIptablesRules(false);

        resetArpScanner();
    }

    private void apStateChanged() {
        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {

            try {
                TimeUnit.SECONDS.sleep(3);

                ApManager apManager = new ApManager(context);
                int apState = apManager.isApOn();

                //Try to check once again if reflection is not working
                if (apState == ApManager.apStateUnknown) {
                    apState = apManager.confirmApState();
                }

                if (apState == ApManager.apStateON) {

                    if (!new PrefManager(context).getBoolPref("APisON")) {

                        new PrefManager(context).setBoolPref("APisON", true);

                        modulesStatus.setIptablesRulesUpdateRequested(context, true);

                        Log.i(LOG_TAG, "ModulesBroadcastReceiver AP is ON");

                    }

                } else if (apState == ApManager.apStateOFF) {
                    if (new PrefManager(context).getBoolPref("APisON")) {
                        new PrefManager(context).setBoolPref("APisON", false);

                        modulesStatus.setIptablesRulesUpdateRequested(context, true);

                        Log.i(LOG_TAG, "ModulesBroadcastReceiver AP is OFF");
                    }
                }
            } catch (Exception e) {
                Log.i(LOG_TAG, "ModulesBroadcastReceiver apStateChanged exception " + e.getMessage() + " " + e.getCause());
            }

        });
    }

    private void tetherStateChanged() {
        checkUSBModemState();
    }

    private void powerOFFDetected() {
        new PrefManager(context).setBoolPref("DNSCrypt Running", false);
        new PrefManager(context).setBoolPref("Tor Running", false);
        new PrefManager(context).setBoolPref("I2PD Running", false);

        ModulesAux.stopModulesIfRunning(context);
    }

    private void packageChanged() {
        Log.i(LOG_TAG, "ModulesBroadcastReceiver packageChanged");
        updateIptablesRules(true);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unlistenNetworkChanges() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            cm.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) networkCallback);
        }
    }

    private void updateIptablesRules(boolean forceUpdate) {

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean refreshRules = sharedPreferences.getBoolean("swRefreshRules", false);

        if (!refreshRules && !forceUpdate) {
            return;
        }

        if (modulesStatus.getMode() == ROOT_MODE
                && !modulesStatus.isUseModulesWithRoot()
                && !lock) {

            CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
                if (!lock) {

                    lock = true;

                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        Log.w(LOG_TAG, "ModulesBroadcastReceiver sleep interruptedException " + e.getMessage());
                    }

                    if (modulesStatus.getMode() == ROOT_MODE && !modulesStatus.isUseModulesWithRoot()) {
                        modulesStatus.setIptablesRulesUpdateRequested(context, true);
                    }

                    lock = false;
                }

            });
        }
    }

    private void checkUSBModemState() {
        final String addressesRangeUSB = "192.168.42.";

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            Tethering.usbTetherOn = false;

            try {
                TimeUnit.SECONDS.sleep(3);

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
                        String hostAddress = inetAddress.getHostAddress();

                        if (hostAddress.contains(addressesRangeUSB)) {
                            Tethering.usbTetherOn = true;
                            String usbModemInterfaceName = intf.getName();
                            Log.i(LOG_TAG, "USB Modem interface name " + usbModemInterfaceName);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Tethering Exception " + e.getMessage() + " " + e.getCause());
            }

            if (Tethering.usbTetherOn && !new PrefManager(context).getBoolPref("ModemIsON")) {
                new PrefManager(context).setBoolPref("ModemIsON", true);
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
            } else if (!Tethering.usbTetherOn && new PrefManager(context).getBoolPref("ModemIsON")) {
                new PrefManager(context).setBoolPref("ModemIsON", false);
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
            }

            Log.i(LOG_TAG, "ModulesBroadcastReceiver USB modem state is " + (Tethering.usbTetherOn ? "ON" : "OFF"));
        });

    }

    private void resetArpScanner(boolean connectionAvailable) {
        if (arpScanner != null) {
            arpScanner.reset(connectionAvailable);
        }
    }

    private void resetArpScanner() {
        if (arpScanner != null && context != null) {
            arpScanner.reset(Util.isConnected(context));
        }
    }
}
