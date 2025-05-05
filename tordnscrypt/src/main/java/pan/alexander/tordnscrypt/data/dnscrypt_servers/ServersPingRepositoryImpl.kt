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

package pan.alexander.tordnscrypt.data.dnscrypt_servers

import android.content.SharedPreferences
import pan.alexander.tordnscrypt.data.modules_configuration.DnsCryptConfigurationDataSource
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.domain.dnscrypt_servers.ServersPingRepository
import pan.alexander.tordnscrypt.utils.connectionchecker.SocketInternetChecker.Companion.NO_CONNECTION
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_OUTBOUND_PROXY
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named

class ServersPingRepositoryImpl @Inject constructor(
    private val serversPingDataSource: ServersPingDataSource,
    private val dnsCryptConfigurationDataSource: DnsCryptConfigurationDataSource,
    @Named(DEFAULT_PREFERENCES_NAME) private val defaultPreferences: SharedPreferences
) : ServersPingRepository {

    private val outboundProxyAddress = getDnsCryptOutboundProxyAddress()

    private val ipv4WithPortPattern =
        Pattern.compile("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}):(\\d+)\\b")

    override fun getTimeout(address: String): Int = try {
        tryGetTimeout(address)
    } catch (_: SocketTimeoutException) {
        NO_CONNECTION
    } catch (_: ConnectException) {
        NO_CONNECTION
    } catch (e: Exception) {
        loge("ServersPingRepositoryImpl getTimeout", e)
        NO_CONNECTION
    }

    private fun tryGetTimeout(address: String): Int {
        val (ip, port) = getIpToPort(address)
        if (ip.isEmpty() || port == 0) {
            return NO_CONNECTION
        }

        return if (isDnsCryptOutboundProxyEnabled()) {
            val outboundProxyIp = outboundProxyAddress.substring(0, outboundProxyAddress.indexOf(":"))
            val outboundProxyPort = outboundProxyAddress.substring(outboundProxyAddress.indexOf(":") + 1)
            serversPingDataSource.checkTimeoutViaProxy(
                ip,
                port,
                outboundProxyIp,
                outboundProxyPort.toInt()
            )
        } else {
            serversPingDataSource.checkTimeoutDirectly(ip, port)
        }

        return NO_CONNECTION
    }

    //Address example: 1.2.3.4:123 or [1:2:3:4]:123 or www.host.com:123
    private fun getIpToPort(address: String): Pair<String, Int> {
        var ip = ""
        var port = 0
        val ipv6Address = address.isIPv6Address()
        if (ipv6Address) {
            ip = address.substring(address.indexOf("[") + 1, address.indexOf("]"))
            port = address.substring(address.indexOf("]") + 2).toInt()
        } else {
            val matcher = ipv4WithPortPattern.matcher(address)
            if (matcher.find()) {
                ip = matcher.group(1) ?: ""
                port = matcher.group(2)?.toInt() ?: 0
            } else if (address.contains(":")) {
                val host = address.substring(0, address.indexOf(":"))
                port = address.substring(address.indexOf(":") + 1).toInt()
                ip = try {
                    InetAddress.getAllByName(host).filter {
                        !it.isLoopbackAddress && !it.isAnyLocalAddress
                    }.minByOrNull {
                        it?.hostAddress?.isIPv6Address() == false
                    }?.hostAddress ?: ""
                } catch (e: Exception) {
                    loge("ServersPingRepositoryImpl tryGetTimeout", e)
                    ""
                }
            }
        }
        return Pair(ip, port)
    }

    private fun String.isIPv6Address() = contains("[") && contains("]")

    private fun isDnsCryptOutboundProxyEnabled() =
        defaultPreferences.getBoolean(DNSCRYPT_OUTBOUND_PROXY, false)

    private fun getDnsCryptOutboundProxyAddress() =
        dnsCryptConfigurationDataSource.getDnsCryptOutboundProxyAddress()
}
