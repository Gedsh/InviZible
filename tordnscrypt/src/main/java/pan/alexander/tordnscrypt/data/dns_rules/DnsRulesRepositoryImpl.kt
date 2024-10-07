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

package pan.alexander.tordnscrypt.data.dns_rules

import kotlinx.coroutines.isActive
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRulesMetadata
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRulesRepository
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRulesRepository.Companion.LOCAL_RULES_DEFAULT_HEADER
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRulesRepository.Companion.REMOTE_RULES_DEFAULT_HEADER
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.show_rules.recycler.DnsRuleRecycleItem
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

class DnsRulesRepositoryImpl @Inject constructor(
    private val dataSource: DnsRulesDataSource,
    pathVars: PathVars
) : DnsRulesRepository {

    private val forwardingDefaultRule = pathVars.dnsCryptDefaultForwardingRule
        .substringBefore(" ")
    private val cloakingDefaultRule = pathVars.dnsCryptDefaultCloakingRule
        .substringBefore(" ")

    override suspend fun getMixedBlacklistRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata =
        DnsRulesMetadata.MixedDnsRulesMetadata(
            getRulesCountFromFile(dataSource.getBlacklistRulesStream())
        )

    override suspend fun getSingleBlacklistRules(): List<DnsRuleRecycleItem.DnsSingleRule> =
        getSingleRulesFromFile(
            dataSource.getSingleBlacklistRulesStream() ?: run {
                val rules = if (getMixedBlacklistRulesMetadata().count > 0
                    && getLocalBlacklistRulesMetadata().count == 0
                ) {
                    getSingleRulesFromFile(dataSource.getBlacklistRulesStream()).also {
                        clearLocalBlacklistRules()
                    }
                } else {
                    emptyList()
                }
                saveSingleBlacklistRules(rules)
                dataSource.getSingleBlacklistRulesStream() ?: throw IllegalStateException(
                    "DnsRulesRepository getSingleBlacklistRules"
                )
            }
        )

    override fun saveSingleBlacklistRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>) {
        dataSource.saveSingleBlacklistRules(mapSingleRulesToLines(rules))
    }

    override suspend fun getRemoteBlacklistRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata {
        val url = getHeaderFromFile(dataSource.getRemoteBlacklistRulesStream())
            ?: REMOTE_RULES_DEFAULT_HEADER
        return DnsRulesMetadata.RemoteDnsRulesMetadata(
            name = getNameFromUrl(url),
            url = url,
            date = Date(dataSource.getRemoteBlacklistRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getRemoteBlacklistRulesStream()),
            size = dataSource.getRemoteBlacklistRulesFileSize()
        )
    }

    override fun clearRemoteBlacklistRules() {
        dataSource.clearRemoteBlacklistRules()
    }

    override fun clearLocalBlacklistRules() {
        dataSource.clearLocalBlacklistRules()
    }

    override suspend fun getLocalBlacklistRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata =
        DnsRulesMetadata.LocalDnsRulesMetadata(
            name = getHeaderFromFile(dataSource.getLocalBlacklistRulesStream())
                ?: LOCAL_RULES_DEFAULT_HEADER,
            date = Date(dataSource.getLocalBlacklistRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getLocalBlacklistRulesStream()),
            size = dataSource.getLocalBlacklistRulesFileSize()
        )

    override suspend fun getMixedWhitelistRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata =
        DnsRulesMetadata.MixedDnsRulesMetadata(
            getRulesCountFromFile(dataSource.getWhitelistRulesStream())
        )

    override suspend fun getSingleWhitelistRules(): List<DnsRuleRecycleItem.DnsSingleRule> =
        getSingleRulesFromFile(
            dataSource.getSingleWhitelistRulesStream() ?: run {
                val rules = if (getMixedWhitelistRulesMetadata().count > 0
                    && getLocalWhitelistRulesMetadata().count == 0
                ) {
                    getSingleRulesFromFile(dataSource.getWhitelistRulesStream()).also {
                        clearLocalWhitelistRules()
                    }
                } else {
                    emptyList()
                }
                saveSingleWhitelistRules(rules)
                dataSource.getSingleWhitelistRulesStream() ?: throw IllegalStateException(
                    "DnsRulesRepository getSingleWhitelistRules"
                )
            }
        )

    override fun saveSingleWhitelistRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>) {
        dataSource.saveSingleWhitelistRules(mapSingleRulesToLines(rules))
    }

    override suspend fun getRemoteWhitelistRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata {
        val url = getHeaderFromFile(dataSource.getRemoteWhitelistRulesStream())
            ?: REMOTE_RULES_DEFAULT_HEADER
        return DnsRulesMetadata.RemoteDnsRulesMetadata(
            name = getNameFromUrl(url),
            url = url,
            date = Date(dataSource.getRemoteWhitelistRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getRemoteWhitelistRulesStream()),
            size = dataSource.getRemoteWhitelistRulesFileSize()
        )
    }

    override fun clearRemoteWhitelistRules() {
        dataSource.clearRemoteWhitelistRules()
    }

    override fun clearLocalWhitelistRules() {
        dataSource.clearLocalWhitelistRules()
    }

    override suspend fun getLocalWhitelistRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata =
        DnsRulesMetadata.LocalDnsRulesMetadata(
            name = getHeaderFromFile(dataSource.getLocalWhitelistRulesStream())
                ?: LOCAL_RULES_DEFAULT_HEADER,
            date = Date(dataSource.getLocalWhitelistRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getLocalWhitelistRulesStream()),
            size = dataSource.getLocaleWhitelistRulesFileSize()
        )

    override suspend fun getMixedIpBlacklistRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata =
        DnsRulesMetadata.MixedDnsRulesMetadata(
            getRulesCountFromFile(dataSource.getIpBlacklistRulesStream())
        )

    override suspend fun getSingleIpBlacklistRules(): List<DnsRuleRecycleItem.DnsSingleRule> =
        getSingleRulesFromFile(
            dataSource.getSingleIpBlacklistRulesStream() ?: run {
                val rules = if (getMixedIpBlacklistRulesMetadata().count > 0
                    && getLocalIpBlacklistRulesMetadata().count == 0
                ) {
                    getSingleRulesFromFile(dataSource.getIpBlacklistRulesStream()).also {
                        clearLocalIpBlacklistRules()
                    }
                } else {
                    emptyList()
                }
                saveSingleIpBlacklistRules(rules)
                dataSource.getSingleIpBlacklistRulesStream() ?: throw IllegalStateException(
                    "DnsRulesRepository getSingleIpBlacklistRules"
                )
            }
        )

    override fun saveSingleIpBlacklistRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>) {
        dataSource.saveSingleIpBlacklistRules(mapSingleRulesToLines(rules))
    }

    override suspend fun getRemoteIpBlacklistRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata {
        val url = getHeaderFromFile(dataSource.getRemoteIpBlacklistRulesStream())
            ?: REMOTE_RULES_DEFAULT_HEADER
        return DnsRulesMetadata.RemoteDnsRulesMetadata(
            name = getNameFromUrl(url),
            url = url,
            date = Date(dataSource.getRemoteIpBlacklistRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getRemoteIpBlacklistRulesStream()),
            size = dataSource.getRemoteIpBlacklistRulesFileSize()
        )
    }

    override fun clearRemoteIpBlacklistRules() {
        dataSource.clearRemoteIpBlacklistRules()
    }

    override fun clearLocalIpBlacklistRules() {
        dataSource.clearLocalIpBlacklistRules()
    }

    override suspend fun getLocalIpBlacklistRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata =
        DnsRulesMetadata.LocalDnsRulesMetadata(
            name = getHeaderFromFile(dataSource.getLocalIpBlacklistRulesStream())
                ?: LOCAL_RULES_DEFAULT_HEADER,
            date = Date(dataSource.getLocalIpBlacklistRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getLocalIpBlacklistRulesStream()),
            size = dataSource.getLocalIpBlacklistRulesFileSize()
        )

    override suspend fun getMixedForwardingRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata =
        DnsRulesMetadata.MixedDnsRulesMetadata(
            getRulesCountFromFile(dataSource.getForwardingRulesStream())
        )

    override suspend fun getSingleForwardingRules(): List<DnsRuleRecycleItem.DnsSingleRule> =
        getSingleRulesFromFile(
            dataSource.getSingleForwardingRulesStream() ?: run {
                val rules = if (getMixedForwardingRulesMetadata().count > 0
                    && getLocalForwardingRulesMetadata().count == 1
                ) {
                    getSingleRulesFromFile(dataSource.getForwardingRulesStream()).also {
                        clearLocalForwardingRules()
                    }
                } else {
                    listOf(
                        DnsRuleRecycleItem.DnsSingleRule(
                            rule = forwardingDefaultRule,
                            protected = true,
                            active = true
                        )
                    )
                }
                saveSingleForwardingRules(rules)
                dataSource.getSingleForwardingRulesStream() ?: throw IllegalStateException(
                    "DnsRulesRepository getSingleForwardingRules"
                )
            }
        )

    override fun saveSingleForwardingRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>) {
        dataSource.saveSingleForwardingRules(mapSingleRulesToLines(rules))
    }

    override suspend fun getRemoteForwardingRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata {
        val url = getHeaderFromFile(dataSource.getRemoteForwardingRulesStream())
            ?: REMOTE_RULES_DEFAULT_HEADER
        return DnsRulesMetadata.RemoteDnsRulesMetadata(
            name = getNameFromUrl(url),
            url = url,
            date = Date(dataSource.getRemoteForwardingRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getRemoteForwardingRulesStream()),
            size = dataSource.getRemoteForwardingRulesFileSize()
        )
    }

    override fun clearRemoteForwardingRules() {
        dataSource.clearRemoteForwardingRules()
    }

    override fun clearLocalForwardingRules() {
        dataSource.clearLocalForwardingRules()
    }

    override suspend fun getLocalForwardingRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata =
        DnsRulesMetadata.LocalDnsRulesMetadata(
            name = getHeaderFromFile(dataSource.getLocalForwardingRulesStream())
                ?: LOCAL_RULES_DEFAULT_HEADER,
            date = Date(dataSource.getLocalForwardingRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getLocalForwardingRulesStream()),
            size = dataSource.getLocalForwardingRulesFileSize()
        )

    override suspend fun getMixedCloakingRulesMetadata(): DnsRulesMetadata.MixedDnsRulesMetadata =
        DnsRulesMetadata.MixedDnsRulesMetadata(
            getRulesCountFromFile(dataSource.getCloakingRulesStream())
        )

    override suspend fun getSingleCloakingRules(): List<DnsRuleRecycleItem.DnsSingleRule> =
        getSingleRulesFromFile(
            dataSource.getSingleCloakingRulesStream() ?: run {
                val rules = if (getMixedCloakingRulesMetadata().count > 0
                    && getLocalCloakingRulesMetadata().count == 1
                ) {
                    getSingleRulesFromFile(dataSource.getCloakingRulesStream()).also {
                        clearLocalCloakingRules()
                    }
                } else {
                    listOf(
                        DnsRuleRecycleItem.DnsSingleRule(
                            rule = cloakingDefaultRule,
                            protected = true,
                            active = true
                        )
                    )
                }
                saveSingleCloakingRules(rules)
                dataSource.getSingleCloakingRulesStream() ?: throw IllegalStateException(
                    "DnsRulesRepository getSingleCloakingRules"
                )
            }
        )

    override fun saveSingleCloakingRules(rules: List<DnsRuleRecycleItem.DnsSingleRule>) {
        dataSource.saveSingleCloakingRules(mapSingleRulesToLines(rules))
    }

    override suspend fun getRemoteCloakingRulesMetadata(): DnsRulesMetadata.RemoteDnsRulesMetadata {
        val url = getHeaderFromFile(dataSource.getRemoteCloakingRulesStream())
            ?: REMOTE_RULES_DEFAULT_HEADER
        return DnsRulesMetadata.RemoteDnsRulesMetadata(
            name = getNameFromUrl(url),
            url = url,
            date = Date(dataSource.getRemoteCloakingRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getRemoteCloakingRulesStream()),
            size = dataSource.getRemoteCloakingRulesFileSize()
        )
    }

    override fun clearRemoteCloakingRules() {
        dataSource.clearRemoteCloakingRules()
    }

    override fun clearLocalCloakingRules() {
        dataSource.clearLocalCloakingRules()
    }

    override suspend fun getLocalCloakingRulesMetadata(): DnsRulesMetadata.LocalDnsRulesMetadata =
        DnsRulesMetadata.LocalDnsRulesMetadata(
            name = getHeaderFromFile(dataSource.getLocalCloakingRulesStream())
                ?: LOCAL_RULES_DEFAULT_HEADER,
            date = Date(dataSource.getLocalCloakingRulesFileDate()),
            count = getRulesCountFromFile(dataSource.getLocalCloakingRulesStream()),
            size = dataSource.getLocalCloakingRulesFileSize()
        )

    private fun mapLineToSingleRule(line: String): DnsRuleRecycleItem.DnsSingleRule {
        val active = !line.startsWith("#")
        val protected = if (active) {
            line.startsWith(forwardingDefaultRule.substringBefore(" "))
                    || line.startsWith(cloakingDefaultRule.substringBefore(" "))
        } else {
            line.startsWith("#${forwardingDefaultRule.substringBefore(" ")}")
                    || line.startsWith("#${cloakingDefaultRule.substringBefore(" ")}")
        }
        return DnsRuleRecycleItem.DnsSingleRule(
            line.removePrefix("#"),
            protected,
            active
        )
    }

    private fun mapSingleRulesToLines(rules: List<DnsRuleRecycleItem.DnsSingleRule>): List<String> {
        val lines = mutableListOf<String>()
        for (rule in rules) {

            if (rule.rule.isEmpty()) {
                continue
            }

            val line = if (rule.active) {
                rule.rule
            } else {
                "#${rule.rule}"
            }
            lines.add(line)
        }
        return lines
    }

    private suspend fun getHeaderFromFile(inputReader: InputStreamReader): String? {
        val namePattern = Pattern.compile("^# ([^#]+) #$")
        BufferedReader(inputReader).use { reader ->
            var line = reader.readLine()
            while (line != null && coroutineContext.isActive) {
                if (line.startsWith("#")) {
                    val matcher = namePattern.matcher(line)
                    if (matcher.matches()) {
                        matcher.group(1)?.let {
                            return it
                        }
                    }
                } else if (line.isNotEmpty()) {
                    return null
                }
                line = reader.readLine()
            }
        }
        return null
    }

    private fun getNameFromUrl(url: String): String =
        url.removePrefix("http://")
            .removePrefix("https://")
            .replaceAfter("/", "")
            .removeSuffix("/")

    private suspend fun getRulesCountFromFile(inputReader: InputStreamReader): Int {
        var count = 0
        BufferedReader(inputReader).use { reader ->
            var line = reader.readLine()
            while (line != null && coroutineContext.isActive) {
                if (line.isNotEmpty() && !line.startsWith("#")) {
                    count++
                }
                line = reader.readLine()
            }
        }
        return count
    }

    private suspend fun getSingleRulesFromFile(
        inputReader: InputStreamReader
    ): List<DnsRuleRecycleItem.DnsSingleRule> {
        val rules = mutableListOf<DnsRuleRecycleItem.DnsSingleRule>()
        BufferedReader(inputReader).use { reader ->
            var line = reader.readLine()
            while (line != null && coroutineContext.isActive) {
                if (line.isNotEmpty()
                    && !line.startsWith("##")
                    && !(line.startsWith("#") && line.endsWith("#"))
                ) {
                    rules += mapLineToSingleRule(line)
                }
                line = reader.readLine()
            }
        }
        return rules
    }
}
