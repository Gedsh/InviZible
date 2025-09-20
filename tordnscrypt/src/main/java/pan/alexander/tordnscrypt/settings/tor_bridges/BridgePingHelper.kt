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

package pan.alexander.tordnscrypt.settings.tor_bridges

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.domain.bridges.DefaultVanillaBridgeInteractor
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_USE_IPV6
import pan.alexander.tordnscrypt.vpn.VpnUtils
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Named

@ExperimentalCoroutinesApi
class BridgePingHelper @Inject constructor(
    private val context: Context,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher,
    private val defaultVanillaBridgeInteractor: DefaultVanillaBridgeInteractor,
    @Named(DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: SharedPreferences
) {

    private val webTunnelBridgePattern by lazy {
        Pattern.compile("^webtunnel +(.+:\\d+)(?: +\\w+)? +url=(http(s)?://[\\w.:-]+)(/[\\w.-]+)*/?")
    }

    private val meekLiteBridgePattern by lazy {
        Pattern.compile("^meek_lite +(.+:\\d+)(?: +\\w+)? +url=https://[\\w./-]+ +front=([\\w./-]+)(?: +utls=\\w+)?")
    }

    private val snowFlakeBridgePattern by lazy {
        Pattern.compile("^snowflake +(.+:\\d+)(?: +\\w+)? ")
    }

    private val conjureBridgePattern by lazy {
        Pattern.compile("^conjure +(.+:\\d+)(?: +\\w+)? ")
    }

    suspend fun getRealIPFromWebTunnelBridges(
        bridges: List<ObfsBridge>,
        bridgesMatcherMap: ConcurrentHashMap<Int, Int>
    ) = try {
        withContext(dispatcherIo) {
            val bridgesToMeasure = mutableListOf<String>()
            for (bridge in bridges) {

                val matcher = webTunnelBridgePattern.matcher(bridge.bridge)
                if (matcher.find()) {
                    val ipWithPort = matcher.group(1) ?: continue
                    val url = matcher.group(2) ?: continue
                    val domain = url.replace(Regex("http(s)?://"), "")
                    val port = if (domain.contains(":")) {
                        domain.substringAfter(":").toIntOrNull() ?: 443
                    } else if (url.startsWith("https")) {
                        443
                    } else {
                        80
                    }
                    val ip = getWorkingIp(domain.removeSuffix(":$port"), port)

                    ensureActive()
                    if (ip.isEmpty()) {
                        continue
                    }

                    val address = getAddress(ip, port)
                    val bridgeLine = bridge.bridge.replace(ipWithPort, address)
                    bridgesToMeasure.add(bridgeLine)
                    bridgesMatcherMap[bridgeLine.hashCode()] =
                        bridge.bridge.hashCode()
                }
            }
            bridgesToMeasure
        }
    } catch (ignored: Exception) {
        emptyList()
    }

    suspend fun getRealIPFromMeekLiteBridges(
        bridges: List<ObfsBridge>,
        bridgesMatcherMap: ConcurrentHashMap<Int, Int>
    ) = try {
        withContext(dispatcherIo) {
            val bridgesToMeasure = mutableListOf<String>()
            for (bridge in bridges) {

                val matcher = meekLiteBridgePattern.matcher(bridge.bridge)
                if (matcher.find()) {
                    val ipWithPort = matcher.group(1) ?: continue
                    val domain = matcher.group(2) ?: continue
                    val port = 443
                    val ip = getWorkingIp(domain, port)

                    ensureActive()
                    if (ip.isEmpty()) {
                        continue
                    }

                    val address = getAddress(ip, port)
                    val bridgeLine = bridge.bridge.replace(ipWithPort, address)
                    bridgesToMeasure.add(bridgeLine)
                    bridgesMatcherMap[bridgeLine.hashCode()] =
                        bridge.bridge.hashCode()
                }
            }
            bridgesToMeasure
        }
    } catch (ignored: Exception) {
        emptyList()
    }

    suspend fun getRealIPFromSnowFlakeBridges(
        bridges: List<ObfsBridge>,
        bridgesMatcherMap: ConcurrentHashMap<Int, Int>
    ) = try {
        withContext(dispatcherIo) {
            val bridgesToMeasure = mutableListOf<String>()
            for (bridge in bridges) {

                val matcher = snowFlakeBridgePattern.matcher(bridge.bridge)
                if (matcher.find()) {
                    val ipWithPort = matcher.group(1) ?: continue
                    val front = "front="
                    val fronts = "fronts="
                    val sqs = "sqsqueue=https://"
                    val domains = if (bridge.bridge.contains(fronts)) {
                        bridge.bridge.split(Regex(" +"))
                            .first { it.contains(fronts) }
                            .removePrefix(fronts)
                            .split(",")
                    } else if (bridge.bridge.contains(front)) {
                        bridge.bridge.split(Regex(" +"))
                            .first { it.contains(front) }
                            .removePrefix(front)
                            .let { listOf(it) }
                    } else if (bridge.bridge.contains(sqs)) {
                        bridge.bridge.split(Regex(" +"))
                            .first { it.contains(sqs) }
                            .removePrefix(sqs)
                            .split("/")
                            .firstOrNull()
                            ?.let { listOf(it) }
                            ?: emptyList()
                    } else {
                        emptyList()
                    }

                    if (domains.isEmpty()) {
                        continue
                    }

                    var ip = ""
                    val port = 443
                    for (domain in domains.shuffled()) {
                        ip = getWorkingIp(domain, port)
                        if (ip.isNotEmpty()) {
                            break
                        }
                    }

                    ensureActive()
                    if (ip.isEmpty()) {
                        continue
                    }

                    val address = getAddress(ip, port)
                    val bridgeLine = bridge.bridge.replace(ipWithPort, address)
                    bridgesToMeasure.add(bridgeLine)
                    bridgesMatcherMap[bridgeLine.hashCode()] =
                        bridge.bridge.hashCode()
                }
            }
            bridgesToMeasure
        }
    } catch (ignored: Exception) {
        emptyList()
    }

    suspend fun getRealIPFromConjureBridges(
        bridges: List<ObfsBridge>,
        bridgesMatcherMap: ConcurrentHashMap<Int, Int>
    ) = try {
        withContext(dispatcherIo) {
            val bridgesToMeasure = mutableListOf<String>()
            for (bridge in bridges) {

                val matcher = conjureBridgePattern.matcher(bridge.bridge)
                if (matcher.find()) {
                    val ipWithPort = matcher.group(1) ?: continue
                    val front = "front="
                    val fronts = "fronts="
                    val domains = if (bridge.bridge.contains(fronts)) {
                        bridge.bridge.split(Regex(" +"))
                            .first { it.contains(fronts) }
                            .removePrefix(fronts)
                            .split(",")
                    } else if (bridge.bridge.contains(front)) {
                        bridge.bridge.split(Regex(" +"))
                            .first { it.contains(front) }
                            .removePrefix(front)
                            .let { listOf(it) }
                    } else {
                        emptyList()
                    }

                    if (domains.isEmpty()) {
                        continue
                    }

                    var ip = ""
                    val port = 443
                    for (domain in domains.shuffled()) {
                        ip = getWorkingIp(domain, port)
                        if (ip.isNotEmpty()) {
                            break
                        }
                    }

                    ensureActive()
                    if (ip.isEmpty()) {
                        continue
                    }

                    val address = getAddress(ip, port)
                    val bridgeLine = bridge.bridge.replace(ipWithPort, address)
                    bridgesToMeasure.add(bridgeLine)
                    bridgesMatcherMap[bridgeLine.hashCode()] =
                        bridge.bridge.hashCode()
                }
            }
            bridgesToMeasure
        }
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun getWorkingIp(domain: String, port: Int) = try {
        withContext(dispatcherIo) {
            InetAddress.getAllByName(domain)
        }.mapNotNull { it.hostAddress }
    } catch (_: Exception) {
        emptyList()
    }.filter {
        !VpnUtils.isIpInLanRange(it) && (isUseIPv6() || !it.isIPv6Address())
    }.let { ips ->
        coroutineScope {
            ips.map { ip ->
                async {
                    try {
                        if (isAddressReachable(ip, port)) ip else ""
                    } catch (_: Exception) {
                        ""
                    }
                }
            }.awaitAll()
                .filter { it.isNotEmpty() }
                .minByOrNull { it.isIPv6Address() } ?: ips.firstOrNull() ?: ""
        }
    }


    private suspend fun isAddressReachable(ip: String, port: Int): Boolean =
        defaultVanillaBridgeInteractor.isAddressReachable(ip, port)

    private fun getAddress(ip: String, port: Int) = if (ip.isIPv6Address()) {
        "[$ip]:$port"
    } else {
        "$ip:$port"
    }

    private fun String.isIPv6Address() = contains(":")

    private fun isUseIPv6() = defaultPreferences.getBoolean(TOR_USE_IPV6, true)

    fun isConnected(): Boolean = NetworkChecker.isNetworkAvailable(context)
}
