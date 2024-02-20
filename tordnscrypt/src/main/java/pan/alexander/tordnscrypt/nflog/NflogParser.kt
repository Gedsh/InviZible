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

package pan.alexander.tordnscrypt.nflog

import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionData
import pan.alexander.tordnscrypt.domain.connection_records.entities.DnsRecord
import pan.alexander.tordnscrypt.domain.connection_records.entities.PacketRecord
import pan.alexander.tordnscrypt.domain.connection_records.entities.UNDEFINED
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
        Pattern.compile("PKT TIME:(\\d+?) UID:(-?\\d+?) ([^ ]+?) SIP:([^ ]*) SPT:(\\d+?) DIP:([^ ]*) DPT:(\\d+?)")
    private val dnsPattern =
        Pattern.compile("DNS TIME:(\\d+?) QNAME:([^ ]*) ANAME:([^ ]*) CNAME:([^ ]*) HINFO:(.*?) RCODE:(\\d+?) IP:([^ ]*)")

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
            val time = (matcher.group(1) ?: "0").toLong()
                .takeIf { it > 0 } ?: System.currentTimeMillis()
            var uid = (matcher.group(2) ?: "-1").toInt()
            val protocol = matcher.group(3) ?: ""
            val saddr = matcher.group(4) ?: ""
            val sport = (matcher.group(5) ?: "0").toInt()
            val daddr = matcher.group(6) ?: ""
            val dport = (matcher.group(7) ?: "0").toInt()

            if (uid >= 0) {
                nflogSessionsHolder.addSession(uid, protocol, saddr, sport, daddr, dport)
            } else {
                uid = nflogSessionsHolder.getUid(protocol, saddr, sport, daddr, dport)
            }

            if (uid == ownUid) {
                return null
            }

            val protocolInt = when(protocol) {
                "TCP" -> 6
                "UDP" -> 17
                "ICMPv4" -> 1
                "ICMPv6" -> 58
                else -> UNDEFINED
            }

            return PacketRecord(
                time = time,
                uid = uid,
                saddr = saddr,
                daddr = daddr,
                protocol = protocolInt,
                allowed = true
            )
        }

        return null
    }

    private fun parseDNS(line: String): DnsRecord? {

        val matcher = dnsPattern.matcher(line)
        if (matcher.find()) {
            val time = (matcher.group(1) ?: "0").toLong()
                .takeIf { it > 0 } ?: System.currentTimeMillis()
            val qName = matcher.group(2)?.toUnicode()?.lowercase() ?: ""
            val aName = matcher.group(3)?.toUnicode()?.lowercase() ?: ""
            val cName = matcher.group(4)?.toUnicode()?.lowercase() ?: ""
            val hInfo = matcher.group(5) ?: ""
            val rCode = (matcher.group(6) ?: "0").toInt()
            val ip = matcher.group(7) ?: ""

            return DnsRecord(
                time = time,
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
