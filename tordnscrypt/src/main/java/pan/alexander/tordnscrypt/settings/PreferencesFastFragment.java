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
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.Date;
import java.util.Objects;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.language.Language;
import pan.alexander.tordnscrypt.utils.GetIPsJobService;
import pan.alexander.tordnscrypt.utils.PrefManager;


public class PreferencesFastFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    public static final int mJobId = 1;
    private int refreshPeriodHours = 12;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences_fast);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(R.string.drawer_menu_fastSettings);
        findPreference("prefDNSCryptServer").setSummary(new PrefManager(Objects.requireNonNull(getActivity())).getStrPref("DNSCrypt Servers"));

        findPreference("swAutostartTor").setOnPreferenceChangeListener(this);
        findPreference("pref_fast_all_through_tor").setOnPreferenceChangeListener(this);
        findPreference("pref_fast_block_http").setOnPreferenceChangeListener(this);

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        refreshPeriodHours = Integer.parseInt(shPref.getString("pref_fast_site_refresh_interval","12"));
        if (shPref.getBoolean("pref_fast_all_through_tor",true)) {
            findPreference("prefTorSiteUnlock").setEnabled(false);
            findPreference("prefTorAppUnlock").setEnabled(false);
        } else {
            findPreference("prefTorSiteUnlock").setEnabled(true);
            findPreference("prefTorAppUnlock").setEnabled(true);
        }

        setUpdateTimeLast();
    }

    public void setDnsCryptServersSumm() {
        if (getActivity()==null)
            return;
        Preference prefDNSCryptServer = findPreference("prefDNSCryptServer");
        if (prefDNSCryptServer!=null)
            prefDNSCryptServer.setSummary(new PrefManager(Objects.requireNonNull(getActivity())).getStrPref("DNSCrypt Servers"));
    }

    public void setUpdateTimeLast(){
        String updateTimeLastStr = new PrefManager(getActivity()).getStrPref("updateTimeLast");
        String lastUpdateResult = new PrefManager(getActivity()).getStrPref("LastUpdateResult");
        final Preference prefLastUpdate = findPreference("pref_fast_chek_update");
        if (prefLastUpdate==null)
            return;

        if (!updateTimeLastStr.isEmpty()) {
            long updateTimeLast = Long.parseLong(updateTimeLastStr);
            Date date = new Date(updateTimeLast);

            String dateString  = android.text.format.DateFormat.getDateFormat(getActivity()).format(date);
            String timeString = android.text.format.DateFormat.getTimeFormat(getActivity()).format(date);

            prefLastUpdate.setSummary(getString(R.string.update_last_check) + " "
                    + dateString + " " + timeString + System.lineSeparator() + lastUpdateResult);
        }
        if (getActivity()==null)
            return;

        prefLastUpdate.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (prefLastUpdate.isEnabled()) {
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.setAction("check_update");
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    getActivity().finish();
                    return true;
                } else {
                    return false;
                }
            }
        });

    }



    @Override
    public void onPause() {
        Language.setFromPreference(getActivity(), "pref_fast_language", true);

        super.onPause();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case "swAutostartTor":
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP || refreshPeriodHours==0) {
                    return true;
                }
                if (Boolean.valueOf(newValue.toString())) {

                    ComponentName jobService = new ComponentName(getActivity(), GetIPsJobService.class);
                    JobInfo.Builder getIPsJobBuilder = new JobInfo.Builder(mJobId, jobService);
                    getIPsJobBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                    getIPsJobBuilder.setPeriodic(refreshPeriodHours*60*60*1000);

                    JobScheduler jobScheduler = (JobScheduler) getActivity().getSystemService(Context.JOB_SCHEDULER_SERVICE);

                    if (jobScheduler != null) {
                        jobScheduler.schedule(getIPsJobBuilder.build());
                    }
                } else if (!new PrefManager(getActivity()).getBoolPref("Tor Running")){
                    JobScheduler jobScheduler = (JobScheduler) getActivity().getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    if (jobScheduler != null) {
                        jobScheduler.cancel(mJobId);
                    }
                }
                return true;
            case "pref_fast_all_through_tor":

                if (new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    TopFragment.NotificationDialogFragment commandResult =
                            TopFragment.NotificationDialogFragment.newInstance(getText(R.string.pref_common_restart_tor).toString());
                    commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
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
                if (new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {
                    TopFragment.NotificationDialogFragment commandResult =
                            TopFragment.NotificationDialogFragment.newInstance(getText(R.string.pref_common_restart_dnscrypt).toString());
                    commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                } else if (new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    TopFragment.NotificationDialogFragment commandResult =
                            TopFragment.NotificationDialogFragment.newInstance(getText(R.string.pref_common_restart_tor).toString());
                    commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                }
                return true;
        }

        return false;
    }
}
