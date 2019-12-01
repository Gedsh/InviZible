package pan.alexander.tordnscrypt.utils;
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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.preference.PreferenceManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class TorRefreshIPsWork {
    private Context context;
    private ArrayList<String> unlockHostsDevice;
    private ArrayList<String> unlockIPsDevice;
    private ArrayList<String> unlockHostsTether;
    private ArrayList<String> unlockIPsTether;
    private String appDataDir;
    private boolean torTethering;
    private boolean routeAllThroughTorDevice;
    private boolean routeAllThroughTorTether;
    private GetIPsJobService getIPsJobService;

    public TorRefreshIPsWork(Context context, GetIPsJobService getIPsJobService) {
        this.context = context;
        this.getIPsJobService = getIPsJobService;
    }

    private void getBridgesIP() {
        Runnable getBridgesIPRunnable = new Runnable() {
            @Override
            public void run() {
                //////////////TO GET BRIDGES WITH TOR//////////////////////////////////////
                ArrayList<String> bridgesIPlist = handleActionGetIP("https://bridges.torproject.org");
                //////////////TO GET UPDATES WITH TOR//////////////////////////////////////
                bridgesIPlist.addAll(handleActionGetIP("https://invizible.net"));
                FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/bridgesIP", bridgesIPlist, "ignored");
            }
        };
        Thread thread = new Thread(getBridgesIPRunnable);
        thread.start();
    }

    public void refreshIPs() {
        boolean rootIsAvailable = new PrefManager(context.getApplicationContext()).getBoolPref("rootIsAvailable");
        if (!rootIsAvailable) return;

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        torTethering = shPref.getBoolean("pref_common_tor_tethering", false);
        routeAllThroughTorDevice = shPref.getBoolean("pref_fast_all_through_tor", true);
        routeAllThroughTorTether = shPref.getBoolean("pref_common_tor_route_all", false);

        PathVars pathVars = new PathVars(context);
        appDataDir = pathVars.appDataDir;

        getBridgesIP();

        Set<String> setUnlockHosts;
        Set<String> setUnlockIPs;
        if (!routeAllThroughTorDevice) {
            setUnlockHosts = new PrefManager(Objects.requireNonNull(context)).getSetStrPref("unlockHosts");
            setUnlockIPs = new PrefManager(Objects.requireNonNull(context)).getSetStrPref("unlockIPs");
        } else {
            setUnlockHosts = new PrefManager(Objects.requireNonNull(context)).getSetStrPref("clearnetHosts");
            setUnlockIPs = new PrefManager(Objects.requireNonNull(context)).getSetStrPref("clearnetIPs");
        }
        unlockHostsDevice = new ArrayList<>(setUnlockHosts);
        unlockIPsDevice = new ArrayList<>(setUnlockIPs);

        if (!routeAllThroughTorTether) {
            setUnlockHosts = new PrefManager(Objects.requireNonNull(context)).getSetStrPref("unlockHostsTether");
            setUnlockIPs = new PrefManager(Objects.requireNonNull(context)).getSetStrPref("unlockIPsTether");
        } else {
            setUnlockHosts = new PrefManager(Objects.requireNonNull(context)).getSetStrPref("clearnetHostsTether");
            setUnlockIPs = new PrefManager(Objects.requireNonNull(context)).getSetStrPref("clearnetIPsTether");
        }
        unlockHostsTether = new ArrayList<>(setUnlockHosts);
        unlockIPsTether = new ArrayList<>(setUnlockIPs);

        performBackgroundWork();
    }

    private void performBackgroundWork() {

        Runnable performWorkRunnable = new Runnable() {
            @Override
            public void run() {
                Log.i(LOG_TAG, "TorRefreshIPsWork performBackgroundWork");

                if (!unlockHostsDevice.isEmpty() || !unlockIPsDevice.isEmpty()) {

                    List<String> unlockIPsReadyDevice = universalGetIPs(unlockHostsDevice, unlockIPsDevice);

                    if (unlockIPsReadyDevice == null) {
                        unlockIPsReadyDevice = new LinkedList<>();
                        unlockIPsReadyDevice.add("");
                    }

                    List<String> unlockIPsReadyTether = universalGetIPs(unlockHostsTether, unlockIPsTether);

                    if (unlockIPsReadyTether == null) {
                        unlockIPsReadyTether = new LinkedList<>();
                        unlockIPsReadyTether.add("");
                    }


                    if (!routeAllThroughTorDevice) {
                        FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/unlock", unlockIPsReadyDevice, "ignored");
                    } else {
                        FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/clearnet", unlockIPsReadyDevice, "ignored");
                    }

                    if (torTethering) {
                        if (!routeAllThroughTorTether) {
                            FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/unlock_tether", unlockIPsReadyTether, "ignored");
                        } else {
                            FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/clearnet_tether", unlockIPsReadyTether, "ignored");
                        }
                    }
                }

                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "TorRefreshIPsWork interrupt exception " + e.getMessage() + " " + e.getCause());
                }

                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                ModulesAux.requestModulesStatusUpdate(context);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && getIPsJobService != null) {
                    getIPsJobService.finishJob();
                }
            }
        };
        Thread thread = new Thread(performWorkRunnable);
        thread.start();
    }


    private List<String> universalGetIPs(ArrayList<String> hosts, ArrayList<String> IPs) {


        ArrayList<String> unlockIPsPrepared = new ArrayList<>();
        List<String> IPsReady = new LinkedList<>();

        if (hosts != null) {
            for (String host : hosts) {
                if (!host.startsWith("#")) {
                    ArrayList<String> preparedIPs = handleActionGetIP(host);
                    unlockIPsPrepared.addAll(preparedIPs);
                }
            }

            for (String unlockIPprepared : unlockIPsPrepared) {
                if (unlockIPprepared.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}"))
                    IPsReady.add(unlockIPprepared);
            }
        }

        if (IPs != null) {
            for (String unlockIP : IPs) {
                if (!unlockIP.startsWith("#")) {
                    IPsReady.add(unlockIP);
                }
            }

        }

        if (IPsReady.isEmpty())
            return null;

        return IPsReady;
    }

    private ArrayList<String> handleActionGetIP(String host) {
        ArrayList<String> preparedIPs = new ArrayList<>();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(new URL(host).getHost());
            for (InetAddress address : addresses) {
                preparedIPs.add(address.getHostAddress());
            }
        } catch (UnknownHostException | MalformedURLException e) {
            Log.e(LOG_TAG, "TorRefreshIPsWork handleActionGetIP exception " + e.getMessage() + " " + e.getCause());
        }
        return preparedIPs;
    }
}
