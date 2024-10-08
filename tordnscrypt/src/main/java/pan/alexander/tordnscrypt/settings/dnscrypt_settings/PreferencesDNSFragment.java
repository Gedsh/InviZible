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

package pan.alexander.tordnscrypt.settings.dnscrypt_settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.ConfigEditorFragment;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType;
import pan.alexander.tordnscrypt.settings.show_rules.remote.UpdateRemoteRulesWorkManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.vpn.service.VpnBuilder;

import static pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.Constants.DNSCRYPT_RELAYS_SOURCE_IPV6;
import static pan.alexander.tordnscrypt.utils.Constants.DNSCRYPT_RESOLVERS_SOURCE_IPV6;
import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX_WITH_MASK;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.MAX_PORT_NUMBER;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_41;
import static pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_61;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_BLOCK_IPv6;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_BOOTSTRAP_RESOLVERS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_DNS64;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_DNS64_PREFIX;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_LISTEN_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_NETPROBE_ADDRESS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_OUTBOUND_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_RELAYS_REFRESH_DELAY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_RULES_REFRESH_DELAY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_SERVERS_REFRESH_DELAY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.HTTP3_QUIC;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IGNORE_SYSTEM_DNS;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

import javax.inject.Inject;
import javax.inject.Named;

public class PreferencesDNSFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener, RulesEraser.OnRulesErased {

    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CoroutineExecutor executor;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public SharedPreferences defaultPreferences;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    public Lazy<RulesEraser> rulesEraser;
    @Inject
    public Lazy<UpdateRemoteRulesWorkManager> updateRemoteRulesWorkManager;

