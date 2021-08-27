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

package pan.alexander.tordnscrypt.data.connection_checker

import android.content.Context
import android.util.Log
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val READ_TIMEOUT_SEC = 15
private const val CONNECT_TIMEOUT_SEC = 15
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 9.0.1; " +
        "Mi Mi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Mobile Safari/537.36"

class InternetChecker(val site: String, private val withTor: Boolean) {

    var con: HttpURLConnection? = null

    fun checkConnectionAvailability(context: Context?): Boolean {
        context ?: return false
        var result = false

        try {
            result = checkConnection(context)
            return result
        } catch (e: Exception) {
            Log.w(LOG_TAG, "InternetChecker checkConnectionAvailability exception ${e.message} ${e.cause}")
        } finally {
            if (result) {
                con?.disconnect()
            }
        }

        return false
    }

    private fun checkConnection(context: Context?): Boolean {
        val url = URL(site)

        con = if (withTor) {
            val proxy = Proxy(
                Proxy.Type.HTTP,
                InetSocketAddress(
                    "127.0.0.1",
                    PathVars.getInstance(context).torHTTPTunnelPort.toInt()
                )
            )

            url.openConnection(proxy) as HttpsURLConnection
        } else {
            url.openConnection() as HttpsURLConnection
        }

        val connection = con ?: return false

        connection.apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_SEC * 1000
            readTimeout = READ_TIMEOUT_SEC * 1000
            setRequestProperty("User-Agent", USER_AGENT)
            connect()
        }

        val code = connection.responseCode
        return code == HttpURLConnection.HTTP_OK
    }
}
