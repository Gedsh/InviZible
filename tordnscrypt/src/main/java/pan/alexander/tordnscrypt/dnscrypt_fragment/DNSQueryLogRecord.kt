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

class DNSQueryLogRecord(val qName: String = "",
                        val aName:String = "",
                        var cName:String = "",
                        val hInfo:String = "",
                        val rCode: Int = 0,
                        val saddr:String = "",
                        var daddr:String = "",
                        var uid:Int = -1000) {
    var blocked = false
    var blockedByIpv6 = false
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DNSQueryLogRecord

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


}