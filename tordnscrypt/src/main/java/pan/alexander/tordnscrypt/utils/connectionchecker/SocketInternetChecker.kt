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

package pan.alexander.tordnscrypt.utils.connectionchecker

import pan.alexander.tordnscrypt.utils.connectionchecker.ProxyAuthManager.setDefaultAuth
import java.lang.Exception
import java.net.*
import javax.inject.Inject

private const val CONNECT_TIMEOUT_SEC = 50
private const val PING_TIMEOUT_SEC = 1
private const val CHECK_ADDRESS_REACHABLE_TIMEOUT_SEC = 3

class SocketInternetChecker @Inject constructor() {

    fun checkConnectionAvailability(
        ip: String,
        port: Int,
        proxyAddress: String,
        proxyPort: Int,
        proxyUser: String,
        proxyPass: String,
        connectTimeout: Int = CONNECT_TIMEOUT_SEC,
        reachableTimeout: Int = CHECK_ADDRESS_REACHABLE_TIMEOUT_SEC
    ): Boolean {

        var socket: Socket? = null

        try {
            socket = if (isProxyUsed(proxyAddress, proxyPort)) {
                setDefaultAuth(proxyUser, proxyPass)
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

            socket.connect(sockAddress, connectTimeout * 1000)
            socket.soTimeout = 100

            return if (isProxyUsed(proxyAddress, proxyPort)) {
                socket.inetAddress.isReachable(reachableTimeout * 1000)
            } else {
                socket.isConnected
            }

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
        proxyPort: Int,
        proxyUser: String,
        proxyPass: String
    ): Int {

        var socket: Socket? = null
        val timeStart = System.currentTimeMillis()

        try {
            socket = if (isProxyUsed(proxyAddress, proxyPort)) {
                setDefaultAuth(proxyUser, proxyPass)
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
            socket.soTimeout = PING_TIMEOUT_SEC * 1000

            if (isProxyUsed(proxyAddress, proxyPort)) {
                socket.shutdownOutput()
                socket.getInputStream().read(byteArrayOf(0))
                return ((System.currentTimeMillis() - timeStart) / 2).toInt()
            } else {
                if (socket.isConnected) {
                    val time = System.currentTimeMillis()
                    socket.shutdownOutput()
                    return (time - timeStart).toInt()
                }
            }

            return NO_CONNECTION

        } finally {
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun isProxyUsed(
        proxyAddress: String,
        proxyPort: Int
    ) = proxyAddress.isNotBlank() && proxyPort != 0

    companion object {
        const val NO_CONNECTION = -1
    }

}
