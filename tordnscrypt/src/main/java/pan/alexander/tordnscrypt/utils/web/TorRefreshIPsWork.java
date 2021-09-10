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
import android.os.Looper;
import android.util.Log;

import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;

import static pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFrag.IPS_FOR_CLEARNET;
import static pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFrag.IPS_FOR_CLEARNET_TETHER;
import static pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFrag.IPS_TO_UNLOCK;
import static pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFrag.IPS_TO_UNLOCK_TETHER;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

public class TorRefreshIPsWork {
    private final Pattern IP_PATTERN = Pattern.compile("^[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}$");

    private final Context context;
    private final GetIPsJobService getIPsJobService;
    private final Lazy<PreferenceRepository> preferenceRepository;

    public TorRefreshIPsWork(Context context, GetIPsJobService getIPsJobService) {
        this.context = context;
        this.getIPsJobService = getIPsJobService;
        this.preferenceRepository = App.instance.daggerComponent.getPreferenceRepository();
    }

    public void refreshIPs() {
        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {

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
        boolean torTethering = shPref.getBoolean("pref_common_tor_tethering", false);
        boolean routeAllThroughTorDevice = shPref.getBoolean("pref_fast_all_through_tor", true);
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
            Looper looper = Looper.getMainLooper();
            Handler handler = new Handler(looper);
            handler.post(getIPsJobService::finishJob);
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
            //FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/unlock_tether", new ArrayList<>(unlockIPsReadyTether), "ignored");
        } else {
            settingsChanged = saveSettings(unlockIPsReadyTether, IPS_FOR_CLEARNET_TETHER);
            //FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/clearnet_tether", new ArrayList<>(unlockIPsReadyTether), "ignored");
        }

        return settingsChanged;
    }

    private Set<String> universalGetIPs(Set<String> hosts, Set<String> IPs) {


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

        if (IPs != null) {
            for (String unlockIP : IPs) {
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
            InetAddress[] addresses = InetAddress.getAllByName(new URL(host).getHost());
            for (InetAddress address : addresses) {
                preparedIPs.add(address.getHostAddress());
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "TorRefreshIPsWork handleActionGetIP exception " + e.getMessage() + " " + e.getCause());
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
