package pan.alexander.tordnscrypt.installer;

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class InstallerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {

            if (isBroadcastMatch(intent)) {
                Log.i(LOG_TAG, "InstallerReceiver onReceive");
            } else {
                return;
            }

            RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

            if (comResult == null || isRootCommandResultEmpty(comResult)) {
                return;
            }

            String rootCommandsResult = getResultString(comResult);

            doAppropriateAction(rootCommandsResult);
        }
    }

    private void doAppropriateAction(String rootCommandsResult) {
        if (rootCommandsResult.replaceAll("\\W+", "").equals("checkModulesRunning")) {
            Installer.continueInstallation(false);

            Log.i(LOG_TAG, "InstallerReceiver receive " + rootCommandsResult + " continueInstallation");
        } else {
            Installer.continueInstallation(true);

            Log.i(LOG_TAG, "InstallerReceiver receive \"" + rootCommandsResult + "\" interruptInstallation");
        }
    }

    private String getResultString(RootCommands comResult) {
        StringBuilder sb = new StringBuilder();
        for (String com : comResult.getCommands()) {
            sb.append(com);
        }
        return sb.toString();
    }

    private boolean isRootCommandResultEmpty(RootCommands comResult) {
        return comResult.getCommands().size() == 0;
    }

    private boolean isBroadcastMatch(Intent intent) {
        if (intent == null) {
            return false;
        }

        String action = intent.getAction();

        if ((action == null) || (action.equals(""))) {
            return false;
        }

        if (!action.equals(RootExecService.COMMAND_RESULT)) {
            return false;
        }

        return intent.getIntExtra("Mark", 0) == RootExecService.InstallerMark;
    }
}
