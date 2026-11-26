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

package pan.alexander.tordnscrypt.settings.firewall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.CoroutinesModule.Companion.DISPATCHER_COMPUTATION
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.Utils
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager
import pan.alexander.tordnscrypt.utils.apps.CrossUserPermission
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import java.util.concurrent.ConcurrentSkipListSet
import javax.inject.Inject
import javax.inject.Named

class FirewallViewModel @Inject constructor(
    private val preferenceRepository: dagger.Lazy<PreferenceRepository>,
    @Named(DISPATCHER_COMPUTATION)
    private val dispatcherComputation: CoroutineDispatcher,
    private val pathVars: dagger.Lazy<PathVars>
) : ViewModel(), InstalledApplicationsManager.OnAppAddListener {

    private val firewallStateMutableLiveData = MutableLiveData<FirewallState>()
    val firewallStateLiveData: LiveData<FirewallState> get() = firewallStateMutableLiveData

    val appsCompleteSet = ConcurrentSkipListSet<FirewallAppModel>()

    val modulesStatus = ModulesStatus.getInstance()

    var appsAllowLan = mutableSetOf<Int>()
    var appsAllowWifi = mutableSetOf<Int>()
    var appsAllowGsm = mutableSetOf<Int>()
    var appsAllowRoaming = mutableSetOf<Int>()
    var appsAllowVpn = mutableSetOf<Int>()
    private val appsBypassVpn = mutableSetOf<String>()

    val criticalSystemUids = hashSetOf<Int>()
    private val appsLock = Mutex()
    private val broadcasts = LocalBroadcastManager.getInstance(App.instance)
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) =
            getDeviceApps(initialLoading = false)
    }

    init {
        broadcasts.registerReceiver(permissionReceiver, IntentFilter(CrossUserPermission.APPS_CHANGED))
    }

    fun getDeviceApps() = getDeviceApps(initialLoading = true)

    private fun getDeviceApps(initialLoading: Boolean) {
        viewModelScope.launch {
            appsLock.withLock { tryGetDeviceApps(initialLoading) }
        }
    }

    override fun onCleared() {
        broadcasts.unregisterReceiver(permissionReceiver)
    }

    private suspend fun tryGetDeviceApps(initialLoading: Boolean) {
        try {
            // Keep the live objects so edits made while discovery runs survive the refresh.
            val previous = if (initialLoading) {
                emptyMap()
            } else {
                appsCompleteSet.associateBy { it.applicationData.uid }
            }

            if (initialLoading) {
                appsCompleteSet.clear()
                firewallStateMutableLiveData.value = FirewallState.Preparing

                // The saved baseline is UI-owned. Refresh must retain edits/saves made since loading.
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
                if (modulesStatus.mode == OperationMode.VPN_MODE) {
                    appsBypassVpn.addAll(
                        preferences.getStringSetPreference(APPS_BYPASS_VPN)
                    )
                }
            }

            val builder = InstalledApplicationsManager.Builder()
                .showSpecialApps(true)
                .setIconRequired()
            if (initialLoading) builder.setOnAppAddListener(this)
            val (installedApps, complete) = withContext(dispatcherComputation) {
                val manager = builder.build()
                manager.getInstalledApps() to manager.isInventoryComplete
            }
            if (!initialLoading && (installedApps.isEmpty() || !complete)) return

            criticalSystemUids.addAll(Utils.getCriticalSystemUids(pathVars.get().appUid))

            val replacement = installedApps.map { applicationData ->
                previous[applicationData.uid] ?: createFirewallAppModel(applicationData)
            }
            // Publish together with UI edits and saving, never an empty/partially rebuilt list.
            appsCompleteSet.clear()
            appsCompleteSet.addAll(replacement)

        } catch (e: Exception) {
            loge("FirewallViewModel getDeviceApps exception", e)
        } finally {
            firewallStateMutableLiveData.value = FirewallState.Ready
        }
    }

    override fun onAppAdded(application: ApplicationData) {
        viewModelScope.launch {
            appsCompleteSet.add(createFirewallAppModel(application))
            firewallStateMutableLiveData.value = FirewallState.Preparing
        }
    }

    private fun createFirewallAppModel(application: ApplicationData): FirewallAppModel {
        val uid = application.uid
        return FirewallAppModel(
            application,
            appsAllowLan.contains(uid),
            appsAllowWifi.contains(uid),
            appsAllowGsm.contains(uid),
            appsAllowRoaming.contains(uid),
            appsAllowVpn.contains(uid),
            appsBypassVpn.contains(application.pack)
        )
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

        if (appsAllowLanToSave != appsAllowLan) {
            iptablesUpdateRequired = true
        }
        if (appsAllowWifiToSave != appsAllowWifi) {
            iptablesUpdateRequired = true
        }
        if (appsAllowGsmToSave != appsAllowGsm) {
            iptablesUpdateRequired = true
        }
        if (appsAllowRoamingToSave != appsAllowRoaming) {
            iptablesUpdateRequired = true
        }
        if (appsAllowVpnToSave != appsAllowVpn) {
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


        if (appsAllowLanToSave != appsAllowLan) {
            appsAllowLan = appsAllowLanToSave
            preferences.setStringSetPreference(APPS_ALLOW_LAN_PREF, appsAllowLan.toStringSet())
            iptablesUpdateRequired = true
        }
        if (appsAllowWifiToSave != appsAllowWifi) {
            appsAllowWifi = appsAllowWifiToSave
            preferences.setStringSetPreference(APPS_ALLOW_WIFI_PREF, appsAllowWifi.toStringSet())
            iptablesUpdateRequired = true
        }
        if (appsAllowGsmToSave != appsAllowGsm) {
            appsAllowGsm = appsAllowGsmToSave
            preferences.setStringSetPreference(APPS_ALLOW_GSM_PREF, appsAllowGsm.toStringSet())
            iptablesUpdateRequired = true
        }
        if (appsAllowRoamingToSave != appsAllowRoaming) {
            appsAllowRoaming = appsAllowRoamingToSave
            preferences.setStringSetPreference(APPS_ALLOW_ROAMING, appsAllowRoaming.toStringSet())
            iptablesUpdateRequired = true
        }
        if (appsAllowVpnToSave != appsAllowVpn) {
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
