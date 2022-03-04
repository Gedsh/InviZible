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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import dagger.Binds
import dagger.Module
import kotlinx.coroutines.ExperimentalCoroutinesApi
import pan.alexander.tordnscrypt.data.bridges.BridgeCheckerRepositoryImpl
import pan.alexander.tordnscrypt.data.connection_checker.ConnectionCheckerRepositoryImpl
import pan.alexander.tordnscrypt.data.dns_resolver.DnsRepositoryImpl
import pan.alexander.tordnscrypt.data.preferences.PreferenceRepositoryImpl
import pan.alexander.tordnscrypt.data.resources.ResourceRepositoryImpl
import pan.alexander.tordnscrypt.domain.bridges.BridgeCheckerRepository
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerRepository
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsRepository
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.domain.resources.ResourceRepository

@Module
abstract class RepositoryModule {

    @Binds
    abstract fun providePreferenceRepository(repository: PreferenceRepositoryImpl): PreferenceRepository

    @Binds
    abstract fun provideDnsRepository(repository: DnsRepositoryImpl): DnsRepository

    @Binds
    abstract fun provideInternetCheckingRepository(
        repository: ConnectionCheckerRepositoryImpl
    ): ConnectionCheckerRepository

    @Binds
    abstract fun provideResourceRepository(
        resourcesRepository: ResourceRepositoryImpl
    ): ResourceRepository

    @Binds
    abstract fun provideBridgeCheckerRepository(
        bridgeCheckerRepository: BridgeCheckerRepositoryImpl
    ): BridgeCheckerRepository
}
