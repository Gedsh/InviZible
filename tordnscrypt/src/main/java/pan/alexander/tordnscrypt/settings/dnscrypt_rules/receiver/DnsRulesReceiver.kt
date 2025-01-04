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

package pan.alexander.tordnscrypt.settings.dnscrypt_rules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote.DownloadRemoteRulesManager.Companion.DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_ACTION
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote.DownloadRemoteRulesManager.Companion.DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_DATA
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.Companion.UPDATE_DNS_RULES_PROGRESS_DATA
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.Companion.UPDATE_LOCAL_DNS_RULES_PROGRESS_ACTION
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.Companion.UPDATE_REMOTE_DNS_RULES_PROGRESS_ACTION
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.Companion.UPDATE_TOTAL_DNS_RULES_PROGRESS_ACTION
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.Companion.UPDATE_TOTAL_DNS_RULES_PROGRESS_DATA
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.remote.DnsRulesDownloadProgress
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.DnsRulesUpdateProgress
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.recycler.DnsRuleRecycleItem
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.util.Date
import javax.inject.Inject

class DnsRulesReceiver @Inject constructor(
    context: Context
) : BroadcastReceiver() {

    var callback: Callback? = null

    private var receiverRegistered = false

    private val localBroadcastManager by lazy {
        LocalBroadcastManager.getInstance(context)
    }

    fun registerReceiver() {
        try {
            receiverRegistered = true
            localBroadcastManager.registerReceiver(
                this,
                IntentFilter(DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_ACTION)
            )
            localBroadcastManager.registerReceiver(
                this,
                IntentFilter(UPDATE_REMOTE_DNS_RULES_PROGRESS_ACTION)
            )
            localBroadcastManager.registerReceiver(
                this,
                IntentFilter(UPDATE_LOCAL_DNS_RULES_PROGRESS_ACTION)
            )
            localBroadcastManager.registerReceiver(
                this,
                IntentFilter(UPDATE_TOTAL_DNS_RULES_PROGRESS_ACTION)
            )
        } catch (e: Exception) {
            loge("DnsRulesReceiver registerReceiver", e)
        }
    }

    fun unregisterReceiver() {
        try {
            if (receiverRegistered) {
                receiverRegistered = false
                localBroadcastManager.unregisterReceiver(this)
            }
        } catch (e: Exception) {
            loge("DnsRulesReceiver unregisterReceiver", e)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_ACTION -> downloadRemoteRulesProgress(intent)
            UPDATE_REMOTE_DNS_RULES_PROGRESS_ACTION -> updateRemoteRulesProgress(intent)
            UPDATE_LOCAL_DNS_RULES_PROGRESS_ACTION -> updateLocalRulesProgress(intent)
            UPDATE_TOTAL_DNS_RULES_PROGRESS_ACTION -> updateTotalRulesProgress(intent)
        }
    }

    @Suppress("deprecation")
    private fun downloadRemoteRulesProgress(intent: Intent) {
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_DATA,
                DnsRulesDownloadProgress::class.java
            )
        } else {
            intent.getParcelableExtra(
                DOWNLOAD_REMOTE_DNS_RULES_PROGRESS_DATA
            )
        }
        data ?: return
        when (data) {
            is DnsRulesDownloadProgress.DownloadProgress -> {
                callback?.onUpdateRemoteRules(
                    DnsRuleRecycleItem.DnsRemoteRule(
                        name = data.name,
                        url = data.url,
                        date = Date(),
                        count = 0,
                        size = data.size,
                        inProgress = true
                    )
                )
            }

            is DnsRulesDownloadProgress.DownloadFinished -> {
                callback?.onUpdateRemoteRules(
                    DnsRuleRecycleItem.DnsRemoteRule(
                        name = data.name,
                        url = data.url,
                        date = Date(),
                        count = 0,
                        size = data.size,
                        inProgress = false
                    )
                )
            }

            is DnsRulesDownloadProgress.DownloadFailure -> {
                callback?.onUpdateRemoteRules(
                    DnsRuleRecycleItem.DnsRemoteRule(
                        name = data.name,
                        url = data.error,
                        date = Date(),
                        count = 0,
                        size = 0,
                        inProgress = false,
                        fault = true
                    )
                )
            }
        }
    }

    @Suppress("deprecation")
    private fun updateRemoteRulesProgress(intent: Intent) {
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                UPDATE_DNS_RULES_PROGRESS_DATA,
                DnsRulesUpdateProgress::class.java
            )
        } else {
            intent.getParcelableExtra(
                UPDATE_DNS_RULES_PROGRESS_DATA
            )
        }
        data ?: return
        when (data) {
            is DnsRulesUpdateProgress.UpdateProgress -> {
                callback?.onUpdateRemoteRules(
                    DnsRuleRecycleItem.DnsRemoteRule(
                        name = data.name,
                        url = data.url ?: "",
                        date = Date(),
                        count = data.count,
                        size = data.size,
                        inProgress = true
                    )
                )
            }

            is DnsRulesUpdateProgress.UpdateFinished -> {
                callback?.onUpdateRemoteRules(
                    DnsRuleRecycleItem.DnsRemoteRule(
                        name = data.name,
                        url = data.url ?: "",
                        date = Date(),
                        count = data.count,
                        size = data.size,
                        inProgress = false
                    )
                )
                callback?.onUpdateFinished()
            }

            is DnsRulesUpdateProgress.UpdateFailure -> {
                callback?.onUpdateRemoteRules(
                    DnsRuleRecycleItem.DnsRemoteRule(
                        name = data.name,
                        url = data.url ?: "",
                        date = Date(),
                        count = 0,
                        size = 0,
                        inProgress = false,
                        fault = true
                    )
                )
                callback?.onUpdateFinished()
            }
        }
    }

    @Suppress("deprecation")
    private fun updateLocalRulesProgress(intent: Intent) {
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                UPDATE_DNS_RULES_PROGRESS_DATA,
                DnsRulesUpdateProgress::class.java
            )
        } else {
            intent.getParcelableExtra(
                UPDATE_DNS_RULES_PROGRESS_DATA
            )
        }
        data ?: return
        when (data) {
            is DnsRulesUpdateProgress.UpdateProgress -> {
                callback?.onUpdateLocalRules(
                    DnsRuleRecycleItem.DnsLocalRule(
                        name = data.name,
                        date = Date(),
                        count = data.count,
                        size = data.size,
                        inProgress = true
                    )
                )
            }

            is DnsRulesUpdateProgress.UpdateFinished -> {
                callback?.onUpdateLocalRules(
                    DnsRuleRecycleItem.DnsLocalRule(
                        name = data.name,
                        date = Date(),
                        count = data.count,
                        size = data.size,
                        inProgress = false
                    )
                )
                callback?.onUpdateFinished()
            }

            is DnsRulesUpdateProgress.UpdateFailure -> {
                callback?.onUpdateLocalRules(
                    DnsRuleRecycleItem.DnsLocalRule(
                        name = data.name,
                        date = Date(),
                        count = 0,
                        size = 0,
                        inProgress = false,
                        fault = true
                    )
                )
                callback?.onUpdateFinished()
            }
        }
    }

    private fun updateTotalRulesProgress(intent: Intent) {
        val count = intent.getIntExtra(UPDATE_TOTAL_DNS_RULES_PROGRESS_DATA, 0)
        callback?.onUpdateTotalRules(count)
    }

    interface Callback {
        fun onUpdateRemoteRules(rules: DnsRuleRecycleItem.DnsRemoteRule)
        fun onUpdateLocalRules(rules: DnsRuleRecycleItem.DnsLocalRule)
        fun onUpdateTotalRules(count: Int)
        fun onUpdateFinished()
    }
}
