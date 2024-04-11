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

package pan.alexander.tordnscrypt.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.dnscrypt_settings.PreferencesDNSFragment;
import pan.alexander.tordnscrypt.settings.show_rules.ShowRulesRecycleFrag;
import pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX_WITH_MASK;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_BOOTSTRAP_RESOLVERS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_DNS64_PREFIX;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_LISTEN_PORT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_OUTBOUND_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DORMANT_CLIENT_TIMEOUT;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.I2PD_OUTBOUND_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY_ADDRESS;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.writeToTextFile;

public class SettingsParser implements OnTextFileOperationsCompleteListener {
    private final SettingsActivity settingsActivity;
    private final String appDataDir;
    private Bundle bundleForReadPublicResolversMdFunction;

    public SettingsParser(SettingsActivity settingsActivity, String appDataDir) {
        this.settingsActivity = settingsActivity;
        this.appDataDir = appDataDir;
    }

    private void readDnscryptProxyToml(List<String> lines) {
        ArrayList<String> key_toml = new ArrayList<>();
        ArrayList<String> val_toml = new ArrayList<>();
        String header = "";
        String key = "";
        String val = "";
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(settingsActivity);
        SharedPreferences.Editor editor = sp.edit();

        if (lines != null) {
            for (String line : lines) {
                if (!line.isEmpty()) {
                    if (line.contains("=")) {
                        key = line.substring(0, line.indexOf("=")).trim();
                        val = line.substring(line.indexOf("=") + 1).trim();
                    } else {
                        if (line.matches("^ *\\[.+] *$")) {
                            header = line.trim();
                        }
                        key = line;
                        val = "";
                    }
                    key_toml.add(key);
                    val_toml.add(val);
                }

                if (key.equals("listen_addresses")) {
                    key = DNSCRYPT_LISTEN_PORT;
                    if (val.contains("\"") && val.contains(":")) {
                        val = val.substring(val.lastIndexOf(":") + 1, val.lastIndexOf("\"")).trim();
                    } else if (val.contains("'") && val.contains(":")) {
                        val = val.substring(val.lastIndexOf(":") + 1, val.lastIndexOf("'")).trim();
                    }
                } else if (key.equals(DNSCRYPT_BOOTSTRAP_RESOLVERS)) {
                    StringBuilder fallbackResolvers = new StringBuilder();
                    for (String resolver: val.split(", ?")) {
                        resolver = resolver
                                .replace("[", "").replace("]", "")
                                .replace("'", "").replace("\"", "");
                        if (resolver.endsWith(":53")) {
                            resolver = resolver.substring(0, resolver.lastIndexOf(":53"));
                        }
                        if (resolver.matches(IPv4_REGEX) || resolver.matches(IPv6_REGEX)) {
                            if (fallbackResolvers.length() != 0) {
                                fallbackResolvers.append(", ");
                            }
                            fallbackResolvers.append(resolver);
                        }
                    }
                    val = fallbackResolvers.toString();
                } else if (key.equals("proxy")) {
                    key = "proxy_port";
                    if (val.contains("\"") && val.contains(":")) {
                        val = val.substring(val.indexOf(":", 10) + 1, val.indexOf("\"", 10)).trim();
                    } else if (val.contains("'") && val.contains(":")) {
                        val = val.substring(val.indexOf(":", 10) + 1, val.indexOf("'", 10)).trim();
                    }
                } else if (header.matches("\\[sources\\.'?public-resolvers'?]") && key.equals("urls")) {
                    key = "Sources";
                } else if (header.matches("\\[sources\\.'?relays'?]") && key.equals("urls")) {
                    key = "Relays";
                } else if (header.matches("\\[sources\\.'?relays'?]") && key.equals("refresh_delay")) {
                    key = "refresh_delay_relays";
                } else if (header.equals("[dns64]") && key.equals("prefix")) {
                    key = DNSCRYPT_DNS64_PREFIX;
                    StringBuilder dns64Prefixes = new StringBuilder();
                    for (String dns64Prefix: val.split(", ?")) {
                        dns64Prefix = dns64Prefix
                                .replace("[", "").replace("]", "")
                                .replace("'", "").replace("\"", "");
                        if (dns64Prefix.matches(IPv6_REGEX_WITH_MASK)) {
                            if (dns64Prefixes.length() != 0) {
                                dns64Prefixes.append(", ");
                            }
                            dns64Prefixes.append(dns64Prefix);
                        }
                    }
                    val = dns64Prefixes.toString();
                }



                String val_saved_str = "";
                boolean val_saved_bool = false;
                boolean isbool = false;

                try {
                    val_saved_str = sp.getString(key, "");
                    if (val_saved_str != null) {
                        val_saved_str = val_saved_str.trim();
                    }
                } catch (ClassCastException e) {
                    isbool = true;
                    val_saved_bool = sp.getBoolean(key, false);
                }


                if (val_saved_str != null && !val_saved_str.isEmpty() && !val_saved_str.equals(val)) {
                    editor.putString(key, val);
                }
                if (isbool && val_saved_bool != Boolean.parseBoolean(val)) {
                    editor.putBoolean(key, Boolean.parseBoolean(val));
                }

                if (key.equals("#proxy") && sp.getBoolean(DNSCRYPT_OUTBOUND_PROXY, false)) {
                    editor.putBoolean(DNSCRYPT_OUTBOUND_PROXY, false);
                }
                if (key.equals("proxy_port") && !sp.getBoolean(DNSCRYPT_OUTBOUND_PROXY, false)) {
                    editor.putBoolean(DNSCRYPT_OUTBOUND_PROXY, true);
                }
                if (val.contains(appDataDir + "/cache/query.log") && !key.contains("#") && !sp.getBoolean("Enable Query logging", false)) {
                    editor.putBoolean("Enable Query logging", true);
                }
                if (val.contains(appDataDir + "/cache/query.log") && key.contains("#") && sp.getBoolean("Enable Query logging", false)) {
                    editor.putBoolean("Enable Query logging", false);
                }
                if (val.contains(appDataDir + "/cache/nx.log") && !key.contains("#") && !sp.getBoolean("Enable Query logging", false)) {
                    editor.putBoolean("Enable Suspicious logging", true);
                }
                if (val.contains(appDataDir + "/cache/nx.log") && key.contains("#") && sp.getBoolean("Enable Query logging", false)) {
                    editor.putBoolean("Enable Suspicious logging", false);
                }
            }
            editor.apply();

            FragmentTransaction fTrans = settingsActivity.getSupportFragmentManager().beginTransaction();
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("key_toml", key_toml);
            bundle.putStringArrayList("val_toml", val_toml);
            PreferencesDNSFragment frag = new PreferencesDNSFragment();
            frag.setArguments(bundle);
            fTrans.replace(android.R.id.content, frag);
            fTrans.commit();
        }
    }

