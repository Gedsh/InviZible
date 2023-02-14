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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.connection_records

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractor
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_CONNECTIVITY_CHECK
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_KERNEL
import pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker
import pan.alexander.tordnscrypt.utils.connectivitycheck.ConnectivityCheckManager
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.vpn.VpnUtils
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN.LINES_IN_DNS_QUERY_RAW_RECORDS
import pan.alexander.tordnscrypt.vpn.service.VpnBuilder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import javax.inject.Inject

private const val REVERSE_LOOKUP_QUEUE_CAPACITY = 32
private const val IP_TO_HOST_ADDRESS_MAP_SIZE = LINES_IN_DNS_QUERY_RAW_RECORDS
private const val DNS_REVERSE_LOOKUP_SUFFIX = ".in-addr.arpa"

class ConnectionRecordsConverter @Inject constructor(
    context: Context,
    preferenceRepository: PreferenceRepository,
    private val dnsInteractor: dagger.Lazy<DnsInteractor>,
    private val cachedExecutor: CachedExecutor,
    private val connectivityCheckManager: ConnectivityCheckManager
) {

    private val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)
    private val blockIPv6: Boolean = sharedPreferences.getBoolean(BLOCK_IPv6, true)
    private val meteredNetwork = NetworkChecker.isMeteredNetwork(context)
    private val vpnDNS = VpnBuilder.vpnDnsSet
    private val modulesStatus = ModulesStatus.getInstance()
    private val fixTTL = (modulesStatus.isFixTTL && modulesStatus.mode == OperationMode.ROOT_MODE
            && !modulesStatus.isUseModulesWithRoot)
    private var compatibilityMode = (sharedPreferences.getBoolean(COMPATIBILITY_MODE, false)
            || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            && modulesStatus.mode == OperationMode.VPN_MODE

    private val dnsQueryLogRecords = ArrayList<ConnectionRecord>()
    private val dnsQueryLogRecordsSublist = ArrayList<ConnectionRecord>()
    private val reverseLookupQueue = ArrayBlockingQueue<String>(REVERSE_LOOKUP_QUEUE_CAPACITY, true)
    private val ipToHostAddressMap = mutableMapOf<IpToTime, String>()
    private var futureTask: Future<*>? = null

    private val firewallEnabled = preferenceRepository.getBoolPreference(FIREWALL_ENABLED)
    private var appsAllowed = mutableSetOf<Int>()
    private val appsLanAllowed = mutableListOf<Int>()

    init {
        if (firewallEnabled) {
            preferenceRepository.getStringSetPreference(APPS_ALLOW_LAN_PREF)
                .forEach { appsLanAllowed.add(it.toInt()) }

            var tempSet: MutableSet<String>? = null
            if (NetworkChecker.isWifiActive(context) || NetworkChecker.isEthernetActive(context)) {
                tempSet = preferenceRepository.getStringSetPreference(APPS_ALLOW_WIFI_PREF)
            } else if (NetworkChecker.isCellularActive(context)) {
                tempSet = preferenceRepository.getStringSetPreference(APPS_ALLOW_GSM_PREF)
            } else if (NetworkChecker.isRoaming(context)) {
                tempSet = preferenceRepository.getStringSetPreference(APPS_ALLOW_ROAMING)
            }

            tempSet?.forEach { appsAllowed.add(it.toInt()) }
        }
    }

    fun convertRecords(dnsQueryRawRecords: List<ConnectionRecord?>): List<ConnectionRecord> {

        dnsQueryLogRecords.clear()

        startReverseLookupQueue()

        dnsQueryRawRecords.forEach { addRecord(it) }

        return dnsQueryLogRecords
    }

    private fun addRecord(dnsQueryRawRecord: ConnectionRecord?) {

        if (dnsQueryRawRecord == null) {
            return
        }

        if (dnsQueryLogRecords.isNotEmpty()) {
            if (dnsQueryRawRecord.uid != -1000) {
                addUID(dnsQueryRawRecord)
                return
            } else if (isIdenticalRecord(dnsQueryRawRecord)
                || isRootMode() && isPointerRecord(dnsQueryRawRecord)
            ) {
                return
            }
        }

        setQueryBlocked(dnsQueryRawRecord)

        if (dnsQueryRawRecord.blocked) {
            dnsQueryLogRecords.removeAll { it == dnsQueryRawRecord }
        }

        dnsQueryLogRecords.add(dnsQueryRawRecord)
    }

    private fun isIdenticalRecord(dnsQueryRawRecord: ConnectionRecord): Boolean {

        for (i in dnsQueryLogRecords.size - 1 downTo 0) {
            val record = dnsQueryLogRecords[i]

            if (dnsQueryRawRecord.aName == record.aName
                && dnsQueryRawRecord.qName == record.qName
                && dnsQueryRawRecord.hInfo == record.hInfo
                && dnsQueryRawRecord.rCode == record.rCode
                && dnsQueryRawRecord.saddr == record.saddr
            ) {

                if (dnsQueryRawRecord.daddr.isNotEmpty() && record.daddr.isNotEmpty()) {
                    if (!record.daddr.contains(dnsQueryRawRecord.daddr.trim())) {
                        dnsQueryLogRecords[i] =
                            record.apply { daddr = daddr + ", " + dnsQueryRawRecord.daddr.trim() }
                    }
                    return true
                }
            }
        }

        return false
    }

    private fun isRootMode() =
        modulesStatus.mode == OperationMode.ROOT_MODE && !modulesStatus.isFixTTL

    private fun isPointerRecord(dnsQueryRawRecord: ConnectionRecord) =
        dnsQueryRawRecord.aName.endsWith(DNS_REVERSE_LOOKUP_SUFFIX)

    private fun addUID(dnsQueryRawRecord: ConnectionRecord) {
        var savedRecord: ConnectionRecord? = null
        dnsQueryLogRecordsSublist.clear()

        val uidBlocked = if (firewallEnabled) {
            if (compatibilityMode && dnsQueryRawRecord.uid == SPECIAL_UID_KERNEL
                || fixTTL && dnsQueryRawRecord.uid == SPECIAL_UID_KERNEL
                || appsAllowed.contains(SPECIAL_UID_CONNECTIVITY_CHECK)
                && connectivityCheckManager.getConnectivityCheckIps()
                    .contains(dnsQueryRawRecord.daddr)
            ) {
                false
            } else if (isIpInLanRange(dnsQueryRawRecord.daddr)) {
                !appsLanAllowed.contains(dnsQueryRawRecord.uid)
            } else {
                !appsAllowed.contains(dnsQueryRawRecord.uid)
            }
        } else {
            false
        }

        for (index in dnsQueryLogRecords.size - 1 downTo 0) {
            val record = dnsQueryLogRecords[index]
            if (savedRecord == null && record.daddr.contains(dnsQueryRawRecord.daddr) && record.uid == -1000) {
                record.blocked = uidBlocked
                record.unused = false
                dnsQueryLogRecordsSublist.add(record)
                savedRecord = record
            } else if (savedRecord != null && savedRecord.aName == record.cName) {
                record.blocked = uidBlocked
                record.unused = false
                dnsQueryLogRecordsSublist.add(record)
                savedRecord = record
            } else if (savedRecord != null && savedRecord.aName != record.cName) {
                break
            }
        }

        if (savedRecord != null) {

            val dnsQueryNewRecord = ConnectionRecord(
                savedRecord.qName, savedRecord.aName, savedRecord.cName,
                savedRecord.hInfo, -1, dnsQueryRawRecord.saddr, "", dnsQueryRawRecord.uid
            )
            dnsQueryNewRecord.blocked = uidBlocked
            dnsQueryNewRecord.unused = false

            dnsQueryLogRecordsSublist.add(dnsQueryNewRecord)

        } else if (vpnDNS == null || !vpnDNS.contains(dnsQueryRawRecord.daddr)) {

            if (!meteredNetwork && dnsQueryRawRecord.daddr.isNotEmpty()) {
                val host = ipToHostAddressMap[IpToTime(dnsQueryRawRecord.daddr)]

                if (host == null) {
                    makeReverseLookup(dnsQueryRawRecord.daddr)
                } else if (host != dnsQueryRawRecord.daddr) {
                    dnsQueryRawRecord.reverseDNS = host
                }
            }

            dnsQueryRawRecord.blocked = uidBlocked

            dnsQueryRawRecord.unused = false

            dnsQueryLogRecords.removeAll { it == dnsQueryRawRecord }
            dnsQueryLogRecords.add(dnsQueryRawRecord)
        }

        if (dnsQueryLogRecordsSublist.isNotEmpty()) {
            dnsQueryLogRecords.removeAll(dnsQueryLogRecordsSublist.toSet())
            dnsQueryLogRecords.addAll(dnsQueryLogRecordsSublist.reversed())
        }
    }

    private fun makeReverseLookup(ip: String) {
        if (!reverseLookupQueue.contains(ip)) {
            reverseLookupQueue.offer(ip)
        }
    }

    private fun startReverseLookupQueue() {

        if (futureTask?.isDone == false) {
            return
        }

        futureTask = cachedExecutor.submit {
            try {

                while (!Thread.currentThread().isInterrupted) {
                    val ip = reverseLookupQueue.take()

                    val host = dnsInteractor.get().reverseResolve(ip)

                    if (ipToHostAddressMap.size >= IP_TO_HOST_ADDRESS_MAP_SIZE) {

                        ipToHostAddressMap.keys.sortedBy { it.time }.let {
                            for (i in 0..it.size / 3) {
                                ipToHostAddressMap.remove(it[i])
                            }
                        }
                    }

                    if (host.isNotBlank()) {
                        ipToHostAddressMap[IpToTime(ip, System.currentTimeMillis())] = host
                    }
                }

            } catch (ignored: InterruptedException) {
            } catch (e: Exception) {
                loge("DNSQueryLogRecordsConverter reverse lookup exception", e)
            }
        }
    }

    private fun setQueryBlocked(dnsQueryRawRecord: ConnectionRecord): Boolean {

        if (dnsQueryRawRecord.daddr == META_ADDRESS
            || dnsQueryRawRecord.daddr == LOOPBACK_ADDRESS
            || dnsQueryRawRecord.daddr == "::"
            || dnsQueryRawRecord.daddr.contains(":") && blockIPv6
            || dnsQueryRawRecord.hInfo.contains("dnscrypt")
            || dnsQueryRawRecord.rCode != 0
        ) {

            dnsQueryRawRecord.blockedByIpv6 = (dnsQueryRawRecord.hInfo.contains(BLOCK_IPv6)
                    || dnsQueryRawRecord.daddr == "::"
                    || dnsQueryRawRecord.daddr.contains(":") && blockIPv6)

            dnsQueryRawRecord.blocked = true
            dnsQueryRawRecord.unused = false

        } else if (dnsQueryRawRecord.daddr.isBlank()
            && dnsQueryRawRecord.cName.isBlank()
            && !dnsQueryRawRecord.aName.contains(DNS_REVERSE_LOOKUP_SUFFIX)
        ) {
            dnsQueryRawRecord.blocked = true
            dnsQueryRawRecord.unused = false
        } else {
            dnsQueryRawRecord.unused = true
        }

        return dnsQueryRawRecord.blocked
    }

    fun onStop() {
        futureTask?.let {
            if (!it.isDone) {
                it.cancel(true)
                futureTask = null
            }
        }
    }

    private fun isIpInLanRange(destAddress: String): Boolean {
        if (destAddress.isBlank()) {
            return false
        }

        for (address in VpnUtils.nonTorList) {
            if (VpnUtils.isIpInSubnet(destAddress, address)) {
                return true
            }
        }
        return false
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
