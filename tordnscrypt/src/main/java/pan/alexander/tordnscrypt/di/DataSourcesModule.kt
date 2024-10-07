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

import dagger.Binds
import dagger.Module
import pan.alexander.tordnscrypt.data.bridges.*
import pan.alexander.tordnscrypt.data.connection_checker.ConnectionCheckerDataSource
import pan.alexander.tordnscrypt.data.connection_checker.ConnectionCheckerDataSourceImpl
import pan.alexander.tordnscrypt.data.dns_resolver.DnsDataSource
import pan.alexander.tordnscrypt.data.dns_resolver.DnsDataSourceImpl
import pan.alexander.tordnscrypt.data.dns_rules.DnsRulesDataSource
import pan.alexander.tordnscrypt.data.dns_rules.DnsRulesDataSourceImpl
import pan.alexander.tordnscrypt.data.preferences.PreferenceDataSource
import pan.alexander.tordnscrypt.data.preferences.PreferenceDataSourceImpl

@Module
abstract class DataSourcesModule {
    @Binds
    abstract fun providePreferencesDataSource(
        preferenceDataSource: PreferenceDataSourceImpl
    ): PreferenceDataSource

    @Binds
    abstract fun provideDnsDataSource(
        dnsDataSource: DnsDataSourceImpl
    ): DnsDataSource

    @Binds
    abstract fun provideInternetCheckerDataSource(
        internetCheckerDataSource: ConnectionCheckerDataSourceImpl
    ): ConnectionCheckerDataSource

    @Binds
    abstract fun provideDefaultVanillaBridgeDataSource(
        bridgeDataSource: DefaultVanillaBridgeDataSourceImpl
    ): DefaultVanillaBridgeDataSource

    @Binds
    abstract fun provideRequestBridgesDataSource(
        bridgesDataSource: RequestBridgesDataSourceImpl
    ): RequestBridgesDataSource

    @Binds
    abstract fun provideBridgesCountriesDataSource(
        bridgesCountriesDataSource: BridgesCountriesDataSourceImpl
    ): BridgesCountriesDataSource

    @Binds
    abstract fun provideDnsRulesDataSource(
        dnsDataSource: DnsRulesDataSourceImpl
    ): DnsRulesDataSource
}
