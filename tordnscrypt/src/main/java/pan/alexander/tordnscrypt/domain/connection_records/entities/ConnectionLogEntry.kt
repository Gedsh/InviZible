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

sealed class ConnectionLogEntry(
    var time: Long = 0,
    var blocked: Boolean = false
)

data class DnsLogEntry(
    val domainsChain: MutableList<String>,
    val ips: MutableSet<String>,
    var visible: Boolean = true,
    var blockedByIpv6: Boolean = false
): ConnectionLogEntry()

data class PacketLogEntry(
    val uid: Int,
    val saddr: String,
    val daddr: String,
    @get:ConnectionProtocol
    val protocol: Int = UNDEFINED,
    var reverseDns: String? = null,
    var dnsLogEntry: DnsLogEntry? = null
): ConnectionLogEntry()
