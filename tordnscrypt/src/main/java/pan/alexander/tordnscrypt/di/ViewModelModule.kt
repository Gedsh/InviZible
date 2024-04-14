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

package pan.alexander.tordnscrypt.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import pan.alexander.tordnscrypt.TopFragmentViewModel
import pan.alexander.tordnscrypt.settings.dnscrypt_servers.DnsServerViewModel
import pan.alexander.tordnscrypt.settings.firewall.FirewallViewModel
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridgesViewModel
import pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsViewModel

@Module
abstract class ViewModelModule {

    @Binds
    abstract fun provideViewModelFactory(
        factory: ViewModelFactory
    ): ViewModelProvider.Factory

    @Binds
    @IntoMap
    @ViewModelKey(UnlockTorIpsViewModel::class)
    @ObsoleteCoroutinesApi
    abstract fun provideUnlockTorIpsViewModel(
        translationViewModel: UnlockTorIpsViewModel
    ): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(FirewallViewModel::class)
    abstract fun provideFirewallViewModel(
        firewallViewModel: FirewallViewModel
    ): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(PreferencesTorBridgesViewModel::class)
    @ExperimentalCoroutinesApi
    abstract fun providePreferencesTorBridgesViewModel(
        preferencesTorBridgesViewModel: PreferencesTorBridgesViewModel
    ): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TopFragmentViewModel::class)
    abstract fun provideTopFragmentViewModel(
        topFragmentViewModel: TopFragmentViewModel
    ): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(DnsServerViewModel::class)
    abstract fun provideDnsServerViewModel(
        dnsServerViewModel: DnsServerViewModel
    ): ViewModel
}
