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

package pan.alexander.tordnscrypt.domain.bridges

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.utils.Constants
import pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX_NO_CAPTURING
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import java.math.BigInteger
import java.net.InetAddress
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named


class BridgesCountriesInteractor @Inject constructor(
    private val bridgesCountriesRepository: BridgesCountriesRepository,
    @Named(CoroutinesModule.DISPATCHER_IO) private val dispatcherIo: CoroutineDispatcher
) {

    private val bridgePatternIPv4 =
        Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d+)\\b")

    private val bridgePatternIPv6 = Pattern.compile("\\[($IPv6_REGEX_NO_CAPTURING)]:(\\d+)\\b")

    private val bridgeCountries = MutableSharedFlow<BridgeCountryData>()

    fun observeBridgeCountries() = bridgeCountries.asSharedFlow()

    suspend fun searchBridgeCountries(bridges: List<String>) = withContext(dispatcherIo) {

        try {

            ensureActive()

            val bridgesIPv4 = mutableListOf<String>()
            val bridgesIPv6 = mutableListOf<String>()
            bridges.forEach {
                if (it.isIPv6Bridge()) {
                    bridgesIPv6.add(it)
                } else {
                    bridgesIPv4.add(it)
                }
            }

            if (bridgesIPv4.isNotEmpty()) {
                searchIPv4BridgeCountries(bridgesIPv4)
            }
            if (bridgesIPv6.isNotEmpty()) {
                searchIPv6BridgeCountries(bridgesIPv6)
            }

        } catch (ignored: CancellationException) {
        } catch (e: java.lang.Exception) {
            loge("BridgesToCountriesInteractor searchBridgeCountries", e)
        }
    }

    private suspend fun searchIPv4BridgeCountries(bridges: List<String>) = coroutineScope {
        val bridgeHashToAddresses = convertIpv4Bridges(bridges).sortedBy {
            it.address
        }.toMutableList()

        bridgesCountriesRepository.getGeoipFile().forEachLine { line ->

            ensureActive()

            if (line.isNotBlank() && !line.startsWith("#")) {
                tryParseIpv4RangeToCountry(line)?.let { rangeToCountry ->

                    getBridgesIPv4WithinRange(
                        rangeToCountry,
                        bridgeHashToAddresses
                    ).takeIf { it.isNotEmpty() }?.forEach { bridgeToCountry ->
                            launch { bridgeCountries.emit(bridgeToCountry) }
                            bridgeHashToAddresses.removeAll { bridgeToCountry.bridgeHash == it.hash }
                        }
                }
            }

            if (bridgeHashToAddresses.isEmpty()) {
                return@forEachLine
            }
        }
    }

    private suspend fun searchIPv6BridgeCountries(bridges: List<String>) = coroutineScope {
        val bridgeHashToAddresses = convertIPv6Bridges(bridges).sortedBy {
            it.address
        }.toMutableList()

        bridgesCountriesRepository.getGeoip6File().forEachLine { line ->

            ensureActive()

            if (line.isNotBlank() && !line.startsWith("#")) {
                tryParseIpv6RangeToCountry(line)?.let { rangeToCountry ->

                    getBridgesIPv6WithinRange(
                        rangeToCountry,
                        bridgeHashToAddresses
                    ).takeIf { it.isNotEmpty() }?.forEach { bridgeToCountry ->
                            launch { bridgeCountries.emit(bridgeToCountry) }
                            bridgeHashToAddresses.removeAll { bridgeToCountry.bridgeHash == it.hash }
                        }
                }
            }

            if (bridgeHashToAddresses.isEmpty()) {
                return@forEachLine
            }
        }
    }

    private fun getBridgesIPv4WithinRange(
        rangeWithCountry: Ipv4RangeToCountry, bridges: List<BridgeIPv4HashToAddress>
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

    private fun getBridgesIPv6WithinRange(
        rangeWithCountry: Ipv6RangeToCountry, bridges: List<BridgeIPv6HashToAddress>
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

    private fun getBridgeIpIPv4(bridgeLine: String): String {
        val matcher = bridgePatternIPv4.matcher(bridgeLine)

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

    private fun getBridgeIpIPv6(bridgeLine: String): String {
        val matcher = bridgePatternIPv6.matcher(bridgeLine)

        if (matcher.find()) {
            val ip = matcher.group(1)
            val port = matcher.group(2)

            if (ip != null && port != null) {
                return ip
            }
        } else {
            loge("BridgesCountriesInteractor It looks like the bridge $bridgeLine is not valid")
        }

        return ""
    }

    private fun tryParseIpv4RangeToCountry(line: String): Ipv4RangeToCountry? = try {
        line.split(",").let { rangesWithCountry ->
            Ipv4RangeToCountry(
                rangesWithCountry[0].toLong(), rangesWithCountry[1].toLong(), rangesWithCountry[2]
            )
        }
    } catch (e: Exception) {
        loge("BridgesToCountriesInteractor tryParseIpRangeToCountry($line)", e)
        null
    }

    private fun tryParseIpv6RangeToCountry(line: String): Ipv6RangeToCountry? = try {
        line.split(",").let { rangesWithCountry ->
            Ipv6RangeToCountry(
                ipv6ToBigInteger(InetAddress.getByName(rangesWithCountry[0]).address),
                ipv6ToBigInteger(InetAddress.getByName(rangesWithCountry[1]).address),
                rangesWithCountry[2]
            )
        }
    } catch (e: Exception) {
        loge("BridgesToCountriesInteractor tryParseIpRangeToCountry($line)", e)
        null
    }

    private fun convertIpv4Bridges(bridges: List<String>): HashSet<BridgeIPv4HashToAddress> {

        val result = hashSetOf<BridgeIPv4HashToAddress>()

        for (bridge in bridges) {
            val bridgeIp = getBridgeIpIPv4(bridge)

            if (bridgeIp.isNotEmpty()) {
                val bridgeAddress = ipv4toLong(InetAddress.getByName(bridgeIp).address)
                result += BridgeIPv4HashToAddress(bridge.hashCode(), bridgeAddress)
            }
        }

        return result
    }

    private fun convertIPv6Bridges(bridges: List<String>): HashSet<BridgeIPv6HashToAddress> {

        val result = hashSetOf<BridgeIPv6HashToAddress>()

        for (bridge in bridges) {
            val bridgeIp = getBridgeIpIPv6(bridge)

            if (bridgeIp.isNotEmpty()) {
                val bridgeAddress = ipv6ToBigInteger(InetAddress.getByName(bridgeIp).address)
                result += BridgeIPv6HashToAddress(bridge.hashCode(), bridgeAddress)
            }
        }

        return result
    }

    private fun ipv4toLong(bytes: ByteArray): Long {
        var result = 0L
        for (i in bytes.indices) {
            result = result shl 8 or (bytes[i].toLong() and 0xff)
        }
        return result
    }

    private fun ipv6ToBigInteger(bytes: ByteArray): BigInteger {
        return BigInteger(1, bytes)
    }

    private fun String.isIPv6Bridge() = contains("[") && contains("]")

}
