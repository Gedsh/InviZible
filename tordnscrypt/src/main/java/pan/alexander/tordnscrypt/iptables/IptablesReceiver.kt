package pan.alexander.tordnscrypt.iptables

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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import pan.alexander.tordnscrypt.App
import pan.alexander.tordnscrypt.modules.ModulesStatus
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.*
import pan.alexander.tordnscrypt.utils.root.RootCommands
import pan.alexander.tordnscrypt.utils.root.RootCommandsMark.IPTABLES_MARK
import pan.alexander.tordnscrypt.utils.root.RootExecService.*
import java.util.*
import javax.inject.Inject

class IptablesReceiver : BroadcastReceiver() {

    @Inject
    lateinit var handler: dagger.Lazy<Handler>

    var lastIptablesCommandsReturnError = false
    private var savedError = ""

    override fun onReceive(context: Context?, intent: Intent?) {
        App.instance.daggerComponent.inject(this)

        if (context == null || intent == null) {
            return
        }

        val action = intent.action

        if (action == null || action.isBlank() || action != COMMAND_RESULT
                || intent.getIntExtra("Mark", 0) != IPTABLES_MARK) {
            return
        }

        Log.i(LOG_TAG, "IptablesReceiver onReceive")

        val comResult = intent.getSerializableExtra("CommandsResult") as RootCommands?

        val result = StringBuilder()
        if (comResult != null) {
            for (com in comResult.commands) {
                Log.i(LOG_TAG, com)
                result.append(com).append("\n")
            }
        }

        if (result.isBlank()) {
            lastIptablesCommandsReturnError = false
            return
        }

        val resultStr = result.toString().lowercase(Locale.ROOT)

        lastIptablesCommandsReturnError = true

        //Prevent cyclic iptables update
        val removedDigits = resultStr.replace(Regex("\\d+"), "*")
        if (removedDigits == savedError) {
            return
        }

        savedError = removedDigits

        handler.get().let {

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val showToastWithCommandsResultError = sharedPreferences.getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false)
            val refreshRules = sharedPreferences.getBoolean(REFRESH_RULES, false)

            if (resultStr.contains("unknown option \"-w\"")) {
                sharedPreferences.edit().putBoolean(WAIT_IPTABLES, false).apply()
                it.postDelayed({ ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true) }, 1000)
            } else if (refreshRules
                && (resultStr.contains(" -w ")
                || resultStr.contains("Exit code=4")
                || resultStr.contains("try again")) ||
                resultStr.matches(Regex(".*tun\\d+.*"))) {
                it.postDelayed({ ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true) }, 5000)
            }
            if (showToastWithCommandsResultError) {
                it.post { Toast.makeText(context, result.toString().trim(), Toast.LENGTH_LONG).show() }
            }
        }
    }
}
