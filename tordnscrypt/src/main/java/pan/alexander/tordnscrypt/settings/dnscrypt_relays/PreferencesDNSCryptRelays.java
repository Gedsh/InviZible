package pan.alexander.tordnscrypt.settings;


import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnTextFileOperationsCompleteListener;

/**
 * A simple {@link Fragment} subclass.
 */
public class PreferencesDNSCryptRelays extends Fragment implements OnTextFileOperationsCompleteListener {
    private String dnsServerName;
    private String[] currentRelaysNames;
    private ArrayList<DNSRelay> dnsRelays;
    private PathVars pathVars;


    public PreferencesDNSCryptRelays() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        if (getActivity() == null) {
            return;
        }

        pathVars = new PathVars(getActivity());

        if (getArguments() != null) {
            dnsServerName = getArguments().getString("dnsServerName");

            String currentRelaysNamesString = getArguments().getString("currentRelaysNames");

            if (currentRelaysNamesString != null && currentRelaysNamesString.contains(":")) {
                currentRelaysNames = currentRelaysNamesString.substring(currentRelaysNamesString.indexOf(":") + 1,
                        currentRelaysNamesString.indexOf(".")).split(", *");
            }
        }

        FileOperations.setOnFileOperationCompleteListener(this);

        FileOperations.readTextFile(getActivity(), pathVars.appDataDir + "/app_data/dnscrypt-proxy/relays.md", "relays.md");


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_preferences_dnscrypt_relays, container, false);
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag, List<String> lines) {
        if (currentFileOperation == FileOperationsVariants.readTextFile && fileOperationResult && tag.equals("relay.md")) {
           fillDnsRelaysList(lines);
        }
    }

    private void fillDnsRelaysList(List<String> lines) {
        String name = "";
        String description = "";
        boolean lockRelay = false;

        for (String line: lines) {
            if (line.isEmpty()) {
                continue;
            }

            if (line.contains("##")) {
                name = line.replace("##", "").trim();
                lockRelay = true;
            } else if (lockRelay && line.contains("sdns://")) {
                lockRelay = false;
            } else {
                description = line.replaceAll("\\s", "").trim();
            }

            if (!name.isEmpty() && !description.isEmpty()) {
                DNSRelay dnsRelay = new DNSRelay(name, description);

                dnsRelay.setChecked(isDnsRelaySelected(name));

                dnsRelays.add(dnsRelay);
            }
        }
    }

    private boolean isDnsRelaySelected(String name) {
        boolean result = false;

        for (String relayName : currentRelaysNames) {
            if (relayName.trim().equals(name)) {
                result = true;
                break;
            }
        }

        return result;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        FileOperations.deleteOnFileOperationCompleteListener();
    }

    private class DNSRelay {
        String name;
        String description;
        boolean checked;

        public DNSRelay(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean checked) {
            this.checked = checked;
        }
    }

}
