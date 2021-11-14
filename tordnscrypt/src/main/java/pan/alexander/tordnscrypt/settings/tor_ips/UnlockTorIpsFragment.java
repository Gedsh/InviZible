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

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.Constants.IP_REGEX;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_FOR_CLEARNET;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_FOR_CLEARNET_TETHER;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_TO_UNLOCK;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_TO_UNLOCK_TETHER;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

import javax.inject.Inject;

public class UnlockTorIpsFragment extends Fragment {

    private final static String DEVICE_OR_TETHER_KEY = "deviceOrTether";
    private final static String DEVICE_VALUE = "device";
    private final static String TETHER_VALUE = "tether";

    RecyclerView rvListHostIP;
    RecyclerView.Adapter<DomainIpAdapter.DomainIpViewHolder> rvAdapter;
    FloatingActionButton floatingBtnAddTorIPs;

    final List<DomainIpEntity> domainIps = new ArrayList<>();

    boolean routeAllThroughTorDevice = true;
    boolean routeAllThroughTorTether = false;

    String deviceOrTether = "";
    String unlockHostsStr;
    String unlockIPsStr;

    @Inject
    public Lazy<CoroutineExecutor> coroutineExecutor;
    @Inject
    public CachedExecutor cachedExecutor;

    public UnlockTorIpsViewModel viewModel;

    public UnlockTorIpsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);

        super.onCreate(savedInstanceState);

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
            deviceOrTether = getArguments().getString(DEVICE_OR_TETHER_KEY);
        }

        if (deviceOrTether == null) {
            return;
        }

        if (deviceOrTether.equals(DEVICE_VALUE)) {
            if (!routeAllThroughTorDevice) {
                activity.setTitle(R.string.pref_tor_unlock);
                unlockHostsStr = "unlockHosts";
                unlockIPsStr = "unlockIPs";
            } else {
                activity.setTitle(R.string.pref_tor_clearnet);
                unlockHostsStr = "clearnetHosts";
                unlockIPsStr = "clearnetIPs";
            }
        } else if (deviceOrTether.equals(TETHER_VALUE)) {
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

        cachedExecutor.submit(() -> {
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

        viewModel = new ViewModelProvider(this).get(UnlockTorIpsViewModel.class);

        View view = inflater.inflate(R.layout.fragment_preferences_tor_ips, container, false);

        initViews(view);

        initRecycler();

        return view;
    }

    private void initViews(View view) {
        rvListHostIP = view.findViewById(R.id.rvTorIPs);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(view.getContext());
        rvListHostIP.setLayoutManager(mLayoutManager);

        floatingBtnAddTorIPs = view.findViewById(R.id.floatingbtnAddTorIPs);
        floatingBtnAddTorIPs.setAlpha(0.8f);
        floatingBtnAddTorIPs.setOnClickListener(v -> {
            DialogAddDomainIp dialogAddHostIP = new DialogAddDomainIp(
                    new WeakReference<>(this),
                    R.style.CustomAlertDialogTheme
            );
            dialogAddHostIP.show();
        });
        floatingBtnAddTorIPs.requestFocus();
    }

    private void initRecycler() {
        rvAdapter = new DomainIpAdapter(this);
        rvListHostIP.setAdapter(rvAdapter);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState == null) {
            getDomainIps();
        }

        observeResolvedDomainIps();
    }

    private void getDomainIps() {
        viewModel.getDomainIps(
                unlockHostsStr,
                unlockIPsStr,
                getString(R.string.please_wait),
                getString(R.string.pref_fast_unlock_host_wrong)
        );
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

        for (DomainIpEntity domainIp : domainIps) {
            if (domainIp.isActive()) {
                if (domainIp instanceof DomainEntity) {
                    for (String ip : ((DomainEntity) domainIp).getIps()) {
                        if (ip.matches(IP_REGEX))
                            ipsToUnlock.add(ip);
                    }
                } else if (domainIp instanceof IpEntity) {
                    String ip = ((IpEntity) domainIp).getIp();
                    if (ip.matches(IP_REGEX))
                        ipsToUnlock.add(ip);
                }
                unlockHostIPContainsActive = true;
            }
        }

        if (domainIps.size() > 0 && ipsToUnlock.isEmpty() && unlockHostIPContainsActive) {
            return;
        }

        boolean settingsChanged = false;


        //////////////////////////////////////////////////////////////////////////////////////
        //////////////When open this fragment to add sites for internal applications/////////
        /////////////////////////////////////////////////////////////////////////////////////

        if (DEVICE_VALUE.equals(deviceOrTether)) {
            if (!routeAllThroughTorDevice) {
                settingsChanged = viewModel.saveDomainIpsToPreferences(ipsToUnlock, IPS_TO_UNLOCK);
            } else {
                settingsChanged = viewModel.saveDomainIpsToPreferences(ipsToUnlock, IPS_FOR_CLEARNET);
            }

            //////////////////////////////////////////////////////////////////////////////////////
            //////////////When open this fragment to add sites for external tether devices/////////
            /////////////////////////////////////////////////////////////////////////////////////
        } else if (TETHER_VALUE.equals(deviceOrTether)) {
            if (!routeAllThroughTorTether) {
                settingsChanged = viewModel.saveDomainIpsToPreferences(ipsToUnlock, IPS_TO_UNLOCK_TETHER);
            } else {
                settingsChanged = viewModel.saveDomainIpsToPreferences(ipsToUnlock, IPS_FOR_CLEARNET_TETHER);
            }
        }

        if (!settingsChanged) {
            return;
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        modulesStatus.setIptablesRulesUpdateRequested(context, true);

        Toast.makeText(context, getText(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show();
    }

    private void observeResolvedDomainIps() {
        viewModel.getDomainIpLiveData().observe(getViewLifecycleOwner(), domainIps -> {
            this.domainIps.clear();
            this.domainIps.addAll(domainIps);
            rvAdapter.notifyDataSetChanged();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        destroyViews();
    }

    private void destroyViews() {
        rvListHostIP = null;
        rvAdapter = null;
        floatingBtnAddTorIPs = null;
    }

    public static UnlockTorIpsFragment getInstance(DeviceOrTether deviceOrTether) {

        Bundle bundle = new Bundle();
        switch (deviceOrTether) {
            case DEVICE:
                bundle.putString(DEVICE_OR_TETHER_KEY, DEVICE_VALUE);
                break;
            case TETHER:
                bundle.putString(DEVICE_OR_TETHER_KEY, TETHER_VALUE);
                break;
        }

        UnlockTorIpsFragment unlockTorIpsFragment = new UnlockTorIpsFragment();
        unlockTorIpsFragment.setArguments(bundle);

        return  unlockTorIpsFragment;
    }

    public enum DeviceOrTether {
        DEVICE,
        TETHER
    }
}
