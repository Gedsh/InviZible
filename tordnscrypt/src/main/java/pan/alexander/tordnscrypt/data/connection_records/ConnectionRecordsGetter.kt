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

package pan.alexander.tordnscrypt.data.connection_records

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import pan.alexander.tordnscrypt.di.logreader.LogReaderSubcomponent.Companion.LOG_READER_CONTEXT
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecord
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN.VPNBinder
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named

class ConnectionRecordsGetter @Inject constructor(
    @Named(LOG_READER_CONTEXT) private val context: Context
) {

    private val bound = AtomicBoolean(false)

    @Volatile
    private var serviceConnection: ServiceConnection? = null
    @Volatile
    private var serviceVPN: WeakReference<ServiceVPN?>? = null

    fun getConnectionRawRecords(): List<ConnectionRecord?> {
        if (serviceVPN == null && serviceConnection == null
            && bound.compareAndSet(false, true)
        ) {
            logi("ConnectionRecordsGetter bind to VPN service")
            bindToVPNService()
        }

        if (!bound.get()) {
            return emptyList()
        }

        lockConnectionRawRecordsListForRead(true)

        val rawRecords = ArrayList<ConnectionRecord?>(
            serviceVPN?.get()?.dnsQueryRawRecords ?: emptyList()
        )

        lockConnectionRawRecordsListForRead(false)

        return rawRecords
    }

    fun clearConnectionRawRecords() {
        serviceVPN?.get()?.clearDnsQueryRawRecords()
    }

    fun connectionRawRecordsNoMoreRequired() {
        unbindVPNService()
    }

    private fun lockConnectionRawRecordsListForRead(lock: Boolean) {
        serviceVPN?.get()?.lockDnsQueryRawRecordsListForRead(lock)
    }

    @Synchronized
    private fun bindToVPNService() {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (service is VPNBinder) {
                    serviceVPN = WeakReference(service.service)
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceVPN = null
                serviceConnection = null
            }
        }

        val intent = Intent(context, ServiceVPN::class.java)
        serviceConnection?.let {
            context.bindService(intent, it, Context.BIND_IMPORTANT)
        }
    }

    private fun unbindVPNService() {
        if (bound.compareAndSet(true, false)) {
            logi("ConnectionRecordsGetter unbind VPN service")
            try {
                serviceConnection?.let { context.unbindService(it) }
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
