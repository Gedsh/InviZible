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

package pan.alexander.tordnscrypt.domain.tor_ips

import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.settings.tor_ips.DomainEntity
import pan.alexander.tordnscrypt.settings.tor_ips.DomainIpEntity
import pan.alexander.tordnscrypt.settings.tor_ips.IpEntity
import javax.inject.Inject

class TorIpsInteractorImpl @Inject constructor(
    private val preferenceRepository: dagger.Lazy<PreferenceRepository>
): TorIpsInteractor {
    override fun getDomainIpsFromPreferences(
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

    override fun saveDomainIpsToPreferences(
        ipsToUnlock: Set<String>,
        settingsKey: String
    ): Boolean {
        val ips = preferenceRepository.get().getStringSetPreference(settingsKey)
        return if (ips.size == ipsToUnlock.size && ips.containsAll(ipsToUnlock)) {
            false
        } else {
            preferenceRepository.get().setStringSetPreference(settingsKey, ipsToUnlock)
            true
        }
    }

    override fun deleteDomainIpFromPreferences(
        domainIp: DomainIpEntity,
        unlockHostsStr: String,
        unlockIPsStr: String
    ) {
        val preferences = preferenceRepository.get()
        if (domainIp is IpEntity) {
            val ipSet = preferences.getStringSetPreference(unlockIPsStr)
            ipSet.remove(domainIp.ip)
            ipSet.remove("#${domainIp.ip}")
            preferences.setStringSetPreference(unlockIPsStr, ipSet)
        } else if (domainIp is DomainEntity) {
            val hostSet = preferences.getStringSetPreference(unlockHostsStr)
            hostSet.remove(domainIp.domain)
            hostSet.remove("#${domainIp.domain}")
            preferences.setStringSetPreference(unlockHostsStr, hostSet)
        }
    }

    override fun saveDomainActiveInPreferences(
        oldDomain: String,
        active: Boolean,
        unlockHostsStr: String
    ) {
        val hostsSet = preferenceRepository.get().getStringSetPreference(unlockHostsStr)
        hostsSet.remove(oldDomain)
        hostsSet.remove("#$oldDomain")
        if (active) {
            hostsSet.add(oldDomain.replace("#", ""))
        } else {
            hostsSet.add("#${oldDomain.replace("#", "")}")
        }
        preferenceRepository.get().setStringSetPreference(unlockHostsStr, hostsSet)
    }

    override fun saveIpActiveInPreferences(oldIp: String, active: Boolean, unlockIPsStr: String) {
        val ipsSet = preferenceRepository.get().getStringSetPreference(unlockIPsStr)
        ipsSet.remove(oldIp)
        ipsSet.remove("#$oldIp")
        if (active) {
            ipsSet.add(oldIp.replace("#", ""))
        } else {
            ipsSet.add("#${oldIp.replace("#", "")}")
        }
        preferenceRepository.get().setStringSetPreference(unlockIPsStr, ipsSet)
    }

    override fun addDomainToPreferences(domain: String, unlockHostsStr: String) {
        val hostsSet = preferenceRepository.get().getStringSetPreference(unlockHostsStr)
        hostsSet.add(domain)
        preferenceRepository.get().setStringSetPreference(unlockHostsStr, hostsSet)
    }

    override fun addIpToPreferences(ip: String, unlockIPsStr: String) {
        val ipsSet = preferenceRepository.get().getStringSetPreference(unlockIPsStr)
        ipsSet.add(ip)
        preferenceRepository.get().setStringSetPreference(unlockIPsStr, ipsSet)
    }

    override fun replaceDomainInPreferences(
        domain: String,
        oldDomain: String,
        unlockHostsStr: String
    ) {
        val hostsSet = preferenceRepository.get().getStringSetPreference(unlockHostsStr)
        hostsSet.remove(oldDomain)
        hostsSet.add(domain)
        preferenceRepository.get().setStringSetPreference(unlockHostsStr, hostsSet)
    }

    override fun replaceIpInPreferences(ip: String, oldIp: String, unlockIPsStr: String) {
        val ipsSet = preferenceRepository.get().getStringSetPreference(unlockIPsStr)
        ipsSet.remove(oldIp)
        ipsSet.add(ip)
        preferenceRepository.get().setStringSetPreference(unlockIPsStr, ipsSet)
    }
}
