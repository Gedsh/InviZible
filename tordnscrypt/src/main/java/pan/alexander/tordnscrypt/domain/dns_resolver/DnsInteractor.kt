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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.dns_resolver

import kotlinx.coroutines.ObsoleteCoroutinesApi
import pan.alexander.tordnscrypt.settings.tor_ips.DomainIpEntity
import pan.alexander.tordnscrypt.utils.dns.Resolver

interface DnsInteractor {

    fun resolveDomain(
        domain: String,
        includeIPv6: Boolean,
    ): Set<String>

    fun resolveDomain(
        domain: String,
        includeIPv6: Boolean,
        timeout: Int
    ): Set<String>

    fun reverseResolve(ip: String): String

    @ObsoleteCoroutinesApi
    suspend fun resolveDomainOrIp(
        domainIps: Set<DomainIpEntity>,
        includeIPv6: Boolean,
        timeout: Int = Resolver.DNS_DEFAULT_TIMEOUT_SEC
    ): Set<DomainIpEntity>
}
