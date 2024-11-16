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

package pan.alexander.tordnscrypt.utils.root;

import static pan.alexander.tordnscrypt.utils.AppExtension.getApp;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.OPERATION_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.NULL_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootServiceNotificationManager.DEFAULT_NOTIFICATION_ID;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.di.SharedPreferencesModule;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

@SuppressLint("UnsafeOptInUsageWarning")
public class RootExecService extends Service
        implements RootExecutor.OnCommandsProgressListener, RootExecutor.OnCommandsDoneListener {

    public RootExecService() {
    }

    public static final String RUN_COMMAND = "pan.alexander.tordnscrypt.action.RUN_COMMAND";
    public static final String COMMAND_RESULT = "pan.alexander.tordnscrypt.action.COMMANDS_RESULT";
    public static final String LOG_TAG = "pan.alexander.TPDCLogs";

    @Inject
    RootExecutor rootExecutor;
    @Inject
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    Lazy<SharedPreferences> defaultPreferences;
    @Inject
    Lazy<PreferenceRepository> preferenceRepository;

    private NotificationManager systemNotificationManager;
    private RootServiceNotificationManager serviceNotificationManager;


    @Override
    public void onCreate() {
        getApp(getApplicationContext()).getDaggerComponent().inject(this);
        super.onCreate();

        systemNotificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        serviceNotificationManager = new RootServiceNotificationManager(this, systemNotificationManager);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && systemNotificationManager != null) {
            serviceNotificationManager.createNotificationChannel();
        }

        rootExecutor.setOnCommandsDoneListener(this);
        rootExecutor.setOnCommandsProgressListener(this);
    }

    @Override
    public void onDestroy() {

        rootExecutor.setOnCommandsDoneListener(null);
        rootExecutor.setOnCommandsProgressListener(null);

        rootExecutor.stopExecutor();

        super.onDestroy();
    }

    public static void performAction(Context context, Intent intent) {
        final PreferenceRepository preferences = App.getInstance().getDaggerComponent().getPreferenceRepository().get();

        boolean rootIsAvailable = preferences.getBoolPreference(ROOT_IS_AVAILABLE);

        if ((intent == null) || Objects.equals(intent.getAction(), "") || !rootIsAvailable) return;


        logi("RootExecService Root = " + true + " performAction");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            loge("RootExecService performAction", e, true);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        moveServiceToForeground();

        if (intent == null) {
            moveServiceToBackground();
            return START_NOT_STICKY;
        }

        final String action = intent.getAction();

        if ((action == null) || (action.isEmpty())) {

            moveServiceToBackground();
            return START_NOT_STICKY;
        }

        if (action.equals(RUN_COMMAND)) {
            RootCommands rootCommands = (RootCommands) intent.getSerializableExtra("Commands");
            int mark = intent.getIntExtra("Mark", 0);

            if (rootCommands != null && rootCommands.getCommands() != null) {
                rootExecutor.execute(
                        rootCommands.getCommands(),
                        mark
                );
            }
        }

        return START_NOT_STICKY;
    }

    private void sendResult(List<String> commandsResult, int mark) {

        if (commandsResult == null || mark == NULL_MARK) {
            return;
        }

        if (!commandsResult.isEmpty()
                && commandsResult.get(0).equals(RootConsoleClosedException.message)) {
            switchToVpnMode();
        }

        RootCommands comResult = new RootCommands(commandsResult);
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult", comResult);
        intent.putExtra("Mark", mark);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void moveServiceToForeground() {
        if (systemNotificationManager != null) {
            serviceNotificationManager.sendNotification(
                    getString(R.string.notification_exec_root_commands),
                    ""
            );
        }
    }

    private void moveServiceToBackground() {
        if (systemNotificationManager != null) {

            systemNotificationManager.cancel(DEFAULT_NOTIFICATION_ID);

            try {
                stopForeground(true);
            } catch (Exception e) {
                loge("RootExecService moveServiceToBackground", e);
            }

            serviceNotificationManager.resetNotification();
        }
    }

    @Override
    public void onCommandsProgress(int progress) {
        updateNotificationProgress(progress);
    }

    private void updateNotificationProgress(int progress) {
        if (systemNotificationManager != null) {
            serviceNotificationManager.updateNotification(
                    getString(R.string.notification_exec_root_commands),
                    "",
                    progress
            );
        }
    }

    @Override
    public void onCommandsDone(@NonNull List<String> results, int mark) {
        sendResult(results, mark);
        moveServiceToBackground();
    }

    @Override
    public void onTimeout(int startId) {
        moveServiceToBackground();
        super.onTimeout(startId);
    }

    private void switchToVpnMode() {

        final Intent prepareIntent = VpnService.prepare(this);
        final boolean vpnServiceEnabled = defaultPreferences.get().getBoolean(VPN_SERVICE_ENABLED, false);
        if (prepareIntent != null || vpnServiceEnabled) {
            return;
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        modulesStatus.setMode(OperationMode.VPN_MODE);
        preferenceRepository.get()
                .setStringPreference(OPERATION_MODE, OperationMode.VPN_MODE.toString());
        logi("VPN mode enabled");


        if (modulesStatus.getDnsCryptState() == RUNNING
                || modulesStatus.getTorState() == RUNNING
                || modulesStatus.getFirewallState() == STARTING
                || modulesStatus.getFirewallState() == RUNNING) {
            defaultPreferences.get().edit().putBoolean(VPN_SERVICE_ENABLED, true).apply();
            ServiceVPNHelper.start(
                    "Root exec service start VPN service after root console failed",
                    this
            );
        }
    }

    static class RootConsoleClosedException extends IllegalStateException {

        private static final String message = "Root is not available!";

        @Nullable
        @Override
        public String getMessage() {
            return message;
        }
    }
}
