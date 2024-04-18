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

package pan.alexander.tordnscrypt.domain.connection_records

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionData
import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionLogEntry
import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionProtocol.TCP
import pan.alexander.tordnscrypt.domain.connection_records.entities.DnsLogEntry
import pan.alexander.tordnscrypt.domain.connection_records.entities.DnsRecord
import pan.alexander.tordnscrypt.domain.connection_records.entities.PacketLogEntry
import pan.alexander.tordnscrypt.domain.connection_records.entities.PacketRecord
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractor
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.iptables.IptablesFirewall
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_CONNECTIVITY_CHECK
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_KERNEL
import pan.alexander.tordnscrypt.utils.Constants.HOST_NAME_REGEX
import pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker
import pan.alexander.tordnscrypt.utils.connectivitycheck.ConnectivityCheckManager
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.utils.root.RootCommandsMark.IPTABLES_MARK
import pan.alexander.tordnscrypt.utils.root.RootExecService.COMMAND_RESULT
import pan.alexander.tordnscrypt.vpn.VpnUtils.isIpInLanRange
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN.LINES_IN_DNS_QUERY_RAW_RECORDS
import pan.alexander.tordnscrypt.vpn.service.VpnBuilder
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentSkipListSet
import javax.inject.Inject

private const val REVERSE_LOOKUP_QUEUE_CAPACITY = 32
private const val IP_TO_HOST_ADDRESS_MAP_SIZE = LINES_IN_DNS_QUERY_RAW_RECORDS
private const val DNS_REVERSE_LOOKUP_SUFFIX = ".in-addr.arpa"

