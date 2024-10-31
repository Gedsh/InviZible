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

package pan.alexander.tordnscrypt.settings.dnscrypt_rules.local

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

class UpdateLocalRulesWorkManager @Inject constructor(
    private val context: Context,
) {

    fun startImportDnsRules(ruleType: DnsRuleType, filesToImport: Array<*>) {

        filesToImport.firstOrNull() ?: return

        val constraints = Constraints.Builder()
            .setRequiresStorageNotLow(true)
            .build()

        val files = if (filesToImport.first() is String) {
            LOCAL_RULES_PATH_ARG to filesToImport.map { it.toString() }.toTypedArray()
        } else {
            LOCAL_RULES_URI_ARG to filesToImport.map { it.toString() }.toTypedArray()
        }

        val importRequest = OneTimeWorkRequestBuilder<UpdateLocalDnsRulesWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setInputData(
                workDataOf(
                    LOCAL_RULES_TYPE_ARG to ruleType.name,
                    files
                )
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                getWorkName(ruleType),
                ExistingWorkPolicy.REPLACE,
                importRequest
            )
    }

    fun stopImportDnsRules(type: DnsRuleType) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(getWorkName(type))
    }

    private fun getWorkName(type: DnsRuleType) =
        when (type) {
            DnsRuleType.BLACKLIST -> REFRESH_LOCAL_DNS_BLACKLIST_WORK
            DnsRuleType.WHITELIST -> REFRESH_LOCAL_DNS_WHITELIST_WORK
            DnsRuleType.IP_BLACKLIST -> REFRESH_LOCAL_DNS_IP_BLACKLIST_WORK
            DnsRuleType.FORWARDING -> REFRESH_LOCAL_DNS_FORWARDING_WORK
            DnsRuleType.CLOAKING -> REFRESH_LOCAL_DNS_CLOAKING_WORK
        }

    companion object {
        const val LOCAL_RULES_TYPE_ARG = "pan.alexander.tordnscrypt.LOCAL_RULES_TYPE_ARG"
        const val LOCAL_RULES_PATH_ARG = "pan.alexander.tordnscrypt.LOCAL_RULES_PATH_ARG"
        const val LOCAL_RULES_URI_ARG = "pan.alexander.tordnscrypt.LOCAL_RULES_URI_ARG"

        const val REFRESH_LOCAL_DNS_BLACKLIST_WORK =
            "pan.alexander.tordnscrypt.REFRESH_LOCAL_DNS_BLACKLIST_WORK"
        const val REFRESH_LOCAL_DNS_WHITELIST_WORK =
            "pan.alexander.tordnscrypt.REFRESH_LOCAL_DNS_WHITELIST_WORK"
        const val REFRESH_LOCAL_DNS_IP_BLACKLIST_WORK =
            "pan.alexander.tordnscrypt.REFRESH_LOCAL_DNS_IP_BLACKLIST_WORK"
        const val REFRESH_LOCAL_DNS_FORWARDING_WORK =
            "pan.alexander.tordnscrypt.REFRESH_LOCAL_DNS_FORWARDING_WORK"
        const val REFRESH_LOCAL_DNS_CLOAKING_WORK =
            "pan.alexander.tordnscrypt.REFRESH_LOCAL_DNS_CLOAKING_WORK"
    }
}
