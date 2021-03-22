package pan.alexander.tordnscrypt.settings.dnscrypt_servers;
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
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.AddDNSCryptServerDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.DNSServerRelays;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class PreferencesDNSCryptServers extends Fragment implements View.OnClickListener,
        PreferencesDNSCryptRelays.OnRoutesChangeListener, OnTextFileOperationsCompleteListener,
        AddDNSCryptServerDialogFragment.OnServerAddedListener, SearchView.OnQueryTextListener {

    private RecyclerView.Adapter<DNSServersAdapter.DNSServersViewHolder> dNSServersAdapter;
    private ArrayList<String> dnsServerNames;
    private ArrayList<String> dnsServerDescr;
    private ArrayList<String> dnsServerSDNS;
    private ArrayList<String> dnscrypt_proxy_toml;
    private CopyOnWriteArrayList<String> dnscrypt_servers_current;
    private CopyOnWriteArrayList<DNSServerRelays> routes_current;
    private CopyOnWriteArrayList<DNSServerItem> list_dns_servers;
    private CopyOnWriteArrayList<DNSServerItem> list_dns_servers_saved;
    private String appDataDir;
    private ArrayList<DNSServerItem> savedOwnDNSCryptServers;
    private String ownServersFilePath;
    private RecyclerView rvDNSServers;
    private CardView cardSearchDNSServer;
    private SearchView searchDNSServer;
    private String searchQuery = "";
    private Parcelable rvViewState;


    public PreferencesDNSCryptServers() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        Context context = getActivity();
        if (context == null) {
            return;
        }

        takeArguments();

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                Verifier verifier = new Verifier(context);
                String appSign = verifier.getApkSignatureZip();
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, getText(R.string.verifier_error).toString(), "6787");
                    if (notificationHelper != null && isAdded()) {
                        notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        context, getText(R.string.verifier_error).toString(), "8990");
                if (isAdded() && notificationHelper != null) {
                    notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                }
                Log.e(LOG_TAG, "PreferencesDNSCryptServers fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });

    }


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preferences_dnscrypt_servers_rv, container, false);

        ImageButton ibAddOwnServer = view.findViewById(R.id.ibAddOwnServer);
        ibAddOwnServer.setOnClickListener(this);

        cardSearchDNSServer = view.findViewById(R.id.cardSearchDNSServer);
        cardSearchDNSServer.setOnClickListener(this);

        searchDNSServer = view.findViewById(R.id.searchDNSServer);
        searchDNSServer.setOnQueryTextListener(this);

        return view;
    }


    @Override
    public void onStart() {
        super.onStart();

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        activity.setTitle(R.string.pref_fast_dns_server);

        PathVars pathVars = PathVars.getInstance(activity);
        appDataDir = pathVars.getAppDataDir();

        FileOperations.setOnFileOperationCompleteListener(this);

        fillDNSServersList(activity);

        createAndFillRecyclerView(activity);

        readOwnServers(activity);
    }

    @Override
    public void onResume() {
        super.onResume();

        Context context = getActivity();
        if (context == null) {
            return;
        }

        searchDNSServer.setQuery(searchQuery, true);

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

        if (rvDNSServers.getLayoutManager() != null) {
            rvViewState = rvDNSServers.getLayoutManager().onSaveInstanceState();
        }

        FileOperations.deleteOnFileOperationCompleteListener(this);

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (list_dns_servers.size() == 0 && list_dns_servers_saved.size() == 0) {
            return;
        }

        list_dns_servers = list_dns_servers_saved;

        saveOwnDNSCryptServersIfChanged(context);

        String dnscrypt_servers = dnsServersListToLine(context);

        if (dnscrypt_servers.isEmpty()) {
            return;
        }

        saveDNSServersToPrefs(context, dnscrypt_servers);

        boolean isChanges = saveDNSServersToTomlList(dnscrypt_servers);

        isChanges = saveDNSRelaysToTomlList() || isChanges;

        isChanges = saveOwnServersToTomlList() || isChanges;

        if (isChanges) {
            saveLinesToTomlFile(context);
            restartDNSCryptIfRunning(context);
        }
    }

    @Override
    public void onRoutesChange(CopyOnWriteArrayList<DNSServerRelays> routesNew) {
        routes_current = routesNew;
    }

    @SuppressWarnings("unchecked")
    private void takeArguments() {
        if (getArguments() != null) {
            dnsServerNames = getArguments().getStringArrayList("dnsServerNames");
            dnsServerDescr = getArguments().getStringArrayList("dnsServerDescr");
            dnsServerSDNS = getArguments().getStringArrayList("dnsServerSDNS");

            if (dnscrypt_proxy_toml == null) {
                dnscrypt_proxy_toml = getArguments().getStringArrayList("dnscrypt_proxy_toml");
            }

            if (dnscrypt_servers_current == null && getArguments() != null) {
                List<String> dnscrypt_servers  = getArguments().getStringArrayList("dnscrypt_servers");
                if (dnscrypt_servers != null) {
                    dnscrypt_servers_current = new CopyOnWriteArrayList<>(dnscrypt_servers);
                }
            }

            if (routes_current == null && getArguments() != null) {
                List<DNSServerRelays> routes = (ArrayList<DNSServerRelays>) getArguments().getSerializable("routes");
                if (routes != null) {
                    routes_current = new CopyOnWriteArrayList<>(routes);
                }
            }
        } else {
            Log.e(LOG_TAG, "PreferencesDNSCryptServers getArguments() nullPointer");
        }
    }

    private void fillDNSServersList(Context context) {

        list_dns_servers = new CopyOnWriteArrayList<>();

        for (int i = 0; i < dnsServerNames.size(); i++) {
            try {
                DNSServerItem dnsServer = new DNSServerItem(context, dnsServerNames.get(i), dnsServerDescr.get(i), dnsServerSDNS.get(i));
                setDnsServerChecked(dnsServer);
                setRoutes(dnsServer);

                if (dnsServer.isVisibility() && !dnsServerNames.get(i).contains("repeat_server"))
                    list_dns_servers.add(dnsServer);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Trying to add wrong DNSCrypt server " + e.getMessage() + " "
                        + dnsServerNames.get(i) + " " + dnsServerDescr.get(i)
                        + " " + dnsServerSDNS);
            }

        }

        if (list_dns_servers.size() > 1) {
            ArrayList<DNSServerItem> tmpList = new ArrayList<>(list_dns_servers);
            Collections.sort(tmpList);
            list_dns_servers.clear();
            list_dns_servers.addAll(tmpList);
        }

        list_dns_servers_saved = new CopyOnWriteArrayList<>(list_dns_servers);
    }

    private void readOwnServers(Context context) {
        ownServersFilePath = appDataDir + "/app_data/dnscrypt-proxy/own-resolvers.md";

        if (new File(ownServersFilePath).isFile()) {
            FileOperations.readTextFile(context, ownServersFilePath, "own-resolvers.md");
        }
    }

    private void setRoutes(DNSServerItem dnsServer) {
        if (routes_current != null && dnsServer.isProtoDNSCrypt()) {

            for (int i = 0; i < routes_current.size(); i++) {
                if (routes_current.get(i).getDnsServerName().equals(dnsServer.getName())) {
                    dnsServer.getRoutes().addAll(routes_current.get(i).getDnsServerRelays());
                }
            }
        }
    }

    private void setDnsServerChecked(DNSServerItem dnsServer) {
        for (int i = 0; i < dnscrypt_servers_current.size(); i++) {
            if (!dnsServer.getName().isEmpty() && dnsServer.getName().equals(dnscrypt_servers_current.get(i).trim()))
                dnsServer.setChecked(true);
        }
    }

    private void createAndFillRecyclerView(Activity activity) {

        rvDNSServers = activity.findViewById(R.id.rvDNSServers);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(activity);
        rvDNSServers.setLayoutManager(mLayoutManager);

        dNSServersAdapter = new DNSServersAdapter(activity, searchDNSServer,
                this, getParentFragmentManager(),
                list_dns_servers, list_dns_servers_saved, routes_current, isRelaysMdExist());

        try {
            rvDNSServers.setAdapter(dNSServersAdapter);
        } catch (IllegalStateException e) {
            Log.e(LOG_TAG, "PreferencesDNSCryptServers setAdapter Exception " + e.getMessage());
        }

        if (rvDNSServers.getLayoutManager() != null) {
            rvDNSServers.getLayoutManager().onRestoreInstanceState(rvViewState);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            rvDNSServers.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY > oldScrollY) {
                    cardSearchDNSServer.setVisibility(View.GONE);
                } else {
                    cardSearchDNSServer.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private String dnsServersListToLine(Context context) {
        dnscrypt_servers_current.clear();

        String line = "";

        StringBuilder dnscrypt_servers = new StringBuilder();
        dnscrypt_servers.append("['");

        for (int i = 0; i < list_dns_servers.size(); i++) {
            if (list_dns_servers.get(i).isChecked()) {
                dnscrypt_servers_current.add(list_dns_servers.get(i).getName());

                dnscrypt_servers.append(list_dns_servers.get(i).getName());
                dnscrypt_servers.append("', '");
            }
        }

        if (dnscrypt_servers.toString().equals("['")) {
            Toast.makeText(context, getText(R.string.pref_dnscrypt_select_server_names), Toast.LENGTH_LONG).show();
            return line;
        }

        dnscrypt_servers.delete(dnscrypt_servers.length() - 4, dnscrypt_servers.length()).append("']");

        line = dnscrypt_servers.toString();

        return line;
    }

    private void saveDNSServersToPrefs(Context context, String dnscrypt_servers) {
        new PrefManager(context).setStrPref("DNSCrypt Servers", dnscrypt_servers);
    }

    private boolean saveDNSServersToTomlList(String dnscrypt_servers) {

        for (int i = 0; i < dnscrypt_proxy_toml.size(); i++) {

            String line = dnscrypt_proxy_toml.get(i);

            if (line.contains("server_names")) {
                String lineSaved = line;
                line = line.replaceFirst("\\[.+]", dnscrypt_servers);

                if (lineSaved.equals(line)) {
                    return false;
                }

                dnscrypt_proxy_toml.set(i, line);
                break;
            }
        }

        return true;
    }

    private boolean saveDNSRelaysToTomlList() {

        ArrayList<String> dnscrypt_proxy_toml_new = new ArrayList<>();

        boolean lockRoutes = false;

        for (int i = 0; i < dnscrypt_proxy_toml.size(); i++) {

            String line = dnscrypt_proxy_toml.get(i);

            if (line.contains("routes")) {
                lockRoutes = true;
                dnscrypt_proxy_toml_new.addAll(prepareDNSRelaysToSave());
            } else if (line.matches("^ ?] *$") && lockRoutes) {
                lockRoutes = false;
            } else if (!lockRoutes) {
                dnscrypt_proxy_toml_new.add(line);
            }
        }

        int size = (Math.min(dnscrypt_proxy_toml.size(), dnscrypt_proxy_toml_new.size()));

        for (int i = 0; i < size; i++) {
            if (!dnscrypt_proxy_toml.get(i).equals(dnscrypt_proxy_toml_new.get(i))) {
                dnscrypt_proxy_toml = dnscrypt_proxy_toml_new;

                return true;
            }
        }

        return false;
    }

    private boolean saveOwnServersToTomlList() {
        if (dnscrypt_proxy_toml == null) {
            return false;
        }

        ArrayList<String> newLines = new ArrayList<>();

        newLines.add("[static]");
        for (DNSServerItem dnsServerItem : list_dns_servers) {
            if (dnsServerItem.getOwnServer()) {
                newLines.add("[static.'" + dnsServerItem.getName() + "']");
                newLines.add("stamp = 'sdns:" + dnsServerItem.getSDNS() + "'");
            }
        }

        ArrayList<String> oldLines = new ArrayList<>();

        boolean lockStatic = false;

        for (int i = 0; i < dnscrypt_proxy_toml.size(); i++) {

            String line = dnscrypt_proxy_toml.get(i);

            if (line.contains("[static]")) {
                lockStatic = true;
                oldLines.add(line);
            } else if (line.contains("[") && line.contains("]") && !line.contains("static") && lockStatic) {
                lockStatic = false;
            } else if (lockStatic) {
                oldLines.add(line);
            }
        }

        if (newLines.equals(oldLines)) {
            return false;
        }

        dnscrypt_proxy_toml.removeAll(oldLines);

        dnscrypt_proxy_toml.addAll(newLines);

        return true;
    }

    private List<String> prepareDNSRelaysToSave() {

        ArrayList<String> routesLines = new ArrayList<>();

        routesLines.add("routes = [");

        if (routes_current != null && routes_current.size() > 0) {
            for (DNSServerRelays dnsServerRelays : routes_current) {
                String serverName = dnsServerRelays.getDnsServerName();

                StringBuilder routeLine = new StringBuilder();

                routeLine.append("{ server_name = ").append("'").append(serverName).append("'").append(", via=[");

                for (String relay : dnsServerRelays.getDnsServerRelays()) {
                    routeLine.append("'").append(relay).append("'").append(", ");
                }

                routeLine.delete(routeLine.lastIndexOf(","), routeLine.length());

                routeLine.append("] }").append(",");

                routesLines.add(routeLine.toString());
            }

            String lastRouteLine = routesLines.get(routesLines.size() - 1);
            lastRouteLine = lastRouteLine.substring(0, lastRouteLine.length() - 1);
            routesLines.set(routesLines.size() - 1, lastRouteLine);
        }

        routesLines.add("]");

        return routesLines;
    }

    private void saveLinesToTomlFile(Context context) {
        FileOperations.writeToTextFile(context, appDataDir
                        + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml", dnscrypt_proxy_toml,
                SettingsActivity.public_resolvers_md_tag);
    }

    private void restartDNSCryptIfRunning(Context context) {

        boolean dnsCryptRunning = ModulesAux.isDnsCryptSavedStateRunning(context);

        if (dnsCryptRunning) {
            ModulesRestarter.restartDNSCrypt(context);
        }
    }

    private boolean isRelaysMdExist() {
        File relaysMd = new File(appDataDir + "/app_data/dnscrypt-proxy/relays.md");
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
            searchDNSServer.setIconified(false);
        }
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag, List<String> lines) {

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (currentFileOperation == FileOperationsVariants.readTextFile && tag.equals("own-resolvers.md")) {

            ArrayList<DNSServerItem> ownDNSCryptServers = parseOwnDNSCryptServers(context, lines);

            savedOwnDNSCryptServers = new ArrayList<>();
            savedOwnDNSCryptServers.addAll(ownDNSCryptServers);


            if (!ownDNSCryptServers.isEmpty()) {
                int newItemCount = ownDNSCryptServers.size();
                ownDNSCryptServers.addAll(list_dns_servers);
                list_dns_servers.clear();
                list_dns_servers.addAll(ownDNSCryptServers);
                list_dns_servers_saved.clear();
                list_dns_servers_saved.addAll(ownDNSCryptServers);
                dNSServersAdapter.notifyItemRangeChanged(0, newItemCount);
            }
        }
    }

    private ArrayList<DNSServerItem> parseOwnDNSCryptServers(Context context, List<String> lines) {
        ArrayList<DNSServerItem> dnsServerItemsOwn = new ArrayList<>();

        if (lines == null) {
            return dnsServerItemsOwn;
        }

        boolean lockServer = false;
        String name = "";
        String description;
        String sdns;
        StringBuilder sb = new StringBuilder();

        ArrayList<String> linesReady = new ArrayList<>(lines);

        for (String line : linesReady) {
            if ((line.contains("##") || lockServer) && line.trim().length() > 2) {
                if (line.contains("##")) {
                    lockServer = true;
                    name = line.substring(2).replaceAll("\\s+", "").trim();
                } else if (line.contains("sdns")) {
                    sdns = line.replace("sdns://", "").trim();
                    lockServer = false;
                    description = sb.toString().replaceAll("\\s", " ");
                    sb.setLength(0);

                    try {
                        DNSServerItem item = new DNSServerItem(context, name, description, sdns);
                        setDnsServerChecked(item);
                        setRoutes(item);
                        item.setOwnServer(true);

                        dnsServerItemsOwn.add(item);
                    } catch (Exception e) {
                        Log.w(LOG_TAG, "Trying to add wrong DNSCrypt server " + e.getMessage() + " "
                                + name + " " + description
                                + " " + sdns);
                    }


                } else if (!line.contains("##") || lockServer) {
                    sb.append(line).append((char) 10);
                }
            }
        }
        return dnsServerItemsOwn;
    }

    private void saveOwnDNSCryptServersIfChanged(Context context) {
        ArrayList<DNSServerItem> newOwnItems = new ArrayList<>();
        ArrayList<String> linesReadyToSave = new ArrayList<>();

        for (DNSServerItem dnsServerItem : list_dns_servers) {
            if (dnsServerItem.getOwnServer()) {
                linesReadyToSave.add("## " + dnsServerItem.getName());
                linesReadyToSave.add(dnsServerItem.getDescription());
                linesReadyToSave.add("sdns://" + dnsServerItem.getSDNS());

                newOwnItems.add(dnsServerItem);
            }
        }

        if (newOwnItems.equals(savedOwnDNSCryptServers)) {
            return;
        }

        FileOperations.writeToTextFile(context, ownServersFilePath, linesReadyToSave, "ignored");

    }

    @Override
    public void onServerAdded(DNSServerItem dnsServerItem) {
        if (list_dns_servers != null && dnsServerItem != null) {
            list_dns_servers.add(0, dnsServerItem);
            list_dns_servers_saved.add(0, dnsServerItem);
            dNSServersAdapter.notifyDataSetChanged();
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
        if (dNSServersAdapter == null || list_dns_servers == null || list_dns_servers_saved == null) {
            return false;
        }

        searchQuery = searchText;

        if (searchText == null || searchText.isEmpty()) {
            list_dns_servers.clear();
            list_dns_servers.addAll(list_dns_servers_saved);
            dNSServersAdapter.notifyDataSetChanged();
            return true;
        }

        list_dns_servers.clear();

        for (DNSServerItem dnsServerItem : list_dns_servers_saved) {
            if (dnsServerItem.getName().toLowerCase().contains(searchText.toLowerCase())
                    || dnsServerItem.getDescription().toLowerCase().contains(searchText.toLowerCase())
                    || (dnsServerItem.isProtoDNSCrypt() && searchText.toLowerCase().contains("dnscrypt server"))
                    || (dnsServerItem.isProtoDoH() && searchText.toLowerCase().contains("doh server"))
                    || (dnsServerItem.isDnssec() && searchText.toLowerCase().contains("dnssec"))
                    || (dnsServerItem.isNofilter() && searchText.toLowerCase().contains("non-filtering"))
                    || (dnsServerItem.isNolog() && searchText.toLowerCase().contains("non-logging"))
                    || (!dnsServerItem.isNolog() && searchText.toLowerCase().contains("keep logs"))
                    || (!dnsServerItem.isNofilter() && searchText.toLowerCase().contains("ad-filtering"))) {
                list_dns_servers.add(dnsServerItem);
            }
        }

        dNSServersAdapter.notifyDataSetChanged();

        return true;
    }
}
