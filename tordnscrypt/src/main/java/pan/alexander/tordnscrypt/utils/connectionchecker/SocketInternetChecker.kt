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

package pan.alexander.tordnscrypt.utils.connectionchecker

import java.lang.Exception
import java.net.*
import javax.inject.Inject

private const val CONNECT_TIMEOUT_SEC = 50
private const val PING_TIMEOUT_SEC = 1

class SocketInternetChecker @Inject constructor() {

    fun checkConnectionAvailability(
        ip: String,
        port: Int,
        proxyAddress: String,
        proxyPort: Int
    ): Boolean {

        var socket: Socket? = null

        try {
            socket = if (proxyAddress.isNotBlank() && proxyPort != 0) {
                val proxySockAdr: SocketAddress = InetSocketAddress(
                    proxyAddress,
                    proxyPort
                )
                val proxy = Proxy(Proxy.Type.SOCKS, proxySockAdr)
                Socket(proxy)
            } else {
                Socket()
            }

            val sockAddress: SocketAddress =
                InetSocketAddress(InetAddress.getByName(ip), port)

            socket.connect(sockAddress, CONNECT_TIMEOUT_SEC * 1000)

            return socket.isConnected
        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    fun checkConnectionPing(
        ip: String,
        port: Int,
        proxyAddress: String,
        proxyPort: Int
    ): Int {

        var socket: Socket? = null
        val timeStart = System.currentTimeMillis()

        try {
            socket = if (proxyAddress.isNotBlank() && proxyPort != 0) {
                val proxySockAdr: SocketAddress = InetSocketAddress(
                    proxyAddress,
                    proxyPort
                )
                val proxy = Proxy(Proxy.Type.SOCKS, proxySockAdr)
                Socket(proxy)
            } else {
                Socket()
            }

            val sockAddress: SocketAddress =
                InetSocketAddress(InetAddress.getByName(ip), port)

            socket.connect(sockAddress, PING_TIMEOUT_SEC * 1000)

            return if (socket.isConnected) {
                (System.currentTimeMillis() - timeStart).toInt()
            } else {
                NO_CONNECTION
            }

        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    companion object {
        const val NO_CONNECTION = -1
    }

}
