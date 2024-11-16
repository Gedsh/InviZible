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

package pan.alexander.tordnscrypt

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import pan.alexander.tordnscrypt.backup.ResetModuleHelper
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.utils.enums.ModuleName
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import javax.inject.Inject
import javax.inject.Named

private const val CHECK_ROOT_TIMEOUT_SEC = 5

class TopFragmentViewModel @Inject constructor(
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher,
    private val resetModuleHelper: dagger.Lazy<ResetModuleHelper>
): ViewModel() {

    private val rootStateMutableLiveData = MutableLiveData<RootState>(RootState.Undefined)
    val rootStateLiveData: LiveData<RootState> get() = rootStateMutableLiveData

    @Volatile
    var rootCheckResultSuccess = false

    private var checkRootJob: Job? = null

    fun checkRootAvailable() {

        if (checkRootJob?.isActive == true) {
            return
        }

        checkRootJob = viewModelScope.launch(dispatcherIo) {
            try {
                withTimeout(CHECK_ROOT_TIMEOUT_SEC * 1000L) {
                    checkRootParams()
                }
            } catch (e: Exception) {
                rootCheckResultSuccess = false
                rootStateMutableLiveData.postValue(RootState.RootNotAvailable)
            }
        }
    }

    fun cancelRootChecking() {
        if (checkRootJob?.isActive == true) {
            checkRootJob?.cancel()
        }
    }

    private fun checkRootParams() {
        val suAvailable = try {
            Shell.SU.available()
        } catch (e: Exception) {
            loge("TopFragmentViewModel suAvailable exception", e)
            false
        }

        var suVersion = ""
        val suResult = mutableListOf<String>()
        val bbResult = mutableListOf<String>()

        if (suAvailable) {
            try {
                suVersion = Shell.SU.version(false) ?: ""
                suResult.addAll(Shell.SU.run("id") ?: emptyList())
                bbResult.addAll(Shell.SU.run("busybox | head -1") ?: emptyList())
            } catch (e: java.lang.Exception) {
                loge("TopFragmentViewModel suParam exception", e)
            }

            rootCheckResultSuccess = true
            rootStateMutableLiveData.postValue(RootState.RootAvailable(suVersion, suResult, bbResult))
        } else {
            rootCheckResultSuccess = true
            rootStateMutableLiveData.postValue(RootState.RootNotAvailable)
        }
    }

    fun resetModuleSettings(moduleName: ModuleName) {
        viewModelScope.launch(dispatcherIo) {
            try {
                resetModuleHelper.get().resetModuleSettings(moduleName)
            } catch (e: Exception) {
                loge("TopFragmentViewModel resetModuleSettings", e)
            }
        }
    }
}
