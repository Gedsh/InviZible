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

package pan.alexander.tordnscrypt.domain.dns_resolver

import android.util.Log
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_ips.DomainEntity
import pan.alexander.tordnscrypt.settings.tor_ips.DomainIpEntity
import pan.alexander.tordnscrypt.settings.tor_ips.IpEntity
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.lang.Exception
import javax.inject.Inject

private const val DELAY_ERROR_RETRY = 100L

class DnsInteractorImpl @Inject constructor(
    private val pathVars: PathVars,
    private val dnsRepository: DnsRepository,
) : DnsInteractor {
    private val modulesStatus = ModulesStatus.getInstance()

    override fun resolveDomain(domain: String): Set<String> =
        when {
            modulesStatus.dnsCryptState == ModuleState.RUNNING && modulesStatus.isDnsCryptReady -> {
                dnsRepository.resolveDomainUDP(domain, pathVars.dnsCryptPort.toInt())
            }
            modulesStatus.torState == ModuleState.RUNNING && modulesStatus.isTorReady -> {
                dnsRepository.resolveDomainUDP(domain, pathVars.torDNSPort.toInt())
            }
            else -> {
                dnsRepository.resolveDomainDOH(domain)
            }
        }

    override fun reverseResolve(ip: String): String =
        dnsRepository.reverseResolve(ip)

    override suspend fun resolveDomainOrIp(domainIps: List<DomainIpEntity>): List<DomainIpEntity> {
        return supervisorScope {

            domainIps.map {
                Pair(it, async { resolveDomainOrIp(it) })
            }.map {
                try {
                    it.second.await()
                } catch (e: Exception) {
                    retryResolveDomainOrIp(it.first)
                }
            }
        }
    }

    private suspend fun retryResolveDomainOrIp(domainIp: DomainIpEntity): DomainIpEntity =
        try {
            delay(DELAY_ERROR_RETRY)
            resolveDomainOrIp(domainIp)
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "DnsInteractor resolveDomainIps exception ${e.message}\n${e.cause}"
            )
            domainIp
        }

    private fun resolveDomainOrIp(domainIp: DomainIpEntity): DomainIpEntity =
        when (domainIp) {
            is DomainEntity -> {
                DomainEntity(domainIp.domain, resolveDomain(domainIp.domain), domainIp.isActive)
            }
            is IpEntity -> {
                IpEntity(domainIp.ip, reverseResolve(domainIp.ip), domainIp.isActive)
            }
        }
}
