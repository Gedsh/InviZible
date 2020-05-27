package pan.alexander.tordnscrypt.tor_fragment;

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

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.TopFragment.TorVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class TorFragmentReceiver extends BroadcastReceiver {
    private TorFragmentView view;
    private TorFragmentPresenterCallbacks presenter;

    private String torPath;
    private String busyboxPath;

    public TorFragmentReceiver(TorFragmentView view, TorFragmentPresenterCallbacks presenter) {
        this.view = view;
        this.presenter = presenter;
    }


    @Override
    public void onReceive(Context context, Intent intent) {

        if (view == null || presenter == null) {
            return;
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        PathVars pathVars = PathVars.getInstance(context);
        torPath = pathVars.getTorPath();
        busyboxPath = pathVars.getBusyboxPath();

        if (intent != null) {
            final String action = intent.getAction();
            if (action == null || action.equals("") || ((intent.getIntExtra("Mark", 0) !=
                    RootExecService.TorRunFragmentMark) &&
                    !action.equals(TopFragment.TOP_BROADCAST))) return;
            Log.i(LOG_TAG, "TorRunFragment onReceive");
            if (action.equals(RootExecService.COMMAND_RESULT)) {

                view.setTorProgressBarIndeterminate(false);

                view.setTorStartButtonEnabled(true);

                RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                if (comResult != null && comResult.getCommands().length == 0) {
                    presenter.setTorSomethingWrong();
                    return;
                }

                StringBuilder sb = new StringBuilder();
                if (comResult != null) {
                    for (String com : comResult.getCommands()) {
                        Log.i(LOG_TAG, com);
                        sb.append(com).append((char) 10);
                    }
                }

                if (sb.toString().contains("Tor_version")) {
                    String[] strArr = sb.toString().split("Tor_version");
                    if (strArr.length > 1) {
                        String[] verArr = strArr[1].trim().split(" ");
                        if (verArr.length > 2 && verArr[1].contains("version")) {
                            TorVersion = verArr[2].trim();
                            new PrefManager(context).setStrPref("TorVersion", TorVersion);

                            if (!modulesStatus.isUseModulesWithRoot()) {
                                if (!presenter.isSavedTorStatusRunning(context)) {
                                    view.setTorLogViewText();
                                }

                                presenter.refreshTorState(context);
                            }
                        }
                    }
                }

                if (sb.toString().toLowerCase().contains(torPath.toLowerCase())
                        && sb.toString().contains("checkTrRunning")) {

                    presenter.saveTorStatusRunning(context, true);
                    modulesStatus.setTorState(RUNNING);
                    view.setStartButtonText(R.string.btnTorStop);
                    presenter.startRefreshTorUnlockIPs(context);
                    presenter.displayLog(5);

                } else if (!sb.toString().toLowerCase().contains(torPath.toLowerCase())
                        && sb.toString().contains("checkTrRunning")) {
                    if (modulesStatus.getTorState() == STOPPED) {
                        presenter.saveTorStatusRunning(context, false);
                    }
                    presenter.stopDisplayLog();
                    presenter.setTorStopped(context);
                    modulesStatus.setTorState(STOPPED);
                    presenter.refreshTorState(context);
                    view.setTorProgressBarProgress(0);
                } else if (sb.toString().contains("Something went wrong!")) {
                    presenter.setTorSomethingWrong();
                }

            }

            if (action.equals(TopFragment.TOP_BROADCAST)) {
                if (TopFragment.TOP_BROADCAST.contains("TOP_BROADCAST")) {
                    Log.i(LOG_TAG, "TorRunFragment onReceive TOP_BROADCAST");

                    checkTorVersionWithRoot(context);
                }

            }

        }
    }

    private void checkTorVersionWithRoot(Context context) {
        if (context != null && presenter.isTorInstalled(context)) {

            String[] commandsCheck = {
                    busyboxPath + "pgrep -l /libtor.so 2> /dev/null",
                    busyboxPath + "echo 'checkTrRunning' 2> /dev/null",
                    busyboxPath + "echo 'Tor_version' 2> /dev/null",
                    torPath + " --version 2> /dev/null"
            };
            RootCommands rootCommands = new RootCommands(commandsCheck);
            Intent intent = new Intent(context, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.TorRunFragmentMark);
            RootExecService.performAction(context, intent);

            view.setTorProgressBarIndeterminate(true);
        }
    }
}
