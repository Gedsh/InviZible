package pan.alexander.tordnscrypt.settings.dnscrypt_settings

import android.content.Context
import android.util.Log
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.WakeLocksManager
import pan.alexander.tordnscrypt.utils.enums.DNSCryptRulesVariant
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import java.io.File
import java.io.PrintWriter
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList

private val blackListHostRulesRegex = Regex("^[a-zA-Z\\d-.=*\\[\\]]+$")
private val blacklistIPRulesRegex = Regex("^(?:[0-9*]{1,3}\\.){3}[0-9*]{1,3}(?:/\\d+)*$")
private val cloakingRulesRegex = Regex("^[a-zA-Z\\d-.=*]+[ \\t]+[a-zA-Z\\d-.=*]+$")
private val forwardingRulesRegex = Regex("^[a-zA-Z\\d-.]+[ \\t]+(?:[0-9*]{1,3}\\.){3}[0-9*]{1,3}(?:, ?(?:[0-9*]{1,3}\\.){3}[0-9*]{1,3})*$")
private val whiteListHostRulesRegex = Regex("^[a-zA-Z\\d-.=*\\[\\]]+$")
private val hostFileRegex = Regex("^(?:0.0.0.0|127.0.0.1)[ \\t]+[a-zA-Z\\d-.]+$")
private const val itpdRedirectAddress = "*i2p 10.191.0.1"
private val excludeFromHost = listOf("localhost", "localhost.localdomain", "local", "0.0.0.0")
private val reentrantLock = ReentrantLock()
private val wakeLocksManager = WakeLocksManager.getInstance()

class ImportRules(private val context: Context,
                  private var rulesVariant: DNSCryptRulesVariant,
                  private val localRules: Boolean,
                  private val filePathToImport: String) : Thread() {

    private val pathVars: PathVars = PathVars.getInstance(context)

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
            DNSCryptRulesVariant.BLACKLIST_HOSTS -> doTheJob(blackListHostRulesPath,
                    blackListHostRulesLocalPath, blackListHostRulesRemotePath, blackListHostRulesRegex, filePathToImport)

            DNSCryptRulesVariant.WHITELIST_HOSTS -> doTheJob(whiteListHostRulesPath,
                    whiteListHostRulesLocalPath, whiteListHostRulesRemotePath, whiteListHostRulesRegex, filePathToImport)

            DNSCryptRulesVariant.BLACKLIST_IPS -> doTheJob(blackListIPRulesPath,
                    blackListIPRulesLocalPath, blackListIPRulesRemotePath, blacklistIPRulesRegex, filePathToImport)

            DNSCryptRulesVariant.CLOAKING -> doTheJob(cloakingRulesPath,
                    cloakingRulesLocalPath, cloakingRulesRemotePath, cloakingRulesRegex, filePathToImport)

            DNSCryptRulesVariant.FORWARDING -> doTheJob(forwardingRulesPath,
                    forwardingRulesLocalPath, forwardingRulesRemotePath, forwardingRulesRegex, filePathToImport)

            DNSCryptRulesVariant.UNDEFINED -> return
        }

    }

    private fun doTheJob(rulesFilePath: String,
                         localRulesFilePath: String,
                         remoteRulesFilePath: String,
                         rulesRegex: Regex,
                         filesToImport: String) {


        val files = filesToImport.split(":").toMutableList()

        reentrantLock.lock()

        if (!wakeLocksManager.isPowerWakeLockHeld) {
            wakeLocksManager.managePowerWakelock(context, true)
            powerLocked = true
        }

        onDNSCryptRuleAddLineListener?.onDNSCryptRuleLinesAddingStarted(currentThread())

        try {
            if (files.isNotEmpty()) {
                File(rulesFilePath).printWriter().use {
                    addDefaultLinesIfRequired(it)
                    mixFiles(it, localRulesFilePath, remoteRulesFilePath, rulesRegex, files)
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
        }


        onDNSCryptRuleAddLineListener?.onDNSCryptRuleLinesAddingFinished()

        if (powerLocked) {
            wakeLocksManager.stopPowerWakelock()
        }

        reentrantLock.unlock()
    }

    private fun mixFiles(printWriter: PrintWriter,
                         localRulesFilePath: String,
                         remoteRulesFilePath: String,
                         rulesRegex: Regex,
                         filesToImport: MutableList<String>) {
        var linesCount = 0
        var hash = 0
        var hashes = IntArray(0)
        val hashesNew = ArrayList<Int>()
        var savedTime = System.currentTimeMillis()

        val fileToAdd: String = if (localRules) {
            remoteRulesFilePath
        } else {
            localRulesFilePath
        }

        val addFile = File(fileToAdd)
        if (addFile.isFile) {
            filesToImport += fileToAdd
        }

        val hashIsRequired = filesToImport.size > 1

        filesToImport.forEachIndexed { index, file ->
            if (file.isNotEmpty()) {
                val inputFile = File(file)

                if (inputFile.isFile) {
                    try {
                        if (DNSCryptRulesVariant.BLACKLIST_HOSTS == rulesVariant) {
                            blackListFileIsHost = isInputFileFormatCorrect(inputFile, hostFileRegex)
                        }

                        if (blackListFileIsHost || isInputFileFormatCorrect(inputFile, rulesRegex)) {
                            inputFile.bufferedReader().use {
                                var line = it.readLine()?.trim()
                                while (line != null && !currentThread().isInterrupted) {
                                    val lineReady = if (blackListFileIsHost) {
                                        hostToBlackList(line)
                                    } else {
                                        cleanRule(line, rulesRegex)
                                    }

                                    if (hashIsRequired) {
                                        hash = lineReady.hashCode()
                                    }

                                    if (lineReady.isNotEmpty() && (!hashIsRequired || index < 1 || Arrays.binarySearch(hashes, hash) < 0)) {

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
                                    line = it.readLine()?.trim()
                                }

                                onDNSCryptRuleAddLineListener?.onDNSCryptRuleLineAdded(linesCount)
                                hashesNew.addAll(hashes.toList())
                                hashes = hashesNew.toIntArray()
                                hashes.sort()
                                hashesNew.clear()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(LOG_TAG, "ImportRules Exception " + e.message + " " + e.cause)
                    }
                }
            }
        }

        if (linesCount > 0) {
            restartDNSCryptIfRequired()
        }
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
            printWriter.println("onion 127.0.0.1:" + PathVars.getInstance(context).torDNSPort)
        }
    }

    private fun isInputFileFormatCorrect(file: File, regExp: Regex): Boolean {
        file.bufferedReader().use {
            var line = it.readLine()?.trim()
            while (line != null) {

                if (currentThread().isInterrupted) {
                    return false
                }

                if (line.isNotEmpty() && !line.contains("#")) {
                    return line.matches(regExp)
                }

                line = it.readLine()?.trim()
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