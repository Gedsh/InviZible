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

import static android.content.Context.CONNECTIVITY_SERVICE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.enums.VPNCommand;
import pan.alexander.tordnscrypt.vpn.Rule;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.modules.ModulesService.DEFAULT_NOTIFICATION_ID;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.VPNCommand.STOP;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_GSM_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_ROAMING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_ALLOW_WIFI_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ARP_SPOOFING_DETECTION;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_ENABLED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPN.EXTRA_COMMAND;
import static pan.alexander.tordnscrypt.vpn.service.ServiceVPN.EXTRA_REASON;

import javax.inject.Inject;
import javax.inject.Named;


public class ServiceVPNHandler extends Handler {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultSharedPreferences;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public Lazy<VpnBuilder> vpnBuilder;
    @Inject
    public Lazy<Handler> handler;

    private final List<Rule> listRule = new CopyOnWriteArrayList<>();
    @Nullable
    private final ServiceVPN serviceVPN;
    private ServiceVPN.Builder last_builder = null;

    private ServiceVPNHandler(Looper looper, @Nullable ServiceVPN serviceVPN) {
        super(looper);
        App.getInstance().getDaggerComponent().inject(this);
        this.serviceVPN = serviceVPN;
    }

    static ServiceVPNHandler getInstance(Looper looper, ServiceVPN serviceVPN) {
        return new ServiceVPNHandler(looper, serviceVPN);
    }

    void queue(Intent intent) {
        VPNCommand cmd = (VPNCommand) intent.getSerializableExtra(EXTRA_COMMAND);
        Message msg = obtainMessage();
        msg.obj = intent;
        if (cmd != null) {
            msg.what = cmd.ordinal();
            removeMessages(msg.what);
            if (cmd != STOP) {
                removeMessages(STOP.ordinal());
                sendMessage(msg);
            } else {
                sendMessageDelayed(msg, 3000);
            }
        }
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
        try {
            handleIntent((Intent) msg.obj);
        } catch (Throwable ex) {
            loge("ServiceVPNHandler handleMessage", ex, true);
        }
    }

    private void handleIntent(Intent intent) {

        if (serviceVPN == null) {
            return;
        }

        final SharedPreferences prefs = defaultSharedPreferences.get();

        VPNCommand cmd = (VPNCommand) intent.getSerializableExtra(EXTRA_COMMAND);
        String reason = intent.getStringExtra(EXTRA_REASON);

        logi("VPN Handler Executing intent=" + intent + " command=" + cmd + " reason=" + reason +
                " vpn=" + (serviceVPN.vpn != null) + " user=" + (pathVars.get().getAppUid() / 100000));

        try {
            if (cmd != null) {
                switch (cmd) {
                    case START -> start();
                    case RELOAD -> reload();
                    case STOP -> stop();
                    default -> loge("VPN Handler Unknown command=" + cmd);
                }
            }

            // Stop service if needed
            if (!hasMessages(VPNCommand.START.ordinal()) &&
                    !hasMessages(VPNCommand.RELOAD.ordinal()) &&
                    !prefs.getBoolean(VPN_SERVICE_ENABLED, false))
                stopServiceVPN();

            // Request garbage collection
            System.gc();
        } catch (Throwable ex) {
            loge("ServiceVPNHandler handleIntent", ex, true);

            serviceVPN.reloading = false;

            if (cmd == VPNCommand.START || cmd == VPNCommand.RELOAD) {
                if (VpnService.prepare(serviceVPN) == null) {
                    logw("VPN Handler prepared connected=" + serviceVPN.isNetworkAvailable());
                    if (serviceVPN.isNetworkAvailable() && !(ex instanceof StartFailedException)) {
                        serviceVPN.handler.get().post(() -> {
                            Toast.makeText(serviceVPN, serviceVPN.getText(R.string.vpn_mode_error), Toast.LENGTH_SHORT).show();
                        });
                    }
                    // Retried on connectivity change
                } else {
                    serviceVPN.handler.get().post(() -> {
                        Toast.makeText(serviceVPN, serviceVPN.getText(R.string.vpn_mode_error), Toast.LENGTH_SHORT).show();
                    });
                    // Disable firewall
                    if (!(ex instanceof StartFailedException)) {
                        prefs.edit().putBoolean(VPN_SERVICE_ENABLED, false).apply();
                    }
                }
            }
        }
    }

