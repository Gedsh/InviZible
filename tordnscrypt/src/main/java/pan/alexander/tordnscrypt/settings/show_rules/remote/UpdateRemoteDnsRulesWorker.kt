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
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import pan.alexander.tordnscrypt.settings.show_rules.local.ImportRulesManager
import pan.alexander.tordnscrypt.settings.show_rules.local.UpdateLocalRulesWorkManager.Companion.REFRESH_LOCAL_DNS_BLACKLIST_WORK
import pan.alexander.tordnscrypt.settings.show_rules.local.UpdateLocalRulesWorkManager.Companion.REFRESH_LOCAL_DNS_CLOAKING_WORK
import pan.alexander.tordnscrypt.settings.show_rules.local.UpdateLocalRulesWorkManager.Companion.REFRESH_LOCAL_DNS_FORWARDING_WORK
import pan.alexander.tordnscrypt.settings.show_rules.local.UpdateLocalRulesWorkManager.Companion.REFRESH_LOCAL_DNS_IP_BLACKLIST_WORK
import pan.alexander.tordnscrypt.settings.show_rules.local.UpdateLocalRulesWorkManager.Companion.REFRESH_LOCAL_DNS_WHITELIST_WORK
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.settings.show_rules.remote.UpdateRemoteRulesWorkManager.Companion.REMOTE_RULES_NAME_ARG
import pan.alexander.tordnscrypt.settings.show_rules.remote.UpdateRemoteRulesWorkManager.Companion.REMOTE_RULES_TYPE_ARG
import pan.alexander.tordnscrypt.settings.show_rules.remote.UpdateRemoteRulesWorkManager.Companion.REMOTE_RULES_URL_ARG
import pan.alexander.tordnscrypt.settings.show_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_BLACKLIST_WORK
import pan.alexander.tordnscrypt.settings.show_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_CLOAKING_WORK
import pan.alexander.tordnscrypt.settings.show_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_FORWARDING_WORK
import pan.alexander.tordnscrypt.settings.show_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_IP_BLACKLIST_WORK
import pan.alexander.tordnscrypt.settings.show_rules.existing.RemixExistingRulesWorkManager.Companion.MIX_DNS_WHITELIST_WORK
import java.io.File
import javax.inject.Inject
import javax.inject.Named

class UpdateRemoteDnsRulesWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    init {
        App.instance.daggerComponent.inject(this)
    }

    @Inject
    @Named(CoroutinesModule.DISPATCHER_IO)
    lateinit var dispatcherIo: CoroutineDispatcher

    @Inject
    lateinit var downloadRulesManager: DownloadRemoteRulesManager

    override suspend fun doWork(): Result {
        try {
            val url = inputData.getString(REMOTE_RULES_URL_ARG) ?: return Result.failure()
            val ruleType = inputData.getString(REMOTE_RULES_TYPE_ARG)?.let {
                DnsRuleType.valueOf(it)
            } ?: return Result.failure()
            val ruleName = inputData.getString(REMOTE_RULES_NAME_ARG) ?: return Result.failure()
            val fileName = when (ruleType) {
                DnsRuleType.BLACKLIST -> "blacklist-remote.txt"
                DnsRuleType.WHITELIST -> "whitelist-remote.txt"
                DnsRuleType.IP_BLACKLIST -> "ip-blacklist-remote.txt"
                DnsRuleType.FORWARDING -> "forwarding-rules-remote.txt"
                DnsRuleType.CLOAKING -> "cloaking-rules-remote.txt"
            }
            val file = downloadRulesManager.downloadRules(ruleName, url, fileName)

            while (isLocalDnsRulesImportingInProgress(ruleType)
                || isMixDnsRulesInProgress(ruleType)
            ) {
                delay(500)
            }

            file?.let {
                if (it.length() > 0) {
                    importRulesFromFile(it, ruleType, url, ruleName)
                } else {
                    return Result.retry()
                }
            } ?: return Result.failure()
            return Result.success()
        } catch (e: Exception) {
            loge("UpdateRemoteDnsRulesWorker doWork", e)
        }
        return Result.failure()
    }

    private suspend fun importRulesFromFile(
        file: File,
        ruleType: DnsRuleType,
        url: String,
        ruleName: String
    ) = runInterruptible(dispatcherIo) {
        ImportRulesManager(
            context = appContext,
            rulesVariant = ruleType,
            remoteRulesUrl = url,
            remoteRulesName = ruleName,
            importType = ImportRulesManager.ImportType.REMOTE_RULES,
            filePathToImport = arrayOf(file.path)
        ).run()
    }

    private fun isLocalDnsRulesImportingInProgress(type: DnsRuleType): Boolean =
        WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(getLocalWorkName(type)).get()
            .firstOrNull()?.state == WorkInfo.State.RUNNING


    private fun getLocalWorkName(type: DnsRuleType) =
        when (type) {
            DnsRuleType.BLACKLIST -> REFRESH_LOCAL_DNS_BLACKLIST_WORK
            DnsRuleType.WHITELIST -> REFRESH_LOCAL_DNS_WHITELIST_WORK
            DnsRuleType.IP_BLACKLIST -> REFRESH_LOCAL_DNS_IP_BLACKLIST_WORK
            DnsRuleType.FORWARDING -> REFRESH_LOCAL_DNS_FORWARDING_WORK
            DnsRuleType.CLOAKING -> REFRESH_LOCAL_DNS_CLOAKING_WORK
        }

    private fun isMixDnsRulesInProgress(type: DnsRuleType): Boolean =
        WorkManager.getInstance(appContext)
            .getWorkInfosForUniqueWork(getMixWorkName(type)).get()
            .firstOrNull()?.state == WorkInfo.State.RUNNING

    private fun getMixWorkName(type: DnsRuleType) =
        when (type) {
            DnsRuleType.BLACKLIST -> MIX_DNS_BLACKLIST_WORK
            DnsRuleType.WHITELIST -> MIX_DNS_WHITELIST_WORK
            DnsRuleType.IP_BLACKLIST -> MIX_DNS_IP_BLACKLIST_WORK
            DnsRuleType.FORWARDING -> MIX_DNS_FORWARDING_WORK
            DnsRuleType.CLOAKING -> MIX_DNS_CLOAKING_WORK
        }

}
