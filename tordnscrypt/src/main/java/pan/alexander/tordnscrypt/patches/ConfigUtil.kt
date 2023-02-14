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

package pan.alexander.tordnscrypt.patches

import android.content.Context
import android.util.Log
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class ConfigUtil(private val context: Context) {
    private val pathVars = App.instance.daggerComponent.getPathVars().get()

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

        if (this.isEmpty()) {
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

    fun updateTorGeoip() {
        val geoip = File(pathVars.appDataDir + "/app_data/tor/geoip")
        val geoip6 = File(pathVars.appDataDir + "/app_data/tor/geoip6")
        val installedGeoipSize = geoip.length()
        val installedGeoip6Size = geoip6.length()
        try {
            ZipInputStream(context.assets.open("tor.mp3")).use { zipInputStream ->
                var zipEntry = zipInputStream.nextEntry
                while (zipEntry != null) {
                    val fileName = zipEntry.name
                    if (fileName.contains("geoip6")) {
                        if (zipEntry.size != installedGeoip6Size) {
                            FileOutputStream(geoip6).use { outputStream ->
                                copyData(zipInputStream, outputStream)
                                Log.i(LOG_TAG, "Tor geoip6 was updated!")
                            }
                        }
                    } else if (fileName.contains("geoip")) {
                        if (zipEntry.size != installedGeoipSize) {
                            FileOutputStream(geoip).use { outputStream ->
                                copyData(zipInputStream, outputStream)
                                Log.i(LOG_TAG, "Tor geoip was updated!")
                            }
                        }
                    }
                    zipEntry = zipInputStream.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ConfigUtil updateTorGeoip exception " + e.message + " " + e.cause)
        }
    }

    @Throws(java.lang.Exception::class)
    private fun copyData(inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(8 * 1024)
        var len: Int
        while (inputStream.read(buffer).also { len = it } > 0) {
            outputStream.write(buffer, 0, len)
        }
    }
}
