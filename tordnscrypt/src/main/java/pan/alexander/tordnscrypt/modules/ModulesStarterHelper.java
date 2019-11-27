package pan.alexander.tordnscrypt.modulesManager;

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

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import java.util.List;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.fileOperations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.RootExecService.DNSCryptRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.I2PDRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.RootExecService.TorRunFragmentMark;
import static pan.alexander.tordnscrypt.modulesManager.ModulesService.DNSCRYPT_KEYWORD;
import static pan.alexander.tordnscrypt.modulesManager.ModulesService.ITPD_KEYWORD;
import static pan.alexander.tordnscrypt.modulesManager.ModulesService.TOR_KEYWORD;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

class ModulesStarterHelper {

    private final Context context;
    private final Handler handler;
    private final PathVars pathVars;

    private ModulesStatus modulesStatus;

    ModulesStarterHelper(Context context, Handler handler, PathVars pathVars) {
        this.context = context;
        this.handler = handler;
        this.pathVars = pathVars;
        this.modulesStatus = ModulesStatus.getInstance();
    }

    Runnable getDNSCryptStarterRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                //new experiment
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

                String dnsCmdString;
                final CommandResult shellResult;
                if (modulesStatus.isUseModulesWithRoot()) {

                    dnsCmdString = pathVars.busyboxPath + "nohup " + pathVars.dnscryptPath + " --config " + pathVars.appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 &";
                    String waitString = pathVars.busyboxPath + "sleep 3";
                    String checkIfModuleRunning = pathVars.busyboxPath + "pgrep -l /dnscrypt-proxy";

                    shellResult = Shell.SU.run(dnsCmdString, waitString, checkIfModuleRunning);

                    if (shellResult.getStdout().contains(pathVars.dnscryptPath)) {
                        sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                    } else {
                        sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                    }

                } else {
                    dnsCmdString = pathVars.dnscryptPath + " --config " + pathVars.appDataDir + "/app_data/dnscrypt-proxy/dnscrypt-proxy.toml";
                    shellResult = Shell.SH.run(dnsCmdString);
                }

                if (!shellResult.isSuccessful()) {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");

                    Log.e(LOG_TAG, "Error DNSCrypt: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                    if (modulesStatus.getDnsCryptState() == STOPPING || modulesStatus.getDnsCryptState() == STOPPED) {
                        return;
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "DNSCrypt Module Fault: " + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr() + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };
    }

    Runnable getTorStarterRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                //new experiment
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

                String torCmdString;
                final CommandResult shellResult;
                if (modulesStatus.isUseModulesWithRoot()) {

                    correctTorConfRunAsDaemon(context, pathVars.appDataDir, true);

                    torCmdString = pathVars.torPath + " -f " + pathVars.appDataDir + "/app_data/tor/tor.conf";
                    String waitString = pathVars.busyboxPath + "sleep 3";
                    String checkIfModuleRunning = pathVars.busyboxPath + "pgrep -l /tor";

                    shellResult = Shell.SU.run(torCmdString, waitString, checkIfModuleRunning);

                    if (shellResult.getStdout().contains(pathVars.torPath)) {
                        sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                    } else {
                        sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                    }

                } else {
                    correctTorConfRunAsDaemon(context, pathVars.appDataDir, false);
                    torCmdString = pathVars.torPath + " -f " + pathVars.appDataDir + "/app_data/tor/tor.conf";
                    shellResult = Shell.SH.run(torCmdString);
                }

                if (!shellResult.isSuccessful()) {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");

                    Log.e(LOG_TAG, "Error Tor: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                    if (modulesStatus.getTorState() == STOPPING || modulesStatus.getTorState() == STOPPED) {
                        return;
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Tor Module Fault: " + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr() + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };
    }

    Runnable getITPDStarterRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                //new experiment
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

                String itpdCmdString;

                final CommandResult shellResult;
                if (modulesStatus.isUseModulesWithRoot()) {
                    correctITPDConfRunAsDaemon(context, pathVars.appDataDir, true);

                    Shell.SU.run(pathVars.busyboxPath + "mkdir -p " + pathVars.appDataDir + "/i2pd_data",
                            "cd " + pathVars.appDataDir + "/app_data/i2pd",
                            pathVars.busyboxPath + "cp -R certificates " + pathVars.appDataDir + "/i2pd_data");

                    itpdCmdString = pathVars.itpdPath + " --conf " + pathVars.appDataDir + "/app_data/i2pd/i2pd.conf --datadir " + pathVars.appDataDir + "/i2pd_data &";
                    String waitString = pathVars.busyboxPath + "sleep 3";
                    String checkIfModuleRunning = pathVars.busyboxPath + "pgrep -l /i2pd";

                    shellResult = Shell.SU.run(itpdCmdString, waitString, checkIfModuleRunning);

                    if (shellResult.getStdout().contains(pathVars.itpdPath)) {
                        sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                    } else {
                        sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                    }

                } else {
                    correctITPDConfRunAsDaemon(context, pathVars.appDataDir, false);
                    itpdCmdString = pathVars.itpdPath + " --conf " + pathVars.appDataDir + "/app_data/i2pd/i2pd.conf --datadir " + pathVars.appDataDir + "/i2pd_data";
                    shellResult = Shell.SH.run(itpdCmdString);
                }

                if (!shellResult.isSuccessful()) {

                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");

                    Log.e(LOG_TAG, "Error ITPD: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());

                    if (modulesStatus.getItpdState() == STOPPING || modulesStatus.getItpdState()  == STOPPED) {
                        return;
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Purple I2P Module Fault: " + shellResult.exitCode + "\n\n ERR = " + shellResult.getStderr() + "\n\n OUT = " + shellResult.getStdout(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };
    }

    private void correctTorConfRunAsDaemon(Context context, String appDataDir, boolean runAsDaemon) {
        String path = appDataDir + "/app_data/tor/tor.conf";
        List<String> lines = FileOperations.readTextFileSynchronous(context, path);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("RunAsDaemon")) {
                if (runAsDaemon && lines.get(i).contains("0")) {
                    lines.set(i, "RunAsDaemon 1");
                    FileOperations.writeTextFileSynchronous(context, path, lines);
                } else if (!runAsDaemon && lines.get(i).contains("1")) {
                    lines.set(i, "RunAsDaemon 0");
                    FileOperations.writeTextFileSynchronous(context, path, lines);
                }
                return;
            }
        }
    }

    private void correctITPDConfRunAsDaemon(Context context, String appDataDir, boolean runAsDaemon) {
        String path = appDataDir + "/app_data/i2pd/i2pd.conf";
        List<String> lines = FileOperations.readTextFileSynchronous(context, path);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("daemon")) {
                if (runAsDaemon && lines.get(i).contains("false")) {
                    lines.set(i, "daemon = true");
                    FileOperations.writeTextFileSynchronous(context, path, lines);
                } else if (!runAsDaemon && lines.get(i).contains("true")) {
                    lines.set(i, "daemon = false");
                    FileOperations.writeTextFileSynchronous(context, path, lines);
                }
                return;
            }
        }
    }

    private void sendResultIntent(int moduleMark, String moduleKeyWord, String binaryPath) {
        RootCommands comResult = new RootCommands(new String[]{moduleKeyWord, binaryPath});
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult",comResult);
        intent.putExtra("Mark" ,moduleMark);
        context.sendBroadcast(intent);
    }
}
