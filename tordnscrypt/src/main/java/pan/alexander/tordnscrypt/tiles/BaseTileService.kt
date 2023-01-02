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
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.di.tiles.TilesSubcomponent
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
abstract class BaseTileService : TileService() {

    @Inject
    lateinit var tilesLimiter: TilesLimiter

    override fun onCreate() {
        tilesSubcomponent?.inject(this)
        super.onCreate()
    }

    override fun onStartListening() {
        tilesLimiter.listenTile(this)
    }

    override fun onDestroy() {
        tilesLimiter.unlistenTile(this)
    }

    override fun onClick() {
        tilesLimiter.checkActiveTilesCount(this)
    }

    override fun onTileRemoved() {
        tilesLimiter.reset()
    }

    companion object {
        var tilesSubcomponent: TilesSubcomponent? = null
            get() = field ?: App.instance.daggerComponent.tilesSubcomponent().create()
                .also { field = it }
            private set

        fun releaseTilesSubcomponent() {
            tilesSubcomponent = null
        }
    }
}
