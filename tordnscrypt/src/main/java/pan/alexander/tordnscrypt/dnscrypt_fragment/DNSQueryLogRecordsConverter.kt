package pan.alexander.tordnscrypt.dnscrypt_fragment

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.firewall.APPS_ALLOW_GSM_PREF
import pan.alexander.tordnscrypt.settings.firewall.APPS_ALLOW_LAN_PREF
import pan.alexander.tordnscrypt.settings.firewall.APPS_ALLOW_ROAMING
import pan.alexander.tordnscrypt.settings.firewall.APPS_ALLOW_WIFI_PREF
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.utils.CachedExecutor
import pan.alexander.tordnscrypt.utils.PrefManager
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.Utils.getHostByIP
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.vpn.Util
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future

class DNSQueryLogRecordsConverter(context: Context) {

    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val blockIPv6: Boolean = sharedPreferences.getBoolean("block_ipv6", true)
    private var compatibilityMode = sharedPreferences.getBoolean("swCompatibilityMode", false)
    private val meteredNetwork = Util.isMeteredNetwork(context)
    private val vpnDNS = ServiceVPN.vpnDNS
    private val modulesStatus = ModulesStatus.getInstance()
    private val fixTTL = (modulesStatus.isFixTTL && modulesStatus.mode == OperationMode.ROOT_MODE
            && !modulesStatus.isUseModulesWithRoot)

    private val dnsQueryLogRecords = ArrayList<DNSQueryLogRecord>()
    private val dnsQueryLogRecordsSublist = ArrayList<DNSQueryLogRecord>()
    private val reverseLookupQueue = ArrayBlockingQueue<String>(100, true)
    private val ipToHostAddressMap = HashMap<String, String>()
    private var futureTask: Future<*>? = null

    private val firewallEnabled = PrefManager(context).getBoolPref("FirewallEnabled")
    private var appsAllowed = mutableSetOf<Int>()
    private val appsLanAllowed = mutableListOf<Int>()

    init {
        if (firewallEnabled) {
            PrefManager(context).getSetStrPref(APPS_ALLOW_LAN_PREF).forEach { appsLanAllowed.add(it.toInt()) }

            var tempSet: MutableSet<String>? = null
            if (Util.isWifiActive(context) || Util.isEthernetActive(context)) {
                tempSet = PrefManager(context).getSetStrPref(APPS_ALLOW_WIFI_PREF)
            } else if (Util.isCellularActive(context)) {
                tempSet = PrefManager(context).getSetStrPref(APPS_ALLOW_GSM_PREF)
            } else if (Util.isRoaming(context)) {
                tempSet = PrefManager(context).getSetStrPref(APPS_ALLOW_ROAMING)
            }

            tempSet?.forEach { appsAllowed.add(it.toInt()) }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            compatibilityMode = true
        }
    }

    fun convertRecords(dnsQueryRawRecords: ArrayList<DNSQueryLogRecord>): ArrayList<DNSQueryLogRecord> {

        dnsQueryLogRecords.clear()

        startReverseLookupQueue()

        dnsQueryRawRecords.forEach { addRecord(it) }

        return dnsQueryLogRecords
    }

    private fun addRecord(dnsQueryRawRecord: DNSQueryLogRecord) {

        if (dnsQueryLogRecords.isNotEmpty()) {
            if (dnsQueryRawRecord.uid != -1000) {
                addUID(dnsQueryRawRecord)
                return
            } else if (isIdenticalRecord(dnsQueryRawRecord)) {
                return
            }
        }

        setQueryBlocked(dnsQueryRawRecord)

        if (dnsQueryRawRecord.blocked) {
            dnsQueryLogRecords.removeAll { it == dnsQueryRawRecord }
        }

        dnsQueryLogRecords.add(dnsQueryRawRecord)
    }

    private fun isIdenticalRecord(dnsQueryRawRecord: DNSQueryLogRecord): Boolean {

        for (i in dnsQueryLogRecords.size - 1 downTo 0) {
            val record = dnsQueryLogRecords[i]

            if (dnsQueryRawRecord.aName == record.aName
                    && dnsQueryRawRecord.qName == record.qName
                    && dnsQueryRawRecord.hInfo == record.hInfo
                    && dnsQueryRawRecord.rCode == record.rCode
                    && dnsQueryRawRecord.saddr == record.saddr) {

                if (dnsQueryRawRecord.daddr.isNotEmpty() && record.daddr.isNotEmpty()) {
                    if (!record.daddr.contains(dnsQueryRawRecord.daddr.trim())) {
                        dnsQueryLogRecords[i] = record.apply { daddr = daddr + ", " + dnsQueryRawRecord.daddr.trim() }
                    }
                    return true
                }
            }
        }

        return false
    }

