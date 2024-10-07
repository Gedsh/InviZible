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

package pan.alexander.tordnscrypt.domain.dns_rules

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.settings.show_rules.recycler.DnsRuleRecycleItem
import pan.alexander.tordnscrypt.utils.Utils.isLogsDirAccessible
import javax.inject.Inject
import javax.inject.Named

class DnsRulesInteractorImpl @Inject constructor(
    private val repository: DnsRulesRepository,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher
) : DnsRulesInteractor {

    override suspend fun getMixedRulesMetadata(type: DnsRuleType): DnsRulesMetadata.MixedDnsRulesMetadata =
        withContext(dispatcherIo) {
            when (type) {
                DnsRuleType.BLACKLIST -> repository.getMixedBlacklistRulesMetadata()
                DnsRuleType.WHITELIST -> repository.getMixedWhitelistRulesMetadata()
                DnsRuleType.IP_BLACKLIST -> repository.getMixedIpBlacklistRulesMetadata()
                DnsRuleType.FORWARDING -> repository.getMixedForwardingRulesMetadata()
                DnsRuleType.CLOAKING -> repository.getMixedCloakingRulesMetadata()
            }
        }

    override suspend fun getSingleRules(type: DnsRuleType): List<DnsRuleRecycleItem.DnsSingleRule> =
        withContext(dispatcherIo) {
            when (type) {
                DnsRuleType.BLACKLIST -> repository.getSingleBlacklistRules()
                DnsRuleType.WHITELIST -> repository.getSingleWhitelistRules()
                DnsRuleType.IP_BLACKLIST -> repository.getSingleIpBlacklistRules()
                DnsRuleType.FORWARDING -> repository.getSingleForwardingRules()
                DnsRuleType.CLOAKING -> repository.getSingleCloakingRules()
            }
        }

    override suspend fun saveSingleRules(
        type: DnsRuleType,
        rules: List<DnsRuleRecycleItem.DnsSingleRule>
    ) = withContext(dispatcherIo) {
        when (type) {
            DnsRuleType.BLACKLIST -> repository.saveSingleBlacklistRules(rules)
            DnsRuleType.WHITELIST -> repository.saveSingleWhitelistRules(rules)
            DnsRuleType.IP_BLACKLIST -> repository.saveSingleIpBlacklistRules(rules)
            DnsRuleType.FORWARDING -> repository.saveSingleForwardingRules(rules)
            DnsRuleType.CLOAKING -> repository.saveSingleCloakingRules(rules)
        }
    }

    override suspend fun getRemoteRulesMetadata(
        type: DnsRuleType
    ): DnsRulesMetadata.RemoteDnsRulesMetadata = withContext(dispatcherIo) {
        when (type) {
            DnsRuleType.BLACKLIST -> repository.getRemoteBlacklistRulesMetadata()
            DnsRuleType.WHITELIST -> repository.getRemoteWhitelistRulesMetadata()
            DnsRuleType.IP_BLACKLIST -> repository.getRemoteIpBlacklistRulesMetadata()
            DnsRuleType.FORWARDING -> repository.getRemoteForwardingRulesMetadata()
            DnsRuleType.CLOAKING -> repository.getRemoteCloakingRulesMetadata()
        }
    }

    override suspend fun getLocalRulesMetadata(
        type: DnsRuleType
    ): DnsRulesMetadata.LocalDnsRulesMetadata = withContext(dispatcherIo) {
        when (type) {
            DnsRuleType.BLACKLIST -> repository.getLocalBlacklistRulesMetadata()
            DnsRuleType.WHITELIST -> repository.getLocalWhitelistRulesMetadata()
            DnsRuleType.IP_BLACKLIST -> repository.getLocalIpBlacklistRulesMetadata()
            DnsRuleType.FORWARDING -> repository.getLocalForwardingRulesMetadata()
            DnsRuleType.CLOAKING -> repository.getLocalCloakingRulesMetadata()
        }
    }

    override suspend fun clearRemoteRules(type: DnsRuleType) = withContext(dispatcherIo) {
        when (type) {
            DnsRuleType.BLACKLIST -> repository.clearRemoteBlacklistRules()
            DnsRuleType.WHITELIST -> repository.clearRemoteWhitelistRules()
            DnsRuleType.IP_BLACKLIST -> repository.clearRemoteIpBlacklistRules()
            DnsRuleType.FORWARDING -> repository.clearRemoteForwardingRules()
            DnsRuleType.CLOAKING -> repository.clearRemoteCloakingRules()
        }
    }

    override suspend fun clearLocalRules(type: DnsRuleType) = withContext(dispatcherIo) {
        when (type) {
            DnsRuleType.BLACKLIST -> repository.clearLocalBlacklistRules()
            DnsRuleType.WHITELIST -> repository.clearLocalWhitelistRules()
            DnsRuleType.IP_BLACKLIST -> repository.clearLocalIpBlacklistRules()
            DnsRuleType.FORWARDING -> repository.clearLocalForwardingRules()
            DnsRuleType.CLOAKING -> repository.clearLocalCloakingRules()
        }
    }

    override suspend fun isExternalStorageAllowsDirectAccess(): Boolean =
        runInterruptible(dispatcherIo) {
            isLogsDirAccessible()
        }
}
