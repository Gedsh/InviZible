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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.modules

import com.jrummyapps.android.shell.CommandResult
import com.jrummyapps.android.shell.ShellExitCode
import java.io.*

class ProcessStarter(private val libraryDir: String) {

    fun startProcess(startCommand: String): CommandResult {

        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        var exitCode: Int

        try {

            val env = Array(1) { "LD_LIBRARY_PATH=$libraryDir" }
            val process = Runtime.getRuntime().exec(startCommand, env)

            BufferedReader(InputStreamReader(process.inputStream)).use { bufferedReader ->
                var line = bufferedReader.readLine()
                while (line != null) {
                    stdout.add(line)
                    line = bufferedReader.readLine()
                }
            }

            BufferedReader(InputStreamReader(process.errorStream)).use { bufferedReader ->
                var line = bufferedReader.readLine()
                while (line != null) {
                    stderr.add(line)
                    line = bufferedReader.readLine()
                }
            }

            try {
                OutputStreamWriter(process.outputStream, "UTF-8").use { writer ->
                    writer.write("exit\n")
                    writer.flush()
                }
            } catch (e: IOException) {
                //noinspection StatementWithEmptyBody
                if (e.message?.contains("EPIPE") == true || e.message?.contains("Stream closed") == true) {
                    // Method most horrid to catch broken pipe, in which case we do nothing. The command is not a shell, the
                    // shell closed stdin, the script already contained the exit command, etc. these cases we want the output
                    // instead of returning null
                } else {
                    // other issues we don't know how to handle, leads to returning null
                    throw e
                }
            }

            exitCode = process.waitFor()
            process.destroy()
        } catch (e: InterruptedException) {
            exitCode = ShellExitCode.WATCHDOG_EXIT
        } catch (e: IOException) {
            exitCode = ShellExitCode.SHELL_WRONG_UID
        }

        return CommandResult(stdout, stderr, exitCode)
    }
}
