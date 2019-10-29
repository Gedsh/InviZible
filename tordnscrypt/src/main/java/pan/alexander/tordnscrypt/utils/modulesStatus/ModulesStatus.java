package pan.alexander.tordnscrypt.utils.modulesStatus;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

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

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;
import com.jrummyapps.android.shell.ShellNotFoundException;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;

import static pan.alexander.tordnscrypt.TopFragment.LOG_TAG;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public final class ModulesStatus {

    private volatile ModuleState dnsCryptState = STOPPED;
    private volatile ModuleState torState = STOPPED;
    private volatile ModuleState itpdState = STOPPED;
    private volatile boolean fresh = false;

    private ModuleState dnsCryptStateLocal = STOPPED;
    private ModuleState torStateLocal = STOPPED;
    private ModuleState itpdStateLocal = STOPPED;

    private Shell.Console console;
    private boolean shellIsOpened;

    private PathVars pathVars;

    private static volatile ModulesStatus modulesStatus;

    private ModulesStatus() {
    }

    public static ModulesStatus getInstance() {
        if (modulesStatus == null) {
            synchronized (ModulesStatus.class) {
                if (modulesStatus == null) {
                    modulesStatus = new ModulesStatus();
                }
            }
        }
        return modulesStatus;
    }

    public void refreshViews(Context context) {
        Intent intent = new Intent(TOP_BROADCAST);
        context.sendBroadcast(intent);
    }

    void refresh(final PathVars pathVars) {
        if (console == null) {
            try {
                console = Shell.SH.getConsole();
                shellIsOpened = true;
            } catch (ShellNotFoundException e) {
                shellIsOpened = false;
                Log.e(LOG_TAG, "ModulesStatus: SH shell not found! " + e.getMessage() + e.getCause());
            }
        }

        if (shellIsOpened) {
            dnsCryptStateLocal = STOPPED;
            torStateLocal = STOPPED;
            itpdStateLocal = STOPPED;

            this.pathVars = pathVars;

            String busyBoxPath = pathVars.busyboxPath;
            String toolBoxPath = "toolbox ";

            String psCmdStringBusyBox = busyBoxPath + "ps";
            String psCmdStringToolBox = toolBoxPath + "ps";


            CommandResult shellResult = console.run(psCmdStringToolBox);
            if (!shellResult.isSuccessful()) {
                Log.w(LOG_TAG,"Error " + psCmdStringToolBox + " " + shellResult.exitCode
                        + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                shellResult = Shell.run(psCmdStringBusyBox);
            }

            String stdOut = shellResult.getStdout();

            checkModulesStatus(stdOut);

            if (dnsCryptState == RUNNING || dnsCryptState == STOPPED) {
                dnsCryptState = dnsCryptStateLocal;
            }

            if (torState == RUNNING || torState == STOPPED) {
                torState = torStateLocal;
            }

            if (itpdState == RUNNING || itpdState == STOPPED) {
                itpdState = itpdStateLocal;
            }

            fresh = true;

            Log.i(LOG_TAG, "DNSCrypt is " + dnsCryptState + " Tor is " + torState + " I2P is " + itpdState);
        }
    }

    void closeSHShell() {
        fresh = false;

        console.run("exit");
        console.close();

        shellIsOpened = false;
        console = null;
    }

    private void checkModulesStatus(String stdOut) {
        if (stdOut.contains(pathVars.dnscryptPath)) {
            dnsCryptStateLocal = RUNNING;
        }

        if (stdOut.contains(pathVars.torPath)) {
            torStateLocal = RUNNING;
        }

        if (stdOut.contains(pathVars.itpdPath)) {
            itpdStateLocal = RUNNING;
        }
    }

    public ModuleState getDnsCryptState() {
        return dnsCryptState;
    }

    public ModuleState getTorState() {
        return torState;
    }

    public ModuleState getItpdState() {
        return itpdState;
    }

    public boolean isFresh() {
        return fresh;
    }

    public void setDnsCryptState(ModuleState dnsCryptState) {
        this.dnsCryptState = dnsCryptState;
    }

    public void setTorState(ModuleState torState) {
        this.torState = torState;
    }

    public void setItpdState(ModuleState itpdState) {
        this.itpdState = itpdState;
    }
}
