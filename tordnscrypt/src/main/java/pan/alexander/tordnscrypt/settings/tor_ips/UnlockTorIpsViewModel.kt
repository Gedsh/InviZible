package pan.alexander.tordnscrypt.settings.tor_ips

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.CoroutinesModule.Companion.DISPATCHER_COMPUTATION
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractor
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import javax.inject.Inject
import javax.inject.Named

class UnlockTorIpsViewModel : ViewModel() {

    @Inject
    lateinit var dnsInteractor: dagger.Lazy<DnsInteractor>
    @Inject
    lateinit var preferenceRepository: dagger.Lazy<PreferenceRepository>
    @Inject @Named(DISPATCHER_COMPUTATION)
    lateinit var dispatcherIo: CoroutineDispatcher
    @Inject
    lateinit var exceptionHandler: CoroutineExceptionHandler

    init {
        App.instance.daggerComponent.inject(this)
    }

    private val mutableDomainIpLiveData = MutableLiveData<Set<DomainIpEntity>>()
    val domainIpLiveData: LiveData<Set<DomainIpEntity>> get() = mutableDomainIpLiveData

    fun resolveDomain(domain: String): Set<String> =
        dnsInteractor.get().resolveDomain(domain)

    fun reverseResolve(ip: String): String =
        dnsInteractor.get().reverseResolve(ip)

    @ObsoleteCoroutinesApi
    fun getDomainIps(
        unlockHostsStr: String,
        unlockIPsStr: String,
        pleaseWaitMessage: String,
        wrongDomainIpMessage: String
    ) {
        viewModelScope.launch(
            dispatcherIo + CoroutineName("getDomainIps") + exceptionHandler
        ) {

            val domainIps = getDomainIpsFromPreferences(unlockHostsStr, unlockIPsStr, pleaseWaitMessage)

            mutableDomainIpLiveData.postValue(domainIps)

            mutableDomainIpLiveData.postValue(
                dnsInteractor.get().resolveDomainOrIp(domainIps)
                    .map {
                        replacePleaseWaitMessage(it, pleaseWaitMessage, wrongDomainIpMessage)
                    }.toSet()
            )

        }
    }

    private fun getDomainIpsFromPreferences(
        unlockHostsStr: String,
        unlockIPsStr: String,
        pleaseWaitMessage: String
    ): Set<DomainIpEntity> {
        val domainIps = hashSetOf<DomainIpEntity>()

        val hosts = preferenceRepository.get().getStringSetPreference(unlockHostsStr)
        val ips: Set<String> = preferenceRepository.get().getStringSetPreference(unlockIPsStr)

        for (host in hosts) {
            val domainClear = host.replace("#", "")
            domainIps.add(
                DomainEntity(
                    domainClear,
                    setOf(pleaseWaitMessage),
                    !host.trim { it <= ' ' }.startsWith("#")
                )
            )
        }

        for (ip in ips) {
            val ipClear = ip.replace("#", "")
            domainIps.add(
                IpEntity(
                    ipClear,
                    pleaseWaitMessage,
                    !ip.trim { it <= ' ' }.startsWith("#")
                )
            )
        }

        return domainIps
    }

    private fun replacePleaseWaitMessage(
        domainIp: DomainIpEntity,
        pleaseWaitMessage: String,
        wrongDomainIpMessage: String
    ): DomainIpEntity =
        when(domainIp) {
            is DomainEntity -> {
                if (pleaseWaitMessage == domainIp.ips.firstOrNull() ?: pleaseWaitMessage) {
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

    fun saveDomainIpsToPreferences(ipsToUnlock: Set<String>, settingsKey: String): Boolean {
        val ips = preferenceRepository.get().getStringSetPreference(settingsKey)
        return if (ips.size == ipsToUnlock.size && ips.containsAll(ipsToUnlock)) {
            false
        } else {
            preferenceRepository.get().setStringSetPreference(settingsKey, ipsToUnlock)
            true
        }
    }

    fun deleteDomainIpFromPreferences(
        domainIp: DomainIpEntity,
        unlockHostsStr: String,
        unlockIPsStr: String
    ) {
        val preferences = preferenceRepository.get()
        if (domainIp is IpEntity) {
            val ipSet = preferences.getStringSetPreference(unlockIPsStr)
            if (domainIp.isActive) {
                ipSet.remove(domainIp.ip)
            } else {
                ipSet.remove("#" + domainIp.ip)
            }
            preferences.setStringSetPreference(unlockIPsStr, ipSet)
        } else if (domainIp is DomainEntity) {
            val hostSet = preferences.getStringSetPreference(unlockHostsStr)
            if (domainIp.isActive) {
                hostSet.remove(domainIp.domain)
            } else {
                hostSet.remove("#" + domainIp.domain)
            }
            preferences.setStringSetPreference(unlockHostsStr, hostSet)
        }
    }

    fun saveDomainActiveInPreferences(oldDomain: String, active: Boolean, unlockHostsStr: String) {
        val hostsSet = preferenceRepository.get().getStringSetPreference(unlockHostsStr)
        if (active) {
            hostsSet.remove("#$oldDomain")
            hostsSet.add(oldDomain.replace("#", ""))
        } else {
            hostsSet.remove(oldDomain)
            hostsSet.add("#$oldDomain")
        }
        preferenceRepository.get().setStringSetPreference(unlockHostsStr, hostsSet)
    }

    fun saveIpActiveInPreferences(oldIp: String, active: Boolean, unlockIPsStr: String) {
        val ipsSet = preferenceRepository.get().getStringSetPreference(unlockIPsStr)
        if (active) {
            ipsSet.remove("#$oldIp")
            ipsSet.add(oldIp.replace("#", ""))
        } else {
            ipsSet.remove(oldIp)
            ipsSet.add("#$oldIp")
        }
        preferenceRepository.get().setStringSetPreference(unlockIPsStr, ipsSet)
    }

    fun addDomainToPreferences(domain: String, unlockHostsStr: String) {
        val hostsSet = preferenceRepository.get().getStringSetPreference(unlockHostsStr)
        hostsSet.add(domain)
        preferenceRepository.get().setStringSetPreference(unlockHostsStr, hostsSet)
    }

    fun addIpToPreferences(ip: String, unlockIPsStr: String) {
        val ipsSet = preferenceRepository.get().getStringSetPreference(unlockIPsStr)
        ipsSet.add(ip)
        preferenceRepository.get().setStringSetPreference(unlockIPsStr, ipsSet)
    }

    fun replaceDomainInPreferences(domain: String, oldDomain: String, unlockHostsStr: String) {
        val hostsSet = preferenceRepository.get().getStringSetPreference(unlockHostsStr)
        hostsSet.remove(oldDomain)
        hostsSet.add(domain)
        preferenceRepository.get().setStringSetPreference(unlockHostsStr, hostsSet)
    }

    fun replaceIpInPreferences(ip: String, oldIp: String, unlockIPsStr: String) {
        val ipsSet = preferenceRepository.get().getStringSetPreference(unlockIPsStr)
        ipsSet.remove(oldIp)
        ipsSet.add(ip)
        preferenceRepository.get().setStringSetPreference(unlockIPsStr, ipsSet)
    }
}