    private void readTorConf(List<String> lines) {
        ArrayList<String> key_tor = new ArrayList<>();
        ArrayList<String> val_tor = new ArrayList<>();
        String key = "";
        String val = "";
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(settingsActivity);
        SharedPreferences.Editor editor = sp.edit();

        if (lines != null) {
            for (String line : lines) {
                if (!line.isEmpty()) {
                    if (line.contains(" ")) {
                        key = line.substring(0, line.indexOf(" ")).trim();
                        val = line.substring(line.indexOf(" ") + 1).trim();
                    } else {
                        key = line;
                        val = "";
                    }
                    if (val.trim().equals("1")) val = "true";
                    if (val.trim().equals("0")) val = "false";

                    key_tor.add(key);
                    val_tor.add(val);
                }

                switch (key) {
                    case "SOCKSPort", "HTTPTunnelPort", "TransPort", "DNSPort" ->
                            val = val.split(" ")[0]
                                    .replaceAll(".+:", "")
                                    .replaceAll("\\D+", "");
                    case "VirtualAddrNetworkIPv4" -> key = "VirtualAddrNetwork";
                    case DORMANT_CLIENT_TIMEOUT -> val = val.replaceAll("\\D+", "");
                }

                String val_saved_str = "";
                boolean val_saved_bool = false;
                boolean isbool = false;

                try {
                    val_saved_str = sp.getString(key, "");
                    if (val_saved_str != null) {
                        val_saved_str = val_saved_str.trim();
                    }
                } catch (ClassCastException e) {
                    isbool = true;
                    val_saved_bool = sp.getBoolean(key, false);
                }


                if (val_saved_str != null && !val_saved_str.isEmpty() && !val_saved_str.equals(val)) {
                    editor.putString(key, val);
                } else if (isbool && val_saved_bool != Boolean.parseBoolean(val)) {
                    editor.putBoolean(key, Boolean.parseBoolean(val));
                }


                switch (key) {
                    case "ExcludeNodes":
                        editor.putBoolean("ExcludeNodes", true);
                        break;
                    case "#ExcludeNodes":
                        editor.putBoolean("ExcludeNodes", false);
                        break;
                    case "EntryNodes":
                        editor.putBoolean("EntryNodes", true);
                        break;
                    case "#ExitNodes":
                        editor.putBoolean("ExitNodes", false);
                        break;
                    case "ExitNodes":
                        editor.putBoolean("ExitNodes", true);
                        break;
                    case "#ExcludeExitNodes":
                        editor.putBoolean("ExcludeExitNodes", false);
                        break;
                    case "ExcludeExitNodes":
                        editor.putBoolean("ExcludeExitNodes", true);
                        break;
                    case "#EntryNodes":
                        editor.putBoolean("EntryNodes", false);
                        break;
                    case "SOCKSPort":
                        editor.putBoolean("Enable SOCKS proxy", true);
                        break;
                    case "#SOCKSPort":
                        editor.putBoolean("Enable SOCKS proxy", false);
                        break;
                    case "TransPort":
                        editor.putBoolean("Enable Transparent proxy", true);
                        break;
                    case "#TransPort":
                        editor.putBoolean("Enable Transparent proxy", false);
                        break;
                    case "DNSPort":
                        editor.putBoolean("Enable DNS", true);
                        break;
                    case "#DNSPort":
                        editor.putBoolean("Enable DNS", false);
                        break;
                    case "HTTPTunnelPort":
                        editor.putBoolean("Enable HTTPTunnel", true);
                        break;
                    case "#HTTPTunnelPort":
                        editor.putBoolean("Enable HTTPTunnel", false);
                        break;
                    case TOR_OUTBOUND_PROXY_ADDRESS:
                        editor.putBoolean(TOR_OUTBOUND_PROXY, true);
                        break;
                    case "#Socks5Proxy":
                        editor.putBoolean(TOR_OUTBOUND_PROXY, false);
                        break;
                    case "TrackHostExits":
                        editor.putBoolean("Enable TrackHostExits", true);
                        break;
                    case "#TrackHostExits":
                        editor.putBoolean("Enable TrackHostExits", false);
                        break;
                }
            }
            editor.apply();

            Bundle bundle = new Bundle();
            bundle.putStringArrayList("key_tor", key_tor);
            bundle.putStringArrayList("val_tor", val_tor);
            PreferencesTorFragment frag = new PreferencesTorFragment();
            frag.setArguments(bundle);
            FragmentTransaction fTrans = settingsActivity.getSupportFragmentManager().beginTransaction();
            fTrans.replace(android.R.id.content, frag);
            fTrans.commit();
        }
    }

