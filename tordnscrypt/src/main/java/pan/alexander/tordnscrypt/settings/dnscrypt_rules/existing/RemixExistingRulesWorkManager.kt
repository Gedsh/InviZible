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

package pan.alexander.tordnscrypt.settings.dnscrypt_rules.existing

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RemixExistingRulesWorkManager @Inject constructor(
    private val context: Context,
) {

    fun startMix(ruleType: DnsRuleType) {

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val mixRequest = OneTimeWorkRequestBuilder<RemixExistingDnsRulesWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setInputData(
                workDataOf(
                    MIX_RULES_TYPE_ARG to ruleType.name,
                )
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                getWorkName(ruleType),
                ExistingWorkPolicy.REPLACE,
                mixRequest
            )
    }

    fun stopMix(type: DnsRuleType) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(getWorkName(type))
    }

    private fun getWorkName(type: DnsRuleType) =
        when (type) {
            DnsRuleType.BLACKLIST -> MIX_DNS_BLACKLIST_WORK
            DnsRuleType.WHITELIST -> MIX_DNS_WHITELIST_WORK
            DnsRuleType.IP_BLACKLIST -> MIX_DNS_IP_BLACKLIST_WORK
            DnsRuleType.FORWARDING -> MIX_DNS_FORWARDING_WORK
            DnsRuleType.CLOAKING -> MIX_DNS_CLOAKING_WORK
        }

    companion object {
        const val MIX_RULES_TYPE_ARG = "pan.alexander.tordnscrypt.SINGLE_RULES_TYPE_ARG"

        const val MIX_DNS_BLACKLIST_WORK =
            "pan.alexander.tordnscrypt.MIX_DNS_BLACKLIST_WORK"
        const val MIX_DNS_WHITELIST_WORK =
            "pan.alexander.tordnscrypt.MIX_DNS_WHITELIST_WORK"
        const val MIX_DNS_IP_BLACKLIST_WORK =
            "pan.alexander.tordnscrypt.MIX_DNS_IP_BLACKLIST_WORK"
        const val MIX_DNS_FORWARDING_WORK =
            "pan.alexander.tordnscrypt.MIX_DNS_FORWARDING_WORK"
        const val MIX_DNS_CLOAKING_WORK =
            "pan.alexander.tordnscrypt.REFRESH_SINGLE_DNS_CLOAKING_WORK"
    }
}
