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

package pan.alexander.tordnscrypt.di

import android.content.Context
import pan.alexander.tordnscrypt.di.logreader.LogReaderSubcomponent
import pan.alexander.tordnscrypt.di.modulesservice.ModulesServiceSubcomponent
import pan.alexander.tordnscrypt.utils.delegates.MutableLazy

class SubcomponentsManager(
    context: Context,
    daggerComponent: AppComponent
) {
    private var logReaderDaggerSubcomponent: LogReaderSubcomponent? by MutableLazy {
        modulesServiceSubcomponent().logReaderSubcomponent().create(context)
    }

    fun initLogReaderDaggerSubcomponent() = logReaderDaggerSubcomponent!!

    fun releaseLogReaderScope() {
        logReaderDaggerSubcomponent = null
    }


    private var modulesServiceSubcomponent: ModulesServiceSubcomponent? by MutableLazy {
        daggerComponent.modulesServiceSubcomponent().create()
    }

    fun modulesServiceSubcomponent() = modulesServiceSubcomponent!!

    fun releaseModulesServiceSubcomponent() {
        modulesServiceSubcomponent = null
    }
}