    private void readITPDconf(List<String> lines) {
        ArrayList<String> key_itpd = new ArrayList<>();
        ArrayList<String> val_itpd = new ArrayList<>();
        String key = "";
        String val = "";
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(settingsActivity);
        SharedPreferences.Editor editor = sp.edit();
        String header = "common";

        if (lines != null) {
            for (String line : lines) {
                if (!line.isEmpty()) {
                    if (line.contains("=")) {
                        key = line.substring(0, line.indexOf("=")).trim();
                        val = line.substring(line.indexOf("=") + 1).trim();
                    } else {
                        key = line;
                        val = "";
                    }
                }

                String val_saved_str = "";
                boolean val_saved_bool = false;
                boolean isbool = false;

                if (key.contains("[")) header = key;

                if (header.equals("common") && key.equals("host")) {
                    key = "incoming host";
                } else if (header.equals("common") && key.equals("port")) {
                    key = "incoming port";
                } else if (header.equals("[ntcp2]") && key.equals("enabled")) {
                    key = "ntcp2 enabled";
                } else if (header.equals("[ssu2]") && key.equals("enabled")) {
                    key = "ssu2 enabled";
                } else if (header.equals("[http]") && key.equals("enabled")) {
                    key = "http enabled";
                } else if (header.equals("[httpproxy]") && key.equals("enabled")) {
                    key = "HTTP proxy";
                } else if (header.equals("[httpproxy]") && key.equals("port")) {
                    key = "HTTP proxy port";
                } else if (header.equals("[httpproxy]") && key.matches("#?outproxy")) {
                    key = "HTTP outproxy address";
                } else if (header.equals("[socksproxy]") && key.equals("enabled")) {
                    key = "Socks proxy";
                } else if (header.equals("[socksproxy]") && key.equals("port")) {
                    key = "Socks proxy port";
                }else if (header.equals("[socksproxy]") && key.matches("#?outproxy.enabled")) {
                    key = "Socks outproxy";
                } else if (header.equals("[socksproxy]") && key.matches("#?outproxy")) {
                    key = "Socks outproxy address";
                } else if (header.equals("[socksproxy]") && key.matches("#?outproxyport")) {
                    key = "Socks outproxy port";
                } else if (header.equals("[sam]") && key.equals("enabled")) {
                    key = "SAM interface";
                } else if (header.equals("[sam]") && key.equals("port")) {
                    key = "SAM interface port";
                } else if (header.equals("[upnp]") && key.equals("enabled")) {
                    key = "UPNP";
                } else if (header.contains("[addressbook]") && key.equals("subscriptions")) {
                    editor.putString(key, val);
                }

                if (!line.isEmpty()) {
                    key_itpd.add(key);
                    val_itpd.add(val);
                }

                try {
                    val_saved_str = sp.getString(key, "");
                    if (val_saved_str != null) {
                        val_saved_str = val_saved_str.trim();
                    }

                } catch (ClassCastException e) {
                    isbool = true;

                    try {
                        val_saved_bool = sp.getBoolean(key, false);
                    } catch (ClassCastException e1) {
                        loge("SettingsParser ClassCastException", e1);
                    }

                }


                if (val_saved_str != null && !val_saved_str.isEmpty() && !val_saved_str.equals(val) && !isbool) {
                    editor.putString(key, val);
                } else if (isbool && val_saved_bool != Boolean.parseBoolean(val)) {
                    editor.putBoolean(key, Boolean.parseBoolean(val));
                }


                switch (key) {
                    case "incomming host":
                        editor.putBoolean("Allow incoming connections", true);
                        break;
                    case "#host":
                        editor.putBoolean("Allow incoming connections", false);
                        break;
                    case "ntcpproxy":
                        editor.putBoolean(I2PD_OUTBOUND_PROXY, true);
                        break;
                    case "#ntcpproxy":
                        editor.putBoolean(I2PD_OUTBOUND_PROXY, false);
                        break;
                }
            }
            editor.apply();

            FragmentTransaction fTrans = settingsActivity.getSupportFragmentManager().beginTransaction();
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("key_itpd", key_itpd);
            bundle.putStringArrayList("val_itpd", val_itpd);
            PreferencesITPDFragment frag = new PreferencesITPDFragment();
            frag.setArguments(bundle);
            fTrans.replace(android.R.id.content, frag);
            fTrans.commit();
        }
    }

