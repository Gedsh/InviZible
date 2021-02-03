package pan.alexander.tordnscrypt.utils;
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
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
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

public class RootExecService extends Service {
    public RootExecService() {
    }

    public static final int DNSCryptRunFragmentMark = 100;
    public static final int TorRunFragmentMark = 200;
    public static final int I2PDRunFragmentMark = 300;
    public static final int HelpActivityMark = 400;
    public static final int BootBroadcastMark = 500;
    public static final int NullMark = 600;
    public static final int FileOperationsMark = 700;
    public static final int InstallerMark = 800;
    public static final int TopFragmentMark = 900;
    public static final int DEFAULT_NOTIFICATION_ID = 102;
    public static final String RUN_COMMAND = "pan.alexander.tordnscrypt.action.RUN_COMMAND";
    public static final String COMMAND_RESULT = "pan.alexander.tordnscrypt.action.COMMANDS_RESULT";
    public static final String LOG_TAG = "pan.alexander.TPDCLogs";
    public static final String ROOT_CHANNEL_ID = "ROOT_COMMANDS_INVIZIBLE";

    private static boolean saveRootLogs = false;
    private static String autoStartDelay = "0";
    private static boolean showToastWithCommandsResultError;

    private ExecutorService executorService;
    private NotificationManager notificationManager;
    private Handler handler;


    @Override
    public void onCreate() {
        super.onCreate();

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            createNotificationChannel();
        }

        executorService = Executors.newSingleThreadExecutor();

        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            handler = new Handler(looper);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
    }

    public static void performAction(Context context, Intent intent) {

        boolean rootIsAvailable = new PrefManager(context.getApplicationContext()).getBoolPref("rootIsAvailable");
        saveRootLogs = new PrefManager(context).getBoolPref("swRootCommandsLog");

        if ((intent == null) || Objects.equals(intent.getAction(), "") || !rootIsAvailable) return;

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        autoStartDelay = shPref.getString("pref_fast_autostart_delay", "0");
        showToastWithCommandsResultError = shPref.getBoolean("pref_common_show_help", false);


        Log.i(LOG_TAG, "RootExecService Root = " + true + " performAction");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            sendNotification(getString(R.string.notification_temp_text), "");
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

        if (!error.isEmpty() || exitCode != 0)  {

            String exitCodeStr = exitCode == 0 ? "" : "Exit code=" + exitCode + " ";
            String errorStr = error.isEmpty() ? "" : "STDERR="
                    + new LinkedHashSet<>(error).toString()
                    .replace(", Try `iptables -h' or 'iptables --help' for more information.", "") + " ";
            String resultStr = result.isEmpty() ? "" : "STDOUT=" + result;

            String errorMessageFinal = "Warning executing root commands.\n"
                    + exitCodeStr + errorStr + resultStr;

            Log.e(LOG_TAG, errorMessageFinal + " Commands:" + runCommands);

            if (handler != null) {
                if (errorStr.contains("unknown option \"-w\"")) {
                    handler.post(() -> {
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(RootExecService.this);
                        sharedPreferences.edit().putString("pref_common_use_iptables", "2").apply();
                        handler.postDelayed(() -> ModulesStatus.getInstance().setIptablesRulesUpdateRequested(this, true), 1000);
                    });
                } else if (errorStr.contains(" -w ") || exitCode == 4) {
                    handler.postDelayed(() -> ModulesStatus.getInstance().setIptablesRulesUpdateRequested(this, true), 5000);
                }

                if (showToastWithCommandsResultError) {
                    handler.post(() -> Toast.makeText(RootExecService.this, errorMessageFinal, Toast.LENGTH_LONG).show());
                }
            }
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

        if (commandsResult == null || mark == NullMark) {
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
            if (mark == BootBroadcastMark) {
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel notificationChannel = new NotificationChannel
                (ROOT_CHANNEL_ID, getString(R.string.notification_channel_root), NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription("");
        notificationChannel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        notificationChannel.enableLights(false);
        notificationChannel.enableVibration(false);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(notificationChannel);

        sendNotification(getString(R.string.notification_temp_text), "");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void sendNotification(String Title, String Text) {

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int iconResource = getResources().getIdentifier("ic_service_notification", "drawable", getPackageName());
        if (iconResource == 0) {
            iconResource = android.R.drawable.ic_menu_view;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, ROOT_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(false)   //Can be swiped out
                .setSmallIcon(iconResource)
                //.setLargeIcon(BitmapFactory.decodeResource(res, R.drawable.large))   // большая картинка
                //.setTicker(Ticker)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                //.setWhen(System.currentTimeMillis())
                //new experiment
                .setPriority(Notification.PRIORITY_MIN)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setChannelId(ROOT_CHANNEL_ID)
                .setProgress(100, 100, true);

        Notification notification = builder.build();

        startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }

    private void stopService(int startID) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            notificationManager.cancel(DEFAULT_NOTIFICATION_ID);

            try {
                stopForeground(true);
            } catch (Exception e) {
                Log.e(LOG_TAG, "RootExecService stop Service exception " + e.getMessage() + " " + e.getCause());
            }
        }

        stopSelf(startID);
    }
}