    private ArrayList<String> key_toml;
    private ArrayList<String> val_toml;
    private ArrayList<String> key_toml_orig;
    private ArrayList<String> val_toml_orig;
    private String appDataDir;
    private boolean isChanged = false;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_dnscrypt);

        if (pathVars.get().getAppVersion().endsWith("p")) {
            removePreferencesWithGPVersion();
        }

        ArrayList<Preference> preferences = new ArrayList<>();

        preferences.add(findPreference(DNSCRYPT_LISTEN_PORT));
        preferences.add(findPreference("dnscrypt_servers"));
        preferences.add(findPreference("doh_servers"));
        preferences.add(findPreference("odoh_servers"));
        preferences.add(findPreference("require_dnssec"));
        preferences.add(findPreference("require_nolog"));
        preferences.add(findPreference("require_nofilter"));
        preferences.add(findPreference("ipv4_servers"));
        preferences.add(findPreference("ipv6_servers"));
        preferences.add(findPreference("force_tcp"));
        preferences.add(findPreference(DNSCRYPT_OUTBOUND_PROXY));
        preferences.add(findPreference("proxy_port"));
        preferences.add(findPreference(DNSCRYPT_BOOTSTRAP_RESOLVERS));
        preferences.add(findPreference(IGNORE_SYSTEM_DNS));
        preferences.add(findPreference(HTTP3_QUIC));
        preferences.add(findPreference("Enable Query logging"));
        preferences.add(findPreference("ignored_qtypes"));
        preferences.add(findPreference("Enable Suspicious logging"));
        preferences.add(findPreference("Sources"));
        preferences.add(findPreference(DNSCRYPT_SERVERS_REFRESH_DELAY));
        preferences.add(findPreference(DNSCRYPT_RULES_REFRESH_DELAY));
        preferences.add(findPreference("Relays"));
        preferences.add(findPreference(DNSCRYPT_RELAYS_REFRESH_DELAY));
        preferences.add(findPreference("block_unqualified"));
        preferences.add(findPreference("block_undelegated"));
        preferences.add(findPreference(DNSCRYPT_BLOCK_IPv6));
        preferences.add(findPreference(DNSCRYPT_DNS64));
        preferences.add(findPreference(DNSCRYPT_DNS64_PREFIX));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            } else if (!pathVars.get().getAppVersion().startsWith("g")) {
                loge("PreferencesDNSFragment preference is null exception");
            }
        }

        Preference editDNSTomlDirectly = findPreference("editDNSTomlDirectly");
        if (editDNSTomlDirectly != null) {
            editDNSTomlDirectly.setOnPreferenceClickListener(this);
        } else if (!pathVars.get().getAppVersion().startsWith("g")) {
            loge("PreferencesDNSFragment preference is null exception");
        }

        Preference cleanDNSCryptFolder = findPreference("cleanDNSCryptFolder");
        if (cleanDNSCryptFolder != null) {
            cleanDNSCryptFolder.setOnPreferenceClickListener(this);
        }

        if (ModulesStatus.getInstance().isUseModulesWithRoot()) {
            removeErasePrefs();
        } else {
            registerImportErasePrefs();
        }
    }

    private void registerImportErasePrefs() {
        ArrayList<Preference> preferences = new ArrayList<>();

        preferences.add(findPreference("erase_blacklist"));
        preferences.add(findPreference("erase_whitelist"));
        preferences.add(findPreference("erase_ipblacklist"));
        preferences.add(findPreference("erase_forwarding_rules"));
        preferences.add(findPreference("erase_cloaking_rules"));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceClickListener(this);
            } else if (!pathVars.get().getAppVersion().startsWith("g")) {
                loge("PreferencesDNSFragment preference is null exception");
            }
        }
    }

    private void removeErasePrefs() {
        PreferenceCategory forwarding = findPreference("pref_dnscrypt_forwarding_rules");
        Preference erase_forwarding_rules = findPreference("erase_forwarding_rules");
        if (forwarding != null && erase_forwarding_rules != null) {
            forwarding.removePreference(erase_forwarding_rules);
        }

        PreferenceCategory cloaking = findPreference("pref_dnscrypt_cloaking_rules");
        Preference erase_cloaking_rules = findPreference("erase_cloaking_rules");
        if (cloaking != null && erase_cloaking_rules != null) {
            cloaking.removePreference(erase_cloaking_rules);
        }

        PreferenceCategory blacklist = findPreference("pref_dnscrypt_blacklist");
        Preference erase_blacklist = findPreference("erase_blacklist");
        if (blacklist != null && erase_blacklist != null) {
            blacklist.removePreference(erase_blacklist);
        }

        PreferenceCategory ipblacklist = findPreference("pref_dnscrypt_ipblacklist");
        Preference erase_ipblacklist = findPreference("erase_ipblacklist");
        if (ipblacklist != null && erase_ipblacklist != null) {
            ipblacklist.removePreference(erase_ipblacklist);
        }

        PreferenceCategory whitelist = findPreference("pref_dnscrypt_whitelist");
        Preference erase_whitelist = findPreference("erase_whitelist");
        if (whitelist != null && erase_whitelist != null) {
            whitelist.removePreference(erase_whitelist);
        }
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

        activity.setTitle(R.string.drawer_menu_DNSSettings);

        appDataDir = pathVars.get().getAppDataDir();

        isChanged = false;

        if (getArguments() != null) {
            key_toml = getArguments().getStringArrayList("key_toml");
            val_toml = getArguments().getStringArrayList("val_toml");
            key_toml_orig = new ArrayList<>(key_toml);
            val_toml_orig = new ArrayList<>(val_toml);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();
        if (context == null || key_toml == null || val_toml == null || key_toml_orig == null || val_toml_orig == null) {
            return;
        }

        List<String> dnscrypt_proxy_toml = new LinkedList<>();
        for (int i = 0; i < key_toml.size(); i++) {
            if (!isChanged
                    && (key_toml_orig.size() != key_toml.size() || !key_toml_orig.get(i).equals(key_toml.get(i)) || !val_toml_orig.get(i).equals(val_toml.get(i)))) {
                isChanged = true;
            }

            if (val_toml.get(i).isEmpty()) {
                dnscrypt_proxy_toml.add(key_toml.get(i));
            } else {
                dnscrypt_proxy_toml.add(key_toml.get(i) + " = " + val_toml.get(i));
            }

        }

        if (!isChanged) return;

        FileManager.writeToTextFile(context, appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml", dnscrypt_proxy_toml, SettingsActivity.dnscrypt_proxy_toml_tag);

        boolean dnsCryptRunning = ModulesAux.isDnsCryptSavedStateRunning();

        if (dnsCryptRunning) {
            ModulesRestarter.restartDNSCrypt(context);
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
        }
    }


    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {

        Context context = getActivity();
        if (context == null || val_toml == null || key_toml == null) {
            return false;
        }

        try {

            if (Objects.equals(preference.getKey(), DNSCRYPT_LISTEN_PORT)) {
                boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                        && ModulesStatus.getInstance().isUseModulesWithRoot();
                if (!newValue.toString().matches("\\d+") || Long.parseLong(newValue.toString()) > MAX_PORT_NUMBER
                        || (!useModulesWithRoot && Integer.parseInt(newValue.toString()) < 1024)) {
                    return false;
                }

                String val = "['127.0.0.1:" + newValue;
                if (defaultPreferences.getBoolean(DNSCRYPT_BLOCK_IPv6, false)) {
                    val += "']";
                } else {
                    val += "', '[::1]:" + newValue + "']";
                }

                val_toml.set(key_toml.indexOf("listen_addresses"), val);
                return true;
            } else if (Objects.equals(preference.getKey(), "dnscrypt_servers") && !Boolean.parseBoolean(newValue.toString())) {
                if (!defaultPreferences.getBoolean("doh_servers", true)) {
                    return false;
                }
            } else if (Objects.equals(preference.getKey(), "doh_servers") && !Boolean.parseBoolean(newValue.toString())) {
                if (!defaultPreferences.getBoolean("dnscrypt_servers", true)) {
                    return false;
                }
            } else if (Objects.equals(preference.getKey(), "ipv4_servers") && !Boolean.parseBoolean(newValue.toString())) {
                if (!defaultPreferences.getBoolean("ipv6_servers", true)) {
                    return false;
                }
            } else if (Objects.equals(preference.getKey(), "ipv6_servers") && !Boolean.parseBoolean(newValue.toString())) {
                if (!defaultPreferences.getBoolean("ipv4_servers", true)) {
                    return false;
                }
            } else if (Objects.equals(preference.getKey(), DNSCRYPT_BOOTSTRAP_RESOLVERS)) {

                List<String> resolvers = getDNSCryptBootstrapResolvers(newValue.toString());

                if (resolvers.isEmpty()) {
                    return false;
                }

                changeDNSCryptBootstrapResolvers(resolvers);

                changeDNSCryptNetprobeAddress(resolvers);

                if (VpnBuilder.vpnDnsSet != null) {
                    VpnBuilder.vpnDnsSet.clear();
                }
                return true;
            } else if (Objects.equals(preference.getKey(), "proxy_port")) {
                boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                        && ModulesStatus.getInstance().isUseModulesWithRoot();
                if (!newValue.toString().matches("\\d+") || Long.parseLong(newValue.toString()) > MAX_PORT_NUMBER
                        || (!useModulesWithRoot && Integer.parseInt(newValue.toString()) < 1024)) {
                    return false;
                }
                String val = "'socks5://127.0.0.1:" + newValue + "'";
                val_toml.set(key_toml.indexOf("proxy"), val);
                return true;
            } else if (Objects.equals(preference.getKey(), "Sources")) {
                if (newValue.toString().trim().isEmpty()) {
                    return false;
                }
                val_toml.set(key_toml.indexOf("urls"), newValue.toString());
                return true;
            } else if (Objects.equals(preference.getKey(), "Relays")) {
                if (newValue.toString().trim().isEmpty()) {
                    return false;
                }
                val_toml.set(key_toml.lastIndexOf("urls"), newValue.toString());
                return true;
            } else if (Objects.equals(preference.getKey(), DNSCRYPT_SERVERS_REFRESH_DELAY)) {
                if (!newValue.toString().matches("\\d+")) {
                    return false;
                }
            } else if (Objects.equals(preference.getKey(), DNSCRYPT_RELAYS_REFRESH_DELAY)) {
                if (!newValue.toString().matches("\\d+")) {
                    return false;
                }
                val_toml.set(key_toml.lastIndexOf("refresh_delay"), newValue.toString());
                return true;
            } else if (Objects.equals(preference.getKey(), DNSCRYPT_RULES_REFRESH_DELAY)) {
                if (!newValue.toString().matches("\\d+")) {
                    return false;
                }
                refreshDnsCryptRulesUpdateInterval(Long.parseLong(newValue.toString()));
                return true;
            } else if (Objects.equals(preference.getKey(), DNSCRYPT_OUTBOUND_PROXY)) {
                if (Boolean.parseBoolean(newValue.toString())
                        && key_toml.contains("#proxy") && key_toml.contains("force_tcp")) {
                    key_toml.set(key_toml.indexOf("#proxy"), "proxy");
                    val_toml.set(key_toml.indexOf("force_tcp"), "true");
                } else if (key_toml.contains("proxy") && key_toml.contains("force_tcp")) {
                    key_toml.set(key_toml.indexOf("proxy"), "#proxy");
                }
                return true;
            } else if (Objects.equals(preference.getKey().trim(), "Enable Query logging")) {

                int position = val_toml.indexOf("\"" + appDataDir + "/cache/query.log\"");

                if (Boolean.parseBoolean(newValue.toString())) {
                    if (position > 0) {
                        key_toml.set(val_toml.indexOf("\"" + appDataDir + "/cache/query.log\""), "file");
                    } else {
                        key_toml.set(val_toml.indexOf("'" + appDataDir + "/cache/query.log'"), "file");
                    }
                } else {
                    if (position > 0) {
                        key_toml.set(val_toml.indexOf("\"" + appDataDir + "/cache/query.log\""), "#file");
                    } else {
                        key_toml.set(val_toml.indexOf("'" + appDataDir + "/cache/query.log'"), "#file");
                    }
                }
                return true;
            } else if (Objects.equals(preference.getKey().trim(), "Enable Suspicious logging")) {

                int position = val_toml.indexOf("\"" + appDataDir + "/cache/nx.log\"");

                if (Boolean.parseBoolean(newValue.toString())) {
                    if (position > 0) {
                        key_toml.set(val_toml.indexOf("\"" + appDataDir + "/cache/nx.log\""), "file");
                    } else {
                        key_toml.set(val_toml.indexOf("'" + appDataDir + "/cache/nx.log'"), "file");
                    }
                } else {
                    if (position > 0) {
                        key_toml.set(val_toml.indexOf("\"" + appDataDir + "/cache/nx.log\""), "#file");
                    } else {
                        key_toml.set(val_toml.indexOf("'" + appDataDir + "/cache/nx.log'"), "#file");
                    }
                }
                return true;
            } else if (Objects.equals(preference.getKey().trim(), HTTP3_QUIC)) {
                int position = key_toml.indexOf("ignore_system_dns");
                if (!key_toml.contains("http3") && position >= 0) {
                    key_toml.add(position + 1, "http3");
                    val_toml.add(position + 1, "true");
                }
            } else if (Objects.equals(preference.getKey().trim(), DNSCRYPT_DNS64)) {

                addDNS64SectionForIPv6();

                boolean locked = false;
                for (int i = 0; i < key_toml.size(); i++) {
                    String key = key_toml.get(i);
                    if (key.equals("[dns64]")) {
                        locked = true;
                    } else if (key.contains("[")) {
                        locked = false;
                    }
                    if (locked && key.equals("#prefix")
                            && Boolean.parseBoolean(newValue.toString())) {
                        key_toml.set(i, "prefix");
                        break;
                    } else if (locked && key.equals("prefix")
                            && !Boolean.parseBoolean(newValue.toString())) {
                        key_toml.set(i, "#prefix");
                        break;
                    }
                }
                return true;
            } else if (Objects.equals(preference.getKey().trim(), DNSCRYPT_DNS64_PREFIX)) {
                StringBuilder dns64Prefixes = new StringBuilder("[");
                for (String dns64Prefix : newValue.toString().split(", ?")) {
                    if (dns64Prefix.matches(IPv6_REGEX_WITH_MASK)) {
                        if (!dns64Prefixes.toString().equals("[")) {
                            dns64Prefixes.append(", ");
                        }
                        dns64Prefixes.append("'").append(dns64Prefix).append("'");
                    }
                }
                dns64Prefixes.append("]");

                if (dns64Prefixes.toString().equals("[]")) {
                    return false;
                }

                boolean locked = false;
                for (int i = 0; i < key_toml.size(); i++) {
                    String key = key_toml.get(i);
                    if (key.equals("[dns64]")) {
                        locked = true;
                    } else if (key.contains("[")) {
                        locked = false;
                    }
                    if (locked && key.equals("prefix")) {
                        val_toml.set(i, dns64Prefixes.toString());
                        return true;
                    }
                }
                return false;
            } else if (Objects.equals(preference.getKey().trim(), DNSCRYPT_BLOCK_IPv6)) {

                changeDNSCryptListenAddressForIPv6(Boolean.parseBoolean(newValue.toString()));

                List<String> resolvers = new ArrayList<>(
                        Arrays.asList(pathVars.get().getDNSCryptFallbackRes().split(", ?"))
                );
                if (Boolean.parseBoolean(newValue.toString()) && resolvers.remove(QUAD_DNS_61)) {
                    changeDNSCryptBootstrapResolvers(resolvers);
                } else if (!Boolean.parseBoolean(newValue.toString())) {
                    boolean containsIPv6Resolver = false;
                    for (String resolver : resolvers) {
                        if (resolver.matches(IPv6_REGEX)) {
                            containsIPv6Resolver = true;
                            break;
                        }
                    }
                    if (!containsIPv6Resolver) {
                        resolvers.add(QUAD_DNS_61);
                        changeDNSCryptBootstrapResolvers(resolvers);
                    }
                }

                addDNS64SectionForIPv6();

                changeDNSCryptSourcesForIPv6(Boolean.parseBoolean(newValue.toString()));
            }

            if (key_toml.contains(preference.getKey().trim()) && !newValue.toString().isEmpty()) {
                val_toml.set(key_toml.indexOf(preference.getKey()), newValue.toString());
                return true;
            } else {
                Toast.makeText(context, R.string.pref_dnscrypt_not_exist, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            loge("PreferencesDNSFragment", e);
            Toast.makeText(context, R.string.wrong, Toast.LENGTH_LONG).show();
        }

        return false;
    }

    private void refreshDnsCryptRulesUpdateInterval(long delay) {
        updateRemoteRulesWorkManager.get().updateRefreshDnsRulesInterval(delay);
    }

    private List<String> getDNSCryptBootstrapResolvers(String newValue) {
        List<String> resolvers = new ArrayList<>();
        for (String resolver : newValue.split(", ?")) {
            if ((resolver.matches(IPv4_REGEX) || resolver.matches(IPv6_REGEX))
                    && !resolver.equals(LOOPBACK_ADDRESS) && !resolver.equals(META_ADDRESS)) {
                resolvers.add(resolver);
            }
        }
        return resolvers;
    }

    private void changeDNSCryptBootstrapResolvers(List<String> resolvers) {
        StringBuilder val = new StringBuilder("[");
        for (String ip : resolvers) {
            if (ip.matches(IPv4_REGEX)) {
                if (!val.toString().equals("[")) {
                    val.append(", ");
                }
                val.append("'").append(ip).append(":53'");
            } else if (ip.matches(IPv6_REGEX)) {
                if (!val.toString().equals("[")) {
                    val.append(", ");
                }
                val.append("'[").append(ip).append("]:53'");
            }
        }
        val.append("]");
        val_toml.set(key_toml.indexOf(DNSCRYPT_BOOTSTRAP_RESOLVERS), val.toString());
    }

    private void changeDNSCryptNetprobeAddress(List<String> resolvers) {
        String ipv4address = "";
        for (String ip : resolvers) {
            if (ip.matches(IPv4_REGEX)) {
                ipv4address = ip;
                break;
            }
        }
        if (ipv4address.isEmpty()) {
            ipv4address = QUAD_DNS_41;
        }
        StringBuilder val = new StringBuilder();
        val.append("'").append(ipv4address).append(":53'");
        if (key_toml.indexOf(DNSCRYPT_NETPROBE_ADDRESS) > 0) {
            val_toml.set(key_toml.indexOf(DNSCRYPT_NETPROBE_ADDRESS), val.toString());
        }
    }

    private void changeDNSCryptListenAddressForIPv6(boolean newValue) {
        String dnsCryptPort = pathVars.get().getDNSCryptPort();

        String val = "['127.0.0.1:" + dnsCryptPort;
        if (newValue) {
            val += "']";
        } else {
            val += "', '[::1]:" + dnsCryptPort + "']";
        }

        val_toml.set(key_toml.indexOf("listen_addresses"), val);
    }

    private void addDNS64SectionForIPv6() {
        int position = key_toml.indexOf("map_file");
        if (!key_toml.contains("[dns64]")
                && position >= 0
                && position < key_toml.size() - 1) {
            key_toml.add(position + 1, "[dns64]");
            val_toml.add(position + 1, "");
            key_toml.add(position + 2, "#prefix");
            val_toml.add(position + 2, "['64:ff9b::/96']");
        }
    }

    private void changeDNSCryptSourcesForIPv6(boolean newValue) {
        boolean resolversLocked = false;
        boolean relaysLocked = false;
        for (int i = 0; i < key_toml.size(); i++) {
            String key = key_toml.get(i);
            if (key.equals("[sources.'public-resolvers']")) {
                resolversLocked = true;
                relaysLocked = false;
            } else if (key.equals("[sources.'relays']")) {
                resolversLocked = false;
                relaysLocked = true;
            } else if (key.contains("[")) {
                resolversLocked = false;
                relaysLocked = false;
            }

            StringBuilder urls = new StringBuilder();
            if (newValue
                    && resolversLocked
                    && key.equals("urls")
                    && val_toml.get(i).contains(DNSCRYPT_RESOLVERS_SOURCE_IPV6)) {
                urls.setLength(0);
                String resolvers = clearUrlsLine(val_toml.get(i));
                for (String resolver : resolvers.split(", ?")) {
                    if (!resolver.equals(DNSCRYPT_RESOLVERS_SOURCE_IPV6)) {
                        if (urls.length() == 0) {
                            urls.append("[");
                        } else {
                            urls.append(", ");
                        }
                        urls.append("'").append(resolver).append("'");
                    }
                }
                if (urls.length() > 0) {
                    urls.append("]");
                }
                val_toml.set(i, urls.toString());
            } else if (!newValue
                    && resolversLocked
                    && key.equals("urls")
                    && !val_toml.get(i).contains(DNSCRYPT_RESOLVERS_SOURCE_IPV6)) {
                urls.setLength(0);
                String resolvers = clearUrlsLine(val_toml.get(i));
                for (String resolver : resolvers.split(", ?")) {
                    if (urls.length() == 0) {
                        urls.append("[");
                    } else {
                        urls.append(", ");
                    }
                    urls.append("'").append(resolver).append("'");
                }
                urls.append(", '").append(DNSCRYPT_RESOLVERS_SOURCE_IPV6).append("'");
                urls.append("]");
                val_toml.set(i, urls.toString());
            } else if (newValue
                    && relaysLocked
                    && key.equals("urls")
                    && val_toml.get(i).contains(DNSCRYPT_RELAYS_SOURCE_IPV6)) {
                urls.setLength(0);
                String relays = clearUrlsLine(val_toml.get(i));
                for (String relay : relays.split(", ?")) {
                    if (!relay.equals(DNSCRYPT_RELAYS_SOURCE_IPV6)) {
                        if (urls.length() == 0) {
                            urls.append("[");
                        } else {
                            urls.append(", ");
                        }
                        urls.append("'").append(relay).append("'");
                    }
                }
                if (urls.length() > 0) {
                    urls.append("]");
                }
                val_toml.set(i, urls.toString());
            } else if (!newValue
                    && relaysLocked
                    && key.equals("urls")
                    && !val_toml.get(i).contains(DNSCRYPT_RELAYS_SOURCE_IPV6)) {
                urls.setLength(0);
                String relays = clearUrlsLine(val_toml.get(i));
                for (String relay : relays.split(", ?")) {
                    if (urls.length() == 0) {
                        urls.append("[");
                    } else {
                        urls.append(", ");
                    }
                    urls.append("'").append(relay).append("'");
                }
                urls.append(", '").append(DNSCRYPT_RELAYS_SOURCE_IPV6).append("'");
                urls.append("]");
                val_toml.set(i, urls.toString());
            }
        }
    }

    private String clearUrlsLine(String urls) {
        return urls
                .replace("[", "")
                .replace("]", "")
                .replace("'", "")
                .replace("\"", "");
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        Activity activity = getActivity();
        if (activity == null || !isAdded()) {
            return false;
        }

        if ("editDNSTomlDirectly".equals(preference.getKey())) {
            ConfigEditorFragment.openEditorFragment(getParentFragmentManager(), "dnscrypt-proxy.toml");
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_blacklist")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_blacklist, () ->
                    eraseRules(DnsRuleType.BLACKLIST));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_whitelist")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_whitelist, () ->
                    eraseRules(DnsRuleType.WHITELIST));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_ipblacklist")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_ipblacklist, () ->
                    eraseRules(DnsRuleType.IP_BLACKLIST));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_forwarding_rules")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_forwarding_rules, () ->
                    eraseRules(DnsRuleType.FORWARDING));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_cloaking_rules")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_cloaking_rules, () ->
                    eraseRules(DnsRuleType.CLOAKING));
            return true;
        } else if ("cleanDNSCryptFolder".equals(preference.getKey().trim())) {
            cleanModuleFolder(activity);
        }

        return false;
    }

    private void eraseRules(DnsRuleType ruleType) {
        RulesEraser eraser = rulesEraser.get();
        eraser.setCallback(this);
        executor.submit("PreferencesDNSFragment eraseRules", () -> {
            eraser.eraseRules(ruleType);
            return null;
        });
    }

    @Override
    public void onRulesEraseFinished() {
        showRulesErasedDialog();
        restartDnsCryptIfNeeded();
    }

    private void showRulesErasedDialog() {
        DialogFragment dialog = NotificationDialogFragment.newInstance(R.string.erase_dnscrypt_rules_dialog_message);
        if (handler != null) {
            handler.get().post(() -> dialog.show(getParentFragmentManager(), "EraseDialog"));
        }
    }

    private void restartDnsCryptIfNeeded() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (ModulesStatus.getInstance().getDnsCryptState() == ModuleState.RUNNING) {
            ModulesRestarter.restartDNSCrypt(context);
        }
    }

    private void showAreYouSureDialog(Activity activity, int title, Runnable action) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(R.string.areYouSure);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> action.run());
        builder.setNegativeButton(getText(R.string.cancel), (dialog, i) -> dialog.cancel());
        builder.show();
    }

    private void removePreferencesWithGPVersion() {
        PreferenceScreen dnscryptSettings = findPreference("dnscrypt_settings");

        ArrayList<PreferenceCategory> categories = new ArrayList<>();
        categories.add(findPreference("pref_dnscrypt_forwarding_rules"));
        categories.add(findPreference("pref_dnscrypt_cloaking_rules"));
        categories.add(findPreference("pref_dnscrypt_blacklist"));
        categories.add(findPreference("pref_dnscrypt_ipblacklist"));
        categories.add(findPreference("pref_dnscrypt_whitelist"));
        categories.add(findPreference("pref_dnscrypt_refresh_rules"));

        for (PreferenceCategory category : categories) {
            if (dnscryptSettings != null && category != null) {
                dnscryptSettings.removePreference(category);
            }
        }

        PreferenceCategory requireServersCategory = findPreference("dnscrypt_require_servers_prop_summ");
        Preference requireNofilter = findPreference("require_nofilter");

        if (!accelerated && requireServersCategory != null && requireNofilter != null) {
            requireServersCategory.removePreference(requireNofilter);
        }

        PreferenceCategory queryLogCategory = findPreference("pref_dnscrypt_query_log");
        Preference ignoredQtypes = findPreference("ignored_qtypes");

        if (queryLogCategory != null && ignoredQtypes != null) {
            queryLogCategory.removePreference(ignoredQtypes);
        }

        PreferenceCategory otherCategory = findPreference("pref_dnscrypt_other");
        Preference editDNSTomlDirectly = findPreference("editDNSTomlDirectly");

        if (otherCategory != null && editDNSTomlDirectly != null) {
            otherCategory.removePreference(editDNSTomlDirectly);
        }

        PreferenceCategory dnsCryptServersCategory = findPreference("pref_dnscrypt_servers");
        Preference serverSources = findPreference("Sources");

        if (dnsCryptServersCategory != null && serverSources != null) {
            dnsCryptServersCategory.removePreference(serverSources);
        }

        PreferenceCategory dnsCryptRelaysCategory = findPreference("pref_dnscrypt_relays");
        Preference relaySources = findPreference("Relays");

        if (dnsCryptRelaysCategory != null && relaySources != null) {
            dnsCryptRelaysCategory.removePreference(relaySources);
        }
    }

    private void cleanModuleFolder(Context context) {
        if (ModulesStatus.getInstance().getDnsCryptState() != STOPPED) {
            Toast.makeText(context, R.string.btnDNSCryptStop, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.submit("PreferencesDNSFragment cleanModuleFolder", () -> {

            boolean success = !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "public-resolvers.md");
            success = success && !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "public-resolvers.md.minisig");
            success = success && !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "relays.md");
            success = success && !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "relays.md.minisig");
            success = success && !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "odoh-servers.md");
            success = success && !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "odoh-servers.md.minisig");
            success = success && !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "odoh-relays.md");
            success = success && !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "odoh-relays.md.minisig");

            Activity activity = getActivity();
            if (activity == null) {
                return null;
            }

            if (success) {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show());
            } else {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.wrong, Toast.LENGTH_SHORT).show());
            }
            return null;
        });
    }
}
