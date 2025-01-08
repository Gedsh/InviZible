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

package pan.alexander.tordnscrypt.di.modulesservice

import dagger.Subcomponent
import pan.alexander.tordnscrypt.di.logreader.LogReaderSubcomponent
import pan.alexander.tordnscrypt.dialogs.ChangeModeDialog
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN

@ModulesServiceScope
@Subcomponent(modules = [ModulesServiceSubcomponentModule::class])
interface ModulesServiceSubcomponent {

    fun logReaderSubcomponent(): LogReaderSubcomponent.Factory

    @Subcomponent.Factory
    interface Factory {
        fun create(): ModulesServiceSubcomponent
    }

    fun inject(service: ServiceVPN)
    fun inject(modulesIptablesRules: ModulesIptablesRules)
    fun inject(changeModeDialog: ChangeModeDialog)
}
