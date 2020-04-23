package pan.alexander.tordnscrypt.settings.tor_preferences

import android.content.Context
import android.util.Log
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import java.io.File
import java.io.PrintWriter

class ModifyForwardingRules(private val context: Context,
                            private val lineToReplaceTo: String) : Thread() {

    private val forwardingRulesFile: String = PathVars.getInstance(context).dnsCryptForwardingRulesPath
    private val localForwardingRulesFile: String = PathVars.getInstance(context).dnsCryptLocalForwardingRulesPath
    private val cacheDir: String = PathVars.getInstance(context).appDataDir + "/cache/"
    private val tempFile: String = "$cacheDir/tmpForwardingRules.txt"
    private val lineToFindRegExp = Regex("^onion +127.0.0.1:\\d+$")

    override fun run() {
        try {
            val dir = File(cacheDir)
            if (!dir.isDirectory) {
                dir.mkdirs()
            }

            replaceLineInFile(forwardingRulesFile, tempFile)
            replaceLineInFile(localForwardingRulesFile, tempFile)

            restartDNSCryptIfRequired()

        } catch (e: java.lang.Exception) {
            Log.e(LOG_TAG, "ImportRules Exception " + e.message + " " + e.cause)
        }
    }

    private fun replaceLineInFile(inputFilePath: String, tmpOutputFilePath: String) {
        try {

            val inputFile = File(inputFilePath)
            val outputFile = File(tmpOutputFilePath)

            if (inputFile.isFile) {
                outputFile.printWriter().use {printWriter ->
                    writeToFile(inputFile, printWriter)
                }

                outputFile.copyTo(inputFile, true)
                outputFile.delete()
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "ImportRules Exception " + e.message + " " + e.cause)
        }
    }

    private fun writeToFile(inputFile: File, printWriter: PrintWriter) {

        try {

            inputFile.bufferedReader().use {
                var line = it.readLine()?.trim()
                while (line != null && !currentThread().isInterrupted) {

                    if (line.matches(lineToFindRegExp)) {
                        printWriter.println(lineToReplaceTo)
                    } else if (line.isNotEmpty()) {
                        printWriter.println(line)
                    }
                    line = it.readLine()?.trim()
                }
            }

        } catch (e: Exception) {
            Log.e(LOG_TAG, "ModifyForwardingRules " + e.message + " " + e.cause)
        }
    }

    private fun restartDNSCryptIfRequired() {
        if (ModulesStatus.getInstance().dnsCryptState == ModuleState.RUNNING) {
            ModulesRestarter.restartDNSCrypt(context)
        }
    }
}