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

package pan.alexander.tordnscrypt.vpn.service

import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.proxy.CLEARNET_APPS_FOR_PROXY
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.NUMBER_REGEX
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import javax.inject.Inject
import javax.inject.Named

class VpnPreferenceHolder @Inject constructor(
    @Named(DEFAULT_PREFERENCES_NAME)
    defaultPreferences: SharedPreferences,
    preferenceRepository: PreferenceRepository,
    pathVars: PathVars
) {
    val nativeLogLevel = Log.ERROR
    val dnsBlockedResponseCode = 3
    val itpdRedirectAddress = "10.191.0.1"
    val ownUID = pathVars.appUid
    val blockHttp = defaultPreferences.getBoolean(BLOCK_HTTP, false)
    val routeAllThroughTor = defaultPreferences.getBoolean(ALL_THROUGH_TOR, true)
    val torTethering = defaultPreferences.getBoolean(TOR_TETHERING, false)
    val torVirtualAddressNetwork: String = pathVars.torVirtAdrNet ?: "10.192.0.0/10"
    val blockIPv6 = defaultPreferences.getBoolean(BLOCK_IPv6, true)

    val setBypassProxy = preferenceRepository.getStringSetPreference(CLEARNET_APPS_FOR_PROXY)

    val compatibilityMode = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
        true
    } else {
        defaultPreferences.getBoolean(COMPATIBILITY_MODE, false)
    }

    val arpSpoofingDetection = defaultPreferences.getBoolean(ARP_SPOOFING_DETECTION, false)
    val blockInternetWhenArpAttackDetected =
        defaultPreferences.getBoolean(ARP_SPOOFING_BLOCK_INTERNET, false)
    val dnsRebindProtection = defaultPreferences.getBoolean(DNS_REBIND_PROTECTION, false)
    val lan = defaultPreferences.getBoolean(BYPASS_LAN, true)
    val firewallEnabled = preferenceRepository.getBoolPreference(FIREWALL_ENABLED)
    val ignoreSystemDNS = defaultPreferences.getBoolean(IGNORE_SYSTEM_DNS, false)

    val proxyAddress = defaultPreferences.getString(PROXY_ADDRESS, "") ?: ""
    val proxyPort = defaultPreferences.getString(PROXY_PORT, "").let {
        if (it?.matches(Regex(NUMBER_REGEX)) == true) {
            it.toInt()
        } else {
            1080
        }
    }

    val useProxy = defaultPreferences.getBoolean(USE_PROXY, false)
            && proxyAddress.isNotBlank()
            && proxyPort != 0

    val torDNSPort = pathVars.torDNSPort.let {
        if (it.matches(Regex(NUMBER_REGEX))) {
            it.toInt()
        } else {
            5400
        }
    }

    val torSOCKSPort = pathVars.torSOCKSPort.let {
        if (it.matches(Regex(NUMBER_REGEX))) {
            it.toInt()
        } else {
            9050
        }
    }

    private val modulesStatus = ModulesStatus.getInstance()
    val fixTTL = (modulesStatus.isFixTTL && modulesStatus.mode == OperationMode.ROOT_MODE
            && !modulesStatus.isUseModulesWithRoot)
    val connectionLogsEnabled = defaultPreferences.getBoolean(CONNECTION_LOGS, true)
}
