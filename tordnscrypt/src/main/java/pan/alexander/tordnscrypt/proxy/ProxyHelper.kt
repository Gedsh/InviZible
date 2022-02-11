package pan.alexander.tordnscrypt.proxy

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

import android.content.Context
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.filemanager.FileManager
import java.net.*

object ProxyHelper {
    private const val defaultProxyAddress = "127.0.0.1:1080"

    fun manageProxy(context: Context?, server: String, port: String, serverOrPortChanged: Boolean,
                    enableDNSCryptProxy: Boolean, enableTorProxy: Boolean, enableItpdProxy: Boolean) {

        if (context == null) {
            return
        }

        val modulesStatus = ModulesStatus.getInstance()
        val pathVars = App.instance.daggerComponent.getPathVars().get()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val dnsCryptProxified = sharedPreferences.getBoolean("Enable proxy", false)
        val torProxified = sharedPreferences.getBoolean("Enable output Socks5Proxy", false)
        val itpdProxified = sharedPreferences.getBoolean("Enable ntcpproxy", false)

        val proxyAddr = if (server.isNotEmpty() && port.isNotEmpty()) {
            "$server:$port"
        } else {
            defaultProxyAddress
        }

        App.instance.daggerComponent.getCachedExecutor().submit {
            if ((enableDNSCryptProxy xor dnsCryptProxified) || serverOrPortChanged) {
                manageDNSCryptProxy(context, pathVars?.dnscryptConfPath, proxyAddr, enableDNSCryptProxy)
                sharedPreferences.edit().putBoolean("Enable proxy", enableDNSCryptProxy).apply()

                if (modulesStatus.dnsCryptState == ModuleState.RUNNING) {
                    ModulesRestarter.restartDNSCrypt(context)
                }
            }
            if ((enableTorProxy xor torProxified) || serverOrPortChanged) {
                mangeTorProxy(context, pathVars?.torConfPath, proxyAddr, enableTorProxy)
                sharedPreferences.edit().putBoolean("Enable output Socks5Proxy", enableTorProxy).apply()
                if (modulesStatus.torState == ModuleState.RUNNING) {
                    ModulesRestarter.restartTor(context)
                }
            }
            if ((enableItpdProxy xor itpdProxified) || serverOrPortChanged) {
                manageITPDProxy(context, pathVars?.itpdConfPath, proxyAddr, enableItpdProxy)
                sharedPreferences.edit().putBoolean("Enable ntcpproxy", enableItpdProxy).apply()
                if (modulesStatus.itpdState == ModuleState.RUNNING) {
                    ModulesRestarter.restartITPD(context)
                }
            }

            modulesStatus.setIptablesRulesUpdateRequested(context, true)
        }
    }

    fun checkProxyConnectivity(proxyHost: String, proxyPort: Int): String {
        val start = System.currentTimeMillis()

        try {
            val dnsCryptFallbackRes = App.instance.daggerComponent.getPathVars().get().dnsCryptFallbackRes
            val sockaddr: SocketAddress = InetSocketAddress(InetAddress.getByName(dnsCryptFallbackRes), 53)
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyHost, proxyPort))

            Socket(proxy).use {
                it.connect(sockaddr, 500)

                if (!it.isConnected) {
                    throw IllegalStateException("unable to connect to $dnsCryptFallbackRes")
                }
            }
        } catch (e: Exception) {
            return e.message ?: ""
        }

        return (System.currentTimeMillis() - start).toString()
    }

    private fun manageDNSCryptProxy(context: Context?, dnsCryptConfPath: String?, address: String, enable: Boolean) {

        if (context == null || dnsCryptConfPath == null) {
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
            } else if (!enable && line.contains("force_tcp")) {
                dnsCryptProxyToml[i] = "force_tcp = false"
            }
        }
        FileManager.writeTextFileSynchronous(context, dnsCryptConfPath, dnsCryptProxyToml)
    }

    private fun mangeTorProxy(context: Context?, torConfPath: String?, address: String, enable: Boolean) {

        if (context == null || torConfPath == null) {
            return
        }

        var clientOnlyLinePosition = -1
        var socksProxyLineExist = false
        val torConf = FileManager.readTextFileSynchronous(context, torConfPath)
        val torConfToSave = mutableListOf<String>()
        for (i in torConf.indices) {
            var line = torConf[i]
            if (line.contains("Socks5Proxy")) {
                line = when {
                    socksProxyLineExist -> ""
                    enable -> "Socks5Proxy $address"
                    else -> "#Socks5Proxy $address"
                }
                socksProxyLineExist = true
            } else if (line.contains("ClientOnly")) {
                clientOnlyLinePosition = i
            }

            if (line.isNotEmpty()) {
                torConfToSave.add(line)
            }
        }
        if (enable && !socksProxyLineExist && clientOnlyLinePosition >= 0) {
            torConfToSave.add(clientOnlyLinePosition, "Socks5Proxy $address")
        }
        FileManager.writeTextFileSynchronous(context, torConfPath, torConfToSave)
    }

    private fun manageITPDProxy(context: Context?, itpdConfPath: String?, address: String, enable: Boolean) {
        if (context == null || itpdConfPath == null) {
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
}
