package pan.alexander.tordnscrypt.settings.firewall

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

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_SHOWS_ALL_APPS

class FirewallPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.setTitle(R.string.pref_firewall_title)

        findPreference<SwitchPreference>(FIREWALL_SHOWS_ALL_APPS)?.onPreferenceChangeListener = this
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_firewall, rootKey)
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference.key == FIREWALL_SHOWS_ALL_APPS) {
            (parentFragmentManager.findFragmentByTag(FirewallFragment.TAG) as? FirewallFragment)
                ?.apply {
                    viewModel.showAllApps = newValue?.toString()?.toBoolean()
                    viewModel.getDeviceApps()
                }

            return true
        }
        return false
    }
}
