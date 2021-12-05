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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.arp

import android.os.Build
import android.util.Log
import pan.alexander.tordnscrypt.di.arp.ArpScope
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.regex.Pattern
import javax.inject.Inject

private const val COMMAND_ARP = "ip neigh" //"ip neighbour show"
private const val ARP_FILE_PATH = "/proc/net/arp"
private const val NOT_SUPPORTED_DELAY_COUNTER = 10
private val macPattern by lazy { Pattern.compile("([0-9a-fA-F]{2}[:]){5}([0-9a-fA-F]{2})") }

@ArpScope
class ArpTableManager @Inject constructor(
    private val commandExecutor: dagger.Lazy<CommandExecutor>,
    private val arpScannerHelper: dagger.Lazy<ArpScannerHelper>
) {

    @Volatile
    var gatewayMac = ""
    @Volatile
    var savedGatewayMac = ""

    var notSupportedCounter = NOT_SUPPORTED_DELAY_COUNTER
    private var notSupportedCounterFreeze = false

    private var arpTableAccessible: Boolean? = null
        get() = field ?: isArpTableAccessible().also { field = it }

    private fun isArpTableAccessible(): Boolean = try {
        File(ARP_FILE_PATH).let {
            it.isFile && it.canRead()
        }
    } catch (ignored: Exception) {
        false
    }


    fun updateGatewayMac(defaultGateway: String) {

        if (defaultGateway.isEmpty()) {
            return
        }

        if (arpTableAccessible == true) {
            updateGatewayMacUsingFile(defaultGateway)
        } else {
            updateGatewayMacUsingShell(defaultGateway)
        }
    }

    private fun updateGatewayMacUsingFile(defaultGateway: String) {
        try {
            tryUpdateGatewayMacUsingFile(defaultGateway)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ArpScanner getArpStringFromFile exception ${e.message}\n${e.cause}")
        }
    }

    private fun tryUpdateGatewayMacUsingFile(defaultGateway: String) {

        BufferedReader(InputStreamReader(File(ARP_FILE_PATH).inputStream())).use { bufferedReader ->
            var line = bufferedReader.readLine()
            while (line != null) {
                if (line.contains("$defaultGateway ")) {

                    gatewayMac = getMacFromLine(line)

                    if (savedGatewayMac.isEmpty() && gatewayMac.isNotBlank()) {
                        val macStared = gatewayMac.substring(0..gatewayMac.length - 7)
                            .replace(Regex("\\w+?"), "*")
                            .plus(gatewayMac.substring(gatewayMac.length - 6))
                        Log.i(LOG_TAG, "ArpScanner gatewayMac is $macStared")
                        savedGatewayMac = gatewayMac
                    }
                    break
                } else {
                    line = bufferedReader.readLine()
                }
            }
        }
    }

    private fun updateGatewayMacUsingShell(defaultGateway: String) {
        try {
            tryUpdateGatewayMacUsingShell(defaultGateway)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ArpScanner getArpStringFromShell exception ${e.message}\n${e.cause}")
        }
    }

    private fun tryUpdateGatewayMacUsingShell(defaultGateway: String) {

        val lines = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && arpScannerHelper.get().isRootAvailable()) {
            commandExecutor.get().execRoot(COMMAND_ARP)
        } else {
            commandExecutor.get().execNormal(COMMAND_ARP)
        }

        var containsNotEmptyLines = false

        for (line: String in lines) {
            if (line.trim().isNotEmpty() && !line.contains("-BOC-")) {
                containsNotEmptyLines = true
            }

            if (line.contains("$defaultGateway ")) {

                gatewayMac = getMacFromLine(line)

                if (savedGatewayMac.isEmpty() && gatewayMac.isNotBlank()) {
                    val macStared = gatewayMac.substring(0..gatewayMac.length - 7)
                        .replace(Regex("\\w+?"), "*")
                        .plus(gatewayMac.substring(gatewayMac.length - 6))
                    Log.i(LOG_TAG, "ArpScanner gatewayMac is $macStared")
                    savedGatewayMac = gatewayMac
                }

                notSupportedCounterFreeze = true

                break
            } else if (getMacFromLine(line).isNotEmpty()) {
                notSupportedCounterFreeze = true
            }
        }

        if (lines.isEmpty() && notSupportedCounter > 0
            || containsNotEmptyLines && !notSupportedCounterFreeze && notSupportedCounter > 0) {
            notSupportedCounter--
        }
    }

    private fun getMacFromLine(line: String): String {
        val matcher = macPattern.matcher(line)

        if (matcher.find()) {
            return matcher.group().trim()
        }

        return ""
    }

    fun clearGatewayMac() {
        gatewayMac = ""
        savedGatewayMac = ""
    }
}
