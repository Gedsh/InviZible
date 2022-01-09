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

package pan.alexander.tordnscrypt.settings.firewall

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.CoroutinesModule.Companion.DISPATCHER_COMPUTATION
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import java.util.concurrent.ConcurrentSkipListSet
import javax.inject.Inject
import javax.inject.Named

class FirewallViewModel @Inject constructor(
    private val preferenceRepository: dagger.Lazy<PreferenceRepository>,
    @Named(DISPATCHER_COMPUTATION)
    private val dispatcherComputation: CoroutineDispatcher
) : ViewModel(), InstalledApplicationsManager.OnAppAddListener {

    private val firewallStateMutableLiveData = MutableLiveData<FirewallState>()
    val firewallStateLiveData: LiveData<FirewallState> get() = firewallStateMutableLiveData

    val appsCompleteSet = ConcurrentSkipListSet<FirewallAppModel>()

    val modulesStatus = ModulesStatus.getInstance()

    var showAllApps: Boolean? = null

    var appsAllowLan = mutableSetOf<Int>()
    var appsAllowWifi = mutableSetOf<Int>()
    var appsAllowGsm = mutableSetOf<Int>()
    var appsAllowRoaming = mutableSetOf<Int>()
    var appsAllowVpn = mutableSetOf<Int>()

    fun getDeviceApps() {
        viewModelScope.launch(dispatcherComputation) {
            tryGetDeviceApps()
        }
    }

    private fun tryGetDeviceApps() {
        try {

            appsCompleteSet.clear()
            firewallStateMutableLiveData.postValue(FirewallState.Preparing)

            val preferences = preferenceRepository.get()

            appsAllowLan.addAll(
                preferences.getStringSetPreference(APPS_ALLOW_LAN_PREF).toIntSet()
            )
            appsAllowWifi.addAll(
                preferences.getStringSetPreference(APPS_ALLOW_WIFI_PREF).toIntSet()
            )
            appsAllowGsm.addAll(
                preferences.getStringSetPreference(APPS_ALLOW_GSM_PREF).toIntSet()
            )
            appsAllowRoaming.addAll(
                preferences.getStringSetPreference(APPS_ALLOW_ROAMING).toIntSet()
            )
            appsAllowVpn.addAll(
                preferences.getStringSetPreference(APPS_ALLOW_VPN).toIntSet()
            )

            val installedApps = InstalledApplicationsManager.Builder()
                .setOnAppAddListener(this)
                .showSpecialApps(true)
                .showAllApps(showAllApps)
                .build()
                .getInstalledApps()

            appsCompleteSet.clear()

            for (applicationData: ApplicationData in installedApps) {
                val uid = applicationData.uid
                appsCompleteSet.add(
                    FirewallAppModel(
                        applicationData,
                        appsAllowLan.contains(uid),
                        appsAllowWifi.contains(uid),
                        appsAllowGsm.contains(uid),
                        appsAllowRoaming.contains(uid),
                        appsAllowVpn.contains(uid)
                    )
                )
            }

        } catch (e: Exception) {
            loge("FirewallViewModel getDeviceApps exception", e)
        } finally {
            firewallStateMutableLiveData.postValue(FirewallState.Ready)
        }
    }

    override fun onAppAdded(application: ApplicationData) {
        val uid = application.uid
        appsCompleteSet.add(
            FirewallAppModel(
                application,
                appsAllowLan.contains(uid),
                appsAllowWifi.contains(uid),
                appsAllowGsm.contains(uid),
                appsAllowRoaming.contains(uid),
                appsAllowVpn.contains(uid)
            )
        )
        firewallStateMutableLiveData.postValue(FirewallState.Preparing)
    }

    fun isFirewallChangesSavingRequired(): Boolean {
        if (firewallStateLiveData.value is FirewallState.Preparing) {
            return false
        }

        val appsAllowLanToSave = mutableSetOf<Int>()
        val appsAllowWifiToSave = mutableSetOf<Int>()
        val appsAllowGsmToSave = mutableSetOf<Int>()
        val appsAllowRoamingToSave = mutableSetOf<Int>()
        val appsAllowVpnToSave = mutableSetOf<Int>()

        for (firewallAppModel: FirewallAppModel in appsCompleteSet) {
            val uid = firewallAppModel.applicationData.uid
            if (firewallAppModel.allowLan) {
                appsAllowLanToSave.add(uid)
            }
            if (firewallAppModel.allowWifi) {
                appsAllowWifiToSave.add(uid)
            }
            if (firewallAppModel.allowGsm) {
                appsAllowGsmToSave.add(uid)
            }
            if (firewallAppModel.allowRoaming) {
                appsAllowRoamingToSave.add(uid)
            }
            if (firewallAppModel.allowVPN) {
                appsAllowVpnToSave.add(uid)
            }
        }

        var iptablesUpdateRequired = false

        if (appsAllowLanToSave.size != appsAllowLan.size
            || !appsAllowLanToSave.containsAll(appsAllowLan)
        ) {
            iptablesUpdateRequired = true
        }
        if (appsAllowWifiToSave.size != appsAllowWifi.size
            || !appsAllowWifiToSave.containsAll(appsAllowWifi)
        ) {
            iptablesUpdateRequired = true
        }
        if (appsAllowGsmToSave.size != appsAllowGsm.size
            || !appsAllowGsmToSave.containsAll(appsAllowGsm)
        ) {
            iptablesUpdateRequired = true
        }
        if (appsAllowRoamingToSave.size != appsAllowRoaming.size
            || !appsAllowRoamingToSave.containsAll(appsAllowRoaming)
        ) {
            iptablesUpdateRequired = true
        }
        if (appsAllowVpnToSave.size != appsAllowVpn.size
            || !appsAllowVpnToSave.containsAll(appsAllowVpn)
        ) {
            iptablesUpdateRequired = true
        }

        return iptablesUpdateRequired
    }

    fun saveFirewallChanges() {

        val appsAllowLanToSave = mutableSetOf<Int>()
        val appsAllowWifiToSave = mutableSetOf<Int>()
        val appsAllowGsmToSave = mutableSetOf<Int>()
        val appsAllowRoamingToSave = mutableSetOf<Int>()
        val appsAllowVpnToSave = mutableSetOf<Int>()

        for (firewallAppModel: FirewallAppModel in appsCompleteSet) {
            val uid = firewallAppModel.applicationData.uid
            if (firewallAppModel.allowLan) {
                appsAllowLanToSave.add(uid)
            }
            if (firewallAppModel.allowWifi) {
                appsAllowWifiToSave.add(uid)
            }
            if (firewallAppModel.allowGsm) {
                appsAllowGsmToSave.add(uid)
            }
            if (firewallAppModel.allowRoaming) {
                appsAllowRoamingToSave.add(uid)
            }
            if (firewallAppModel.allowVPN) {
                appsAllowVpnToSave.add(uid)
            }
        }

        var iptablesUpdateRequired = false
        val preferences = preferenceRepository.get()


        if (appsAllowLanToSave.size != appsAllowLan.size
            || !appsAllowLanToSave.containsAll(appsAllowLan)
        ) {
            appsAllowLan = appsAllowLanToSave
            preferences.setStringSetPreference(APPS_ALLOW_LAN_PREF, appsAllowLan.toStringSet())
            iptablesUpdateRequired = true
        }
        if (appsAllowWifiToSave.size != appsAllowWifi.size
            || !appsAllowWifiToSave.containsAll(appsAllowWifi)
        ) {
            appsAllowWifi = appsAllowWifiToSave
            preferences.setStringSetPreference(APPS_ALLOW_WIFI_PREF, appsAllowWifi.toStringSet())
            iptablesUpdateRequired = true
        }
        if (appsAllowGsmToSave.size != appsAllowGsm.size
            || !appsAllowGsmToSave.containsAll(appsAllowGsm)
        ) {
            appsAllowGsm = appsAllowGsmToSave
            preferences.setStringSetPreference(APPS_ALLOW_GSM_PREF, appsAllowGsm.toStringSet())
            iptablesUpdateRequired = true
        }
        if (appsAllowRoamingToSave.size != appsAllowRoaming.size
            || !appsAllowRoamingToSave.containsAll(appsAllowRoaming)
        ) {
            appsAllowRoaming = appsAllowRoamingToSave
            preferences.setStringSetPreference(APPS_ALLOW_ROAMING, appsAllowRoaming.toStringSet())
            iptablesUpdateRequired = true
        }
        if (appsAllowVpnToSave.size != appsAllowVpn.size
            || !appsAllowVpnToSave.containsAll(appsAllowVpn)
        ) {
            appsAllowVpn = appsAllowVpnToSave
            preferences.setStringSetPreference(APPS_ALLOW_VPN, appsAllowVpn.toStringSet())
            iptablesUpdateRequired = true
        }

        if (iptablesUpdateRequired) {
            modulesStatus
                .setIptablesRulesUpdateRequested(App.instance.applicationContext, true)
        }
    }

    fun activateAllFirsStart() {

        for (firewallAppModel: FirewallAppModel in appsCompleteSet) {
            val uid = firewallAppModel.applicationData.uid
            appsAllowLan.add(uid)
            appsAllowWifi.add(uid)
            appsAllowGsm.add(uid)
            appsAllowRoaming.add(uid)
            appsAllowVpn.add(uid)
        }

        val preferences = preferenceRepository.get()

        preferences.setStringSetPreference(APPS_ALLOW_LAN_PREF, appsAllowLan.toStringSet())

        preferences.setStringSetPreference(APPS_ALLOW_WIFI_PREF, appsAllowWifi.toStringSet())

        preferences.setStringSetPreference(APPS_ALLOW_GSM_PREF, appsAllowGsm.toStringSet())

        preferences.setStringSetPreference(APPS_ALLOW_ROAMING, appsAllowRoaming.toStringSet())

        if (modulesStatus.isRootAvailable) {
            preferences.setStringSetPreference(APPS_ALLOW_VPN, appsAllowVpn.toStringSet())
        }
    }
}
