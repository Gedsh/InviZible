package pan.alexander.tordnscrypt.settings;
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

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class PreferencesDNSFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    ArrayList<String> key_toml;
    ArrayList<String> val_toml;
    ArrayList<String> key_toml_orig;
    ArrayList<String> val_toml_orig;
    String appDataDir;
    String dnscryptPath;
    String busyboxPath;
    private boolean isChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_dnscrypt);


        findPreference("listen_port").setOnPreferenceChangeListener(this);
        findPreference("max_clients").setOnPreferenceChangeListener(this);
        findPreference("ipv4_servers").setOnPreferenceChangeListener(this);
        findPreference("ipv6_servers").setOnPreferenceChangeListener(this);
        findPreference("dnscrypt_servers").setOnPreferenceChangeListener(this);
        findPreference("doh_servers").setOnPreferenceChangeListener(this);
        findPreference("require_dnssec").setOnPreferenceChangeListener(this);
        findPreference("require_nolog").setOnPreferenceChangeListener(this);
        findPreference("require_nofilter").setOnPreferenceChangeListener(this);
        findPreference("force_tcp").setOnPreferenceChangeListener(this);
        findPreference("Enable proxy").setOnPreferenceChangeListener(this);
        findPreference("proxy_port").setOnPreferenceChangeListener(this);
        findPreference("timeout").setOnPreferenceChangeListener(this);
        findPreference("keepalive").setOnPreferenceChangeListener(this);
        findPreference("cert_refresh_delay").setOnPreferenceChangeListener(this);
        findPreference("dnscrypt_ephemeral_keys").setOnPreferenceChangeListener(this);
        findPreference("tls_disable_session_tickets").setOnPreferenceChangeListener(this);
        findPreference("fallback_resolver").setOnPreferenceChangeListener(this);
        findPreference("ignore_system_dns").setOnPreferenceChangeListener(this);
        findPreference("netprobe_timeout").setOnPreferenceChangeListener(this);
        findPreference("block_ipv6").setOnPreferenceChangeListener(this);
        findPreference("Enable DNS cache").setOnPreferenceChangeListener(this);
        findPreference("cache_size").setOnPreferenceChangeListener(this);
        findPreference("cache_min_ttl").setOnPreferenceChangeListener(this);
        findPreference("cache_max_ttl").setOnPreferenceChangeListener(this);
        findPreference("cache_neg_min_ttl").setOnPreferenceChangeListener(this);
        findPreference("cache_neg_max_ttl").setOnPreferenceChangeListener(this);
        findPreference("Enable Query logging").setOnPreferenceChangeListener(this);
        findPreference("ignored_qtypes").setOnPreferenceChangeListener(this);
        findPreference("Enable Suspicious logging").setOnPreferenceChangeListener(this);
        findPreference("Sources").setOnPreferenceChangeListener(this);
        findPreference("refresh_delay").setOnPreferenceChangeListener(this);
        findPreference("minisign_key").setOnPreferenceChangeListener(this);

        if (getArguments() != null) {
            key_toml = getArguments().getStringArrayList("key_toml");
            val_toml = getArguments().getStringArrayList("val_toml");
            key_toml_orig = new ArrayList<>(key_toml);
            val_toml_orig = new ArrayList<>(val_toml);
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

        getActivity().setTitle(R.string.drawer_menu_DNSSettings);

        PathVars pathVars = new PathVars(getActivity());
        appDataDir = pathVars.appDataDir;
        dnscryptPath = pathVars.dnscryptPath;
        busyboxPath = pathVars.busyboxPath;


    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() == null) {
            return;
        }

        List<String> dnscrypt_proxy_toml = new LinkedList<>();
        for (int i = 0; i < key_toml.size(); i++) {
            if (!(key_toml_orig.get(i).equals(key_toml.get(i)) && val_toml_orig.get(i).equals(val_toml.get(i))) && !isChanged) {
                isChanged = true;
            }

            if (val_toml.get(i).isEmpty()) {
                dnscrypt_proxy_toml.add(key_toml.get(i));
            } else {
                dnscrypt_proxy_toml.add(key_toml.get(i) + " = " + val_toml.get(i));
            }

        }

        if (!isChanged) return;

        FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml", dnscrypt_proxy_toml, SettingsActivity.dnscrypt_proxy_toml_tag);

        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");

        if (dnsCryptRunning) {
            ModulesRestarter.restartDNSCrypt(getActivity());
        }
    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        try {
            if (Objects.equals(preference.getKey(), "listen_port")) {
                String val = "[\"127.0.0.1:" + newValue.toString() + "\"]";
                val_toml.set(key_toml.indexOf("listen_addresses"), val);
                return true;
            } else if (Objects.equals(preference.getKey(), "fallback_resolver")) {
                String val = "\"" + newValue.toString() + ":53\"";
                val_toml.set(key_toml.indexOf("fallback_resolver"), val);
                if (key_toml.indexOf("netprobe_address") > 0) {
                    val_toml.set(key_toml.indexOf("netprobe_address"), val);
                }
                return true;
            } else if (Objects.equals(preference.getKey(), "proxy_port")) {
                String val = "\"socks5://127.0.0.1:" + newValue.toString() + "\"";
                val_toml.set(key_toml.indexOf("proxy"), val);
                return true;
            } else if (Objects.equals(preference.getKey(), "Enable DNS cache")) {
                val_toml.set(key_toml.indexOf("cache"), newValue.toString());
                return true;
            } else if (Objects.equals(preference.getKey(), "Sources")) {
                val_toml.set(key_toml.indexOf("urls"), newValue.toString());
                return true;
            } else if (Objects.equals(preference.getKey(), "Enable proxy")) {
                if (Boolean.valueOf(newValue.toString())) {
                    key_toml.set(key_toml.indexOf("#proxy"), "proxy");
                } else {
                    key_toml.set(key_toml.indexOf("proxy"), "#proxy");
                }
                return true;
            } else if (Objects.equals(preference.getKey().trim(), "Enable Query logging")) {
                if (Boolean.valueOf(newValue.toString())) {
                    key_toml.set(val_toml.indexOf("\"" + appDataDir + "/cache/query.log\""), "file");
                } else {
                    key_toml.set(val_toml.indexOf("\"" + appDataDir + "/cache/query.log\""), "#file");
                }
                return true;
            } else if (Objects.equals(preference.getKey().trim(), "Enable Suspicious logging")) {
                if (Boolean.valueOf(newValue.toString())) {
                    key_toml.set(val_toml.indexOf("\"" + appDataDir + "/cache/nx.log\""), "file");
                } else {
                    key_toml.set(val_toml.indexOf("\"" + appDataDir + "/cache/nx.log\""), "#file");
                }
                return true;
            }

            if (key_toml.contains(preference.getKey().trim())) {
                val_toml.set(key_toml.indexOf(preference.getKey()), newValue.toString());
                return true;
            } else {
                Toast.makeText(getActivity(), R.string.pref_dnscrypt_not_exist, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "PreferencesDNSFragment exception " + e.getMessage() + " " + e.getCause());
            Toast.makeText(getActivity(), R.string.wrong, Toast.LENGTH_LONG).show();
        }

        return false;
    }
}
