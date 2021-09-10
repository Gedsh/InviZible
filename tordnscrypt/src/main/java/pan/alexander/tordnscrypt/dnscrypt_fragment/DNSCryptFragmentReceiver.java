package pan.alexander.tordnscrypt.dnscrypt_fragment;

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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.utils.root.RootExecService;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class DNSCryptFragmentReceiver extends BroadcastReceiver {

    private final DNSCryptFragmentView view;
    private final DNSCryptFragmentPresenterInterface presenter;

    private String dnscryptPath;
    private String busyboxPath;

    private final Lazy<PreferenceRepository> preferenceRepository;

    public DNSCryptFragmentReceiver(DNSCryptFragmentView view, DNSCryptFragmentPresenter presenter) {
        this.view = view;
        this.presenter = presenter;
        this.preferenceRepository = App.instance.daggerComponent.getPreferenceRepository();
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

        PathVars pathVars = PathVars.getInstance(context);
        dnscryptPath = pathVars.getDNSCryptPath();
        busyboxPath = pathVars.getBusyboxPath();


        if (intent != null) {
            final String action = intent.getAction();
            if (action == null || action.equals("") || ((intent.getIntExtra("Mark", 0) !=
                    RootExecService.DNSCryptRunFragmentMark) &&
                    !action.equals(TOP_BROADCAST))) return;
            Log.i(LOG_TAG, "DNSCryptRunFragment onReceive");

            if (action.equals(RootExecService.COMMAND_RESULT)) {

                view.setDNSCryptProgressBarIndeterminate(false);

                view.setDNSCryptStartButtonEnabled(true);

                RootCommands comResult = (RootCommands) intent.getSerializableExtra("CommandsResult");

                if (comResult != null && comResult.getCommands().size() == 0) {
                    presenter.setDnsCryptSomethingWrong();
                    modulesStatus.setDnsCryptState(FAULT);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                if (comResult != null) {
                    for (String com : comResult.getCommands()) {
                        Log.i(LOG_TAG, com);
                        sb.append(com).append((char) 10);
                    }
                }

                if (sb.toString().contains("DNSCrypt_version")) {
                    String[] strArr = sb.toString().split("DNSCrypt_version");
                    if (strArr.length > 1 && strArr[1].trim().matches("\\d+\\.\\d+\\.\\d+")) {
                        DNSCryptVersion = strArr[1].trim();
                        preferenceRepository.get()
                                .setStringPreference("DNSCryptVersion", DNSCryptVersion);

                        if (!modulesStatus.isUseModulesWithRoot()) {

                            if (!ModulesAux.isDnsCryptSavedStateRunning()) {
                                view.setDNSCryptLogViewText();
                            }

                            presenter.refreshDNSCryptState();
                        }
                    }
                }

                if (sb.toString().toLowerCase().contains(dnscryptPath.toLowerCase())
                        && sb.toString().contains("checkDNSRunning")) {
                    modulesStatus.setDnsCryptState(RUNNING);
                    presenter.displayLog();
                } else if (!sb.toString().toLowerCase().contains(dnscryptPath.toLowerCase())
                        && sb.toString().contains("checkDNSRunning")) {
                    if (modulesStatus.getDnsCryptState() == STOPPED) {
                        ModulesAux.saveDNSCryptStateRunning(false);
                    }
                    presenter.stopDisplayLog();
                    presenter.setDnsCryptStopped();
                    modulesStatus.setDnsCryptState(STOPPED);
                    presenter.refreshDNSCryptState();
                } else if (sb.toString().contains("Something went wrong!")) {
                    presenter.setDnsCryptSomethingWrong();
                    modulesStatus.setDnsCryptState(FAULT);
                }

            } else if (action.equals(TOP_BROADCAST)) {
                if (TOP_BROADCAST.contains("TOP_BROADCAST")) {
                    Log.i(LOG_TAG, "DNSCryptRunFragment onReceive TOP_BROADCAST");

                    checkDNSVersionWithRoot(context);
                }

                FragmentManager fragmentManager = view.getFragmentFragmentManager();
                Activity activity = view.getFragmentActivity();

                CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
                    try {
                        if (activity == null || activity.isFinishing()) {
                            return;
                        }

                        Verifier verifier = new Verifier(context);
                        String appSignAlt = verifier.getApkSignature();
                        if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {

                            if (fragmentManager != null) {
                                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                        activity, context.getString(R.string.verifier_error), "15");
                                if (notificationHelper != null) {
                                    notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                                }
                            }
                        }

                    } catch (Exception e) {
                        if (fragmentManager != null) {
                            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                    context, context.getString(R.string.verifier_error), "18");
                            if (notificationHelper != null) {
                                notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
                            }
                        }
                        Log.e(LOG_TAG, "DNSCryptRunFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                                Arrays.toString(e.getStackTrace()));
                    }
                });

            }
        }
    }

    private void checkDNSVersionWithRoot(Context context) {

        if (presenter.isDNSCryptInstalled()) {

            List<String> commandsCheck = new ArrayList<>(Arrays.asList(
                    busyboxPath + "pgrep -l /libdnscrypt-proxy.so 2> /dev/null",
                    busyboxPath + "echo 'checkDNSRunning' 2> /dev/null",
                    busyboxPath + "echo 'DNSCrypt_version' 2> /dev/null",
                    dnscryptPath + " --version 2> /dev/null"
            ));
            RootCommands rootCommands = new RootCommands(commandsCheck);
            Intent intent = new Intent(context, RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands", rootCommands);
            intent.putExtra("Mark", RootExecService.DNSCryptRunFragmentMark);
            RootExecService.performAction(context, intent);

            view.setDNSCryptProgressBarIndeterminate(true);
        }
    }
}
