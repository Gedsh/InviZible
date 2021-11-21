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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.data.dns_resolver

import pan.alexander.tordnscrypt.utils.dns.Record
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsRepository
import javax.inject.Inject

class DnsRepositoryImpl @Inject constructor(
    private val dnsDataSource: DnsDataSource
) : DnsRepository {

    override fun resolveDomainUDP(domain: String, port: Int, timeout: Int): Set<String> {
        return dnsDataSource.resolveDomainUDP(domain, port, timeout)
            ?.filter { isRecordValid(it) }
            ?.flatMap {
                when {
                    it.isA || it.isAAAA -> listOf(it.value.trim())
                    it.isCname -> resolveDomainUDP("https://${it.value}", port, timeout)
                    else -> emptyList()
                }
            }
            ?.toHashSet() ?: emptySet()
    }

    override fun resolveDomainDOH(domain: String, timeout: Int): Set<String> {
        return dnsDataSource.resolveDomainDOH(domain, timeout)
            ?.filter { isRecordValid(it) }
            ?.flatMap {
                when {
                    it.isA || it.isAAAA -> listOf(it.value.trim())
                    it.isCname -> resolveDomainDOH("https://${it.value}", timeout)
                    else -> emptyList()
                }
            }
            ?.toHashSet() ?: emptySet()
    }

    override fun reverseResolveDomainUDP(ip: String, port: Int, timeout: Int): String {
        return dnsDataSource.reverseResolveUDP(ip, port, timeout)
            ?.getOrNull(0)?.value ?: ""
    }

    override fun reverseResolveDomainDOH(ip: String, timeout: Int): String {
        return dnsDataSource.reverseResolveDOH(ip, timeout)
        ?.getOrNull(0)?.value ?: ""
    }

    private fun isRecordValid(record: Record?): Boolean {
        return record?.value != null && record.value.isNotEmpty() && !record.isExpired
    }
}
