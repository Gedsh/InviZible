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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.arp

import android.content.Context
import pan.alexander.tordnscrypt.di.arp.ArpScope
import pan.alexander.tordnscrypt.utils.connectionchecker.NetworkChecker
import javax.inject.Inject

@ArpScope
class ConnectionManager @Inject constructor(
    private val context: Context
) {
    @Volatile
    var connectionAvailable = false
    @Volatile
    var cellularActive = false
    @Volatile
    var wifiActive = false
    @Volatile
    var ethernetActive = false

    fun updateActiveNetworks() {
        cellularActive = NetworkChecker.isCellularActive(context)
        wifiActive = NetworkChecker.isWifiActive(context)
        ethernetActive = NetworkChecker.isEthernetActive(context)
    }

    fun clearActiveNetworks() {
        cellularActive = false
        wifiActive = false
        ethernetActive = false
    }

    fun isConnected(): Boolean = NetworkChecker.isNetworkAvailable(context)
}
