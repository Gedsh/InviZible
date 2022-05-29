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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.nflog

import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionData
import pan.alexander.tordnscrypt.domain.connection_records.entities.DnsRecord
import pan.alexander.tordnscrypt.domain.connection_records.entities.PacketRecord
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.net.IDN
import java.util.regex.Pattern
import javax.inject.Inject

class NflogParser @Inject constructor(
    private val nflogSessionsHolder: NflogSessionsHolder,
    pathVars: dagger.Lazy<PathVars>
) {

    private val packetPattern =
        Pattern.compile("PKT UID:(-?\\d+) ([^ ]+) SIP:([^ ]*) SPT:(\\d+) DIP:([^ ]*) DPT:(\\d+)")
    private val dnsPattern =
        Pattern.compile("DNS QNAME:([^ ]*) ANAME:([^ ]*) CNAME:([^ ]*) HINFO:(.*?) RCODE:(\\d+) IP:([^ ]*)")

    private val ownUid = pathVars.get().appUid

    fun parse(line: String): ConnectionData? =
        when {
            line.startsWith("PKT") -> parsePacket(line)
            line.startsWith("DNS") -> parseDNS(line)
            line.startsWith("ERR") -> parseError(line).let { null }
            else -> parseUnknown(line).let { null }
        }

    private fun parsePacket(line: String): PacketRecord? {
        val matcher = packetPattern.matcher(line)
        if (matcher.find()) {
            var uid = (matcher.group(1) ?: "-1").toInt()
            val protocol = matcher.group(2) ?: ""
            val saddr = matcher.group(3) ?: ""
            val sport = (matcher.group(4) ?: "0").toInt()
            val daddr = matcher.group(5) ?: ""
            val dport = (matcher.group(6) ?: "0").toInt()

            if (uid >= 0) {
                nflogSessionsHolder.addSession(uid, protocol, saddr, sport, daddr, dport)
            } else {
                uid = nflogSessionsHolder.getUid(protocol, saddr, sport, daddr, dport)
            }

            if (uid == ownUid) {
                return null
            }

            return PacketRecord(
                time = System.currentTimeMillis(),
                uid = uid,
                saddr = saddr,
                daddr = daddr
            )
        }

        return null
    }

    private fun parseDNS(line: String): DnsRecord? {

        val matcher = dnsPattern.matcher(line)
        if (matcher.find()) {
            val qName = matcher.group(1)?.toUnicode() ?: ""
            val aName = matcher.group(2)?.toUnicode() ?: ""
            val cName = matcher.group(3)?.toUnicode() ?: ""
            val hInfo = matcher.group(4) ?: ""
            val rCode = (matcher.group(5) ?: "0").toInt()
            val ip = matcher.group(6) ?: ""

            return DnsRecord(
                time = System.currentTimeMillis(),
                qName = qName,
                aName = aName,
                cName = cName,
                hInfo = hInfo,
                rCode = rCode,
                ip = ip
            )
        } else {
            loge("NflogParser failed to parse line $line")
        }

        return null
    }

    private fun parseError(line: String) {
        if (line.contains("unsupported yet")) {
            return
        }

        loge("NflogParser Nflog error. $line")
    }

    private fun parseUnknown(line: String) {
        loge("NflogParser unknown line $line")
    }

    private fun String.toUnicode(): String = IDN.toUnicode(this, IDN.ALLOW_UNASSIGNED)
}
