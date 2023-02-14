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

import android.os.Build
import androidx.annotation.RequiresApi
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
class DNSCryptTileService : BaseTileService() {

    @Inject
    lateinit var tileManager: dagger.Lazy<ModulesControlTileManager>

    override fun onCreate() {
        tilesSubcomponent?.inject(this)
        super.onCreate()
    }

    override fun onStartListening() {
        super.onStartListening()

        val tile = qsTile ?: return
        tileManager.get().startUpdatingState(tile, ModulesControlTileManager.ManageTask.MANAGE_DNSCRYPT)
    }

    override fun onStopListening() {
        super.onStopListening()

        tileManager.get().stopUpdatingState()
    }

    override fun onDestroy() {
        tileManager.get().stopUpdatingState()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()

        val tile = qsTile ?: return
        tileManager.get().manageModule(tile, ModulesControlTileManager.ManageTask.MANAGE_DNSCRYPT)
    }
}
