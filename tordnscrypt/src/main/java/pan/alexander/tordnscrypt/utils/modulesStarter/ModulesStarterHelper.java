package pan.alexander.tordnscrypt.utils.modulesStarter;

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
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import java.util.List;
import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.fileOperations.FileOperations;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class ModulesStarterHelper {

    private ModulesStarterHelper() {}

    static Runnable getDNSCryptStarterRunnable(final Context context, final PathVars pathVars, final Handler handler, final boolean useModulesWithRoot) {
        return new Runnable() {
            @Override
            public void run() {
                //new experiment
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                String dnsCmdString;
                final CommandResult shellResult;
                if (useModulesWithRoot) {
                    dnsCmdString = pathVars.busyboxPath+ "nohup " + pathVars.dnscryptPath+" --config "+pathVars.appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml >/dev/null 2>&1 &";
                    shellResult = Shell.SU.run(dnsCmdString);
                } else {
                    dnsCmdString = pathVars.dnscryptPath+" --config "+pathVars.appDataDir+"/app_data/dnscrypt-proxy/dnscrypt-proxy.toml";
                    shellResult = Shell.run(dnsCmdString);
                }

                if (!shellResult.isSuccessful()) {
                    Log.e(LOG_TAG,"Error DNSCrypt: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context,"Error DNSCrypt: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };
    }

    static Runnable getTorStarterRunnable(final Context context, final PathVars pathVars, final Handler handler, final boolean useModulesWithRoot) {
        return new Runnable() {
            @Override
            public void run() {
                //new experiment
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                String torCmdString;
                final CommandResult shellResult;
                if (useModulesWithRoot) {
                    correctTorConfRunAsDaemon(context, pathVars.appDataDir, true);
                    torCmdString = pathVars.torPath + " -f " + pathVars.appDataDir + "/app_data/tor/tor.conf";
                    shellResult = Shell.SU.run(torCmdString);
                } else {
                    correctTorConfRunAsDaemon(context, pathVars.appDataDir, false);
                    torCmdString = pathVars.torPath+" -f "+pathVars.appDataDir+"/app_data/tor/tor.conf";
                    shellResult = Shell.run(torCmdString);
                }

                if (!shellResult.isSuccessful()) {
                    Log.e(LOG_TAG,"Error Tor: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context,"Error Tor: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };
    }

    static Runnable getITPDStarterRunnable(final Context context, final PathVars pathVars, final Handler handler, final boolean useModulesWithRoot) {
        return new Runnable() {
            @Override
            public void run() {
                //new experiment
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                String itpdCmdString;

                final CommandResult shellResult;
                if (useModulesWithRoot) {
                    correctITPDConfRunAsDaemon(context, pathVars.appDataDir, true);
                    itpdCmdString = pathVars.itpdPath + " --conf " + pathVars.appDataDir + "/app_data/i2pd/i2pd.conf --datadir " + pathVars.appDataDir + "/i2pd_data &";
                    shellResult = Shell.SU.run(itpdCmdString);
                } else {
                    correctITPDConfRunAsDaemon(context, pathVars.appDataDir, false);
                    itpdCmdString = pathVars.itpdPath+" --conf "+pathVars.appDataDir+"/app_data/i2pd/i2pd.conf --datadir "+pathVars.appDataDir+"/i2pd_data";
                    shellResult = Shell.run(itpdCmdString);
                }

                if (!shellResult.isSuccessful()) {
                    Log.e(LOG_TAG,"Error ITPD: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context,"Error ITPD: " + shellResult.exitCode + " ERR=" + shellResult.getStderr() + " OUT=" + shellResult.getStdout(),Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        };
    }

    private static void correctTorConfRunAsDaemon(Context context, String appDataDir, boolean runAsDaemon) {
        String path = appDataDir+"/app_data/tor/tor.conf";
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

    private static void correctITPDConfRunAsDaemon(Context context, String appDataDir, boolean runAsDaemon) {
        String path = appDataDir+"/app_data/i2pd/i2pd.conf";
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
}