class ConnectionRecordsConverter @Inject constructor(
    private val context: Context,
    private val preferenceRepository: PreferenceRepository,
    private val dnsInteractor: dagger.Lazy<DnsInteractor>,
    private val executor: CoroutineExecutor,
    private val connectivityCheckManager: ConnectivityCheckManager,
    private val iptablesFirewall: dagger.Lazy<IptablesFirewall>
) {

    private val modulesStatus = ModulesStatus.getInstance()

    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    @Volatile
    private var blockIPv6: Boolean = isBlockIPv6()

    @Volatile
    private var meteredNetwork = isMeteredNetwork()

    @Volatile
    private var vpnDNS = VpnBuilder.vpnDnsSet

    @Volatile
    private var compatibilityMode = isCompatibilityMode()

    @Volatile
    private var allThroughTor = isRouteAllThroughTor()

    private val logRecords = ArrayList<ConnectionLogEntry>()
    private val reverseLookupQueue = ArrayBlockingQueue<String>(REVERSE_LOOKUP_QUEUE_CAPACITY, true)
    private val ipToHostAddressMap = mutableMapOf<IpToTime, String>()
    private var task: Job? = null

    private var firewallEnabled = isFirewallEnabled()

    private val hostRegex by lazy { Regex(HOST_NAME_REGEX) }

    private val appsAllowed by lazy { ConcurrentSkipListSet<Int>() }

    private val appsLanAllowed by lazy { ConcurrentSkipListSet<Int>() }

    private val appsSpecialAllowed by lazy { ConcurrentSkipListSet<Int>() }

    private val appsThroughTor by lazy { ConcurrentSkipListSet<Int>() }

    private val appsBypassTor by lazy { ConcurrentSkipListSet<Int>() }

    @Volatile
    private var receiverRegistered = false

    init {
        if (isRootMode()) {
            executor.submit("ConnectionRecordsConverter init") {
                try {
                    updateVars()
                } catch (e: Exception) {
                    loge("ConnectionRecordsConverter init", e, true)
                }
            }
        }
    }

    fun convertRecords(rawRecords: List<ConnectionData>): List<ConnectionLogEntry> {

        registerIptablesReceiver()

        logRecords.clear()

        startReverseLookupQueue()

        rawRecords.forEach { addRecord(it) }

        return logRecords
    }

    private fun addRecord(rawRecord: ConnectionData) {
        when (rawRecord) {
            is DnsRecord -> addDnsRecord(rawRecord)
            is PacketRecord -> addPacketRecord(rawRecord)
        }
    }

    private fun addDnsRecord(processedDnsRecord: DnsRecord) {

        if (!processedDnsRecord.aName.matches(hostRegex)
            && !processedDnsRecord.qName.matches(hostRegex)
            || isRootMode() && isPointerRecord(processedDnsRecord)
        ) {
            return
        }

        if (logRecords.isEmpty()) {
            logRecords.add(getDnsLogEntry(processedDnsRecord))
        } else {

            var consumed = false

            for (i in logRecords.size - 1 downTo 0) {

                val logEntry = if (logRecords[i] is DnsLogEntry) {
                    logRecords[i]
                } else {
                    continue
                } as DnsLogEntry

                val dnsRecordHost = processedDnsRecord.aName.ifEmpty { processedDnsRecord.qName }
                val dnsRecordBlocked = isDnsBlocked(processedDnsRecord)
                val dnsRecordBlockedByIPv6 = if (dnsRecordBlocked) {
                    isDnsBlockedByIPv6(processedDnsRecord)
                } else {
                    false
                }

                if (logEntry.blocked == dnsRecordBlocked
                    && logEntry.blockedByIpv6 == dnsRecordBlockedByIPv6
                    && logEntry.domainsChain.indexOf(dnsRecordHost) >= 0
                ) {
                    if (processedDnsRecord.ip.isNotEmpty()) {
                        logEntry.ips.add(processedDnsRecord.ip)
                    }
                    if (processedDnsRecord.cName.isNotEmpty()
                        && !logEntry.domainsChain.contains(processedDnsRecord.cName)
                    ) {
                        logEntry.domainsChain.add(
                            logEntry.domainsChain.indexOf(dnsRecordHost) + 1,
                            processedDnsRecord.cName
                        )
                    }
                    consumed = true
                } else if (logEntry.blocked == dnsRecordBlocked
                    && logEntry.blockedByIpv6 == dnsRecordBlockedByIPv6
                    && processedDnsRecord.cName.isNotEmpty()
                    && logEntry.domainsChain.indexOf(processedDnsRecord.cName) >= 0
                ) {
                    if (!logEntry.domainsChain.contains(dnsRecordHost)) {
                        logEntry.domainsChain.add(
                            logEntry.domainsChain.indexOf(processedDnsRecord.cName),
                            dnsRecordHost
                        )
                    }
                    consumed = true
                }

                //Hide blocked DNSs that later resolved successfully
                if (logEntry.blocked && !logEntry.blockedByIpv6
                    && logEntry.domainsChain.contains(dnsRecordHost)
                    && !dnsRecordBlocked
                ) {
                    logEntry.visible = false
                }
            }

            if (!consumed) {
                //Do not add blocked DNSs that were previously resolved successfully
                val freshEntry = getDnsLogEntry(processedDnsRecord)
                if (freshEntry.blocked && !freshEntry.blockedByIpv6) {
                    var found = false
                    for (i in logRecords.size - 1 downTo 0) {
                        val logEntry = if (logRecords[i] is DnsLogEntry) {
                            logRecords[i]
                        } else {
                            continue
                        } as DnsLogEntry
                        if (!logEntry.blocked
                            && logEntry.domainsChain.containsAll(freshEntry.domainsChain)
                        ) {
                            found = true
                            break
                        }
                    }
                    if (!found) {
                        logRecords.add(freshEntry)
                    }
                } else {
                    logRecords.add(freshEntry)
                }
            }
        }
    }

    private fun getDnsLogEntry(dnsRecord: DnsRecord): DnsLogEntry {
        val domains = mutableListOf<String>()

        if (dnsRecord.aName.isNotEmpty()) {
            domains.add(dnsRecord.aName)
        }

        if (dnsRecord.qName.isNotEmpty() && !domains.contains(dnsRecord.qName)) {
            domains.add(0, dnsRecord.qName)
        }

        if (dnsRecord.cName.isNotEmpty() && !domains.contains(dnsRecord.cName)) {
            domains.add(dnsRecord.cName)
        }

        val ips = if (dnsRecord.ip.isNotEmpty()) {
            mutableSetOf(dnsRecord.ip)
        } else {
            mutableSetOf()
        }

        val entry = DnsLogEntry(domains, ips).also {
            it.time = dnsRecord.time
        }

        addDnsRecordBlocked(dnsRecord, entry)

        return entry
    }

    private fun addDnsRecordBlocked(dnsRecord: DnsRecord, logEntry: DnsLogEntry) {
        if (isDnsBlocked(dnsRecord)) {
            logEntry.blockedByIpv6 = isDnsBlockedByIPv6(dnsRecord)
            logEntry.blocked = true
        }
    }

    private fun isDnsBlocked(dnsRecord: DnsRecord) =
        dnsRecord.ip == META_ADDRESS
                || dnsRecord.ip == LOOPBACK_ADDRESS
                || dnsRecord.ip == "::"
                || dnsRecord.ip.contains(":") && blockIPv6
                || dnsRecord.hInfo.contains("dnscrypt")
                || dnsRecord.rCode != 0
                || dnsRecord.ip.isEmpty() && dnsRecord.cName.isEmpty() && !isPointerRecord(dnsRecord)

    private fun isDnsBlockedByIPv6(dnsRecord: DnsRecord) =
        dnsRecord.hInfo.contains(DNSCRYPT_BLOCK_IPv6)
                || dnsRecord.ip == "::"
                || dnsRecord.ip.contains(":") && blockIPv6

    private fun addPacketRecord(packetRecord: PacketRecord) {

        val packetLogEntry = PacketLogEntry(
            uid = packetRecord.uid,
            saddr = packetRecord.saddr,
            daddr = packetRecord.daddr,
            protocol = packetRecord.protocol
        ).also {
            it.time = packetRecord.time
        }

        packetLogEntry.blocked = isUidBlocked(packetRecord)

        var consumed = false

        for (i in logRecords.size - 1 downTo 0) {
            val logEntry = if (logRecords[i] is DnsLogEntry) {
                logRecords[i]
            } else {
                continue
            } as DnsLogEntry

            if (!logEntry.blocked && logEntry.ips.contains(packetRecord.daddr)) {
                if (packetLogEntry.dnsLogEntry == null
                    || (packetLogEntry.dnsLogEntry?.domainsChain?.size == logEntry.domainsChain.size
                            && packetLogEntry.dnsLogEntry?.domainsChain?.containsAll(logEntry.domainsChain) == true)
                ) {
                    logEntry.visible = false
                }
                if (packetLogEntry.dnsLogEntry == null) {
                    packetLogEntry.dnsLogEntry = logEntry
                }
                consumed = true
            }
        }

        if (consumed) {
            logRecords.add(packetLogEntry)
        } else if (vpnDNS == null || !vpnDNS.contains(packetRecord.daddr) || !packetRecord.allowed) {
            if (!meteredNetwork && packetRecord.daddr.isNotEmpty()) {
                val host = ipToHostAddressMap[IpToTime(packetRecord.daddr)]
                if (host == null) {
                    makeReverseLookup(packetRecord.daddr)
                } else if (host != packetRecord.daddr) {
                    packetLogEntry.reverseDns = host
                }
            }
            logRecords.add(packetLogEntry)
        }
    }

    private fun isUidBlocked(packetRecord: PacketRecord) =
        if (isRootMode() || isFixTTL()) {
            if (firewallEnabled) {
                if (compatibilityMode && packetRecord.uid == SPECIAL_UID_KERNEL
                    || isFixTTL() && packetRecord.uid == SPECIAL_UID_KERNEL
                    || packetRecord.uid == SPECIAL_UID_KERNEL
                    && appsSpecialAllowed.contains(SPECIAL_UID_KERNEL)
                    || appsSpecialAllowed.contains(SPECIAL_UID_CONNECTIVITY_CHECK)
                    && connectivityCheckManager.getConnectivityCheckIps()
                        .contains(packetRecord.daddr)
                ) {
                    false
                } else if (isIpInLanRange(packetRecord.daddr)) {
                    !appsLanAllowed.contains(packetRecord.uid)
                } else {
                    if (isTorRunning() && packetRecord.protocol != TCP) {
                        if (appsAllowed.contains(packetRecord.uid)) {
                            if (allThroughTor) {
                                !appsBypassTor.contains(packetRecord.uid)
                            } else {
                                !appsThroughTor.contains(packetRecord.uid)
                            }
                        } else {
                            true
                        }
                    } else {
                        !appsAllowed.contains(packetRecord.uid)
                    }
                }
            } else {
                false
            }
        } else if (firewallEnabled) {
            !packetRecord.allowed
        } else {
            false
        }

    private fun isRootMode() =
        modulesStatus.mode == OperationMode.ROOT_MODE

    private fun isPointerRecord(dnsQueryRawRecord: DnsRecord) =
        dnsQueryRawRecord.aName.endsWith(DNS_REVERSE_LOOKUP_SUFFIX)

    private fun makeReverseLookup(ip: String) {
        if (!reverseLookupQueue.contains(ip)) {
            reverseLookupQueue.offer(ip)
        }
    }

    private fun startReverseLookupQueue() {

        if (task?.isCompleted == false) {
            return
        }

        task = executor.submit("ConnectionRecordsConverter startReverseLookupQueue") {
            try {

                while (!Thread.currentThread().isInterrupted) {
                    val ip = reverseLookupQueue.take()

                    val host = try {
                        dnsInteractor.get().reverseResolve(ip)
                    } catch (e: IOException) {
                        ""
                    }

                    if (ipToHostAddressMap.size >= IP_TO_HOST_ADDRESS_MAP_SIZE) {

                        ipToHostAddressMap.keys.sortedBy { it.time }.let {
                            for (i in 0..it.size / 3) {
                                ipToHostAddressMap.remove(it[i])
                            }
                        }
                    }

                    if (host.isNotEmpty()) {
                        ipToHostAddressMap[IpToTime(ip, System.currentTimeMillis())] = host
                    }
                }

            } catch (ignored: InterruptedException) {
            } catch (e: Exception) {
                loge("DNSQueryLogRecordsConverter reverse lookup exception", e)
            }
        }
    }

    fun onStop() {
        task?.let {
            if (!it.isCompleted) {
                it.cancel()
                task = null
            }
        }

        unregisterIptablesReceiver()
    }

    private fun isFirewallEnabled() = preferenceRepository.getBoolPreference(FIREWALL_ENABLED)

    private fun isFixTTL() =
        modulesStatus.isFixTTL && modulesStatus.mode == OperationMode.ROOT_MODE
                && !modulesStatus.isUseModulesWithRoot

    private fun isCompatibilityMode() = (sharedPreferences.getBoolean(COMPATIBILITY_MODE, false)
            || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            && modulesStatus.mode == OperationMode.VPN_MODE

    private fun isBlockIPv6() = sharedPreferences.getBoolean(DNSCRYPT_BLOCK_IPv6, false)

    private fun isMeteredNetwork() = NetworkChecker.isMeteredNetwork(context)

    private fun isRouteAllThroughTor() = sharedPreferences.getBoolean(ALL_THROUGH_TOR, true)

    private fun isTorRunning() = modulesStatus.torState == ModuleState.RUNNING

    private val iptablesReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {

                if (!isRootMode()) {
                    return
                }

                val int = intent ?: return
                val action = int.action ?: return

                if (int.getIntExtra("Mark", 0) == IPTABLES_MARK
                    && action == COMMAND_RESULT
                ) {
                    val result = goAsync()

                    executor.submit("ConnectionRecordsConverter iptablesReceiver") {
                        try {
                            updateVars()
                        } catch (e: Exception) {
                            loge("ConnectionRecordsConverter iptablesReceiver", e, true)
                        } finally {
                            result.finish()
                        }
                    }
                }
            }
        }
    }

    private fun registerIptablesReceiver() {
        if (!receiverRegistered) {
            LocalBroadcastManager.getInstance(context)
                .registerReceiver(iptablesReceiver, IntentFilter(COMMAND_RESULT))
            receiverRegistered = true
        }
    }

    private fun updateVars() {

        iptablesFirewall.get().prepareUidAllowed()
        blockIPv6 = isBlockIPv6()
        meteredNetwork = isMeteredNetwork()
        vpnDNS = VpnBuilder.vpnDnsSet
        firewallEnabled = isFirewallEnabled()
        compatibilityMode = isCompatibilityMode()

        val networkAvailable = NetworkChecker.isNetworkAvailable(context)
        appsAllowed.apply {
            clear()
            if (networkAvailable) {
                addAll(iptablesFirewall.get().uidAllowed)
            }
        }
        appsLanAllowed.apply {
            clear()
            if (networkAvailable) {
                addAll(iptablesFirewall.get().uidLanAllowed)
            }
        }
        appsSpecialAllowed.apply {
            clear()
            if (networkAvailable) {
                addAll(iptablesFirewall.get().uidSpecialAllowed)
            }
        }

        allThroughTor = isRouteAllThroughTor()
        appsThroughTor.apply {
            clear()
            addAll(preferenceRepository.getStringSetPreference(UNLOCK_APPS).map { it.toInt() })
        }
        appsBypassTor.apply {
            clear()
            addAll(preferenceRepository.getStringSetPreference(CLEARNET_APPS).map { it.toInt() })
        }
    }

    private fun unregisterIptablesReceiver() = try {
        if (receiverRegistered) {
            LocalBroadcastManager.getInstance(context)
                .unregisterReceiver(iptablesReceiver)
        } else {
            Unit
        }
    } catch (ignored: Exception) {
    } finally {
        receiverRegistered = false
    }

    private class IpToTime(
        val ip: String,
        val time: Long = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as IpToTime

            if (ip != other.ip) return false

            return true
        }

        override fun hashCode(): Int {
            return ip.hashCode()
        }
    }
}
