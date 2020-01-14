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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.language.Language;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;


public class PreferencesFastFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    public static final int mJobId = 1;
    private int refreshPeriodHours = 12;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_fast);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (getActivity() == null) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        getActivity().setTitle(R.string.drawer_menu_fastSettings);

        Preference prefDNSCryptServer = findPreference("prefDNSCryptServer");
        if (prefDNSCryptServer != null) {
            prefDNSCryptServer.setSummary(new PrefManager(Objects.requireNonNull(getActivity())).getStrPref("DNSCrypt Servers"));
        }

        Preference swAutostartTor = findPreference("swAutostartTor");
        if (swAutostartTor != null) {
            swAutostartTor.setOnPreferenceChangeListener(this);
        }
        Preference pref_fast_theme = findPreference("pref_fast_theme");
        if (pref_fast_theme != null) {
            pref_fast_theme.setOnPreferenceChangeListener(this);
        }

        Preference pref_fast_language = findPreference("pref_fast_language");
        if (pref_fast_language != null) {
            pref_fast_language.setOnPreferenceChangeListener(this);
        }

        if (ModulesStatus.getInstance().getMode() == ROOT_MODE) {
            Preference pref_fast_all_through_tor = findPreference("pref_fast_all_through_tor");
            if (pref_fast_all_through_tor != null) {
                pref_fast_all_through_tor.setOnPreferenceChangeListener(this);
            }

            Preference pref_fast_block_http = findPreference("pref_fast_block_http");
            if (pref_fast_block_http != null) {
                pref_fast_block_http.setOnPreferenceChangeListener(this);
            }

            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String refreshPeriod = shPref.getString("pref_fast_site_refresh_interval", "12");
            refreshPeriodHours = Integer.parseInt(refreshPeriod);

            Preference prefTorSiteUnlock = findPreference("prefTorSiteUnlock");
            Preference prefTorAppUnlock = findPreference("prefTorAppUnlock");

            if (shPref.getBoolean("pref_fast_all_through_tor", true)) {
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
        } else {
            removePreferencesWithNoRootMode();
        }

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {

    }

    @Override
    public void onResume() {
        super.onResume();

        setUpdateTimeLast();
    }

    public void setDnsCryptServersSumm(final String servers) {
        final Preference prefDNSCryptServer = findPreference("prefDNSCryptServer");

        if (prefDNSCryptServer != null) {
            prefDNSCryptServer.setSummary(servers);
        }
    }

    private void setUpdateTimeLast() {

        if (getActivity() == null) {
            return;
        }

        String updateTimeLastStr = new PrefManager(getActivity()).getStrPref("updateTimeLast");
        String lastUpdateResult = new PrefManager(getActivity()).getStrPref("LastUpdateResult");
        final Preference prefLastUpdate = findPreference("pref_fast_chek_update");
        if (prefLastUpdate == null)
            return;

        if (!updateTimeLastStr.isEmpty() && updateTimeLastStr.trim().matches("\\d+")) {
            long updateTimeLast = Long.parseLong(updateTimeLastStr);
            Date date = new Date(updateTimeLast);

            String dateString = android.text.format.DateFormat.getDateFormat(getActivity()).format(date);
            String timeString = android.text.format.DateFormat.getTimeFormat(getActivity()).format(date);

            prefLastUpdate.setSummary(getString(R.string.update_last_check) + " "
                    + dateString + " " + timeString + System.lineSeparator() + lastUpdateResult);
        } else if (lastUpdateResult.equals(getString(R.string.update_fault))
                && new PrefManager(getActivity()).getStrPref("updateTimeLast").isEmpty()
                && appVersion.startsWith("p")) {
            Preference pref_fast_auto_update = findPreference("pref_fast_auto_update");
            if (pref_fast_auto_update != null) {
                pref_fast_auto_update.setEnabled(false);
            }
            prefLastUpdate.setSummary(lastUpdateResult);
        } else {
            prefLastUpdate.setSummary(lastUpdateResult);
        }
        if (getActivity() == null)
            return;

        prefLastUpdate.setOnPreferenceClickListener(preference -> {
            if (prefLastUpdate.isEnabled()) {
                new Handler().post(() -> {

                    if (getActivity() == null) {
                        return;
                    }

                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    intent.setAction("check_update");
                    getActivity().overridePendingTransition(0, 0);
                    getActivity().finish();

                    startActivity(intent);
                });

                return true;
            } else {
                return false;
            }
        });

    }

    @SuppressWarnings("deprecation")
    private void changeTheme() {
        new Handler().post(() -> {

            if (getActivity() == null) {
                return;
            }

            SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            try {
                String theme = defaultSharedPreferences.getString("pref_fast_theme", "4");

                switch (theme) {
                    case "1":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                        break;
                    case "2":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                        break;
                    case "3":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_TIME);
                        break;
                    case "4":
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                        break;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "PreferencesFastFragment changeTheme exception " + e.getMessage() + " " + e.getCause());
            }

            activityCurrentRecreate();
        });

    }

    private void activityCurrentRecreate() {

        if (getActivity() == null) {
            return;
        }

        Intent intent = getActivity().getIntent();
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        getActivity().overridePendingTransition(0, 0);
        getActivity().finish();

        getActivity().overridePendingTransition(0, 0);
        startActivity(intent);

        new PrefManager(getActivity()).setBoolPref("refresh_main_activity", true);
    }

    @Override
    public void onPause() {
        Language.setFromPreference(getActivity(), "pref_fast_language", true);

        super.onPause();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {

        if (getActivity() == null) {
            return false;
        }

        switch (preference.getKey()) {
            case "swAutostartTor":
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours == 0) {
                    return true;
                }
                if (Boolean.valueOf(newValue.toString())) {

                    ComponentName jobService = new ComponentName(getActivity(), GetIPsJobService.class);
                    JobInfo.Builder getIPsJobBuilder = new JobInfo.Builder(mJobId, jobService);
                    getIPsJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                    getIPsJobBuilder.setPeriodic(refreshPeriodHours * 60 * 60 * 1000);

                    JobScheduler jobScheduler = (JobScheduler) getActivity().getSystemService(Context.JOB_SCHEDULER_SERVICE);

                    if (jobScheduler != null) {
                        jobScheduler.schedule(getIPsJobBuilder.build());
                    }
                } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    JobScheduler jobScheduler = (JobScheduler) getActivity().getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    if (jobScheduler != null) {
                        jobScheduler.cancel(mJobId);
                    }
                }
                return true;
            case "pref_fast_all_through_tor":

                if (new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                    ModulesAux.requestModulesStatusUpdate(getActivity());
                }

                Preference prefTorSiteUnlock = findPreference("prefTorSiteUnlock");
                Preference prefTorAppUnlock = findPreference("prefTorAppUnlock");
                if (prefTorSiteUnlock != null && prefTorAppUnlock != null) {
                    if (Boolean.valueOf(newValue.toString())) {
                        prefTorSiteUnlock.setEnabled(false);
                        prefTorAppUnlock.setEnabled(false);
                    } else {
                        prefTorSiteUnlock.setEnabled(true);
                        prefTorAppUnlock.setEnabled(true);
                    }
                }

                return true;
            case "pref_fast_block_http":
                if (new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")
                        || new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                    ModulesAux.requestModulesStatusUpdate(getActivity());
                }
                return true;
            case "pref_fast_theme":
                changeTheme();
                return true;
            case "pref_fast_language":
                new Handler().post(this::activityCurrentRecreate);
                return true;
        }

        return false;
    }

    private void removePreferencesWithNoRootMode() {

        PreferenceCategory torSettingsCategory = findPreference("Tor Settings");

        List<Preference> preferencesList = new ArrayList<>();

        preferencesList.add(findPreference("pref_fast_all_through_tor"));
        preferencesList.add(findPreference("prefTorSiteUnlock"));
        preferencesList.add(findPreference("prefTorAppUnlock"));
        preferencesList.add(findPreference("prefTorSiteExclude"));
        preferencesList.add(findPreference("prefTorAppExclude"));
        preferencesList.add(findPreference("pref_fast_site_refresh_interval"));

        for (Preference preference : preferencesList) {
            if (preference != null) {
                if (torSettingsCategory != null) {
                    torSettingsCategory.removePreference(preference);
                }
            }
        }

        PreferenceCategory fastUpdateCategory = findPreference("fast_update");
        Preference updateThroughTor = findPreference("pref_fast through_tor_update");
        if (fastUpdateCategory != null && updateThroughTor != null) {
            fastUpdateCategory.removePreference(updateThroughTor);
        }

        PreferenceCategory fastOtherCategory = findPreference("fast_other");

        Preference blockHttp = findPreference("pref_fast_block_http");
        if (fastOtherCategory != null && blockHttp != null) {
            fastOtherCategory.removePreference(blockHttp);
        }
    }
}
