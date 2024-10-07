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

package pan.alexander.tordnscrypt.settings.show_rules.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest.Companion.DEFAULT_BACKOFF_DELAY_MILLIS
import androidx.work.workDataOf
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_REFRESH_DELAY
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_BLACKLIST_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_CLOAKING_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_FORWARDING_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_IP_BLACKLIST_URL
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_WHITELIST_URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named

private const val DEFAULT_DELAY_HOURS = 72

class UpdateRemoteRulesWorkManager @Inject constructor(
    private val context: Context,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: SharedPreferences,
    private val preferences: PreferenceRepository
) {

    fun startRefreshDnsRules(ruleName: String, ruleType: DnsRuleType) {

        val interval = getInterval()
        if (interval == 0L) {
            return
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<UpdateRemoteDnsRulesWorker>(interval, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                DEFAULT_BACKOFF_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setInputData(workDataOf(
                REMOTE_RULES_TYPE_ARG to ruleType.name,
                REMOTE_RULES_NAME_ARG to ruleName,
                REMOTE_RULES_URL_ARG to getRuleUrl(ruleType)
            ))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                getWorkName(ruleType),
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                updateRequest
            )
    }

    fun stopRefreshDnsRules(type: DnsRuleType) {

        val interval = getInterval()
        if (interval == 0L) {
            return
        }

        WorkManager.getInstance(context)
            .cancelUniqueWork(getWorkName(type))
    }

    private fun getInterval(): Long = try {
        val refreshPeriod = defaultPreferences.getString(
            DNSCRYPT_REFRESH_DELAY,
            DEFAULT_DELAY_HOURS.toString()
        )
        refreshPeriod?.toLong() ?: DEFAULT_DELAY_HOURS.toLong()
    } catch (e: Exception) {
        loge("UpdateDnsRulesManager getInterval", e)
        DEFAULT_DELAY_HOURS.toLong()
    }

    private fun getWorkName(type: DnsRuleType) =
        when (type) {
            DnsRuleType.BLACKLIST -> REFRESH_REMOTE_DNS_BLACKLIST_WORK
            DnsRuleType.WHITELIST -> REFRESH_REMOTE_DNS_WHITELIST_WORK
            DnsRuleType.IP_BLACKLIST -> REFRESH_REMOTE_DNS_IP_BLACKLIST_WORK
            DnsRuleType.FORWARDING -> REFRESH_REMOTE_DNS_FORWARDING_WORK
            DnsRuleType.CLOAKING -> REFRESH_REMOTE_DNS_CLOAKING_WORK
        }

    private fun getRuleUrl(type: DnsRuleType) =
        when (type) {
            DnsRuleType.BLACKLIST -> preferences.getStringPreference(REMOTE_BLACKLIST_URL)
            DnsRuleType.WHITELIST -> preferences.getStringPreference(REMOTE_WHITELIST_URL)
            DnsRuleType.IP_BLACKLIST -> preferences.getStringPreference(REMOTE_IP_BLACKLIST_URL)
            DnsRuleType.FORWARDING -> preferences.getStringPreference(REMOTE_FORWARDING_URL)
            DnsRuleType.CLOAKING -> preferences.getStringPreference(REMOTE_CLOAKING_URL)
        }

    companion object {
        const val REMOTE_RULES_URL_ARG = "pan.alexander.tordnscrypt.REMOTE_RULES_URL_ARG"
        const val REMOTE_RULES_NAME_ARG = "pan.alexander.tordnscrypt.REMOTE_RULES_NAME_ARG"
        const val REMOTE_RULES_TYPE_ARG = "pan.alexander.tordnscrypt.REMOTE_RULES_TYPE_ARG"

        const val REFRESH_REMOTE_DNS_BLACKLIST_WORK = "pan.alexander.tordnscrypt.REFRESH_REMOTE_DNS_BLACKLIST_WORK"
        const val REFRESH_REMOTE_DNS_WHITELIST_WORK = "pan.alexander.tordnscrypt.REFRESH_REMOTE_DNS_WHITELIST_WORK"
        const val REFRESH_REMOTE_DNS_IP_BLACKLIST_WORK = "pan.alexander.tordnscrypt.REFRESH_REMOTE_DNS_IP_BLACKLIST_WORK"
        const val REFRESH_REMOTE_DNS_FORWARDING_WORK = "pan.alexander.tordnscrypt.REFRESH_REMOTE_DNS_FORWARDING_WORK"
        const val REFRESH_REMOTE_DNS_CLOAKING_WORK = "pan.alexander.tordnscrypt.REFRESH_REMOTE_DNS_CLOAKING_WORK"
    }
}
