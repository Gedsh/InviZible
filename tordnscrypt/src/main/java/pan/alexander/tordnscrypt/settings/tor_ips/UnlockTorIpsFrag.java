package pan.alexander.tordnscrypt.settings.tor_ips;
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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class UnlockTorIpsFrag extends Fragment {

    public static final String IPS_TO_UNLOCK = "ipsToUnlock";
    public static final String IPS_FOR_CLEARNET = "ipsForClearNet";
    public static final String IPS_TO_UNLOCK_TETHER = "ipsToUnlockTether";
    public static final String IPS_FOR_CLEARNET_TETHER = "ipsForClearNetTether";

    RecyclerView rvListHostIP;
    RecyclerView.Adapter<HostIPAdapter.HostIPViewHolder> rvAdapter;
    FloatingActionButton floatingBtnAddTorIPs;

    private final ArrayList<String> unlockHosts = new ArrayList<>();
    private final ArrayList<String> unlockIPs = new ArrayList<>();
    final ArrayList<HostIP> unlockHostIP = new ArrayList<>();

    boolean routeAllThroughTorDevice = true;
    boolean routeAllThroughTorTether = false;

    String deviceOrTether = "";
    String unlockHostsStr;
    String unlockIPsStr;

    private Future<?> getHostIPTask;

    public UnlockTorIpsFrag() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        ////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////Reverse logic when route all through Tor!///////////////////
        //////////////////////////////////////////////////////////////////////////////////
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(activity);
        routeAllThroughTorDevice = shPref.getBoolean("pref_fast_all_through_tor", true);
        routeAllThroughTorTether = shPref.getBoolean("pref_common_tor_route_all", false);

        if (getArguments() != null) {
            deviceOrTether = getArguments().getString("deviceOrTether");
        }

        if (deviceOrTether == null) {
            return;
        }

        if (deviceOrTether.equals("device")) {
            if (!routeAllThroughTorDevice) {
                activity.setTitle(R.string.pref_tor_unlock);
                unlockHostsStr = "unlockHosts";
                unlockIPsStr = "unlockIPs";
            } else {
                activity.setTitle(R.string.pref_tor_clearnet);
                unlockHostsStr = "clearnetHosts";
                unlockIPsStr = "clearnetIPs";
            }
        } else if (deviceOrTether.equals("tether")) {
            if (!routeAllThroughTorTether) {
                activity.setTitle(R.string.pref_tor_unlock);
                unlockHostsStr = "unlockHostsTether";
                unlockIPsStr = "unlockIPsTether";
            } else {
                activity.setTitle(R.string.pref_tor_clearnet);
                unlockHostsStr = "clearnetHostsTether";
                unlockIPsStr = "clearnetIPsTether";
            }
        }

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                Verifier verifier = new Verifier(activity);
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            activity, getString(R.string.verifier_error), "123");
                    if (notificationHelper != null && isAdded()) {
                        notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        activity, getString(R.string.verifier_error), "168");
                if (notificationHelper != null && isAdded()) {
                    notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                }
                Log.e(LOG_TAG, "UnlockTorIpsFrag fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_preferences_tor_ips, container, false);

        rvListHostIP = view.findViewById(R.id.rvTorIPs);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(view.getContext());
        rvListHostIP.setLayoutManager(mLayoutManager);

        floatingBtnAddTorIPs = view.findViewById(R.id.floatingbtnAddTorIPs);
        floatingBtnAddTorIPs.setAlpha(0.8f);
        floatingBtnAddTorIPs.setOnClickListener(v -> {
            DialogAddHostIP dialogAddHostIP = new DialogAddHostIP(view.getContext(), this, R.style.CustomAlertDialogTheme);
            dialogAddHostIP.show();
        });
        floatingBtnAddTorIPs.requestFocus();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        unlockHosts.clear();
        unlockHosts.addAll(new PrefManager(activity).getSetStrPref(unlockHostsStr));
        unlockIPs.clear();
        unlockIPs.addAll(new PrefManager(activity).getSetStrPref(unlockIPsStr));

        unlockHostIP.clear();
        for (String host : unlockHosts) {
            String hostClear = host.replace("#", "");
            unlockHostIP.add(new HostIP(hostClear, getString(R.string.please_wait), true, false, !host.trim().startsWith("#")));
        }
        for (String IPs : unlockIPs) {
            String IPsClear = IPs.replace("#", "");
            unlockHostIP.add(new HostIP(getString(R.string.please_wait), IPsClear, false, true, !IPs.trim().startsWith("#")));
        }

        rvAdapter = new HostIPAdapter(this);
        rvListHostIP.setAdapter(rvAdapter);

        GetHostIP getHostIPRunnable = new GetHostIP(this, unlockHostIP);
        getHostIPTask = CachedExecutor.INSTANCE.getExecutorService().submit(getHostIPRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (getHostIPTask != null && !getHostIPTask.isCancelled()) {
            getHostIPTask.cancel(true);
            getHostIPTask = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();
        if (context == null) {
            return;
        }

        boolean unlockHostIPContainsActive = false;

        Set<String> ipsToUnlock = new HashSet<>();
        for (int i = 0; i < unlockHostIP.size(); i++) {
            if (unlockHostIP.get(i).active) {
                String[] arr = unlockHostIP.get(i).IP.split(", ");
                for (String ip : arr) {
                    if (ip.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}"))
                        ipsToUnlock.add(ip);
                }
                unlockHostIPContainsActive = true;
            }
        }

        if (unlockHostIP.size() > 0 && ipsToUnlock.isEmpty() && unlockHostIPContainsActive) {
            return;
        }

        boolean settingsChanged = false;


        //////////////////////////////////////////////////////////////////////////////////////
        //////////////When open this fragment to add sites for internal applications/////////
        /////////////////////////////////////////////////////////////////////////////////////

        if ("device".equals(deviceOrTether)) {
            if (!routeAllThroughTorDevice) {
                settingsChanged = saveSettings(context, ipsToUnlock, IPS_TO_UNLOCK);
                //FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/unlock", ipsToUnlock, "ignored");
            } else {
                settingsChanged = saveSettings(context, ipsToUnlock, IPS_FOR_CLEARNET);
                //FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/clearnet", ipsToUnlock, "ignored");
            }

            //////////////////////////////////////////////////////////////////////////////////////
            //////////////When open this fragment to add sites for external tether devices/////////
            /////////////////////////////////////////////////////////////////////////////////////
        } else if ("tether".equals(deviceOrTether)) {
            if (!routeAllThroughTorTether) {
                settingsChanged = saveSettings(context, ipsToUnlock, IPS_TO_UNLOCK_TETHER);
                //FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/unlock_tether", new ArrayList<>(ipsToUnlock), "ignored");
            } else {
                settingsChanged = saveSettings(context, ipsToUnlock, IPS_FOR_CLEARNET_TETHER);
                //FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/clearnet_tether", new ArrayList<>(ipsToUnlock), "ignored");
            }
        }

        if (!settingsChanged) {
            return;
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        modulesStatus.setIptablesRulesUpdateRequested(context, true);

        Toast.makeText(context, getText(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show();
    }

    private boolean saveSettings(Context context, Set<String> ipsToUnlock, String settingsKey) {
        Set<String> ips = new PrefManager(context).getSetStrPref(settingsKey);
        if (ips.size() == ipsToUnlock.size() && ips.containsAll(ipsToUnlock)) {
            return false;
        } else {
            new PrefManager(context).setSetStrPref(settingsKey, ipsToUnlock);
            return true;
        }
    }

    synchronized String genIPByHost(String host) throws MalformedURLException, UnknownHostException {
        InetAddress[] addresses = InetAddress.getAllByName(new URL(host).getHost());
        StringBuilder sb = new StringBuilder();
        for (InetAddress address : addresses) {
            sb.append(address.getHostAddress()).append(", ");
        }

        return sb.substring(0, sb.length() - 2);
    }

    synchronized String getHostByIP(String ip) throws UnknownHostException {
        InetAddress addr = InetAddress.getByName(ip);
        return addr.getCanonicalHostName();
    }
}
