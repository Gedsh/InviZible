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

import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsInteractor
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.DNSCryptInteractor
import pan.alexander.tordnscrypt.domain.log_reader.itpd.ITPDHtmlInteractor
import pan.alexander.tordnscrypt.domain.log_reader.itpd.ITPDInteractor
import pan.alexander.tordnscrypt.domain.log_reader.tor.TorInteractor
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.enums.ModuleState

class LogReaderFacade(
    private val dnsCryptInteractor: DNSCryptInteractor,
    private val torInteractor: TorInteractor,
    private val itpdInteractor: ITPDInteractor,
    private val itpdHtmlInteractor: ITPDHtmlInteractor,
    private val connectionRecordsInteractor: ConnectionRecordsInteractor
) {
    private val modulesStatus = ModulesStatus.getInstance()

    fun parseDNSCryptLog() {
        if (dnsCryptInteractor.hasAnyListener()) {
            dnsCryptInteractor.parseDNSCryptLog()
        } else {
            dnsCryptInteractor.resetParserState()
        }
    }

    fun parseTorLog() {
        if (torInteractor.hasAnyListener()) {
            torInteractor.parseTorLog()
        } else {
            torInteractor.resetParserState()
        }
    }

    fun parseITPDLog() {
        if (itpdInteractor.hasAnyListener()) {
            itpdInteractor.parseITPDLog()
        } else {
            itpdInteractor.resetParserState()
        }
    }

    fun parseITPDHTML() {
        if (itpdHtmlInteractor.hasAnyListener()) {
            itpdHtmlInteractor.parseITPDHTML()
        } else {
            itpdHtmlInteractor.resetParserState()
        }
    }

    fun convertConnectionRecords() {
        if (connectionRecordsInteractor.hasAnyListener()) {
            connectionRecordsInteractor.convertRecords()
        }
    }

    fun isAnyListenerAvailable(): Boolean {
        return dnsCryptInteractor.hasAnyListener()
                || torInteractor.hasAnyListener()
                || itpdInteractor.hasAnyListener()
                || itpdHtmlInteractor.hasAnyListener()
                || connectionRecordsInteractor.hasAnyListener()
    }

    fun isModulesStateNotChanging(): Boolean {
        return (modulesStatus.dnsCryptState == ModuleState.STOPPED ||
                modulesStatus.dnsCryptState == ModuleState.FAULT ||
                modulesStatus.dnsCryptState == ModuleState.RUNNING && modulesStatus.isDnsCryptReady)
                && (modulesStatus.torState == ModuleState.STOPPED ||
                modulesStatus.torState == ModuleState.FAULT ||
                modulesStatus.torState == ModuleState.RUNNING && modulesStatus.isTorReady)
                && (modulesStatus.itpdState == ModuleState.STOPPED ||
                modulesStatus.itpdState == ModuleState.FAULT ||
                modulesStatus.itpdState == ModuleState.RUNNING && modulesStatus.isItpdReady)
    }
}