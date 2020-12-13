package pan.alexander.tordnscrypt.patches

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity
import android.util.Log
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import java.io.File

class ConfigUtil(private val activity: Activity) {
    private val pathVars = PathVars.getInstance(activity)

    fun patchDNSCryptConfig(dnsCryptConfigPatches: List<PatchLine>) {
        readFromFile(pathVars.dnscryptConfPath).replaceLinesInFile(dnsCryptConfigPatches).writeToFile(pathVars.dnscryptConfPath)
    }

    fun patchTorConfig(torConfigPatches: List<PatchLine>) {
        readFromFile(pathVars.torConfPath).replaceLinesInFile(torConfigPatches).writeToFile(pathVars.torConfPath)
    }

    fun patchItpdConfig(itpdConfigPatches: List<PatchLine>) {
        readFromFile(pathVars.itpdConfPath).replaceLinesInFile(itpdConfigPatches).writeToFile(pathVars.itpdConfPath)
    }

    private fun readFromFile(filePath: String): List<String> {
        val file = File(filePath)

        return if (file.isFile && (file.canRead() || file.setReadable(true))) {
            file.readLines()
        } else {
            Log.e(LOG_TAG, "Patches ConfigUtil cannot read from file $filePath")
            emptyList()
        }
    }

    private fun List<String>.writeToFile(filePath: String) {

        if (this.isEmpty() || activity.isFinishing) {
            return
        }

        val file = File(filePath)
        val text = StringBuilder()

        this.forEach { line ->
            if (line.isNotEmpty()) {
                text.append(line).append("\n")
            }
         }

        if (file.isFile && (file.canWrite() || file.setWritable(true))) {
            file.writeText(text.toString())
        } else {
            Log.e(LOG_TAG, "Patches ConfigUtil cannot write to file $filePath")
        }
    }

    private fun List<String>.replaceLinesInFile(replacementLines: List<PatchLine>): List<String> {
        val newLines = mutableListOf<String>()

        this.forEach { line -> newLines.add(line.trim()) }

        var currentHeader = ""
        for (index: Int in newLines.indices) {

            val line = newLines[index]

            if (line.matches(Regex("\\[.+]"))) {
                currentHeader = line
            }

            for (replacementLine: PatchLine in replacementLines) {
                if ((replacementLine.header.isEmpty() || replacementLine.header == currentHeader)
                        && line.matches(replacementLine.lineToFind)) {
                    newLines[index] = replacementLine.lineToReplace
                }
            }
        }

        return if (this.size != newLines.size || !this.containsAll(newLines)) {
            newLines
        } else {
            emptyList()
        }
    }
}
