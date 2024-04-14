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

object ConnectionProtocol {
    const val UNDEFINED = -1
    const val TCP = 6
    const val UDP = 17
    const val ICMPv4 = 1
    const val ICMPv6 = 58
    const val IP = 0
    const val IGMP = 2
    const val IPIP = 4
    const val EGP = 8
    const val PUP = 12
    const val IDP = 22
    const val DCCP = 33
    const val RSVP = 46
    const val GRE = 47
    const val IPv6inIPv4 = 41
    const val ESP = 50
    const val AH = 51
    const val BEETPH = 94
    const val PIM = 103
    const val COMP = 108
    const val SCTP = 132
    const val UDPLITE = 136
    const val RAW = 255
    const val MAX = 256

    fun toString(protocol: Int) =  when (protocol) {
        TCP -> "TCP"
        UDP -> "UDP"
        ICMPv4 -> "ICMPv4"
        ICMPv6 -> "ICMPv6"
        IP -> "IP"
        IGMP -> "IGMP"
        IPIP -> "IPIP"
        EGP -> "EGP"
        PUP -> "PUP"
        IDP -> "IDP"
        DCCP -> "DCCP"
        RSVP -> "RSVP"
        GRE -> "GRE"
        IPv6inIPv4 -> "IPv6-in-IPv4"
        ESP -> "ESP"
        AH -> "AH"
        BEETPH -> "BEETPH"
        PIM -> "PIM"
        COMP -> "COMP"
        SCTP -> "SCTP"
        UDPLITE -> "UDPLITE"
        RAW -> "RAW"
        MAX -> "MAX"
        else -> ""
    }
}
