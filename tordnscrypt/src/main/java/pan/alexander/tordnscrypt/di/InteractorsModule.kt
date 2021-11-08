package pan.alexander.tordnscrypt.di

import dagger.Binds
import dagger.Module
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractor
import pan.alexander.tordnscrypt.domain.connection_checker.ConnectionCheckerInteractorImpl
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractor
import pan.alexander.tordnscrypt.domain.dns_resolver.DnsInteractorImpl

@Module
abstract class InteractorsModule {

    @Binds
    abstract fun provideDnsInteractor(dnsInteractor: DnsInteractorImpl): DnsInteractor

    @Binds
    abstract fun provideInternetCheckerInteractor(
        internetCheckerInteractor: ConnectionCheckerInteractorImpl
    ): ConnectionCheckerInteractor
}
