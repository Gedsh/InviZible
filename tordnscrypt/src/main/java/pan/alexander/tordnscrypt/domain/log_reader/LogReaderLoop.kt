package pan.alexander.tordnscrypt.domain.log_reader

import android.util.Log
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsInteractor
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.DNSCryptInteractor
import pan.alexander.tordnscrypt.domain.log_reader.itpd.ITPDHtmlInteractor
import pan.alexander.tordnscrypt.domain.log_reader.itpd.ITPDInteractor
import pan.alexander.tordnscrypt.domain.log_reader.tor.TorInteractor
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.lang.Exception
import java.util.concurrent.locks.ReentrantLock

private const val TIMER_INITIAL_DELAY = 1L
private const val TIMER_INITIAL_PERIOD = 1L
private const val TIMER_MAIN_PERIOD = 5L
private const val COUNTER_STARTING = 30
private const val COUNTER_STOPPING = 5

class LogReaderLoop(
    dnsCryptInteractor: DNSCryptInteractor,
    torInteractor: TorInteractor,
    itpdInteractor: ITPDInteractor,
    itpdHtmlInteractor: ITPDHtmlInteractor,
    private val connectionRecordsInteractor: ConnectionRecordsInteractor
) {
    private val reentrantLock = ReentrantLock()

    private val logReaderFacade = LogReaderFacade(
        dnsCryptInteractor,
        torInteractor,
        itpdInteractor,
        itpdHtmlInteractor,
        connectionRecordsInteractor
    )

    private var timer: ScheduledExecutor? = null
    private var displayPeriod: Long = 0

    private var counterStarting = COUNTER_STARTING
    private var counterStopping = COUNTER_STOPPING

    fun startLogsParser(period: Long = TIMER_INITIAL_PERIOD) {

        if (!reentrantLock.tryLock()) {
            return
        }

        try {
            startLoop(period)
        } catch (e: Exception) {
            Log.e(
                LOG_TAG, "LogReaderLoop startLogsParser exception " +
                        "${e.message} ${e.cause} ${e.stackTrace.joinToString { "," }}"
            )
        } finally {
            if (reentrantLock.isHeldByCurrentThread) {
                reentrantLock.unlock()
            }
        }
    }

    private fun startLoop(period: Long) {
        if (timer?.isLooping() == true && period == displayPeriod) {
            return
        }

        Log.i(LOG_TAG, "LogReaderLoop startLogsParser, period $period sec")

        displayPeriod = period

        timer?.stopExecutor()

        timer = ScheduledExecutor(TIMER_INITIAL_DELAY, period)

        timer?.execute { parseLogs() }
    }

    private fun stopLogsParser() {
        reentrantLock.lock()
        try {
            timer?.stopExecutor()
            timer = null
            connectionRecordsInteractor.stopConverter(true)
            App.instance.releaseLogReaderScope()
            Log.i(LOG_TAG, "LogReaderLoop stopLogsParser")
        } catch (e: Exception) {
            loge("LogReaderLoop stopLogsParser", e)
        } finally {
            reentrantLock.unlock()
        }
    }

    private fun parseLogs() {
        if (logReaderFacade.isAnyListenerAvailable()) {
            counterStopping = COUNTER_STOPPING
        } else {
            counterStopping--
        }

        if (counterStopping <= 0) {
            stopLogsParser()
            return
        }

        logReaderFacade.parseDNSCryptLog()

        logReaderFacade.parseTorLog()

        logReaderFacade.parseITPDLog()

        logReaderFacade.parseITPDHTML()

        logReaderFacade.convertConnectionRecords()

        if (logReaderFacade.isModulesStateNotChanging()) {
            counterStarting--
        } else {
            counterStarting = COUNTER_STARTING
        }

        if (counterStarting == 0) {
            startLogsParser(TIMER_MAIN_PERIOD)
            counterStarting = COUNTER_STARTING
        }
    }
}