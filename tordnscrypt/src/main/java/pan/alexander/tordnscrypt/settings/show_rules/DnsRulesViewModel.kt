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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.show_rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import pan.alexander.tordnscrypt.di.CoroutinesModule.Companion.SUPERVISOR_JOB_IO_DISPATCHER_SCOPE
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRulesInteractor
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.settings.show_rules.existing.RemixExistingRulesWorkManager
import pan.alexander.tordnscrypt.settings.show_rules.local.UpdateLocalRulesWorkManager
import pan.alexander.tordnscrypt.settings.show_rules.recycler.DnsRuleRecycleItem
import pan.alexander.tordnscrypt.settings.show_rules.remote.UpdateRemoteRulesWorkManager
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_BLACKLIST_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_CLOAKING_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_FORWARDING_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_IP_BLACKLIST_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_WHITELIST_URL
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Named

class DnsRulesViewModel @Inject constructor(
    private val interactor: DnsRulesInteractor,
    @Named(SUPERVISOR_JOB_IO_DISPATCHER_SCOPE)
    private val baseCoroutineScope: CoroutineScope,
    private val preferences: dagger.Lazy<PreferenceRepository>,
    private val remixExistingRulesWorkManager: dagger.Lazy<RemixExistingRulesWorkManager>,
    private val updateRemoteDnsRulesManager: dagger.Lazy<UpdateRemoteRulesWorkManager>,
    private val updateLocalRulesWorkManager: dagger.Lazy<UpdateLocalRulesWorkManager>
) : ViewModel() {
    private val supervisorScope = baseCoroutineScope + CoroutineName("DnsRulesViewModel")

    private val dnsRulesMutableStateFlow: MutableStateFlow<List<DnsRuleRecycleItem>?> =
        MutableStateFlow(null)
    val dnsRulesStateFlow: StateFlow<List<DnsRuleRecycleItem>?> get() = dnsRulesMutableStateFlow

    val totalRulesCount = AtomicInteger(0)

    fun requestRules(rulesType: DnsRuleType) {
        viewModelScope.launch {
            try {
                tryRequestRules(rulesType)
            } catch (e: Exception) {
                loge("DnsRulesViewModel requestRules", e)
            }
        }

    }

    private suspend fun tryRequestRules(rulesType: DnsRuleType) {
        val totalRulesCount = interactor.getMixedRulesMetadata(rulesType).count

        val singleRules = interactor.getSingleRules(rulesType)

        this@DnsRulesViewModel.totalRulesCount.set(totalRulesCount)

        dnsRulesMutableStateFlow.value = mutableListOf<DnsRuleRecycleItem>().also {

            var displayedRemoteRulesLastIndex = -1
            val remoteRulesMetadata = interactor.getRemoteRulesMetadata(rulesType)
            if (remoteRulesMetadata.count > 0) {
                displayedRemoteRulesLastIndex = 0
                it.add(
                    displayedRemoteRulesLastIndex,
                    DnsRuleRecycleItem.DnsRemoteRule(
                        name = remoteRulesMetadata.name,
                        url = remoteRulesMetadata.url,
                        date = remoteRulesMetadata.date,
                        count = remoteRulesMetadata.count,
                        size = remoteRulesMetadata.size,
                        inProgress = false
                    )
                )
            }
            val displayedAddButtonRemoteRulesLastIndex = displayedRemoteRulesLastIndex + 1
            it.add(
                displayedAddButtonRemoteRulesLastIndex,
                DnsRuleRecycleItem.AddRemoteRulesButton
            )

            val displayedAddButtonLocalRulesLastIndex: Int
            val localRulesMetadata = interactor.getLocalRulesMetadata(rulesType)
            if (localRulesMetadata.count > 0) {
                val displayedLocalRulesLastIndex = displayedAddButtonRemoteRulesLastIndex + 1
                it.add(
                    displayedLocalRulesLastIndex,
                    DnsRuleRecycleItem.DnsLocalRule(
                        name = localRulesMetadata.name,
                        date = localRulesMetadata.date,
                        count = localRulesMetadata.count,
                        size = localRulesMetadata.size,
                        inProgress = false
                    )
                )
                displayedAddButtonLocalRulesLastIndex = displayedLocalRulesLastIndex + 1
                it.add(
                    displayedAddButtonLocalRulesLastIndex,
                    DnsRuleRecycleItem.AddLocalRulesButton
                )
            } else {
                displayedAddButtonLocalRulesLastIndex = displayedAddButtonRemoteRulesLastIndex + 1
                it.add(
                    displayedAddButtonLocalRulesLastIndex,
                    DnsRuleRecycleItem.AddLocalRulesButton
                )
            }


            it.addAll(singleRules)

            it.add(DnsRuleRecycleItem.AddSingleRuleButton)
        }
    }

    fun updateRemoteRules(
        ruleType: DnsRuleType,
        currentRules: List<DnsRuleRecycleItem>,
        name: String
    ) {
        supervisorScope.launch {
            try {
                saveTemporarily(currentRules)
                saveSingleRules(ruleType, getSingleRules(currentRules))
                updateRemoteDnsRulesManager.get().startRefreshDnsRules(name, ruleType)
            } catch (e: Exception) {
                loge("DnsRulesViewModel updateRemoteRules", e)
            }
        }
    }

    fun deleteRemoteRules(ruleType: DnsRuleType, currentRules: List<DnsRuleRecycleItem>) {
        supervisorScope.launch {
            try {
                saveTemporarily(currentRules)
                updateRemoteDnsRulesManager.get().stopRefreshDnsRules(ruleType)
                saveSingleRules(ruleType, getSingleRules(currentRules))
                delay(500)
                interactor.clearRemoteRules(ruleType)
                remixExistingRulesWorkManager.get().startMix(ruleType)
            } catch (e: Exception) {
                loge("DnsRulesViewModel deleteRemoteRules", e)
            }
        }
    }

    fun importLocalRules(
        ruleType: DnsRuleType,
        currentRules: List<DnsRuleRecycleItem>,
        files: Array<*>
    ) {
        supervisorScope.launch {
            try {
                saveTemporarily(currentRules)
                saveSingleRules(ruleType, getSingleRules(currentRules))
                updateLocalRulesWorkManager.get().startImportDnsRules(ruleType, files)
            } catch (e: Exception) {
                loge("DnsRulesViewModel importLocalRules", e)
            }
        }
    }

    fun deleteLocalRules(ruleType: DnsRuleType, currentRules: List<DnsRuleRecycleItem>) {
        supervisorScope.launch {
            try {
                saveTemporarily(currentRules)
                updateLocalRulesWorkManager.get().stopImportDnsRules(ruleType)
                saveSingleRules(ruleType, getSingleRules(currentRules))
                delay(500)
                interactor.clearLocalRules(ruleType)
                remixExistingRulesWorkManager.get().startMix(ruleType)
            } catch (e: Exception) {
                loge("DnsRulesViewModel deleteLocalRules", e)
            }
        }
    }

    private fun getSingleRules(rules: List<DnsRuleRecycleItem>) =
        rules.filterIsInstance<DnsRuleRecycleItem.DnsSingleRule>()

    fun saveTemporarily(rules: List<DnsRuleRecycleItem>) {
        val savedRules = dnsRulesMutableStateFlow.value
        if (savedRules == null || rules.size != savedRules.size || !rules.containsAll(savedRules)) {
            dnsRulesMutableStateFlow.value = rules
        }
    }

    fun saveRules(ruleType: DnsRuleType, rules: List<DnsRuleRecycleItem>) {
        supervisorScope.launch {
            try {
                val saved = saveSingleRules(ruleType, getSingleRules(rules))
                if (saved) {
                    remixExistingRulesWorkManager.get().startMix(ruleType)
                }
            } catch (e: Exception) {
                loge("DnsRulesViewModel saveRules", e)
            }
        }
    }

    fun saveRemoteRulesUrl(ruleType: DnsRuleType, url: String) = with(preferences.get()) {
        val urlToSave = if (url.startsWith("http")) {
            url
        } else {
            "https://$url"
        }
        when (ruleType) {
            DnsRuleType.BLACKLIST -> setStringPreference(REMOTE_BLACKLIST_URL, urlToSave)
            DnsRuleType.WHITELIST -> setStringPreference(REMOTE_WHITELIST_URL, urlToSave)
            DnsRuleType.IP_BLACKLIST -> setStringPreference(REMOTE_IP_BLACKLIST_URL, urlToSave)
            DnsRuleType.FORWARDING -> setStringPreference(REMOTE_FORWARDING_URL, urlToSave)
            DnsRuleType.CLOAKING -> setStringPreference(REMOTE_CLOAKING_URL, urlToSave)
        }
    }

    private suspend fun saveSingleRules(
        ruleType: DnsRuleType,
        rules: List<DnsRuleRecycleItem.DnsSingleRule>,
    ): Boolean {
        val singleRules = interactor.getSingleRules(ruleType)
        if (singleRules.size != rules.size || !singleRules.containsAll(rules)) {
            interactor.saveSingleRules(ruleType, rules)
            return true
        }
        return false
    }

    suspend fun isExternalStorageAllowsDirectAccess() =
        interactor.isExternalStorageAllowsDirectAccess()
}
