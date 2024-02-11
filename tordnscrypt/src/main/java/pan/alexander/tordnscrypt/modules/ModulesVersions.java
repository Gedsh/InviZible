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

package pan.alexander.tordnscrypt.modules;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.jrummyapps.android.shell.Shell;
import com.jrummyapps.android.shell.ShellNotFoundException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.root.RootCommands;

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.DNSCRYPT_RUN_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.I2PD_RUN_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootCommandsMark.TOR_RUN_FRAGMENT_MARK;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.COMMAND_RESULT;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ModulesVersions {
    private final CachedExecutor cachedExecutor;

    private String dnsCryptVersion = "";
    private String torVersion = "";
    private String itpdVersion = "";

    private Shell.Console console;

    @Inject
    ModulesVersions(CachedExecutor cachedExecutor) {
        this.cachedExecutor = cachedExecutor;
    }

    public void refreshVersions(final Context context) {

        cachedExecutor.submit(() -> {
            //openCommandShell();

            PathVars pathVars = App.getInstance().getDaggerComponent().getPathVars().get();

            //checkModulesVersions(pathVars);
            checkModulesVersionsModern(context, pathVars);

            if (isBinaryFileAccessible(pathVars.getDNSCryptPath()) && !dnsCryptVersion.isEmpty()) {
                sendResult(context, dnsCryptVersion, DNSCRYPT_RUN_FRAGMENT_MARK);
            }

            if (isBinaryFileAccessible(pathVars.getTorPath()) && !torVersion.isEmpty()) {
                sendResult(context, torVersion, TOR_RUN_FRAGMENT_MARK);
            }

            if (isBinaryFileAccessible(pathVars.getITPDPath()) && !itpdVersion.isEmpty()) {
                sendResult(context, itpdVersion, I2PD_RUN_FRAGMENT_MARK);
            }

            //closeCommandShell();
        });
    }

    private boolean isBinaryFileAccessible(String path) {
        File file = new File(path);
        return file.isFile() && file.canExecute();
    }

    private void sendResult(Context context, String version, int mark){

        if (version == null) {
            return;
        }

        RootCommands comResult = new RootCommands(new ArrayList<>(Collections.singletonList(version)));
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult",comResult);
        intent.putExtra("Mark",mark);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void checkModulesVersions(PathVars pathVars) {
        if (console == null || console.isClosed()) {
            return;
        }

        dnsCryptVersion = console.run(
                "echo 'DNSCrypt_version'",
                pathVars.getDNSCryptPath() + " --version")
                .getStdout();

        torVersion = console.run(
                "echo 'Tor_version'",
                pathVars.getTorPath() + " --version")
                .getStdout();

        itpdVersion = console.run(
                "echo 'ITPD_version'",
                pathVars.getITPDPath() + " --version")
                .getStdout();
    }

    private void checkModulesVersionsModern(Context context, PathVars pathVars) {

        List<String> dnsCryptOutput = new ProcessStarter(context.getApplicationInfo().nativeLibraryDir)
                .startProcess(pathVars.getDNSCryptPath() + " --version").stdout;
        if (!dnsCryptOutput.isEmpty()) {
            dnsCryptVersion = "DNSCrypt_version " + dnsCryptOutput.get(0);
        }

        List<String> torOutput = new ProcessStarter(context.getApplicationInfo().nativeLibraryDir)
                .startProcess(pathVars.getTorPath() + " --version").stdout;
        if (!torOutput.isEmpty()) {
            torVersion = "Tor_version " + torOutput.get(0);
        }

        List<String> itpdOutput = new ProcessStarter(context.getApplicationInfo().nativeLibraryDir)
                .startProcess(pathVars.getITPDPath() + " --version").stdout;
        if (!itpdOutput.isEmpty()) {
            itpdVersion = "ITPD_version " + itpdOutput.get(0);
        }
    }

    private void openCommandShell() {
        closeCommandShell();

        try {
            console = Shell.SH.getConsole();
        } catch (ShellNotFoundException e) {
            loge("ModulesStatus: SH shell not found!", e);
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
