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

package pan.alexander.tordnscrypt.data.connection_records

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionData
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN.VPNBinder
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ConnectionRecordsGetter @Inject constructor(
    private val context: Context
) {

    private val bound = AtomicBoolean(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            if (service is VPNBinder) {
                serviceVPN = WeakReference(service.service)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (bound.compareAndSet(true, false)) {
                serviceVPN = null
            }
        }
    }

    @Volatile
    private var serviceVPN: WeakReference<ServiceVPN?>? = null

    fun getConnectionRawRecords(): Map<ConnectionData, Boolean> {
        if (bound.compareAndSet(false, true)) {
            logi("ConnectionRecordsGetter bind to VPN service")
            bindToVPNService()
        }

        val rawRecords = try {
            serviceVPN?.get()?.dnsQueryRawRecords ?: emptyMap<ConnectionData, Boolean>()
        } catch (e: Exception) {
            logw("ConnectionRecordsGetter getConnectionRawRecords", e)
            emptyMap<ConnectionData, Boolean>()
        }

        return rawRecords
    }

    fun clearConnectionRawRecords() {
        try {
            serviceVPN?.get()?.clearDnsQueryRawRecords()
        } catch (e: java.lang.Exception) {
            logw("ConnectionRecordsGetter clearConnectionRawRecords", e)
        }
    }

    fun connectionRawRecordsNoMoreRequired() {
        unbindVPNService()
    }

    @Synchronized
    private fun bindToVPNService() {
        val intent = Intent(context, ServiceVPN::class.java)
        serviceConnection.let {
            context.bindService(intent, it, Context.BIND_IMPORTANT)
        }
    }

    private fun unbindVPNService() {
        if (bound.compareAndSet(true, false)) {
            logi("ConnectionRecordsGetter unbind VPN service")

            try {
                serviceConnection.let { context.unbindService(it) }
            } catch (e: Exception) {
                logw(
                    "ConnectionRecordsGetter unbindVPNService exception "
                            + e.message + " "
                            + e.cause + "\n"
                            + Log.getStackTraceString(e)
                )
            }
        }

    }
}
