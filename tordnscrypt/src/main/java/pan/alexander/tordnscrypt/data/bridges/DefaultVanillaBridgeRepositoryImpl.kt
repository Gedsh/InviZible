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

package pan.alexander.tordnscrypt.data.bridges

import android.content.SharedPreferences
import org.json.JSONException
import org.json.JSONObject
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.domain.bridges.DefaultVanillaBridgeRepository
import pan.alexander.tordnscrypt.utils.Constants.*
import pan.alexander.tordnscrypt.utils.connectionchecker.SocketInternetChecker
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_PASS
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_USER
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY_ADDRESS
import java.lang.IllegalArgumentException
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import kotlin.Exception

private const val CONNECT_TIMEOUT_SEC = 1

class DefaultVanillaBridgeRepositoryImpl @Inject constructor(
    private val socketInternetChecker: Provider<SocketInternetChecker>,
    private val bridgeDataSource: DefaultVanillaBridgeDataSource,
    @Named(DEFAULT_PREFERENCES_NAME) private val defaultPreferences: SharedPreferences
) : DefaultVanillaBridgeRepository {

    private val bridgeIPv4Pattern =
        Pattern.compile("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}):(\\d+)\\b")
    private val bridgeIPv6Pattern =
        Pattern.compile("\\[($IPv6_REGEX_NO_CAPTURING)]:(\\d+)\\b")

    override fun getTimeout(bridgeLine: String): Int =
        try {
            tryGetTimeout(bridgeLine)
        } catch (ignored: Exception) {
            SocketInternetChecker.NO_CONNECTION
        }

    private fun tryGetTimeout(bridgeLine: String): Int {

        val bridgeIPv6 = bridgeLine.isIPv6Bridge()

        val matcher = if (bridgeIPv6) {
            bridgeIPv6Pattern.matcher(bridgeLine)
        } else {
            bridgeIPv4Pattern.matcher(bridgeLine)
        }

        if (matcher.find()) {
            val ip = matcher.group(1)
            val port = matcher.group(2)

            if (ip != null && port != null
                && (bridgeIPv6 && isBridgeIPv6Correct(port) || isBridgeIPv4Correct(ip, port))
            ) {
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
                            proxyAddress[1].trim().toInt(),
                            getTorProxyUser(),
                            getTorProxyPass()
                        )
                    }
                } else {
                    return checkTimeoutDirectly(ip, port)
                }
            }
        }

        return SocketInternetChecker.NO_CONNECTION
    }

    private fun isBridgeIPv4Correct(ip: String, port: String) =
        ip.matches(Regex(IPv4_REGEX)) && port.matches(Regex(NUMBER_REGEX))

    private fun isBridgeIPv6Correct(port: String) = port.matches(Regex(NUMBER_REGEX))

    private fun String.isIPv6Bridge() = contains("[") && contains("]")

    private fun isTorOutboundProxyEnabled() =
        defaultPreferences.getBoolean(TOR_OUTBOUND_PROXY, false)

    private fun getTorOutboundProxyAddress() =
        defaultPreferences.getString(TOR_OUTBOUND_PROXY_ADDRESS, "")

    private fun getTorProxyUser() = defaultPreferences.getString(PROXY_USER, "") ?: ""

    private fun getTorProxyPass() = defaultPreferences.getString(PROXY_PASS, "") ?: ""

    private fun checkTimeoutDirectly(ip: String, port: String) =
        socketInternetChecker.get()
            .checkConnectionPing(ip, port.toInt(), "", 0, "", "")

    private fun checkTimeoutViaProxy(
        ip: String,
        port: String,
        proxyAddress: String,
        proxyPort: Int,
        proxyUser: String,
        proxyPass: String,
    ) = socketInternetChecker.get()
        .checkConnectionPing(ip, port.toInt(), proxyAddress, proxyPort, proxyUser, proxyPass)

    override suspend fun getRelaysWithFingerprintAndAddress(
        allowIPv6Relays: Boolean
    ): List<RelayAddressFingerprint> {

        val relays = mutableListOf<RelayAddressFingerprint>()

        bridgeDataSource.getRelaysWithFingerprintAndAddress()
            .forEach {
                try {
                    if (it.contains("fingerprint")) {
                        val relay = mapJsonToRelay(JSONObject(it), allowIPv6Relays)
                        relays.addAll(relay)
                    }
                } catch (e: Exception) {
                    logw("BridgeRepository getRelaysWithFingerprintAndAddress", e)
                }
            }

        return relays
    }

    private fun mapJsonToRelay(
        json: JSONObject,
        allowIPv6Relays: Boolean
    ): List<RelayAddressFingerprint> {

        val relays = mutableListOf<RelayAddressFingerprint>()

        val relayIPv4Line = try {
            json.getJSONArray("or_addresses").getString(0)
        } catch (e: JSONException) {
            ""
        }
        val relayIPv6Line = try {
            json.getJSONArray("or_addresses").getString(1)
        } catch (e: JSONException) {
            ""
        }
        val fingerprint = json.getString("fingerprint")

        parseIPv4Relay(relayIPv4Line, fingerprint)?.let { relays.add(it) }

        if (allowIPv6Relays && relayIPv6Line.isIPv6Bridge()) {
            parseIPv6Relay(relayIPv6Line, fingerprint)?.let { relays.add(it) }
        }

        if (relays.isNotEmpty()) {
            return relays
        }

        throw IllegalArgumentException("JSON $json is not valid relay")

    }

    private fun parseIPv4Relay(relayLine: String, fingerprint: String): RelayAddressFingerprint? {
        val matcher = bridgeIPv4Pattern.matcher(relayLine)
        return mapToRelayAddressFingerprint(matcher, fingerprint)
    }

    private fun parseIPv6Relay(relayLine: String, fingerprint: String): RelayAddressFingerprint? {
        val matcher = bridgeIPv6Pattern.matcher(relayLine)
        return mapToRelayAddressFingerprint(matcher, fingerprint)
    }

    private fun mapToRelayAddressFingerprint(
        matcher: Matcher,
        fingerprint: String
    ): RelayAddressFingerprint? {
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
        return null
    }

    override fun isAddressReachable(ip: String, port: Int): Boolean = try {
        tryCheckAddress(ip, port, CONNECT_TIMEOUT_SEC, CONNECT_TIMEOUT_SEC)
    } catch (ignored: Exception) {
        false
    }

    @Suppress("SameParameterValue")
    private fun tryCheckAddress(
        ip: String,
        port: Int,
        connectTimeout: Int,
        reachableTimeout: Int
    ): Boolean = if (isTorOutboundProxyEnabled()) {
        val proxyAddress = getTorOutboundProxyAddress()?.split(":") ?: emptyList()
        if (proxyAddress.size == 2
            && proxyAddress[0].trim().matches(Regex(IPv4_REGEX))
            && proxyAddress[1].trim().matches(Regex(NUMBER_REGEX))
        ) {
            socketInternetChecker.get().checkConnectionAvailability(
                ip = ip,
                port = port,
                proxyAddress = proxyAddress[0].trim(),
                proxyPort = proxyAddress[1].trim().toInt(),
                proxyUser = getTorProxyUser(),
                proxyPass = getTorProxyPass(),
                connectTimeout = connectTimeout,
                reachableTimeout = reachableTimeout
            )
        } else {
            socketInternetChecker.get().checkConnectionAvailability(
                ip = ip,
                port = port,
                proxyAddress = "",
                proxyPort = 0,
                "",
                "",
                connectTimeout = connectTimeout,
                reachableTimeout = reachableTimeout
            )
        }
    } else {
        socketInternetChecker.get().checkConnectionAvailability(
            ip = ip,
            port = port,
            proxyAddress = "",
            proxyPort = 0,
            "",
            "",
            connectTimeout = connectTimeout,
            reachableTimeout = reachableTimeout
        )
    }
}
