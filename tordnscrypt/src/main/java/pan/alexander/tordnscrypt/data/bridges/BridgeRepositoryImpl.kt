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

package pan.alexander.tordnscrypt.data.bridges

import org.json.JSONObject
import pan.alexander.tordnscrypt.domain.bridges.BridgeRepository
import pan.alexander.tordnscrypt.utils.connectionchecker.SocketInternetChecker
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Provider

class BridgeRepositoryImpl @Inject constructor(
    private val socketInternetChecker: Provider<SocketInternetChecker>,
    private val bridgeDataSource: BridgeDataSource
) : BridgeRepository {

    private val bridgePattern =
        Pattern.compile("([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}):(\\d+)")

    override fun getTimeout(bridgeLine: String): Int =
        try {
            tryGetTimeout(bridgeLine)
        } catch (ignored: Exception) {
            SocketInternetChecker.NO_CONNECTION
        }

    private fun tryGetTimeout(bridgeLine: String): Int {
        val matcher = bridgePattern.matcher(bridgeLine)

        return if (matcher.find()) {
            val ip = matcher.group(1)
            val port = matcher.group(2)

            if (ip != null && ip.isNotBlank()
                && port != null && port.isNotBlank()
            ) {
                socketInternetChecker.get()
                    .checkConnectionPing(ip, port.toInt(), "", 0)
            } else {
                SocketInternetChecker.NO_CONNECTION
            }
        } else {
            SocketInternetChecker.NO_CONNECTION
        }
    }

    override fun getRelaysWithFingerprintAndAddress(
        proxyAddress: String,
        proxyPort: Int
    ): List<RelayAddressFingerprint> {

        val relays = mutableListOf<RelayAddressFingerprint>()

        bridgeDataSource.getRelaysWithFingerprintAndAddress(proxyAddress, proxyPort)
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
