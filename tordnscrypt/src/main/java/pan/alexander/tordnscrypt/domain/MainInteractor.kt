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

package pan.alexander.tordnscrypt.domain

import android.util.Log
import pan.alexander.tordnscrypt.data.ConnectionRecordsRepositoryImpl
import pan.alexander.tordnscrypt.data.ModulesLogRepositoryImpl
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsInteractor
import pan.alexander.tordnscrypt.domain.connection_records.OnConnectionRecordsUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.ScheduledExecutor
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.DNSCryptInteractor
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.OnDNSCryptLogUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.itpd.ITPDHtmlInteractor
import pan.alexander.tordnscrypt.domain.log_reader.itpd.ITPDInteractor
import pan.alexander.tordnscrypt.domain.log_reader.itpd.OnITPDHtmlUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.itpd.OnITPDLogUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.tor.OnTorLogUpdatedListener
import pan.alexander.tordnscrypt.domain.log_reader.tor.TorInteractor
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import java.lang.Exception
import java.util.concurrent.locks.ReentrantLock

private const val TIMER_INITIAL_DELAY = 1L
private const val TIMER_INITIAL_PERIOD = 1L
private const val TIMER_MAIN_PERIOD = 5L
private const val COUNTER_STARTING = 30
private const val COUNTER_STOPPING = 5

class MainInteractor {
    private val modulesStatus = ModulesStatus.getInstance()

    private val modulesLogRepository = ModulesLogRepositoryImpl()
    private val connectionsRepository = ConnectionRecordsRepositoryImpl()

    private var timer: ScheduledExecutor? = null
    private var displayPeriod: Long = 0

    private val dnsCryptInteractor = DNSCryptInteractor(modulesLogRepository)
    private val torInteractor = TorInteractor(modulesLogRepository)
    private val itpdInteractor = ITPDInteractor(modulesLogRepository)
    private val itpdHtmlInteractor = ITPDHtmlInteractor(modulesLogRepository)
    private val connectionRecordsInteractor = ConnectionRecordsInteractor(connectionsRepository)

    private val reentrantLock = ReentrantLock()

    private var counterStarting = COUNTER_STARTING
    private var counterStopping = COUNTER_STOPPING

    companion object {
        @Volatile
        private var mainInteractor: MainInteractor? = null

        fun getInstance(): MainInteractor {
            if (mainInteractor == null) {
                synchronized(MainInteractor::class.java) {
                    if (mainInteractor == null) {
                        mainInteractor = MainInteractor()
                    }
                }
            }
            return mainInteractor ?: MainInteractor()
        }
    }


    fun addOnDNSCryptLogUpdatedListener(onDNSCryptLogUpdatedListener: OnDNSCryptLogUpdatedListener) {
        dnsCryptInteractor.addListener(onDNSCryptLogUpdatedListener)
        startLogsParser()
    }

    fun removeOnDNSCryptLogUpdatedListener(onDNSCryptLogUpdatedListener: OnDNSCryptLogUpdatedListener) {
        dnsCryptInteractor.removeListener(onDNSCryptLogUpdatedListener)
    }

    fun addOnTorLogUpdatedListener(onTorLogUpdatedListener: OnTorLogUpdatedListener) {
        torInteractor.addListener(onTorLogUpdatedListener)
        startLogsParser()
    }

    fun removeOnTorLogUpdatedListener(onTorLogUpdatedListener: OnTorLogUpdatedListener) {
        torInteractor.removeListener(onTorLogUpdatedListener)
    }

    fun addOnITPDLogUpdatedListener(onITPDLogUpdatedListener: OnITPDLogUpdatedListener) {
        itpdInteractor.addListener(onITPDLogUpdatedListener)
        startLogsParser()
    }

    fun removeOnITPDLogUpdatedListener(onITPDLogUpdatedListener: OnITPDLogUpdatedListener) {
        itpdInteractor.removeListener(onITPDLogUpdatedListener)
    }

    fun addOnITPDHtmlUpdatedListener(onITPDHtmlUpdatedListener: OnITPDHtmlUpdatedListener) {
        itpdHtmlInteractor.addListener(onITPDHtmlUpdatedListener)
        startLogsParser()
    }

