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

package pan.alexander.tordnscrypt.data.connection_records

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecord
import pan.alexander.tordnscrypt.utils.root.RootExecService
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN
import pan.alexander.tordnscrypt.vpn.service.ServiceVPN.VPNBinder
import java.lang.ref.WeakReference

class ConnectionRecordsGetter {
    private val applicationContext = App.instance.applicationContext
    @Volatile private var serviceConnection: ServiceConnection? = null
    @Volatile private var serviceVPN: WeakReference<ServiceVPN?>? = null
    @Volatile private var bound = false

    fun getConnectionRawRecords(): List<ConnectionRecord?> {
        if (serviceVPN == null || serviceConnection == null) {
            bindToVPNService(applicationContext)
        }

        if (!bound) {
            return emptyList()
        }

        lockConnectionRawRecordsListForRead(true)

        val rawRecords = ArrayList<ConnectionRecord?>(serviceVPN?.get()?.dnsQueryRawRecords ?: emptyList())

        lockConnectionRawRecordsListForRead(false)

        return rawRecords
    }

    fun clearConnectionRawRecords() {
        serviceVPN?.get()?.clearDnsQueryRawRecords()
    }

    fun connectionRawRecordsNoMoreRequired() {
        unbindVPNService(applicationContext)
    }

    private fun lockConnectionRawRecordsListForRead(lock: Boolean) {
        serviceVPN?.get()?.lockDnsQueryRawRecordsListForRead(lock)
    }

    @Synchronized
    private fun bindToVPNService(context: Context?) {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                if (service is VPNBinder) {
                    serviceVPN = WeakReference(service.service)
                    bound = true
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                bound = false
            }
        }

        context?.let {
            val intent = Intent(context, ServiceVPN::class.java)
            serviceConnection?.let { context.bindService(intent, it, 0) }
        }
    }

    private fun unbindVPNService(context: Context?) {
        if (bound) {
            try {
                context?.let {
                    serviceConnection?.let { context.unbindService(it) }
                }
            } catch (e: Exception) {
                Log.w(
                    RootExecService.LOG_TAG,
                    "ConnectionRecordsGetter unbindVPNService exception " + e.message + " " + e.cause
                )
            }
            bound = false
            serviceVPN = null
            serviceConnection = null
        }

    }
}
