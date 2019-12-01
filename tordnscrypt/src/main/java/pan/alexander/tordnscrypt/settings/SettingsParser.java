package pan.alexander.tordnscrypt.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.writeToTextFile;

public class SettingsParser implements OnTextFileOperationsCompleteListener {
    private SettingsActivity settingsActivity;
    private String appDataDir;
    private Bundle bundleForReadPublicResolversMdFunction;

    public SettingsParser (SettingsActivity settingsActivity) {
        this.settingsActivity = settingsActivity;
    }

    private void readDnscryptProxyToml(List<String> lines) {
        ArrayList<String> key_toml = new ArrayList<>();
        ArrayList<String> val_toml = new ArrayList<>();
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
                        key = line;
                        val = "";
                    }
                    key_toml.add(key);
                    val_toml.add(val);
                }

                if (key.equals("listen_addresses")) {
                    key = "listen_port";
                    val = val.substring(val.indexOf(":") + 1, val.indexOf("\"", 3)).trim();
                }
                if (key.equals("fallback_resolver"))
                    val = val.substring(val.indexOf("\"") + 1, val.indexOf(":")).trim();
                if (key.equals("proxy")) {
                    key = "proxy_port";
                    val = val.substring(val.indexOf(":", 10) + 1, val.indexOf("\"", 10)).trim();
                }
                if (key.equals("cache")) key = "Enable DNS cache";
                if (key.equals("urls")) key = "Sources";

                String val_saved_str = "";
                boolean val_saved_bool = false;
                boolean isbool = false;

                try {
                    val_saved_str = Objects.requireNonNull(sp.getString(key, "")).trim();
                } catch (ClassCastException e) {
                    isbool = true;
                    val_saved_bool = sp.getBoolean(key, false);
                }


                if (!val_saved_str.isEmpty() && !val_saved_str.equals(val) && !isbool) {
                    editor.putString(key, val);
                }
                if (isbool && val_saved_bool != Boolean.valueOf(val)) {
                    editor.putBoolean(key, Boolean.valueOf(val));
                }

                if (key.equals("#proxy") && sp.getBoolean("Enable proxy", false)) {
                    editor.putBoolean("Enable proxy", false);
                }
                if (key.equals("proxy_port") && !sp.getBoolean("Enable proxy", false)) {
                    editor.putBoolean("Enable proxy", true);
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
            for (String line:lines) {
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

                String val_saved_str = "";
                boolean val_saved_bool = false;
                boolean isbool = false;

                try {
                    val_saved_str = Objects.requireNonNull(sp.getString(key, "")).trim();
                } catch (ClassCastException e) {
                    isbool = true;
                    val_saved_bool = sp.getBoolean(key, false);
                }


                if (!val_saved_str.isEmpty() && !val_saved_str.equals(val) && !isbool) {
                    editor.putString(key, val);
                } else if (isbool && val_saved_bool != Boolean.valueOf(val)) {
                    editor.putBoolean(key, Boolean.valueOf(val));
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
            for (String line:lines) {
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
                } else if (header.equals("[http]") && key.equals("enabled")) {
                    key = "http enabled";
                } else if (header.equals("[httpproxy]") && key.equals("enabled")) {
                    key = "HTTP proxy";
                } else if (header.equals("[httpproxy]") && key.equals("port")) {
                    key = "HTTP proxy port";
                } else if (header.equals("[socksproxy]") && key.equals("enabled")) {
                    key = "Socks proxy";
                } else if (header.equals("[socksproxy]") && key.equals("port")) {
                    key = "Socks proxy port";
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
                        Log.e(LOG_TAG, "SettingsParser ClassCastException " + e1.getMessage() + " " + e1.getCause());
                    }

                }


                if (val_saved_str != null && !val_saved_str.isEmpty() && !val_saved_str.equals(val) && !isbool) {
                    editor.putString(key, val);
                } else if (isbool && val_saved_bool != Boolean.valueOf(val)) {
                    editor.putBoolean(key, Boolean.valueOf(val));
                }


                switch (key) {
                    case "incomming host":
                        editor.putBoolean("Allow incoming connections", true);
                        break;
                    case "#host":
                        editor.putBoolean("Allow incoming connections", false);
                        break;
                    case "ntcpproxy":
                        editor.putBoolean("Enable ntcpproxy", true);
                        break;
                    case "#ntcpproxy":
                        editor.putBoolean("Enable ntcpproxy", false);
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

    private void readPublicResolversMd(String path, List<String> lines) {
        StringBuilder sb = new StringBuilder();
        boolean lockServer = false;
        boolean lockMD = false;
        boolean lockTOML = false;
        ArrayList<String> dnsServerNames = new ArrayList<>();
        ArrayList<String> dnsServerDescr = new ArrayList<>();
        ArrayList<String> dnsServerSDNS = new ArrayList<>();
        ArrayList<String> dnscrypt_proxy_toml = new ArrayList<>();
        ArrayList<String> dnscrypt_servers = new ArrayList<>();

        if (lines != null) {
            for (String line : lines) {

                if (path.contains("public-resolvers.md")) {
                    lockMD = true;
                    lockTOML = false;
                }
                if (path.contains("dnscrypt-proxy.toml")) {
                    lockMD = false;
                    lockTOML = true;
                }

                if ((line.contains("##") || lockServer) && lockMD) {
                    if (line.contains("##")) {
                        lockServer = true;
                        dnsServerNames.add(line.substring(2).replaceAll("\\s+", "").trim());
                    } else if (line.contains("sdns")) {
                        dnsServerSDNS.add(line.replace("sdns://", "").trim());
                        lockServer = false;
                        dnsServerDescr.add(sb.toString());
                        sb.setLength(0);
                    } else if (!line.contains("##") || lockServer) {
                        sb.append(line).append((char) 10);
                    }
                }
                if (lockTOML) {
                    if (!line.isEmpty()) {
                        dnscrypt_proxy_toml.add(line);
                        if (line.matches("server_names .+")) {
                            String temp = line.substring(line.indexOf("[") + 1, line.indexOf("]")).trim();
                            temp = temp.replace("\"", "").trim();
                            dnscrypt_servers = new ArrayList<>(Arrays.asList(temp.trim().split(", ?")));
                        }

                    }

                }

            }

            //Check equals server names
            if (!dnsServerNames.isEmpty()) {
                for (int i = 0; i < dnsServerNames.size(); i++) {
                    for (int j = 0; j < dnsServerNames.size(); j++) {
                        String dnsServerName = dnsServerNames.get(i);
                        if (dnsServerName.equals(dnsServerNames.get(j)) && j != i) {
                            dnsServerNames.set(j, dnsServerName + "_repeat_server" + j);
                        }

                    }

                }
            }


            if (bundleForReadPublicResolversMdFunction == null)
                bundleForReadPublicResolversMdFunction = new Bundle();

            if (!dnsServerNames.isEmpty())
                bundleForReadPublicResolversMdFunction.putStringArrayList("dnsServerNames", dnsServerNames);
            if (!dnsServerDescr.isEmpty())
                bundleForReadPublicResolversMdFunction.putStringArrayList("dnsServerDescr", dnsServerDescr);
            if (!dnsServerSDNS.isEmpty())
                bundleForReadPublicResolversMdFunction.putStringArrayList("dnsServerSDNS", dnsServerSDNS);
            if (!dnscrypt_proxy_toml.isEmpty())
                bundleForReadPublicResolversMdFunction.putStringArrayList("dnscrypt_proxy_toml", dnscrypt_proxy_toml);
            if (!dnscrypt_servers.isEmpty())
                bundleForReadPublicResolversMdFunction.putStringArrayList("dnscrypt_servers", dnscrypt_servers);

            if (bundleForReadPublicResolversMdFunction.get("dnsServerNames")!=null
                    && bundleForReadPublicResolversMdFunction.get("dnsServerDescr")!=null
                    && bundleForReadPublicResolversMdFunction.get("dnsServerSDNS")!=null
                    && bundleForReadPublicResolversMdFunction.get("dnscrypt_proxy_toml")!=null
                    && bundleForReadPublicResolversMdFunction.get("dnscrypt_servers")!=null) {
                PreferencesDNSCryptServersRv frag = new PreferencesDNSCryptServersRv();
                frag.setArguments(bundleForReadPublicResolversMdFunction);
                FragmentTransaction fTrans = settingsActivity.getSupportFragmentManager().beginTransaction();
                fTrans.replace(android.R.id.content, frag);
                fTrans.commit();
            }
        }
    }

    private void readRules(String path, List<String> lines) {
        ArrayList<String> rules_file = new ArrayList<>();
        if (lines !=null) {
            rules_file.addAll(lines);
        } else {
            rules_file.add("");
        }
        FragmentTransaction fTrans = settingsActivity.getSupportFragmentManager().beginTransaction();
        Bundle bundle = new Bundle();
        bundle.putStringArrayList("rules_file", rules_file);
        bundle.putString("path",path);
        ShowRulesRecycleFrag frag = new ShowRulesRecycleFrag();
        frag.setArguments(bundle);
        fTrans.replace(android.R.id.content, frag);
        fTrans.commit();
    }

    public void activateSettingsParser() {
        PathVars pathVars = new PathVars(settingsActivity);
        appDataDir = pathVars.appDataDir;

        FileOperations.setOnFileOperationCompleteListener(this);
    }

    public void deactivateSettingsParser() {
        if (bundleForReadPublicResolversMdFunction!=null)
            bundleForReadPublicResolversMdFunction.clear();
        FileOperations.deleteOnFileOperationCompleteListener();
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, final String path, final String tag, final List<String> lines) {
        if (SettingsActivity.dialogFragment != null) {
            SettingsActivity.dialogFragment.dismiss();
            SettingsActivity.dialogFragment = null;
        }

        if (fileOperationResult && currentFileOperation == readTextFile) {
            settingsActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
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
                        case SettingsActivity.public_resolvers_md_tag:
                            readPublicResolversMd(path, lines);
                            break;
                        case SettingsActivity.rules_tag:
                            readRules(path, lines);
                            break;
                    }

                }
            });

        } else if(!fileOperationResult && currentFileOperation == readTextFile) {
            if (tag.equals(SettingsActivity.rules_tag)) {
                readRules(path, lines);
            }
        } else if (fileOperationResult && currentFileOperation == writeToTextFile) {
            settingsActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(settingsActivity,settingsActivity.getText(R.string.toastSettings_saved),Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
