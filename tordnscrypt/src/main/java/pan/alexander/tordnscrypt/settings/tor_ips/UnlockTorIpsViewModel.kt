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

package pan.alexander.tordnscrypt.settings.tor_ips

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.di.CoroutinesModule.Companion.DISPATCHER_COMPUTATION
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractor
import pan.alexander.tordnscrypt.domain.resources.ResourceRepository
import pan.alexander.tordnscrypt.domain.tor_ips.TorIpsInteractor
import pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFragment.DEVICE_VALUE
import pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFragment.TETHER_VALUE
import pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX
import pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_FOR_CLEARNET
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IPS_TO_UNLOCK
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named

@ObsoleteCoroutinesApi
class UnlockTorIpsViewModel @Inject constructor(
    private val torIpsInteractor: TorIpsInteractor,
    private val dnsInteractor: dagger.Lazy<DnsInteractor>,
    @Named(DISPATCHER_COMPUTATION)
    private val dispatcherIo: CoroutineDispatcher,
    private val exceptionHandler: CoroutineExceptionHandler,
    private val resourceRepository: dagger.Lazy<ResourceRepository>
) : ViewModel() {

    var deviceOrTether: String = ""
    private var unlockHostsStr: String = ""
    private var unlockIPsStr: String = ""
    var routeAllThroughTorDevice: Boolean = false
    var routeAllThroughTorTether: Boolean = false

    private val mutableDomainIpLiveData = MutableLiveData<Set<DomainIpEntity>>()
    val domainIpLiveData: LiveData<Set<DomainIpEntity>> get() = mutableDomainIpLiveData

    private val domainIps: MutableSet<DomainIpEntity> by lazy {
        Collections.newSetFromMap(ConcurrentHashMap())
    }

    private val coroutineContext by lazy {
        dispatcherIo + CoroutineName("getDomainIps") + exceptionHandler
    }

    @Volatile
    private var getDomainIpsJob: Job? = null

    private val ipv4Regex by lazy { Regex(IPv4_REGEX) }
    private val ipv6Regex by lazy { Regex(IPv6_REGEX) }

    fun defineAppropriatePreferenceKeys(
        deviceOrTether: String,
        routeAllThroughTorDevice: Boolean,
        routeAllThroughTorTether: Boolean
    ) {
        this.deviceOrTether = deviceOrTether
        this.routeAllThroughTorDevice = routeAllThroughTorDevice
        this.routeAllThroughTorTether = routeAllThroughTorTether

        if (deviceOrTether == DEVICE_VALUE) {
            if (!routeAllThroughTorDevice) {
                unlockHostsStr = "unlockHosts"
                unlockIPsStr = "unlockIPs"
            } else {
                unlockHostsStr = "clearnetHosts"
                unlockIPsStr = "clearnetIPs"
            }
        } else if (deviceOrTether == TETHER_VALUE) {
            if (!routeAllThroughTorTether) {
                unlockHostsStr = "unlockHostsTether"
                unlockIPsStr = "unlockIPsTether"
            } else {
                unlockHostsStr = "clearnetHostsTether"
                unlockIPsStr = "clearnetIPsTether"
            }
        }
    }

    private fun updateDomainIps(domainIps: Set<DomainIpEntity>) {
        this.domainIps.removeAll(domainIps)
        this.domainIps.addAll(domainIps)
        mutableDomainIpLiveData.postValue(this.domainIps)
    }

    fun addDomainIp(domainIp: DomainIpEntity, includeIPv6: Boolean) {
        this.domainIps.remove(domainIp)
        this.domainIps.add(domainIp)
        resolveDomainIps(includeIPv6)
        mutableDomainIpLiveData.postValue(domainIps)
    }

    fun updateDomainIp(
        domainIp: DomainIpEntity,
        oldDomainIp: DomainIpEntity,
        includeIPv6: Boolean
    ) {
        this.domainIps.remove(oldDomainIp)
        this.domainIps.add(domainIp)
        resolveDomainIps(includeIPv6)
        mutableDomainIpLiveData.postValue(domainIps)
    }

    fun removeDomainIp(domainIp: DomainIpEntity, includeIPv6: Boolean) {
        this.domainIps.remove(domainIp)
        resolveDomainIps(includeIPv6)
        mutableDomainIpLiveData.postValue(domainIps)
    }

    fun resolveDomain(domain: String, includeIPv6: Boolean): Set<String> =
        dnsInteractor.get().resolveDomain(domain, includeIPv6)

    fun reverseResolve(ip: String): String =
        dnsInteractor.get().reverseResolve(ip)

    fun getDomainIps(includeIPv6: Boolean) {
        viewModelScope.launch(coroutineContext) {
            getDomainIpsFromPreferences()
            resolveDomainIps(includeIPv6)
        }
    }

    private suspend fun getDomainIpsFromPreferences() {
        coroutineScope {
            val result =
                getDomainIpsFromPreferences(
                    unlockHostsStr,
                    unlockIPsStr,
                    resourceRepository.get().getPleaseWaitString()
                )

            ensureActive()
            updateDomainIps(result)
        }
    }

    private fun resolveDomainIps(includeIPv6: Boolean) {

        getDomainIpsJob?.cancel()

        getDomainIpsJob = viewModelScope.launch(coroutineContext) {
            val result = dnsInteractor.get().resolveDomainOrIp(domainIps, includeIPv6)
                .map {
                    replacePleaseWaitMessage(
                        it,
                        resourceRepository.get().getPleaseWaitString(),
                        resourceRepository.get().getWrongIpString()
                    )
                }.toSet()

            ensureActive()
            updateDomainIps(result)
        }
    }

    private fun replacePleaseWaitMessage(
        domainIp: DomainIpEntity,
        pleaseWaitMessage: String,
        wrongDomainIpMessage: String
    ): DomainIpEntity =
        when (domainIp) {
            is DomainEntity -> {
                if (pleaseWaitMessage == (domainIp.ips.firstOrNull() ?: pleaseWaitMessage)) {
                    DomainEntity(domainIp.domain, setOf(wrongDomainIpMessage), domainIp.isActive)
                } else {
                    domainIp
                }
            }
            is IpEntity -> {
                if (pleaseWaitMessage == domainIp.domain) {
                    IpEntity(domainIp.ip, "", domainIp.isActive)
                } else {
                    domainIp
                }
            }
        }

    private fun getDomainIpsFromPreferences(
        unlockHostsStr: String,
        unlockIPsStr: String,
        pleaseWaitMessage: String
    ): Set<DomainIpEntity> = torIpsInteractor.getDomainIpsFromPreferences(
        unlockHostsStr,
        unlockIPsStr,
        pleaseWaitMessage
    )

    fun saveDomainIps(): Boolean {
        var unlockHostIPContainsActive = false

        val ipsToUnlock = hashSetOf<String>()

        for (domainIp in domainIps) {
            if (domainIp.isActive) {
                if (domainIp is DomainEntity) {
                    for (ip in domainIp.ips) {
                        if (ip.matches(ipv4Regex) || ip.matches(ipv6Regex)) {
                            ipsToUnlock.add(ip)
                        }
                    }
                } else if (domainIp is IpEntity) {
                    val ip = domainIp.ip
                    if (ip.matches(ipv4Regex) || ip.matches(ipv6Regex)) {
                        ipsToUnlock.add(ip)
                    }
                }
                unlockHostIPContainsActive = true
            }
        }

        if (domainIps.size > 0 && ipsToUnlock.isEmpty() && unlockHostIPContainsActive) {
            return false
        }

        var settingsChanged = false


        //////////////////////////////////////////////////////////////////////////////////////
        //////////////When open this fragment to add sites for internal applications/////////
        /////////////////////////////////////////////////////////////////////////////////////
        if (DEVICE_VALUE == deviceOrTether) {
            settingsChanged = if (!routeAllThroughTorDevice) {
                saveDomainIpsToPreferences(ipsToUnlock, IPS_TO_UNLOCK)
            } else {
                saveDomainIpsToPreferences(ipsToUnlock, IPS_FOR_CLEARNET)
            }

            //////////////////////////////////////////////////////////////////////////////////////
            //////////////When open this fragment to add sites for external tether devices/////////
            /////////////////////////////////////////////////////////////////////////////////////
        } else if (TETHER_VALUE == deviceOrTether) {
            settingsChanged = if (!routeAllThroughTorTether) {
                saveDomainIpsToPreferences(
                    ipsToUnlock.filter { it.matches(ipv4Regex) }.toSet(),
                    PreferenceKeys.IPS_TO_UNLOCK_TETHER
                )
            } else {
                saveDomainIpsToPreferences(
                    ipsToUnlock.filter { it.matches(ipv4Regex) }.toSet(),
                    PreferenceKeys.IPS_FOR_CLEARNET_TETHER
                )
            }
        }

        return settingsChanged
    }

    private fun saveDomainIpsToPreferences(
        ipsToUnlock: Set<String>,
        settingsKey: String
    ): Boolean = torIpsInteractor.saveDomainIpsToPreferences(
        ipsToUnlock,
        settingsKey
    )

    fun deleteDomainIpFromPreferences(
        domainIp: DomainIpEntity
    ) = torIpsInteractor.deleteDomainIpFromPreferences(
        domainIp,
        unlockHostsStr,
        unlockIPsStr
    )

    fun saveDomainActiveInPreferences(
        oldDomain: String,
        active: Boolean
    ) = torIpsInteractor.saveDomainActiveInPreferences(
        oldDomain,
        active,
        unlockHostsStr
    )

    fun saveIpActiveInPreferences(
        oldIp: String,
        active: Boolean
    ) = torIpsInteractor.saveIpActiveInPreferences(
        oldIp,
        active,
        unlockIPsStr
    )

    fun addDomainToPreferences(
        domain: String
    ) = torIpsInteractor.addDomainToPreferences(
        domain,
        unlockHostsStr
    )

    fun addIpToPreferences(
        ip: String
    ) = torIpsInteractor.addIpToPreferences(
        ip,
        unlockIPsStr
    )

    fun replaceDomainInPreferences(
        domain: String,
        oldDomain: String
    ) = torIpsInteractor.replaceDomainInPreferences(
        domain,
        oldDomain,
        unlockHostsStr
    )

    fun replaceIpInPreferences(
        ip: String,
        oldIp: String
    ) = torIpsInteractor.replaceIpInPreferences(
        ip,
        oldIp,
        unlockIPsStr
    )
}
