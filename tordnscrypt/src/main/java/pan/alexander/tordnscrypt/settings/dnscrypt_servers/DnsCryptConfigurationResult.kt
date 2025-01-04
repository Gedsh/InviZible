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

package pan.alexander.tordnscrypt.settings.dnscrypt_servers

import pan.alexander.tordnscrypt.settings.dnscrypt_relays.DnsServerRelay

sealed interface DnsCryptConfigurationResult {
    data object Loading: DnsCryptConfigurationResult
    data class DnsCryptProxyToml(val lines: List<String>): DnsCryptConfigurationResult
    data class DnsCryptServers(val servers: List<String>): DnsCryptConfigurationResult
    data class DnsCryptRoutes(val routes: List<DnsServerRelay>): DnsCryptConfigurationResult
    data class DnsCryptPublicResolvers(val resolvers: List<DnsCryptResolver>): DnsCryptConfigurationResult
    data class DnsCryptOwnResolvers(val resolvers: List<DnsCryptResolver>): DnsCryptConfigurationResult
    data class DnsCryptOdohResolvers(val resolvers: List<DnsCryptResolver>): DnsCryptConfigurationResult
    data object Finished: DnsCryptConfigurationResult
    data object Undefined: DnsCryptConfigurationResult
}
