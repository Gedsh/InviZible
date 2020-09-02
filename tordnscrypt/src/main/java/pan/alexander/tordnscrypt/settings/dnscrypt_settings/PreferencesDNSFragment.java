package pan.alexander.tordnscrypt.settings.dnscrypt_settings;
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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.ImportRulesDialog;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.ConfigEditorFragment;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.DNSCryptRulesVariant;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;
import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

public class PreferencesDNSFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    public static final int PICK_BLACKLIST_HOSTS = 1001;
    public static final int PICK_WHITELIST_HOSTS = 1002;
    public static final int PICK_BLACKLIST_IPS = 1003;
    public static final int PICK_FORWARDING = 1004;
    public static final int PICK_CLOAKING = 1005;

    private ArrayList<String> key_toml;
    private ArrayList<String> val_toml;
    private ArrayList<String> key_toml_orig;
    private ArrayList<String> val_toml_orig;
    private String appDataDir;
    private boolean isChanged = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_dnscrypt);

        if (appVersion.endsWith("p")) {
            removePreferencesWithGPVersion();
        }

        ArrayList<Preference> preferences = new ArrayList<>();

        preferences.add(findPreference("listen_port"));
        preferences.add(findPreference("dnscrypt_servers"));
        preferences.add(findPreference("doh_servers"));
        preferences.add(findPreference("require_dnssec"));
        preferences.add(findPreference("require_nolog"));
        preferences.add(findPreference("require_nofilter"));
        preferences.add(findPreference("ipv4_servers"));
        preferences.add(findPreference("ipv6_servers"));
        preferences.add(findPreference("force_tcp"));
        preferences.add(findPreference("Enable proxy"));
        preferences.add(findPreference("proxy_port"));
        preferences.add(findPreference("fallback_resolver"));
        preferences.add(findPreference("ignore_system_dns"));
        preferences.add(findPreference("Enable Query logging"));
        preferences.add(findPreference("ignored_qtypes"));
        preferences.add(findPreference("Enable Suspicious logging"));
        preferences.add(findPreference("Sources"));
        preferences.add(findPreference("refresh_delay"));
        preferences.add(findPreference("Relays"));
        preferences.add(findPreference("refresh_delay_relays"));
        preferences.add(findPreference("block_unqualified"));
        preferences.add(findPreference("block_undelegated"));
        preferences.add(findPreference("block_ipv6"));
        preferences.add(findPreference("local_blacklist"));
        preferences.add(findPreference("local_whitelist"));
        preferences.add(findPreference("local_ipblacklist"));
        preferences.add(findPreference("local_forwarding_rules"));
        preferences.add(findPreference("local_cloaking_rules"));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            } else if (!appVersion.startsWith("g")) {
                Log.e(LOG_TAG, "PreferencesDNSFragment preference is null exception");
            }
        }

        preferences = new ArrayList<>();

        preferences.add(findPreference("local_blacklist"));
        preferences.add(findPreference("local_whitelist"));
        preferences.add(findPreference("local_ipblacklist"));
        preferences.add(findPreference("local_forwarding_rules"));
        preferences.add(findPreference("local_cloaking_rules"));
        preferences.add(findPreference("erase_blacklist"));
        preferences.add(findPreference("erase_whitelist"));
        preferences.add(findPreference("erase_ipblacklist"));
        preferences.add(findPreference("erase_forwarding_rules"));
        preferences.add(findPreference("erase_cloaking_rules"));
        preferences.add(findPreference("editDNSTomlDirectly"));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceClickListener(this);
            } else if (!appVersion.startsWith("g")) {
                Log.e(LOG_TAG, "PreferencesDNSFragment preference is null exception");
            }
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

        PathVars pathVars = PathVars.getInstance(getActivity());
        appDataDir = pathVars.getAppDataDir();

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

        if (getActivity() == null || key_toml == null || val_toml == null || key_toml_orig == null || val_toml_orig == null) {
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

        FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml", dnscrypt_proxy_toml, SettingsActivity.dnscrypt_proxy_toml_tag);

        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");

        if (dnsCryptRunning) {
            ModulesRestarter.restartDNSCrypt(getActivity());
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(getActivity(), true);
        }
    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (getActivity() == null || val_toml == null || key_toml == null) {
            return false;
        }

        try {
            if (Objects.equals(preference.getKey(), "listen_port")) {
                boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                        && ModulesStatus.getInstance().isUseModulesWithRoot();
                if (!newValue.toString().matches("\\d+")
                        || (!useModulesWithRoot && Integer.parseInt(newValue.toString()) < 1024)) {
                    return false;
                }
                String val = "['127.0.0.1:" + newValue.toString() + "']";
                val_toml.set(key_toml.indexOf("listen_addresses"), val);
                return true;
            } else if (Objects.equals(preference.getKey(), "fallback_resolver")) {

                if (!newValue.toString().matches("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
                        || newValue.toString().equals("127.0.0.1") || newValue.toString().equals("0.0.0.0")) {
                    return false;
                }

                String val = "'" + newValue.toString() + ":53'";
                val_toml.set(key_toml.indexOf("fallback_resolver"), val);
                if (key_toml.indexOf("netprobe_address") > 0) {
                    val_toml.set(key_toml.indexOf("netprobe_address"), val);
                }
                return true;
            } else if (Objects.equals(preference.getKey(), "proxy_port")) {
                boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                        && ModulesStatus.getInstance().isUseModulesWithRoot();
                if (!newValue.toString().matches("\\d+")
                        || (!useModulesWithRoot && Integer.parseInt(newValue.toString()) < 1024)) {
                    return false;
                }
                String val = "'socks5://127.0.0.1:" + newValue.toString() + "'";
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
            } else if (Objects.equals(preference.getKey(), "refresh_delay")) {
                if (!newValue.toString().matches("\\d+")) {
                    return false;
                }
            } else if (Objects.equals(preference.getKey(), "refresh_delay_relays")) {
                if (!newValue.toString().matches("\\d+")) {
                    return false;
                }
                val_toml.set(key_toml.lastIndexOf("refresh_delay"), newValue.toString());
                return true;
            } else if (Objects.equals(preference.getKey(), "Enable proxy")) {
                if (Boolean.parseBoolean(newValue.toString())) {
                    key_toml.set(key_toml.indexOf("#proxy"), "proxy");
                    val_toml.set(key_toml.indexOf("force_tcp"), "true");
                } else {
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
            }

            if (key_toml.contains(preference.getKey().trim()) && !newValue.toString().isEmpty()) {
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

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (getActivity() == null) {
            return false;
        }

        if ("editDNSTomlDirectly".equals(preference.getKey()) && isAdded()) {
            ConfigEditorFragment.openEditorFragment(getParentFragmentManager(), "dnscrypt-proxy.toml");
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_blacklist") && isAdded()) {
            eraseRules(DNSCryptRulesVariant.BLACKLIST_HOSTS, "remote_blacklist");
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_whitelist") && isAdded()) {
            eraseRules(DNSCryptRulesVariant.WHITELIST_HOSTS, "remote_whitelist");
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_ipblacklist") && isAdded()) {
            eraseRules(DNSCryptRulesVariant.BLACKLIST_IPS, "remote_ipblacklist");
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_forwarding_rules") && isAdded()) {
            eraseRules(DNSCryptRulesVariant.FORWARDING, "remote_forwarding_rules");
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_cloaking_rules") && isAdded()) {
            eraseRules(DNSCryptRulesVariant.CLOAKING, "remote_cloaking_rules");
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_blacklist") && isAdded()) {

            if (isDownloadDirAccessible()) {
                FilePickerDialog dialog = new FilePickerDialog(getActivity(), getFilePickerProperties(getActivity()));
                dialog.setDialogSelectionListener(files -> importRules(DNSCryptRulesVariant.BLACKLIST_HOSTS, files));
                dialog.show();
            } else {
                openFileWithSAF(PICK_BLACKLIST_HOSTS);
            }

            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_whitelist") && isAdded()) {

            if (isDownloadDirAccessible()) {
                FilePickerDialog dialog = new FilePickerDialog(getActivity(), getFilePickerProperties(getActivity()));
                dialog.setDialogSelectionListener(files -> importRules(DNSCryptRulesVariant.WHITELIST_HOSTS, files));
                dialog.show();
            } else {
                openFileWithSAF(PICK_WHITELIST_HOSTS);
            }

            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_ipblacklist") && isAdded()) {

            if (isDownloadDirAccessible()) {
                FilePickerDialog dialog = new FilePickerDialog(getActivity(), getFilePickerProperties(getActivity()));
                dialog.setDialogSelectionListener(files -> importRules(DNSCryptRulesVariant.BLACKLIST_IPS, files));
                dialog.show();
            } else {
                openFileWithSAF(PICK_BLACKLIST_IPS);
            }

            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_forwarding_rules") && isAdded()) {

            if (isDownloadDirAccessible()) {
                FilePickerDialog dialog = new FilePickerDialog(getActivity(), getFilePickerProperties(getActivity()));
                dialog.setDialogSelectionListener(files -> importRules(DNSCryptRulesVariant.FORWARDING, files));
                dialog.show();
            } else {
                openFileWithSAF(PICK_FORWARDING);
            }

            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_cloaking_rules") && isAdded()) {

            if (isDownloadDirAccessible()) {
                FilePickerDialog dialog = new FilePickerDialog(getActivity(), getFilePickerProperties(getActivity()));
                dialog.setDialogSelectionListener(files -> importRules(DNSCryptRulesVariant.CLOAKING, files));
                dialog.show();
            } else {
                openFileWithSAF(PICK_CLOAKING);
            }

            return true;
        }

        return false;
    }

    public void importRules(DNSCryptRulesVariant dnsCryptRulesVariant, Object[] files) {

        if (getActivity() == null) {
            return;
        }

        ImportRules importRules = new ImportRules(getActivity(), dnsCryptRulesVariant,
                true, files);
        ImportRulesDialog importRulesDialog = ImportRulesDialog.newInstance();
        importRules.setOnDNSCryptRuleAddLineListener(importRulesDialog);
        importRulesDialog.show(getParentFragmentManager(), "ImportRulesDialog");
        importRules.start();
    }

    private void eraseRules(DNSCryptRulesVariant dnsCryptRulesVariant, String remoteRulesLinkPreferenceTag) {
        if (getActivity() == null) {
            return;
        }

        EraseRules eraseRules = new EraseRules(getActivity(), getParentFragmentManager(),
                dnsCryptRulesVariant, remoteRulesLinkPreferenceTag);
        eraseRules.start();
    }

    private void openFileWithSAF(int fileType) {
        if (getActivity() == null) {
            return;
        }

        Uri uri = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        if (uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(EXTRA_INITIAL_URI, uri);
        }

        try {
            getActivity().startActivityForResult(intent, fileType);
        } catch (Exception e) {
            Log.e(LOG_TAG, "PreferencesDNSFragment openFileWithSAF exception " + e.getMessage() + " " + e.getCause());
        }

    }

    private void removePreferencesWithGPVersion() {
        PreferenceScreen dnscryptSettings = findPreference("dnscrypt_settings");

        ArrayList<PreferenceCategory> categories = new ArrayList<>();
        categories.add(findPreference("pref_dnscrypt_forwarding_rules"));
        categories.add(findPreference("pref_dnscrypt_cloaking_rules"));
        categories.add(findPreference("pref_dnscrypt_blacklist"));
        categories.add(findPreference("pref_dnscrypt_ipblacklist"));
        categories.add(findPreference("pref_dnscrypt_whitelist"));
        categories.add(findPreference("pref_dnscrypt_other"));

        for (PreferenceCategory category : categories) {
            if (dnscryptSettings != null && category != null) {
                dnscryptSettings.removePreference(category);
            }
        }

        PreferenceCategory requireServersCategory = findPreference("dnscrypt_require_servers_prop_summ");
        Preference requireNofilter = findPreference("require_nofilter");

        if (requireServersCategory != null && requireNofilter != null) {
            requireServersCategory.removePreference(requireNofilter);
        }

        PreferenceCategory queryLogCategory = findPreference("pref_dnscrypt_query_log");
        Preference ignoredQtypes = findPreference("ignored_qtypes");

        if (queryLogCategory != null && ignoredQtypes != null) {
            queryLogCategory.removePreference(ignoredQtypes);
        }
    }

    private boolean isDownloadDirAccessible() {
        boolean result = false;

        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

            if (dir != null && dir.isDirectory() && Objects.requireNonNull(dir.list()).length > 0) {
                result = true;
            } else {
                Log.w(LOG_TAG, "Download Dir is not accessible!");
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Download Dir is not accessible " + e.getMessage() + e.getCause());
        }

        return result;
    }

    private DialogProperties getFilePickerProperties(Context context) {

        String cacheDirPath = PathVars.getInstance(context).getCacheDirPath(context);

        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.MULTI_MODE;
        properties.selection_type = DialogConfigs.FILE_SELECT;
        properties.root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        properties.error_dir = new File(cacheDirPath);
        properties.offset = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        properties.extensions = new String[]{"txt"};

        return properties;
    }
}
