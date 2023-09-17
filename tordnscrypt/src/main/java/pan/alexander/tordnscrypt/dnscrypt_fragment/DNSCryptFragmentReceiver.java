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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.dnscrypt_fragment;

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
import static pan.alexander.tordnscrypt.modules.ModulesService.DNSCRYPT_KEYWORD;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.DNSCRYPT_RUN_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

import javax.inject.Inject;

public class DNSCryptFragmentReceiver extends BroadcastReceiver {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CachedExecutor cachedExecutor;
    @Inject
    public Lazy<Verifier> verifierLazy;

    private final DNSCryptFragmentView view;
    private final DNSCryptFragmentPresenterInterface presenter;

    private String dnscryptPath;
    private String busyboxPath;



    public DNSCryptFragmentReceiver(DNSCryptFragmentView view, DNSCryptFragmentPresenter presenter) {
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

        dnscryptPath = pathVars.get().getDNSCryptPath();
        busyboxPath = pathVars.get().getBusyboxPath();


        if (intent != null) {
            final String action = intent.getAction();
            if (action == null
                    || action.equals("")
                    || ((intent.getIntExtra("Mark", 0) != DNSCRYPT_RUN_FRAGMENT_MARK) &&
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
                    if (strArr.length > 1 && strArr[1].trim().matches("(STDOUT=)?\\d+\\.\\d+\\.\\d+")) {
                        DNSCryptVersion = strArr[1].replace("STDOUT=", "").trim();
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
                        && sb.toString().contains(DNSCRYPT_KEYWORD)) {
                    modulesStatus.setDnsCryptState(RUNNING);
                    presenter.displayLog();
                } else if (!sb.toString().toLowerCase().contains(dnscryptPath.toLowerCase())
                        && sb.toString().contains(DNSCRYPT_KEYWORD)) {
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

                cachedExecutor.submit(() -> {
                    try {
                        if (activity == null || activity.isFinishing()) {
                            return;
                        }

                        Verifier verifier = verifierLazy.get();
                        String appSign = verifier.getAppSignature();
                        String appSignAlt = verifier.getApkSignature();
                        if (!verifier.decryptStr(verifier.getWrongSign(), appSign, appSignAlt).equals(TOP_BROADCAST)) {

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

            RootCommands.execute(context, commandsCheck, DNSCRYPT_RUN_FRAGMENT_MARK);

            view.setDNSCryptProgressBarIndeterminate(true);
        }
    }
}
