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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
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
import java.util.Arrays;

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
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

import javax.inject.Inject;

public class UnlockTorIpsFragment extends Fragment {

    private final static String DEVICE_OR_TETHER_KEY = "deviceOrTether";
    final static String DEVICE_VALUE = "device";
    final static String TETHER_VALUE = "tether";

    RecyclerView rvListHostIP;
    DomainIpAdapter domainIpAdapter;
    FloatingActionButton floatingBtnAddTorIPs;


    @Inject
    public Lazy<CoroutineExecutor> coroutineExecutor;
    @Inject
    public CachedExecutor cachedExecutor;
    @Inject
    ViewModelProvider.Factory viewModelFactory;

    public UnlockTorIpsViewModel viewModel;

    public UnlockTorIpsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this, viewModelFactory).get(UnlockTorIpsViewModel.class);

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        ////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////Reverse logic when route all through Tor!///////////////////
        //////////////////////////////////////////////////////////////////////////////////
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean routeAllThroughTorDevice = shPref.getBoolean(ALL_THROUGH_TOR, true);
        boolean routeAllThroughTorTether = shPref.getBoolean("pref_common_tor_route_all", false);

        String deviceOrTether = null;
        if (getArguments() != null) {
            deviceOrTether = getArguments().getString(DEVICE_OR_TETHER_KEY);
        }

        if (deviceOrTether == null) {
            return;
        }

        if (savedInstanceState == null) {
            viewModel.defineAppropriatePreferenceKeys(
                    deviceOrTether,
                    routeAllThroughTorDevice,
                    routeAllThroughTorTether
            );
        }

        setTitle(
                activity,
                deviceOrTether,
                routeAllThroughTorDevice,
                routeAllThroughTorTether
        );

        cachedExecutor.submit(() -> {
            try {
                Verifier verifier = new Verifier(activity);
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            activity, getString(R.string.verifier_error), "123");
                    if (notificationHelper != null && isAdded()) {
                        activity.runOnUiThread(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        activity, getString(R.string.verifier_error), "168");
                if (notificationHelper != null && isAdded()) {
                    activity.runOnUiThread(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                }
                Log.e(LOG_TAG, "UnlockTorIpsFrag fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });
    }

    private void setTitle(
            Activity activity,
            String deviceOrTether,
            boolean routeAllThroughTorDevice,
            boolean routeAllThroughTorTether
    ) {
        if (deviceOrTether.equals(DEVICE_VALUE)) {
            if (!routeAllThroughTorDevice) {
                activity.setTitle(R.string.pref_tor_unlock);
            } else {
                activity.setTitle(R.string.pref_tor_clearnet);
            }
        } else if (deviceOrTether.equals(TETHER_VALUE)) {
            if (!routeAllThroughTorTether) {
                activity.setTitle(R.string.pref_tor_unlock);
            } else {
                activity.setTitle(R.string.pref_tor_clearnet);
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

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
        domainIpAdapter = new DomainIpAdapter(this);
        rvListHostIP.setAdapter(domainIpAdapter);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState == null) {
            viewModel.getDomainIps();
        }

        observeResolvedDomainIps();
    }

    @Override
    public void onStop() {
        super.onStop();

        Activity activity = getActivity();
        if (activity == null || activity.isChangingConfigurations()) {
            return;
        }

        if (viewModel.saveDomainIps()) {
            ModulesStatus modulesStatus = ModulesStatus.getInstance();
            modulesStatus.setIptablesRulesUpdateRequested(activity, true);

            Toast.makeText(activity, getText(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show();
        }
    }

    private void observeResolvedDomainIps() {
        viewModel.getDomainIpLiveData().observe(getViewLifecycleOwner(), domainIps ->
                domainIpAdapter.updateDomainIps(domainIps));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        destroyViews();
    }

    private void destroyViews() {
        rvListHostIP = null;
        domainIpAdapter = null;
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

        return unlockTorIpsFragment;
    }

    public enum DeviceOrTether {
        DEVICE,
        TETHER
    }
}
