package pan.alexander.tordnscrypt.itpd_fragment;

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.TopFragment.ITPDVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class ITPDFragmentReceiver extends BroadcastReceiver {
    private ITPDFragmentView view;
    private ITPDFragmentPresenterCallbacks presenter;

    private String itpdPath;
    private String busyboxPath;

    public ITPDFragmentReceiver(ITPDFragmentView view, ITPDFragmentPresenterCallbacks presenter) {
        this.view = view;
        this.presenter = presenter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (view == null || presenter == null) {
            return;
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        PathVars pathVars = new PathVars(context);
        itpdPath = pathVars.itpdPath;
        busyboxPath = pathVars.busyboxPath;

        if (intent != null) {
            final String action = intent.getAction();
            if (action == null || action.equals("") || ((intent.getIntExtra("Mark", 0) !=
                    RootExecService.I2PDRunFragmentMark) &&
                    !action.equals(TOP_BROADCAST))) return;
            Log.i(LOG_TAG, "I2PDFragment onReceive");

            if (action.equals(RootExecService.COMMAND_RESULT)) {

                view.setITPDProgressBarIndeterminate(false);

                view.setITPDStartButtonEnabled(true);

                RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                if (comResult != null && comResult.getCommands().length == 0) {

                    presenter.setITPDSomethingWrong();
                    return;
                }

                StringBuilder sb = new StringBuilder();
                if (comResult != null) {
                    for (String com : comResult.getCommands()) {
                        Log.i(LOG_TAG, com);
                        sb.append(com).append((char) 10);
                    }
                }

                if (sb.toString().contains("ITPD_version")) {
                    String[] strArr = sb.toString().split("ITPD_version");
                    if (strArr.length > 1) {
                        String[] verArr = strArr[1].trim().split(" ");
                        if (verArr.length > 2 && verArr[1].contains("version")) {
                            ITPDVersion = verArr[2].trim();
                            new PrefManager(context).setStrPref("ITPDVersion", ITPDVersion);

                            if (!modulesStatus.isUseModulesWithRoot()) {

                                if (!presenter.isSavedITPDStatusRunning(context)) {
                                    view.setITPDLogViewText();
                                }

                                presenter.refreshITPDState(context);
                            }
                        }
                    }
                }

                if (sb.toString().toLowerCase().contains(itpdPath)
                        && sb.toString().contains("checkITPDRunning")) {

                    presenter.setITPDRunning();
                    presenter.saveITPDStatusRunning(context, true);
                    modulesStatus.setItpdState(RUNNING);
                    presenter.displayLog(10000);

                } else if (!sb.toString().toLowerCase().contains(itpdPath)
                        && sb.toString().contains("checkITPDRunning")) {
                    if (modulesStatus.getItpdState() == STOPPED) {
                        presenter.saveITPDStatusRunning(context, false);
                    }
                    presenter.stopDisplayLog();
                    presenter.setITPDStopped();
                    modulesStatus.setItpdState(STOPPED);
                    presenter.refreshITPDState(context);
                } else if (sb.toString().contains("Something went wrong!")) {
                    presenter.setITPDSomethingWrong();
                }

            }

            if (action.equals(TOP_BROADCAST)) {
                if (TopFragment.TOP_BROADCAST.contains("TOP_BROADCAST")) {
                    checkITPDVersionWithRoot(context);
                    Log.i(LOG_TAG, "ITPDRunFragment onReceive TOP_BROADCAST");
                }
            }
        }

    }

    private void checkITPDVersionWithRoot(Context context) {
        if (context != null && presenter.isITPDInstalled(context)) {
            String[] commandsCheck = {
                    busyboxPath + "pgrep -l /i2pd",
                    busyboxPath + "echo 'checkITPDRunning'",
                    busyboxPath + "echo 'ITPD_version'",
                    itpdPath + " --version"};
            RootCommands rootCommands = new RootCommands(commandsCheck);
            Intent intent = new Intent(context, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.I2PDRunFragmentMark);
            RootExecService.performAction(context, intent);

            view.setITPDProgressBarIndeterminate(true);
        }
    }
}
