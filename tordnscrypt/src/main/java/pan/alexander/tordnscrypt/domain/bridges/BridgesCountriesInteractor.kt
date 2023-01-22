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

package pan.alexander.tordnscrypt.domain.bridges

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.utils.Constants
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.net.InetAddress
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named


class BridgesCountriesInteractor @Inject constructor(
    private val bridgesCountriesRepository: BridgesCountriesRepository,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher
) {

    private val bridgePattern =
        Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d+)\\b")

    private val bridgeCountries = MutableSharedFlow<BridgeCountryData>()

    fun observeBridgeCountries() = bridgeCountries.asSharedFlow()

    suspend fun searchBridgeCountries(bridges: List<String>) = withContext(dispatcherIo) {

        try {

            ensureActive()

            val bridgeHashToAddresses = convertBridges(bridges).sortedBy {
                it.address
            }.toMutableList()

            bridgesCountriesRepository.getGeoipFile().forEachLine { line ->

                ensureActive()

                if (line.isNotBlank() && !line.startsWith("#")) {
                    tryParseIpRangeToCountry(line)?.let { rangeToCountry ->

                        getBridgesWithinRange(rangeToCountry, bridgeHashToAddresses)
                            .takeIf { it.isNotEmpty() }
                            ?.forEach { bridgeToCountry ->
                                launch { bridgeCountries.emit(bridgeToCountry) }
                                bridgeHashToAddresses.removeAll { bridgeToCountry.bridgeHash == it.hash }
                            }
                    }
                }

                if (bridgeHashToAddresses.isEmpty()) {
                    return@forEachLine
                }
            }
        } catch (ignored: CancellationException) {
        } catch (e: java.lang.Exception) {
            loge("BridgesToCountriesInteractor searchBridgeCountries", e)
        }
    }

    private fun getBridgesWithinRange(
        rangeWithCountry: IpRangeToCountry,
        bridges: List<BridgeHashToAddress>
    ): List<BridgeCountryData> {

        val result = arrayListOf<BridgeCountryData>()

        if (bridges.isEmpty() || bridges.last().address < rangeWithCountry.ipRangeStart) {
            return result
        }

        for (bridge in bridges) {
            if (bridge.address >= rangeWithCountry.ipRangeStart && bridge.address <= rangeWithCountry.ipRangeEnd) {
                result += BridgeCountryData(bridge.hash, rangeWithCountry.country)
            }
        }

        return result
    }

    private fun getBridgeIp(bridgeLine: String): String {
        val matcher = bridgePattern.matcher(bridgeLine)

        if (matcher.find()) {
            val ip = matcher.group(1)
            val port = matcher.group(2)

            if (ip != null && port != null && ip.matches(Regex(Constants.IPv4_REGEX))) {
                return ip
            }
        } else {
            loge("BridgesCountriesInteractor It looks like the bridge $bridgeLine is not valid")
        }

        return ""
    }

    private fun tryParseIpRangeToCountry(line: String): IpRangeToCountry? =
        try {
            line.split(",").let { rangesWithCountry ->
                IpRangeToCountry(
                    rangesWithCountry[0].toLong(),
                    rangesWithCountry[1].toLong(),
                    rangesWithCountry[2]
                )
            }
        } catch (e: Exception) {
            loge("BridgesToCountriesInteractor tryParseIpRangeToCountry($line)", e)
            null
        }

    private fun convertBridges(bridges: List<String>): HashSet<BridgeHashToAddress> {

        val result = hashSetOf<BridgeHashToAddress>()

        for (bridge in bridges) {

            val bridgeIp = getBridgeIp(bridge)

            if (bridgeIp.isNotEmpty()) {
                val bridgeAddress = pack(InetAddress.getByName(bridgeIp).address)
                result += BridgeHashToAddress(bridge.hashCode(), bridgeAddress)
            }
        }

        return result
    }

    private fun pack(bytes: ByteArray): Long {
        var result = 0L
        for (i in bytes.indices) {
            result = result shl 8 or (bytes[i].toLong() and 0xff)
        }
        return result
    }

    data class BridgeHashToAddress(
        val hash: Int,
        val address: Long
    )
}
