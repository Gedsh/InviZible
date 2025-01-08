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

package pan.alexander.tordnscrypt.data.dns_rules

import java.io.InputStreamReader

interface DnsRulesDataSource {
    fun getBlacklistRulesStream(): InputStreamReader
    fun getSingleBlacklistRulesStream(): InputStreamReader?
    fun saveSingleBlacklistRules(rules: List<String>)
    fun getRemoteBlacklistRulesStream(): InputStreamReader
    fun getRemoteBlacklistRulesFileSize(): Long
    fun getRemoteBlacklistRulesFileDate(): Long
    fun clearRemoteBlacklistRules()
    fun getLocalBlacklistRulesStream(): InputStreamReader
    fun clearLocalBlacklistRules()
    fun getLocalBlacklistRulesFileSize(): Long
    fun getLocalBlacklistRulesFileDate(): Long

    fun getWhitelistRulesStream(): InputStreamReader
    fun getSingleWhitelistRulesStream(): InputStreamReader?
    fun saveSingleWhitelistRules(rules: List<String>)
    fun getRemoteWhitelistRulesStream(): InputStreamReader
    fun getRemoteWhitelistRulesFileSize(): Long
    fun getRemoteWhitelistRulesFileDate(): Long
    fun clearRemoteWhitelistRules()
    fun getLocalWhitelistRulesStream(): InputStreamReader
    fun getLocaleWhitelistRulesFileSize(): Long
    fun getLocalWhitelistRulesFileDate(): Long
    fun clearLocalWhitelistRules()

    fun getIpBlacklistRulesStream(): InputStreamReader
    fun getSingleIpBlacklistRulesStream(): InputStreamReader?
    fun saveSingleIpBlacklistRules(rules: List<String>)
    fun getRemoteIpBlacklistRulesStream(): InputStreamReader
    fun getRemoteIpBlacklistRulesFileSize(): Long
    fun getRemoteIpBlacklistRulesFileDate(): Long
    fun clearRemoteIpBlacklistRules()
    fun getLocalIpBlacklistRulesStream(): InputStreamReader
    fun getLocalIpBlacklistRulesFileSize(): Long
    fun getLocalIpBlacklistRulesFileDate(): Long
    fun clearLocalIpBlacklistRules()

    fun getForwardingRulesStream(): InputStreamReader
    fun getSingleForwardingRulesStream(): InputStreamReader?
    fun saveSingleForwardingRules(rules: List<String>)
    fun getRemoteForwardingRulesStream(): InputStreamReader
    fun getRemoteForwardingRulesFileSize(): Long
    fun getRemoteForwardingRulesFileDate(): Long
    fun clearRemoteForwardingRules()
    fun getLocalForwardingRulesStream(): InputStreamReader
    fun getLocalForwardingRulesFileSize(): Long
    fun getLocalForwardingRulesFileDate(): Long
    fun clearLocalForwardingRules()

    fun getCloakingRulesStream(): InputStreamReader
    fun getSingleCloakingRulesStream(): InputStreamReader?
    fun saveSingleCloakingRules(rules: List<String>)
    fun getRemoteCloakingRulesStream(): InputStreamReader
    fun getRemoteCloakingRulesFileSize(): Long
    fun getRemoteCloakingRulesFileDate(): Long
    fun clearRemoteCloakingRules()
    fun getLocalCloakingRulesStream(): InputStreamReader
    fun getLocalCloakingRulesFileSize(): Long
    fun getLocalCloakingRulesFileDate(): Long
    fun clearLocalCloakingRules()
}
