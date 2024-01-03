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

package pan.alexander.tordnscrypt.arp

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import pan.alexander.tordnscrypt.di.arp.ArpScope
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteOrder
import java.util.regex.Pattern
import javax.inject.Inject

private const val COMMAND_RULE_SHOW = "ip rule"
private const val COMMAND_ROUTE_SHOW = "ip route show table %s"

private val ethTablePattern by lazy { Pattern.compile("eth\\d lookup (\\w+)") }
private val defaultGatewayPattern by lazy { Pattern.compile("default via (([0-9*]{1,3}\\.){3}[0-9*]{1,3})") }

@ArpScope
class DefaultGatewayManager @Inject constructor(
    context: Context,
    private val connectionManager: ConnectionManager,
    private val commandExecutor: CommandExecutor,
    private val arpScannerHelper: ArpScannerHelper
) {

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Volatile
    var defaultGateway = ""
    @Volatile
    var savedDefaultGateway = ""

    @Volatile
    private var ethernetTable = ""

    fun updateDefaultWiFiGateway() {
        val dhcp = wifiManager.dhcpInfo ?: return
        var ipAddress = dhcp.gateway
        ipAddress =
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) Integer.reverseBytes(ipAddress) else ipAddress
        val ipAddressByte = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
        try {
            val myAddr = InetAddress.getByAddress(ipAddressByte)

            defaultGateway = myAddr.hostAddress?.trim() ?: ""

            if (savedDefaultGateway.isEmpty()) {
                Log.i(LOG_TAG, "ArpScanner defaultGateway is $defaultGateway")
                savedDefaultGateway = defaultGateway
            }
        } catch (e: Exception) {

            if (connectionManager.connectionAvailable
                && !connectionManager.cellularActive
                && !connectionManager.wifiActive
                && !connectionManager.ethernetActive) {
                arpScannerHelper.makePause(true, resetInternalValues = true)
            } else {

                if (defaultGateway.isNotEmpty()) {
                    arpScannerHelper.resetArpScannerState()
                }

                Log.e(LOG_TAG, "ArpScanner error getting default gateway ${e.message}\n${e.cause}")
            }
        }
    }

    fun requestRuleTable() {
        if (ethernetTable.isEmpty()) {
            try {
                Log.i(LOG_TAG, "ArpScanner requestRuleTable")
                requestDefaultEthernetGateway(commandExecutor.execNormal(COMMAND_RULE_SHOW))
            } catch (e: Exception) {
                Log.e(LOG_TAG, "ArpScanner requestRuleTable exception ${e.message} ${e.cause}")
            }
        } else {
            requestDefaultEthernetGateway()
        }
    }

    private fun requestDefaultEthernetGateway(lines: MutableList<String>) {

        try {
            for (line: String in lines) {
                val matcher = ethTablePattern.matcher(line)

                if (matcher.find()) {

                    ethernetTable = matcher.group(1) ?: ""

                    Log.i(LOG_TAG, "ArpScanner ethTable is $ethernetTable")

                    if (ethernetTable.isNotEmpty()) {
                        setDefaultEthernetGateway(
                            commandExecutor.execNormal(
                                String.format(
                                    COMMAND_ROUTE_SHOW,
                                    ethernetTable
                                )
                            )
                        )
                    }

                    break
                }
            }
        } catch (e: java.lang.Exception) {
            Log.e(
                LOG_TAG,
                "ArpScanner requestDefaultEthernetGateway(lines) exception ${e.message} ${e.cause}"
            )
        }
    }

    private fun requestDefaultEthernetGateway() {
        try {
            if (ethernetTable.isNotEmpty()) {
                setDefaultEthernetGateway(
                    commandExecutor.execNormal(
                        String.format(
                            COMMAND_ROUTE_SHOW,
                            ethernetTable
                        )
                    )
                )
            }
        } catch (e: java.lang.Exception) {
            Log.e(
                LOG_TAG,
                "ArpScanner requestDefaultEthernetGateway exception ${e.message} ${e.cause}"
            )
        }
    }

    private fun setDefaultEthernetGateway(lines: MutableList<String>) {

        try {
            for (line: String in lines) {
                val matcher = defaultGatewayPattern.matcher(line)

                if (matcher.find()) {

                    matcher.group(1)?.let { defaultGateway = it }

                    if (savedDefaultGateway.isEmpty()) {
                        Log.i(LOG_TAG, "ArpScanner defaultGateway is $defaultGateway")
                        savedDefaultGateway = defaultGateway
                    }

                    break
                }
            }

        } catch (e: Exception) {
            if (defaultGateway.isNotEmpty()) {
                arpScannerHelper.resetArpScannerState()
            }

            Log.e(LOG_TAG, "ArpScanner error getting default gateway ${e.message} ${e.cause}")
        }
    }

    fun clearDefaultGateway() {
        defaultGateway = ""
        savedDefaultGateway = ""
        ethernetTable = ""
    }
}
