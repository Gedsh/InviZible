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

package pan.alexander.tordnscrypt.proxy

import android.content.Context
import android.content.SharedPreferences
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.DEFAULT_PROXY_PORT
import pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX
import pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.tordnscrypt.utils.Constants.QUAD_DNS_41
import pan.alexander.tordnscrypt.utils.connectionchecker.ProxyAuthManager.setDefaultAuth
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor
import pan.alexander.tordnscrypt.utils.filemanager.FileManager
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_OUTBOUND_PROXY
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.I2PD_OUTBOUND_PROXY
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_PASS
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.PROXY_USER
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_PROXY
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import javax.inject.Inject
import javax.inject.Named

class ProxyHelper @Inject constructor(
    private val context: Context,
    private val pathVars: PathVars,
    private val executor: CoroutineExecutor,
    @Named(DEFAULT_PREFERENCES_NAME) private val defaultPreferences: SharedPreferences
) {

    fun manageProxy(
        server: String,
        port: String,
        serverOrPortChanged: Boolean,
        enableNonTorProxy: Boolean,
        enableDNSCryptProxy: Boolean,
        enableTorProxy: Boolean,
        enableItpdProxy: Boolean
    ) {

        val modulesStatus = ModulesStatus.getInstance()

        val nonTorProxified = defaultPreferences.getBoolean(USE_PROXY, false)
        val dnsCryptProxified = defaultPreferences.getBoolean(DNSCRYPT_OUTBOUND_PROXY, false)
        val torProxified = defaultPreferences.getBoolean(TOR_OUTBOUND_PROXY, false)
        val itpdProxified = defaultPreferences.getBoolean(I2PD_OUTBOUND_PROXY, false)

        val proxyAddr = if (server.isNotEmpty() && port.isNotEmpty()) {
            "$server:$port"
        } else {
            "$LOOPBACK_ADDRESS:$DEFAULT_PROXY_PORT"
        }

        executor.submit("ProxyHelper manageProxy") {
            val dnsCryptSettingChanged = enableDNSCryptProxy xor dnsCryptProxified
            if (dnsCryptSettingChanged || serverOrPortChanged) {
                manageDNSCryptProxy(pathVars.dnscryptConfPath, proxyAddr, enableDNSCryptProxy)
                defaultPreferences.edit().putBoolean(DNSCRYPT_OUTBOUND_PROXY, enableDNSCryptProxy)
                    .apply()

                if (modulesStatus.dnsCryptState == ModuleState.RUNNING && (enableDNSCryptProxy || dnsCryptSettingChanged)) {
                    ModulesRestarter.restartDNSCrypt(context)
                }
            }
            val torSettingChanged = enableTorProxy xor torProxified
            if (torSettingChanged || serverOrPortChanged) {
                mangeTorProxy(pathVars.torConfPath, proxyAddr, enableTorProxy)
                defaultPreferences.edit().putBoolean(TOR_OUTBOUND_PROXY, enableTorProxy).apply()
                if (modulesStatus.torState == ModuleState.RUNNING && (enableTorProxy || torSettingChanged)) {
                    ModulesRestarter.restartTor(context)
                }
            }
            val itpdSettingChanged = enableItpdProxy xor itpdProxified
            if (itpdSettingChanged || serverOrPortChanged) {
                manageITPDProxy(pathVars.itpdConfPath, proxyAddr, enableItpdProxy)
                defaultPreferences.edit().putBoolean(I2PD_OUTBOUND_PROXY, enableItpdProxy).apply()
                if (modulesStatus.itpdState == ModuleState.RUNNING && (enableItpdProxy || itpdSettingChanged)) {
                    ModulesRestarter.restartITPD(context)
                }
            }

            val nonTorProxySettingsChanged = enableNonTorProxy xor nonTorProxified
            if (dnsCryptSettingChanged || torSettingChanged || itpdSettingChanged
                || nonTorProxySettingsChanged || serverOrPortChanged
            ) {
                defaultPreferences.edit().putBoolean(USE_PROXY, enableNonTorProxy).apply()
                modulesStatus.setIptablesRulesUpdateRequested(context, true)
            }

        }
    }

    fun checkProxyConnectivity(
        proxyHost: String,
        proxyPort: Int,
        proxyUser: String,
        proxyPass: String
    ): String {
        val start = System.currentTimeMillis()

        try {
            val dnsCryptFallbackRes = pathVars.dnsCryptFallbackRes
                .split(Regex(", ?"))
                .filter { it.matches(Regex(IPv4_REGEX)) }
                .shuffled()
                .getOrElse(0) { QUAD_DNS_41 }
            val sockaddr: SocketAddress =
                InetSocketAddress(InetAddress.getByName(dnsCryptFallbackRes), 53)
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort))

            Socket(proxy).use {
                setDefaultAuth(proxyUser, proxyPass)
                it.connect(sockaddr, CHECK_CONNECTION_TIMEOUT_MSEC)
                it.soTimeout = CHECK_CONNECTION_TIMEOUT_MSEC

                if (!it.isConnected) {
                    throw IllegalStateException("unable to connect to $dnsCryptFallbackRes")
                }
            }
        } catch (e: Exception) {
            return e.message ?: ""
        }

        return (System.currentTimeMillis() - start).toString()
    }

    private fun manageDNSCryptProxy(dnsCryptConfPath: String?, address: String, enable: Boolean) {

        if (dnsCryptConfPath == null) {
            return
        }

        val dnsCryptProxyToml = FileManager.readTextFileSynchronous(context, dnsCryptConfPath)
        for (i in dnsCryptProxyToml.indices) {
            val line = dnsCryptProxyToml[i]
            if (line.contains("proxy = ")) {
                if (enable) {
                    dnsCryptProxyToml[i] = "proxy = 'socks5://$address'"
                } else {
                    dnsCryptProxyToml[i] = "#proxy = 'socks5://$address'"
                }
            } else if (enable && line.contains("force_tcp")) {
                dnsCryptProxyToml[i] = "force_tcp = true"
            }
        }
        FileManager.writeTextFileSynchronous(context, dnsCryptConfPath, dnsCryptProxyToml)
    }

    private fun mangeTorProxy(torConfPath: String?, address: String, enable: Boolean) {

        if (torConfPath == null) {
            return
        }

        val proxyUser = defaultPreferences.getString(PROXY_USER, "") ?: ""
        val proxyPass = defaultPreferences.getString(PROXY_PASS, "") ?: ""
        val auth = proxyUser.isNotEmpty() || proxyPass.isNotEmpty()

        var clientOnlyLinePosition = -1
        var socksProxyLineExist = false
        var socksProxyLinePosition = -1
        var socksProxyUserNameLineExist = false
        var socksProxyPasswordLineExist = false
        val torConf = FileManager.readTextFileSynchronous(context, torConfPath)
        val torConfToSave = mutableListOf<String>()
        for (i in torConf.indices) {
            var line = torConf[i]
            if (line.contains("Socks5Proxy ")) {
                line = when {
                    socksProxyLineExist -> ""
                    enable -> "Socks5Proxy $address"
                    else -> "#Socks5Proxy $address"
                }
                socksProxyLineExist = true
                socksProxyLinePosition = i
            } else if (line.contains("ClientOnly ")) {
                clientOnlyLinePosition = i
            } else if (line.contains("Socks5ProxyUsername ")) {
                line = when {
                    socksProxyUserNameLineExist -> ""
                    enable && auth -> "Socks5ProxyUsername $proxyUser"
                    else -> "#Socks5ProxyUsername $proxyUser"
                }
                socksProxyUserNameLineExist = true
            } else if (line.contains("Socks5ProxyPassword ")) {
                line = when {
                    socksProxyPasswordLineExist -> ""
                    enable && auth -> "Socks5ProxyPassword $proxyPass"
                    else -> "#Socks5ProxyPassword $proxyPass"
                }
                socksProxyPasswordLineExist = true
            }

            if (line.isNotEmpty()) {
                torConfToSave.add(line)
            }
        }
        if (enable && !socksProxyLineExist && clientOnlyLinePosition >= 0) {
            torConfToSave.add(clientOnlyLinePosition, "Socks5Proxy $address")
            socksProxyLinePosition = clientOnlyLinePosition
        }
        if (enable && auth && !socksProxyUserNameLineExist && socksProxyLinePosition >= 0) {
            torConfToSave.add(socksProxyLinePosition + 1, "Socks5ProxyUsername $proxyUser")
        }
        if (enable && auth && !socksProxyPasswordLineExist && socksProxyLinePosition >= 0) {
            torConfToSave.add(socksProxyLinePosition + 2, "Socks5ProxyPassword $proxyPass")
        }
        FileManager.writeTextFileSynchronous(context, torConfPath, torConfToSave)
    }

    private fun manageITPDProxy(itpdConfPath: String?, address: String, enable: Boolean) {
        if (itpdConfPath == null) {
            return
        }

        val itpdConf = FileManager.readTextFileSynchronous(context, itpdConfPath)
        for (i in itpdConf.indices) {
            val line = itpdConf[i]
            if (line.contains("ntcpproxy")) {
                if (enable) {
                    itpdConf[i] = "ntcpproxy = socks://$address"
                } else {
                    itpdConf[i] = "#ntcpproxy = socks://$address"
                }
            } else if (line.matches(Regex("^#?proxy = (socks|http)://.+"))) {
                if (enable) {
                    itpdConf[i] = "proxy = socks://$address"
                } else {
                    itpdConf[i] = "#proxy = socks://$address"
                }
            }
        }
        FileManager.writeTextFileSynchronous(context, itpdConfPath, itpdConf)
    }

    companion object {
        const val CHECK_CONNECTION_TIMEOUT_MSEC = 5000
    }
}
