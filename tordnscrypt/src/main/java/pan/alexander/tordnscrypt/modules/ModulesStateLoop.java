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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.iptables.IptablesRules;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesDefault;
import static pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges.snowFlakeBridgesOwn;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class ModulesStateLoop implements Runnable {
    //Depends on timer, currently 10 sec
    private static final int STOP_COUNTER_DELAY = 10;

    //Delay in sec before service can stop
    private static int stopCounter = STOP_COUNTER_DELAY;

    private boolean iptablesUpdateTemporaryBlocked;

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

    private SharedPreferences sharedPreferences;

    private Handler handler;

    ModulesStateLoop(ModulesService modulesService) {
        //Delay in sec before service can stop
        stopCounter = STOP_COUNTER_DELAY;

        this.modulesService = modulesService;

        modulesStatus = ModulesStatus.getInstance();

        iptablesRules = new ModulesIptablesRules(modulesService);

        contextUIDUpdater = new ContextUIDUpdater(modulesService);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(modulesService);

        handler = new Handler(Looper.getMainLooper());

        restoreModulesSavedState();
    }

    @Override
    public synchronized void run() {

        try {

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

            updateFixTTLRules();

            updateIptablesRules(dnsCryptState, torState, itpdState, operationMode, rootIsAvailable, useModulesWithRoot);

            if (contextUIDUpdateRequested) {
                updateContextUID(dnsCryptState, torState, itpdState);
            }

            if (stopCounter <= 0) {

                denySystemDNS(operationMode, dnsCryptState, torState, useModulesWithRoot);

                Log.i(LOG_TAG, "ModulesStateLoop stopCounter is zero. Stop service.");
                modulesStatus.setContextUIDUpdateRequested(false);
                safeStopModulesService();
            }
        } catch (Exception e) {
            Toast.makeText(modulesService, R.string.wrong, Toast.LENGTH_SHORT).show();
            Log.e(LOG_TAG, "ModulesStateLoop exception " + e.getMessage() + " " + e.getCause());
        }

    }

    private void denySystemDNS(OperationMode operationMode, ModuleState dnsCryptState,
                               ModuleState torState, boolean useModulesWithRoot) {
        if (operationMode != ROOT_MODE) {
            return;
        }

        boolean useDefaultBridges = new PrefManager(modulesService).getBoolPref("useDefaultBridges");
        boolean useOwnBridges = new PrefManager(modulesService).getBoolPref("useOwnBridges");
        boolean bridgesSnowflakeDefault = new PrefManager(modulesService).getStrPref("defaultBridgesObfs").equals(snowFlakeBridgesDefault);
        boolean bridgesSnowflakeOwn = new PrefManager(modulesService).getStrPref("ownBridgesObfs").equals(snowFlakeBridgesOwn);
        boolean dnsCryptSystemDNSAllowed = new PrefManager(modulesService).getBoolPref("DNSCryptSystemDNSAllowed");

        if (dnsCryptSystemDNSAllowed
                || (dnsCryptState == STOPPED && torState == RUNNING && useModulesWithRoot
                && (useDefaultBridges && bridgesSnowflakeDefault || useOwnBridges && bridgesSnowflakeOwn))) {
            new PrefManager(modulesService).setBoolPref("DNSCryptSystemDNSAllowed", false);
            ModulesIptablesRules.denySystemDNS(modulesService);
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

    private void updateFixTTLRules() {
        if (modulesStatus.isFixTTLRulesUpdateRequested()) {

            modulesStatus.setFixTTLRulesUpdateRequested(false);

            if (!modulesStatus.isIptablesRulesUpdateRequested()) {
                iptablesRules.refreshFixTTLRules();
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
            } else if (iptablesUpdateTemporaryBlocked) {
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
            }

            boolean vpnServiceEnabled = sharedPreferences.getBoolean("VPNServiceEnabled", false);

            if (iptablesRules != null && rootIsAvailable && operationMode == ROOT_MODE) {
                List<String> commands = iptablesRules.configureIptables(dnsCryptState, torState, itpdState);
                iptablesRules.sendToRootExecService(commands);

                Log.i(LOG_TAG, "Iptables rules updated");

                stopCounter = STOP_COUNTER_DELAY;
            } else if (operationMode == VPN_MODE) {

                if (dnsCryptState == STOPPED && torState == STOPPED) {
                    ServiceVPNHelper.stop("All modules stopped", modulesService);
                } else if (vpnServiceEnabled) {
                    ServiceVPNHelper.reload("Modules state changed", modulesService);
                } else {
                    startVPNService();
                }

                stopCounter = STOP_COUNTER_DELAY;
            }

            if (modulesStatus.isFixTTL() && !modulesStatus.isUseModulesWithRoot() && (operationMode == ROOT_MODE)) {
                if (((dnsCryptState == STOPPED && torState == STOPPED) || useModulesWithRoot) && vpnServiceEnabled) {
                    ServiceVPNHelper.stop("All modules stopped", modulesService);
                } else if (vpnServiceEnabled) {
                    ServiceVPNHelper.reload("TTL is fixed", modulesService);
                } else {
                    startVPNService();
                }
            } else if ((operationMode == ROOT_MODE || operationMode == PROXY_MODE) && vpnServiceEnabled) {
                ServiceVPNHelper.stop("TTL stop fixing", modulesService);
            }

            //Avoid too frequent iptables update
            if (handler != null) {
                iptablesUpdateTemporaryBlocked = true;
                handler.postDelayed(() -> {
                    iptablesUpdateTemporaryBlocked = false;
                    ModulesAux.makeModulesStateExtraLoop(modulesService);
                }, 9000);
            }

        } else if (useModulesWithRoot && operationMode == ROOT_MODE) {

            if (dnsCryptState != STOPPED && dnsCryptState != RUNNING && dnsCryptState != FAULT) {
                return;
            } else if (torState != STOPPED && torState != RUNNING && torState != FAULT) {
                return;
            } else if (itpdState != STOPPED && itpdState != RUNNING && itpdState != FAULT) {
                return;
            } else if (modulesStatus.isContextUIDUpdateRequested()) {
                return;
            }

            stopCounter--;
        } else if ((dnsCryptState == STOPPED || dnsCryptState == FAULT)
                && (torState == STOPPED || torState == FAULT)
                && (itpdState == STOPPED || itpdState == FAULT)) {
            stopCounter--;
        }

    }

    private void updateContextUID(ModuleState dnsCryptState, ModuleState torState, ModuleState itpdState) {

        if (!modulesStatus.isRootAvailable()) {
            modulesStatus.setContextUIDUpdateRequested(false);
            Log.w(LOG_TAG, "Modules Selinux context and UID not updated. Root is Not Available");
            return;
        }

        if (dnsCryptState != STOPPED || torState != STOPPED || itpdState != STOPPED) {
            ModulesAux.stopModulesIfRunning(modulesService);
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

    private void startVPNService() {

        //Start VPN service if it is not started by modules presenters

        final Intent prepareIntent = VpnService.prepare(modulesService);

        if (handler != null && prepareIntent == null) {
            handler.postDelayed(() -> {
                if (modulesService != null && modulesStatus != null && sharedPreferences != null
                        && !sharedPreferences.getBoolean("VPNServiceEnabled", false)
                        && (modulesStatus.getDnsCryptState() == RUNNING || modulesStatus.getTorState() == RUNNING)) {
                    sharedPreferences.edit().putBoolean("VPNServiceEnabled", true).apply();
                    ServiceVPNHelper.start("ModulesStateLoop start VPN service", modulesService);
                }
            }, 10000);
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
