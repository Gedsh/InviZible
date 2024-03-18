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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsRepository
import pan.alexander.tordnscrypt.domain.connection_records.entities.ConnectionData
import pan.alexander.tordnscrypt.domain.connection_records.entities.DnsRecord
import pan.alexander.tordnscrypt.domain.connection_records.entities.PacketRecord
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.tor_apps.ApplicationData.Companion.SPECIAL_UID_KERNEL
import pan.alexander.tordnscrypt.utils.enums.OperationMode
import javax.inject.Inject

@ExperimentalCoroutinesApi
class ConnectionRecordsRepositoryImpl @Inject constructor(
    private val connectionRecordsGetter: ConnectionRecordsGetter,
    private val nflogRecordsGetter: NflogRecordsGetter
) : ConnectionRecordsRepository {

    private val modulesStatus = ModulesStatus.getInstance()

    @Volatile
    private var savedMode = modulesStatus.mode

    override fun getRawConnectionRecords(): List<ConnectionData> =
        if (isVpnMode()) {

            if (modulesStatus.mode != savedMode) {
                stopNflogRecordsGetter()
                savedMode = modulesStatus.mode
            }

            connectionRecordsGetter.getConnectionRawRecords().toSortedKeysList()

        } else if (isFixTTL()) {

            (connectionRecordsGetter.getConnectionRawRecords() + nflogRecordsGetter.getConnectionRawRecords())
                .toSortedKeysList()


        } else if (isRootMode()) {

            if (modulesStatus.mode != savedMode) {
                stopConnectionRecordsGetter()
                savedMode = modulesStatus.mode
            }

            nflogRecordsGetter.getConnectionRawRecords().toSortedKeysList()
        } else {
            emptyList()
        }

    override fun clearConnectionRawRecords() {
        if (isVpnMode() || isFixTTL()) {
            connectionRecordsGetter.clearConnectionRawRecords()
        } else if (isRootMode()) {
            nflogRecordsGetter.clearConnectionRawRecords()
        }
    }

    override fun connectionRawRecordsNoMoreRequired() {
        if (isVpnMode() || isFixTTL()) {
            connectionRecordsGetter.connectionRawRecordsNoMoreRequired()
        }
    }

    private fun stopConnectionRecordsGetter() = with(connectionRecordsGetter) {
        clearConnectionRawRecords()
        connectionRawRecordsNoMoreRequired()
    }

    private fun stopNflogRecordsGetter() = with(nflogRecordsGetter) {
        clearConnectionRawRecords()
    }

    private fun isVpnMode() = modulesStatus.mode == OperationMode.VPN_MODE

    private fun isRootMode() = modulesStatus.mode == OperationMode.ROOT_MODE
            && !modulesStatus.isUseModulesWithRoot

    private fun isFixTTL() = modulesStatus.isFixTTL
            && modulesStatus.mode == OperationMode.ROOT_MODE
            && !modulesStatus.isUseModulesWithRoot

    private fun Map<ConnectionData, Long>.toSortedKeysList(): List<ConnectionData> = let { map ->
        map.entries.sortedBy { it.value }.map { it.key }
    }

}
