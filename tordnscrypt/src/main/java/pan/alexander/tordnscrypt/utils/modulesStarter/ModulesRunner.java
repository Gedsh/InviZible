package pan.alexander.tordnscrypt.utils.modulesStarter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v7.preference.PreferenceManager;

public class ModulesStarterHelper {

    private ModulesStarterHelper() {

    }

    private static void runDNSCrypt(Context context) {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showNotification = shPref.getBoolean("swShowNotification", true);
        Intent intent = new Intent(context, ModulesStarterService.class);
        intent.setAction(ModulesStarterService.actionStartDnsCrypt);
        intent.putExtra("showNotification", showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void runTor(Context context) {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showNotification = shPref.getBoolean("swShowNotification", true);
        Intent intent = new Intent(context, ModulesStarterService.class);
        intent.setAction(ModulesStarterService.actionStartTor);
        intent.putExtra("showNotification", showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void runITPD(Context context) {
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean showNotification = shPref.getBoolean("swShowNotification", true);
        Intent intent = new Intent(context, ModulesStarterService.class);
        intent.setAction(ModulesStarterService.actionStartITPD);
        intent.putExtra("showNotification", showNotification);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
