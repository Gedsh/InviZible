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

package pan.alexander.tordnscrypt.tiles

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.tiles.ModulesControlTileManager.Companion.UPDATE_INTERVAL_SEC
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import javax.inject.Inject
import javax.inject.Named

@RequiresApi(Build.VERSION_CODES.N)
class ChangeTorIpTileManager @Inject constructor(
    private val dispatcherMain: MainCoroutineDispatcher,
    @Named(CoroutinesModule.SUPERVISOR_JOB_IO_DISPATCHER_SCOPE)
    private val baseCoroutineScope: CoroutineScope,
    private val coroutineExceptionHandler: CoroutineExceptionHandler,
    private val context: Context
) {

    private val modulesStatus = ModulesStatus.getInstance()
    private var tile: Tile? = null

    @Volatile
    private var task: Job? = null
    @Volatile
    private var savedTileState = Tile.STATE_INACTIVE

    fun startUpdatingState(tile: Tile) {

        this.tile = tile

        val coroutineScope =
            baseCoroutineScope + CoroutineName("Tile new Tor IP") + coroutineExceptionHandler

        task?.cancel()

        savedTileState = Tile.STATE_INACTIVE

        task = coroutineScope.launch {
            while (isActive) {
                updateTile()
                delay(UPDATE_INTERVAL_SEC * 1000L)
            }
        }
    }

    fun stopUpdatingState() {
        task?.cancel()
        tile = null
    }

    private suspend fun updateTile() {
        val tile = tile ?: return

        val newTileState: Int
        val newTileLabel: String
        if (modulesStatus.torState == ModuleState.RUNNING && !modulesStatus.isTorReady
            || modulesStatus.torState == ModuleState.RESTARTING
        ) {
            newTileState = Tile.STATE_ACTIVE
            newTileLabel = context.getString(R.string.tile_changing_tor_ip)
        } else if (modulesStatus.torState == ModuleState.RUNNING) {
            newTileState = Tile.STATE_INACTIVE
            newTileLabel = context.getString(R.string.tile_change_tor_ip)
        } else {
            newTileState = Tile.STATE_UNAVAILABLE
            newTileLabel = when (modulesStatus.torState) {
                ModuleState.STARTING, ModuleState.RESTARTING -> {
                    context.getString(R.string.tvTorStarting)
                }
                ModuleState.RUNNING -> {
                    context.getString(R.string.tvTorRunning)
                }
                ModuleState.STOPPING -> {
                    context.getString(R.string.tvTorStopping)
                }
                else -> {
                    context.getString(R.string.tvTorStop)
                }
            }
        }

        if (savedTileState == Tile.STATE_ACTIVE && newTileState == Tile.STATE_INACTIVE) {
            withContext(dispatcherMain) {
                Toast.makeText(
                    context,
                    context.getText(R.string.toast_new_tor_identity),
                    Toast.LENGTH_SHORT
                ).show()
            }

        }

        savedTileState = newTileState

        if (tile.label != newTileLabel || tile.state != newTileState) {
            withContext(dispatcherMain) {
                tile.state = newTileState
                tile.label = newTileLabel
                tile.updateTile()
            }
        }
    }

    fun tileClicked(tile: Tile) {
        if (tile.state == Tile.STATE_INACTIVE) {
            ModulesRestarter.restartTor(context)
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }

        if (task?.isCompleted != false) {
            startUpdatingState(tile)
        }
    }
}
