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

package pan.alexander.tordnscrypt.di

import dagger.Binds
import dagger.Module
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractorImpl
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractor
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractorImpl
import pan.alexander.tordnscrypt.domain.tor_ips.TorIpsInteractor
import pan.alexander.tordnscrypt.domain.tor_ips.TorIpsInteractorImpl

@Module
abstract class InteractorsModule {

    @Binds
    abstract fun provideDnsInteractor(dnsInteractor: DnsInteractorImpl): DnsInteractor

    @Binds
    abstract fun provideInternetCheckerInteractor(
        internetCheckerInteractor: ConnectionCheckerInteractorImpl
    ): ConnectionCheckerInteractor

    @Binds
    abstract fun provideTorIpsInteractor(
        torIpsInteractor: TorIpsInteractorImpl
    ): TorIpsInteractor
}
