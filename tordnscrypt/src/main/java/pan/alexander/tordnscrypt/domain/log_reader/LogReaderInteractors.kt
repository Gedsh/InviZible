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

package pan.alexander.tordnscrypt.domain.log_reader

import pan.alexander.tordnscrypt.di.logreader.LogReaderScope
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsInteractor
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsInteractorInterface
import pan.alexander.tordnscrypt.domain.connection_records.OnConnectionRecordsUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.DNSCryptInteractor
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.OnDNSCryptLogUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.itpd.ITPDHtmlInteractor
import pan.alexander.tordnscrypt.domain.log_reader.itpd.ITPDInteractor
import pan.alexander.tordnscrypt.domain.log_reader.itpd.OnITPDHtmlUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.itpd.OnITPDLogUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.tor.OnTorLogUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.tor.TorInteractor
import javax.inject.Inject

@LogReaderScope
class LogReaderInteractors @Inject constructor(
    modulesLogRepository: ModulesLogRepository,
    private val connectionRecordsInteractor: ConnectionRecordsInteractor
) :
    DNSCryptInteractorInterface,
    TorInteractorInterface,
    ITPDInteractorInterface,
    ConnectionRecordsInteractorInterface {

    private val dnsCryptInteractor = DNSCryptInteractor(modulesLogRepository)
    private val torInteractor = TorInteractor(modulesLogRepository)
    private val itpdInteractor = ITPDInteractor(modulesLogRepository)
    private val itpdHtmlInteractor = ITPDHtmlInteractor(modulesLogRepository)

    private val logReaderLoop = LogReaderLoop(
        dnsCryptInteractor,
        torInteractor,
        itpdInteractor,
        itpdHtmlInteractor,
        connectionRecordsInteractor
    )

    override fun <T: OnDNSCryptLogUpdatedListener> addOnDNSCryptLogUpdatedListener(onDNSCryptLogUpdatedListener: T) {
        dnsCryptInteractor.addListener(onDNSCryptLogUpdatedListener)
        logReaderLoop.startLogsParser()
    }

    override fun <T: OnDNSCryptLogUpdatedListener> removeOnDNSCryptLogUpdatedListener(onDNSCryptLogUpdatedListener: T) {
        dnsCryptInteractor.removeListener(onDNSCryptLogUpdatedListener)
    }

    override fun <T: OnTorLogUpdatedListener> addOnTorLogUpdatedListener(onTorLogUpdatedListener: T) {
        torInteractor.addListener(onTorLogUpdatedListener)
        logReaderLoop.startLogsParser()
    }

    override fun <T: OnTorLogUpdatedListener> removeOnTorLogUpdatedListener(onTorLogUpdatedListener: T) {
        torInteractor.removeListener(onTorLogUpdatedListener)
    }

    override fun <T: OnITPDLogUpdatedListener> addOnITPDLogUpdatedListener(onITPDLogUpdatedListener: T) {
        itpdInteractor.addListener(onITPDLogUpdatedListener)
        logReaderLoop.startLogsParser()
    }

    override fun <T: OnITPDLogUpdatedListener> removeOnITPDLogUpdatedListener(onITPDLogUpdatedListener: T) {
        itpdInteractor.removeListener(onITPDLogUpdatedListener)
    }

    override fun <T: OnITPDHtmlUpdatedListener> addOnITPDHtmlUpdatedListener(onITPDHtmlUpdatedListener: T) {
        itpdHtmlInteractor.addListener(onITPDHtmlUpdatedListener)
        logReaderLoop.startLogsParser()
    }

    override fun <T: OnITPDHtmlUpdatedListener> removeOnITPDHtmlUpdatedListener(onITPDHtmlUpdatedListener: T) {
        itpdHtmlInteractor.removeListener(onITPDHtmlUpdatedListener)
    }

    override fun <T: OnConnectionRecordsUpdatedListener> addOnConnectionRecordsUpdatedListener(onConnectionRecordsUpdatedListener: T) {
        connectionRecordsInteractor.addListener(onConnectionRecordsUpdatedListener)
        logReaderLoop.startLogsParser()
    }

    override fun <T: OnConnectionRecordsUpdatedListener> removeOnConnectionRecordsUpdatedListener(onConnectionRecordsUpdatedListener: T) {
        connectionRecordsInteractor.removeListener(onConnectionRecordsUpdatedListener)
    }

    override fun clearConnectionRecords() {
        connectionRecordsInteractor.clearConnectionRecords()
    }
}
