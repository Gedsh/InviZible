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

package pan.alexander.tordnscrypt.settings.dnscrypt_rules.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.domain.dns_rules.DnsRuleType
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.DnsRulesRegex.Companion.blackListHostRulesRegex
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.DnsRulesRegex.Companion.blacklistIPRulesRegex
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.DnsRulesRegex.Companion.cloakingRulesRegex
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.DnsRulesRegex.Companion.forwardingRulesRegex
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.DnsRulesRegex.Companion.hostFileRegex
import pan.alexander.tordnscrypt.settings.dnscrypt_rules.local.ImportRulesManager.DnsRulesRegex.Companion.whiteListHostRulesRegex
import pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import pan.alexander.tordnscrypt.utils.logger.Logger.loge
import pan.alexander.tordnscrypt.utils.wakelock.WakeLocksManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.locks.ReentrantLock

private val excludeFromHost = listOf("localhost", "localhost.localdomain", "local", META_ADDRESS)
private val reentrantLock = ReentrantLock()
private val wakeLocksManager = WakeLocksManager.getInstance()

class ImportRulesManager(
    private val context: Context,
    private var rulesVariant: DnsRuleType,
    private var remoteRulesUrl: String? = null,
    private var remoteRulesName: String? = null,
    private val importType: ImportType,
    private val filePathToImport: Array<*>
) : Runnable {

    private val localBroadcastManager by lazy {
        LocalBroadcastManager.getInstance(context)
    }

    private val pathVars: PathVars = App.instance.daggerComponent.getPathVars().get()

    private val blackListHostRulesPath = pathVars.dnsCryptBlackListPath
    private val blackListHostSingleRulesPath = pathVars.dnsCryptSingleBlackListPath
    private val blackListHostRulesLocalPath = pathVars.dnsCryptLocalBlackListPath
    private val blackListHostRulesRemotePath = pathVars.dnsCryptRemoteBlackListPath

    private val blackListIPRulesPath = pathVars.dnsCryptIPBlackListPath
    private val blackListIPRulesLocalPath = pathVars.dnsCryptLocalIPBlackListPath
    private val blackListSingleIPRulesPath = pathVars.dnsCryptSingleIPBlackListPath
    private val blackListIPRulesRemotePath = pathVars.dnsCryptRemoteIPBlackListPath

    private val whiteListHostRulesPath = pathVars.dnsCryptWhiteListPath
    private val whiteListSingleRulesPath = pathVars.dnsCryptSingleWhiteListPath
    private val whiteListHostRulesLocalPath = pathVars.dnsCryptLocalWhiteListPath
    private val whiteListHostRulesRemotePath = pathVars.dnsCryptRemoteWhiteListPath

    private val cloakingRulesPath = pathVars.dnsCryptCloakingRulesPath
    private val cloakingSingleRulesPath = pathVars.dnsCryptSingleCloakingRulesPath
    private val cloakingRulesLocalPath = pathVars.dnsCryptLocalCloakingRulesPath
    private val cloakingRulesRemotePath = pathVars.dnsCryptRemoteCloakingRulesPath

    private val forwardingRulesPath = pathVars.dnsCryptForwardingRulesPath
    private val forwardingSingleRulesPath = pathVars.dnsCryptSingleForwardingRulesPath
    private val forwardingRulesLocalPath = pathVars.dnsCryptLocalForwardingRulesPath
    private val forwardingRulesRemotePath = pathVars.dnsCryptRemoteForwardingRulesPath

    private val defaultForwardingRule = pathVars.dnsCryptDefaultForwardingRule
    private val defaultCloakingRule = pathVars.dnsCryptDefaultCloakingRule

    private val contentResolver = context.applicationContext.contentResolver

    private var hashes = hashSetOf<String>()
    private var savedTime = System.currentTimeMillis()

    private var powerLocked = false

    private var rulesFilePath: String = ""
    private var singleRulesFilePath: String = ""
    private var localRulesFilePath: String = ""
    private var remoteRulesFilePath: String = ""
    private var rulesRegex: Regex = blackListHostRulesRegex

    @Volatile
    private var importedLinesCount = 0

    @Volatile
    private var totalLinesCount = 0
    private var fileNames = ""


    override fun run() {

        when (rulesVariant) {
            DnsRuleType.BLACKLIST -> {
                rulesFilePath = blackListHostRulesPath
                singleRulesFilePath = blackListHostSingleRulesPath
                localRulesFilePath = blackListHostRulesLocalPath
                remoteRulesFilePath = blackListHostRulesRemotePath
                rulesRegex = blackListHostRulesRegex
            }

            DnsRuleType.WHITELIST -> {
                rulesFilePath = whiteListHostRulesPath
                singleRulesFilePath = whiteListSingleRulesPath
                localRulesFilePath = whiteListHostRulesLocalPath
                remoteRulesFilePath = whiteListHostRulesRemotePath
                rulesRegex = whiteListHostRulesRegex
            }

            DnsRuleType.IP_BLACKLIST -> {
                rulesFilePath = blackListIPRulesPath
                singleRulesFilePath = blackListSingleIPRulesPath
                localRulesFilePath = blackListIPRulesLocalPath
                remoteRulesFilePath = blackListIPRulesRemotePath
                rulesRegex = blacklistIPRulesRegex
            }

            DnsRuleType.CLOAKING -> {
                rulesFilePath = cloakingRulesPath
                singleRulesFilePath = cloakingSingleRulesPath
                localRulesFilePath = cloakingRulesLocalPath
                remoteRulesFilePath = cloakingRulesRemotePath
                rulesRegex = cloakingRulesRegex
            }

            DnsRuleType.FORWARDING -> {
                rulesFilePath = forwardingRulesPath
                singleRulesFilePath = forwardingSingleRulesPath
                localRulesFilePath = forwardingRulesLocalPath
                remoteRulesFilePath = forwardingRulesRemotePath
                rulesRegex = forwardingRulesRegex
            }
        }

        doTheJob(filePathToImport)
    }

    private fun doTheJob(
        filesToImport: Array<*>
    ) {

        reentrantLock.lock()

        if (!wakeLocksManager.isPowerWakeLockHeld) {
            wakeLocksManager.managePowerWakelock(context, true)
            powerLocked = true
        }

        try {

            val fileToSave = when (importType) {
                ImportType.LOCAL_RULES, ImportType.SINGLE_RULES -> localRulesFilePath
                ImportType.REMOTE_RULES -> remoteRulesFilePath
            }

            if (filesToImport.isNotEmpty()) {
                File(fileToSave).printWriter().use {
                    filesToImport.map { path ->
                        when (path) {
                            is String -> getFileNameFromPath(path)
                            is Uri -> getFileNameFromUri(path)
                            else -> throw IllegalArgumentException("ImportRulesManager unknown path type")
                        }
                    }.let { names ->
                        fileNames = names.joinToString()
                        when (importType) {
                            ImportType.LOCAL_RULES -> addFileHeader(it, names)

                            ImportType.REMOTE_RULES -> remoteRulesUrl?.let { url ->
                                addFileHeader(it, listOf(url))
                            }

                            ImportType.SINGLE_RULES -> Unit
                        }
                    }
                    mixFiles(it, filesToImport.toMutableList(), true)
                }
            }

            val fileToAdd = when (importType) {
                ImportType.LOCAL_RULES, ImportType.SINGLE_RULES -> remoteRulesFilePath
                ImportType.REMOTE_RULES -> localRulesFilePath
            }

            val filesForFinalMixing = mutableListOf<Any?>(
                singleRulesFilePath, fileToSave, fileToAdd
            )

            File(rulesFilePath).printWriter().use {
                //addDefaultLinesIfRequired()
                mixFiles(it, filesForFinalMixing, false)
            }

            if (importedLinesCount > 0) {
                sendImportFinishedBroadcast(
                    getRulesFileSize(),
                    importedLinesCount
                )
            } else {
                sendImportFailedBroadcast(
                    "Imported zero rules"
                )
            }

            sendTotalRulesBroadcast(totalLinesCount)

        } catch (e: Exception) {
            sendImportFailedBroadcast(
                e.message ?: ""
            )
            loge("ImportRules doTheJob", e)
        } finally {

            if (powerLocked) {
                wakeLocksManager.stopPowerWakelock()
            }

            restartDNSCryptIfRequired()

            reentrantLock.unlock()
        }

    }

    private fun mixFiles(
        printWriter: PrintWriter,
        filesToImport: MutableList<Any?>,
        countAsImportedLines: Boolean
    ) {
        try {

            hashes.clear()

            filesToImport.forEachIndexed { index, file ->

                if (file is String) {
                    mixFilesWithPass(
                        index,
                        file,
                        filesToImport.size,
                        rulesRegex,
                        printWriter,
                        countAsImportedLines
                    )
                } else if (file is Uri) {
                    mixFilesWithUri(
                        index,
                        file,
                        filesToImport.size,
                        rulesRegex,
                        printWriter,
                        countAsImportedLines
                    )
                }
            }

            sendImportProgressBroadcast(
                getRulesFileSize(),
                importedLinesCount
            )
            sendTotalRulesBroadcast(totalLinesCount)
        } catch (e: Exception) {
            loge("ImportRules mixFiles", e)
        }
    }

    private fun mixFilesWithPass(
        index: Int,
        file: String,
        filesCount: Int,
        rulesRegex: Regex,
        printWriter: PrintWriter,
        countAsImportedLines: Boolean
    ) {

        if (file.isNotEmpty()) {
            val inputFile = File(file)

            if (inputFile.isFile) {
                try {
                    val blackListFileIsHost = if (DnsRuleType.BLACKLIST == rulesVariant) {
                        isInputFileFormatCorrect(inputFile, hostFileRegex)
                    } else {
                        false
                    }

                    val hashesNew = hashSetOf<String>()

                    if (blackListFileIsHost || isInputFileFormatCorrect(inputFile, rulesRegex)) {
                        inputFile.bufferedReader().use { reader ->
                            mixFilesCommonPart(
                                printWriter,
                                reader,
                                rulesRegex,
                                index,
                                filesCount,
                                hashesNew,
                                blackListFileIsHost,
                                countAsImportedLines
                            )
                        }
                    }
                } catch (e: Exception) {
                    loge("ImportRules mixFilesWithPass", e)
                }
            }
        }
    }

    private fun mixFilesWithUri(
        index: Int,
        uri: Uri,
        filesCount: Int,
        rulesRegex: Regex,
        printWriter: PrintWriter,
        countAsImportedLines: Boolean
    ) {
        try {
            val blackListFileIsHost = if (DnsRuleType.BLACKLIST == rulesVariant) {
                isInputFileFormatCorrect(uri, hostFileRegex)
            } else {
                false
            }

            val hashesNew = hashSetOf<String>()

            if (blackListFileIsHost || isInputFileFormatCorrect(uri, rulesRegex)) {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        mixFilesCommonPart(
                            printWriter,
                            reader,
                            rulesRegex,
                            index,
                            filesCount,
                            hashesNew,
                            blackListFileIsHost,
                            countAsImportedLines
                        )
                    }
                }
            }
        } catch (e: Exception) {
            loge("ImportRules mixFilesWithUri", e)
        }
    }

    private fun mixFilesCommonPart(
        printWriter: PrintWriter,
        reader: BufferedReader,
        rulesRegex: Regex,
        index: Int,
        filesCount: Int,
        hashesNew: MutableSet<String>,
        blackListFileIsHost: Boolean,
        countAsImportedLines: Boolean
    ) {
        var line = reader.readLine()?.trim()
        while (line != null && !Thread.currentThread().isInterrupted) {
            val lineReady = if (blackListFileIsHost) {
                hostToBlackList(line)
            } else {
                cleanRule(line, rulesRegex)
            }

            if (lineReady.isNotEmpty() && (index == 0 || !hashes.contains(lineReady))) {

                if (filesCount > 1 && index < filesCount - 1) {
                    hashesNew += lineReady
                }

                printWriter.println(lineReady)
                if (countAsImportedLines) {
                    importedLinesCount++
                } else {
                    totalLinesCount++
                }
                val currentTime = System.currentTimeMillis()
                if (currentTime - savedTime > 300) {
                    if (countAsImportedLines) {
                        sendImportProgressBroadcast(
                            getRulesFileSize(),
                            importedLinesCount
                        )
                    } else {
                        sendTotalRulesBroadcast(totalLinesCount)
                    }
                    savedTime = currentTime
                }
            }
            line = reader.readLine()?.trim()
        }

        hashes += hashesNew

        if (countAsImportedLines) {
            sendImportProgressBroadcast(
                getRulesFileSize(),
                importedLinesCount
            )
        } else {
            sendTotalRulesBroadcast(totalLinesCount)
        }
    }

    private fun cleanRule(line: String, regExp: Regex): String {
        var output = line

        if (line.isEmpty() || line.startsWith("#")) {
            return ""
        }

        val index = line.indexOf("#")
        if (index > 0 && index < line.length - 1) {
            output = line.substring(0, index).trim()
        }

        if(!output.matches(regExp)) {
            return ""
        }

        return output
    }

    private fun hostToBlackList(line: String): String {
        var output = ""

        if (line.startsWith("#") || !line.matches(hostFileRegex)) {
            return ""
        }

        val index = line.lastIndexOf(" ") + 1
        if (index in 8 until line.length) {
            output = line.substring(index)
        }

        if (excludeFromHost.contains(output)) {
            return ""
        }

        return output
    }

    private fun addDefaultLinesIfRequired() {
        if (DnsRuleType.CLOAKING == rulesVariant) {
            val file = File(singleRulesFilePath)
            if (!file.isFile) {
                file.createNewFile()
            }
            if (file.length() == 0L) {
                file.printWriter().use {
                    it.println(defaultCloakingRule)
                }
            }
        } else if (DnsRuleType.FORWARDING == rulesVariant) {
            val file = File(singleRulesFilePath)
            if (!file.isFile) {
                file.createNewFile()
            }
            if (file.length() == 0L) {
                file.printWriter().use {
                    it.println(defaultForwardingRule)
                }
            }
        }
    }

    private fun isInputFileFormatCorrect(file: File, regExp: Regex): Boolean {
        file.bufferedReader().use {
            try {
                return isInputFileFormatCorrect(it, regExp)
            } catch (e: Exception) {
                loge("ImportRules isInputFileFormatCorrect", e)
            }
        }
        return false
    }

    private fun isInputFileFormatCorrect(uri: Uri, regExp: Regex): Boolean {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            try {
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                   return isInputFileFormatCorrect(reader, regExp)
                }
            } catch (e: Exception) {
                loge("ImportRules isInputFileFormatCorrect", e)
            }
        }
        return false
    }

    private fun isInputFileFormatCorrect(reader: BufferedReader, regExp: Regex): Boolean {
        var line: String? = reader.readLine()?.trim()
        var index = 0
        while (line != null) {

            if (Thread.currentThread().isInterrupted) {
                return false
            }

            if (line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("!")) {
                var output = line
                val hashIndex = line.indexOf("#")
                if (hashIndex > 0 && hashIndex < line.length - 1) {
                    output = line.substring(0, hashIndex).trim()
                }
                index++
                if (output.matches(regExp)) {
                    return true
                } else if (index > 100) {
                    return false
                }
            }

            line = reader.readLine()?.trim()
        }
        return false
    }

    private fun restartDNSCryptIfRequired() {
        if (ModulesStatus.getInstance().dnsCryptState == ModuleState.RUNNING) {
            ModulesRestarter.restartDNSCrypt(context)
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.moveToFirst()
        val nameColumnIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
        val fileName = if (nameColumnIndex >= 0) {
            cursor?.getString(nameColumnIndex) ?: ""
        } else {
            ""
        }
        cursor?.close()
        return fileName
    }

    private fun getFileNameFromPath(path: String) =
        path.substringAfterLast("/")

    private fun getRulesFileSize(): Long {
        val filePath = when (importType) {
            ImportType.LOCAL_RULES -> localRulesFilePath
            ImportType.REMOTE_RULES -> remoteRulesFilePath
            ImportType.SINGLE_RULES -> singleRulesFilePath
        }
        return File(filePath).length()
    }

    private fun addFileHeader(
        printWriter: PrintWriter,
        names: List<String>
    ) = with(printWriter) {
        val nameLine = "# ${names.joinToString().replace("#", "")} #"
        println("#".repeat(nameLine.length))
        println()
        println(nameLine)
        println()
        println("#".repeat(nameLine.length))
        println()
    }

    private fun sendImportProgressBroadcast(
        size: Long,
        count: Int
    ) {
        when (importType) {
            ImportType.LOCAL_RULES -> {
                Intent(UPDATE_LOCAL_DNS_RULES_PROGRESS_ACTION).apply {
                    putExtra(
                        UPDATE_DNS_RULES_PROGRESS_DATA,
                        DnsRulesUpdateProgress.UpdateProgress(
                            name = fileNames,
                            size = size,
                            count = count
                        )
                    )
                }
            }

            ImportType.REMOTE_RULES -> {
                Intent(UPDATE_REMOTE_DNS_RULES_PROGRESS_ACTION).apply {
                    putExtra(
                        UPDATE_DNS_RULES_PROGRESS_DATA,
                        DnsRulesUpdateProgress.UpdateProgress(
                            name = remoteRulesName ?: "",
                            url = remoteRulesUrl,
                            size = size,
                            count = count
                        )
                    )
                }
            }

            ImportType.SINGLE_RULES -> null
        }?.let {
            localBroadcastManager.sendBroadcast(it)
        }
    }

    private fun sendImportFinishedBroadcast(
        size: Long,
        count: Int
    ) {
        when (importType) {
            ImportType.LOCAL_RULES -> {
                Intent(UPDATE_LOCAL_DNS_RULES_PROGRESS_ACTION).apply {
                    putExtra(
                        UPDATE_DNS_RULES_PROGRESS_DATA,
                        DnsRulesUpdateProgress.UpdateFinished(
                            name = fileNames,
                            size = size,
                            count = count
                        )
                    )
                }
            }

            ImportType.REMOTE_RULES -> {
                Intent(UPDATE_REMOTE_DNS_RULES_PROGRESS_ACTION).apply {
                    putExtra(
                        UPDATE_DNS_RULES_PROGRESS_DATA,
                        DnsRulesUpdateProgress.UpdateFinished(
                            name = remoteRulesName ?: "",
                            url = remoteRulesUrl,
                            size = size,
                            count = count
                        )
                    )
                }
            }

            ImportType.SINGLE_RULES -> null
        }?.let {
            localBroadcastManager.sendBroadcast(it)
        }
    }

    private fun sendImportFailedBroadcast(
        error: String
    ) {
        when (importType) {
            ImportType.LOCAL_RULES -> {
                Intent(UPDATE_LOCAL_DNS_RULES_PROGRESS_ACTION).apply {
                    putExtra(
                        UPDATE_DNS_RULES_PROGRESS_DATA,
                        DnsRulesUpdateProgress.UpdateFailure(
                            name = fileNames,
                            error = error
                        )
                    )
                }
            }

            ImportType.REMOTE_RULES -> {
                Intent(UPDATE_REMOTE_DNS_RULES_PROGRESS_ACTION).apply {
                    putExtra(
                        UPDATE_DNS_RULES_PROGRESS_DATA,
                        DnsRulesUpdateProgress.UpdateFailure(
                            name = remoteRulesName ?: "",
                            url = remoteRulesUrl,
                            error = error
                        )
                    )
                }
            }

            ImportType.SINGLE_RULES -> null
        }?.let {
            localBroadcastManager.sendBroadcast(it)
        }
    }

    private fun sendTotalRulesBroadcast(count: Int) {
        Intent(UPDATE_TOTAL_DNS_RULES_PROGRESS_ACTION).apply {
            putExtra(
                UPDATE_TOTAL_DNS_RULES_PROGRESS_DATA,
                count
            )
        }.also {
            localBroadcastManager.sendBroadcast(it)
        }
    }

    enum class ImportType {
        REMOTE_RULES,
        LOCAL_RULES,
        SINGLE_RULES
    }

    interface DnsRulesRegex {
        companion object {
            val blackListHostRulesRegex = Regex("^[a-zA-Z\\d-.=_*\\[\\]?,]+$")
            val blacklistIPRulesRegex = Regex("^[0-9a-fA-F:.=*\\[\\]]+$")
            val cloakingRulesRegex = Regex("^[a-zA-Z\\d-.=_*\\[\\]?]+[ \\t]+[a-zA-Z\\d-.=_*:]+$")
            val forwardingRulesRegex =
                Regex("^[a-zA-Z\\d-._]+[ \\t]+[0-9a-fA-F:.,\\[\\]]+$")
            val whiteListHostRulesRegex = Regex("^[a-zA-Z\\d-.=_*\\[\\]?]+$")
            val hostFileRegex = Regex("^(?:0.0.0.0|127.0.0.1)[ \\t]+[a-zA-Z\\d-._]+$")
        }
    }

    companion object {
        const val UPDATE_REMOTE_DNS_RULES_PROGRESS_ACTION =
            "pan.alexander.tordnscrypt.UPDATE_REMOTE_DNS_RULES_PROGRESS_ACTION"
        const val UPDATE_LOCAL_DNS_RULES_PROGRESS_ACTION =
            "pan.alexander.tordnscrypt.UPDATE_LOCAL_DNS_RULES_PROGRESS_ACTION"
        const val UPDATE_DNS_RULES_PROGRESS_DATA =
            "pan.alexander.tordnscrypt.UPDATE_DNS_RULES_PROGRESS_DATA"
        const val UPDATE_TOTAL_DNS_RULES_PROGRESS_ACTION =
            "pan.alexander.tordnscrypt.UPDATE_TOTAL_DNS_RULES_PROGRESS_ACTION"
        const val UPDATE_TOTAL_DNS_RULES_PROGRESS_DATA =
            "pan.alexander.tordnscrypt.UPDATE_TOTAL_DNS_RULES_PROGRESS_DATA"
    }
}
