package pan.alexander.tordnscrypt.settings.tor_preferences;
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.ConfigEditorFragment;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.tor_countries.CountrySelectFragment;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;


public class PreferencesTorFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    public static final String ISOLATE_DEST_ADDRESS = "IsolateDestAddr";
    public static final String ISOLATE_DEST_PORT = "IsolateDestPort";
    public static ArrayList<String> key_tor;
    public static ArrayList<String> val_tor;
    private ArrayList<String> key_tor_orig;
    private ArrayList<String> val_tor_orig;
    private String appDataDir;
    private boolean isChanged;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_tor);

        if (appVersion.endsWith("p")) {
            changePreferencesForGPVersion();
        }

        ArrayList<Preference> preferences = new ArrayList<>();

        preferences.add(findPreference("VirtualAddrNetwork"));
        preferences.add(findPreference("AvoidDiskWrites"));
        preferences.add(findPreference("ConnectionPadding"));
        preferences.add(findPreference("ReducedConnectionPadding"));
        preferences.add(findPreference("ExcludeExitNodes"));
        preferences.add(findPreference("ExitNodes"));
        preferences.add(findPreference("ExcludeNodes"));
        preferences.add(findPreference("EntryNodes"));
        preferences.add(findPreference("StrictNodes"));
        preferences.add(findPreference("FascistFirewall"));
        preferences.add(findPreference("NewCircuitPeriod"));
        preferences.add(findPreference("MaxCircuitDirtiness"));
        preferences.add(findPreference("EnforceDistinctSubnets"));
        preferences.add(findPreference("Enable SOCKS proxy"));
        preferences.add(findPreference("SOCKSPort"));
        preferences.add(findPreference("Enable HTTPTunnel"));
        preferences.add(findPreference("HTTPTunnelPort"));
        preferences.add(findPreference("Enable Transparent proxy"));
        preferences.add(findPreference("TransPort"));
        preferences.add(findPreference("Enable DNS"));
        preferences.add(findPreference("DNSPort"));
        preferences.add(findPreference("ClientUseIPv4"));
        preferences.add(findPreference("ClientUseIPv6"));
        preferences.add(findPreference("pref_tor_snowflake_stun"));
        preferences.add(findPreference("pref_tor_isolate_dest_address"));
        preferences.add(findPreference("pref_tor_isolate_dest_port"));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            } else if (!appVersion.startsWith("g")) {
                Log.e(LOG_TAG, "PreferencesTorFragment preference is null exception");
            }
        }

        Preference editTorConfDirectly = findPreference("editTorConfDirectly");
        if (editTorConfDirectly != null) {
            editTorConfDirectly.setOnPreferenceClickListener(this);
        }

        Preference cleanTorFolder = findPreference("cleanTorFolder");
        if (cleanTorFolder != null) {
            cleanTorFolder.setOnPreferenceClickListener(this);
        }

        if (getArguments() != null) {
            key_tor = getArguments().getStringArrayList("key_tor");
            val_tor = getArguments().getStringArrayList("val_tor");
            key_tor_orig = new ArrayList<>(key_tor);
            val_tor_orig = new ArrayList<>(val_tor);
        }
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() == null) {
            return;
        }

        getActivity().setTitle(R.string.drawer_menu_TorSettings);

        PathVars pathVars = PathVars.getInstance(getActivity());
        appDataDir = pathVars.getAppDataDir();
    }

    @Override
    public void onStop() {
        super.onStop();

        List<String> tor_conf = new LinkedList<>();
        for (int i = 0; i < key_tor.size(); i++) {

            if (key_tor.size() != key_tor_orig.size()
                    || (!(key_tor_orig.get(i).equals(key_tor.get(i))
                    && val_tor_orig.get(i).equals(val_tor.get(i)))
                    && !isChanged)) {
                isChanged = true;
            }

            if (val_tor.get(i).isEmpty()) {
                tor_conf.add(key_tor.get(i));
            } else {
                String val = val_tor.get(i);
                if (val.equals("true")) val = "1";
                if (val.equals("false")) val = "0";
                tor_conf.add(key_tor.get(i) + " " + val);
            }

        }

        if (!isChanged || getActivity() == null) return;

        FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/tor.conf", tor_conf, SettingsActivity.tor_conf_tag);

        boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");

        if (torRunning) {
            ModulesRestarter.restartTor(getActivity());
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
            ModulesAux.requestModulesStatusUpdate(getActivity());
        }

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (getActivity() == null) {
            return false;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean isolateDestAddress = sharedPreferences.getBoolean("pref_tor_isolate_dest_address", false);
        boolean isolateDestPort = sharedPreferences.getBoolean("pref_tor_isolate_dest_port", false);
        boolean allowTorTethering = sharedPreferences.getBoolean("pref_common_tor_tethering", false);

        if (Objects.equals(preference.getKey(), "ExcludeExitNodes")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#ExcludeExitNodes")) {
                key_tor.set(key_tor.indexOf("#ExcludeExitNodes"), "ExcludeExitNodes");
                openCountrySelectFragment(CountrySelectFragment.excludeExitNodes, "ExcludeExitNodes");
            } else if (key_tor.contains("ExcludeExitNodes")) {
                key_tor.set(key_tor.indexOf("ExcludeExitNodes"), "#ExcludeExitNodes");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "ExitNodes")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#ExitNodes")) {
                key_tor.set(key_tor.indexOf("#ExitNodes"), "ExitNodes");
                openCountrySelectFragment(CountrySelectFragment.exitNodes, "ExitNodes");
            } else if (key_tor.contains("ExitNodes")) {
                key_tor.set(key_tor.indexOf("ExitNodes"), "#ExitNodes");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "ExcludeNodes")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#ExcludeNodes")) {
                key_tor.set(key_tor.indexOf("#ExcludeNodes"), "ExcludeNodes");
                openCountrySelectFragment(CountrySelectFragment.excludeNodes, "ExcludeNodes");
            } else if (key_tor.contains("ExcludeNodes")) {
                key_tor.set(key_tor.indexOf("ExcludeNodes"), "#ExcludeNodes");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "EntryNodes")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#EntryNodes")) {
                key_tor.set(key_tor.indexOf("#EntryNodes"), "EntryNodes");
                openCountrySelectFragment(CountrySelectFragment.entryNodes, "EntryNodes");
            } else if (key_tor.contains("EntryNodes")) {
                key_tor.set(key_tor.indexOf("EntryNodes"), "#EntryNodes");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Enable SOCKS proxy")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#SOCKSPort")) {
                key_tor.set(key_tor.indexOf("#SOCKSPort"), "SOCKSPort");
            } else if (key_tor.contains("SOCKSPort")) {
                key_tor.set(key_tor.indexOf("SOCKSPort"), "#SOCKSPort");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Enable HTTPTunnel")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#HTTPTunnelPort")) {
                key_tor.set(key_tor.indexOf("#HTTPTunnelPort"), "HTTPTunnelPort");
            } else if (key_tor.contains("HTTPTunnelPort")) {
                key_tor.set(key_tor.indexOf("HTTPTunnelPort"), "#HTTPTunnelPort");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Enable Transparent proxy")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#TransPort")) {
                key_tor.set(key_tor.indexOf("#TransPort"), "TransPort");
            } else if (key_tor.contains("TransPort")) {
                key_tor.set(key_tor.indexOf("TransPort"), "#TransPort");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Enable DNS")) {
            if (Boolean.parseBoolean(newValue.toString()) && key_tor.contains("#DNSPort")) {
                key_tor.set(key_tor.indexOf("#DNSPort"), "DNSPort");
            } else if (key_tor.contains("DNSPort")) {
                key_tor.set(key_tor.indexOf("DNSPort"), "#DNSPort");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "DNSPort")) {
            ModifyForwardingRules modifyForwardingRules = new ModifyForwardingRules(getActivity(),
                    "onion 127.0.0.1:" + newValue.toString().trim());
            modifyForwardingRules.start();
        } else if (Objects.equals(preference.getKey(), "pref_tor_snowflake_stun")) {
            if (key_tor.contains("ClientTransportPlugin") && getActivity() != null) {
                boolean saveExtendedLogs = new PrefManager(getActivity()).getBoolPref("swRootCommandsLog");
                String saveLogsString = "";
                if (saveExtendedLogs) {
                    saveLogsString = " -log " + appDataDir + "/logs/Snowflake.log";
                }
                int index = key_tor.indexOf("ClientTransportPlugin");
                String clientTransportPlugin = val_tor.get(index);
                val_tor.set(index, clientTransportPlugin.replaceAll("stun:.+", "stun:" + newValue.toString().trim() + saveLogsString));
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "SOCKSPort")
                || Objects.equals(preference.getKey(), "HTTPTunnelPort")
                || Objects.equals(preference.getKey(), "TransPort")) {
            newValue = addIsolateFlags(newValue, allowTorTethering, isolateDestAddress, isolateDestPort);
        } else if (Objects.equals(preference.getKey(), "pref_tor_isolate_dest_address")) {
            if (key_tor.contains("SOCKSPort")) {
                int index = key_tor.indexOf("SOCKSPort");
                String val = val_tor.get(index).split(" ")[0];
                val = addIsolateFlags(val, allowTorTethering, Boolean.parseBoolean(newValue.toString()), isolateDestPort);
                val_tor.set(index, val);
            }
            if (key_tor.contains("HTTPTunnelPort")) {
                int index = key_tor.indexOf("HTTPTunnelPort");
                String val = val_tor.get(index).split(" ")[0];
                val = addIsolateFlags(val, allowTorTethering, Boolean.parseBoolean(newValue.toString()), isolateDestPort);
                val_tor.set(index, val);
            }
            if (key_tor.contains("TransPort")) {
                int index = key_tor.indexOf("TransPort");
                String val = val_tor.get(index).split(" ")[0];
                val = addIsolateFlags(val, allowTorTethering, Boolean.parseBoolean(newValue.toString()), isolateDestPort);
                val_tor.set(index, val);
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "pref_tor_isolate_dest_port")) {
            if (key_tor.contains("SOCKSPort")) {
                int index = key_tor.indexOf("SOCKSPort");
                String val = val_tor.get(index).split(" ")[0];
                val = addIsolateFlags(val, allowTorTethering, isolateDestAddress, Boolean.parseBoolean(newValue.toString()));
                val_tor.set(index, val);
            }
            if (key_tor.contains("HTTPTunnelPort")) {
                int index = key_tor.indexOf("HTTPTunnelPort");
                String val = val_tor.get(index).split(" ")[0];
                val = addIsolateFlags(val, allowTorTethering, isolateDestAddress, Boolean.parseBoolean(newValue.toString()));
                val_tor.set(index, val);
            }
            if (key_tor.contains("TransPort")) {
                int index = key_tor.indexOf("TransPort");
                String val = val_tor.get(index).split(" ")[0];
                val = addIsolateFlags(val, allowTorTethering, isolateDestAddress, Boolean.parseBoolean(newValue.toString()));
                val_tor.set(index, val);
            }
            return true;
        }

        if (key_tor.contains(preference.getKey().trim())) {
            val_tor.set(key_tor.indexOf(preference.getKey()), newValue.toString());
            return true;
        } else {
            Toast.makeText(getActivity(), R.string.pref_tor_not_exist, Toast.LENGTH_SHORT).show();
        }


        return false;
    }

    private String addIsolateFlags(Object val, boolean allowTorTethering, boolean isolateDestinationAddress, boolean isolateDestinationPort) {
        String value = val.toString();
        if (allowTorTethering) {
            value = "0.0.0.0:" + value;
        }
        if (isolateDestinationAddress) {
            value += " " + ISOLATE_DEST_ADDRESS;
        }
        if (isolateDestinationPort) {
            value += " " + ISOLATE_DEST_PORT;
        }
        return value;
    }

    private void openCountrySelectFragment(int nodesType, String keyStr) {
        if (!isAdded()) {
            return;
        }

        FragmentTransaction fTrans = getParentFragmentManager().beginTransaction();
        fTrans.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        Fragment frg = new CountrySelectFragment();
        Bundle bndl = new Bundle();
        bndl.putInt("nodes_type", nodesType);
        bndl.putString("countries", val_tor.get(key_tor.indexOf(keyStr)));
        frg.setArguments(bndl);
        fTrans.replace(android.R.id.content, frg, "CountrySelectFragment");
        fTrans.addToBackStack("CountrySelectFragmentTag");
        fTrans.commit();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (getActivity() == null || !isAdded()) {
            return false;
        }

        if ("cleanTorFolder".equals(preference.getKey())) {

            if (ModulesStatus.getInstance().getTorState() != STOPPED) {
                Toast.makeText(getActivity(), R.string.btnTorStop, Toast.LENGTH_SHORT).show();
                return true;
            }

            new Thread(() -> {
                boolean successfully = false;
                if (getActivity() != null) {
                    successfully = FileOperations.deleteDirSynchronous(getActivity(), appDataDir + "/tor_data");
                }

                if (getActivity() != null) {
                    if (successfully) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), R.string.done, Toast.LENGTH_SHORT).show());
                    } else {
                        getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), R.string.wrong, Toast.LENGTH_SHORT).show());
                    }

                }
            }).start();


            return true;
        } else if ("editTorConfDirectly".equals(preference.getKey())) {
            ConfigEditorFragment.openEditorFragment(getParentFragmentManager(), "tor.conf");
            return true;
        }
        return false;
    }

    private void changePreferencesForGPVersion() {
        PreferenceCategory torSettingsCategory = findPreference("tor_settings");

        if (torSettingsCategory != null) {
            ArrayList<Preference> preferences = new ArrayList<>();
            preferences.add(findPreference("ClientUseIPv4"));
            preferences.add(findPreference("ClientUseIPv6"));
            preferences.add(findPreference("AvoidDiskWrites"));
            preferences.add(findPreference("ConnectionPadding"));
            preferences.add(findPreference("ReducedConnectionPadding"));
            preferences.add(findPreference("MaxCircuitDirtiness"));
            preferences.add(findPreference("EnforceDistinctSubnets"));
            preferences.add(findPreference("Enable SOCKS proxy"));
            preferences.add(findPreference("Enable HTTPTunnel"));
            preferences.add(findPreference("Enable Transparent proxy"));
            preferences.add(findPreference("Enable DNS"));

            for (Preference preference : preferences) {
                if (preference != null) {
                    torSettingsCategory.removePreference(preference);
                }
            }
        }

        PreferenceCategory otherCategory = findPreference("pref_tor_other");
        Preference editTorConfDirectly = findPreference("editTorConfDirectly");
        if (otherCategory != null && editTorConfDirectly != null) {
            otherCategory.removePreference(editTorConfDirectly);
        }
    }
}
