package pan.alexander.tordnscrypt.dialogs;

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

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

import javax.inject.Inject;

public class AskForceClose extends ExtendedDialogFragment {

    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CachedExecutor cachedExecutor;

    private static String module;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    public static DialogFragment getInstance(String module) {
        AskForceClose.module = module;
        return new AskForceClose();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public AlertDialog.Builder assignBuilder() {
        if (getActivity() == null) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);
        builder.setMessage(String.format(getString(R.string.ask_force_close_text), module, module))
                .setTitle(getString(R.string.ask_force_close_title))
                .setPositiveButton(R.string.ask_force_close_btn, (dialog, which) -> {
                    if (getActivity() != null) {
                        final Context context = getActivity();
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            if (context != null) {
                                ModulesAux.stopModulesService(context);
                            }
                        }, 1000);

                        if (modulesStatus.isRootAvailable()) {
                            forceStopModulesWithRootMethod(getActivity(), handler);
                        } else {
                            forceStopModulesWithService(getActivity(), handler);
                        }
                    }

                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dismiss());

        return builder;
    }

    private void forceStopModulesWithService(Context context, Handler handler) {

        if (modulesStatus.isUseModulesWithRoot()) {
            return;
        }

        saveModulesAreStopped();

        Log.e(LOG_TAG, "FORCE CLOSE ALL NO ROOT METHOD");

        modulesStatus.setUseModulesWithRoot(true);
        modulesStatus.setDnsCryptState(STOPPED);
        modulesStatus.setTorState(STOPPED);
        modulesStatus.setItpdState(STOPPED);

        cleanModulesFolders(context);

        handler.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 3000);
    }

    private void forceStopModulesWithRootMethod(Context context, Handler handler) {

        saveModulesAreStopped();

        Log.e(LOG_TAG, "FORCE CLOSE ALL ROOT METHOD");

        boolean useModulesWithRoot = modulesStatus.isUseModulesWithRoot();

        ModulesKiller.forceCloseApp(pathVars.get());

        cleanModulesFolders(context);

        if (!useModulesWithRoot) {
            handler.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 3000);
        }
    }

    private void cleanModulesFolders(Context context) {

        String appDataDir = pathVars.get().getAppDataDir();

        cachedExecutor.submit(() -> {
            FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "public-resolvers.md");
            FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "public-resolvers.md.minisig");
            FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "relays.md");
            FileManager.deleteFileSynchronous(context, appDataDir
                    + "/app_data/dnscrypt-proxy", "relays.md.minisig");
            FileManager.deleteDirSynchronous(context, appDataDir + "/tor_data");
            FileManager.deleteDirSynchronous(context, appDataDir + "/i2pd_data");
        });
    }

    private void saveModulesAreStopped() {
        ModulesAux.saveDNSCryptStateRunning(false);
        ModulesAux.saveTorStateRunning(false);
        ModulesAux.saveITPDStateRunning(false);
    }
}
