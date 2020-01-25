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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

public class ModulesBroadcastReceiver extends BroadcastReceiver {

    private Context context;
    private boolean receiverRegistered = false;
    private Object networkCallback;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private final ReentrantLock reentrantLock = new ReentrantLock();

    public ModulesBroadcastReceiver(Context context) {
        this.context = context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) {
            return;
        }

        if (intent.getAction().equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            Log.i(LOG_TAG, "ModulesBroadcastReceiver Received " + intent);

            idleStateChanged(context);

        } else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            connectivityStateChanged(intent);
        }
    }

    void registerReceivers() {
        registerIdleStateChanged();
        registerConnectivityChanges();
    }

    void unregisterReceivers() {
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

            @Override
            public void onAvailable(@NonNull Network network) {
                Log.i(LOG_TAG, "ModulesBroadcastReceiver Available network=" + network);
                updateIptablesRules();
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
                    updateIptablesRules();
                }
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                Log.i(LOG_TAG, "ModulesBroadcastReceiver Changed capabilities=" + network);
                updateIptablesRules();
            }

            @Override
            public void onLost(@NonNull Network network) {
                Log.i(LOG_TAG, "ModulesBroadcastReceiver Lost network=" + network);
                updateIptablesRules();
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
            updateIptablesRules();
        }
    }

    private void connectivityStateChanged(Intent intent) {
        // Reload rules
        Log.i(LOG_TAG, "ModulesBroadcastReceiver Received " + intent);
        updateIptablesRules();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void unlistenNetworkChanges() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            cm.unregisterNetworkCallback((ConnectivityManager.NetworkCallback) networkCallback);
        }
    }

    private void updateIptablesRules() {

        if (modulesStatus.getMode() == ROOT_MODE
                && !modulesStatus.isUseModulesWithRoot()
                && !reentrantLock.isLocked()) {

            new Thread(()->{
                if (!reentrantLock.isLocked()) {

                    reentrantLock.lock();

                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException e) {
                        Log.w(LOG_TAG, "ModulesBroadcastReceiver sleep interruptedException " + e.getMessage());
                    }

                    if (modulesStatus.getMode() == ROOT_MODE
                            && !modulesStatus.isUseModulesWithRoot()
                            && !reentrantLock.isLocked()) {
                        modulesStatus.setIptablesRulesUpdateRequested(true);
                    }

                    reentrantLock.unlock();
                }

            }).start();
        }
    }
}
