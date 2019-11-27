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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.util.Log;

import java.util.TimerTask;

import pan.alexander.tordnscrypt.iptables.IptablesRules;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;

public class ModulesStateLoop extends TimerTask {
    //Depends on timer, currently 10 sec
    private final int STOP_COUNTER_DELAY = 10;

    private final ModulesStatus modulesStatus;
    private final ModulesService modulesService;
    private final IptablesRules iptablesRules;

    private final ContextUIDUpdater contextUIDUpdater;

    private Thread dnsCryptThread;
    private Thread torThread;
    private Thread itpdThread;

    private ModuleState savedDNSCryptState;
    private ModuleState savedTorState;
    private ModuleState savedItpdState;

    //Delay in sec before service can stop
    private int stopCounter = STOP_COUNTER_DELAY;

    ModulesStateLoop(ModulesService modulesService) {
        this.modulesService = modulesService;

        modulesStatus = ModulesStatus.getInstance();

        iptablesRules = new ModulesIptablesRules(modulesService);

        contextUIDUpdater = new ContextUIDUpdater(modulesService);
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
            safeStopModulesService();
        }
    }

    private void updateModulesState(ModuleState dnsCryptState, ModuleState torState, ModuleState itpdState) {
        if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
            if (dnsCryptState == STOPPED) {
                modulesStatus.setDnsCryptState(ModuleState.RUNNING);
            }
        } else {
            if (dnsCryptState == RUNNING) {
                modulesStatus.setDnsCryptState(STOPPED);
            }
        }

        if (torThread != null && torThread.isAlive()) {
            if (torState == STOPPED) {
                modulesStatus.setTorState(ModuleState.RUNNING);
            }
        } else {
            if (torState == RUNNING) {
                modulesStatus.setTorState(STOPPED);
            }
        }

        if (itpdThread != null && itpdThread.isAlive()) {
            if (itpdState == STOPPED) {
                modulesStatus.setItpdState(ModuleState.RUNNING);
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

            savedDNSCryptState = dnsCryptState;
            savedTorState = torState;
            savedItpdState = itpdState;

            Log.i(LOG_TAG, "DNSCrypt is " + dnsCryptState +
                    " Tor is " + torState + " I2P is " + itpdState);

            if (dnsCryptState != STOPPED && dnsCryptState != RUNNING) {
                return;
            } else if (torState != STOPPED && torState != RUNNING) {
                return;
            } else if (itpdState != STOPPED && itpdState != RUNNING) {
                return;
            }

            if (modulesStatus.isIptablesRulesUpdateRequested()) {
                modulesStatus.setIptablesRulesUpdateRequested(false);

                if (rootIsAvailable) {
                    Log.w(LOG_TAG, "Iptables rules isn't updated, no root!");
                }
            }

            if (iptablesRules != null && rootIsAvailable && operationMode == ROOT_MODE) {
                String[] commands = iptablesRules.configureIptables(dnsCryptState, torState, itpdState);
                iptablesRules.sendToRootExecService(commands);

                Log.i(LOG_TAG, "Iptables rules updated");

                stopCounter = STOP_COUNTER_DELAY;
            }

        } else if (useModulesWithRoot) {

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

    private void safeStopModulesService() {
        modulesService.stopSelf();
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
