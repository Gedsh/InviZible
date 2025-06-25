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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.FakeSniInputDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dialogs.SniInputListener;
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsInteractorInterface;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.language.Language;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.nflog.NflogManager;
import pan.alexander.tordnscrypt.utils.ThemeUtils;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.workers.UpdateIPsManager;
import pan.alexander.tordnscrypt.views.SwitchPlusClickPreference;

import static pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated;
import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.dialogs.FakeSniInputDialogFragmentKt.FAKE_SNI_ARG;
import static pan.alexander.tordnscrypt.utils.Utils.verifyHostsSet;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.AUTO_START_DELAY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.BLOCK_HTTP;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.BLOCK_LAN_ON_FREE_WIFI;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.BYPASS_LAN;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CONNECTION_LOGS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_SERVERS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FAKE_SNI;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FAKE_SNI_HOSTS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_WAS_STARTED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_ENABLED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.MAIN_ACTIVITY_RECREATE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PREVENT_DNS_LEAKS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SITES_IPS_REFRESH_INTERVAL;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_ENTRY_NODES;

import javax.inject.Inject;
import javax.inject.Named;


public class PreferencesFastFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener,
        SwitchPlusClickPreference.SwitchPlusClickListener,
        SniInputListener {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    public Lazy<NflogManager> nflogManager;
    @Inject
    public Lazy<ConnectionRecordsInteractorInterface> connectionRecordsInteractor;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public Lazy<FakeSniInputDialogFragment> fakeSniInputDialogFragment;
    @Inject
    public Lazy<UpdateIPsManager> updateIPsManager;
    @Inject
    public CoroutineExecutor executor;

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

        setDnsCryptServersSumm(preferenceRepository.get().getStringPreference(DNSCRYPT_SERVERS));

        Preference swAutostartTor = findPreference("swAutostartTor");
        if (swAutostartTor != null) {
            swAutostartTor.setOnPreferenceChangeListener(this);
        }

        Preference useBridges = findPreference("prefTorBridges");
        boolean entryNodes = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(TOR_ENTRY_NODES, false);
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

        SwitchPlusClickPreference fakeSni = findPreference(FAKE_SNI);
        if (fakeSni != null) {
            Set<String> hosts = verifyHostsSet(
                    preferenceRepository.get().getStringSetPreference(FAKE_SNI_HOSTS)
            );
            if (hosts.isEmpty()) {
                hosts = new LinkedHashSet<>(
                        Arrays.asList(context.getResources().getStringArray(R.array.default_fake_sni))
                );
            }
            fakeSni.setSummary(TextUtils.join(", ", hosts));
            fakeSni.setSwitchClickListener(this);
        }

        if (modulesStatus.getMode() == ROOT_MODE
                || modulesStatus.getMode() == VPN_MODE) {
            changePreferencesWithRootOrVPNMode(context);
        } else {
            changePreferencesWithProxyMode();
        }

        if (pathVars.get().getAppVersion().startsWith("g")) {
            changePreferencesForGPVersion();
        } else if (pathVars.get().getAppVersion().endsWith("d")) {
            changePreferencesForFDVersion();
        } else if (pathVars.get().getAppVersion().startsWith("l")) {
            changePreferencesForLiteVersion();
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        Context context = getActivity();

        if (pathVars.get().getAppVersion().startsWith("g") && !accelerated && context != null) {
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
                            .getStringPreference(DNSCRYPT_SERVERS));
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
                && pathVars.get().getAppVersion().startsWith("p")) {
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
                ThemeUtils.setDayNightTheme(context, pathVars.get());
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
                    updateIPsManager.get().startRefreshTorUnlockIPs();
                } else if (!ModulesAux.isTorSavedStateRunning()) {
                    updateIPsManager.get().stopRefreshTorUnlockIPs();
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
            case BLOCK_LAN_ON_FREE_WIFI:
                if (ModulesAux.isDnsCryptSavedStateRunning()
                        || ModulesAux.isTorSavedStateRunning()
                        || modulesStatus.getFirewallState() == RUNNING) {
                    modulesStatus.setIptablesRulesUpdateRequested(context, true);
                }
                return true;
            case "pref_fast_theme":
                if (pathVars.get().getAppVersion().startsWith("g") && !accelerated) {
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
            case PREVENT_DNS_LEAKS:
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
                return true;
        }

        return false;
    }


    @Override
    public void onCheckedChanged(SwitchCompat buttonView, boolean isChecked) {
        if (isChecked) {
            Set<String> sni = preferenceRepository.get().getStringSetPreference(FAKE_SNI_HOSTS);
            addTorConfWebTunnelSNIs(sni);
        } else {
            clearTorConfWebTunnelSNIs();
        }
    }

    @Override
    public void onClick(View view) {
        FakeSniInputDialogFragment fragment = fakeSniInputDialogFragment.get();
        Bundle bundle = new Bundle();
        Set<String> hosts = verifyHostsSet(
                preferenceRepository.get().getStringSetPreference(FAKE_SNI_HOSTS)
        );
        if (hosts.isEmpty()) {
            hosts = new LinkedHashSet<>(
                    Arrays.asList(getResources().getStringArray(R.array.default_fake_sni))
            );
        }
        bundle.putStringArrayList(FAKE_SNI_ARG, new ArrayList<>(hosts));
        fragment.setArguments(bundle);
        fragment.show(getChildFragmentManager(), "FakeSniInputDialogFragment");
    }

    private void changePreferencesWithRootOrVPNMode(Context context) {
        Preference prefFastPreventDnsLeak = findPreference(PREVENT_DNS_LEAKS);
        if (prefFastPreventDnsLeak != null) {
            prefFastPreventDnsLeak.setOnPreferenceChangeListener(this);
        }

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

        PreferenceCategory fast_other_category = findPreference("fast_other");
        Preference pref_fast_block_lan_on_free_wifi = findPreference(BLOCK_LAN_ON_FREE_WIFI);
        if (pref_fast_block_lan_on_free_wifi != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && preferenceRepository.get().getBoolPreference(FIREWALL_ENABLED)
                && preferenceRepository.get().getBoolPreference(FIREWALL_WAS_STARTED)) {
            pref_fast_block_lan_on_free_wifi.setOnPreferenceChangeListener(this);
        } else if (fast_other_category != null && pref_fast_block_lan_on_free_wifi != null) {
            fast_other_category.removePreference(pref_fast_block_lan_on_free_wifi);
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

        PreferenceCategory dnsCryptServersCategory = findPreference("DNSCrypt servers");
        Preference prefFastPreventDnsLeak = findPreference(PREVENT_DNS_LEAKS);
        if (dnsCryptServersCategory != null && prefFastPreventDnsLeak != null) {
            dnsCryptServersCategory.removePreference(prefFastPreventDnsLeak);
        }

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

        Preference prefFastBlockLanOnFreeWifi = findPreference(BLOCK_LAN_ON_FREE_WIFI);
        if (fastOtherCategory != null && prefFastBlockLanOnFreeWifi != null) {
            fastOtherCategory.removePreference(prefFastBlockLanOnFreeWifi);
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

    @Override
    public void setSni(String text) {

        if (text == null) {
            return;
        }

        Set<String> sni = verifyHostsSet(
                new LinkedHashSet<>(Arrays.asList(text.split(", ?| +|\\n")))
        );

        Set<String> savedSni = verifyHostsSet(
                preferenceRepository.get().getStringSetPreference(FAKE_SNI_HOSTS)
        );

        if (savedSni.size() == sni.size() && savedSni.containsAll(sni)) {
            return;
        }

        preferenceRepository.get().setStringSetPreference(FAKE_SNI_HOSTS, sni);

        if (sni.isEmpty()) {
            sni = new LinkedHashSet<>(
                    Arrays.asList(getResources().getStringArray(R.array.default_fake_sni))
            );
        }

        SwitchPlusClickPreference fakeSni = findPreference(FAKE_SNI);
        if (fakeSni != null) {
            fakeSni.setSummary(TextUtils.join(", ", sni));
        }

        boolean fakeSniEnabled = defaultPreferences.get().getBoolean(FAKE_SNI, false);
        if (fakeSniEnabled) {
            rewriteTorConfWebTunnelSNIs(sni);
        }
    }

    private void addTorConfWebTunnelSNIs(Set<String> snis) {
        executor.submit("PreferencesFastFragment addTorConfWebTunnelSNIs", () -> {
            try {
                tryAddTorConfWebTunnelSNIs(snis);
            } catch (Exception e) {
                loge("PreferencesFastFragment addTorConfWebTunnelSNIs", e);
            }
            return null;
        });
    }

    private void tryAddTorConfWebTunnelSNIs(Set<String> snis) {
        Pattern pattern = Pattern.compile("(url=\\S+)");
        List<String> torConf = FileManager.readTextFileSynchronous(
                requireContext(),
                pathVars.get().getAppDataDir() + "/app_data/tor/tor.conf"
        );
        boolean bridgesActive = false;
        boolean linesModified = false;
        for (int i = 0; i < torConf.size(); i++) {
            String line = torConf.get(i);
            if (line.contains("UseBridges 1")) {
                bridgesActive = true;
            } else if (bridgesActive && line.startsWith("Bridge webtunnel")) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String urlPart = matcher.group(1);
                    String newLine = matcher.replaceFirst(
                            urlPart
                                    + " servernames="
                                    + TextUtils.join(",", snis)
                    );
                    torConf.set(i, newLine);
                    linesModified = true;
                }
            }
        }

        if (linesModified) {
            FileManager.writeTextFileSynchronous(
                    requireContext(),
                    pathVars.get().getAppDataDir() + "/app_data/tor/tor.conf",
                    torConf
            );
        }

        restartTorIfNeeded();
    }

    private void clearTorConfWebTunnelSNIs() {
        executor.submit("PreferencesFastFragment clearTorConfWebTunnelSNIs", () -> {
            try {
                tryRewriteTorConfWebTunnelSNIs(null);
            } catch (Exception e) {
                loge("PreferencesFastFragment clearTorConfWebTunnelSNIs", e);
            }
            return null;
        });
    }

    private void rewriteTorConfWebTunnelSNIs(Set<String> snis) {
        executor.submit("PreferencesFastFragment rewriteTorConfWebTunnelSNIs", () -> {
            try {
                tryRewriteTorConfWebTunnelSNIs(snis);
            } catch (Exception e) {
                loge("PreferencesFastFragment rewriteTorConfWebTunnelSNIs", e);
            }
            return null;
        });
    }

    private void tryRewriteTorConfWebTunnelSNIs(Set<String> snis) {
        Pattern pattern = Pattern.compile("(servernames=\\S+)");
        List<String> torConf = FileManager.readTextFileSynchronous(
                requireContext(),
                pathVars.get().getAppDataDir() + "/app_data/tor/tor.conf"
        );
        boolean bridgesActive = false;
        boolean linesModified = false;
        for (int i = 0; i < torConf.size(); i++) {
            String line = torConf.get(i);
            if (line.contains("UseBridges 1")) {
                bridgesActive = true;
            } else if (bridgesActive && line.startsWith("Bridge webtunnel")) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String newLine = matcher.replaceFirst(
                            snis != null ? "servernames=" + TextUtils.join(",", snis) : ""
                    );
                    if (!newLine.equals(line)) {
                        torConf.set(i, newLine);
                        linesModified = true;
                    }
                }
            }
        }

        if (linesModified) {
            FileManager.writeTextFileSynchronous(
                    requireContext(),
                    pathVars.get().getAppDataDir() + "/app_data/tor/tor.conf",
                    torConf
            );
        }

        restartTorIfNeeded();
    }

    private void restartTorIfNeeded() {
        boolean torRunning = ModulesAux.isTorSavedStateRunning();
        if (torRunning) {
            ModulesRestarter.restartTor(getContext());
            modulesStatus.setIptablesRulesUpdateRequested(getContext(), true);
        }
    }
}
