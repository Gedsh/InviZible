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

package pan.alexander.tordnscrypt.settings.dnscrypt_relays;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitProgressDialog;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;

import javax.inject.Inject;


public class PreferencesDNSCryptRelays extends Fragment implements OnTextFileOperationsCompleteListener {

    @Inject
    public Lazy<PathVars> pathVars;

    private String dnsServerName;
    private final ArrayList<DnsRelayItem> dnsRelayItems = new ArrayList<>();
    private List<DnsServerRelay> routesCurrent = new ArrayList<>();
    private RecyclerView.Adapter<DnsRelaysAdapter.DNSRelaysViewHolder> adapter;
    private OnRoutesChangeListener onRoutesChangeListener;
    private DialogFragment pleaseWaitDialog;
    private boolean serverIPv6;


    public PreferencesDNSCryptRelays() {
    }

    public interface OnRoutesChangeListener {
        void onRoutesChange(CopyOnWriteArrayList<DnsServerRelay> routesNew);
    }

    public void setOnRoutesChangeListener(OnRoutesChangeListener onRoutesChangeListener) {
        this.onRoutesChangeListener = onRoutesChangeListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        takeArguments(getArguments());

        openPleaseWaitDialog();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        activity.setTitle(R.string.pref_dnscrypt_relays_title);

        View view = inflater.inflate(R.layout.fragment_preferences_dnscrypt_relays, container, false);

        RecyclerView rvDNSRelay = view.findViewById(R.id.rvDNSRelays);

        RecyclerView.LayoutManager manager = new LinearLayoutManager(activity);
        rvDNSRelay.setLayoutManager(manager);

        adapter = new DnsRelaysAdapter(activity, dnsRelayItems);
        rvDNSRelay.setAdapter(adapter);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        FileManager.setOnFileOperationCompleteListener(this);

        if (dnsRelayItems.isEmpty()) {
            FileManager.readTextFile(
                    requireContext(),
                    pathVars.get().getAppDataDir() + "/app_data/dnscrypt-proxy/relays.md", "relays.md"
            );
        }
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag, List<String> lines) {
        closePleaseWaitDialog();
        if (currentFileOperation == FileOperationsVariants.readTextFile && fileOperationResult && tag.equals("relays.md")) {
            fillDnsRelaysList(new ArrayList<>(lines));
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        FileManager.deleteOnFileOperationCompleteListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (dnsServerName == null || dnsRelayItems.isEmpty()) {
            return;
        }

        if (routesCurrent == null) {
            routesCurrent = new CopyOnWriteArrayList<>();
        }

        DnsServerRelay dnsServerRelaysNew = createRelaysObjForCurrentServer();

        CopyOnWriteArrayList<DnsServerRelay> routesNew = updateRelaysListForAllServers(dnsServerRelaysNew);

        callbackToPreferencesDNSCryptServers(routesNew);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        adapter = null;
        pleaseWaitDialog = null;
    }

    @SuppressWarnings("unchecked")
    private void takeArguments(Bundle args) {
        if (args != null) {
            dnsServerName = args.getString("dnsServerName");

            ArrayList<DnsServerRelay> routesCurrentTmp = (ArrayList<DnsServerRelay>) args.getSerializable("routesCurrent");

            if (routesCurrentTmp != null) {
                routesCurrent = routesCurrentTmp;
            }

            serverIPv6 = args.getBoolean("dnsServerIPv6");

        }
    }

    private void fillDnsRelaysList(List<String> lines) {
        String name = "";
        String description = "";
        boolean lockRelay = false;

        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }

            if (line.contains("##")) {
                name = line.replace("##", "").trim();
                lockRelay = true;
            } else if (lockRelay && line.contains("sdns://")) {
                lockRelay = false;
            } else if (lockRelay) {
                description = line.replaceAll("\\s", " ").trim();
            }

            if (!name.isEmpty() && !description.isEmpty() && !lockRelay) {
                DnsRelayItem dnsRelayItem = new DnsRelayItem(name, description);

                dnsRelayItem.setChecked(isDnsRelaySelected(name));

                boolean addServer;

                boolean relayIPv6 = name.contains("ipv6");

                if (serverIPv6) {
                    addServer = relayIPv6;
                } else {
                    addServer = !relayIPv6;
                }

                if (addServer) {
                    dnsRelayItems.add(dnsRelayItem);
                }


                name = "";
                description = "";
            }
        }
        Collections.sort(dnsRelayItems);

        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> adapter.notifyDataSetChanged());
        }
    }

    private boolean isDnsRelaySelected(String name) {
        boolean result = false;

        if (routesCurrent == null || dnsServerName == null) {
            return false;
        }

        for (int i = 0; i < routesCurrent.size(); i++) {
            DnsServerRelay dnsServerRelays = routesCurrent.get(i);
            if (dnsServerRelays.dnsServerName().equals(dnsServerName)) {
                if (dnsServerRelays.dnsServerRelays().contains(name)) {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    private DnsServerRelay createRelaysObjForCurrentServer() {
        ArrayList<String> dnsRelaysNamesForCurrentServer = new ArrayList<>();
        for (DnsRelayItem dnsRelayItem : dnsRelayItems) {
            if (dnsRelayItem.isChecked()) {
                dnsRelaysNamesForCurrentServer.add(dnsRelayItem.getName());
            }
        }

        DnsServerRelay dnsServerRelaysNew = null;
        if (!dnsRelaysNamesForCurrentServer.isEmpty()) {
            dnsServerRelaysNew = new DnsServerRelay(dnsServerName, dnsRelaysNamesForCurrentServer);
        }

        return dnsServerRelaysNew;
    }

    private CopyOnWriteArrayList<DnsServerRelay> updateRelaysListForAllServers(DnsServerRelay dnsServerRelaysNew) {
        CopyOnWriteArrayList<DnsServerRelay> routesNew = new CopyOnWriteArrayList<>();

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

    private void callbackToPreferencesDNSCryptServers(CopyOnWriteArrayList<DnsServerRelay> routesNew) {
        if (onRoutesChangeListener != null) {
            onRoutesChangeListener.onRoutesChange(routesNew);
        }
    }

    private void openPleaseWaitDialog() {
        if (isAdded()) {
            pleaseWaitDialog = new PleaseWaitProgressDialog();
            pleaseWaitDialog.show(getParentFragmentManager(), "PleaseWaitProgressDialog");
        }
    }

    private void closePleaseWaitDialog() {
        if (pleaseWaitDialog != null && pleaseWaitDialog.isAdded()) {
            try {
                pleaseWaitDialog.dismiss();
                pleaseWaitDialog = null;
            } catch (Exception e) {
                logw("PreferencesDNSCryptRelays closePleaseWaitDialog", e);
            }
        }
    }

}
