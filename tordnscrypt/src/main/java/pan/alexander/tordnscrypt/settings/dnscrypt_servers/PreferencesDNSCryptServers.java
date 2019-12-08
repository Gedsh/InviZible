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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.DNSServerRelays;
import pan.alexander.tordnscrypt.settings.dnscrypt_relays.PreferencesDNSCryptRelays;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class PreferencesDNSCryptServersRv extends Fragment implements PreferencesDNSCryptRelays.OnRoutesChangeListener {

    private ArrayList<String> dnsServerNames;
    private ArrayList<String> dnsServerDescr;
    private ArrayList<String> dnsServerSDNS;
    private ArrayList<String> dnscrypt_proxy_toml;
    private ArrayList<String> dnscrypt_servers_current;
    private ArrayList<DNSServerRelays> routes_current;
    private ArrayList<DNSServerItem> list_dns_servers;
    private String appDataDir;
    private OnServersChangeListener callback;
    private int lastAdapterPosition = 0;


    public PreferencesDNSCryptServersRv() {
        // Required empty public constructor
    }

    public interface OnServersChangeListener {
        void onServersChange();
    }

    public void setOnServersChangeListener(OnServersChangeListener callback) {
        this.callback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        takeArguments();

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Verifier verifier = new Verifier(getActivity());
                    String appSign = verifier.getApkSignatureZipModern();
                    String appSignAlt = verifier.getApkSignature();
                    if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(), getText(R.string.verifier_error).toString(), "6787");
                        if (notificationHelper != null) {
                            if (getFragmentManager() != null) {
                                notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                            }
                        }
                    }

                } catch (Exception e) {
                    if (getFragmentManager() != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(), getText(R.string.verifier_error).toString(), "8990");
                        if (notificationHelper != null) {
                            notificationHelper.show(getFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                    Log.e(LOG_TAG, "PreferencesDNSCryptServersRv fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                            Arrays.toString(e.getStackTrace()));
                }
            }
        });
        thread.start();

    }

    @Override
    public void onResume() {
        super.onResume();

        Objects.requireNonNull(getActivity()).setTitle(R.string.pref_fast_dns_server);

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;

        fillDNSServersList();

        createAndFillRecyclerView();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preferences_dnscrypt_servers_rv, container, false);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() == null)
            return;

        if (list_dns_servers.size() == 0)
            return;

        String dnscrypt_servers = dnsServersListToLine();

        if (dnscrypt_servers.isEmpty()) {
            return;
        }

        saveDNSServersToPrefs(dnscrypt_servers);

        if (callback != null)
            callback.onServersChange();

        boolean isChanges = saveDNSServersToTomlList(dnscrypt_servers);

        isChanges = saveDNSRelaysToTomlList() || isChanges;

        if (isChanges) {
            saveLinesToTomlFile();
            restartDNSCryptIfRunning();
        }
    }

    @Override
    public void onRoutesChange(ArrayList<DNSServerRelays> routesNew) {
        routes_current = routesNew;
    }

    private void restartDNSCryptIfRunning() {

        if (getActivity() == null) {
            return;
        }

        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");

        if (dnsCryptRunning) {
            ModulesRestarter.restartDNSCrypt(getActivity());
        }
    }

    private void saveLinesToTomlFile() {
        FileOperations.writeToTextFile(getActivity(), appDataDir
                        + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml", dnscrypt_proxy_toml,
                SettingsActivity.public_resolvers_md_tag);
    }

    private List<String> prepareDNSRelaysToSave() {

        ArrayList<String> routesLines = new ArrayList<>();

        routesLines.add("routes = [");

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

        routesLines.add("]");

        return routesLines;
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

        int size = (dnscrypt_proxy_toml.size() <= dnscrypt_proxy_toml_new.size() ? dnscrypt_proxy_toml.size() : dnscrypt_proxy_toml_new.size());

        for (int i = 0; i < size; i++) {
            if (!dnscrypt_proxy_toml.get(i).equals(dnscrypt_proxy_toml_new.get(i))) {
                dnscrypt_proxy_toml = dnscrypt_proxy_toml_new;

                return true;
            }
        }

        return false;
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

    private void saveDNSServersToPrefs(String dnscrypt_servers) {
        if (getActivity() != null) {
            new PrefManager(getActivity()).setStrPref("DNSCrypt Servers", dnscrypt_servers);
        }
    }

    private String dnsServersListToLine() {
        dnscrypt_servers_current.clear();

        String line = "";

        StringBuilder dnscrypt_servers = new StringBuilder();
        dnscrypt_servers.append("[\"");

        for (int i = 0; i < list_dns_servers.size(); i++) {
            if (list_dns_servers.get(i).isChecked()) {
                dnscrypt_servers_current.add(list_dns_servers.get(i).getName());

                dnscrypt_servers.append(list_dns_servers.get(i).getName());
                dnscrypt_servers.append("\", \"");
            }
        }

        if (dnscrypt_servers.toString().equals("[\"")) {
            Toast.makeText(getActivity(), getText(R.string.pref_dnscrypt_select_server_names), Toast.LENGTH_LONG).show();
            return line;
        }

        dnscrypt_servers.delete(dnscrypt_servers.length() - 4, dnscrypt_servers.length()).append("\"]");

        line = dnscrypt_servers.toString();

        return line;
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

            if (dnscrypt_servers_current == null) {
                dnscrypt_servers_current = getArguments().getStringArrayList("dnscrypt_servers");
            }

            if (routes_current == null) {
                routes_current = (ArrayList<DNSServerRelays>) getArguments().getSerializable("routes");
            }
        } else {
            Log.e(LOG_TAG, "PreferencesDNSCryptServersRv getArguments() nullPointer");
        }
    }

    private void createAndFillRecyclerView() {

        if (getActivity() == null) {
            return;
        }

        final RecyclerView rvDNSServers = getActivity().findViewById(R.id.rvDNSServers);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        rvDNSServers.setLayoutManager(mLayoutManager);

        RecyclerView.Adapter dNSServersAdapter = new DNSServersAdapter(getActivity(),
                this, getFragmentManager(), list_dns_servers, routes_current);

        try {
            rvDNSServers.setAdapter(dNSServersAdapter);
        } catch (IllegalStateException e) {
            Log.e(LOG_TAG, "PreferencesDNSCryptServersRv setAdapter Exception " + e.getMessage());
        }

        if (lastAdapterPosition != 0) {
            rvDNSServers.scrollToPosition(lastAdapterPosition);
        }
    }

    private void fillDNSServersList() {
        Context context = getActivity();
        list_dns_servers = new ArrayList<>();
        for (int i = 0; i < dnsServerNames.size(); i++) {
            DNSServerItem dnsServer = new DNSServerItem(context, dnsServerNames.get(i), dnsServerDescr.get(i), dnsServerSDNS.get(i));
            setDnsServerChecked(dnsServer);
            setRoutes(dnsServer);

            if (dnsServer.isVisibility() && !dnsServerNames.get(i).contains("repeat_server"))
                list_dns_servers.add(dnsServer);
        }
    }

    private void setDnsServerChecked(DNSServerItem dnsServer) {
        for (int i = 0; i < dnscrypt_servers_current.size(); i++) {
            if (!dnsServer.getName().isEmpty() && dnsServer.getName().equals(dnscrypt_servers_current.get(i).trim()))
                dnsServer.setChecked(true);
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

    void setLastAdapterPosition(int lastAdapterPosition) {
        this.lastAdapterPosition = lastAdapterPosition;
    }
}
