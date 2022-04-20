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

package pan.alexander.tordnscrypt.iptables

import android.content.Context
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.iptables.IptablesConstants.FILTER_FIREWALL_LAN
import pan.alexander.tordnscrypt.iptables.IptablesConstants.FILTER_OUTPUT_FIREWALL
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_PORT_AGPS1
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_PORT_AGPS2
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_PORT_NTP
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_AGPS
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_KERNEL
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_NTP
import pan.alexander.tordnscrypt.utils.Constants.NUMBER_REGEX
import pan.alexander.tordnscrypt.utils.Utils
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker.isCellularActive
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker.isEthernetActive
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker.isRoaming
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker.isVpnActive
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker.isWifiActive
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.vpn.VpnUtils
import javax.inject.Inject

private const val FIREWALL_RETURN_MARK = 15600

class IptablesFirewall @Inject constructor(
    private val context: Context,
    private val preferences: PreferenceRepository,
    private val pathVars: PathVars
) {
    private val numberRegex by lazy { Regex("-?$NUMBER_REGEX") }
    private val positiveNumberRegex by lazy { Regex(NUMBER_REGEX) }
    private val negativeNumberRegex by lazy { Regex("-$NUMBER_REGEX") }

    private val modulesStatus = ModulesStatus.getInstance()

    private val ownUID = pathVars.appUid

    private val uidAllowed by lazy { hashSetOf<Int>() }
    private val uidSpecialAllowed by lazy { hashSetOf<Int>() }
    private val uidLanAllowed by lazy { hashSetOf<Int>() }

    fun getFirewallRules(tetheringActive: Boolean): List<String> {

        prepareUidAllowed()

        val iptables = getIptables()

        return sequenceOf(
            "$iptables -F $FILTER_OUTPUT_FIREWALL 2> /dev/null",
            "$iptables -D OUTPUT -j $FILTER_OUTPUT_FIREWALL 2> /dev/null || true",
            "$iptables -N $FILTER_OUTPUT_FIREWALL 2> /dev/null",
            "$iptables -I OUTPUT 2 -j $FILTER_OUTPUT_FIREWALL",
            "$iptables -A $FILTER_OUTPUT_FIREWALL -m owner --uid-owner $ownUID -j RETURN"
        ).plus(
            getTetheringRules(iptables, tetheringActive)
        ).plus(
            getLanRules(iptables)
        ).plus(
            getAppRulesByConnectionType(iptables)
        ).plus(
            getSpecialRules(iptables)
        ).plus(
            "$iptables -A $FILTER_OUTPUT_FIREWALL -j REJECT"
        ).toList()
    }

    fun getFastUpdateFirewallRules(): List<String> {

        val iptables = getIptables()

        return arrayListOf(
            "$iptables -D OUTPUT -j $FILTER_OUTPUT_FIREWALL 2> /dev/null || true",
            "$iptables -I OUTPUT 2 -j $FILTER_OUTPUT_FIREWALL"
        )
    }

    fun getClearFirewallRules(): List<String> {

        val iptables = getIptables()

        return arrayListOf(
            "$iptables -F $FILTER_FIREWALL_LAN 2> /dev/null",
            "$iptables -F $FILTER_OUTPUT_FIREWALL 2> /dev/null",
            "$iptables -D OUTPUT -j $FILTER_OUTPUT_FIREWALL 2> /dev/null || true"
        )
    }

    private fun getLanRules(iptables: String): List<String> =
        sequenceOf(
            "$iptables -F $FILTER_FIREWALL_LAN 2> /dev/null",
            "$iptables -N $FILTER_FIREWALL_LAN 2> /dev/null"
        ).plus(
            VpnUtils.nonTorList
                .asSequence()
                .filter { it != "127.0.0.0/8" } //exclude localhost
                .map {
                    "$iptables -A $FILTER_OUTPUT_FIREWALL -d $it -j $FILTER_FIREWALL_LAN"
                }
        ).plus(
            "$iptables -A $FILTER_OUTPUT_FIREWALL -m mark --mark $FIREWALL_RETURN_MARK -j RETURN"
        ).plus(
            uidLanAllowed
                .map {
                    when {
                        it >= 0 -> "$iptables -A $FILTER_FIREWALL_LAN -m owner --uid-owner $it -j MARK --set-mark $FIREWALL_RETURN_MARK 2> /dev/null || true"
                        it == SPECIAL_UID_KERNEL -> "$iptables -A $FILTER_FIREWALL_LAN -m owner ! --uid-owner 0:999999999 -j MARK --set-mark $FIREWALL_RETURN_MARK 2> /dev/null || true"
                        else -> ""
                    }
                }
        ).plus(
            "$iptables -A $FILTER_FIREWALL_LAN -m mark --mark $FIREWALL_RETURN_MARK -j RETURN"
        ).plus(
            "$iptables -A $FILTER_FIREWALL_LAN -j REJECT"
        ).toList()

    private fun getAppRulesByConnectionType(iptables: String): List<String> =
        uidAllowed.map {
            "$iptables -A $FILTER_OUTPUT_FIREWALL -m owner --uid-owner $it -j RETURN"
        }

    private fun getSpecialRules(iptables: String): List<String> =
        uidSpecialAllowed.flatMap {
            when (it) {
                SPECIAL_UID_KERNEL -> {
                    arrayListOf(
                        "$iptables -A $FILTER_OUTPUT_FIREWALL -m owner ! --uid-owner 0:999999999 -j RETURN"
                    )
                }
                SPECIAL_UID_NTP -> {
                    arrayListOf(
                        "$iptables -A $FILTER_OUTPUT_FIREWALL -p tcp --dport $SPECIAL_PORT_NTP -m owner --uid-owner 1000 -j RETURN",
                        "$iptables -A $FILTER_OUTPUT_FIREWALL -p udp --dport $SPECIAL_PORT_NTP -m owner --uid-owner 1000 -j RETURN",
                    )
                }
                SPECIAL_UID_AGPS -> {
                    arrayListOf(
                        "$iptables -A $FILTER_OUTPUT_FIREWALL -p tcp --dport $SPECIAL_PORT_AGPS1 -j RETURN",
                        "$iptables -A $FILTER_OUTPUT_FIREWALL -p udp --dport $SPECIAL_PORT_AGPS1 -j RETURN",
                        "$iptables -A $FILTER_OUTPUT_FIREWALL -p tcp --dport $SPECIAL_PORT_AGPS2 -j RETURN",
                        "$iptables -A $FILTER_OUTPUT_FIREWALL -p udp --dport $SPECIAL_PORT_AGPS2 -j RETURN"
                    )
                }
                else -> emptyList()
            }
        }

    private fun getTetheringRules(iptables: String, tetheringActive: Boolean): List<String> =
        if (tetheringActive) {
            val dnsTetherUid = Utils.getDnsTetherUid(ownUID)
            arrayListOf(
                "$iptables -A $FILTER_OUTPUT_FIREWALL -m owner --uid-owner $dnsTetherUid -p tcp --sport 53 -j RETURN",
                "$iptables -A $FILTER_OUTPUT_FIREWALL -m owner --uid-owner $dnsTetherUid -p udp --sport 53 -j RETURN"
            )
        } else {
            emptyList()
        }

    private fun prepareUidAllowed() {
        clearAllowedUids()
        fillAllowedAndSpecialUids(getUidsAllowed())
        fillLanAllowed()
    }

    private fun fillAllowedAndSpecialUids(listAllowed: List<String?>) =
        listAllowed.forEach {
            if (it?.matches(negativeNumberRegex) == true) {
                uidSpecialAllowed.add(it.toInt())
            } else if (it?.matches(positiveNumberRegex) == true) {
                uidAllowed.add(it.toInt())
            }
        }

    private fun fillLanAllowed() {
        preferences.getStringSetPreference(APPS_ALLOW_LAN_PREF).forEach {
            if (it.matches(numberRegex)) {
                uidLanAllowed.add(it.toInt())
            }
        }
    }

    private fun clearAllowedUids() {
        uidAllowed.clear()
        uidSpecialAllowed.clear()
        uidLanAllowed.clear()
    }

    private fun getUidsAllowed(): List<String> {

        val firewallEnabled = preferences.getBoolPreference(FIREWALL_ENABLED)
        if (!firewallEnabled || modulesStatus.mode != OperationMode.ROOT_MODE) {
            return getInstalledApps()
                .map { it.uid.toString() }
        }

        val ttlFix = modulesStatus.isFixTTL
                && modulesStatus.mode == OperationMode.ROOT_MODE
                && !modulesStatus.isUseModulesWithRoot

        val listAllowed = arrayListOf<String>()

        when {
            isVpnActive(context) && !ttlFix ->
                listAllowed.addAll(preferences.getStringSetPreference(APPS_ALLOW_VPN))
            isWifiActive(context) || isEthernetActive(context) ->
                listAllowed.addAll(preferences.getStringSetPreference(APPS_ALLOW_WIFI_PREF))
            isRoaming(context) ->
                listAllowed.addAll(preferences.getStringSetPreference(APPS_ALLOW_ROAMING))
            isCellularActive(context) ->
                listAllowed.addAll(preferences.getStringSetPreference(APPS_ALLOW_GSM_PREF))
            isWifiActive(context, true) ->
                listAllowed.addAll(preferences.getStringSetPreference(APPS_ALLOW_WIFI_PREF))
        }

        return listAllowed
    }

    private fun getIptables() = pathVars.iptablesPath.removeSuffix(" ")

    private fun getInstalledApps(): List<ApplicationData> =
        InstalledApplicationsManager.Builder()
            .build()
            .getInstalledApps()
}
