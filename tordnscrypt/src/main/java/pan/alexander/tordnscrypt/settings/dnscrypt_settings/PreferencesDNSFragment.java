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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
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

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.ImportRulesDialog;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.ConfigEditorFragment;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.enums.DNSCryptRulesVariant;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN;

import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;
import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IGNORE_SYSTEM_DNS;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

import javax.inject.Inject;

public class PreferencesDNSFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    public static final int PICK_BLACKLIST_HOSTS = 1001;
    public static final int PICK_WHITELIST_HOSTS = 1002;
    public static final int PICK_BLACKLIST_IPS = 1003;
    public static final int PICK_FORWARDING = 1004;
    public static final int PICK_CLOAKING = 1005;

    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CachedExecutor cachedExecutor;

    private final static String ipv4Regex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";

    private ArrayList<String> key_toml;
    private ArrayList<String> val_toml;
    private ArrayList<String> key_toml_orig;
    private ArrayList<String> val_toml_orig;
    private String appDataDir;
    private boolean isChanged = false;
    private boolean rootDirAccessible;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_dnscrypt);

        checkRootDirAccessible();

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
        preferences.add(findPreference("bootstrap_resolvers"));
        preferences.add(findPreference(IGNORE_SYSTEM_DNS));
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

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            } else if (!appVersion.startsWith("g")) {
                Log.e(LOG_TAG, "PreferencesDNSFragment preference is null exception");
            }
        }

        Preference editDNSTomlDirectly = findPreference("editDNSTomlDirectly");
        if (editDNSTomlDirectly != null) {
            editDNSTomlDirectly.setOnPreferenceClickListener(this);
        } else if (!appVersion.startsWith("g")) {
            Log.e(LOG_TAG, "PreferencesDNSFragment preference is null exception");
        }

        Preference cleanDNSCryptFolder = findPreference("cleanDNSCryptFolder");
        if (cleanDNSCryptFolder != null) {
            cleanDNSCryptFolder.setOnPreferenceClickListener(this);
        }

        if (ModulesStatus.getInstance().isUseModulesWithRoot()) {
            removeImportErasePrefs();
        } else {
            registerImportErasePrefs();
        }
    }

    private void registerImportErasePrefs() {
        ArrayList<Preference> preferences = new ArrayList<>();

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

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceClickListener(this);
            } else if (!appVersion.startsWith("g")) {
                Log.e(LOG_TAG, "PreferencesDNSFragment preference is null exception");
            }
        }
    }

    private void removeImportErasePrefs() {
        PreferenceCategory forwarding = findPreference("pref_dnscrypt_forwarding_rules");
        Preference local_forwarding_rules = findPreference("local_forwarding_rules");
        Preference erase_forwarding_rules = findPreference("erase_forwarding_rules");
        if (forwarding != null && local_forwarding_rules != null && erase_forwarding_rules != null) {
            forwarding.removePreference(local_forwarding_rules);
            forwarding.removePreference(erase_forwarding_rules);
        }

        PreferenceCategory cloaking = findPreference("pref_dnscrypt_cloaking_rules");
        Preference local_cloaking_rules = findPreference("local_cloaking_rules");
        Preference erase_cloaking_rules = findPreference("erase_cloaking_rules");
        if (cloaking != null && local_cloaking_rules != null && erase_cloaking_rules != null) {
            cloaking.removePreference(local_cloaking_rules);
            cloaking.removePreference(erase_cloaking_rules);
        }

        PreferenceCategory blacklist = findPreference("pref_dnscrypt_blacklist");
        Preference local_blacklist = findPreference("local_blacklist");
        Preference erase_blacklist = findPreference("erase_blacklist");
        if (blacklist != null && local_blacklist != null && erase_blacklist != null) {
            blacklist.removePreference(local_blacklist);
            blacklist.removePreference(erase_blacklist);
        }

        PreferenceCategory ipblacklist = findPreference("pref_dnscrypt_ipblacklist");
        Preference local_ipblacklist = findPreference("local_ipblacklist");
        Preference erase_ipblacklist = findPreference("erase_ipblacklist");
        if (ipblacklist != null && local_ipblacklist != null && erase_ipblacklist != null) {
            ipblacklist.removePreference(local_ipblacklist);
            ipblacklist.removePreference(erase_ipblacklist);
        }

        PreferenceCategory whitelist = findPreference("pref_dnscrypt_whitelist");
        Preference local_whitelist = findPreference("local_whitelist");
        Preference erase_whitelist = findPreference("erase_whitelist");
        if (whitelist != null && local_whitelist != null && erase_whitelist != null) {
            whitelist.removePreference(local_whitelist);
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
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        Context context = getActivity();
        if (context == null || val_toml == null || key_toml == null) {
            return false;
        }

        try {
            boolean invalidFallbackResolver = !newValue.toString().matches(ipv4Regex)
                    || newValue.toString().equals(LOOPBACK_ADDRESS) || newValue.toString().equals(META_ADDRESS);

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
            } else if (Objects.equals(preference.getKey(), "bootstrap_resolvers")) {

                if (invalidFallbackResolver) {
                    return false;
                }

                String val = "['" + newValue.toString() + ":53']";
                val_toml.set(key_toml.indexOf("bootstrap_resolvers"), val);
                val = "'" + newValue.toString() + ":53'";
                if (key_toml.indexOf("netprobe_address") > 0) {
                    val_toml.set(key_toml.indexOf("netprobe_address"), val);
                }

                if (ServiceVPN.vpnDnsSet != null) {
                    ServiceVPN.vpnDnsSet.clear();
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
                if (Boolean.parseBoolean(newValue.toString()) && key_toml.contains("#proxy") && key_toml.contains("force_tcp")) {
                    key_toml.set(key_toml.indexOf("#proxy"), "proxy");
                    val_toml.set(key_toml.indexOf("force_tcp"), "true");
                } else if (key_toml.contains("proxy") && key_toml.contains("force_tcp")) {
                    key_toml.set(key_toml.indexOf("proxy"), "#proxy");
                    val_toml.set(key_toml.indexOf("force_tcp"), "false");
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
                Toast.makeText(context, R.string.pref_dnscrypt_not_exist, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "PreferencesDNSFragment exception " + e.getMessage() + " " + e.getCause());
            Toast.makeText(context, R.string.wrong, Toast.LENGTH_LONG).show();
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        Activity activity = getActivity();
        if (activity == null || !isAdded()) {
            return false;
        }

        if ("editDNSTomlDirectly".equals(preference.getKey())) {
            ConfigEditorFragment.openEditorFragment(getParentFragmentManager(), "dnscrypt-proxy.toml");
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_blacklist")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_blacklist, () ->
                    eraseRules(activity, DNSCryptRulesVariant.BLACKLIST_HOSTS, "remote_blacklist"));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_whitelist")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_whitelist, () ->
                    eraseRules(activity, DNSCryptRulesVariant.WHITELIST_HOSTS, "remote_whitelist"));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_ipblacklist")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_ipblacklist, () ->
                    eraseRules(activity, DNSCryptRulesVariant.BLACKLIST_IPS, "remote_ipblacklist"));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_forwarding_rules")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_forwarding_rules, () ->
                    eraseRules(activity, DNSCryptRulesVariant.FORWARDING, "remote_forwarding_rules"));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "erase_cloaking_rules")) {
            showAreYouSureDialog(activity, R.string.pref_dnscrypt_erase_cloaking_rules, () ->
                    eraseRules(activity, DNSCryptRulesVariant.CLOAKING, "remote_cloaking_rules"));
            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_blacklist")) {

            if (rootDirAccessible) {
                FilePickerDialog dialog = new FilePickerDialog(activity, getFilePickerProperties(activity));
                dialog.setDialogSelectionListener(files -> importRules(activity, DNSCryptRulesVariant.BLACKLIST_HOSTS, files));
                dialog.show();
            } else {
                openFileWithSAF(activity, PICK_BLACKLIST_HOSTS);
            }

            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_whitelist")) {

            if (rootDirAccessible) {
                FilePickerDialog dialog = new FilePickerDialog(activity, getFilePickerProperties(activity));
                dialog.setDialogSelectionListener(files -> importRules(activity, DNSCryptRulesVariant.WHITELIST_HOSTS, files));
                dialog.show();
            } else {
                openFileWithSAF(activity, PICK_WHITELIST_HOSTS);
            }

            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_ipblacklist")) {

            if (rootDirAccessible) {
                FilePickerDialog dialog = new FilePickerDialog(activity, getFilePickerProperties(activity));
                dialog.setDialogSelectionListener(files -> importRules(activity, DNSCryptRulesVariant.BLACKLIST_IPS, files));
                dialog.show();
            } else {
                openFileWithSAF(activity, PICK_BLACKLIST_IPS);
            }

            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_forwarding_rules")) {

            if (rootDirAccessible) {
                FilePickerDialog dialog = new FilePickerDialog(activity, getFilePickerProperties(activity));
                dialog.setDialogSelectionListener(files -> importRules(activity, DNSCryptRulesVariant.FORWARDING, files));
                dialog.show();
            } else {
                openFileWithSAF(activity, PICK_FORWARDING);
            }

            return true;
        } else if (Objects.equals(preference.getKey().trim(), "local_cloaking_rules")) {

            if (rootDirAccessible) {
                FilePickerDialog dialog = new FilePickerDialog(activity, getFilePickerProperties(activity));
                dialog.setDialogSelectionListener(files -> importRules(activity, DNSCryptRulesVariant.CLOAKING, files));
                dialog.show();
            } else {
                openFileWithSAF(activity, PICK_CLOAKING);
            }

            return true;
        } else if ("cleanDNSCryptFolder".equals(preference.getKey().trim())) {
            cleanModuleFolder(activity);
        }

        return false;
    }

    public void importRules(Context context, DNSCryptRulesVariant dnsCryptRulesVariant, Object[] files) {

        if (context == null) {
            return;
        }

        ImportRules importRules = new ImportRules(context, dnsCryptRulesVariant,
                true, files);
        ImportRulesDialog importRulesDialog = ImportRulesDialog.newInstance();
        importRules.setOnDNSCryptRuleAddLineListener(importRulesDialog);
        importRulesDialog.show(getParentFragmentManager(), "ImportRulesDialog");
        importRules.start();
    }

    private void eraseRules(Context context, DNSCryptRulesVariant dnsCryptRulesVariant, String remoteRulesLinkPreferenceTag) {
        if (context == null) {
            return;
        }

        EraseRules eraseRules = new EraseRules(context, getParentFragmentManager(),
                dnsCryptRulesVariant, remoteRulesLinkPreferenceTag);
        eraseRules.start();
    }

    private void showAreYouSureDialog(Activity activity, int title, Runnable action) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle(title);
        builder.setMessage(R.string.areYouSure);
        builder.setPositiveButton(R.string.ok, (dialog, which) -> action.run());
        builder.setNegativeButton(getText(R.string.cancel), (dialog, i) -> dialog.cancel());
        builder.show();
    }

    private void openFileWithSAF(Activity activity, int fileType) {
        if (activity == null) {
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

        PackageManager packageManager = activity.getPackageManager();
        if (packageManager != null && intent.resolveActivity(packageManager) != null) {
            activity.startActivityForResult(intent, fileType);
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

        PreferenceCategory otherCategory = findPreference("pref_dnscrypt_other");
        Preference editDNSTomlDirectly = findPreference("editDNSTomlDirectly");

        if (otherCategory != null && editDNSTomlDirectly != null) {
            otherCategory.removePreference(editDNSTomlDirectly);
        }
    }

    private void checkRootDirAccessible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            cachedExecutor.submit(() -> {
                rootDirAccessible = Utils.INSTANCE.isLogsDirAccessible();
            });
        }
    }

    private DialogProperties getFilePickerProperties(Context context) {

        String cacheDirPath = pathVars.get().getCacheDirPath(context);

        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.MULTI_MODE;
        properties.selection_type = DialogConfigs.FILE_SELECT;
        properties.root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        properties.error_dir = new File(cacheDirPath);
        properties.offset = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        properties.extensions = new String[]{"txt"};

        return properties;
    }

    private void cleanModuleFolder(Context context) {
        if (ModulesStatus.getInstance().getDnsCryptState() != STOPPED) {
            Toast.makeText(context, R.string.btnDNSCryptStop, Toast.LENGTH_SHORT).show();
            return;
        }

        cachedExecutor.submit(() -> {

            boolean successfully1 = !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "public-resolvers.md");
            boolean successfully2 = !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "public-resolvers.md.minisig");
            boolean successfully3 = !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "relays.md");
            boolean successfully4 = !FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "relays.md.minisig");

            Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            if (successfully1 || successfully2 || successfully3 || successfully4) {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show());
            } else {
                activity.runOnUiThread(() -> Toast.makeText(activity, R.string.wrong, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
