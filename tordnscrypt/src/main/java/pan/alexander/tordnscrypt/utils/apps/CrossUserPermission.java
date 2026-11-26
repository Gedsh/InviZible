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

    Copyright 2019-2026 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.apps;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Collections;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.root.RootCommands;
import pan.alexander.tordnscrypt.vpn.Rule;

import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.NULL_MARK;

/** Owns one asynchronous request per process; never retains an Activity or Fragment. */
public final class CrossUserPermission {
    public static final String APPS_CHANGED = "pan.alexander.tordnscrypt.CROSS_USER_APPS_CHANGED";
    private static final Requests requests = new Requests();

    private CrossUserPermission() { }

    /** Returns true when an asynchronous grant request was accepted or is already pending. */
    public static boolean request(Context context, PathVars paths) {
        Context app = context.getApplicationContext();
        PreferenceRepository preferences = App.getInstance().getDaggerComponent().getPreferenceRepository().get();
        boolean supportedBuild = !paths.getAppVersion().endsWith("p");
        return requests.request(new Environment() {
            @Override
            public boolean granted() {
                return Utils.isInteractAcrossUsersPermissionGranted(app);
            }

            @Override
            public boolean eligible() {
                // Use the same source as RootExecService, including failures detected by RootExecutor.
                return supportedBuild && preferences.getBoolPreference(ROOT_IS_AVAILABLE);
            }
            @Override
            public boolean dispatch(ResultReceiver receiver) {
                int userId = android.os.Process.myUid() / 100_000;
                return RootCommands.execute(app, Collections.singletonList(
                        "pm grant --user " + userId + " "
                                + app.getPackageName() + " android.permission.INTERACT_ACROSS_USERS"),
                        NULL_MARK, receiver);
            }
            @Override
            public void refresh() {
                Rule.invalidateInstalledApps();
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                if (ModulesService.serviceIsRunning) {
                    ModulesAux.makeModulesStateExtraLoop(app);
                }
                LocalBroadcastManager.getInstance(app).sendBroadcast(new Intent(APPS_CHANGED));
            }
        });
    }

    interface Environment {
        boolean granted();
        boolean eligible();
        boolean dispatch(ResultReceiver receiver);
        void refresh();
    }

    static final class Requests {
        private long sequence;
        private long pending;

        boolean request(Environment environment) {
            final long id;
            synchronized (this) {
                if (environment.granted()) {
                    return false;
                }
                if (pending != 0) {
                    return true;
                }
                if (!environment.eligible()) {
                    return false;
                }
                id = ++sequence;
                pending = id;
            }
            ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
                @Override
                protected void onReceiveResult(int code, Bundle data) {
                    synchronized (Requests.this) {
                        if (pending != id) {
                            return;
                        }
                        pending = 0;
                    }
                    try {
                        if (environment.granted()) {
                            logi("INTERACT_ACROSS_USERS grant verified");
                            environment.refresh();
                        } else {
                            logw("INTERACT_ACROSS_USERS request finished without a grant");
                        }
                    } catch (Exception e) {
                        logw("INTERACT_ACROSS_USERS completion failed", e);
                    }
                }
            };
            try {
                if (environment.dispatch(receiver)) {
                    logi("Request INTERACT_ACROSS_USERS permission to access applications in work profile");
                    return true;
                }
            } catch (Exception e) {
                logw("INTERACT_ACROSS_USERS dispatch failed", e);
            }
            synchronized (this) {
                if (pending == id) {
                    pending = 0;
                }
            }
            return false;
        }
    }
}
