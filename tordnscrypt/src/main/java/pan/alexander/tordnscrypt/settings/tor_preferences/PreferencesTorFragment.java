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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.tor_preferences;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.ConfigEditorFragment;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.tor_bridges.SnowflakeConfigurator;
import pan.alexander.tordnscrypt.settings.tor_countries.CountrySelectFragment;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SNOWFLAKE_RENDEZVOUS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY_ADDRESS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_DEFAULT_BRIDGES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_OWN_BRIDGES;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

import javax.inject.Inject;


public class PreferencesTorFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    public static final String ISOLATE_DEST_ADDRESS = "IsolateDestAddr";
    public static final String ISOLATE_DEST_PORT = "IsolateDestPort";
    private ArrayList<String> key_tor;
    private ArrayList<String> val_tor;
    private ArrayList<String> key_tor_orig;
    private ArrayList<String> val_tor_orig;
    private String appDataDir;
    public String entryNodes;
    public String excludeNodes;
    public String excludeExitNodes;
    public String exitNodes;
    private boolean isChanged;

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CachedExecutor cachedExecutor;
    @Inject
    public Lazy<SnowflakeConfigurator> snowflakeConfigurator;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);

        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_tor);

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (appVersion.endsWith("p")) {
            changePreferencesForGPVersion();
        }

        ArrayList<Preference> preferences = new ArrayList<>();

        preferences.add(findPreference("VirtualAddrNetwork"));
        preferences.add(findPreference("HardwareAccel"));
        preferences.add(findPreference("AvoidDiskWrites"));
        preferences.add(findPreference("ConnectionPadding"));
        preferences.add(findPreference("ReducedConnectionPadding"));
        preferences.add(findPreference("ExcludeExitNodes"));
        preferences.add(findPreference("ExitNodes"));
        preferences.add(findPreference("ExcludeNodes"));
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
        preferences.add(findPreference(TOR_OUTBOUND_PROXY));
        preferences.add(findPreference(TOR_OUTBOUND_PROXY_ADDRESS));
        preferences.add(findPreference("pref_tor_isolate_dest_address"));
        preferences.add(findPreference("pref_tor_isolate_dest_port"));
        preferences.add(findPreference(SNOWFLAKE_RENDEZVOUS));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            } else if (!appVersion.startsWith("g")) {
                Log.e(LOG_TAG, "PreferencesTorFragment preference is null exception");
            }
        }


        Preference entryNodesPref = findPreference("EntryNodes");
        boolean useDefaultBridges = preferenceRepository.get().getBoolPreference(USE_DEFAULT_BRIDGES);
        boolean useOwnBridges = preferenceRepository.get().getBoolPreference(USE_OWN_BRIDGES);
        boolean entryNodesActive = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("EntryNodes", false);
        if (entryNodesPref != null) {
            if (useDefaultBridges || useOwnBridges) {
                if (entryNodesActive) {
                    entryNodesPref.setOnPreferenceChangeListener(this);
                } else {
                    entryNodesPref.setEnabled(false);
                }
                entryNodesPref.setSummary(R.string.pref_tor_entry_nodes_alt_summ);
            } else {
                entryNodesPref.setOnPreferenceChangeListener(this);
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

        entryNodes = null;
        excludeNodes = null;
        excludeExitNodes = null;
        exitNodes = null;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        activity.setTitle(R.string.drawer_menu_TorSettings);

        appDataDir = pathVars.get().getAppDataDir();

        isChanged = false;

        if (getArguments() != null) {
            key_tor = getArguments().getStringArrayList("key_tor");
            val_tor = getArguments().getStringArrayList("val_tor");
            key_tor_orig = new ArrayList<>(key_tor);
            val_tor_orig = new ArrayList<>(val_tor);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (key_tor == null || val_tor == null
                || key_tor_orig == null || val_tor_orig == null) {
            return;
        }

        if (entryNodes != null && key_tor.contains("EntryNodes")) {
            val_tor.set(key_tor.indexOf("EntryNodes"), entryNodes);
            entryNodes = null;
        } else if (entryNodes != null && key_tor.contains("#EntryNodes")) {
            val_tor.set(key_tor.indexOf("#EntryNodes"), entryNodes);
            entryNodes = null;
        } else if (excludeNodes != null && key_tor.contains("ExcludeNodes")) {
            val_tor.set(key_tor.indexOf("ExcludeNodes"), excludeNodes);
            excludeNodes = null;
        } else if (excludeNodes != null && key_tor.contains("#ExcludeNodes")) {
            val_tor.set(key_tor.indexOf("#ExcludeNodes"), excludeNodes);
            excludeNodes = null;
        } else if (excludeExitNodes != null && key_tor.contains("ExcludeExitNodes")) {
            val_tor.set(key_tor.indexOf("ExcludeExitNodes"), excludeExitNodes);
            excludeExitNodes = null;
        } else if (excludeExitNodes != null && key_tor.contains("#ExcludeExitNodes")) {
            val_tor.set(key_tor.indexOf("#ExcludeExitNodes"), excludeExitNodes);
            excludeExitNodes = null;
        } else if (exitNodes != null && key_tor.contains("ExitNodes")) {
            val_tor.set(key_tor.indexOf("ExitNodes"), exitNodes);
            exitNodes = null;
        } else if (exitNodes != null && key_tor.contains("#ExitNodes")) {
            val_tor.set(key_tor.indexOf("#ExitNodes"), exitNodes);
            exitNodes = null;
        }

        List<String> tor_conf = new LinkedList<>();
        for (int i = 0; i < key_tor.size(); i++) {

            if (!isChanged
                    && (key_tor_orig.size() != key_tor.size()
                    || !key_tor_orig.get(i).equals(key_tor.get(i))
                    || !val_tor_orig.get(i).equals(val_tor.get(i)))) {
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

        if (!isChanged) {
            return;
        }

        FileManager.writeToTextFile(context, appDataDir + "/app_data/tor/tor.conf", tor_conf, SettingsActivity.tor_conf_tag);

        boolean torRunning = ModulesAux.isTorSavedStateRunning();

        if (torRunning) {
            ModulesRestarter.restartTor(context);
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
        }

    }

    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {

        Context context = getActivity();
        if (context == null || key_tor == null || val_tor == null) {
            return false;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isolateDestAddress = sharedPreferences.getBoolean("pref_tor_isolate_dest_address", false);
        boolean isolateDestPort = sharedPreferences.getBoolean("pref_tor_isolate_dest_port", false);
        boolean allowTorTethering = sharedPreferences.getBoolean(TOR_TETHERING, false);

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
        } else if (Objects.equals(preference.getKey(), "HardwareAccel")) {
            if (Boolean.parseBoolean(newValue.toString())
                    && !key_tor.contains("HardwareAccel") && key_tor.contains("Schedulers") ) {
                key_tor.add(key_tor.indexOf("Schedulers"), "HardwareAccel");
                val_tor.add(key_tor.indexOf("HardwareAccel"), newValue.toString());
            }
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

            boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                    && ModulesStatus.getInstance().isUseModulesWithRoot();
            if (!newValue.toString().matches("\\d+")
                    || (!useModulesWithRoot && Integer.parseInt(newValue.toString()) < 1024)) {
                return false;
            }

            ModifyForwardingRules modifyForwardingRules = new ModifyForwardingRules(context,
                    "onion 127.0.0.1:" + newValue.toString().trim());
            cachedExecutor.submit(modifyForwardingRules.getRunnable());
        } else if (Objects.equals(preference.getKey(), SNOWFLAKE_RENDEZVOUS)) {

            if (!key_tor.contains("ClientTransportPlugin")) {
                return true;
            }

            int index = key_tor.indexOf("ClientTransportPlugin");

            if (val_tor.get(index).contains("snowflake")) {
                val_tor.set(index, snowflakeConfigurator.get()
                        .getConfiguration(Integer.parseInt(newValue.toString())));
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "pref_tor_snowflake_stun")) {

            String serversStr = newValue.toString().trim();

            if (serversStr.isEmpty()) {
                return false;
            }

            String[] servers = serversStr.split(", ?");
            for (String server: servers) {
                if (!server.matches(".+\\..+:\\d+")) {
                    return false;
                }
            }

            if (!key_tor.contains("ClientTransportPlugin")) {
                return true;
            }

            int index = key_tor.indexOf("ClientTransportPlugin");

            if (val_tor.get(index).contains("snowflake")) {
                val_tor.set(index, snowflakeConfigurator.get()
                        .getConfiguration(serversStr));
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "SOCKSPort")
                || Objects.equals(preference.getKey(), "HTTPTunnelPort")
                || Objects.equals(preference.getKey(), "TransPort")) {

            boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                    && ModulesStatus.getInstance().isUseModulesWithRoot();
            if (!newValue.toString().matches("\\d+")
                    || (!useModulesWithRoot && Integer.parseInt(newValue.toString()) < 1024)) {
                return false;
            }

            newValue = addIsolateFlags(newValue, allowTorTethering, isolateDestAddress, isolateDestPort);
        } else if (Objects.equals(preference.getKey(), "pref_tor_isolate_dest_address")) {
            if (key_tor.contains("SOCKSPort")) {
                int index = key_tor.indexOf("SOCKSPort");
                String val = val_tor.get(index).split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
                val = addIsolateFlags(val, allowTorTethering, Boolean.parseBoolean(newValue.toString()), isolateDestPort);
                val_tor.set(index, val);
            }
            if (key_tor.contains("HTTPTunnelPort")) {
                int index = key_tor.indexOf("HTTPTunnelPort");
                String val = val_tor.get(index).split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
                val = addIsolateFlags(val, allowTorTethering, Boolean.parseBoolean(newValue.toString()), isolateDestPort);
                val_tor.set(index, val);
            }
            if (key_tor.contains("TransPort")) {
                int index = key_tor.indexOf("TransPort");
                String val = val_tor.get(index).split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
                val = addIsolateFlags(val, allowTorTethering, Boolean.parseBoolean(newValue.toString()), isolateDestPort);
                val_tor.set(index, val);
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "pref_tor_isolate_dest_port")) {
            if (key_tor.contains("SOCKSPort")) {
                int index = key_tor.indexOf("SOCKSPort");
                String val = val_tor.get(index).split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
                val = addIsolateFlags(val, allowTorTethering, isolateDestAddress, Boolean.parseBoolean(newValue.toString()));
                val_tor.set(index, val);
            }
            if (key_tor.contains("HTTPTunnelPort")) {
                int index = key_tor.indexOf("HTTPTunnelPort");
                String val = val_tor.get(index).split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
                val = addIsolateFlags(val, allowTorTethering, isolateDestAddress, Boolean.parseBoolean(newValue.toString()));
                val_tor.set(index, val);
            }
            if (key_tor.contains("TransPort")) {
                int index = key_tor.indexOf("TransPort");
                String val = val_tor.get(index).split(" ")[0].replaceAll(".+:", "").replaceAll("\\D+", "");
                val = addIsolateFlags(val, allowTorTethering, isolateDestAddress, Boolean.parseBoolean(newValue.toString()));
                val_tor.set(index, val);
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "VirtualAddrNetwork")
                && !newValue.toString().matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/\\d+$")) {
            return false;
        } else if ((Objects.equals(preference.getKey(), "NewCircuitPeriod") || Objects.equals(preference.getKey(), "MaxCircuitDirtiness"))
                && !newValue.toString().matches("\\d+")) {
            return false;
        } else if ((Objects.equals(preference.getKey(), TOR_OUTBOUND_PROXY))) {
            if (Boolean.parseBoolean(newValue.toString())) {
                if (key_tor.contains("#Socks5Proxy")) {
                    key_tor.set(key_tor.indexOf("#Socks5Proxy"), "Socks5Proxy");
                } else if (key_tor.contains("ClientOnly") && !key_tor.contains("Socks5Proxy")) {
                    int index = key_tor.indexOf("ClientOnly");
                    key_tor.add(index, "Socks5Proxy");
                    val_tor.add(index, "127.0.0.1:1080");
                }
            } else if (key_tor.contains("Socks5Proxy")) {
                key_tor.set(key_tor.indexOf("Socks5Proxy"), "#Socks5Proxy");
            }
            return true;
        } else if (Objects.equals(preference.getKey(), "Socks5Proxy")
                && !newValue.toString().matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(:\\d+)?$")) {
            return false;
        }

        if (key_tor.contains(preference.getKey().trim())) {
            val_tor.set(key_tor.indexOf(preference.getKey()), newValue.toString());
            return true;
        } else {
            Toast.makeText(context, R.string.pref_tor_not_exist, Toast.LENGTH_SHORT).show();
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
        if (!isAdded() || key_tor == null || val_tor == null) {
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
    public boolean onPreferenceClick(@NonNull Preference preference) {
        Context context = getActivity();
        if (context == null || !isAdded()) {
            return false;
        }

        if ("cleanTorFolder".equals(preference.getKey())) {

            if (ModulesStatus.getInstance().getTorState() != STOPPED) {
                Toast.makeText(context, R.string.btnTorStop, Toast.LENGTH_SHORT).show();
                return true;
            }

            cachedExecutor.submit(() -> {
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }

                boolean successfully = FileManager.deleteDirSynchronous(activity, appDataDir + "/tor_data");

                if (successfully) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show());
                } else {
                    activity.runOnUiThread(() -> Toast.makeText(activity, R.string.wrong, Toast.LENGTH_SHORT).show());
                }
            });


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
            preferences.add(findPreference("AvoidDiskWrites"));
            preferences.add(findPreference("ConnectionPadding"));
            preferences.add(findPreference("ReducedConnectionPadding"));
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
