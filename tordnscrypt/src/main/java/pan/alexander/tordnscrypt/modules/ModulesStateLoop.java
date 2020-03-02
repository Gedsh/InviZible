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

import android.os.Build;
import android.util.Log;

import java.util.TimerTask;

import pan.alexander.tordnscrypt.iptables.IptablesRules;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class ModulesStateLoop extends TimerTask {
    //Depends on timer, currently 10 sec
    private static final int STOP_COUNTER_DELAY = 10;

    //Delay in sec before service can stop
    private static int stopCounter = STOP_COUNTER_DELAY;

    private final ModulesStatus modulesStatus;
    private final ModulesService modulesService;
    private final IptablesRules iptablesRules;

    private final ContextUIDUpdater contextUIDUpdater;

    private static Thread dnsCryptThread;
    private static Thread torThread;
    private static Thread itpdThread;

    private ModuleState savedDNSCryptState;
    private ModuleState savedTorState;
    private ModuleState savedItpdState;

    ModulesStateLoop(ModulesService modulesService) {
        //Delay in sec before service can stop
        stopCounter = STOP_COUNTER_DELAY;

        this.modulesService = modulesService;

        modulesStatus = ModulesStatus.getInstance();

        iptablesRules = new ModulesIptablesRules(modulesService);

        contextUIDUpdater = new ContextUIDUpdater(modulesService);

        restoreModulesSavedState();
    }

    @Override
    public void run() {

        if (modulesStatus == null) {
            return;
        }

        ModuleState dnsCryptState = modulesStatus.getDnsCryptState();
        ModuleState torState = modulesStatus.getTorState();
        ModuleState itpdState = modulesStatus.getItpdState();

        OperationMode operationMode = modulesStatus.getMode();

        boolean rootIsAvailable = modulesStatus.isRootAvailable();
        boolean useModulesWithRoot = modulesStatus.isUseModulesWithRoot();
        boolean contextUIDUpdateRequested = modulesStatus.isContextUIDUpdateRequested();


        if (!useModulesWithRoot) {
            updateModulesState(dnsCryptState, torState, itpdState);
        }

        updateIptablesRules(dnsCryptState, torState, itpdState, operationMode, rootIsAvailable, useModulesWithRoot);

        if (rootIsAvailable && contextUIDUpdateRequested) {
            updateContextUID(dnsCryptState, torState, itpdState);
        }

        if (stopCounter <= 0) {
            Log.i(LOG_TAG, "ModulesStateLoop stopCounter is zero. Stop service.");
            modulesStatus.setContextUIDUpdateRequested(false);
            safeStopModulesService();
        }
    }

    private void updateModulesState(ModuleState dnsCryptState, ModuleState torState, ModuleState itpdState) {
        if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
            if (dnsCryptState == STOPPED) {
                modulesStatus.setDnsCryptState(ModuleState.RUNNING);
                stopCounter = STOP_COUNTER_DELAY;
            }
        } else {
            if (dnsCryptState == RUNNING) {
                modulesStatus.setDnsCryptState(STOPPED);
            }
        }

        if (torThread != null && torThread.isAlive()) {
            if (torState == STOPPED) {
                modulesStatus.setTorState(ModuleState.RUNNING);
                stopCounter = STOP_COUNTER_DELAY;
            }
        } else {
            if (torState == RUNNING) {
                modulesStatus.setTorState(STOPPED);
            }
        }

        if (itpdThread != null && itpdThread.isAlive()) {
            if (itpdState == STOPPED) {
                modulesStatus.setItpdState(ModuleState.RUNNING);
                stopCounter = STOP_COUNTER_DELAY;
            }
        } else {
            if (itpdState == RUNNING) {
                modulesStatus.setItpdState(STOPPED);
            }
        }
    }

    private void updateIptablesRules(ModuleState dnsCryptState, ModuleState torState,
                                     ModuleState itpdState, OperationMode operationMode,
                                     boolean rootIsAvailable, boolean useModulesWithRoot) {

        if (dnsCryptState != savedDNSCryptState
                || torState != savedTorState
                || itpdState != savedItpdState
                || modulesStatus.isIptablesRulesUpdateRequested()) {

            Log.i(LOG_TAG, "DNSCrypt is " + dnsCryptState +
                    " Tor is " + torState + " I2P is " + itpdState);

            if (dnsCryptState != STOPPED && dnsCryptState != RUNNING) {
                return;
            } else if (torState != STOPPED && torState != RUNNING) {
                return;
            } else if (itpdState != STOPPED && itpdState != RUNNING) {
                return;
            }

            savedDNSCryptState = dnsCryptState;
            new PrefManager(modulesService).setStrPref("savedDNSCryptState", dnsCryptState.toString());

            savedTorState = torState;
            new PrefManager(modulesService).setStrPref("savedTorState", torState.toString());

            savedItpdState = itpdState;
            new PrefManager(modulesService).setStrPref("savedITPDState", itpdState.toString());

            if (modulesStatus.isIptablesRulesUpdateRequested()) {
                modulesStatus.setIptablesRulesUpdateRequested(false);

                if (!rootIsAvailable) {
                    Log.w(LOG_TAG, "Iptables rules isn't updated, no root!");
                }
            }

            if (iptablesRules != null && rootIsAvailable && operationMode == ROOT_MODE) {
                String[] commands = iptablesRules.configureIptables(dnsCryptState, torState, itpdState);
                iptablesRules.sendToRootExecService(commands);

                Log.i(LOG_TAG, "Iptables rules updated");

                stopCounter = STOP_COUNTER_DELAY;
            } else if (operationMode == VPN_MODE) {

                if (dnsCryptState == STOPPED && torState == STOPPED) {
                    ServiceVPNHelper.stop("All modules stopped", modulesService);
                } else {
                    ServiceVPNHelper.reload("Modules state changed", modulesService);
                }

                stopCounter = STOP_COUNTER_DELAY;
            }

            if (modulesStatus.isFixTTL() && !modulesStatus.isUseModulesWithRoot() && (operationMode == ROOT_MODE)) {
                if ((dnsCryptState == STOPPED && torState == STOPPED) || useModulesWithRoot) {
                    ServiceVPNHelper.stop("All modules stopped", modulesService);
                } else {
                    ServiceVPNHelper.reload("TTL is fixed", modulesService);
                }
            } else if (operationMode == ROOT_MODE || operationMode == PROXY_MODE){
                ServiceVPNHelper.stop("TTL stop fixing", modulesService);
            }

        } else if (useModulesWithRoot && operationMode == ROOT_MODE) {

            if (dnsCryptState != STOPPED && dnsCryptState != RUNNING) {
                return;
            } else if (torState != STOPPED && torState != RUNNING) {
                return;
            } else if (itpdState != STOPPED && itpdState != RUNNING) {
                return;
            } else if (modulesStatus.isContextUIDUpdateRequested()) {
                return;
            }

            stopCounter--;
        } else if (dnsCryptState == STOPPED && torState == STOPPED && itpdState == STOPPED) {
            stopCounter--;
        }

    }

    private void updateContextUID(ModuleState dnsCryptState, ModuleState torState, ModuleState itpdState) {

        if (!modulesStatus.isRootAvailable()) {
            modulesStatus.setContextUIDUpdateRequested(false);
            Log.w(LOG_TAG, "Modules Selinux context and UID not updated. Root is Not Available");
            return;
        }

        if (dnsCryptState != STOPPED) {
            return;
        } else if (torState != STOPPED) {
            return;
        } else if (itpdState != STOPPED) {
            return;
        }

        modulesStatus.setContextUIDUpdateRequested(false);

        contextUIDUpdater.updateModulesContextAndUID();

        Log.i(LOG_TAG, "Modules Selinux context and UID updated for "
                + (modulesStatus.isUseModulesWithRoot() ? "Root" : "No Root"));
    }

    private void restoreModulesSavedState() {
        String savedDNSCryptStateStr = new PrefManager(modulesService).getStrPref("savedDNSCryptState");
        if (!savedDNSCryptStateStr.isEmpty()) {
            savedDNSCryptState = ModuleState.valueOf(savedDNSCryptStateStr);
        }

        String savedTorStateStr = new PrefManager(modulesService).getStrPref("savedTorState");
        if (!savedTorStateStr.isEmpty()) {
            savedTorState = ModuleState.valueOf(savedTorStateStr);
        }

        String savedITPDStateStr = new PrefManager(modulesService).getStrPref("savedITPDState");
        if (!savedITPDStateStr.isEmpty()) {
            savedItpdState = ModuleState.valueOf(savedITPDStateStr);
        }
    }

    private void safeStopModulesService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            modulesService.stopForeground(true);
        }

        modulesService.stopSelf();
    }

    void setDnsCryptThread(Thread dnsCryptThread) {
        ModulesStateLoop.dnsCryptThread = dnsCryptThread;
    }

    void setTorThread(Thread torThread) {
        ModulesStateLoop.torThread = torThread;
    }

    void setItpdThread(Thread itpdThread) {
        ModulesStateLoop.itpdThread = itpdThread;
    }
}
