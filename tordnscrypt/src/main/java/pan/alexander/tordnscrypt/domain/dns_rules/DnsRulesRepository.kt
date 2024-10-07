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

import pan.alexander.tordnscrypt.settings.show_rules.recycler.DnsRuleRecycleItem

interface DnsRulesRepository {

    suspend fun getMixedBlacklistRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata
    suspend fun getSingleBlacklistRules(): List<DnsRuleRecycleItem.DnsSingleRule>
    fun saveSingleBlacklistRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>)
    suspend fun getRemoteBlacklistRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata
    fun clearRemoteBlacklistRules()
    suspend fun getLocalBlacklistRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata
    fun clearLocalBlacklistRules()

    suspend fun getMixedWhitelistRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata
    suspend fun getSingleWhitelistRules(): List<DnsRuleRecycleItem.DnsSingleRule>
    fun saveSingleWhitelistRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>)
    suspend fun getRemoteWhitelistRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata
    fun clearRemoteWhitelistRules()
    suspend fun getLocalWhitelistRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata
    fun clearLocalWhitelistRules()

    suspend fun getMixedIpBlacklistRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata
    suspend fun getSingleIpBlacklistRules(): List<DnsRuleRecycleItem.DnsSingleRule>
    fun saveSingleIpBlacklistRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>)
    suspend fun getRemoteIpBlacklistRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata
    fun clearRemoteIpBlacklistRules()
    suspend fun getLocalIpBlacklistRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata
    fun clearLocalIpBlacklistRules()

    suspend fun getMixedForwardingRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata
    suspend fun getSingleForwardingRules(): List<DnsRuleRecycleItem.DnsSingleRule>
    fun saveSingleForwardingRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>)
    suspend fun getRemoteForwardingRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata
    fun clearRemoteForwardingRules()
    suspend fun getLocalForwardingRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata
    fun clearLocalForwardingRules()

    suspend fun getMixedCloakingRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata
    suspend fun getSingleCloakingRules(): List<DnsRuleRecycleItem.DnsSingleRule>
    fun saveSingleCloakingRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>)
    suspend fun getRemoteCloakingRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata
    fun clearRemoteCloakingRules()
    suspend fun getLocalCloakingRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata
    fun clearLocalCloakingRules()

    companion object {
        const val LOCAL_RULES_DEFAULT_HEADER = "local-rules.txt"
        const val REMOTE_RULES_DEFAULT_HEADER = "remote-rules.txt"
    }
}
