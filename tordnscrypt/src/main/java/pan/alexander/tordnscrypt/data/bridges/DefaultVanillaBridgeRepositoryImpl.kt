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

package pan.alexander.tordnscrypt.data.bridges

import android.content.SharedPreferences
import org.json.JSONObject
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.domain.bridges.DefaultVanillaBridgeRepository
import pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX
import pan.alexander.tordnscrypt.utils.Constants.NUMBER_REGEX
import pan.alexander.tordnscrypt.utils.connectionchecker.SocketInternetChecker
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY_ADDRESS
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

class DefaultVanillaBridgeRepositoryImpl @Inject constructor(
    private val socketInternetChecker: Provider<SocketInternetChecker>,
    private val bridgeDataSource: DefaultVanillaBridgeDataSource,
    @Named(DEFAULT_PREFERENCES_NAME) private val defaultPreferences: SharedPreferences
) : DefaultVanillaBridgeRepository {

    private val bridgePattern =
        Pattern.compile("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}):(\\d+)\\b")

    override fun getTimeout(bridgeLine: String): Int =
        try {
            tryGetTimeout(bridgeLine)
        } catch (ignored: Exception) {
            SocketInternetChecker.NO_CONNECTION
        }

    private fun tryGetTimeout(bridgeLine: String): Int {
        val matcher = bridgePattern.matcher(bridgeLine)

        if (matcher.find()) {
            val ip = matcher.group(1)
            val port = matcher.group(2)

            if (ip != null && port != null && isBridgeCorrect(ip, port)) {
                if (isTorOutboundProxyEnabled()) {
                    val proxyAddress = getTorOutboundProxyAddress()?.split(":") ?: emptyList()
                    if (proxyAddress.size == 2
                        && proxyAddress[0].trim().matches(Regex(IPv4_REGEX))
                        && proxyAddress[1].trim().matches(Regex(NUMBER_REGEX))
                    ) {
                        return checkTimeoutViaProxy(
                            ip,
                            port,
                            proxyAddress[0].trim(),
                            proxyAddress[1].trim().toInt()
                        )
                    }
                } else {
                    return checkTimeoutDirectly(ip, port)
                }
            }
        }

        return SocketInternetChecker.NO_CONNECTION
    }

    private fun isBridgeCorrect(ip: String, port: String) =
        ip.matches(Regex(IPv4_REGEX)) && port.matches(Regex(NUMBER_REGEX))

    private fun isTorOutboundProxyEnabled() =
        defaultPreferences.getBoolean(TOR_OUTBOUND_PROXY, false)

    private fun getTorOutboundProxyAddress() =
        defaultPreferences.getString(TOR_OUTBOUND_PROXY_ADDRESS, "")

    private fun checkTimeoutDirectly(ip: String, port: String) =
        socketInternetChecker.get()
            .checkConnectionPing(ip, port.toInt(), "", 0)

    private fun checkTimeoutViaProxy(
        ip: String,
        port: String,
        proxyAddress: String,
        proxyPort: Int
    ) = socketInternetChecker.get()
        .checkConnectionPing(ip, port.toInt(), proxyAddress, proxyPort)

    override suspend fun getRelaysWithFingerprintAndAddress(): List<RelayAddressFingerprint> {

        val relays = mutableListOf<RelayAddressFingerprint>()

        bridgeDataSource.getRelaysWithFingerprintAndAddress()
            .forEach {
                try {
                    if (it.contains("fingerprint")) {
                        val relay = mapJsonToRelay(JSONObject(it))
                        relays.add(relay)
                    }
                } catch (e: Exception) {
                    logw("BridgeRepository getRelaysWithFingerprintAndAddress", e)
                }
            }

        return relays
    }

    private fun mapJsonToRelay(json: JSONObject): RelayAddressFingerprint {

        val bridgeLine = json.getJSONArray("or_addresses").getString(0)
        val fingerprint = json.getString("fingerprint")

        val matcher = bridgePattern.matcher(bridgeLine)

        if (matcher.find()) {
            val ip = matcher.group(1)
            val port = matcher.group(2)

            if (ip != null && ip.isNotBlank()
                && port != null && port.isNotBlank()
            ) {
                return RelayAddressFingerprint(
                    address = ip,
                    port = port,
                    fingerprint = fingerprint
                )
            }
        }

        throw IllegalArgumentException("JSON $json is not valid relay")

    }

}
