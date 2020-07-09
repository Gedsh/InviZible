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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

public class PreferencesITPDFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private ArrayList<String> key_itpd;
    private ArrayList<String> val_itpd;
    private ArrayList<String> key_itpd_orig;
    private ArrayList<String> val_itpd_orig;
    private String appDataDir;
    private boolean isChanged;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_i2pd);

        if (appVersion.endsWith("p")) {
            changePreferencesForGPVersion();
        }

        ArrayList<Preference> preferences = new ArrayList<>();

        preferences.add(findPreference("Allow incoming connections"));
        preferences.add(findPreference("incoming port"));
        preferences.add(findPreference("incoming host"));
        preferences.add(findPreference("ipv4"));
        preferences.add(findPreference("ipv6"));
        preferences.add(findPreference("notransit"));
        preferences.add(findPreference("floodfill"));
        preferences.add(findPreference("bandwidth"));
        preferences.add(findPreference("share"));
        preferences.add(findPreference("ssu"));
        preferences.add(findPreference("ntcp"));
        preferences.add(findPreference("Enable ntcpproxy"));
        preferences.add(findPreference("ntcpproxy"));
        preferences.add(findPreference("HTTP proxy"));
        preferences.add(findPreference("HTTP proxy port"));
        preferences.add(findPreference("HTTP outproxy"));
        preferences.add(findPreference("HTTP outproxy address"));
        preferences.add(findPreference("Socks proxy"));
        preferences.add(findPreference("Socks proxy port"));
        preferences.add(findPreference("Socks outproxy"));
        preferences.add(findPreference("Socks outproxy address"));
        preferences.add(findPreference("Socks outproxy port"));
        preferences.add(findPreference("SAM interface"));
        preferences.add(findPreference("SAM interface port"));
        preferences.add(findPreference("elgamal"));
        preferences.add(findPreference("UPNP"));
        preferences.add(findPreference("ntcp2 enabled"));
        preferences.add(findPreference("verify"));
        preferences.add(findPreference("transittunnels"));
        preferences.add(findPreference("openfiles"));
        preferences.add(findPreference("coresize"));
        preferences.add(findPreference("ntcpsoft"));
        preferences.add(findPreference("ntcphard"));
        preferences.add(findPreference("defaulturl"));

        for (Preference preference : preferences) {
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            } else if (!appVersion.startsWith("g")){
                Log.e(LOG_TAG, "PreferencesITPDFragment preference is null exception");
            }
        }

        Preference editITPDConfDirectly = findPreference("editITPDConfDirectly");
        if (editITPDConfDirectly != null) {
            editITPDConfDirectly.setOnPreferenceClickListener(this);
        }

        Preference editTunnelsDirectly = findPreference("editTunnelsDirectly");
        if (editTunnelsDirectly != null) {
            editTunnelsDirectly.setOnPreferenceClickListener(this);
        }

        Preference cleanITPDFolder = findPreference("cleanITPDFolder");
        if (cleanITPDFolder != null) {
            cleanITPDFolder.setOnPreferenceClickListener(this);
        }

        if (getArguments() != null) {
            key_itpd = getArguments().getStringArrayList("key_itpd");
            val_itpd = getArguments().getStringArrayList("val_itpd");
            key_itpd_orig = new ArrayList<>(key_itpd);
            val_itpd_orig = new ArrayList<>(val_itpd);
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

        getActivity().setTitle(R.string.drawer_menu_I2PDSettings);

        PathVars pathVars = PathVars.getInstance(getActivity());
        appDataDir = pathVars.getAppDataDir();
    }

    public void onStop() {
        super.onStop();

        if (getActivity() == null) {
            return;
        }

        List<String> itpd_conf = new LinkedList<>();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());

        if (key_itpd.indexOf("subscriptions") >= 0)
            val_itpd.set(key_itpd.indexOf("subscriptions"), sp.getString("subscriptions", ""));


        for (int i = 0; i < key_itpd.size(); i++) {
            if (!(key_itpd_orig.get(i).equals(key_itpd.get(i)) && val_itpd_orig.get(i).equals(val_itpd.get(i))) && !isChanged) {
                isChanged = true;
            }

            switch (key_itpd.get(i)) {
                case "incoming host":
                    key_itpd.set(i, "host");
                    break;
                case "HTTP outproxy address":
                    if (sp.getBoolean("HTTP outproxy", false)) {
                        key_itpd.set(i, "outproxy");
                    } else {
                        key_itpd.set(i, "#outproxy");
                    }
                    break;
                case "incoming port":
                case "Socks proxy port":
                case "HTTP proxy port":
                case "SAM interface port":
                    key_itpd.set(i, "port");
                    break;
                case "Socks outproxy":
                    key_itpd.set(i, "outproxy.enabled");
                    break;
                case "Socks outproxy port":
                    key_itpd.set(i, "outproxyport");
                    break;
                case "Socks outproxy address":
                    key_itpd.set(i, "outproxy");
                    break;
                case "ntcp2 enabled":
                case "SAM interface":
                case "Socks proxy":
                case "http enabled":
                case "HTTP proxy":
                case "UPNP":
                    key_itpd.set(i, "enabled");
                    break;
            }

            if (val_itpd.get(i).isEmpty()) {
                itpd_conf.add(key_itpd.get(i));
            } else {
                itpd_conf.add(key_itpd.get(i) + " = " + val_itpd.get(i));
            }

        }

        if (!isChanged) return;

        FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/i2pd/i2pd.conf", itpd_conf, SettingsActivity.itpd_conf_tag);

        boolean itpdRunning = new PrefManager(getActivity()).getBoolPref("I2PD Running");

        if (itpdRunning) {
            ModulesRestarter.restartITPD(getActivity());
            ModulesStatus.getInstance().setIptablesRulesUpdateRequested(getActivity(), true);
            //ModulesAux.requestModulesStatusUpdate(getActivity());
        }


    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        try {
            if (Objects.equals(preference.getKey(), "Allow incoming connections")) {
                if (Boolean.parseBoolean(newValue.toString())) {
                    key_itpd.set(key_itpd.indexOf("#host"), "incoming host");
                    key_itpd.set(key_itpd.indexOf("#port"), "incoming port");
                } else {
                    key_itpd.set(key_itpd.indexOf("incoming host"), "#host");
                    key_itpd.set(key_itpd.indexOf("incoming port"), "#port");
                }
                return true;
            } else if (Objects.equals(preference.getKey(), "Enable ntcpproxy")) {
                if (Boolean.parseBoolean(newValue.toString())) {
                    key_itpd.set(key_itpd.indexOf("#ntcpproxy"), "ntcpproxy");
                } else {
                    key_itpd.set(key_itpd.indexOf("ntcpproxy"), "#ntcpproxy");
                }
                return true;
            } else if (Objects.equals(preference.getKey(), "HTTP outproxy")) {
                isChanged = true;
                return true;
            } else if (Objects.equals(preference.getKey(), "incoming port")
                    || Objects.equals(preference.getKey(), "HTTP proxy port")
                    || Objects.equals(preference.getKey(), "Socks proxy port")
                    || Objects.equals(preference.getKey(), "Socks outproxy port")
                    || Objects.equals(preference.getKey(), "SAM interface port")) {
                boolean useModulesWithRoot = ModulesStatus.getInstance().getMode() == ROOT_MODE
                        && ModulesStatus.getInstance().isUseModulesWithRoot();
                if (!newValue.toString().matches("\\d+")
                        || (!useModulesWithRoot && Integer.parseInt(newValue.toString()) < 1024)) {
                    return false;
                }
            } else if ((Objects.equals(preference.getKey(), "share")
                    || Objects.equals(preference.getKey(), "transittunnels")
                    || Objects.equals(preference.getKey(), "openfiles")
                    || Objects.equals(preference.getKey(), "coresize")
                    || Objects.equals(preference.getKey(), "ntcpsoft")
                    || Objects.equals(preference.getKey(), "ntcphard"))
                    && !newValue.toString().matches("\\d+")) {
                return false;
            } else if ((Objects.equals(preference.getKey(), "incoming host")
                    || Objects.equals(preference.getKey(), "HTTP outproxy address")
                    || Objects.equals(preference.getKey(), "Socks outproxy address")
                    || Objects.equals(preference.getKey(), "defaulturl"))
                    && newValue.toString().trim().isEmpty()) {
                return false;
            }

            if (key_itpd.contains(preference.getKey().trim())) {
                val_itpd.set(key_itpd.indexOf(preference.getKey()), newValue.toString());
                return true;
            } else {
                Toast.makeText(getActivity(), R.string.pref_itpd_not_exist, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "PreferencesITPDFragment onPreferenceChange exception " + e.getMessage() + " " + e.getCause());
            Toast.makeText(getActivity(), R.string.wrong, Toast.LENGTH_LONG).show();
        }


        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (getActivity() == null) {
            return false;
        }

        if ("cleanITPDFolder".equals(preference.getKey())) {

            if (ModulesStatus.getInstance().getItpdState() != STOPPED) {
                Toast.makeText(getActivity(), R.string.btnITPDStop, Toast.LENGTH_SHORT).show();
                return true;
            }

            CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
                boolean successfully = false;
                if (getActivity() != null) {
                    successfully = FileOperations.deleteDirSynchronous(getActivity(), appDataDir + "/i2pd_data");
                }

                if (getActivity() != null) {
                    if (successfully) {
                        getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), R.string.done, Toast.LENGTH_SHORT).show());
                    } else {
                        getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), R.string.wrong, Toast.LENGTH_SHORT).show());
                    }

                }
            });


            return true;
        } else if ("editITPDConfDirectly".equals(preference.getKey()) && isAdded()) {
            ConfigEditorFragment.openEditorFragment(getParentFragmentManager(), "i2pd.conf");
            return true;
        } else if ("editTunnelsDirectly".equals(preference.getKey()) && isAdded()) {
            ConfigEditorFragment.openEditorFragment(getParentFragmentManager(), "tunnels.conf");
            return true;
        }
        return false;
    }

    private void changePreferencesForGPVersion() {
        PreferenceCategory categoryCommon = findPreference("itpd_settings_common");

        if (categoryCommon != null) {
            ArrayList<Preference> preferences = new ArrayList<>();
            preferences.add(findPreference("Allow incoming connections"));
            preferences.add(findPreference("incoming host"));
            preferences.add(findPreference("incoming port"));

            for (Preference preference : preferences) {
                if (preference != null) {
                    categoryCommon.removePreference(preference);
                }
            }
        }

        PreferenceScreen preferenceScreen = findPreference("itpd_settings_screen");

        if (preferenceScreen != null) {
            ArrayList<PreferenceCategory> categories = new ArrayList<>();
            categories.add(findPreference("category_itpd_sam_interface"));
            categories.add(findPreference("category_itpd_cryptography"));
            categories.add(findPreference("category_itpd_upnp"));
            categories.add(findPreference("category_itpd_reseeding"));

            for (PreferenceCategory category : categories) {
                if (category != null) {
                    preferenceScreen.removePreference(category);
                }
            }
        }

        PreferenceCategory categoryLimits = findPreference("category_itpd_limits");

        if (categoryLimits != null) {
            ArrayList<Preference> preferences = new ArrayList<>();
            preferences.add(findPreference("openfiles"));
            preferences.add(findPreference("coresize"));
            preferences.add(findPreference("ntcpsoft"));
            preferences.add(findPreference("ntcphard"));

            for (Preference preference : preferences) {
                if (preference != null) {
                    categoryLimits.removePreference(preference);
                }
            }
        }

        PreferenceCategory categorySocksProxy = findPreference("itpd_category_socks_proxy");
        Preference enableSocks = findPreference("Socks proxy");
        if (categorySocksProxy != null && enableSocks != null) {
            categorySocksProxy.removePreference(enableSocks);
        }

        PreferenceCategory categoryHTTPProxy = findPreference("itpd_category_http_proxy");
        Preference enableHTTP = findPreference("HTTP proxy");
        if (categoryHTTPProxy != null && enableHTTP != null) {
            categoryHTTPProxy.removePreference(enableHTTP);
        }

        PreferenceCategory otherCategory = findPreference("pref_itpd_other");
        Preference editITPDConfDirectly = findPreference("editITPDConfDirectly");
        if (otherCategory != null && editITPDConfDirectly != null) {
            otherCategory.removePreference(editITPDConfDirectly);
        }

    }
}
