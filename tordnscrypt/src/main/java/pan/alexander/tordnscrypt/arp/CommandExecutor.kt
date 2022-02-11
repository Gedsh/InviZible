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

package pan.alexander.tordnscrypt.arp

import android.util.Log
import com.jrummyapps.android.shell.Shell
import com.jrummyapps.android.shell.ShellNotFoundException
import pan.alexander.tordnscrypt.di.arp.ArpScope
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.lang.Exception
import javax.inject.Inject

@ArpScope
class CommandExecutor @Inject constructor() {

    private var console: Shell.Console? = null

    fun execNormal(command: String): MutableList<String> {
        val result = mutableListOf<String>()
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(command)

            process.inputStream.bufferedReader().use {
                result.addAll(it.readLines())
            }
            process.errorStream.bufferedReader().use {
                it.forEachLine { line ->
                    Log.e(LOG_TAG, "ArpScanner execCommand $command error $line")
                }
            }
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                Log.w(
                    LOG_TAG,
                    "ArpScanner result exitCode:$exitCode command:$command"
                )
            }

        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "ArpScanner execCommand $command exception ${e.message}\n${e.cause}"
            )
        } finally {
            process?.destroy()
        }
        return result
    }


    fun execRoot(command: String): MutableList<String> {
        val result = mutableListOf<String>()

        console ?: openCommandShell()

        val console = console ?: return result


        if (console.isClosed) {
            return result
        }

        try {
            result.addAll(console.run(command).getStdout().split("\n"))
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "Arp command executor: SU exec failed " + e.message + e.cause
            )
        }

        return result
    }

    private fun openCommandShell() {
        closeRootCommandShell()
        try {
            console = Shell.SU.getConsole()
        } catch (e: ShellNotFoundException) {
            Log.e(
                LOG_TAG,
                "Arp command executor: SU not found! " + e.message + e.cause
            )
        }
    }

    fun closeRootCommandShell() {
        console?.let { console ->
            if (!console.isClosed) {
                repeat(2) { console.run("exit") }
                console.close()
            }
        }
        console = null
    }
}
