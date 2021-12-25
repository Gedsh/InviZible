package pan.alexander.tordnscrypt.utils.web;
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.preference.PreferenceManager;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractor;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;

import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_FOR_CLEARNET;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_FOR_CLEARNET_TETHER;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_TO_UNLOCK;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_TO_UNLOCK_TETHER;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

import javax.inject.Inject;

public class TorRefreshIPsWork {

    private final static long DELAY_ERROR_RETRY_MSEC = 500;

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<DnsInteractor> dnsInteractor;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    public CachedExecutor cachedExecutor;

    private final Pattern IP_PATTERN = Pattern.compile(IPv4_REGEX);

    private final Context context;
    private final GetIPsJobService getIPsJobService;

    private boolean exceptionWhenResolvingHost = false;

    public TorRefreshIPsWork(Context context, GetIPsJobService getIPsJobService) {
        App.getInstance().getDaggerComponent().inject(this);
        this.context = context;
        this.getIPsJobService = getIPsJobService;
    }

    public void refreshIPs() {
        cachedExecutor.submit(() -> {

            Log.i(LOG_TAG, "TorRefreshIPsWork refreshIPs");

            try {
                updateData();
            } catch (Exception e) {
                Log.e(LOG_TAG, "TorRefreshIPsWork performBackgroundWork exception " + e.getMessage()
                        + " " + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()));
            }

        });
    }

    private void updateData() {

        if (context == null) {
            return;
        }

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean torTethering = shPref.getBoolean(TOR_TETHERING, false);
        boolean routeAllThroughTorDevice = shPref.getBoolean(ALL_THROUGH_TOR, true);
        boolean routeAllThroughTorTether = shPref.getBoolean("pref_common_tor_route_all", false);

        boolean settingsChanged = false;

        if (updateDeviceData(routeAllThroughTorDevice)) {
            settingsChanged = true;
        }

        if (torTethering && updateTetheringData(routeAllThroughTorTether)) {
            settingsChanged = true;
        }

        if (settingsChanged) {
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getIPsJobService != null) {
            handler.get().post(() -> getIPsJobService.finishJob(exceptionWhenResolvingHost));
        }

    }


    private boolean updateDeviceData(boolean routeAllThroughTorDevice) {
        PreferenceRepository preferences = preferenceRepository.get();
        Set<String> setUnlockHostsDevice;
        Set<String> setUnlockIPsDevice;
        if (!routeAllThroughTorDevice) {
            setUnlockHostsDevice = preferences.getStringSetPreference("unlockHosts");
            setUnlockIPsDevice = preferences.getStringSetPreference("unlockIPs");
        } else {
            setUnlockHostsDevice = preferences.getStringSetPreference("clearnetHosts");
            setUnlockIPsDevice = preferences.getStringSetPreference("clearnetIPs");
        }

        if (setUnlockHostsDevice.isEmpty() && setUnlockIPsDevice.isEmpty()) {
            return false;
        }

        boolean settingsChanged;

        Set<String> unlockIPsReadyDevice = universalGetIPs(setUnlockHostsDevice, setUnlockIPsDevice);

        if (unlockIPsReadyDevice.isEmpty()) {
            return false;
        }

        if (!routeAllThroughTorDevice) {
            settingsChanged = saveSettings(unlockIPsReadyDevice, IPS_TO_UNLOCK);
        } else {
            settingsChanged = saveSettings(unlockIPsReadyDevice, IPS_FOR_CLEARNET);
        }

        return settingsChanged;
    }

    private boolean updateTetheringData(boolean routeAllThroughTorTether) {
        PreferenceRepository preferences = preferenceRepository.get();
        Set<String> setUnlockHostsTether;
        Set<String> setUnlockIPsTether;
        if (!routeAllThroughTorTether) {
            setUnlockHostsTether = preferences.getStringSetPreference("unlockHostsTether");
            setUnlockIPsTether = preferences.getStringSetPreference("unlockIPsTether");
        } else {
            setUnlockHostsTether = preferences.getStringSetPreference("clearnetHostsTether");
            setUnlockIPsTether = preferences.getStringSetPreference("clearnetIPsTether");
        }

        if (setUnlockHostsTether.isEmpty() && setUnlockIPsTether.isEmpty()) {
            return false;
        }

        boolean settingsChanged;

        Set<String> unlockIPsReadyTether = universalGetIPs(setUnlockHostsTether, setUnlockIPsTether);

        if (unlockIPsReadyTether.isEmpty()) {
            return false;
        }

        if (!routeAllThroughTorTether) {
            settingsChanged = saveSettings(unlockIPsReadyTether, IPS_TO_UNLOCK_TETHER);
        } else {
            settingsChanged = saveSettings(unlockIPsReadyTether, IPS_FOR_CLEARNET_TETHER);
        }

        return settingsChanged;
    }

    private Set<String> universalGetIPs(Set<String> hosts, Set<String> ips) {


        Set<String> unlockIPsPrepared = new HashSet<>();
        Set<String> IPsReady = new HashSet<>();

        if (hosts != null) {
            for (String host : hosts) {
                if (!host.startsWith("#")) {
                    ArrayList<String> preparedIPs = handleActionGetIP(host);
                    unlockIPsPrepared.addAll(preparedIPs);
                }
            }

            for (String unlockIPPrepared : unlockIPsPrepared) {
                Matcher matcher = IP_PATTERN.matcher(unlockIPPrepared);
                if (matcher.find()) {
                    IPsReady.add(unlockIPPrepared);
                }
            }
        }

        if (ips != null) {
            for (String unlockIP : ips) {
                Matcher matcher = IP_PATTERN.matcher(unlockIP);
                if (matcher.find()) {
                    IPsReady.add(unlockIP);
                }
            }

        }

        return IPsReady;
    }

    private ArrayList<String> handleActionGetIP(String host) {
        ArrayList<String> preparedIPs = new ArrayList<>();
        try {
            preparedIPs.addAll(dnsInteractor.get().resolveDomain(host));
        } catch (Exception ignored) {
            try {
                TimeUnit.MILLISECONDS.sleep(DELAY_ERROR_RETRY_MSEC);
                preparedIPs.addAll(dnsInteractor.get().resolveDomain(host));
            } catch (Exception e) {
                exceptionWhenResolvingHost = true;
                Log.e(LOG_TAG, "TorRefreshIPsWork get " + host + " exception "
                        + e.getMessage() + "\n" + e.getCause());
            }
        }
        return preparedIPs;
    }

    private boolean saveSettings(Set<String> ipsToUnlock, String settingsKey) {
        Set<String> ips = preferenceRepository.get().getStringSetPreference(settingsKey);
        if (ips.size() == ipsToUnlock.size() && ips.containsAll(ipsToUnlock)) {
            return false;
        } else {
            preferenceRepository.get().setStringSetPreference(settingsKey, ipsToUnlock);
            return true;
        }
    }
}
