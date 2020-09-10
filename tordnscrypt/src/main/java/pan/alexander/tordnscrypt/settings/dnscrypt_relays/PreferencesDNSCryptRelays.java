package pan.alexander.tordnscrypt.settings.dnscrypt_relays;

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitProgressDialog;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

/**
 * A simple {@link Fragment} subclass.
 */
public class PreferencesDNSCryptRelays extends Fragment implements OnTextFileOperationsCompleteListener {
    private String dnsServerName;
    private final ArrayList<DNSRelayItem> dnsRelayItems = new ArrayList<>();
    private ArrayList<DNSServerRelays> routesCurrent;
    private RecyclerView.Adapter<DNSRelaysAdapter.DNSRelaysViewHolder> adapter;
    private OnRoutesChangeListener onRoutesChangeListener;
    private static DialogFragment pleaseWaitDialog;
    private boolean serverIPv6;


    public PreferencesDNSCryptRelays() {
        // Required empty public constructor
    }

    public interface OnRoutesChangeListener {
        void onRoutesChange(ArrayList<DNSServerRelays> routesNew);
    }

    public void setOnRoutesChangeListener(OnRoutesChangeListener onRoutesChangeListener) {
        this.onRoutesChangeListener = onRoutesChangeListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActivity() == null) {
            return;
        }

        setRetainInstance(true);

        takeArguments(getArguments());

        openPleaseWaitDialog();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() == null) {
            return;
        }

        FileOperations.setOnFileOperationCompleteListener(this);

        PathVars pathVars = PathVars.getInstance(getActivity());

        if (dnsRelayItems.isEmpty()) {
            FileOperations.readTextFile(getActivity(), pathVars.getAppDataDir() + "/app_data/dnscrypt-proxy/relays.md", "relays.md");
        }

        getActivity().setTitle(R.string.pref_dnscrypt_relays_title);

        RecyclerView rvDNSRelay = getActivity().findViewById(R.id.rvDNSRelays);

        RecyclerView.LayoutManager manager = new LinearLayoutManager(getActivity());
        rvDNSRelay.setLayoutManager(manager);

        adapter = new DNSRelaysAdapter(getActivity(), dnsRelayItems);
        rvDNSRelay.setAdapter(adapter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preferences_dnscrypt_relays, container, false);
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

        FileOperations.deleteOnFileOperationCompleteListener();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (dnsServerName == null || dnsRelayItems.size() == 0) {
            return;
        }

        if (routesCurrent == null) {
            routesCurrent = new ArrayList<>();
        }

        DNSServerRelays dnsServerRelaysNew = createRelaysObjForCurrentServer();

        ArrayList<DNSServerRelays> routesNew = updateRelaysListForAllServers(dnsServerRelaysNew);

        callbackToPreferencesDNSCryptServers(routesNew);
    }

    @SuppressWarnings("unchecked")
    private void takeArguments(Bundle args) {
        if (args != null) {
            dnsServerName = args.getString("dnsServerName");

            ArrayList<DNSServerRelays> routesCurrentTmp = (ArrayList<DNSServerRelays>) args.getSerializable("routesCurrent");

            if (routesCurrentTmp != null) {
                routesCurrent = (ArrayList<DNSServerRelays>) routesCurrentTmp;
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
                DNSRelayItem dnsRelayItem = new DNSRelayItem(name, description);

                dnsRelayItem.setChecked(isDnsRelaySelected(name));

                boolean addServer;

                boolean relayIPv6 = false;
                if (name.contains("ipv6")) {
                    relayIPv6 = true;
                }

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

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
        }
    }

    private boolean isDnsRelaySelected(String name) {
        boolean result = false;

        if (routesCurrent == null || dnsServerName == null) {
            return false;
        }

        for (int i = 0; i < routesCurrent.size(); i++) {
            DNSServerRelays dnsServerRelays = routesCurrent.get(i);
            if (dnsServerRelays.getDnsServerName().equals(dnsServerName)) {
                if (dnsServerRelays.getDnsServerRelays().contains(name)) {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    private DNSServerRelays createRelaysObjForCurrentServer() {
        ArrayList<String> dnsRelaysNamesForCurrentServer = new ArrayList<>();
        for (DNSRelayItem dnsRelayItem : dnsRelayItems) {
            if (dnsRelayItem.isChecked()) {
                dnsRelaysNamesForCurrentServer.add(dnsRelayItem.getName());
            }
        }

        DNSServerRelays dnsServerRelaysNew = null;
        if (!dnsRelaysNamesForCurrentServer.isEmpty()) {
            dnsServerRelaysNew = new DNSServerRelays(dnsServerName, dnsRelaysNamesForCurrentServer);
        }

        return dnsServerRelaysNew;
    }

    private ArrayList<DNSServerRelays> updateRelaysListForAllServers(DNSServerRelays dnsServerRelaysNew) {
        ArrayList<DNSServerRelays> routesNew = new ArrayList<>();

        for (int i = 0; i < routesCurrent.size(); i++) {

            DNSServerRelays dnsServerRelays = routesCurrent.get(i);

            if (dnsServerRelays.getDnsServerName().equals(dnsServerName)) {
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

    private void callbackToPreferencesDNSCryptServers(ArrayList<DNSServerRelays> routesNew) {
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
        if (pleaseWaitDialog != null) {
            try {
                pleaseWaitDialog.dismiss();
            } catch (Exception e) {
                Log.w(LOG_TAG, "PreferencesDNSCryptRelays closePleaseWaitDialog Exception: " + e.getMessage() + " " + e.getCause());
            }
        }
    }

}
