package pan.alexander.tordnscrypt.utils.modulesStarter;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.jrummyapps.android.shell.CommandResult;
import com.jrummyapps.android.shell.Shell;

import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesStatus;

import static pan.alexander.tordnscrypt.TopFragment.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.RootExecService.COMMAND_RESULT;
import static pan.alexander.tordnscrypt.utils.RootExecService.DNSCryptRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.I2PDRunFragmentMark;
import static pan.alexander.tordnscrypt.utils.RootExecService.TorRunFragmentMark;

class ModulesKiller {
    private final String DNSCRYPT_KEYWORD = "checkDNSRunning";
    private final String TOR_KEYWORD = "checkTrRunning";
    private final String ITPD_KEYWORD = "checkITPDRunning";

    private final Context context;
    private final PathVars pathVars;

    private Thread dnsCryptThread;
    private Thread torThread;
    private Thread itpdThread;

    private ModulesStatus modulesStatus;

    ModulesKiller(Context context, PathVars pathVars) {
        this.context = context;
        this.pathVars = pathVars;
        modulesStatus = ModulesStatus.getInstance();
    }

    Runnable killDNSCrypt = new Runnable() {
        @Override
        public void run() {
            int attempts = 0;
            boolean result = false;

            while (attempts < 3 && !result) {
                result = killWithKillAll(pathVars.dnscryptPath, dnsCryptThread, modulesStatus.isUseModulesWithRoot());
                attempts++;
            }

            if (!result) {
                if (modulesStatus.isRootAvailable()) {
                    Log.w(LOG_TAG, "ModulesKiller cannot stop DNSCrypt. Stop with root method!");
                    killWithKillAll(pathVars.dnscryptPath, dnsCryptThread, true);
                } else {
                    Log.w(LOG_TAG, "ModulesKiller cannot stop DNSCrypt. Stop with interrupt thread!");
                    if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
                        dnsCryptThread.interrupt();
                    }
                }

                makeDelay(3);

                if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, pathVars.dnscryptPath);
                    Log.e(LOG_TAG, "ModulesKiller cannot stop DNSCrypt!");
                } else {
                    sendResultIntent(DNSCryptRunFragmentMark, DNSCRYPT_KEYWORD, "");
                }
            }
        }
    };

    Runnable killTor = new Runnable() {
        @Override
        public void run() {
            int attempts = 0;
            boolean result = false;

            while (attempts < 3 && !result) {
                result = killWithKillAll(pathVars.torPath, torThread, modulesStatus.isUseModulesWithRoot());
                attempts++;
            }

            if (!result) {
                if (modulesStatus.isRootAvailable()) {
                    Log.w(LOG_TAG, "ModulesKiller cannot stop Tor. Stop with root method!");
                    killWithKillAll(pathVars.torPath, torThread, true);
                } else {
                    Log.w(LOG_TAG, "ModulesKiller cannot stop Tor. Stop with interrupt thread!");
                    if (torThread != null && torThread.isAlive()) {
                        torThread.interrupt();
                    }
                }

                makeDelay(3);

                if (torThread != null && torThread.isAlive()) {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, pathVars.torPath);
                    Log.e(LOG_TAG, "ModulesKiller cannot stop Tor!");
                } else {
                    sendResultIntent(TorRunFragmentMark, TOR_KEYWORD, "");
                }
            }
        }
    };

    Runnable killITPD = new Runnable() {
        @Override
        public void run() {
            int attempts = 0;
            boolean result = false;

            while (attempts < 3 && !result) {
                result = killWithKillAll(pathVars.itpdPath, itpdThread, modulesStatus.isUseModulesWithRoot());
                attempts++;
            }

            if (!result) {
                if (modulesStatus.isRootAvailable()) {
                    Log.w(LOG_TAG, "ModulesKiller cannot stop I2P. Stop with root method!");
                    killWithKillAll(pathVars.itpdPath, itpdThread, true);
                } else {
                    Log.w(LOG_TAG, "ModulesKiller cannot stop I2P. Stop with interrupt thread!");
                    if (itpdThread != null && itpdThread.isAlive()) {
                        itpdThread.interrupt();
                    }
                }

                makeDelay(3);

                if (itpdThread != null && itpdThread.isAlive()) {
                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, pathVars.itpdPath);
                    Log.e(LOG_TAG, "ModulesKiller cannot stop I2P!");
                } else {
                    sendResultIntent(I2PDRunFragmentMark, ITPD_KEYWORD, "");
                }
            }
        }
    };

    private boolean killWithKillAll(String binaryPath, Thread thread, boolean killWithRoot) {
        boolean result = false;
        if (killWithRoot) {
            String killString = pathVars.busyboxPath + "killall " + binaryPath;
            String checkString = pathVars.busyboxPath + "pgrep -l " + binaryPath;
            CommandResult shellResult = Shell.SU.run(killString, checkString);
            result = !shellResult.getStdout().contains(binaryPath);
        } else if (thread != null){
            String killString = pathVars.busyboxPath + "killall " + binaryPath;
            Shell.SH.run(killString);
            makeDelay(1);
            result = !thread.isAlive();
        }
        return result;
    }

    private void sendResultIntent(int moduleMark, String moduleKeyWord, String binaryPath) {
        RootCommands comResult = new RootCommands(new String[]{moduleKeyWord, binaryPath});
        Intent intent = new Intent(COMMAND_RESULT);
        intent.putExtra("CommandsResult",comResult);
        intent.putExtra("Mark" ,moduleMark);
        context.sendBroadcast(intent);
    }

    private static void makeDelay(int sec) {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException ignored) {}
    }

    void setDnsCryptThread(Thread dnsCryptThread) {
        this.dnsCryptThread = dnsCryptThread;
    }

    void setTorThread(Thread torThread) {
        this.torThread = torThread;
    }

    void setItpdThread(Thread itpdThread) {
        this.itpdThread = itpdThread;
    }
}