    private void start() {

        if (serviceVPN == null) {
            return;
        }

        if (serviceVPN.vpn == null) {

            listRule.clear();
            listRule.addAll(Rule.getRules(serviceVPN));
            List<String> listAllowed = getAllowedRules();

            last_builder = vpnBuilder.get().getBuilder(serviceVPN, listAllowed, listRule);
            serviceVPN.vpn = startVPN(last_builder);

            if (serviceVPN.vpn == null) {
                throw new StartFailedException("VPN Handler Start VPN Service Failed");
            }

            serviceVPN.startNative(serviceVPN.vpn, listAllowed);

            ModulesStatus modulesStatus = ModulesStatus.getInstance();
            if (modulesStatus.getMode() == OperationMode.VPN_MODE
                    && serviceVPN.vpnPreferences.getFirewallEnabled()) {
                modulesStatus.setFirewallState(RUNNING, preferenceRepository.get());
            } else {
                modulesStatus.setFirewallState(STOPPED, preferenceRepository.get());
            }
        }
    }

    private void reload() {

        if (serviceVPN == null) {
            return;
        }

        serviceVPN.reloading = true;

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        boolean fixTTL = modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();

        String oldVpnInterfaceName = "";
        if (fixTTL) {
            oldVpnInterfaceName = ModulesIptablesRules.blockTethering(serviceVPN, pathVars.get());
        }

        listRule.clear();
        listRule.addAll(Rule.getRules(serviceVPN));
        List<String> listAllowed = getAllowedRules();

        ServiceVPN.Builder builder = vpnBuilder.get().getBuilder(serviceVPN, listAllowed, listRule);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            last_builder = builder;
            logi("VPN Handler Legacy restart");

            if (serviceVPN.vpn != null) {
                serviceVPN.stopNative();
                stopVPN(serviceVPN.vpn);
                serviceVPN.vpn = null;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
            serviceVPN.vpn = startVPN(last_builder);

        } else {
            if (serviceVPN.vpn != null && builder.equals(last_builder)) {
                logi("VPN Handler Native restart");
                serviceVPN.stopNative();

                // Set underlying network
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setUnderlyingNetwork();
                }

            } else {
                last_builder = builder;

                SharedPreferences prefs = defaultSharedPreferences.get();
                boolean handover = prefs.getBoolean("VPN handover", true);
                logi("VPN Handler restart handover=" + handover);

                if (handover) {
                    // Attempt seamless handover
                    ParcelFileDescriptor prev = serviceVPN.vpn;
                    serviceVPN.vpn = startVPN(builder);

                    if (prev != null && serviceVPN.vpn == null) {
                        logw("VPN Handler Handover failed");
                        serviceVPN.stopNative();
                        stopVPN(prev);
                        prev = null;
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ignored) {
                        }
                        serviceVPN.vpn = startVPN(last_builder);
                        if (serviceVPN.vpn == null)
                            throw new IllegalStateException("VPN Handler Handover failed");
                    }

                    if (prev != null) {
                        serviceVPN.stopNative();
                        stopVPN(prev);
                    }
                } else {
                    if (serviceVPN.vpn != null) {
                        serviceVPN.stopNative();
                        stopVPN(serviceVPN.vpn);
                    }

                    serviceVPN.vpn = startVPN(builder);
                }
            }
        }

        if (serviceVPN.vpn == null)
            throw new StartFailedException("VPN Handler Start VPN Service Failed");

        serviceVPN.startNative(serviceVPN.vpn, listAllowed);

        if (fixTTL) {
            String finalOldVpnInterfaceName = oldVpnInterfaceName;
            postDelayed(() -> {
                modulesStatus.setFixTTLRulesUpdateRequested(serviceVPN, true);
                ModulesIptablesRules.allowTethering(serviceVPN, pathVars.get(), finalOldVpnInterfaceName);
            }, 1000);
        }

        serviceVPN.reloading = false;

        if (modulesStatus.getMode() == OperationMode.VPN_MODE) {
            if (modulesStatus.getFirewallState() == STARTING) {
                modulesStatus.setFirewallState(RUNNING, preferenceRepository.get());
            } else if (modulesStatus.getFirewallState() == STOPPING) {
                modulesStatus.setFirewallState(STOPPED, preferenceRepository.get());
            }
        } else {
            modulesStatus.setFirewallState(STOPPED, preferenceRepository.get());
        }

        if (defaultSharedPreferences.get().getBoolean(ARP_SPOOFING_DETECTION, false)) {
            try {
                ArpScanner.getArpComponent().get().reset(
                        serviceVPN.isNetworkAvailable() || serviceVPN.isInternetAvailable()
                );
            } catch (Exception e) {
                loge("ServiceVPNHandler Arp Scanner reset exception", e);
            }
        }
    }

    private void stop() {

        //This prevents the ModulesService from sending a stop signal when the service is already stopping
        handler.get().post(() -> defaultSharedPreferences.get()
                .edit()
                .putBoolean(VPN_SERVICE_ENABLED, false)
                .commit()
        );

        if (serviceVPN != null && serviceVPN.vpn != null) {
            try {
                serviceVPN.stopNative();
                stopVPN(serviceVPN.vpn);
                serviceVPN.vpn = null;
                serviceVPN.vpnRulesHolder.get().unPrepare();
                listRule.clear();
            } catch (Exception e) {
                loge("ServiceVPNHandler stop()", e);
            }
        }

        stopServiceVPN();
    }

    private List<String> getAllowedRules() {
        List<String> listAllowed = new ArrayList<>();

        if (serviceVPN == null) {
            return listAllowed;
        }

        //Update connected state
        ConnectionCheckerInteractor interactor = serviceVPN.connectionCheckerInteractor.get();
        interactor.checkNetworkConnection();

        //Request disconnected state confirmation in case of Always on VPN is enabled
        if (!serviceVPN.isInternetAvailable()) {
            interactor.checkInternetConnection();
        }

        //if (serviceVPN.isNetworkAvailable() || serviceVPN.isInternetAvailable()) {

        PreferenceRepository preferences = preferenceRepository.get();

        if (!preferences.getBoolPreference(FIREWALL_ENABLED)
                || ModulesStatus.getInstance().getMode() == ROOT_MODE) {
            for (Rule rule : listRule) {
                listAllowed.add(String.valueOf(rule.uid));
            }
        } else if (NetworkChecker.isWifiActive(serviceVPN) || NetworkChecker.isEthernetActive(serviceVPN)) {
            listAllowed.addAll(preferences.getStringSetPreference(APPS_ALLOW_WIFI_PREF));
        } else if (NetworkChecker.isRoaming(serviceVPN)) {
            listAllowed.addAll(preferences.getStringSetPreference(APPS_ALLOW_ROAMING));
        } else if (NetworkChecker.isCellularActive(serviceVPN)) {
            listAllowed.addAll(preferences.getStringSetPreference(APPS_ALLOW_GSM_PREF));
        }
        //}

        logi("VPN Handler Allowed " + listAllowed.size() + " of " + listRule.size());
        return listAllowed;
    }

    private ParcelFileDescriptor startVPN(ServiceVPN.Builder builder) throws SecurityException {
        try {
            ParcelFileDescriptor pfd = builder.establish();

            // Set underlying network
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setUnderlyingNetwork();
            }

            return pfd;
        } catch (SecurityException ex) {
            throw ex;
        } catch (Throwable ex) {
            loge("ServiceVPNHandler startVPN", ex, true);
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void setUnderlyingNetwork() {

        if (serviceVPN == null) {
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) serviceVPN.getSystemService(CONNECTIVITY_SERVICE);
        Network[] networks = NetworkChecker.getAvailableNetworksSorted(serviceVPN);
        if (networks.length > 1) {
            serviceVPN.setUnderlyingNetworks(networks);
            for (Network network : networks) {
                logi("VPN Handler Setting underlying network=" + cm.getNetworkInfo(network));
            }
        }/* else if (!serviceVPN.isNetworkAvailable() && !serviceVPN.isInternetAvailable()) {
            Unfortunately, this code causes the Telegram messenger always connecting.
            logi("VPN Handler Setting underlying network=empty");
            serviceVPN.setUnderlyingNetworks(new Network[]{});
        }*/ else {
            logi("VPN Handler Setting underlying network=default");
            serviceVPN.setUnderlyingNetworks(null);
        }
    }

    void stopVPN(ParcelFileDescriptor pfd) {
        logi("VPN Handler Stopping");
        try {
            pfd.close();
        } catch (IOException ex) {
            loge("ServiceVPNHandler stopVPN", ex, true);
        }
    }

    private void stopServiceVPN() {

        if (serviceVPN == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && serviceVPN.notificationManager != null) {
            try {
                serviceVPN.notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                serviceVPN.stopForeground(true);
            } catch (Exception e) {
                loge("ServiceVPNHandler stopServiceVPN", e);
            }
        }

        defaultSharedPreferences.get().edit().putBoolean(VPN_SERVICE_ENABLED, false).apply();

        serviceVPN.stopSelf();

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        if (modulesStatus.getMode() == OperationMode.VPN_MODE
                || modulesStatus.getMode() == OperationMode.PROXY_MODE) {
            modulesStatus.setFirewallState(STOPPED, preferenceRepository.get());
        }
        ModuleState dnsCryptState = modulesStatus.getDnsCryptState();
        ModuleState torState = modulesStatus.getTorState();
        ModuleState itpdState = modulesStatus.getItpdState();

        //If modules are running start ModulesService Foreground, which is background because of serviceVPN.stopSelf() with same notification id
        if (dnsCryptState != STOPPED || torState != STOPPED || itpdState != STOPPED) {
            ModulesAux.requestModulesStatusUpdate(serviceVPN);
        }
    }

    private static class StartFailedException extends IllegalStateException {
        StartFailedException(String msg) {
            super(msg);
        }
    }

    List<Rule> getAppsList() {
        return listRule;
    }
}
