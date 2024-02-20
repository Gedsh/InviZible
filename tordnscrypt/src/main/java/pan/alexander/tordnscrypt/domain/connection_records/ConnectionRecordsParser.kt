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
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionLogEntry
import pan.alexander.tordnscrypt.domain.connection_records.entities.DnsLogEntry
import pan.alexander.tordnscrypt.domain.connection_records.entities.PacketLogEntry
import pan.alexander.tordnscrypt.iptables.Tethering
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.Constants
import pan.alexander.tordnscrypt.utils.apps.InstalledAppNamesStorage
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Named

private const val MAX_LINES_IN_LOG = 200

class ConnectionRecordsParser @Inject constructor(
    private val applicationContext: Context,
    private val installedAppNamesStorage: dagger.Lazy<InstalledAppNamesStorage>,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    defaultPreferences: SharedPreferences
) {

    private val modulesStatus = ModulesStatus.getInstance()
    private val localEthernetDeviceAddress =
        defaultPreferences.getString(
            "pref_common_local_eth_device_addr",
            Constants.STANDARD_ADDRESS_LOCAL_PC
        ) ?: Constants.STANDARD_ADDRESS_LOCAL_PC

    private val liveLogEntryBlocked by lazy {
        ContextCompat.getColor(applicationContext, R.color.liveLogEntryBlocked)
    }
    private val liveLogEntryNoDns by lazy {
        ContextCompat.getColor(applicationContext, R.color.liveLogEntryNoDns)
    }
    private val liveLogEntryDnsUnused by lazy {
        ContextCompat.getColor(applicationContext, R.color.liveLogEntryDnsUnused)
    }
    private val liveLogEntryDnsUsed by lazy {
        ContextCompat.getColor(applicationContext, R.color.liveLogEntryDnsUsed)
    }

    private val dateFormatToday by lazy {
        SimpleDateFormat("HH:mm:ss", Locale.ROOT)
    }
    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }

    fun formatLines(connectionRecords: List<ConnectionLogEntry>): String {

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

            if (record is DnsLogEntry && !record.visible) {
                continue
            }

            if (record.blocked) {
                lines.append("<font color=$liveLogEntryBlocked>")
            } else if (record is PacketLogEntry && record.dnsLogEntry == null) {
                lines.append("<font color=$liveLogEntryNoDns>")
            } else if (record is DnsLogEntry) {
                lines.append("<font color=$liveLogEntryDnsUnused>")
            } else if (record is PacketLogEntry) {
                lines.append("<font color=$liveLogEntryDnsUsed>")
            }

            lines.append("[")
            if (DateUtils.isToday(record.time)) {
                lines.append(dateFormatToday.format(record.time))
            } else {
                lines.append(dateFormat.format(record.time))
            }
            lines.append("] ")

            if (record is PacketLogEntry) {
                var appName = installedAppNamesStorage.get().getAppNameByUid(record.uid) ?: ""
                if (appName.isEmpty() || record.uid == 1000) {
                    appName =
                        applicationContext.packageManager.getNameForUid(record.uid) ?: "Undefined"
                }

                val protocol = when (record.protocol) {
                    6 -> " (TCP)"
                    17 -> " (UDP)"
                    1 -> " (ICMPv4)"
                    58 -> " (ICMPv6)"
                    else -> ""
                }

                if (Tethering.apIsOn && fixTTL && record.saddr.contains(apAddresses)) {
                    lines.append("<b>").append("WiFi").append("</b>")
                        .append(protocol).append(" -> ")
                } else if (Tethering.usbTetherOn && fixTTL && record.saddr.contains(usbAddresses)) {
                    lines.append("<b>").append("USB").append("</b>")
                        .append(protocol).append(" -> ")
                } else if (Tethering.ethernetOn && fixTTL && record.saddr.contains(
                        localEthernetDeviceAddress
                    )
                ) {
                    lines.append("<b>").append("LAN").append("</b>")
                        .append(protocol).append(" -> ")
                } else if (appName.isNotEmpty()) {
                    lines.append("<b>").append(appName).append("</b>")
                        .append(protocol).append(" -> ")
                } else {
                    lines.append("<b>").append("Unknown UID").append(record.uid).append("</b>")
                        .append(protocol).append(" -> ")
                }

                record.dnsLogEntry?.let {
                    lines.append(it.domainsChain.joinToString(" -> "))
                        .append(" -> ")
                        .append(record.daddr)
                } ?: record.reverseDns?.let {
                    lines.append(it).append(" -> ").append(record.daddr)
                } ?: run {
                    lines.append(record.daddr)
                }
            } else if (record is DnsLogEntry) {
                if (record.domainsChain.isNotEmpty()) {
                    lines.append(record.domainsChain.joinToString(" -> "))
                }
                if (record.blocked && record.blockedByIpv6) {
                    lines.append(" ipv6")
                }
                if (!record.blocked && record.ips.isNotEmpty()) {
                    lines.append(" -> ").append(record.ips.joinToString(", "))
                }
            }

            lines.append("</font>")

            if (i < connectionRecords.size - 1) {
                lines.append("<br />")
            }
        }

        return lines.toString()
    }
}
