package pan.alexander.tordnscrypt.modules;

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

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jrummyapps.android.shell.Shell;
import com.jrummyapps.android.shell.ShellNotFoundException;

import java.io.File;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.RootCommands;

import static pan.alexander.tordnscrypt.utils.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.RootExecService.DNSCryptRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.I2PDRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.RootExecService.TorRunFragmentMark;

public class ModulesVersions {
    private static volatile ModulesVersions holder;

    private String dnsCryptVersion;
    private String torVersion;
    private String itpdVersion;

    private Shell.Console console;

    private ModulesVersions() {
    }

    public static ModulesVersions getInstance() {
        if (holder == null) {
            synchronized (ModulesVersions.class) {
                if (holder == null) {
                    holder = new ModulesVersions();
                }
            }
        }
        return holder;
    }

    public void refreshVersions(final Context context) {
        new Thread(() -> {
            openCommandShell();

            PathVars pathVars = getPathVars(context);

            checkModulesVersions(pathVars);

            if (isBinaryFileAccessible(pathVars.dnscryptPath)) {
                sendResult(context, dnsCryptVersion, DNSCryptRunFragmentMark);
            }

            if (isBinaryFileAccessible(pathVars.torPath)) {
                sendResult(context, torVersion, TorRunFragmentMark);
            }

            if (isBinaryFileAccessible(pathVars.itpdPath)) {
                sendResult(context, itpdVersion, I2PDRunFragmentMark);
            }

            closeCommandShell();
        }).start();
    }

    private PathVars getPathVars(Context context) {
        return new PathVars(context);
    }

    private boolean isBinaryFileAccessible(String path) {
        File file = new File(path);
        return file.isFile() && file.canExecute();
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
        dnsCryptVersion = console.run(
                "echo 'DNSCrypt_version'",
                pathVars.dnscryptPath + " --version")
                .getStdout();

        torVersion = console.run(
                "echo 'Tor_version'",
                pathVars.torPath + " --version")
                .getStdout();

        itpdVersion = console.run(
                "echo 'ITPD_version'",
                pathVars.itpdPath + " --version")
                .getStdout();
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
