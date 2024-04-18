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

import dagger.Module
import dagger.Provides
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import javax.inject.Named
import javax.inject.Singleton

@Module
class CoroutinesModule {

    @Provides
    @Named(SUPERVISOR_JOB_MAIN_DISPATCHER_SCOPE)
    fun provideSupervisorMainDispatcherCoroutineScope(
        dispatcherMain: MainCoroutineDispatcher
    ): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcherMain)
    }

    @Provides
    @Named(SUPERVISOR_JOB_IO_DISPATCHER_SCOPE)
    fun provideSupervisorIoDispatcherCoroutineScope(
        @Named(DISPATCHER_IO) dispatcherIo: CoroutineDispatcher
    ): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcherIo)
    }

    @Provides
    @Singleton
    @Named(SUPERVISOR_JOB_IO_DISPATCHER_SCOPE_SINGLETON)
    fun provideSupervisorIoDispatcherCoroutineScopeSingleton(
        @Named(DISPATCHER_IO) dispatcherIo: CoroutineDispatcher
    ): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcherIo)
    }

    @Provides
    fun provideDispatcherMain(): MainCoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Named(DISPATCHER_IO)
    fun provideDispatcherIo(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Named(DISPATCHER_COMPUTATION)
    fun provideDispatcherComputation(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun provideCoroutineExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { coroutine, throwable ->
            loge("Coroutine ${coroutine[CoroutineName]} unhandled exception", throwable)
        }
    }

    companion object {
        const val SUPERVISOR_JOB_MAIN_DISPATCHER_SCOPE = "SUPERVISOR_JOB_MAIN_DISPATCHER_SCOPE"
        const val SUPERVISOR_JOB_IO_DISPATCHER_SCOPE = "SUPERVISOR_JOB_IO_DISPATCHER_SCOPE"
        const val SUPERVISOR_JOB_IO_DISPATCHER_SCOPE_SINGLETON = "SUPERVISOR_JOB_IO_DISPATCHER_SCOPE_SINGLETON"
        const val DISPATCHER_IO = "DISPATCHER_IO"
        const val DISPATCHER_COMPUTATION = "DISPATCHER_COMPUTATION"
    }
}
