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

package pan.alexander.tordnscrypt.modules

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import pan.alexander.tordnscrypt.di.SharedPreferencesModule.Companion.DEFAULT_PREFERENCES_NAME
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.REMOTE_CONTROL
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val STATUS_ACTION = "pan.alexander.tordnscrypt.STATUS_ACTION"

private const val STATUS_ARG = "STATUS"
private const val STATUS_RUNNING = "RUNNING"
private const val STATUS_READY = "READY"
private const val STATUS_STOPPED = "STOPPED"

private const val MODULE_ARG = "MODULE"
private const val DNSCRYPT = "DNSCRYPT"
private const val TOR = "TOR"
private const val I2PD = "I2PD"

private const val DNSCRYPT_DNS_PORT_ARG = "DNSCRYPT_DNS_PORT"

private const val TOR_DNS_PORT_ARG = "TOR_DNS_PORT"
private const val TOR_SOCKS_PROXY_PORT_ARG = "TOR_SOCKS_PROXY_PORT"
private const val TOR_TRANSPARENT_PROXY_PORT_ARG = "TOR_TRANSPARENT_PROXY_PORT"
private const val TOR_HTTP_PROXY_PORT_ARG = "TOR_HTTP_PROXY_PORT"

private const val I2PD_HTTP_PROXY_PORT_ARG = "I2PD_HTTP_PROXY_PORT"

@Singleton
class ModulesStatusBroadcaster @Inject constructor(
    private val context: Context,
    private val pathVars: PathVars,
    @Named(DEFAULT_PREFERENCES_NAME)
    defaultSharedPreferences: SharedPreferences
) {

    @Volatile
    private var remoteControlActive = defaultSharedPreferences
        .getBoolean(REMOTE_CONTROL, false)

    fun broadcastDNSCryptRunning() {
        if (remoteControlActive) {
            getDNSCryptIntent().also {
                it.putExtra(STATUS_ARG, STATUS_RUNNING)
                context.sendBroadcast(it)
                logi("Broadcast DNSCrypt running")
            }
        }
    }

    fun broadcastDNSCryptReady() {
        if (remoteControlActive) {
            getDNSCryptIntent().also {
                it.putExtra(STATUS_ARG, STATUS_READY)
                context.sendBroadcast(it)
                logi("Broadcast DNSCrypt ready")
            }
        }
    }

    fun broadcastDNSCryptStopped() {
        if (remoteControlActive) {
            getDNSCryptIntent().also {
                it.putExtra(STATUS_ARG, STATUS_STOPPED)
                context.sendBroadcast(it)
                logi("Broadcast DNSCrypt stopped")
            }
        }
    }

    fun broadcastTorRunning() {
        if (remoteControlActive) {
            getTorIntent().also {
                it.putExtra(STATUS_ARG, STATUS_RUNNING)
                context.sendBroadcast(it)
                logi("Broadcast Tor running")
            }
        }
    }

    fun broadcastTorReady() {
        if (remoteControlActive) {
            getTorIntent().also {
                it.putExtra(STATUS_ARG, STATUS_READY)
                context.sendBroadcast(it)
                logi("Broadcast Tor ready")
            }
        }
    }

    fun broadcastTorStopped() {
        if (remoteControlActive) {
            getTorIntent().also {
                it.putExtra(STATUS_ARG, STATUS_STOPPED)
                context.sendBroadcast(it)
                logi("Broadcast Tor stopped")
            }
        }
    }

    fun broadcastI2PDRunning() {
        if (remoteControlActive) {
            getI2PDIntent().also {
                it.putExtra(STATUS_ARG, STATUS_RUNNING)
                context.sendBroadcast(it)
                logi("Broadcast I2PD running")
            }
        }
    }

    fun broadcastI2PDReady() {
        if (remoteControlActive) {
            getI2PDIntent().also {
                it.putExtra(STATUS_ARG, STATUS_READY)
                context.sendBroadcast(it)
                logi("Broadcast I2PD ready")
            }
        }
    }

    fun broadcastI2PDStopped() {
        if (remoteControlActive) {
            getI2PDIntent().also {
                it.putExtra(STATUS_ARG, STATUS_STOPPED)
                context.sendBroadcast(it)
                logi("Broadcast I2PD stopped")
            }
        }
    }

    private fun getDNSCryptIntent() =
        Intent().also {
            it.setAction(STATUS_ACTION)
            it.putExtra(MODULE_ARG, DNSCRYPT)
            it.putExtra(DNSCRYPT_DNS_PORT_ARG, pathVars.dnsCryptPort)
        }

    private fun getTorIntent() =
        Intent().also {
            it.setAction(STATUS_ACTION)
            it.putExtra(MODULE_ARG, TOR)
            it.putExtra(TOR_DNS_PORT_ARG, pathVars.torDNSPort)
            it.putExtra(TOR_SOCKS_PROXY_PORT_ARG, pathVars.torSOCKSPort)
            it.putExtra(TOR_TRANSPARENT_PROXY_PORT_ARG, pathVars.torTransPort)
            it.putExtra(TOR_HTTP_PROXY_PORT_ARG, pathVars.torHTTPTunnelPort)
        }

    private fun getI2PDIntent() =
        Intent().also {
            it.setAction(STATUS_ACTION)
            it.putExtra(MODULE_ARG, I2PD)
            it.putExtra(I2PD_HTTP_PROXY_PORT_ARG, pathVars.itpdHttpProxyPort)
        }

    fun onRemoteControlChanged(enabled: Boolean) {
        remoteControlActive = enabled
    }

}
