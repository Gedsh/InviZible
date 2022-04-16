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

package pan.alexander.tordnscrypt.utils.root

import android.content.SharedPreferences
import com.jrummyapps.android.shell.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import pan.alexander.tordnscrypt.di.CoroutinesModule
import pan.alexander.tordnscrypt.di.SharedPreferencesModule
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logw
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.AUTO_START_DELAY
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVE_ROOT_LOGS
import pan.alexander.tordnscrypt.utils.root.RootCommandsMark.BOOT_BROADCAST_MARK
import pan.alexander.tordnscrypt.utils.root.RootCommandsMark.IPTABLES_MARK
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.roundToInt

private const val ATTEMPTS_TO_OPEN_ROOT_CONSOLE = 3
private const val ATTEMPTS_TO_EXECUTE_COMMAND = 3
private const val NOTIFICATION_UPDATE_INTERVAL_MSEC = 200L
private const val MAX_EXECUTION_TIME_SEC = 300

@ExperimentalCoroutinesApi
class RootExecutor @Inject constructor(
    private val pathVars: dagger.Lazy<PathVars>,
    private val preferenceRepository: dagger.Lazy<PreferenceRepository>,
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    private val defaultPreferences: dagger.Lazy<SharedPreferences>,
    private val dispatcherMain: MainCoroutineDispatcher,
    @Named(CoroutinesModule.DISPATCHER_IO)
    dispatcherIo: CoroutineDispatcher,
) {

    var onCommandsProgressListener: OnCommandsProgressListener? = null
    var onCommandsDoneListener: OnCommandsDoneListener? = null

    private val wordRegex by lazy { Regex("\\w+") }

    private val coroutineScope = CoroutineScope(
        SupervisorJob() +
                dispatcherIo.limitedParallelism(1) +
                CoroutineName("RootExecutor")
    )

    private val commandsInProgress = MutableSharedFlow<CommandsWithMark>(
        0,
        10,
        BufferOverflow.DROP_OLDEST
    ).also {
        coroutineScope.launch {
            it.onEach {
                try {
                    withTimeout(MAX_EXECUTION_TIME_SEC * 1000L) {
                        executeCommands(it.commands, it.mark)
                    }
                } catch (e: CancellationException) {
                    loge("RootExecutor commands take too long", e)
                    commandsDone(
                        listOf("Commands take too long, more than $MAX_EXECUTION_TIME_SEC seconds"),
                        it.mark
                    )
                } catch (e: Exception) {
                    loge("RootExecutor execute", e)
                    commandsDone(
                        listOf(e.message ?: ""),
                        it.mark
                    )
                }
            }.collect()
        }
    }

    @Volatile
    private var console: Shell.Console? = null

    fun execute(
        commands: List<String>,
        @RootCommandsMark mark: Int
    ) {
        commandsInProgress.tryEmit(CommandsWithMark(commands, mark))
    }

    fun stopExecutor() = coroutineScope.launch {
        closeRootCommandShell()
        coroutineScope.cancel()
    }

    private suspend fun executeCommands(
        commands: List<String>,
        @RootCommandsMark mark: Int
    ) {

        var startTime = System.currentTimeMillis()

        if (mark == BOOT_BROADCAST_MARK) {
            makeAutostartDelayIfRequired()
        }

        val commandsFiltered = commands.filter {
            it.contains(wordRegex)
        }

        val results = commandsFiltered.mapIndexed { index, command ->
            var result: String
            var attempts = 0
            do {

                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Commands take too long. Next command $command")
                }

                if (attempts > 0) {
                    delay(attempts * 100L)
                }

                result = executeCommand(command)?.let {
                    getResult(command, it)
                } ?: "Root console error"

                attempts++
            } while (
                result.isNotBlank()
                && attempts < ATTEMPTS_TO_EXECUTE_COMMAND
                && mark == IPTABLES_MARK
            )


            val currentTime = System.currentTimeMillis()
            if (currentTime - startTime > NOTIFICATION_UPDATE_INTERVAL_MSEC) {
                startTime = currentTime
                updateNotificationProgress(
                    ((index + 1) / commandsFiltered.size.toFloat() * 100).roundToInt()
                )
            }

            return@mapIndexed result
        }.filter {
            it.contains(wordRegex)
        }

        updateNotificationProgress(100)

        delay(NOTIFICATION_UPDATE_INTERVAL_MSEC)

        if (preferenceRepository.get().getBoolPreference(SAVE_ROOT_LOGS)) {
            saveToFile(commandsFiltered, results)
        }

        commandsDone(results, mark)

    }

    private suspend fun updateNotificationProgress(progress: Int) = withContext(dispatcherMain) {
        onCommandsProgressListener?.onCommandsProgress(progress)
    }

    private suspend fun commandsDone(results: List<String>, mark: Int) =
        withContext(dispatcherMain) {
            onCommandsDoneListener?.onCommandsDone(results, mark)
        }

    private suspend fun makeAutostartDelayIfRequired() {
        try {
            val delay = defaultPreferences.get().getString(AUTO_START_DELAY, "0")
            delay?.let {
                if (it != "0") {
                    delay(it.toLong() * 1000)
                }
            }
        } catch (ignored: CancellationException) {
        } catch (e: Exception) {
            loge("RootExecutor makeAutostartDelay", e)
        }
    }

    private suspend fun executeCommand(command: String): ExecutionResult? {

        console ?: openCommandShell()

        val console = console ?: return null

        try {
            val commandResult = console.run(command)
            return ExecutionResult(
                commandResult.exitCode,
                commandResult.stdout,
                commandResult.stderr
            )
        } catch (e: Exception) {
            loge("RootExecutor executeCommand", e)
        }

        return null
    }

    private suspend fun openCommandShell() {
        closeRootCommandShell()

        var attempts = 0

        do {
            runCatching {

                if (attempts > 0) {
                    delay(attempts * 100L)
                }

                console = Shell.SU.getConsole()

                attempts++
            }.onFailure {
                loge("RootExecutor openCommandShell", it)
            }
        } while (console?.isClosed != false && attempts < ATTEMPTS_TO_OPEN_ROOT_CONSOLE)

    }

    private fun closeRootCommandShell() {
        try {
            console?.let { console ->
                if (!console.isClosed) {
                    console.run("exit")
                    console.close()
                }
            }
            console = null
        } catch (e: Exception) {
            loge("RootExecutor closeRootCommandShell", e)
        }
    }

    private fun getResult(command: String, commandResult: ExecutionResult): String {

        val results = mutableListOf<String>()

        if (commandResult.exitCode !in 0..2) {
            results.add("Exit code=${commandResult.exitCode}")
        }

        val out = commandResult.stdOut.filter {
            it.contains(wordRegex)
        }
        if (out.isNotEmpty()) {
            results.add("STDOUT=${out.joinToString("; ")}")
        }

        val err = commandResult.stdErr.filter {
            it.contains(wordRegex)
        }.map {
            it.replace(
                ", Try `iptables -h' or 'iptables --help' for more information.",
                ""
            )
        }
        if (err.isNotEmpty()) {
            results.add("STDERR=${err.joinToString("; ")}")
        }

        val result = if (results.isNotEmpty()) {
            results.joinToString(", ")
        } else {
            ""
        }

        if (result.isNotEmpty()) {
            logw("Warning executing root command. Result: $result Command: $command")
        }

        return result

    }

    private fun saveToFile(commands: List<String>, commandResults: List<String>) {
        try {
            var logsDir = File(pathVars.get().appDataDir, "logs")

            if (!logsDir.isDirectory) {
                logsDir.mkdirs()
                logsDir = File(pathVars.get().appDataDir, "logs")
            }

            if (!logsDir.isDirectory) {
                throw IOException("Unable to create logs dir")
            }

            FileOutputStream(File(logsDir, "RootExec.log"), true)
                .bufferedWriter().use { writer ->

                    writer.appendLine("********************")

                    writer.appendLine("COMMANDS")
                    commands.forEach {
                        writer.appendLine(it)
                    }
                    writer.appendLine("--------------------")

                    writer.appendLine("RESULT")
                    commandResults.forEach {
                        writer.appendLine(it)
                    }

                    writer.appendLine("********************")
                }
        } catch (e: Exception) {
            loge("RootExecutor saveToFile", e)
        }
    }

    data class CommandsWithMark(
        val commands: List<String>,
        val mark: Int
    )

    data class ExecutionResult(
        val exitCode: Int,
        val stdOut: List<String>,
        val stdErr: List<String>
    )

    interface OnCommandsDoneListener {
        fun onCommandsDone(results: List<String>, mark: Int)
    }

    interface OnCommandsProgressListener {
        fun onCommandsProgress(progress: Int)
    }
}
