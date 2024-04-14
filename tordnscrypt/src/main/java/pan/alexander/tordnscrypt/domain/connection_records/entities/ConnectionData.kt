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

package pan.alexander.tordnscrypt.domain.connection_records.entities

import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionProtocol.UNDEFINED

sealed class ConnectionData(val time: Long) {
    override fun toString(): String {
        return "ConnectionData(time=$time)"
    }
}

class DnsRecord(
    time: Long,
    val qName: String,
    val aName: String,
    val cName: String,
    val hInfo: String,
    val rCode: Int,
    val ip: String
): ConnectionData(time) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DnsRecord

        if (qName != other.qName) return false
        if (aName != other.aName) return false
        if (cName != other.cName) return false
        if (hInfo != other.hInfo) return false
        if (rCode != other.rCode) return false
        if (ip != other.ip) return false

        return true
    }

    override fun hashCode(): Int {
        var result = qName.hashCode()
        result = 31 * result + aName.hashCode()
        result = 31 * result + cName.hashCode()
        result = 31 * result + hInfo.hashCode()
        result = 31 * result + rCode
        result = 31 * result + ip.hashCode()
        return result
    }

    override fun toString(): String {
        return "DnsRecord(time='$time', qName='$qName', aName='$aName', cName='$cName', hInfo='$hInfo', rCode=$rCode, ip='$ip')"
    }


}

class PacketRecord(
    time: Long,
    val uid: Int,
    val saddr: String,
    val daddr: String,
    val protocol: Int = UNDEFINED,
    val allowed: Boolean
): ConnectionData(time) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PacketRecord

        if (uid != other.uid) return false
        if (saddr != other.saddr) return false
        if (daddr != other.daddr) return false
        if (protocol != other.protocol) return false
        return allowed == other.allowed
    }

    override fun hashCode(): Int {
        var result = uid
        result = 31 * result + saddr.hashCode()
        result = 31 * result + daddr.hashCode()
        result = 31 * result + protocol.hashCode()
        result = 31 * result + allowed.hashCode()
        return result
    }

    override fun toString(): String {
        return "PacketRecord(time='$time', uid=$uid, saddr='$saddr', daddr='$daddr, protocol='$protocol', allowed='$allowed')"
    }


}
