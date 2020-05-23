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

import java.util.*

const val recordsToCheck = 5

class DNSQueryLogRecords(private val blockIPv6: Boolean,
                         private val vpnDNS1: String,
                         private val vpnDNS2: String) {

    constructor(blockIPv6: Boolean, vpnDNS1: String) : this(blockIPv6, vpnDNS1, "149.112.112.112")

    private lateinit var dnsQueryLogRecords: LinkedList<DNSQueryLogRecord>

    fun convertRecords(dnsQueryRawRecords: LinkedList<DNSQueryLogRecord>): LinkedList<DNSQueryLogRecord> {
        dnsQueryLogRecords = LinkedList<DNSQueryLogRecord>()
        dnsQueryRawRecords.forEach { addRecord(it) }
        return dnsQueryLogRecords
    }

    private fun addRecord(dnsQueryRawRecord: DNSQueryLogRecord) {

        if (!dnsQueryLogRecords.isEmpty() && dnsQueryRawRecord.uid != -1000) {
            addUID(dnsQueryRawRecord)
            return
        } else if (!dnsQueryLogRecords.isEmpty() && isIdenticalRecord(dnsQueryRawRecord)) {
            return
        }

        setQueryBlocked(dnsQueryRawRecord)

        dnsQueryLogRecords.add(dnsQueryRawRecord)
    }

    private fun isIdenticalRecord(dnsQueryRawRecord: DNSQueryLogRecord) : Boolean {

        var lastRecordIndex = 0
        if (dnsQueryLogRecords.size - recordsToCheck > 0) {
            lastRecordIndex = dnsQueryLogRecords.size - recordsToCheck - 1
        }

        for (i in dnsQueryLogRecords.size - 1 downTo lastRecordIndex) {
            val lastRecord = dnsQueryLogRecords[i]

            if (dnsQueryRawRecord.aName == lastRecord.aName
                    && dnsQueryRawRecord.qName == lastRecord.qName
                    && dnsQueryRawRecord.hInfo == lastRecord.hInfo
                    && dnsQueryRawRecord.rCode == lastRecord.rCode
                    && dnsQueryRawRecord.saddr == lastRecord.saddr) {

                if (lastRecord.daddr.contains(dnsQueryRawRecord.daddr)) {
                    return true
                } else if (dnsQueryRawRecord.daddr.isNotEmpty() && lastRecord.daddr.isNotEmpty()) {
                    if (!lastRecord.daddr.contains(dnsQueryRawRecord.daddr)) {
                        dnsQueryLogRecords[i] = lastRecord.apply { daddr = daddr + ", " + dnsQueryRawRecord.daddr }
                    }
                    return true
                }
            }
        }

        return false
    }

    private fun addUID(dnsQueryRawRecord: DNSQueryLogRecord) {
        var savedRecord: DNSQueryLogRecord? = null
        var savedIndex: Int = -1
        for (index in dnsQueryLogRecords.size - 1 downTo 0) {
            val record = dnsQueryLogRecords[index]
            if (savedIndex < 0 && record.daddr.contains(dnsQueryRawRecord.daddr) && record.uid == -1000) {
                savedRecord = record
                savedIndex = index
            } else if (savedRecord != null && savedRecord.aName == record.cName) {
                savedRecord = record
                savedIndex = index
            } else if (savedRecord != null && savedRecord.aName != record.cName) {
                break
            }
        }

        if (savedRecord != null && savedIndex >= 0) {

            val dnsQueryNewRecord = DNSQueryLogRecord(savedRecord.qName, savedRecord.aName, "",
                    "", 0, dnsQueryRawRecord.saddr, "", dnsQueryRawRecord.uid)

            val previousDnsQueryLogRecord: DNSQueryLogRecord? = if (savedIndex - 1 >= 0) {
                dnsQueryLogRecords[savedIndex - 1]
            } else {
                null
            }

            if (previousDnsQueryLogRecord?.uid != dnsQueryRawRecord.uid) {
                dnsQueryLogRecords.add(savedIndex, dnsQueryNewRecord)
            }
        } else if (dnsQueryRawRecord.daddr != vpnDNS1 && dnsQueryRawRecord.daddr != vpnDNS2) {

            val previousDnsQueryLogRecord: DNSQueryLogRecord? = if (savedIndex - 1 >= 0) {
                dnsQueryLogRecords[savedIndex - 1]
            } else {
                null
            }

            if (previousDnsQueryLogRecord?.uid != dnsQueryRawRecord.uid) {
                dnsQueryLogRecords.add(dnsQueryRawRecord)
            }
        }
    }

    private fun setQueryBlocked(dnsQueryRawRecord: DNSQueryLogRecord): Boolean {

        if (dnsQueryRawRecord.daddr == "0.0.0.0" || dnsQueryRawRecord.daddr == "127.0.0.1" || dnsQueryRawRecord.daddr == "::"
                || dnsQueryRawRecord.daddr.contains(":") && blockIPv6
                || dnsQueryRawRecord.hInfo.contains("dnscrypt") || dnsQueryRawRecord.rCode != 0) {

            if (dnsQueryRawRecord.hInfo.contains("block_ipv6") || dnsQueryRawRecord.daddr == "::"
                    || dnsQueryRawRecord.daddr.contains(":") && blockIPv6) {
                dnsQueryRawRecord.blockedByIpv6 = true
            }

            dnsQueryRawRecord.blocked = true

        } else if (dnsQueryRawRecord.daddr.isEmpty() && dnsQueryRawRecord.cName.isEmpty()) {
            dnsQueryRawRecord.blocked = true
        }

        return dnsQueryRawRecord.blocked
    }
}