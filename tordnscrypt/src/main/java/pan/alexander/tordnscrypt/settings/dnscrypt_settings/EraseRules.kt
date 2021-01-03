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