    fun removeOnITPDHtmlUpdatedListener(onITPDHtmlUpdatedListener: OnITPDHtmlUpdatedListener) {
        itpdHtmlInteractor.removeListener(onITPDHtmlUpdatedListener)
    }

    fun addOnConnectionRecordsUpdatedListener(onConnectionRecordsUpdatedListener: OnConnectionRecordsUpdatedListener) {
        connectionRecordsInteractor.addListener(onConnectionRecordsUpdatedListener)
        startLogsParser()
    }

    fun removeOnConnectionRecordsUpdatedListener(onConnectionRecordsUpdatedListener: OnConnectionRecordsUpdatedListener) {
        connectionRecordsInteractor.removeListener(onConnectionRecordsUpdatedListener)
    }

    fun clearConnectionRecords() {
        connectionRecordsInteractor.clearConnectionRecords()
    }

    private fun startLogsParser(period: Long = TIMER_INITIAL_PERIOD) {

        if (!reentrantLock.tryLock()) {
            return
        }

        try {
            if (timer?.isLooping() == true && period == displayPeriod) {
                return
            }

            Log.i(LOG_TAG, "MainInteractor startLogsParser")

            displayPeriod = period

            timer?.stopExecutor()

            timer = ScheduledExecutor(TIMER_INITIAL_DELAY, period)

            timer?.execute { parseLogs() }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "MainInteractor startLogsParser exception ${e.message} ${e.cause}")
        } finally {
            if (reentrantLock.isHeldByCurrentThread) {
                reentrantLock.unlock()
            }
        }
    }

    private fun stopLogsParser() {
        timer?.stopExecutor()
        timer = null
        connectionRecordsInteractor.stopConverter(true)
        mainInteractor = null

        Log.i(LOG_TAG, "MainInteractor stopLogsParser")
    }

    private fun parseLogs() {
        try {

            if (!dnsCryptInteractor.hasAnyListener()
                && !torInteractor.hasAnyListener()
                && !itpdInteractor.hasAnyListener()
                && !itpdHtmlInteractor.hasAnyListener()
                && !connectionRecordsInteractor.hasAnyListener()
            ) {
                counterStopping--
            } else {
                counterStopping = COUNTER_STOPPING
            }

            if (counterStopping <= 0) {
                stopLogsParser()
                return
            }

            if (dnsCryptInteractor.hasAnyListener()) {
                try {
                    dnsCryptInteractor.parseDNSCryptLog()
                } catch (e: Exception) {
                    Log.e(
                        LOG_TAG, "MainInteractor parseDNSCryptLog exception " +
                                "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
                    )
                }
            }
            if (torInteractor.hasAnyListener()) {
                try {
                    torInteractor.parseTorLog()
                } catch (e: Exception) {
                    Log.e(
                        LOG_TAG, "MainInteractor parseTorLog exception " +
                                "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
                    )
                }
            }
            if (itpdInteractor.hasAnyListener()) {
                try {
                    itpdInteractor.parseITPDLog()
                } catch (e: Exception) {
                    Log.e(
                        LOG_TAG, "MainInteractor parseITPDLog exception " +
                                "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
                    )
                }
            }
            if (itpdHtmlInteractor.hasAnyListener()) {
                try {
                    itpdHtmlInteractor.parseITPDHTML()
                } catch (e: Exception) {
                    Log.e(
                        LOG_TAG, "MainInteractor parseITPDHTML exception " +
                                "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
                    )
                }

            }
            if (connectionRecordsInteractor.hasAnyListener()) {
                try {
                    connectionRecordsInteractor.convertRecords()
                } catch (e: Exception) {
                    Log.e(
                        LOG_TAG, "MainInteractor convertRecords exception " +
                                "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
                    )
                }

            }

            if (isModulesStateNotChanging()) {
                counterStarting--
            } else {
                counterStarting = COUNTER_STARTING
            }

            if (counterStarting == 0) {
                startLogsParser(TIMER_MAIN_PERIOD)
                counterStarting = COUNTER_STARTING
            }

        } catch (e: Exception) {
            Log.e(
                LOG_TAG, "MainInteractor parseLogs exception " +
                        "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
            )
        }
    }

    private fun isModulesStateNotChanging(): Boolean {
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