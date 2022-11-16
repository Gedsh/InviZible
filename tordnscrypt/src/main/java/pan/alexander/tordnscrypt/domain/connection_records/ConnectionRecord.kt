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

package pan.alexander.tordnscrypt.domain.connection_records

class ConnectionRecord(
    val qName: String = "",
    val aName: String = "",
    var cName: String = "",
    val hInfo: String = "",
    val rCode: Int = 0,
    val saddr: String = "",
    var daddr: String = "",
    var uid: Int = -1000
) {
    var reverseDNS = ""
    var blocked = false
    var blockedByIpv6 = false
    var unused = true

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConnectionRecord

        if (qName != other.qName) return false
        if (aName != other.aName) return false
        if (cName != other.cName) return false
        if (hInfo != other.hInfo) return false
        if (rCode != other.rCode) return false
        if (saddr != other.saddr) return false
        if (daddr != other.daddr) return false
        if (uid != other.uid) return false

        return true
    }

    override fun hashCode(): Int {
        var result = qName.hashCode()
        result = 31 * result + aName.hashCode()
        result = 31 * result + cName.hashCode()
        result = 31 * result + hInfo.hashCode()
        result = 31 * result + rCode
        result = 31 * result + saddr.hashCode()
        result = 31 * result + daddr.hashCode()
        result = 31 * result + uid
        return result
    }

    override fun toString(): String {
        return "ConnectionRecord(qName='$qName'," +
                " aName='$aName'," +
                " cName='$cName'," +
                " hInfo='$hInfo'," +
                " rCode=$rCode," +
                " saddr='$saddr'," +
                " daddr='$daddr'," +
                " uid=$uid," +
                " reverseDNS='$reverseDNS'," +
                " blocked=$blocked," +
                " blockedByIpv6=$blockedByIpv6," +
                " unused=$unused)"
    }

}
