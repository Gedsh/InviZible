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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.tor_ips

import pan.alexander.tordnscrypt.settings.tor_ips.DomainIpEntity

interface TorIpsInteractor {
    fun getDomainIpsFromPreferences(
        unlockHostsStr: String,
        unlockIPsStr: String,
        pleaseWaitMessage: String
    ): Set<DomainIpEntity>

    fun saveDomainIpsToPreferences(
        ipsToUnlock: Set<String>,
        settingsKey: String
    ): Boolean

    fun deleteDomainIpFromPreferences(
        domainIp: DomainIpEntity,
        unlockHostsStr: String,
        unlockIPsStr: String
    )

    fun saveDomainActiveInPreferences(
        oldDomain: String,
        active: Boolean,
        unlockHostsStr: String
    )

    fun saveIpActiveInPreferences(
        oldIp: String,
        active: Boolean,
        unlockIPsStr: String
    )

    fun addDomainToPreferences(
        domain: String,
        unlockHostsStr: String
    )

    fun addIpToPreferences(
        ip: String,
        unlockIPsStr: String
    )

    fun replaceDomainInPreferences(
        domain: String,
        oldDomain: String,
        unlockHostsStr: String
    )

    fun replaceIpInPreferences(
        ip: String,
        oldIp: String,
        unlockIPsStr: String
    )
}
