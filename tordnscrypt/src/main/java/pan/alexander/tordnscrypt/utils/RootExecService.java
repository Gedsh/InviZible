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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
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
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;

public class RootExecService extends Service {
    public RootExecService() {
    }

    public static final String RUN_COMMAND = "pan.alexander.tordnscrypt.action.RUN_COMMAND";
    public static final String COMMAND_RESULT = "pan.alexander.tordnscrypt.action.COMMANDS_RESULT";
    public static final int DNSCryptRunFragmentMark = 100;
    public static final int TorRunFragmentMark = 200;
    public static final int I2PDRunFragmentMark = 300;
    public static final int BackupActivityMark = 400;
    public static final int HelpActivityMark = 500;
    public static final int BootBroadcastMark = 600;
    public static final int SettingsActivityMark = 700;
    public static final int NullMark = 800;
    public static final int FileOperationsMark = 900;
    public final static String LOG_TAG = "pan.alexander.TPDCLogs";
    ExecutorService executorService;
    public static boolean lockStartStop = false;
    private static boolean saveRootLogs = false;
    private static String autostartDelay = "0";

    public final String ANDROID_CHANNEL_ID = "GOTO Invisible";
    private NotificationManager notificationManager;
    public static final int DEFAULT_NOTIFICATION_ID = 102;


    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newFixedThreadPool(1);

        notificationManager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
    }

    public static void performAction(Context context, Intent intent) {

        boolean rootOK = new PrefManager(context.getApplicationContext()).getBoolPref("rootOK");
        saveRootLogs = new PrefManager(context).getBoolPref("swRootCommandsLog");

        if ((intent == null) || Objects.equals(intent.getAction(), "") || !rootOK) return;

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        autostartDelay = shPref.getString("pref_fast_autostart_delay","0");


        Log.i(LOG_TAG,"RootExecService Root = " + true + " performAction");
        lockStartStop = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if ((action == null) || (action.isEmpty())) {
                lockStartStop = false;
                stopSelf(startId);
                return START_NOT_STICKY;
            }

            if(action.equals(RUN_COMMAND)){

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    NotificationChannel notificationChannel = new NotificationChannel
                            (ANDROID_CHANNEL_ID, "NOTIFICATION_CHANNEL_INVIZIBLE", NotificationManager.IMPORTANCE_LOW);
                    notificationChannel.setDescription("Temp notification");
                    notificationChannel.enableLights(false);
                    notificationChannel.enableVibration(false);
                    notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
                    assert notificationManager != null;
                    notificationManager.createNotificationChannel(notificationChannel);

                    sendNotification(getText(R.string.notification_temp_text).toString(),
                            getString(R.string.app_name),getText(R.string.notification_temp_text).toString());

                }

                RootCommands rootCommands = (RootCommands) intent.getSerializableExtra("Commands");
                int mark = intent.getIntExtra("Mark",0);
                ExecRunnable execCommands = new ExecRunnable(rootCommands,mark,startId);
                executorService.execute(execCommands);
            }
        }
        return START_NOT_STICKY;
    }


    @SuppressWarnings("deprecation")
    private List<String> runCommands(String[] runcommands){
        List<String> result = Shell.SU.run(runcommands);

        if (saveRootLogs) {
            String appDataDir = getApplicationContext().getApplicationInfo().dataDir;
            try {
                File f = new File(appDataDir+"/logs");

                if (f.mkdirs() && f.setReadable(true) && f.setWritable(true)) {
                    Log.i(LOG_TAG, "RootExecService log dir created");
                } else {
                    Log.e(LOG_TAG, "RootExecService Unable to create and chmod log dir");
                }

                PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(appDataDir+"/logs/RootExec.log", true)));
                writer.println("********************");
                writer.println("COMMANDS");
                for (String command:runcommands)
                    writer.println(command);

                writer.println("--------------------");
                writer.println("RESULT");

                if (result != null) {
                    for (String res:result) {
                        writer.println(res);
                        Log.i(LOG_TAG,"ROOT COMMANDS RESULT "+res);
                    }
                }
                writer.println("********************");

                writer.close();
            } catch (IOException e) {
                Log.e(LOG_TAG,"RootExecService Unable to create RootExec log file "+e.getMessage());
            }
        }
        return result;

    }

    private void sendResult(List<String> commandsResult, int mark){

        if (commandsResult==null || mark==NullMark) {
            lockStartStop = false;
            return;
        }

        RootCommands comResult = new RootCommands(commandsResult.toArray(new String[0]));
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult",comResult);
        intent.putExtra("Mark",mark);
        sendBroadcast(intent);

        lockStartStop = false;
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
            if (mark==BootBroadcastMark) {
                if (!autostartDelay.equals("0")) {
                    try {
                        Thread.sleep(Integer.parseInt(autostartDelay));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            sendResult(runCommands(rootCommands.getCommands()), mark);

            /*if (mark==BootBroadcastMark) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }*/

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationManager.cancel(DEFAULT_NOTIFICATION_ID);
                stopForeground(true);
            }

            if (executorService.isShutdown()) {
                stopSelf();
            } else {
                stopSelf(startID);
            }
        }
    }

    public void sendNotification(String Ticker, String Title, String Text) {

        //These three lines makes Notification to open main activity after clicking on it
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,ANDROID_CHANNEL_ID);
        builder.setContentIntent(contentIntent)
                .setOngoing(true)   //Can't be swiped out
                .setSmallIcon(R.drawable.ic_visibility_off_black_24dp)
                //.setLargeIcon(BitmapFactory.decodeResource(res, R.drawable.large))   // большая картинка
                .setTicker(Ticker)
                .setContentTitle(Title) //Заголовок
                .setContentText(Text) // Текст уведомления
                .setWhen(System.currentTimeMillis());

        Notification notification = builder.build();

        startForeground(DEFAULT_NOTIFICATION_ID, notification);
    }
}
