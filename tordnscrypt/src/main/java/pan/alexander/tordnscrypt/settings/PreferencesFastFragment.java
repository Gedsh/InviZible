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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.language.Language;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;


public class PreferencesFastFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    public static final int mJobId = 1;
    private int refreshPeriodHours = 12;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_fast);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        if (getActivity() == null) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        getActivity().setTitle(R.string.drawer_menu_fastSettings);
        findPreference("prefDNSCryptServer").setSummary(new PrefManager(Objects.requireNonNull(getActivity())).getStrPref("DNSCrypt Servers"));

        findPreference("swAutostartTor").setOnPreferenceChangeListener(this);
        findPreference("pref_fast_theme").setOnPreferenceChangeListener(this);
        findPreference("pref_fast_language").setOnPreferenceChangeListener(this);

        if (ModulesStatus.getInstance().isRootAvailable()) {
            findPreference("pref_fast_all_through_tor").setOnPreferenceChangeListener(this);
            findPreference("pref_fast_block_http").setOnPreferenceChangeListener(this);

            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String refreshPeriod = shPref.getString("pref_fast_site_refresh_interval", "12");
            if (refreshPeriod != null) {
                refreshPeriodHours = Integer.parseInt(refreshPeriod);
            }

            if (shPref.getBoolean("pref_fast_all_through_tor", true)) {
                findPreference("prefTorSiteUnlock").setEnabled(false);
                findPreference("prefTorAppUnlock").setEnabled(false);
            } else {
                findPreference("prefTorSiteUnlock").setEnabled(true);
                findPreference("prefTorAppUnlock").setEnabled(true);
            }
        } else {
            removePreferencesWithFullNoRootMode();
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

    public void setDnsCryptServersSumm() {
        if (getActivity() == null)
            return;
        Preference prefDNSCryptServer = findPreference("prefDNSCryptServer");
        if (prefDNSCryptServer != null)
            prefDNSCryptServer.setSummary(new PrefManager(Objects.requireNonNull(getActivity())).getStrPref("DNSCrypt Servers"));
    }

    public void setUpdateTimeLast() {

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
            findPreference("pref_fast_auto_update").setEnabled(false);
            prefLastUpdate.setSummary(lastUpdateResult);
        } else {
            prefLastUpdate.setSummary(lastUpdateResult);
        }
        if (getActivity() == null)
            return;

        prefLastUpdate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (prefLastUpdate.isEnabled()) {
                    new Handler().post(new Runnable() {

                        @Override
                        public void run() {

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
                        }
                    });

                    return true;
                } else {
                    return false;
                }
            }
        });

    }

    private void changeTheme() {
        new Handler().post(new Runnable() {

            @Override
            public void run() {

                if (getActivity() == null) {
                    return;
                }

                SharedPreferences defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                try {
                    String theme = defaultSharedPreferences.getString("pref_fast_theme", "4");
                    if (theme == null) {
                        return;
                    }

                    switch (theme) {
                        case "1":
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                            break;
                        case "2":
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                            break;
                        case "3":
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
                            break;
                        case "4":
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                            break;
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "PreferencesFastFragment changeTheme exception " + e.getMessage() + " " + e.getCause());
                }

                activityCurrentRecreate();
            }
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

                if (Boolean.valueOf(newValue.toString())) {
                    findPreference("prefTorSiteUnlock").setEnabled(false);
                    findPreference("prefTorAppUnlock").setEnabled(false);
                } else {
                    findPreference("prefTorSiteUnlock").setEnabled(true);
                    findPreference("prefTorAppUnlock").setEnabled(true);
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
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        activityCurrentRecreate();
                    }
                });
                return true;
        }

        return false;
    }

    private void removePreferencesWithFullNoRootMode() {

        PreferenceCategory torSettingsCategory = (PreferenceCategory) findPreference("Tor Settings");

        List<Preference> preferencesList = new ArrayList<>();

        preferencesList.add(findPreference("pref_fast_all_through_tor"));
        preferencesList.add(findPreference("prefTorSiteUnlock"));
        preferencesList.add(findPreference("prefTorAppUnlock"));
        preferencesList.add(findPreference("prefTorSiteExclude"));
        preferencesList.add(findPreference("prefTorAppExclude"));
        preferencesList.add(findPreference("pref_fast_site_refresh_interval"));

        for (Preference preference: preferencesList) {
            if (preference != null) {
                torSettingsCategory.removePreference(preference);
            }
        }

        PreferenceCategory fastOtherCategory = (PreferenceCategory) findPreference("fast_other");
        Preference blockHttp = findPreference("pref_fast_block_http");
        fastOtherCategory.removePreference(blockHttp);
    }
}
