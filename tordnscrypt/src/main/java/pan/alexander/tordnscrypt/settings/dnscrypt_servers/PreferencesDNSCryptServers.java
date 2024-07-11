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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.dnscrypt_servers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.AddDNSCryptServerDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.DnsServerRelay;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays.IPV6_SERVER_ARG;
import static pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays.RELAY_TYPE_ARG;
import static pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays.ROUTES_ARG;
import static pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays.RelayType.DNSCRYPT_RELAY;
import static pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays.RelayType.ODOH_RELAY;
import static pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays.SERVER_NAME_ARG;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_SERVERS;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import javax.inject.Inject;
import javax.inject.Named;

public class PreferencesDNSCryptServers extends Fragment implements View.OnClickListener,
        PreferencesDNSCryptRelays.OnRoutesChangeListener,
        AddDNSCryptServerDialogFragment.OnServerAddedListener, SearchView.OnQueryTextListener {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CoroutineExecutor executor;
    @Inject
    public Lazy<Verifier> verifierLazy;
    @Inject
    public ViewModelProvider.Factory viewModelFactory;

    private DnsServerViewModel viewModel;
    private RecyclerView.Adapter<DnsServersAdapter.DNSServersViewHolder> dnsServersAdapter;
    private final List<String> dnscryptProxyToml = new ArrayList<>();
    private List<DnsCryptResolver> dnsCryptPublicResolversMd;
    private List<DnsCryptResolver> dnsCryptOdohResolversMd;
    private List<DnsCryptResolver> dnsCryptOwnResolversMd;
    private final Set<DnsServerItem> savedOwnDNSCryptServers = new LinkedHashSet<>();
    private final List<String> dnscryptServersToml = new ArrayList<>();
    private final ArrayList<DnsServerRelay> dnsCryptRoutesToml = new ArrayList<>();
    List<DnsServerItem> dnsServerItems = new ArrayList<>();
    List<DnsServerItem> dnsServerItemsSaved = new ArrayList<>();
    private RecyclerView rvDNSServers;
    private LinearProgressIndicator progressBar;
    private CardView cardSearchDNSServer;
    SearchView searchView;
    private Parcelable rvViewState;


    public PreferencesDNSCryptServers() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this, viewModelFactory).get(DnsServerViewModel.class);

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        executor.submit("PreferencesDNSCryptServers verifier", () -> {
            try {
                Verifier verifier = verifierLazy.get();
                String appSign = verifier.getAppSignature();
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(verifier.getWrongSign(), appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            activity, getText(R.string.verifier_error).toString(), "6787");
                    if (notificationHelper != null && isAdded()) {
                        activity.runOnUiThread(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        activity, getText(R.string.verifier_error).toString(), "8990");
                if (isAdded() && notificationHelper != null) {
                    activity.runOnUiThread(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                }
                loge("PreferencesDNSCryptServers fault", e, true);
            }
            return null;
        });

    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view;
        try {
            view = inflater.inflate(R.layout.fragment_preferences_dnscrypt_servers_rv, container, false);
        } catch (Exception e) {
            loge("PreferencesDNSCryptServers onCreateView", e);
            throw e;
        }

        FloatingActionButton ibAddOwnServer = view.findViewById(R.id.ibAddOwnServer);
        ibAddOwnServer.setOnClickListener(this);

        cardSearchDNSServer = view.findViewById(R.id.cardSearchDNSServer);
        cardSearchDNSServer.setOnClickListener(this);

        searchView = view.findViewById(R.id.searchDNSServer);
        searchView.setOnQueryTextListener(this);

        rvDNSServers = view.findViewById(R.id.rvDNSServers);
        progressBar = view.findViewById(R.id.pbDnsCryptServers);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setTitle();

        initRecycler();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            makeSearchBarCollapsible();
        }

        observeDnsCryptConfiguration();
        if (dnscryptProxyToml.isEmpty()) {
            requestDnsCryptConfiguration();
        }
    }

    private void setTitle() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        activity.setTitle(R.string.pref_fast_dns_server);
    }

    private void initRecycler() {
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getContext());
        rvDNSServers.setLayoutManager(mLayoutManager);

        dnsServersAdapter = new DnsServersAdapter(this);
        dnsServersAdapter.setHasStableIds(true);

        try {
            rvDNSServers.setAdapter(dnsServersAdapter);
        } catch (IllegalStateException e) {
            loge("PreferencesDNSCryptServers setAdapter", e);
        }

        if (rvDNSServers.getLayoutManager() != null) {
            rvDNSServers.getLayoutManager().onRestoreInstanceState(rvViewState);
        }
    }

    private void updateRecycler() {
        if (dnsServersAdapter != null) {
            dnsServersAdapter.notifyDataSetChanged();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void makeSearchBarCollapsible() {
        rvDNSServers.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > oldScrollY) {
                cardSearchDNSServer.setVisibility(View.GONE);
            } else {
                cardSearchDNSServer.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        changeSearchBarDependingOnOrientation();

        String searchText = viewModel.getSearchQuery();
        if (searchText != null && !searchText.isEmpty()) {
            searchView.setQuery(viewModel.getSearchQuery(), false);
            searchServer(searchText);
        }
    }

    private void changeSearchBarDependingOnOrientation() {

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margins = ((ViewGroup.MarginLayoutParams) cardSearchDNSServer.getLayoutParams()).bottomMargin;
            params.setMargins(0, margins, 0, margins);
            params.gravity = Gravity.END;
            cardSearchDNSServer.setLayoutParams(params);
        } else {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margins = ((ViewGroup.MarginLayoutParams) cardSearchDNSServer.getLayoutParams()).bottomMargin;
            params.setMargins(0, margins, 0, margins);
            cardSearchDNSServer.setLayoutParams(params);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        saveRecyclerState();
        saveSettings();
    }

    private void saveRecyclerState() {
        if (rvDNSServers.getLayoutManager() != null) {
            rvViewState = rvDNSServers.getLayoutManager().onSaveInstanceState();
        }
    }

    private void saveSettings() {
        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (dnsServerItems.isEmpty() && dnsServerItemsSaved.isEmpty()) {
            return;
        }

        dnsServerItems.clear();
        dnsServerItems.addAll(dnsServerItemsSaved);

        saveOwnDnsCryptServersIfChanged();

        String servers = dnsServersListToLine();

        if (servers.isEmpty()) {
            return;
        }

        saveDnsServersToPrefs(servers);

        boolean isChanges = saveDnsServersToToml(servers);

        isChanges = saveDnsRelaysToToml() || isChanges;

        isChanges = saveOwnServersToToml() || isChanges;

        if (isChanges) {
            saveLinesToTomlFile();
            restartDNSCryptIfRunning(context);
        }
    }

    @Override
    public void onRoutesChange(List<DnsServerRelay> routes, String server) {
        dnsCryptRoutesToml.clear();
        dnsCryptRoutesToml.addAll(routes);
        for (DnsServerItem item: dnsServerItems) {
            if (item.getName().equals(server)){
                item.setRoutes(getRoutes(item));
                item.setChecked(!item.isProtoODoH() || !item.getRoutes().isEmpty());
                break;
            }
        }
        updateRecycler();
    }

    private void requestDnsCryptConfiguration() {
        viewModel.getDnsCryptConfigurations();
    }

    private void observeDnsCryptConfiguration() {
        viewModel.getDnsCryptConfiguration().observe(getViewLifecycleOwner(), configuration -> {
            if (configuration instanceof DnsCryptConfigurationResult.Loading) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
            } else if (configuration instanceof DnsCryptConfigurationResult.DnsCryptProxyToml) {
                List<String> lines = ((DnsCryptConfigurationResult.DnsCryptProxyToml) configuration).getLines();
                dnscryptProxyToml.clear();
                dnscryptProxyToml.addAll(lines);
            } else if (configuration instanceof DnsCryptConfigurationResult.DnsCryptServers) {
                List<String> resolvers = ((DnsCryptConfigurationResult.DnsCryptServers) configuration).getServers();
                dnscryptServersToml.clear();
                dnscryptServersToml.addAll(resolvers);
            } else if (configuration instanceof DnsCryptConfigurationResult.DnsCryptRoutes && dnsCryptRoutesToml.isEmpty()) {
                List<DnsServerRelay> routes = ((DnsCryptConfigurationResult.DnsCryptRoutes) configuration).getRoutes();
                dnsCryptRoutesToml.addAll(routes);
            } else if (configuration instanceof DnsCryptConfigurationResult.DnsCryptPublicResolvers) {
                dnsCryptPublicResolversMd = ((DnsCryptConfigurationResult.DnsCryptPublicResolvers) configuration).getResolvers();
            } else if (configuration instanceof DnsCryptConfigurationResult.DnsCryptOdohResolvers) {
                dnsCryptOdohResolversMd = ((DnsCryptConfigurationResult.DnsCryptOdohResolvers) configuration).getResolvers();
            } else if (configuration instanceof DnsCryptConfigurationResult.DnsCryptOwnResolvers) {
                dnsCryptOwnResolversMd = ((DnsCryptConfigurationResult.DnsCryptOwnResolvers) configuration).getResolvers();
            } else if (configuration instanceof DnsCryptConfigurationResult.Finished) {
                progressBar.setVisibility(View.GONE);
                progressBar.setIndeterminate(false);
                fillDnsServersList();
                addOwnDnsCryptServers();
                String searchText = viewModel.getSearchQuery();
                if (searchText != null && !searchText.isEmpty()) {
                    restoreSearchText(searchText);
                } else {
                    updateRecycler();
                }

            }
        });
    }

    private void fillDnsServersList() {
        dnsServerItems.clear();

        addServersFromPublicResolversMd();
        addServersFromOdohServersMd();

        Collections.sort(dnsServerItems);
        dnsServerItemsSaved.clear();
        dnsServerItemsSaved.addAll(dnsServerItems);
    }

    private void addServersFromPublicResolversMd() {
        Context context = requireContext();
        DnsServerFeatures features = new DnsServerFeatures(context, defaultPreferences.get());
        for (int i = 0; i < dnsCryptPublicResolversMd.size(); i++) {
            try {
                DnsServerItem dnsServer = new DnsServerItem(
                        dnsCryptPublicResolversMd.get(i).getName(),
                        dnsCryptPublicResolversMd.get(i).getDescription(),
                        dnsCryptPublicResolversMd.get(i).getSdns(),
                        features
                );
                dnsServer.setChecked(isDnsServerActive(dnsServer));
                dnsServer.setRoutes(getRoutes(dnsServer));
                if (dnsServer.isVisible()) {
                    dnsServerItems.add(dnsServer);
                }
            } catch (Exception e) {
                logw("Trying to add wrong DNSCrypt server "
                        + e.getMessage() + " " + e.getCause() + " "
                        + dnsCryptPublicResolversMd.get(i));
            }

        }
    }

    private void addServersFromOdohServersMd() {
        Context context = requireContext();
        DnsServerFeatures features = new DnsServerFeatures(context, defaultPreferences.get());
        for (int i = 0; i < dnsCryptOdohResolversMd.size(); i++) {
            try {
                DnsServerItem dnsServer = new DnsServerItem(
                        dnsCryptOdohResolversMd.get(i).getName(),
                        dnsCryptOdohResolversMd.get(i).getDescription(),
                        dnsCryptOdohResolversMd.get(i).getSdns(),
                        features
                );
                dnsServer.setChecked(isDnsServerActive(dnsServer));
                dnsServer.setRoutes(getRoutes(dnsServer));
                if (dnsServer.isVisible()) {
                    dnsServerItems.add(dnsServer);
                }
            } catch (Exception e) {
                logw("Trying to add wrong ODoH server "
                        + e.getMessage() + " " + e.getCause() + " "
                        + dnsCryptOdohResolversMd.get(i));
            }
        }
    }

    private boolean isDnsServerActive(DnsServerItem dnsServer) {
        for (int i = 0; i < dnscryptServersToml.size(); i++) {
            if (!dnsServer.getName().isEmpty()
                    && dnsServer.getName().equals(dnscryptServersToml.get(i).trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> getRoutes(DnsServerItem dnsServer) {
        if (dnsServer.isProtoDNSCrypt() || dnsServer.isProtoODoH()) {
            for (int i = 0; i < dnsCryptRoutesToml.size(); i++) {
                if (dnsCryptRoutesToml.get(i).dnsServerName().equals(dnsServer.getName())) {
                    return dnsCryptRoutesToml.get(i).dnsServerRelays();
                }
            }
        }
        return Collections.emptyList();
    }

    private String dnsServersListToLine() {

        StringBuilder servers = new StringBuilder();
        servers.append("['");

        for (int i = 0; i < dnsServerItems.size(); i++) {
            if (dnsServerItems.get(i).isChecked()) {
                if (dnsServerItems.get(i).isProtoODoH() && dnsServerItems.get(i).getRoutes().isEmpty()) {
                    continue;
                }
                servers.append(dnsServerItems.get(i).getName());
                servers.append("', '");
            }
        }

        if (servers.toString().equals("['")) {
            Toast.makeText(getActivity(), getText(R.string.pref_dnscrypt_select_server_names), Toast.LENGTH_LONG).show();
            return "";
        }

        servers.delete(servers.length() - 4, servers.length()).append("']");

        return servers.toString();
    }

    private void saveDnsServersToPrefs(String servers) {
        preferenceRepository.get()
                .setStringPreference(DNSCRYPT_SERVERS, servers);
    }

    private boolean saveDnsServersToToml(String servers) {

        for (int i = 0; i < dnscryptProxyToml.size(); i++) {

            String line = dnscryptProxyToml.get(i);
            if (line.contains("server_names")) {
                String lineSaved = line;
                line = line.replaceFirst("\\[.+]", servers);

                if (lineSaved.equals(line)) {
                    return false;
                }
                dnscryptProxyToml.set(i, line);
                break;
            }
        }

        return true;
    }

    private boolean saveDnsRelaysToToml() {

        List<String> lines = new ArrayList<>();

        boolean lockRoutes = false;

        for (int i = 0; i < dnscryptProxyToml.size(); i++) {

            String line = dnscryptProxyToml.get(i);
            if (line.contains("routes")) {
                lockRoutes = true;
                lines.addAll(prepareDNSRelaysToSave());
            } else if (line.matches("^ ?] *$") && lockRoutes) {
                lockRoutes = false;
            } else if (!lockRoutes) {
                lines.add(line);
            }
        }

        if (dnscryptProxyToml.size() != lines.size()) {
            dnscryptProxyToml.clear();
            dnscryptProxyToml.addAll(lines);
            return true;
        } else {
            for (int i = 0; i < lines.size(); i++) {
                if (!dnscryptProxyToml.get(i).equals(lines.get(i))) {
                    dnscryptProxyToml.clear();
                    dnscryptProxyToml.addAll(lines);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean saveOwnServersToToml() {
        List<String> newLines = new ArrayList<>();
        newLines.add("[static]");

        for (DnsServerItem dnsServerItem : dnsServerItems) {
            if (dnsServerItem.getOwnServer()) {
                newLines.add("[static.'" + dnsServerItem.getName() + "']");
                newLines.add("stamp = 'sdns:" + dnsServerItem.getSDNS() + "'");
            }
        }

        List<String> oldLines = getOldLines();

        if (newLines.equals(oldLines)) {
            return false;
        }

        dnscryptProxyToml.removeAll(oldLines);
        dnscryptProxyToml.addAll(newLines);

        return true;
    }

    @NonNull
    private ArrayList<String> getOldLines() {
        ArrayList<String> oldLines = new ArrayList<>();

        boolean lockStatic = false;

        for (int i = 0; i < dnscryptProxyToml.size(); i++) {

            String line = dnscryptProxyToml.get(i);

            if (line.contains("[static]")) {
                lockStatic = true;
                oldLines.add(line);
            } else if (line.contains("[") && line.contains("]") && !line.contains("static") && lockStatic) {
                lockStatic = false;
            } else if (lockStatic) {
                oldLines.add(line);
            }
        }
        return oldLines;
    }

    private List<String> prepareDNSRelaysToSave() {

        List<String> routes = new ArrayList<>();
        routes.add("routes = [");

        if (!dnsCryptRoutesToml.isEmpty()) {
            for (DnsServerRelay dnsServerRelays : dnsCryptRoutesToml) {
                String route = getRoute(dnsServerRelays).toString();
                routes.add(route);
            }

            String lastRouteLine = routes.get(routes.size() - 1);
            lastRouteLine = lastRouteLine.substring(0, lastRouteLine.length() - 1);
            routes.set(routes.size() - 1, lastRouteLine);
        }

        routes.add("]");

        return routes;
    }

    private StringBuilder getRoute(DnsServerRelay dnsServerRelays) {
        String serverName = dnsServerRelays.dnsServerName();

        StringBuilder route = new StringBuilder();

        route.append("{ server_name = ").append("'").append(serverName).append("'").append(", via=[");

        for (String relay : dnsServerRelays.dnsServerRelays()) {
            route.append("'").append(relay).append("'").append(", ");
        }

        route.delete(route.lastIndexOf(","), route.length());

        route.append("] }").append(",");
        return route;
    }

    private void saveLinesToTomlFile() {
        viewModel.saveDnsCryptProxyToml(dnscryptProxyToml);
    }

    private void restartDNSCryptIfRunning(Context context) {
        if (ModulesStatus.getInstance().getDnsCryptState() == ModuleState.RUNNING) {
            ModulesRestarter.restartDNSCrypt(context);
        }
    }

    boolean isRelaysMdExist() {
        File relaysMd = new File(pathVars.get().getDNSCryptRelaysPath());
        return relaysMd.isFile();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ibAddOwnServer) {
            if (isAdded()) {
                AddDNSCryptServerDialogFragment addServer = AddDNSCryptServerDialogFragment.getInstance();
                addServer.setOnServerAddListener(this);
                addServer.show(getParentFragmentManager(), "AddDNSCryptServerDialogFragment");
            }
        } else if (v.getId() == R.id.cardSearchDNSServer) {
            searchView.setIconified(false);
        }
    }

    private void addOwnDnsCryptServers() {
        Context context = requireContext();
        DnsServerFeatures features = new DnsServerFeatures(context, defaultPreferences.get());
        List<DnsServerItem> dnsServerItemsOwn = new ArrayList<>();
        for (DnsCryptResolver resolver : dnsCryptOwnResolversMd) {
            try {
                DnsServerItem dnsServer = new DnsServerItem(
                        resolver.getName(),
                        resolver.getDescription(),
                        resolver.getSdns(),
                        features
                );
                dnsServer.setOwnServer(true);
                dnsServer.setChecked(isDnsServerActive(dnsServer));
                dnsServer.setRoutes(getRoutes(dnsServer));
                dnsServerItemsOwn.add(dnsServer);
            } catch (Exception e) {
                logw("PreferencesDNSCryptServers addOwnDnsCryptServers", e);
            }
        }
        savedOwnDNSCryptServers.clear();
        savedOwnDNSCryptServers.addAll(dnsServerItemsOwn);


        if (!dnsServerItemsOwn.isEmpty()) {
            dnsServerItemsOwn.addAll(dnsServerItems);
            dnsServerItems.clear();
            dnsServerItems.addAll(dnsServerItemsOwn);
            dnsServerItemsSaved.clear();
            dnsServerItemsSaved.addAll(dnsServerItemsOwn);
        }
    }

    private void restoreSearchText(String searchText) {
        searchView.setQuery(searchText, false);
        searchServer(searchText);
    }

    private void saveOwnDnsCryptServersIfChanged() {
        Set<DnsServerItem> ownItems = new LinkedHashSet<>();
        List<String> linesReadyToSave = new ArrayList<>();

        for (DnsServerItem dnsServerItem : dnsServerItems) {
            if (dnsServerItem.getOwnServer()) {
                linesReadyToSave.add("## " + dnsServerItem.getName());
                linesReadyToSave.add(dnsServerItem.getDescription());
                linesReadyToSave.add("sdns://" + dnsServerItem.getSDNS());

                ownItems.add(dnsServerItem);
            }
        }

        if (ownItems.equals(savedOwnDNSCryptServers)) {
            return;
        }

        viewModel.saveOwnResolversMd(linesReadyToSave);

    }

    @Override
    public void onServerAdded(DnsServerItem dnsServerItem) {
        if (dnsServerItems != null && dnsServerItem != null) {
            dnsServerItems.add(0, dnsServerItem);
            dnsServerItemsSaved.add(0, dnsServerItem);
            updateRecycler();
            rvDNSServers.smoothScrollToPosition(0);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return searchServer(query);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return searchServer(newText);
    }

    private boolean searchServer(String searchText) {
        if (dnsServersAdapter == null || dnsServerItems == null || dnsServerItemsSaved == null) {
            return false;
        }

        viewModel.setSearchQuery(searchText);

        if (searchText == null || searchText.isEmpty()) {
            dnsServerItems.clear();
            dnsServerItems.addAll(dnsServerItemsSaved);
            updateRecycler();
            return true;
        }

        dnsServerItems.clear();

        for (DnsServerItem dnsServerItem : dnsServerItemsSaved) {
            if (dnsServerItem.getName().toLowerCase().contains(searchText.toLowerCase())
                    || dnsServerItem.getDescription().toLowerCase().contains(searchText.toLowerCase())
                    || (dnsServerItem.isProtoDNSCrypt() && searchText.toLowerCase().contains("dnscrypt server"))
                    || (dnsServerItem.isProtoDoH() && searchText.toLowerCase().contains("doh server"))
                    || (dnsServerItem.isProtoODoH() && searchText.toLowerCase().contains("odoh server"))
                    || (dnsServerItem.isDnssec() && searchText.toLowerCase().contains("dnssec"))
                    || (dnsServerItem.isNofilter() && searchText.toLowerCase().contains("non-filtering"))
                    || (dnsServerItem.isNolog() && searchText.toLowerCase().contains("non-logging"))
                    || (!dnsServerItem.isNolog() && searchText.toLowerCase().contains("keep logs"))
                    || (!dnsServerItem.isNofilter() && searchText.toLowerCase().contains("filtering"))) {
                dnsServerItems.add(dnsServerItem);
            }
        }

        updateRecycler();

        return true;
    }

    void openDNSRelaysPref(DnsServerItem dnsServer) {
        Bundle bundle = new Bundle();
        if (dnsServer.isProtoDNSCrypt()) {
            bundle.putSerializable(RELAY_TYPE_ARG, DNSCRYPT_RELAY);
        } else if (dnsServer.isProtoODoH()) {
            bundle.putSerializable(RELAY_TYPE_ARG, ODOH_RELAY);
        }
        bundle.putString(SERVER_NAME_ARG, dnsServer.getName());
        bundle.putBoolean(IPV6_SERVER_ARG, dnsServer.isIpv6());
        bundle.putSerializable(ROUTES_ARG, dnsCryptRoutesToml);
        PreferencesDNSCryptRelays preferencesDNSCryptRelays = new PreferencesDNSCryptRelays();
        preferencesDNSCryptRelays.setArguments(bundle);
        FragmentTransaction fragmentTransaction = getParentFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        fragmentTransaction.replace(android.R.id.content, preferencesDNSCryptRelays);
        fragmentTransaction.addToBackStack("preferencesDNSCryptRelaysTag");
        fragmentTransaction.commit();
    }
}
