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

package pan.alexander.tordnscrypt.arp

import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.arp.ArpScope
import pan.alexander.tordnscrypt.di.arp.ArpSubcomponent
import pan.alexander.tordnscrypt.utils.delegates.MutableLazy
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.concurrent.withLock

const val MITM_ATTACK_WARNING = "pan.alexander.tordnscrypt.arp.mitm_attack_warning"

@ArpScope
class ArpScanner @Inject constructor(
    private val arpScannerLoop: dagger.Lazy<ArpScannerLoop>,
    private val arpScannerHelper: dagger.Lazy<ArpScannerHelper>,
    private val uiUpdater: dagger.Lazy<ArpRelatedUiUpdater>,
    private val connectionManager: dagger.Lazy<ConnectionManager>
) {

    @Volatile
    private var scheduledExecutorService: ScheduledExecutorService? = null

    fun start() {

        if (arpScannerHelper.get().isArpDetectionDisabled()) return

        val connections = connectionManager.get()

        connections.updateActiveNetworks()

        if (!connections.wifiActive
            && !connections.ethernetActive
            && (connections.cellularActive || !connections.connectionAvailable)
        ) {
            return
        }

        if (scheduledExecutorService == null || scheduledExecutorService?.isShutdown == true) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        } else {
            return
        }

        arpScannerHelper.get().makePause(false, resetInternalValues = true)

        logi("Start ArpScanner")

        scheduledExecutorService?.scheduleWithFixedDelay({

            val reentrantLock = arpScannerHelper.get().arpScannerReentrantLock

            if (!reentrantLock.tryLock(5, TimeUnit.SECONDS)) {
                TimeUnit.SECONDS.sleep(1)
                return@scheduleWithFixedDelay
            }

            arpScannerLoop.get().checkArpAttack(scheduledExecutorService)

            if (reentrantLock.isHeldByCurrentThread && reentrantLock.isLocked) {
                reentrantLock.unlock()
            }

        }, 1, 10, TimeUnit.SECONDS)

        if (!connections.isConnected() && !connections.connectionAvailable) {
            arpScannerHelper.get().makePause(true, resetInternalValues = true)
        }
    }

    fun reset(connectionAvailable: Boolean) {

        if (arpScannerHelper.get().isArpDetectionDisabled()) return

        val attackDetected = arpAttackDetected || dhcpGatewayAttackDetected

        val connections = connectionManager.get()

        connections.connectionAvailable = connectionAvailable

        if (arpScannerHelper.get().isArpDetectionDisabled() && !attackDetected) {
            return
        }

        connections.updateActiveNetworks()

        if (connectionAvailable
            && (connections.wifiActive
                    || connections.ethernetActive
                    || !connections.cellularActive)
        ) {
            if (scheduledExecutorService?.isShutdown == false) {
                arpScannerHelper.get().makePause(false, resetInternalValues = false)

                if (!attackDetected) {
                    arpScannerHelper.get().resetArpScannerState()
                }

                logi("ArpScanner reset due to connectivity changed")
            } else {
                start()
            }
        } else {
            arpScannerHelper.get().makePause(true, resetInternalValues = true)
        }
    }

    fun stop() {

        arpScannerHelper.get().arpScannerReentrantLock.withLock {
            try {

                arpScannerLoop.get().stopping = true

                connectionManager.get().clearActiveNetworks()

                val updateIcons = arpAttackDetected || dhcpGatewayAttackDetected

                arpScannerHelper.get().resetArpScannerState()

                if (updateIcons) {
                    uiUpdater.get().updateMainActivityIcons()
                } else {
                    uiUpdater.get().stopUpdates()
                }

                logi("Stopping ArpScanner")
            } catch (e: java.lang.Exception) {
                logw("ArpScanner stop exception ${e.message}\n${e.cause}\n${e.stackTrace}")
            }
        }

    }

    companion object {
        @Volatile
        @JvmStatic
        var arpAttackDetected = false

        @Volatile
        @JvmStatic
        var dhcpGatewayAttackDetected = false

        private var arpSubcomponent: ArpSubcomponent? by MutableLazy {
            App.instance.daggerComponent.arpSubcomponent().create()
        }

        @JvmStatic
        fun getArpComponent(): ArpSubcomponent {
            return arpSubcomponent!!
        }

        @JvmStatic
        fun releaseArpComponent() {
            arpSubcomponent = null
        }
    }

}
