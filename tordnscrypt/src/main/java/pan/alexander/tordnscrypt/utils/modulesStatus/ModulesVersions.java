package pan.alexander.tordnscrypt.utils.modulesStatus;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jrummyapps.android.shell.Shell;
import com.jrummyapps.android.shell.ShellNotFoundException;

import java.util.List;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.RootCommands;

import static pan.alexander.tordnscrypt.TopFragment.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.RootExecService.DNSCryptRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.I2PDRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.NullMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.TorRunFragmentMark;

public class ModulesVersionHolder {
    private static ModulesVersionHolder holder;

    private String dnsCryptVersion;
    private String torVersion;
    private String itpdVersion;

    private Shell.Console console;

    private ModulesVersionHolder() {
    }

    public ModulesVersionHolder getInstance() {
        if (holder == null) {
            synchronized (ModulesVersionHolder.class) {
                if (holder == null) {
                    holder = new ModulesVersionHolder();
                }
            }
        }
        return holder;
    }

    public void refreshModulesVersion(final Context context, final PathVars pathVars) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                openCommandShell();

                checkModulesVersions(pathVars);

                sendResult(context, dnsCryptVersion, DNSCryptRunFragmentMark);
                sendResult(context, torVersion, TorRunFragmentMark);
                sendResult(context, itpdVersion, I2PDRunFragmentMark);

                closeCommandShell();
            }
        }).start();
    }

    private void sendResult(Context context, String version, int mark){

        if (version == null) {
            return;
        }

        RootCommands comResult = new RootCommands(new String[]{version});
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult",comResult);
        intent.putExtra("Mark",mark);
        context.sendBroadcast(intent);
    }

    private void checkModulesVersions(PathVars pathVars) {
        dnsCryptVersion = console.run(pathVars.dnscryptPath + " --version").getStdout();

        torVersion = console.run(pathVars.torPath + " --version").getStdout();

        itpdVersion = console.run(pathVars.itpdPath + " --version").getStdout();
    }

    private void openCommandShell() {
        closeCommandShell();

        try {
            console = Shell.SH.getConsole();
        } catch (ShellNotFoundException e) {
            Log.e(LOG_TAG, "ModulesStatus: SH shell not found! " + e.getMessage() + e.getCause());
        }
    }

    private void closeCommandShell() {

        if (console != null && !console.isClosed()) {
            console.run("exit");
            console.close();
        }
        console = null;
    }

}
