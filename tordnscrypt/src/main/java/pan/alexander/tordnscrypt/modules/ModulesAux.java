package pan.alexander.tordnscrypt.modulesManager;

import android.content.Context;
import android.content.Intent;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;

public class ModulesAux {
    public static void setModulesContextAndUID(Context context, PathVars pathVars, boolean useModulesWithRoot) {

        boolean dnsCryptRunning = new PrefManager(context).getBoolPref("DNSCrypt Running");
        boolean torRunning = new PrefManager(context).getBoolPref("Tor Running");
        boolean itpdRunning = new PrefManager(context).getBoolPref("I2PD Running");

        if (dnsCryptRunning) {
            new PrefManager(context).setBoolPref("DNSCrypt Running", false);
            ModulesKiller.stopDNSCrypt(context);
        }

        if (torRunning) {
            new PrefManager(context).setBoolPref("Tor Running", false);
            ModulesKiller.stopTor(context);
        }

        if (itpdRunning) {
            new PrefManager(context).setBoolPref("I2PD Running", false);
            ModulesKiller.stopITPD(context);
        }

        String appUID = new PrefManager(context).getStrPref("appUID");
        String[] commands;
        if (useModulesWithRoot) {
            commands = new String[]{
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/app_data/dnscrypt-proxy",
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/tor_data",
                    pathVars.busyboxPath + "chown -R 0.0 " + pathVars.appDataDir + "/i2pd_data"
            };
        } else {
            commands = new String[]{
                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/app_data/dnscrypt-proxy",
                    "restorecon -R " + pathVars.appDataDir + "/app_data/dnscrypt-proxy",

                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/tor_data",
                    "restorecon -R " + pathVars.appDataDir + "/tor_data",

                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/i2pd_data",
                    "restorecon -R " + pathVars.appDataDir + "/i2pd_data",

                    pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + pathVars.appDataDir + "/logs",
                    "restorecon -R " + pathVars.appDataDir + "/logs"
            };
        }

        RootCommands rootCommands = new RootCommands(commands);
        Intent intent = new Intent(context, RootExecService.class);
        intent.setAction(RootExecService.RUN_COMMAND);
        intent.putExtra("Commands", rootCommands);
        intent.putExtra("Mark", RootExecService.NullMark);
        RootExecService.performAction(context, intent);
    }
}
