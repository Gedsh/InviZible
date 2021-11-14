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
import kotlinx.coroutines.channels.actor
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_ips.DomainEntity
import pan.alexander.tordnscrypt.settings.tor_ips.DomainIpEntity
import pan.alexander.tordnscrypt.settings.tor_ips.IpEntity
import pan.alexander.tordnscrypt.utils.dns.Resolver
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val DELAY_ERROR_RETRY = 100L
private const val ERROR_RETRY_COUNT = 1

class DnsInteractorImpl @Inject constructor(
    private val pathVars: PathVars,
    private val dnsRepository: DnsRepository,
) : DnsInteractor {
    private val modulesStatus = ModulesStatus.getInstance()

    override fun resolveDomain(domain: String): Set<String> =
        resolveDomain(domain, Resolver.DNS_DEFAULT_TIMEOUT_SEC)

    override fun resolveDomain(domain: String, timeout: Int): Set<String> =
        when {
            modulesStatus.dnsCryptState == ModuleState.RUNNING && modulesStatus.isDnsCryptReady -> {
                dnsRepository.resolveDomainUDP(domain, pathVars.dnsCryptPort.toInt(), timeout)
            }
            modulesStatus.torState == ModuleState.RUNNING && modulesStatus.isTorReady -> {
                dnsRepository.resolveDomainUDP(domain, pathVars.torDNSPort.toInt(), timeout)
            }
            else -> {
                dnsRepository.resolveDomainDOH(domain, timeout)
            }
        }

    override fun reverseResolve(ip: String): String =
        when {
            modulesStatus.dnsCryptState == ModuleState.RUNNING && modulesStatus.isDnsCryptReady -> {
                dnsRepository.reverseResolveDomainUDP(
                    ip,
                    pathVars.dnsCryptPort.toInt(),
                    Resolver.DNS_DEFAULT_TIMEOUT_SEC
                )
            }
            modulesStatus.torState == ModuleState.RUNNING && modulesStatus.isTorReady -> {
                dnsRepository.reverseResolveDomainUDP(
                    ip,
                    pathVars.torDNSPort.toInt(),
                    Resolver.DNS_DEFAULT_TIMEOUT_SEC
                )
            }
            else -> {
                dnsRepository.reverseResolveDomainDOH(
                    ip,
                    Resolver.DNS_DEFAULT_TIMEOUT_SEC
                )
            }
        }

    @ObsoleteCoroutinesApi
    override suspend fun resolveDomainOrIp(
        domainIps: Set<DomainIpEntity>,
        timeout: Int
    ): Set<DomainIpEntity> {
        val result = Collections.newSetFromMap(ConcurrentHashMap<DomainIpEntity, Boolean>())

        coroutineScope {
            val channel = actor<Triple<DomainIpEntity, Deferred<DomainIpEntity>, Int>> {
                for (triple in channel) {
                    launch {
                        supervisorScope {
                            try {
                                val hostIp = triple.second.await()
                                result.add(hostIp)
                                if (result.size == domainIps.size) {
                                    channel.close()
                                }
                            } catch (e: IOException) {
                                if (triple.third < ERROR_RETRY_COUNT) {
                                    channel.send(
                                        Triple(
                                            triple.first,
                                            async { resolveDomainOrIp(triple.first, timeout) },
                                            triple.third + 1
                                        )
                                    )

                                } else {
                                    Log.e(
                                        LOG_TAG,
                                        "DnsInteractor ${e.javaClass} ${e.message}\n${e.cause}"
                                    )
                                    result.add(triple.first)
                                    if (result.size == domainIps.size) {
                                        channel.close()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(
                                    LOG_TAG,
                                    "DnsInteractor ${e.javaClass} ${e.message}\n${e.cause}"
                                )
                                channel.close()
                            }
                        }
                    }
                }
            }

            supervisorScope {
                domainIps.map {
                    Triple(it, async { resolveDomainOrIp(it, timeout) }, 0)
                }.map {
                    channel.send(it)
                }
            }

        }

        return result
    }

    suspend fun resolveDomainOrIpOld(
        domainIps: List<DomainIpEntity>,
        timeout: Int
    ): List<DomainIpEntity> {
        return supervisorScope {

            domainIps.map {
                it to async { resolveDomainOrIp(it, timeout) }
            }.map {
                try {
                    it.second.await()
                } catch (e: Exception) {
                    retryResolveDomainOrIp(it.first, timeout)
                }
            }
        }
    }

    private suspend fun retryResolveDomainOrIp(
        domainIp: DomainIpEntity,
        timeout: Int
    ): DomainIpEntity =
        try {
            delay(DELAY_ERROR_RETRY)
            resolveDomainOrIp(domainIp, timeout)
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "DnsInteractor resolveDomainIps exception ${e.message}\n${e.cause}"
            )
            domainIp
        }

    private fun resolveDomainOrIp(domainIp: DomainIpEntity, timeout: Int): DomainIpEntity =
        when (domainIp) {
            is DomainEntity -> {
                DomainEntity(
                    domainIp.domain,
                    resolveDomain(domainIp.domain, timeout),
                    domainIp.isActive
                )
            }
            is IpEntity -> {
                IpEntity(domainIp.ip, reverseResolve(domainIp.ip), domainIp.isActive)
            }
        }
}
