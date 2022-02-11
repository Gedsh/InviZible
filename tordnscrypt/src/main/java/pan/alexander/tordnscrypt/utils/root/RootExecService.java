package pan.alexander.tordnscrypt.utils.root;
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

import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ROOT_IS_AVAILABLE;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.BOOT_BROADCAST_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.NULL_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootServiceNotificationManager.DEFAULT_NOTIFICATION_ID;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;

public class RootExecService extends Service {
    public RootExecService() {
    }

    public static final String RUN_COMMAND = "pan.alexander.tordnscrypt.action.RUN_COMMAND";
    public static final String COMMAND_RESULT = "pan.alexander.tordnscrypt.action.COMMANDS_RESULT";
    public static final String LOG_TAG = "pan.alexander.TPDCLogs";

    private static boolean saveRootLogs = false;
    private static String autoStartDelay = "0";

    private ExecutorService executorService;
    private NotificationManager systemNotificationManager;
    private RootServiceNotificationManager serviceNotificationManager;


    @Override
    public void onCreate() {
        super.onCreate();

        systemNotificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        serviceNotificationManager = new RootServiceNotificationManager(this, systemNotificationManager);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && systemNotificationManager != null) {
            serviceNotificationManager.createNotificationChannel();
        }

        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    public static void performAction(Context context, Intent intent) {
        final PreferenceRepository preferences = App.getInstance().getDaggerComponent().getPreferenceRepository().get();

        boolean rootIsAvailable = preferences.getBoolPreference(ROOT_IS_AVAILABLE);
        saveRootLogs = preferences.getBoolPreference("swRootCommandsLog");

        if ((intent == null) || Objects.equals(intent.getAction(), "") || !rootIsAvailable) return;

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        autoStartDelay = shPref.getString("pref_fast_autostart_delay", "0");


        Log.i(LOG_TAG, "RootExecService Root = " + true + " performAction");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && systemNotificationManager != null) {
            serviceNotificationManager.sendNotification(getString(R.string.notification_temp_text), "");
        }

        if (intent == null) {
            stopService(startId);
            return START_NOT_STICKY;
        }

        final String action = intent.getAction();

        if ((action == null) || (action.isEmpty())) {

            stopService(startId);
            return START_NOT_STICKY;
        }

        if (action.equals(RUN_COMMAND)) {
            RootCommands rootCommands = (RootCommands) intent.getSerializableExtra("Commands");
            int mark = intent.getIntExtra("Mark", 0);
            ExecRunnable execCommands = new ExecRunnable(rootCommands, mark, startId);
            executorService.execute(execCommands);
        }

        return START_NOT_STICKY;
    }


    private List<String> runCommands(List<String> runCommands) {
        List<String> result = new ArrayList<>();
        List<String> error = new ArrayList<>();

        int exitCode = execWithSU(runCommands, result, error);

        if (!error.isEmpty() || exitCode != 0) {

            String exitCodeStr = "";
            if (exitCode != 0) {
                exitCodeStr = "Exit code=" + exitCode + " ";
                result.add(exitCodeStr);
            }

            String errorStr = "";
            if (!error.isEmpty()) {
                errorStr = "STDERR=" + new LinkedHashSet<>(error).toString()
                        .replace(", Try `iptables -h' or 'iptables --help' for more information.", "") + " ";
                result.addAll(error);
            }

            String resultStr = "";
            if (!result.isEmpty()) {
                resultStr = "STDOUT=" + result;
            }

            String errorMessageFinal = "Warning executing root commands.\n"
                    + exitCodeStr + errorStr + resultStr;

            Log.e(LOG_TAG, errorMessageFinal + " Commands:" + runCommands);
        }

        if (saveRootLogs) {
            String appDataDir = getApplicationContext().getApplicationInfo().dataDir;
            try {
                File f = new File(appDataDir + "/logs");

                if (!f.isDirectory()) {
                    if (f.mkdirs()) {
                        Log.i(LOG_TAG, "RootExecService log dir created");
                    } else {
                        Log.e(LOG_TAG, "RootExecService Unable to create log dir");
                    }
                }

                PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(appDataDir + "/logs/RootExec.log", true)));
                writer.println("********************");
                writer.println("COMMANDS");
                for (String command : runCommands)
                    writer.println(command);

                writer.println("--------------------");
                writer.println("RESULT");

                for (String res : result) {
                    writer.println(res);
                    Log.i(LOG_TAG, "ROOT COMMANDS RESULT " + res);
                }
                writer.println("********************");

                writer.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "RootExecService Unable to create RootExec log file " + e.getMessage());
            }
        }
        return result;

    }

    private int execWithSU(List<String> runCommands, List<String> result, List<String> error) {

        int exitCode = -1;

        try {
            exitCode = Shell.Pool.SU.run(runCommands, result, error, true);
        } catch (Shell.ShellDiedException e) {
            Log.e(LOG_TAG, "RootExecService SU shell is died " + e.getMessage());
        }

        return exitCode;
    }

    private void sendResult(List<String> commandsResult, int mark) {

        if (commandsResult == null || mark == NULL_MARK) {
            return;
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

    class ExecRunnable implements Runnable {
        RootCommands rootCommands;
        int mark;
        int startID;

        ExecRunnable(RootCommands rootCommands, int mark, int startID) {
            this.rootCommands = rootCommands;
            this.mark = mark;
            this.startID = startID;
        }

        @Override
        public void run() {
            if (mark == BOOT_BROADCAST_MARK) {
                if (!autoStartDelay.equals("0")) {
                    try {
                        Thread.sleep(Integer.parseInt(autoStartDelay));
                    } catch (InterruptedException e) {
                        Log.e(LOG_TAG, "RootExecService interrupt boot delay exception " + e.getMessage() + " " + e.getCause());
                    }
                }
            }
            sendResult(runCommands(rootCommands.getCommands()), mark);

            stopService(startID);
        }
    }

    private void stopService(int startID) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            systemNotificationManager.cancel(DEFAULT_NOTIFICATION_ID);

            try {
                stopForeground(true);
            } catch (Exception e) {
                Log.e(LOG_TAG, "RootExecService stop Service exception " + e.getMessage() + " " + e.getCause());
            }
        }

        stopSelf(startID);
    }
}
