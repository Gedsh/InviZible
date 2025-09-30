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

import pan.alexander.tordnscrypt.utils.Constants.HOST_NAME_REGEX
import pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX_NO_BOUNDS
import pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX_NO_BOUNDS
import pan.alexander.tordnscrypt.utils.Constants.URL_REGEX
import java.util.regex.Pattern
import javax.inject.Inject

private const val ipv4BridgeBase = "(\\d{1,3}\\.){3}\\d{1,3}:\\d+( +\\w+)?"
private const val ipv6BridgeBase = "\\[$IPv6_REGEX_NO_BOUNDS]:\\d+( +\\w+)?"

class BridgeChecker @Inject constructor() {

    private val urlRegex by lazy { Regex("url=$URL_REGEX") }
    private val frontsRegex by lazy { Regex("fronts=($HOST_NAME_REGEX,)*$HOST_NAME_REGEX") }
    private val frontRegex by lazy { Regex("front=$HOST_NAME_REGEX") }
    private val conjureAmpCacheRegex by lazy { Regex("ampcache=$URL_REGEX") }
    private val conjureTransportRegex by lazy { Regex("transport=(min|prefix|dtls)") }
    private val conjureRegistrarRegex by lazy { Regex("registrar=(dns|ampcache)") }
    private val webTunnelServerNameRegex by lazy { Regex("servername=$HOST_NAME_REGEX") }
    private val webTunnelAddrRegex by lazy { Regex("addr=($IPv4_REGEX_NO_BOUNDS:\\d+)|$IPv6_REGEX_NO_BOUNDS:\\d+") }
    private val webTunnelVersionRegex by lazy { Regex("ver=[0-9.]+") }

    fun getObfs4BridgeChecker(input: String): PreferencesTorBridges.Checkable {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^obfs4 +$bridgeBase +cert=.+ +iat-mode=\\d")
        return PreferencesTorBridges.Checkable { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getObfs3BridgeChecker(input: String): PreferencesTorBridges.Checkable {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^obfs3 +$bridgeBase")
        return PreferencesTorBridges.Checkable { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getScrambleSuitBridgeChecker(input: String): PreferencesTorBridges.Checkable {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^scramblesuit +$bridgeBase( +password=\\w+)?")
        return PreferencesTorBridges.Checkable { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getMeekLiteBridgeChecker(input: String): PreferencesTorBridges.Checkable {
        val bridgeBase = input.getBridgeBase()
        val pattern =
            Pattern.compile("^meek_lite +$bridgeBase +url=https://[\\w.+/-]+ +front=[\\w./-]+( +utls=\\w+)?")
        return PreferencesTorBridges.Checkable { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getSnowFlakeBridgeChecker(input: String): PreferencesTorBridges.Checkable {
        val bridgeBase = input.getBridgeBase()
        val pattern =
            Pattern.compile("^snowflake +$bridgeBase(?: +fingerprint=\\w+)?(?: +url=https://[\\w.+/-]+)?(?: +ampcache=https://[\\w.+/-]+)?(?: +front(s)?=[\\w./-]+)?(?: +ice=(?:stun:[\\w./-]+?:\\d+,?)+)?(?: +utls-imitate=\\w+)?(?: +sqsqueue=https://[\\w.+/-]+)?(?: +sqscreds=[-A-Za-z0-9+/=]+)?(?: +ice=(?:stun:[\\w./-]+?:\\d+,?)+)?")
        return PreferencesTorBridges.Checkable { bridge -> pattern.matcher(bridge).matches() }
    }

    fun getConjureBridgeChecker(input: String): PreferencesTorBridges.Checkable {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^conjure +$bridgeBase .+")
        return PreferencesTorBridges.Checkable { bridge ->
            pattern.matcher(bridge).matches()
                    && bridge.contains(urlRegex)
                    && (!bridge.contains("front=") || bridge.contains(frontRegex))
                    && (!bridge.contains("fronts=") || bridge.contains(frontsRegex))
                    && (!bridge.contains("transport=") || bridge.contains(conjureTransportRegex))
                    && (!bridge.contains("registrar=") || bridge.contains(conjureRegistrarRegex))
                    && (!bridge.contains("registrar=ampcache") || bridge.contains(
                conjureAmpCacheRegex
            ))
        }
    }

    fun getWebTunnelBridgeChecker(input: String): PreferencesTorBridges.Checkable {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile("^webtunnel +$bridgeBase ")
        return PreferencesTorBridges.Checkable { bridge ->
            val matcher = pattern.matcher(bridge)
            matcher.find()
                    && bridge.contains(urlRegex)
                    && (!bridge.contains("servername=") || bridge.contains(webTunnelServerNameRegex))
                    && (!bridge.contains("addr=") || bridge.contains(webTunnelAddrRegex))
                    && (!bridge.contains("ver=") || bridge.contains(webTunnelVersionRegex))
                    && (
                    !bridge.replace(matcher.group(), "")
                        .split(" ")
                        .any { !it.contains("=") }
                    )
        }
    }

    fun getOtherBridgeChecker(input: String): PreferencesTorBridges.Checkable {
        val bridgeBase = input.getBridgeBase()
        val pattern = Pattern.compile(bridgeBase)
        return PreferencesTorBridges.Checkable { bridge -> pattern.matcher(bridge).matches() }
    }

    private fun String.getBridgeBase() = if (isIPv6Bridge()) {
        ipv6BridgeBase
    } else {
        ipv4BridgeBase
    }

    private fun String.isIPv6Bridge() = contains("[") && contains("]")
}
