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

package pan.alexander.tordnscrypt.data.modules_configuration

import android.content.SharedPreferences
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX_WITH_PORT
import pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.tordnscrypt.utils.parsers.DnsCryptConfigurationParser
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_OUTBOUND_PROXY_PORT
import javax.inject.Inject
import javax.inject.Named

class DnsCryptConfigurationDataSource @Inject constructor(
    private val dnsCryptConfigurationParser: DnsCryptConfigurationParser,
    @Named(DEFAULT_PREFERENCES_NAME) private val defaultPreferences: SharedPreferences
) {
    fun getDnsCryptOutboundProxyAddress(): String =
        dnsCryptConfigurationParser.dnsCryptProxyToml
            .find {
                it.startsWith("proxy = 'socks5://") || it.startsWith("#proxy = 'socks5://")
            }?.removePrefix("#")
            ?.removeSurrounding("proxy = 'socks5://", "'")
            ?.takeIf {
                it.matches(Regex(IPv4_REGEX_WITH_PORT))
            } ?: "$LOOPBACK_ADDRESS:${getDnsCryptOutboundProxyPort()}"


    private fun getDnsCryptOutboundProxyPort() =
        defaultPreferences.getString(DNSCRYPT_OUTBOUND_PROXY_PORT, "") ?: ""
}
