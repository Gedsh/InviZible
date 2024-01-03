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

package pan.alexander.tordnscrypt.di.logreader

import dagger.Binds
import dagger.Module
import pan.alexander.tordnscrypt.data.connection_records.ConnectionRecordsRepositoryImpl
import pan.alexander.tordnscrypt.data.log_reader.ModulesLogRepositoryImpl
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsRepository
import pan.alexander.tordnscrypt.domain.log_reader.ModulesLogRepository

@Module
abstract class LogReaderRepositoryModule {
    @Binds
    abstract fun provideModulesLogRepository(
        repository: ModulesLogRepositoryImpl
    ): ModulesLogRepository

    @Binds
    abstract fun provideConnectionRecordsRepository(
        repository: ConnectionRecordsRepositoryImpl
    ): ConnectionRecordsRepository
}
