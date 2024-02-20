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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.itpd_fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.utils.root.RootExecService;

import static pan.alexander.tordnscrypt.TopFragment.ITPDVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.modules.ModulesService.ITPD_KEYWORD;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.I2PD_RUN_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

import javax.inject.Inject;

public class ITPDFragmentReceiver extends BroadcastReceiver {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;

    private final ITPDFragmentView view;
    private final ITPDFragmentPresenterInterface presenter;

    private String itpdPath;
    private String busyboxPath;

    public ITPDFragmentReceiver(ITPDFragmentView view, ITPDFragmentPresenterInterface presenter) {
        App.getInstance().getDaggerComponent().inject(this);
        this.view = view;
        this.presenter = presenter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (view == null
                || view.getFragmentActivity() == null
                || view.getFragmentActivity().isFinishing()
                || presenter == null) {
            return;
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();

        itpdPath = pathVars.get().getITPDPath();
        busyboxPath = pathVars.get().getBusyboxPath();

        if (intent != null) {
            final String action = intent.getAction();
            if (action == null
                    || action.equals("")
                    || ((intent.getIntExtra("Mark", 0) != I2PD_RUN_FRAGMENT_MARK) &&
                    !action.equals(TOP_BROADCAST))) return;
            logi("I2PDFragment onReceive");

            if (action.equals(RootExecService.COMMAND_RESULT)) {

                //view.setITPDProgressBarIndeterminate(false);

                view.setITPDStartButtonEnabled(true);

                RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                if (comResult != null && comResult.getCommands().size() == 0) {

                    presenter.setITPDSomethingWrong();
                    return;
                }

                StringBuilder sb = new StringBuilder();
                if (comResult != null) {
                    for (String com : comResult.getCommands()) {
                        logi(com);
                        sb.append(com).append((char) 10);
                    }
                }

                if (sb.toString().contains("ITPD_version")) {
                    String[] strArr = sb.toString().split("ITPD_version");
                    if (strArr.length > 1) {
                        String[] verArr = strArr[1].trim().split(" ");
                        if (verArr.length > 2 && verArr[1].contains("version")) {
                            ITPDVersion = verArr[2].trim();
                            preferenceRepository.get()
                                    .setStringPreference("ITPDVersion", ITPDVersion);

                            if (!modulesStatus.isUseModulesWithRoot()) {

                                if (!ModulesAux.isITPDSavedStateRunning()) {
                                    view.setITPDLogViewText();
                                }

                                presenter.refreshITPDState();
                            }
                        }
                    }
                }

                if (sb.toString().toLowerCase().contains(itpdPath.toLowerCase())
                        && sb.toString().contains(ITPD_KEYWORD)) {
                    modulesStatus.setItpdState(RUNNING);
                    presenter.displayLog();
                } else if (!sb.toString().toLowerCase().contains(itpdPath.toLowerCase())
                        && sb.toString().contains(ITPD_KEYWORD)) {
                    if (modulesStatus.getItpdState() == STOPPED) {
                        ModulesAux.saveITPDStateRunning(false);
                    }
                    presenter.stopDisplayLog();
                    presenter.setITPDStopped();
                    modulesStatus.setItpdState(STOPPED);
                    presenter.refreshITPDState();
                } else if (sb.toString().contains("Something went wrong!")) {
                    presenter.setITPDSomethingWrong();
                }

            }

            if (action.equals(TOP_BROADCAST)) {
                if (TopFragment.TOP_BROADCAST.contains("TOP_BROADCAST")) {
                    checkITPDVersionWithRoot(context);
                    logi("ITPDRunFragment onReceive TOP_BROADCAST");
                }
            }
        }

    }

    private void checkITPDVersionWithRoot(Context context) {
        if (context != null && presenter.isITPDInstalled()) {
            List<String> commandsCheck = new ArrayList<>(Arrays.asList(
                    busyboxPath + "pgrep -l /libi2pd.so 2> /dev/null",
                    busyboxPath + "echo 'checkITPDRunning' 2> /dev/null",
                    busyboxPath + "echo 'ITPD_version' 2> /dev/null",
                    itpdPath + " --version 2> /dev/null"
            ));

            RootCommands.execute(context, commandsCheck, I2PD_RUN_FRAGMENT_MARK);

            view.setITPDProgressBarIndeterminate(true);
        }
    }
}
