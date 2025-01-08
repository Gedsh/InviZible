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

package pan.alexander.tordnscrypt.patches

import android.content.Context
import android.os.Build
import dalvik.system.ZipPathValidator
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.logger.Logger.logi
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class ConfigUtil(private val context: Context) {
    private val pathVars = App.instance.daggerComponent.getPathVars().get()

    fun patchDNSCryptConfig(dnsCryptConfigPatches: List<AlterConfig>) {
        readFromFile(pathVars.dnscryptConfPath).run {
            addLinesToFile(dnsCryptConfigPatches.filterIsInstance<AlterConfig.AddLine>())
                .replaceLinesInFile(dnsCryptConfigPatches.filterIsInstance<AlterConfig.ReplaceLine>())
                .addOdohDNSCryptSection(getOdohSection())
                .takeIf { it.size != this.size || !it.containsAll(this) }
                ?.writeToFile(pathVars.dnscryptConfPath)
        }
    }

    fun patchTorConfig(torConfigPatches: List<AlterConfig>) {
        readFromFile(pathVars.torConfPath).run {
            addLinesToFile(torConfigPatches.filterIsInstance<AlterConfig.AddLine>())
                .replaceLinesInFile(torConfigPatches.filterIsInstance<AlterConfig.ReplaceLine>())
                .takeIf { it.size != this.size || !it.containsAll(this) }
                ?.writeToFile(pathVars.torConfPath)
        }
    }

    fun patchItpdConfig(itpdConfigPatches: List<AlterConfig>) {
        readFromFile(pathVars.itpdConfPath).run {
            addLinesToFile(itpdConfigPatches.filterIsInstance<AlterConfig.AddLine>())
                .replaceLinesInFile(itpdConfigPatches.filterIsInstance<AlterConfig.ReplaceLine>())
                .takeIf { it.size != this.size || !it.containsAll(this) }
                ?.writeToFile(pathVars.itpdConfPath)
        }
    }

    private fun readFromFile(filePath: String): List<String> {
        val file = File(filePath)

        return if (file.isFile && (file.canRead() || file.setReadable(true))) {
            file.readLines()
        } else {
            loge("Patches ConfigUtil cannot read from file $filePath")
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
            loge("Patches ConfigUtil cannot write to file $filePath")
        }
    }

    private fun List<String>.addLinesToFile(addLines: List<AlterConfig.AddLine>): List<String> {
        val newLines = mutableListOf<String>()
        val keyRegex = Regex("[ =]")

        this.forEach { line -> newLines.add(line.trim()) }

        var currentHeader = ""
        for (index: Int in newLines.indices) {

            val line = newLines[index]

            if (line.matches(Regex("\\[.+]"))) {
                currentHeader = line
            }

            for (addLine: AlterConfig.AddLine in addLines) {

                val keyToAdd = addLine.lineToAdd.split(keyRegex).firstOrNull() ?: ""
                val existingKey = newLines.find {
                    it.split(keyRegex).firstOrNull()?.trim() == keyToAdd
                }
                if (existingKey != null) {
                    continue
                }

                if ((addLine.header.isEmpty() || addLine.header == currentHeader)
                    && line.matches(addLine.lineToFind)
                ) {
                    newLines.add(index + 1, addLine.lineToAdd)
                }
            }
        }

        return newLines
    }

    private fun List<String>.replaceLinesInFile(replacementLines: List<AlterConfig.ReplaceLine>): List<String> {
        val newLines = mutableListOf<String>()

        this.forEach { line -> newLines.add(line.trim()) }

        var currentHeader = ""
        for (index: Int in newLines.indices) {

            val line = newLines[index]

            if (line.matches(Regex("\\[.+]"))) {
                currentHeader = line
            }

            for (replacementLine: AlterConfig.ReplaceLine in replacementLines) {
                if ((replacementLine.header.isEmpty() || replacementLine.header == currentHeader)
                    && line.matches(replacementLine.lineToFind)
                ) {
                    newLines[index] = replacementLine.lineToReplace
                }
            }
        }

        return newLines
    }

    private fun List<String>.addOdohDNSCryptSection(lines: List<String>): List<String> {

        if (this.contains(lines.first())) {
            return this
        }

        val newLines = mutableListOf<String>()

        this.forEach { line -> newLines.add(line.trim()) }

        var currentHeader = ""
        for (index: Int in newLines.indices) {

            val line = newLines[index]

            if (line.matches(Regex("\\[.+]"))) {
                currentHeader = line
            }

            if (currentHeader == "[sources.'relays']" && line == "prefix = ''") {
                newLines.addAll(index + 1, lines)
                break
            }
        }

        return newLines
    }

    private fun getOdohSection() =
        """
            [sources.'odoh-servers']
            urls = ['https://raw.githubusercontent.com/DNSCrypt/dnscrypt-resolvers/master/v3/odoh-servers.md', 'https://download.dnscrypt.info/resolvers-list/v3/odoh-servers.md', 'https://ipv6.download.dnscrypt.info/resolvers-list/v3/odoh-servers.md']
            cache_file = 'odoh-servers.md'
            minisign_key = 'RWQf6LRCGA9i53mlYecO4IzT51TGPpvWucNSCh1CBM0QTaLn73Y7GFO3'
            refresh_delay = 72
            prefix = ''
            [sources.'odoh-relays']
            urls = ['https://raw.githubusercontent.com/DNSCrypt/dnscrypt-resolvers/master/v3/odoh-relays.md', 'https://download.dnscrypt.info/resolvers-list/v3/odoh-relays.md', 'https://ipv6.download.dnscrypt.info/resolvers-list/v3/odoh-relays.md']
            cache_file = 'odoh-relays.md'
            minisign_key = 'RWQf6LRCGA9i53mlYecO4IzT51TGPpvWucNSCh1CBM0QTaLn73Y7GFO3'
            refresh_delay = 72
            prefix = ''
        """.trimIndent().split("\n")

    fun updateTorGeoip() {
        val geoip = File(pathVars.appDataDir + "/app_data/tor/geoip")
        val geoip6 = File(pathVars.appDataDir + "/app_data/tor/geoip6")
        val installedGeoipSize = geoip.length()
        val installedGeoip6Size = geoip6.length()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ZipPathValidator.clearCallback()
            }

            ZipInputStream(context.assets.open("tor.mp3")).use { zipInputStream ->
                var zipEntry = zipInputStream.nextEntry
                while (zipEntry != null) {
                    val fileName = zipEntry.name
                    if (fileName.endsWith("geoip6")) {
                        if (zipEntry.size != installedGeoip6Size) {
                            FileOutputStream(geoip6).use { outputStream ->
                                copyData(zipInputStream, outputStream)
                                logi("Tor geoip6 was updated!")
                            }
                        }
                    } else if (fileName.endsWith("geoip")) {
                        if (zipEntry.size != installedGeoipSize) {
                            FileOutputStream(geoip).use { outputStream ->
                                copyData(zipInputStream, outputStream)
                                logi("Tor geoip was updated!")
                            }
                        }
                    }
                    zipEntry = zipInputStream.nextEntry
                }
            }
        } catch (e: Exception) {
            loge("ConfigUtil updateTorGeoip", e)
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
