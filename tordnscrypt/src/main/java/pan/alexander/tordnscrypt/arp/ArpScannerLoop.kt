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

package pan.alexander.tordnscrypt.arp

import android.util.Log
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.arp.ArpScope
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.util.concurrent.ScheduledExecutorService
import javax.inject.Inject

private const val ARP_NOTIFICATION_ID = 110
private const val DHCP_NOTIFICATION_ID = 111

@ArpScope
class ArpScannerLoop @Inject constructor(
    private val arpWarningNotification: ArpWarningNotification,
    private val uiUpdater: ArpRelatedUiUpdater,
    private val arpTableManager: ArpTableManager,
    private val connectionManager: ConnectionManager,
    private val arpScannerHelper: ArpScannerHelper,
    private val defaultGatewayManager: DefaultGatewayManager,
    private val commandExecutor: CommandExecutor
) {

    @Volatile
    var stopping = false
    var paused = false

    fun checkArpAttack(scheduledExecutorService: ScheduledExecutorService?) {
        try {
            tryCheckArpAttack(scheduledExecutorService)
        } catch (e: Exception) {
            if (defaultGatewayManager.defaultGateway.isNotEmpty()) {
                arpScannerHelper.resetArpScannerState()
            }
            Log.w(
                LOG_TAG,
                "ArpScanner executor exception! ${e.message}\n${e.cause}\n${e.stackTrace}"
            )
        }
    }

    private fun tryCheckArpAttack(scheduledExecutorService: ScheduledExecutorService?) {

        if (stopping) {

            if (defaultGatewayManager.defaultGateway.isNotEmpty()) {
                arpScannerHelper.resetArpScannerState()
            }

            commandExecutor.closeRootCommandShell()

            scheduledExecutorService?.let {
                if (!it.isShutdown) {
                    Log.i(LOG_TAG, "ArpScanner Stopped")
                    it.shutdownNow()
                }
            }

            return
        }

        if (paused) {
            return
        }

        if (connectionManager.wifiActive) {
            defaultGatewayManager.updateDefaultWiFiGateway()
        } else if (connectionManager.ethernetActive) {
            defaultGatewayManager.requestRuleTable()
        } else if (!connectionManager.cellularActive
            && connectionManager.connectionAvailable
        ) {
            defaultGatewayManager.updateDefaultWiFiGateway()
        }

        if (defaultGatewayManager.savedDefaultGateway.isNotEmpty()
            && defaultGatewayManager.defaultGateway.isNotEmpty()
        ) {

            if (defaultGatewayManager.savedDefaultGateway != defaultGatewayManager.defaultGateway) {
                Log.e(LOG_TAG, "DHCPAttackDetected defaultGateway changed")
                Log.i(
                    LOG_TAG,
                    "Upstream Network Saved default Gateway:${defaultGatewayManager.savedDefaultGateway}"
                )
                Log.i(
                    LOG_TAG,
                    "Upstream Network Current default Gateway:${defaultGatewayManager.defaultGateway}"
                )

                if (!ArpScanner.dhcpGatewayAttackDetected) {
                    arpWarningNotification.send(
                        R.string.ask_force_close_title,
                        R.string.notification_rogue_dhcp,
                        DHCP_NOTIFICATION_ID
                    )
                    uiUpdater.makeToast(R.string.notification_rogue_dhcp)
                    uiUpdater.updateMainActivityIcons()
                    arpScannerHelper.reloadIptablesWithRootMode()
                }

                ArpScanner.dhcpGatewayAttackDetected = true

                return
            } else if (ArpScanner.dhcpGatewayAttackDetected) {
                ArpScanner.dhcpGatewayAttackDetected = false
                uiUpdater.updateMainActivityIcons()
                arpScannerHelper.reloadIptablesWithRootMode()
            }
        }

        if (arpTableManager.notSupportedCounter > 0) {
            arpTableManager.updateGatewayMac(defaultGatewayManager.defaultGateway)
        }

        if (arpTableManager.savedGatewayMac.isNotEmpty()
            && arpTableManager.gatewayMac.isNotEmpty()
        ) {

            if (!arpScannerHelper.getArpSpoofingDetectionSupported()) {
                arpScannerHelper.saveArpSpoofingDetectionNotSupported(false)
            }

            if (arpTableManager.gatewayMac != arpTableManager.savedGatewayMac) {
                Log.e(LOG_TAG, "ArpAttackDetected")
                Log.i(
                    LOG_TAG,
                    "Upstream Network Saved default Gateway:${defaultGatewayManager.savedDefaultGateway} MAC:${arpTableManager.savedGatewayMac}"
                )
                Log.i(
                    LOG_TAG,
                    "Upstream Network Current default Gateway:${defaultGatewayManager.defaultGateway} MAC:${arpTableManager.gatewayMac}"
                )


                if (!ArpScanner.arpAttackDetected) {
                    arpWarningNotification.send(
                        R.string.ask_force_close_title,
                        R.string.notification_arp_spoofing,
                        ARP_NOTIFICATION_ID
                    )
                    uiUpdater.makeToast(R.string.notification_arp_spoofing)
                    uiUpdater.updateMainActivityIcons()
                    arpScannerHelper.reloadIptablesWithRootMode()
                }

                ArpScanner.arpAttackDetected = true

            } else if (ArpScanner.arpAttackDetected) {
                ArpScanner.arpAttackDetected = false
                uiUpdater.updateMainActivityIcons()
                arpScannerHelper.reloadIptablesWithRootMode()
            }
        }

        if (arpTableManager.notSupportedCounter == 0 && arpScannerHelper.getArpSpoofingDetectionSupported()) {
            arpScannerHelper.saveArpSpoofingDetectionNotSupported(true)
            Log.w(LOG_TAG, "Arp Spoofing detection is not supported. Only rogue DHCP detection.")
            //uiUpdater.makeToast(R.string.toast_arp_detection_not_supported)
            //arpScanner.stop()
        }
    }
}
