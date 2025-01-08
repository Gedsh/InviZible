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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.data.dns_resolver

import pan.alexander.tordnscrypt.utils.dns.Record

interface DnsDataSource {
    fun resolveDomainUDP(
        domain: String,
        includeIPv6: Boolean,
        port: Int,
        timeout: Int
    ): Array<Record>?

    fun resolveDomainDOH(
        domain: String,
        includeIPv6: Boolean,
        timeout: Int
    ): Array<Record>?

    fun reverseResolveUDP(
        ip: String,
        port: Int,
        timeout: Int
    ): Array<Record>?

    fun reverseResolveDOH(
        ip: String,
        timeout: Int
    ): Array<Record>?
}
