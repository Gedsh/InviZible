package pan.alexander.tordnscrypt.dnscrypt_fragment

import java.util.*

class DNSQueryLogRecords (private val vpnDNS1: String,
                          private val vpnDNS2: String) {

    constructor(vpnDNS1: String): this(vpnDNS1, "149.112.112.112")

    private lateinit var dnsQueryLogRecords: LinkedList<DNSQueryLogRecord>

    fun convertRecords(dnsQueryRawRecords: LinkedList<DNSQueryLogRecord>):LinkedList<DNSQueryLogRecord>  {
        dnsQueryLogRecords = LinkedList<DNSQueryLogRecord>()
        dnsQueryRawRecords.forEach { addRecord(it) }
        return dnsQueryLogRecords
    }

    private fun addRecord(dnsQueryRawRecord: DNSQueryLogRecord) {

        if (!dnsQueryLogRecords.isEmpty() && dnsQueryRawRecord.uid != -1000) {
            addUID(dnsQueryRawRecord)
            return
        } else if (!dnsQueryLogRecords.isEmpty()) {

            val lastRecord = dnsQueryLogRecords.last

            if (dnsQueryRawRecord.aName == lastRecord.aName
                    && dnsQueryRawRecord.qName == lastRecord.qName
                    && dnsQueryRawRecord.hInfo == lastRecord.hInfo
                    && dnsQueryRawRecord.rCode == lastRecord.rCode) {

                if (lastRecord.ip.contains(dnsQueryRawRecord.ip)) {
                    return
                } else if (dnsQueryRawRecord.ip.isNotEmpty() && lastRecord.ip.isNotEmpty()) {
                    if (!lastRecord.ip.contains(dnsQueryRawRecord.ip)) {
                        dnsQueryLogRecords[dnsQueryLogRecords.size - 1] = lastRecord.apply { ip = ip + ", " + dnsQueryRawRecord.ip }
                    }
                    return
                }
            }
        }

        setQueryBlocked(dnsQueryRawRecord)

        dnsQueryLogRecords.add(dnsQueryRawRecord)
    }

    private fun addUID(dnsQueryRawRecord: DNSQueryLogRecord) {
        var savedRecord: DNSQueryLogRecord? = null
        var savedIndex: Int = -1
        for (index in dnsQueryLogRecords.size - 1 downTo 0) {
            val record = dnsQueryLogRecords[index]
            if (savedIndex < 0 && record.ip.contains(dnsQueryRawRecord.ip) && record.uid == -1000) {
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
                    "", 0, "", dnsQueryRawRecord.uid)

            val previousDnsQueryLogRecord: DNSQueryLogRecord? = if (savedIndex - 1 >= 0) {
                dnsQueryLogRecords[savedIndex - 1]
            } else {
                null
            }

            if (previousDnsQueryLogRecord?.uid != dnsQueryRawRecord.uid) {
                dnsQueryLogRecords.add(savedIndex, dnsQueryNewRecord)
            }
        } else if (dnsQueryRawRecord.ip != vpnDNS1 && dnsQueryRawRecord.ip != vpnDNS2) {

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

        if (dnsQueryRawRecord.ip == "0.0.0.0" || dnsQueryRawRecord.ip == "127.0.0.1" || dnsQueryRawRecord.ip.contains(":")
                || dnsQueryRawRecord.hInfo.contains("dnscrypt") || dnsQueryRawRecord.rCode != 0) {

            if (dnsQueryRawRecord.hInfo.contains("block_ipv6") || dnsQueryRawRecord.ip.contains(":")) {
                dnsQueryRawRecord.blockedByIpv6 = true
            }

            dnsQueryRawRecord.blocked = true

        } else if (dnsQueryRawRecord.ip.isEmpty() && dnsQueryRawRecord.cName.isEmpty()) {
            dnsQueryRawRecord.blocked = true
        }

        return dnsQueryRawRecord.blocked
    }
}