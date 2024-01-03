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

package pan.alexander.tordnscrypt.domain.connection_records

import android.content.Context
import android.content.SharedPreferences
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.iptables.Tethering
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants
import pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS
import pan.alexander.tordnscrypt.utils.apps.InstalledAppNamesStorage
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import java.util.*
import javax.inject.Inject
import javax.inject.Named

private const val MAX_LINES_IN_LOG = 200

class ConnectionRecordsParser @Inject constructor(
    private val applicationContext: Context,
    private val installedAppNamesStorage: dagger.Lazy<InstalledAppNamesStorage>,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    defaultPreferences: SharedPreferences,
    private val pathVars: dagger.Lazy<PathVars>
) {

    private val modulesStatus = ModulesStatus.getInstance()
    private val localEthernetDeviceAddress =
        defaultPreferences.getString(
            "pref_common_local_eth_device_addr",
            Constants.STANDARD_ADDRESS_LOCAL_PC
        ) ?: Constants.STANDARD_ADDRESS_LOCAL_PC

    fun formatLines(connectionRecords: List<ConnectionRecord>): String {

        val fixTTL =
            modulesStatus.isFixTTL && modulesStatus.mode == OperationMode.ROOT_MODE && !modulesStatus.isUseModulesWithRoot

        val apAddresses = if (Tethering.wifiAPAddressesRange.lastIndexOf(".") > 0) {
            Tethering.wifiAPAddressesRange.substring(
                0, Tethering.wifiAPAddressesRange.lastIndexOf(".")
            )
        } else {
            Constants.STANDARD_AP_INTERFACE_RANGE
        }

        val usbAddresses = if (Tethering.usbModemAddressesRange.lastIndexOf(".") > 0) {
            Tethering.usbModemAddressesRange.substring(
                0, Tethering.usbModemAddressesRange.lastIndexOf(".")
            )
        } else {
            Constants.STANDARD_USB_MODEM_INTERFACE_RANGE
        }

        val lines = StringBuilder()

        lines.append("<br />")

        var start = 0
        val logSize: Int = connectionRecords.size
        if (logSize > MAX_LINES_IN_LOG) {
            start = logSize - MAX_LINES_IN_LOG
        }

        for (i in start until logSize) {

            val record = connectionRecords[i]

            if (pathVars.get().appVersion.startsWith("g") && record.blocked && record.blockedByIpv6
                /*remove artifacts*/
                || (record.aName.trim() == "=" || record.qName.trim() == "=")
                && record.uid == -1000
            ) {
                continue
            }

            if (record.blocked) {
                lines.append("<font color=#f08080>")
            } else if (record.uid != -1000 && record.daddr.trim().isNotEmpty()) {
                lines.append("<font color=#E7AD42>")
            } else if (record.unused) {
                lines.append("<font color=#9e9e9e>")
            } else {
                lines.append("<font color=#009688>")
            }

            if (record.uid != -1000) {
                var appName = installedAppNamesStorage.get().getAppNameByUid(record.uid) ?: ""
                if (appName.isEmpty() || record.uid == 1000) {
                    appName =
                        applicationContext.packageManager.getNameForUid(record.uid) ?: "Undefined"
                }

                if (Tethering.apIsOn && fixTTL && record.saddr.contains(apAddresses)) {
                    lines.append("<b>").append("WiFi").append("</b>").append(" -> ")
                } else if (Tethering.usbTetherOn && fixTTL && record.saddr.contains(usbAddresses)) {
                    lines.append("<b>").append("USB").append("</b>").append(" -> ")
                } else if (Tethering.ethernetOn && fixTTL && record.saddr.contains(
                        localEthernetDeviceAddress
                    )
                ) {
                    lines.append("<b>").append("LAN").append("</b>").append(" -> ")
                } else if (appName.isNotEmpty()) {
                    lines.append("<b>").append(appName).append("</b>").append(" -> ")
                } else {
                    lines.append("<b>").append("Unknown UID").append(record.uid).append("</b>")
                        .append(" -> ")
                }
            }

            if (record.aName.trim().isNotEmpty()) {
                lines.append(record.aName.lowercase(Locale.ROOT))
                if (record.blocked && record.blockedByIpv6) {
                    lines.append(" ipv6")
                }
            } else if (record.qName.trim().isNotEmpty()) {
                lines.append(record.qName.lowercase(Locale.ROOT))
            }

            if (record.cName.trim().isNotEmpty() && record.uid == -1000) {
                lines.append(" -> ").append(record.cName.lowercase(Locale.ROOT))
            }
            if (record.daddr.trim().isNotEmpty()
                && (!record.daddr.contains(META_ADDRESS)
                        && !record.daddr.contains(LOOPBACK_ADDRESS)
                        || record.uid != -1000)
            ) {
                if (record.uid == -1000) {
                    lines.append(" -> ")
                }
                if (record.uid != -1000 && record.reverseDNS.isNotEmpty()) {
                    lines.append(record.reverseDNS).append(" -> ")
                }
                lines.append(record.daddr)
            }
            lines.append("</font>")

            if (i < connectionRecords.size - 1) {
                lines.append("<br />")
            }
        }

        return lines.toString()
    }
}
