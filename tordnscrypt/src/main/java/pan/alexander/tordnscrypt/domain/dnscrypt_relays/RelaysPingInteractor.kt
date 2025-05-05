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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.domain.dnscrypt_relays

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.domain.dnscrypt_servers.ServersPingRepository
import pan.alexander.tordnscrypt.utils.connectionchecker.SocketInternetChecker.Companion.NO_CONNECTION
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import javax.inject.Inject
import javax.inject.Named

class RelaysPingInteractor @Inject constructor(
    private val serversPingRepository: ServersPingRepository,
    private val relaysPingRepository: RelaysPingRepository,
    @Named(CoroutinesModule.DISPATCHER_IO)
    private val dispatcherIo: CoroutineDispatcher
) {
    suspend fun getTimeout(name: String, sdns: String) = withContext(dispatcherIo) {
        val address = relaysPingRepository.getAddressFromSDNS(sdns)
        if (address.isNotEmpty()) {
            serversPingRepository.getTimeout(address)
        } else {
            loge("RelaysPingInteractor no address for $name")
            NO_CONNECTION
        }
    }
}
