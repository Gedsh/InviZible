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

package pan.alexander.tordnscrypt.settings.dnscrypt_relays;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.PathVars;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import javax.inject.Inject;


public class PreferencesDNSCryptRelays extends Fragment
        implements OnRelayPingListener, DnsRelaysAdapter.OnRelayPingMeasure {

    public static String RELAY_TYPE_ARG = "pan.alexander.tordnscrypt.RELAY_TYPE_ARG";
    public static String SERVER_NAME_ARG = "pan.alexander.tordnscrypt.SERVER_NAME_ARG";
    public static String IPV6_SERVER_ARG = "pan.alexander.tordnscrypt.IPV6_SERVER_ARG";
    public static String ROUTES_ARG = "pan.alexander.tordnscrypt.ROUTES_ARG";

    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public ViewModelProvider.Factory viewModelFactory;

    private DnsRelayViewModel viewModel;
    private RecyclerView rvDNSRelay;
    private DnsRelaysAdapter adapter;
    private LinearProgressIndicator pbDnsCryptRelays;

    public PreferencesDNSCryptRelays() {
    }

    public interface OnRoutesChangeListener {
        void onRoutesChange(List<DnsServerRelay> routes, String currentServer);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this, viewModelFactory).get(DnsRelayViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        activity.setTitle(R.string.pref_dnscrypt_relays_title);

        View view;
        try {
            view = inflater.inflate(R.layout.fragment_preferences_dnscrypt_relays, container, false);
        } catch (Exception e) {
            loge("PreferencesDNSCryptRelays onCreateView", e);
            throw e;
        }

        pbDnsCryptRelays = view.findViewById(R.id.pbDnsCryptRelays);
        rvDNSRelay = view.findViewById(R.id.rvDNSRelays);

        RecyclerView.LayoutManager manager = new LinearLayoutManager(activity);
        rvDNSRelay.setLayoutManager(manager);

        adapter = new DnsRelaysAdapter(activity);
        adapter.setOnRelayPingMeasurer(this);
        adapter.setHasStableIds(true);
        rvDNSRelay.setAdapter(adapter);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        observeConfigurationState();
        requestRelaysConfiguration();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (adapter != null) {
            adapter.setOnRelayPingMeasurer(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        List<DnsRelayItem> dnsRelayItems = adapter.getItems();

        if (dnsRelayItems.isEmpty()) {
            return;
        }

        DnsServerRelay serverRoutes = getRelaysForCurrentServer(dnsRelayItems);

        List<DnsServerRelay> allRoutes = updateAndGetRelaysForAllServers(serverRoutes);

        saveRoutes(allRoutes);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        rvDNSRelay = null;
        adapter = null;
        pbDnsCryptRelays = null;
    }

    private void requestRelaysConfiguration() {
        Bundle bundle = getArguments();
        if (bundle == null) {
            return;
        }
        Object type = bundle.get(RELAY_TYPE_ARG);
        if (type != null) {
            viewModel.getRelaysConfiguration((RelayType) type);
        }
    }

    private void observeConfigurationState() {
        viewModel.getRelaysConfigurationState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof RelayConfigurationResult.Loading) {
                showProgress();
            } else if (state instanceof RelayConfigurationResult.Relays) {
                List<DnsRelay> relays = ((RelayConfigurationResult.Relays) state).getRelays();
                fillDnsRelaysList(relays);
            } else if (state instanceof RelayConfigurationResult.Finished) {
                hideProgress();
            }
        });
    }

    private void fillDnsRelaysList(List<DnsRelay> relays) {
        List<DnsRelayItem> recyclerItems = new ArrayList<>();
        String dnsServerName = getServerName();
        boolean ipv6Server = isServerIPv6();
        List<DnsServerRelay> routes = getRoutes();
        for (DnsRelay relay : relays) {
            if (ipv6Server && isRelayIPv6(relay) || !ipv6Server && !isRelayIPv6(relay)) {
                DnsRelayItem item = new DnsRelayItem(
                        relay.getName(),
                        relay.getDescription(),
                        relay.getSdns()
                );
                item.setChecked(isDnsRelaySelected(routes, dnsServerName, relay.getName()));
                recyclerItems.add(item);
            }
        }
        adapter.addItems(recyclerItems);
    }

    private String getServerName() {
        Bundle bundle = getArguments();
        if (bundle == null) {
            return "";
        }
        return bundle.getString(SERVER_NAME_ARG);
    }

    private boolean isServerIPv6() {
        Bundle bundle = getArguments();
        if (bundle == null) {
            return false;
        }
        return bundle.getBoolean(IPV6_SERVER_ARG);
    }

    @SuppressWarnings("unchecked")
    private List<DnsServerRelay> getRoutes() {
        Bundle bundle = getArguments();
        if (bundle == null) {
            return Collections.emptyList();
        }
        Object routes = bundle.getSerializable(ROUTES_ARG);
        if (routes != null) {
            return (ArrayList<DnsServerRelay>) routes;
        }
        return Collections.emptyList();
    }

    private boolean isRelayIPv6(DnsRelay relay) {
        return relay.getName().contains("ipv6");
    }

    private boolean isDnsRelaySelected(List<DnsServerRelay> routes, String serverName, String relayName) {
        for (int i = 0; i < routes.size(); i++) {
            DnsServerRelay route = routes.get(i);
            if (route.dnsServerName().equals(serverName)
                    && route.dnsServerRelays().contains(relayName)) {
                return true;
            }
        }
        return false;
    }

    private DnsServerRelay getRelaysForCurrentServer(List<DnsRelayItem> dnsRelayItems) {
        List<String> dnsRelaysNamesForCurrentServer = new ArrayList<>();
        for (DnsRelayItem dnsRelayItem : dnsRelayItems) {
            if (dnsRelayItem.isChecked()) {
                dnsRelaysNamesForCurrentServer.add(dnsRelayItem.getName());
            }
        }

        DnsServerRelay dnsServerRelaysNew = null;
        if (!dnsRelaysNamesForCurrentServer.isEmpty()) {
            dnsServerRelaysNew = new DnsServerRelay(getServerName(), dnsRelaysNamesForCurrentServer);
        }

        return dnsServerRelaysNew;
    }

    private List<DnsServerRelay> updateAndGetRelaysForAllServers(DnsServerRelay dnsServerRelaysNew) {
        List<DnsServerRelay> routesNew = new ArrayList<>();

        List<DnsServerRelay> routesCurrent = getRoutes();
        String dnsServerName = getServerName();
        for (int i = 0; i < routesCurrent.size(); i++) {

            DnsServerRelay dnsServerRelays = routesCurrent.get(i);

            if (dnsServerRelays.dnsServerName().equals(dnsServerName)) {
                if (dnsServerRelaysNew != null) {
                    routesNew.add(dnsServerRelaysNew);
                }
            } else {
                routesNew.add(dnsServerRelays);
            }
        }

        if (dnsServerRelaysNew != null && !routesNew.contains(dnsServerRelaysNew)) {
            routesNew.add(dnsServerRelaysNew);
        }

        return routesNew;
    }

    private void saveRoutes(List<DnsServerRelay> routesNew) {
        String server = getServerName();
        for (Fragment fragment: getParentFragmentManager().getFragments()) {
            if (fragment instanceof OnRoutesChangeListener) {
                ((OnRoutesChangeListener) fragment).onRoutesChange(routesNew, server);
                break;
            }
        }
    }

    @Override
    public void measureRelayPing(String name, String sdns) {
        viewModel.checkRelayPing(this, name, sdns);
    }

    @Override
    public void onPingUpdated(String name, int ping) {
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            return;
        }

        int position;
        if (adapter != null) {
            position = adapter.updateRelayPing(name, ping);
        } else {
            position = NO_POSITION;
        }
        if (position >= 0 && rvDNSRelay != null) {
            rvDNSRelay.post(() -> {
                if (adapter != null && !rvDNSRelay.isComputingLayout()) {
                    adapter.notifyItemChanged(position, new Object());
                }
            });
        }
    }

    private void showProgress() {
        pbDnsCryptRelays.setVisibility(View.VISIBLE);
        pbDnsCryptRelays.setIndeterminate(true);
    }

    private void hideProgress() {
        pbDnsCryptRelays.setIndeterminate(false);
        pbDnsCryptRelays.setVisibility(View.GONE);
    }

    public enum RelayType {
        DNSCRYPT_RELAY,
        ODOH_RELAY
    }

}
