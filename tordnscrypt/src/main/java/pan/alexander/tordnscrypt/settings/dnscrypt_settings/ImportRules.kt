package pan.alexander.tordnscrypt.settings.dnscrypt_settings

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

import android.content.Context
import android.net.Uri
import android.util.Log
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.Constants.META_ADDRESS
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.wakelock.WakeLocksManager
import pan.alexander.tordnscrypt.utils.enums.DNSCryptRulesVariant
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList

private val blackListHostRulesRegex = Regex("^[a-zA-Z\\d-.=_*\\[\\]?]+$")
private val blacklistIPRulesRegex = Regex("^(?:[0-9*]{1,3}\\.){1,3}[0-9*]{1,3}(?:/\\d+)*$")
private val cloakingRulesRegex = Regex("^[a-zA-Z\\d-.=_*]+[ \\t]+[a-zA-Z\\d-.=_*]+$")
private val forwardingRulesRegex =
    Regex("^[a-zA-Z\\d-._]+[ \\t]+(?:[0-9*]{1,3}\\.){3}[0-9*]{1,3}(?:, ?(?:[0-9*]{1,3}\\.){3}[0-9*]{1,3})*$")
private val whiteListHostRulesRegex = Regex("^[a-zA-Z\\d-.=_*\\[\\]?]+$")
private val hostFileRegex = Regex("^(?:0.0.0.0|127.0.0.1)[ \\t]+[a-zA-Z\\d-._]+$")
private const val itpdRedirectAddress = "*i2p 10.191.0.1"
private val excludeFromHost = listOf("localhost", "localhost.localdomain", "local", META_ADDRESS)
private val reentrantLock = ReentrantLock()
private val wakeLocksManager = WakeLocksManager.getInstance()