    private fun addUID(dnsQueryRawRecord: DNSQueryLogRecord) {
        var savedRecord: DNSQueryLogRecord? = null
        dnsQueryLogRecordsSublist.clear()

        val uidBlocked = if (firewallEnabled) {
            if (compatibilityMode && dnsQueryRawRecord.uid == ApplicationData.SPECIAL_UID_KERNEL
                    || fixTTL && dnsQueryRawRecord.uid == ApplicationData.SPECIAL_UID_KERNEL) {
                false
            }  else if (isIpInLanRange(dnsQueryRawRecord.daddr)) {
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

            val dnsQueryNewRecord = DNSQueryLogRecord(savedRecord.qName, savedRecord.aName, savedRecord.cName,
                    savedRecord.hInfo, -1, dnsQueryRawRecord.saddr, "", dnsQueryRawRecord.uid)
            dnsQueryNewRecord.blocked = uidBlocked
            dnsQueryNewRecord.unused = false

            dnsQueryLogRecordsSublist.add(dnsQueryNewRecord)

        } else if (vpnDNS != null && !vpnDNS.contains(dnsQueryRawRecord.daddr)) {

            if (!meteredNetwork && dnsQueryRawRecord.daddr.isNotEmpty()) {
                val host = ipToHostAddressMap[dnsQueryRawRecord.daddr]

                if (host == null) {
                    makeReverseLookup(dnsQueryRawRecord.daddr)
                } else {
                    dnsQueryRawRecord.reverseDNS = host
                }
            }

            dnsQueryRawRecord.blocked = uidBlocked

            dnsQueryRawRecord.unused = false

            dnsQueryLogRecords.removeAll { it == dnsQueryRawRecord }
            dnsQueryLogRecords.add(dnsQueryRawRecord)
        }

        if (dnsQueryLogRecordsSublist.isNotEmpty()) {
            dnsQueryLogRecords.removeAll(dnsQueryLogRecordsSublist)
            dnsQueryLogRecords.addAll(dnsQueryLogRecordsSublist.reversed())
        }
    }

    private fun makeReverseLookup(ip: String) {
        if (!reverseLookupQueue.contains(ip)) {
            reverseLookupQueue.offer(ip)
        }
    }

    private fun startReverseLookupQueue() {

        futureTask = CachedExecutor.getExecutorService().submit {
            try {

                while (!Thread.currentThread().isInterrupted) {
                    val ip = reverseLookupQueue.take()

                    val host = getHostByIP(ip)

                    if (host != ip) {
                        if (ipToHostAddressMap.size > 500) {
                            ipToHostAddressMap.clear()
                        }

                        ipToHostAddressMap[ip] = host
                    }
                }

            } catch (ignored: InterruptedException) {
            } catch (exception: Exception) {
                Log.e(LOG_TAG, "DNSQueryLogRecordsConverter reverse lookup exception " + exception.message + " " + exception.cause)
            }
        }
    }

    private fun setQueryBlocked(dnsQueryRawRecord: DNSQueryLogRecord): Boolean {

        if (dnsQueryRawRecord.daddr == "0.0.0.0"
                || dnsQueryRawRecord.daddr == "127.0.0.1"
                || dnsQueryRawRecord.daddr == "::"
                || dnsQueryRawRecord.daddr.contains(":") && blockIPv6
                || dnsQueryRawRecord.hInfo.contains("dnscrypt")
                || dnsQueryRawRecord.rCode != 0) {

            dnsQueryRawRecord.blockedByIpv6 = (dnsQueryRawRecord.hInfo.contains("block_ipv6")
                    || dnsQueryRawRecord.daddr == "::"
                    || dnsQueryRawRecord.daddr.contains(":") && blockIPv6)

            dnsQueryRawRecord.blocked = true
            dnsQueryRawRecord.unused = false

        } else if (dnsQueryRawRecord.daddr.isBlank()
                && dnsQueryRawRecord.cName.isBlank()
                && !dnsQueryRawRecord.aName.contains(".in-addr.arpa")) {
            dnsQueryRawRecord.blocked = true
            dnsQueryRawRecord.unused = false
        } else {
            dnsQueryRawRecord.unused = true
        }

        return dnsQueryRawRecord.blocked
    }

    fun onStop() {
        futureTask?.let { if (!it.isCancelled) it.cancel(true) }
    }

    private fun isIpInLanRange(destAddress: String): Boolean {
        if (destAddress.isBlank()) {
            return false
        }

        for (address in Util.nonTorList) {
            if (Util.isIpInSubnet(destAddress, address)) {
                return true
            }
        }
        return false
    }
}
