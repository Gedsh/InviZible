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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.connectionchecker

import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants
import java.lang.Exception
import java.net.*
import javax.inject.Inject

private const val CONNECT_TIMEOUT_SEC = 50

class SocketInternetChecker @Inject constructor(
    pathVars: PathVars
) {
    private val torSocksPort: String = pathVars.torSOCKSPort

    private var socket: Socket? = null

    fun checkConnectionAvailability(ip: String, port: Int, withTor: Boolean): Boolean {
        return try {
            tryCheckConnection(ip, port, withTor)
        } finally {
            tryCloseSocket()
        }
    }

    private fun tryCheckConnection(ip: String, port: Int, withTor: Boolean): Boolean {
        socket = if (withTor) {
            val proxySockAdr: SocketAddress = InetSocketAddress(
                Constants.LOOPBACK_ADDRESS, torSocksPort.toInt()
            )
            val proxy = Proxy(Proxy.Type.SOCKS, proxySockAdr)
            Socket(proxy)
        } else {
            Socket()
        }

        val sockAddress: SocketAddress =
            InetSocketAddress(InetAddress.getByName(ip), port)
        socket?.connect(sockAddress, CONNECT_TIMEOUT_SEC * 1000)

        return socket?.isConnected == true
    }

    private fun tryCloseSocket() {
        try {
            socket?.close()
        } catch (ignored: Exception) {
        }
    }
}