    private void readRules(String path, List<String> lines) {
        ArrayList<String> rules_file = new ArrayList<>();
        if (lines != null) {
            rules_file.addAll(lines);
        } else {
            rules_file.add("");
        }
        FragmentTransaction fTrans = settingsActivity.getSupportFragmentManager().beginTransaction();
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("rules_file", rules_file);
        bundle.putString("path", path);
        ShowRulesRecycleFrag frag = new ShowRulesRecycleFrag();
        frag.setArguments(bundle);
        fTrans.replace(android.R.id.content, frag);
        fTrans.commit();
    }

    public void activateSettingsParser() {
        FileManager.setOnFileOperationCompleteListener(this);
    }

    public void deactivateSettingsParser() {
        if (bundleForReadPublicResolversMdFunction != null) {
            bundleForReadPublicResolversMdFunction.clear();
        }

        FileManager.deleteOnFileOperationCompleteListener(this);
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, final String path, final String tag, final List<String> lines) {

        if (settingsActivity == null) {
            return;
        }

        DialogFragment dialogFragment = settingsActivity.dialogFragment;
        if (dialogFragment != null) {
            dialogFragment.dismiss();
        }

        if (fileOperationResult && currentFileOperation == readTextFile) {
            settingsActivity.runOnUiThread(() -> {
                switch (tag) {
                    case SettingsActivity.dnscrypt_proxy_toml_tag:
                        readDnscryptProxyToml(lines);
                        break;
                    case SettingsActivity.tor_conf_tag:
                        readTorConf(lines);
                        break;
                    case SettingsActivity.itpd_conf_tag:
                        readITPDconf(lines);
                        break;
                    case SettingsActivity.rules_tag:
                        readRules(path, lines);
                        break;
                }

            });

        } else if (!fileOperationResult && currentFileOperation == readTextFile) {
            if (tag.equals(SettingsActivity.rules_tag)) {
                readRules(path, lines);
            }
        } else if (fileOperationResult && currentFileOperation == writeToTextFile) {
            settingsActivity.runOnUiThread(() -> Toast.makeText(settingsActivity, settingsActivity.getText(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show());
        }
    }
}
