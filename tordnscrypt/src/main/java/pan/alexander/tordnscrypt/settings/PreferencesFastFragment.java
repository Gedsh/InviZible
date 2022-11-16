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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsInteractorInterface;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.language.Language;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.nflog.NflogManager;
import pan.alexander.tordnscrypt.utils.ThemeUtils;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated;
import static pan.alexander.tordnscrypt.utils.jobscheduler.JobSchedulerManager.startRefreshTorUnlockIPs;
import static pan.alexander.tordnscrypt.utils.jobscheduler.JobSchedulerManager.stopRefreshTorUnlockIPs;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.AUTO_START_DELAY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.BLOCK_HTTP;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.BYPASS_LAN;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CONNECTION_LOGS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MAIN_ACTIVITY_RECREATE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SITES_IPS_REFRESH_INTERVAL;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

import javax.inject.Inject;


public class PreferencesFastFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    public Lazy<NflogManager> nflogManager;
    @Inject
    public Lazy<ConnectionRecordsInteractorInterface> connectionRecordsInteractor;

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        App.getInstance().getSubcomponentsManager()
                .initLogReaderDaggerSubcomponent()
                .inject(this);
        super.onCreate(savedInstanceState);

        //noinspection deprecation
        setRetainInstance(true);

        addPreferencesFromResource(R.xml.preferences_fast);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Context context = getActivity();

        if (context == null) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        getActivity().setTitle(R.string.drawer_menu_fastSettings);

        setDnsCryptServersSumm(preferenceRepository.get().getStringPreference("DNSCrypt Servers"));

        Preference swAutostartTor = findPreference("swAutostartTor");
        if (swAutostartTor != null) {
            swAutostartTor.setOnPreferenceChangeListener(this);
        }

        Preference useBridges = findPreference("prefTorBridges");
        boolean entryNodes = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("EntryNodes", false);
        if (useBridges != null && entryNodes) {
            useBridges.setEnabled(false);
            useBridges.setSummary(R.string.pref_fast_use_tor_bridges_alt_summ);
        }

        Preference pref_fast_autostart_delay = findPreference(AUTO_START_DELAY);
        if (pref_fast_autostart_delay != null) {
            pref_fast_autostart_delay.setOnPreferenceChangeListener(this);
        }

        Preference pref_fast_theme = findPreference("pref_fast_theme");
        if (pref_fast_theme != null) {
            pref_fast_theme.setOnPreferenceChangeListener(this);
        }

        Preference pref_fast_language = findPreference("pref_fast_language");
        if (pref_fast_language != null) {
            pref_fast_language.setOnPreferenceChangeListener(this);
        }

        if (modulesStatus.getMode() == ROOT_MODE
                || modulesStatus.getMode() == VPN_MODE) {
            changePreferencesWithRootOrVPNMode(context);
        } else {
            changePreferencesWithProxyMode();
        }

        if (appVersion.startsWith("g")) {
            changePreferencesForGPVersion();
        } else if (appVersion.endsWith("d")) {
            changePreferencesForFDVersion();
        } else if (appVersion.startsWith("l")) {
            changePreferencesForLiteVersion();
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        Context context = getActivity();

        if (appVersion.startsWith("g") && !accelerated && context != null) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (preferences != null) {
                preferences.edit().putString("pref_fast_theme", "1").apply();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        Context context = getActivity();

        if (handler != null && context != null) {
            handler.get().postDelayed(() -> {
                if (getActivity() != null && !getActivity().isFinishing()) {
                    setDnsCryptServersSumm(preferenceRepository.get()
                            .getStringPreference("DNSCrypt Servers"));
                }
            }, 1000);
        }

        setUpdateTimeLast(context);
    }

    public void setDnsCryptServersSumm(final String servers) {
        final Preference prefDNSCryptServer = findPreference("prefDNSCryptServer");

        if (prefDNSCryptServer != null) {
            prefDNSCryptServer.setSummary(servers.replaceAll("[\\[\\]'\"]", ""));
        }
    }

    private void setUpdateTimeLast(Context context) {

        if (context == null) {
            return;
        }

        Activity activity = getActivity();

        String updateTimeLastStr = preferenceRepository.get().getStringPreference("updateTimeLast");
        String lastUpdateResult = preferenceRepository.get().getStringPreference("LastUpdateResult");
        final Preference prefLastUpdate = findPreference("pref_fast_chek_update");
        if (prefLastUpdate == null)
            return;

        if (!updateTimeLastStr.isEmpty() && updateTimeLastStr.trim().matches("\\d+")) {
            long updateTimeLast = Long.parseLong(updateTimeLastStr);
            Date date = new Date(updateTimeLast);

            String dateString = android.text.format.DateFormat.getDateFormat(context).format(date);
            String timeString = android.text.format.DateFormat.getTimeFormat(context).format(date);

            prefLastUpdate.setSummary(getString(R.string.update_last_check) + " "
                    + dateString + " " + timeString + System.lineSeparator() + lastUpdateResult);
        } else if (lastUpdateResult.equals(getString(R.string.update_fault))
                && preferenceRepository.get().getStringPreference("updateTimeLast").isEmpty()
                && appVersion.startsWith("p")) {
            Preference pref_fast_auto_update = findPreference("pref_fast_auto_update");
            if (pref_fast_auto_update != null) {
                pref_fast_auto_update.setEnabled(false);
            }
            prefLastUpdate.setSummary(lastUpdateResult);
        } else {
            prefLastUpdate.setSummary(lastUpdateResult);
        }
        if (activity == null || activity.isFinishing())
            return;

        prefLastUpdate.setOnPreferenceClickListener(preference -> {
            if (prefLastUpdate.isEnabled() && handler != null) {
                handler.get().post(() -> {

                    if (activity.isFinishing()) {
                        return;
                    }

                    Intent intent = new Intent(context, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    intent.setAction("check_update");
                    activity.overridePendingTransition(0, 0);
                    activity.finish();

                    startActivity(intent);
                });

                return true;
            } else {
                return false;
            }
        });

    }

    private void changeTheme() {

        Context context = getActivity();

        if (context == null || handler == null) {
            return;
        }

        handler.get().post(() -> {
            try {
                ThemeUtils.setDayNightTheme(context);
                activityCurrentRecreate();
            } catch (Exception e) {
                loge("PreferencesFastFragment changeTheme", e);
            }
        });

    }

    private void activityCurrentRecreate() {

        Activity activity = getActivity();

        if (activity == null || activity.isFinishing()) {
            return;
        }

        Intent intent = activity.getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        activity.overridePendingTransition(0, 0);
        activity.finish();

        activity.overridePendingTransition(0, 0);
        startActivity(intent);

        preferenceRepository.get().setBoolPreference(MAIN_ACTIVITY_RECREATE, true);
    }

    @Override
    public void onPause() {

        Activity activity = getActivity();
        if (activity != null) {
            Language.setFromPreference(activity, "pref_fast_language", true);
        }

        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        handler.get().removeCallbacksAndMessages(null);
    }

    @SuppressLint("UnsafeOptInUsageWarning")
    @Override
    public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {

        Context context = getActivity();

        if (context == null) {
            return false;
        }

        switch (preference.getKey()) {
            case "swAutostartTor":
                if (Boolean.parseBoolean(newValue.toString())) {
                    startRefreshTorUnlockIPs(context);
                } else if (!ModulesAux.isTorSavedStateRunning()) {
                    stopRefreshTorUnlockIPs(context);
                }
                return true;
            case ALL_THROUGH_TOR:

                if (modulesStatus.getMode() == ROOT_MODE || modulesStatus.getMode() == VPN_MODE) {

                    if (modulesStatus.getTorState() == RUNNING) {
                        modulesStatus.setIptablesRulesUpdateRequested(context, true);
                    }

                    Preference prefTorAppUnlock = findPreference("prefTorAppUnlock");
                    Preference prefTorSiteUnlock = findPreference("prefTorSiteUnlock");

                    if (prefTorSiteUnlock != null && prefTorAppUnlock != null) {
                        if (Boolean.parseBoolean(newValue.toString())) {
                            prefTorSiteUnlock.setEnabled(false);
                            prefTorAppUnlock.setEnabled(false);
                        } else {
                            prefTorSiteUnlock.setEnabled(true);
                            prefTorAppUnlock.setEnabled(true);
                        }
                    }
                }

                return true;
            case CONNECTION_LOGS:
                if (Boolean.parseBoolean(newValue.toString())) {
                    if (ModulesAux.isDnsCryptSavedStateRunning() && !modulesStatus.isUseModulesWithRoot()) {
                        if (modulesStatus.getMode() == ROOT_MODE) {
                            nflogManager.get().startNflog();
                        }
                        modulesStatus.setIptablesRulesUpdateRequested(context, true);
                    }
                } else {
                    if (ModulesAux.isDnsCryptSavedStateRunning() && !modulesStatus.isUseModulesWithRoot()) {
                        if (modulesStatus.getMode() == ROOT_MODE) {
                            nflogManager.get().stopNflog();
                        } else if (modulesStatus.getMode() == VPN_MODE) {
                            connectionRecordsInteractor.get().clearConnectionRecords();
                        }
                        modulesStatus.setIptablesRulesUpdateRequested(context, true);
                    }
                }
                return true;
            case BLOCK_HTTP:
            case BYPASS_LAN:
                if (ModulesAux.isDnsCryptSavedStateRunning()
                        || ModulesAux.isTorSavedStateRunning()) {
                    modulesStatus.setIptablesRulesUpdateRequested(context, true);
                }
                return true;
            case "pref_fast_theme":
                if (appVersion.startsWith("g") && !accelerated) {
                    if (isAdded()) {
                        DialogFragment notificationDialogFragment = NotificationDialogFragment.newInstance(R.string.only_premium_feature);
                        notificationDialogFragment.show(getParentFragmentManager(), "NotificationDialogFragment");
                    }
                    return false;
                }
                changeTheme();
                return true;
            case "pref_fast_language":
                if (handler != null) {
                    handler.get().post(this::activityCurrentRecreate);
                    return true;
                }
            case SITES_IPS_REFRESH_INTERVAL:
            case AUTO_START_DELAY:
                return newValue.toString().matches("\\d+");
        }

        return false;
    }

    private void changePreferencesWithRootOrVPNMode(Context context) {
        Preference pref_fast_all_through_tor = findPreference(ALL_THROUGH_TOR);
        if (pref_fast_all_through_tor != null) {
            pref_fast_all_through_tor.setOnPreferenceChangeListener(this);
        }

        Preference bypassLan = findPreference(BYPASS_LAN);
        if (bypassLan != null) {
            bypassLan.setOnPreferenceChangeListener(this);
        }

        Preference pref_fast_logs = findPreference(CONNECTION_LOGS);
        if (pref_fast_logs != null) {
            pref_fast_logs.setOnPreferenceChangeListener(this);
        }

        Preference pref_fast_block_http = findPreference(BLOCK_HTTP);
        if (pref_fast_block_http != null) {
            pref_fast_block_http.setOnPreferenceChangeListener(this);
        }

        Preference pref_fast_site_refresh_interval = findPreference(SITES_IPS_REFRESH_INTERVAL);
        if (pref_fast_site_refresh_interval != null) {
            pref_fast_site_refresh_interval.setOnPreferenceChangeListener(this);
        }

        Preference prefTorSiteUnlock = findPreference("prefTorSiteUnlock");
        Preference prefTorAppUnlock = findPreference("prefTorAppUnlock");

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        if (shPref.getBoolean(ALL_THROUGH_TOR, true)) {
            if (prefTorSiteUnlock != null && prefTorAppUnlock != null) {
                prefTorSiteUnlock.setEnabled(false);
                prefTorAppUnlock.setEnabled(false);
            }
        } else {
            if (prefTorSiteUnlock != null && prefTorAppUnlock != null) {
                prefTorSiteUnlock.setEnabled(true);
                prefTorAppUnlock.setEnabled(true);
            }
        }
    }

    private void changePreferencesWithProxyMode() {

        PreferenceCategory torSettingsCategory = findPreference("Tor Settings");

        List<Preference> preferencesList = new ArrayList<>();

        preferencesList.add(findPreference(ALL_THROUGH_TOR));
        preferencesList.add(findPreference("prefTorSiteUnlock"));
        preferencesList.add(findPreference("prefTorAppUnlock"));
        preferencesList.add(findPreference("prefTorSiteExclude"));
        preferencesList.add(findPreference("prefTorAppExclude"));
        preferencesList.add(findPreference(BYPASS_LAN));
        preferencesList.add(findPreference(SITES_IPS_REFRESH_INTERVAL));

        for (Preference preference : preferencesList) {
            if (preference != null && torSettingsCategory != null) {
                torSettingsCategory.removePreference(preference);
            }
        }

        PreferenceCategory fastUpdateCategory = findPreference("fast_update");
        Preference updateThroughTor = findPreference("pref_fast through_tor_update");
        if (fastUpdateCategory != null && updateThroughTor != null) {
            fastUpdateCategory.removePreference(updateThroughTor);
        }

        PreferenceCategory fastOtherCategory = findPreference("fast_other");

        Preference connectionLogs = findPreference(CONNECTION_LOGS);
        if (fastOtherCategory != null && connectionLogs != null) {
            fastOtherCategory.removePreference(connectionLogs);
        }

        Preference blockHttp = findPreference(BLOCK_HTTP);
        if (fastOtherCategory != null && blockHttp != null) {
            fastOtherCategory.removePreference(blockHttp);
        }
    }

    private void changePreferencesForGPVersion() {
        PreferenceScreen preferencesFast = findPreference("fast_preferences");
        PreferenceCategory fastUpdateCategory = findPreference("fast_update");
        if (preferencesFast != null && fastUpdateCategory != null) {
            preferencesFast.removePreference(fastUpdateCategory);
        }

        PreferenceCategory fastOtherCategory = findPreference("fast_other");

        Preference blockHttp = findPreference(BLOCK_HTTP);
        if (fastOtherCategory != null && blockHttp != null) {
            fastOtherCategory.removePreference(blockHttp);
        }
    }

    private void changePreferencesForFDVersion() {
        PreferenceScreen preferencesFast = findPreference("fast_preferences");
        PreferenceCategory fastUpdateCategory = findPreference("fast_update");
        if (preferencesFast != null && fastUpdateCategory != null) {
            preferencesFast.removePreference(fastUpdateCategory);
        }
    }

    private void changePreferencesForLiteVersion() {
        Preference prefFastAutoUpdate = findPreference("pref_fast_auto_update");
        Preference prefThroughTorUpdate = findPreference("pref_fast through_tor_update");
        Preference prefCheckUpdate = findPreference("pref_fast_chek_update");

        if (prefFastAutoUpdate != null) {
            prefFastAutoUpdate.setSummary(R.string.only_for_pro);
            ((SwitchPreference) prefFastAutoUpdate).setChecked(false);
            prefFastAutoUpdate.setEnabled(false);
        }

        if (prefThroughTorUpdate != null) {
            prefThroughTorUpdate.setSummary(R.string.only_for_pro);
            prefThroughTorUpdate.setEnabled(false);
        }

        if (prefCheckUpdate != null) {
            prefCheckUpdate.setSummary(R.string.only_for_pro);
            prefCheckUpdate.setEnabled(false);
        }
    }
}
