package pan.alexander.tordnscrypt.settings.dnscrypt_settings

import android.content.Context
import android.util.Log
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.R
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment
import pan.alexander.tordnscrypt.modules.ModulesRestarter
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.settings.PathVars
import pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG
import pan.alexander.tordnscrypt.utils.enums.DNSCryptRulesVariant
import pan.alexander.tordnscrypt.utils.enums.ModuleState
import java.io.File
import java.lang.Exception

class EraseRules(private val context: Context,
                 private val fragmentManager: FragmentManager,
                 private val dnsCryptRulesVariant: DNSCryptRulesVariant,
                 private val remoteRulesLinkPreferenceTag: String) : Thread() {

    override fun run() {
        val pathVars = PathVars.getInstance(context)

        when(dnsCryptRulesVariant) {

            DNSCryptRulesVariant.BLACKLIST_HOSTS -> eraseRuleVariant(pathVars.dnsCryptBlackListPath,
                    pathVars.dnsCryptLocalBlackListPath, pathVars.dnsCryptRemoteBlackListPath)

            DNSCryptRulesVariant.BLACKLIST_IPS -> eraseRuleVariant(pathVars.dnsCryptIPBlackListPath,
                    pathVars.dnsCryptLocalIPBlackListPath, pathVars.dnsCryptRemoteIPBlackListPath)

            DNSCryptRulesVariant.WHITELIST_HOSTS -> eraseRuleVariant(pathVars.dnsCryptWhiteListPath,
                    pathVars.dnsCryptLocalWhiteListPath, pathVars.dnsCryptRemoteWhiteListPath)

            DNSCryptRulesVariant.CLOAKING -> eraseRuleVariant(pathVars.dnsCryptCloakingRulesPath,
                    pathVars.dnsCryptLocalCloakingRulesPath, pathVars.dnsCryptRemoteCloakingRulesPath)

            DNSCryptRulesVariant.FORWARDING -> eraseRuleVariant(pathVars.dnsCryptForwardingRulesPath,
                    pathVars.dnsCryptLocalForwardingRulesPath, pathVars.dnsCryptRemoteForwardingRulesPath)

            DNSCryptRulesVariant.UNDEFINED -> return

        }
    }

    private fun eraseRuleVariant(rulesFilePath: String, localRulesFilePath: String, remoteRulesFilePath: String) {
        eraseFile(rulesFilePath)
        eraseFile(localRulesFilePath)
        eraseFile(remoteRulesFilePath)
        erasePreference()
        restartDNSCryptIfRequired()
        showFinalDialog()
    }

    private fun eraseFile(filePath: String) {

        var eraseText = ""
        if (dnsCryptRulesVariant == DNSCryptRulesVariant.CLOAKING) {
            eraseText = "*i2p 10.191.0.1"
        } else if (dnsCryptRulesVariant == DNSCryptRulesVariant.FORWARDING) {
            eraseText = "onion 127.0.0.1:" + PathVars.getInstance(context).torDNSPort
        }

        try {
            val file = File(filePath)
            if (file.isFile) {
                file.writeText(eraseText)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "EraseRules Exception " + e.message + " " + e.cause)
        }

    }

    private fun erasePreference() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPreferences.edit().putString(remoteRulesLinkPreferenceTag, "").apply()
    }

    private fun showFinalDialog() {
        val dialog = NotificationDialogFragment.newInstance(R.string.erase_dnscrypt_rules_dialog_message)
        dialog.show(fragmentManager, "EraseDialog")
    }

    private fun restartDNSCryptIfRequired() {
        if (ModulesStatus.getInstance().dnsCryptState == ModuleState.RUNNING) {
            ModulesRestarter.restartDNSCrypt(context)
        }
    }
}