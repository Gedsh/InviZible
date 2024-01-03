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
import android.content.SharedPreferences
import android.util.Log
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.di.arp.ArpScope
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.utils.root.RootExecService
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Named
import kotlin.concurrent.withLock

@ArpScope
class ArpScannerHelper @Inject constructor(
    private val context: Context,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    private val defaultSharedPreferences: SharedPreferences,
    private val appPreferenceRepository: PreferenceRepository,
    private val cachedExecutor: CachedExecutor,
    private val defaultGatewayManager: dagger.Lazy<DefaultGatewayManager>,
    private val arpTableManager: dagger.Lazy<ArpTableManager>,
    private val arpScannerLoop: dagger.Lazy<ArpScannerLoop>,
    private val uiUpdater: dagger.Lazy<ArpRelatedUiUpdater>
) {

    val arpScannerReentrantLock = ReentrantLock()

    private val modulesStatus by lazy { ModulesStatus.getInstance() }

    fun makePause(makePause: Boolean, resetInternalValues: Boolean) {
        val attackDetected = ArpScanner.arpAttackDetected || ArpScanner.dhcpGatewayAttackDetected

        arpScannerLoop.get().paused = makePause

        if (resetInternalValues) {
            resetArpScannerState()
        }

        if (isArpDetectionDisabled() && !attackDetected) {
            return
        }

        if (makePause) {
            Log.i(RootExecService.LOG_TAG, "ArpScanner is paused")
        } else {
            Log.i(RootExecService.LOG_TAG, "ArpScanner is active")
        }

        if (attackDetected) {
            uiUpdater.get().updateMainActivityIcons()
            reloadIptablesWithRootMode()
        }
    }

    fun resetArpScannerState() {
        cachedExecutor.submit {
            arpScannerReentrantLock.withLock {
                ArpScanner.arpAttackDetected = false
                ArpScanner.dhcpGatewayAttackDetected = false

                defaultGatewayManager.get().clearDefaultGateway()

                arpTableManager.get().clearGatewayMac()
            }
        }

    }

    fun reloadIptablesWithRootMode() {
        if (isArpAttackConnectionBlockingDisabled()) return

        val modulesStatus = ModulesStatus.getInstance()
        if (modulesStatus.mode == OperationMode.ROOT_MODE) {
            modulesStatus.setIptablesRulesUpdateRequested(context, true)
        }
    }

    fun isArpDetectionDisabled(): Boolean =
        !defaultSharedPreferences.getBoolean(
            ARP_SPOOFING_DETECTION,
            false
        )

    private fun isArpAttackConnectionBlockingDisabled(): Boolean =
        !defaultSharedPreferences.getBoolean(
            ARP_SPOOFING_BLOCK_INTERNET,
            false
        )

    fun isRootAvailable(): Boolean = modulesStatus.isRootAvailable

    fun getArpSpoofingDetectionSupported() = !appPreferenceRepository.getBoolPreference(ARP_SPOOFING_NOT_SUPPORTED)

    fun saveArpSpoofingDetectionNotSupported(supported: Boolean) {
        appPreferenceRepository.setBoolPreference(ARP_SPOOFING_NOT_SUPPORTED, supported)
    }
}
