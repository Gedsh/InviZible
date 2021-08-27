package pan.alexander.tordnscrypt.di
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

import dagger.Component
import pan.alexander.tordnscrypt.BootCompleteReceiver
import pan.alexander.tordnscrypt.MainActivity
import pan.alexander.tordnscrypt.SettingsActivity
import pan.alexander.tordnscrypt.TopFragment
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.main_fragment.MainFragment
import pan.alexander.tordnscrypt.modules.ModulesService
import pan.alexander.tordnscrypt.proxy.ProxyFragment
import pan.alexander.tordnscrypt.settings.PreferencesFastFragment
import pan.alexander.tordnscrypt.settings.dnscrypt_servers.PreferencesDNSCryptServers
import pan.alexander.tordnscrypt.settings.firewall.FirewallFragment
import pan.alexander.tordnscrypt.settings.tor_apps.UnlockTorAppsFragment
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges
import pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFrag
import pan.alexander.tordnscrypt.settings.tor_preferences.PreferencesTorFragment
import pan.alexander.tordnscrypt.update.UpdateService
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN
import javax.inject.Singleton

@Singleton
@Component(modules = [SharedPreferencesModule::class, RepositoryModule::class,
    DataSourcesModule::class, HelpersModule::class, CoroutinesModule::class])
interface AppComponent {
    fun getPreferenceRepository(): dagger.Lazy<PreferenceRepository>

    fun inject(activity: MainActivity)
    fun inject(activity: SettingsActivity)
    fun inject(fragment: TopFragment)
    fun inject(fragment: MainFragment)
    fun inject(fragment: PreferencesFastFragment)
    fun inject(fragment: UnlockTorAppsFragment)
    fun inject(fragment: PreferencesTorBridges)
    fun inject(fragment: UnlockTorIpsFrag)
    fun inject(fragment: PreferencesTorFragment)
    fun inject(fragment: ProxyFragment)
    fun inject(fragment: PreferencesDNSCryptServers)
    fun inject(fragment: FirewallFragment)
    fun inject(service: ModulesService)
    fun inject(service: ServiceVPN)
    fun inject(service: UpdateService)
    fun inject(receiver: BootCompleteReceiver)
}
