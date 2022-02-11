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

import android.util.Log
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.*
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import javax.inject.Named

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
    fun provideDispatcherMain(): MainCoroutineDispatcher = Dispatchers.Main

    @Provides
    @Named(DISPATCHER_IO)
    fun provideDispatcherIo(cachedExecutor: CachedExecutor): CoroutineDispatcher =
        cachedExecutor.executorService.asCoroutineDispatcher()

    @Provides
    @Named(DISPATCHER_COMPUTATION)
    fun provideDispatcherComputation(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun provideCoroutineExceptionHandler(): CoroutineExceptionHandler {
        return CoroutineExceptionHandler { coroutine, throwable ->
            Log.e(
                LOG_TAG,
                "Coroutine ${coroutine[CoroutineName]} exception ${throwable.message} cause ${throwable.cause}"
            )
        }
    }

    companion object {
        const val SUPERVISOR_JOB_MAIN_DISPATCHER_SCOPE = "SUPERVISOR_JOB_MAIN_DISPATCHER_SCOPE"
        const val SUPERVISOR_JOB_IO_DISPATCHER_SCOPE = "SUPERVISOR_JOB_IO_DISPATCHER_SCOPE"
        const val DISPATCHER_IO = "DISPATCHER_IO"
        const val DISPATCHER_COMPUTATION = "DISPATCHER_COMPUTATION"
    }
}
