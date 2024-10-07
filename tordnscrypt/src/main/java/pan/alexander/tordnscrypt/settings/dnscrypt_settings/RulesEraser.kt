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

package pan.alexander.tordnscrypt.settings.dnscrypt_settings

import androidx.annotation.WorkerThread
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.show_rules.existing.RemixExistingRulesWorkManager
import pan.alexander.tordnscrypt.settings.show_rules.local.UpdateLocalRulesWorkManager
import pan.alexander.tordnscrypt.settings.show_rules.remote.UpdateRemoteRulesWorkManager
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_BLACKLIST_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_CLOAKING_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_FORWARDING_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_IP_BLACKLIST_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_WHITELIST_URL
import java.io.File
import javax.inject.Inject
import kotlin.Exception

class RulesEraser @Inject constructor(
    private val preferences: PreferenceRepository,
    private val pathVars: PathVars,
    private val remixExistingRulesWorkManager: RemixExistingRulesWorkManager,
    private val updateRemoteDnsRulesManager: UpdateRemoteRulesWorkManager,
    private val updateLocalRulesWorkManager: UpdateLocalRulesWorkManager
) {

    var callback: OnRulesErased? = null

    @WorkerThread
    fun eraseRules(ruleType: DnsRuleType) {
        stopRelatedWorks(ruleType)
        Thread.sleep(500)
        getFiles(ruleType).forEach {
            eraseFile(it)
        }
        erasePreference(ruleType)
        callback?.onRulesEraseFinished()
    }

    private fun getFiles(ruleType: DnsRuleType) =
        when (ruleType) {
            DnsRuleType.BLACKLIST -> listOf(
                pathVars.dnsCryptBlackListPath,
                pathVars.dnsCryptSingleBlackListPath,
                pathVars.dnsCryptLocalBlackListPath,
                pathVars.dnsCryptRemoteBlackListPath
            )

            DnsRuleType.IP_BLACKLIST -> listOf(
                pathVars.dnsCryptIPBlackListPath,
                pathVars.dnsCryptSingleIPBlackListPath,
                pathVars.dnsCryptLocalIPBlackListPath,
                pathVars.dnsCryptRemoteIPBlackListPath
            )

            DnsRuleType.WHITELIST -> listOf(
                pathVars.dnsCryptWhiteListPath,
                pathVars.dnsCryptSingleWhiteListPath,
                pathVars.dnsCryptLocalWhiteListPath,
                pathVars.dnsCryptRemoteWhiteListPath
            )

            DnsRuleType.CLOAKING -> listOf(
                pathVars.dnsCryptCloakingRulesPath,
                pathVars.dnsCryptSingleCloakingRulesPath,
                pathVars.dnsCryptLocalCloakingRulesPath,
                pathVars.dnsCryptRemoteCloakingRulesPath
            )

            DnsRuleType.FORWARDING -> listOf(
                pathVars.dnsCryptForwardingRulesPath,
                pathVars.dnsCryptSingleForwardingRulesPath,
                pathVars.dnsCryptLocalForwardingRulesPath,
                pathVars.dnsCryptRemoteForwardingRulesPath
            )
        }

    private fun eraseFile(filePath: String) {

        var eraseText = ""
        if (filePath == pathVars.dnsCryptCloakingRulesPath
            || filePath == pathVars.dnsCryptSingleCloakingRulesPath
        ) {
            eraseText = pathVars.dnsCryptDefaultCloakingRule
        } else if (filePath == pathVars.dnsCryptForwardingRulesPath
            || filePath == pathVars.dnsCryptSingleForwardingRulesPath
        ) {
            eraseText = pathVars.dnsCryptDefaultForwardingRule
        }

        try {
            val file = File(filePath)
            if (file.isFile) {
                file.printWriter().use { it.println(eraseText) }
            }
        } catch (e: Exception) {
            loge("EraseRules", e)
        }

    }

    private fun erasePreference(ruleType: DnsRuleType) {
        preferences.setStringPreference(getRemoteRulesUrlPreferenceKey(ruleType), "")
    }

    private fun getRemoteRulesUrlPreferenceKey(ruleType: DnsRuleType) =
        when (ruleType) {
            DnsRuleType.BLACKLIST -> REMOTE_BLACKLIST_URL
            DnsRuleType.IP_BLACKLIST -> REMOTE_IP_BLACKLIST_URL
            DnsRuleType.WHITELIST -> REMOTE_WHITELIST_URL
            DnsRuleType.CLOAKING -> REMOTE_CLOAKING_URL
            DnsRuleType.FORWARDING -> REMOTE_FORWARDING_URL
        }

    private fun stopRelatedWorks(ruleType: DnsRuleType) {
        remixExistingRulesWorkManager.stopMix(ruleType)
        updateRemoteDnsRulesManager.stopRefreshDnsRules(ruleType)
        updateLocalRulesWorkManager.stopImportDnsRules(ruleType)
    }

    interface OnRulesErased {
        fun onRulesEraseFinished()
    }
}