class ImportRules(
    private val context: Context,
    private var rulesVariant: DNSCryptRulesVariant,
    private val localRules: Boolean,
    private val filePathToImport: Array<*>
) : Thread() {

    private val pathVars: PathVars = App.instance.daggerComponent.getPathVars().get()

    private val blackListHostRulesPath = pathVars.dnsCryptBlackListPath
    private val blackListHostRulesLocalPath = pathVars.dnsCryptLocalBlackListPath
    private val blackListHostRulesRemotePath = pathVars.dnsCryptRemoteBlackListPath

    private val blackListIPRulesPath = pathVars.dnsCryptIPBlackListPath
    private val blackListIPRulesLocalPath = pathVars.dnsCryptLocalIPBlackListPath
    private val blackListIPRulesRemotePath = pathVars.dnsCryptRemoteIPBlackListPath

    private val whiteListHostRulesPath = pathVars.dnsCryptWhiteListPath
    private val whiteListHostRulesLocalPath = pathVars.dnsCryptLocalWhiteListPath
    private val whiteListHostRulesRemotePath = pathVars.dnsCryptRemoteWhiteListPath

    private val cloakingRulesPath = pathVars.dnsCryptCloakingRulesPath
    private val cloakingRulesLocalPath = pathVars.dnsCryptLocalCloakingRulesPath
    private val cloakingRulesRemotePath = pathVars.dnsCryptRemoteCloakingRulesPath

    private val forwardingRulesPath = pathVars.dnsCryptForwardingRulesPath
    private val forwardingRulesLocalPath = pathVars.dnsCryptLocalForwardingRulesPath
    private val forwardingRulesRemotePath = pathVars.dnsCryptRemoteForwardingRulesPath

    private var onDNSCryptRuleAddLineListener: OnDNSCryptRuleAddLineListener? = null

    private var powerLocked = false

    private var blackListFileIsHost = false

    private val contentResolver = context.applicationContext.contentResolver

    private var linesCount = 0
    private var hash = 0
    private var hashes = IntArray(0)
    private var savedTime = System.currentTimeMillis()

    interface OnDNSCryptRuleAddLineListener {
        fun onDNSCryptRuleLinesAddingStarted(importThread: Thread)
        fun onDNSCryptRuleLineAdded(count: Int)
        fun onDNSCryptRuleLinesAddingFinished()
    }

    fun setOnDNSCryptRuleAddLineListener(onDNSCryptRuleAddLineListener: OnDNSCryptRuleAddLineListener) {
        this.onDNSCryptRuleAddLineListener = onDNSCryptRuleAddLineListener
    }

    override fun run() {

        when (rulesVariant) {
            DNSCryptRulesVariant.BLACKLIST_HOSTS -> doTheJob(
                blackListHostRulesPath,
                blackListHostRulesLocalPath,
                blackListHostRulesRemotePath,
                blackListHostRulesRegex,
                filePathToImport
            )

            DNSCryptRulesVariant.WHITELIST_HOSTS -> doTheJob(
                whiteListHostRulesPath,
                whiteListHostRulesLocalPath,
                whiteListHostRulesRemotePath,
                whiteListHostRulesRegex,
                filePathToImport
            )

            DNSCryptRulesVariant.BLACKLIST_IPS -> doTheJob(
                blackListIPRulesPath,
                blackListIPRulesLocalPath,
                blackListIPRulesRemotePath,
                blacklistIPRulesRegex,
                filePathToImport
            )

            DNSCryptRulesVariant.CLOAKING -> doTheJob(
                cloakingRulesPath,
                cloakingRulesLocalPath,
                cloakingRulesRemotePath,
                cloakingRulesRegex,
                filePathToImport
            )

            DNSCryptRulesVariant.FORWARDING -> doTheJob(
                forwardingRulesPath,
                forwardingRulesLocalPath,
                forwardingRulesRemotePath,
                forwardingRulesRegex,
                filePathToImport
            )

            DNSCryptRulesVariant.UNDEFINED -> return
        }

    }

    private fun doTheJob(
        rulesFilePath: String,
        localRulesFilePath: String,
        remoteRulesFilePath: String,
        rulesRegex: Regex,
        filesToImport: Array<*>
    ) {


        reentrantLock.lock()

        if (!wakeLocksManager.isPowerWakeLockHeld) {
            wakeLocksManager.managePowerWakelock(context, true)
            powerLocked = true
        }

        onDNSCryptRuleAddLineListener?.onDNSCryptRuleLinesAddingStarted(currentThread())

        try {
            if (filesToImport.isNotEmpty()) {
                File(rulesFilePath).printWriter().use {
                    addDefaultLinesIfRequired(it)
                    mixFiles(
                        it,
                        localRulesFilePath,
                        remoteRulesFilePath,
                        rulesRegex,
                        filesToImport.toMutableList()
                    )
                }
            }

            val fileToSave = if (localRules) {
                localRulesFilePath
            } else {
                remoteRulesFilePath
            }

            File(rulesFilePath).copyTo(File(fileToSave), true)

        } catch (e: Exception) {
            Log.e(LOG_TAG, "ImportRules Exception " + e.message + " " + e.cause)
        } finally {
            onDNSCryptRuleAddLineListener?.onDNSCryptRuleLinesAddingFinished()

            if (powerLocked) {
                wakeLocksManager.stopPowerWakelock()
            }

            reentrantLock.unlock()
        }

    }

    private fun mixFiles(
        printWriter: PrintWriter,
        localRulesFilePath: String,
        remoteRulesFilePath: String,
        rulesRegex: Regex,
        filesToImport: MutableList<Any?>
    ) {

        val fileToAdd: String = if (localRules) {
            remoteRulesFilePath
        } else {
            localRulesFilePath
        }

        val addFile = File(fileToAdd)
        if (addFile.isFile) {
            filesToImport.add(addFile)
        }

        val hashIsRequired = filesToImport.size > 1

        filesToImport.forEachIndexed { index, file ->

            if (file is String) {
                mixFilesWithPass(index, file, rulesRegex, hashIsRequired, printWriter)
            } else if (file is Uri) {
                mixFilesWithUri(index, file, rulesRegex, hashIsRequired, printWriter)
            }
        }

        onDNSCryptRuleAddLineListener?.onDNSCryptRuleLineAdded(linesCount)

        if (linesCount > 0) {
            restartDNSCryptIfRequired()
        }
    }

    private fun mixFilesWithPass(
        index: Int, file: String,
        rulesRegex: Regex, hashIsRequired: Boolean, printWriter: PrintWriter
    ) {

        if (file.isNotEmpty()) {
            val inputFile = File(file)

            if (inputFile.isFile) {
                try {
                    if (DNSCryptRulesVariant.BLACKLIST_HOSTS == rulesVariant) {
                        blackListFileIsHost = isInputFileFormatCorrect(inputFile, hostFileRegex)
                    }

                    val hashesNew = ArrayList<Int>()

                    if (blackListFileIsHost || isInputFileFormatCorrect(inputFile, rulesRegex)) {
                        inputFile.bufferedReader().use { reader ->
                            mixFilesCommonPart(
                                printWriter,
                                reader,
                                rulesRegex,
                                hashIsRequired,
                                index,
                                hashesNew
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "ImportRules Exception " + e.message + " " + e.cause)
                }
            }
        }
    }

    private fun mixFilesWithUri(
        index: Int, uri: Uri,
        rulesRegex: Regex, hashIsRequired: Boolean, printWriter: PrintWriter
    ) {
        try {
            if (DNSCryptRulesVariant.BLACKLIST_HOSTS == rulesVariant) {
                blackListFileIsHost = isInputFileFormatCorrect(uri, hostFileRegex)
            }

            val hashesNew = ArrayList<Int>()

            if (blackListFileIsHost || isInputFileFormatCorrect(uri, rulesRegex)) {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        mixFilesCommonPart(
                            printWriter,
                            reader,
                            rulesRegex,
                            hashIsRequired,
                            index,
                            hashesNew
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "ImportRules Exception " + e.message + " " + e.cause)
        }
    }

    private fun mixFilesCommonPart(
        printWriter: PrintWriter, reader: BufferedReader,
        rulesRegex: Regex, hashIsRequired: Boolean,
        index: Int, hashesNew: ArrayList<Int>
    ) {
        var line = reader.readLine()?.trim()
        while (line != null && !currentThread().isInterrupted) {
            val lineReady = if (blackListFileIsHost) {
                hostToBlackList(line)
            } else {
                cleanRule(line, rulesRegex)
            }

            if (hashIsRequired) {
                hash = lineReady.hashCode()
            }

            if (lineReady.isNotEmpty() && (!hashIsRequired || index < 1 || Arrays.binarySearch(
                    hashes,
                    hash
                ) < 0)
            ) {

                if (hashIsRequired) {
                    hashesNew += hash
                }

                printWriter.println(lineReady)
                linesCount++
                if (System.currentTimeMillis() - savedTime > 500) {
                    onDNSCryptRuleAddLineListener?.onDNSCryptRuleLineAdded(linesCount)
                    savedTime = System.currentTimeMillis()
                }
            }
            line = reader.readLine()?.trim()
        }

        val arrNew = IntArray(hashes.size + hashesNew.size)
        System.arraycopy(hashes, 0, arrNew, 0, hashes.size)
        System.arraycopy(hashesNew.toIntArray(), 0, arrNew, hashes.size, hashesNew.size)
        hashes = arrNew
        hashes.sort()
    }

    private fun cleanRule(line: String, regExp: Regex): String {

        if (line.startsWith("#") || !line.matches(regExp)) {
            return ""
        }

        return line
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

    private fun addDefaultLinesIfRequired(printWriter: PrintWriter) {
        if (DNSCryptRulesVariant.CLOAKING == rulesVariant) {
            printWriter.println(itpdRedirectAddress)
        } else if (DNSCryptRulesVariant.FORWARDING == rulesVariant) {
            printWriter.println(
                "onion 127.0.0.1:" + App.instance.daggerComponent.getPathVars().get().torDNSPort
            )
        }
    }

    private fun isInputFileFormatCorrect(file: File, regExp: Regex): Boolean {
        file.bufferedReader().use {
            var line = it.readLine()?.trim()
            while (line != null) {

                if (currentThread().isInterrupted) {
                    return false
                }

                if (line.isNotEmpty() && !line.contains("#") && !line.contains("!")) {
                    return line.matches(regExp)
                }

                line = it.readLine()?.trim()
            }
        }
        return false
    }

    private fun isInputFileFormatCorrect(uri: Uri, regExp: Regex): Boolean {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine().trim()
                while (line != null) {

                    if (currentThread().isInterrupted) {
                        return false
                    }

                    if (line.isNotEmpty() && !line.contains("#") && !line.contains("!")) {
                        return line.matches(regExp)
                    }

                    line = reader.readLine().trim()
                }
            }
        }

        return false
    }

    private fun restartDNSCryptIfRequired() {
        if (ModulesStatus.getInstance().dnsCryptState == ModuleState.RUNNING) {
            ModulesRestarter.restartDNSCrypt(context)
        }
    }
}