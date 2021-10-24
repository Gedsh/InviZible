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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.List;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.arp.ArpScanner;
import pan.alexander.tordnscrypt.domain.log_reader.DNSCryptInteractorInterface;
import pan.alexander.tordnscrypt.domain.log_reader.ITPDInteractorInterface;
import pan.alexander.tordnscrypt.domain.log_reader.LogReaderInteractors;
import pan.alexander.tordnscrypt.domain.log_reader.TorInteractorInterface;
import pan.alexander.tordnscrypt.domain.log_reader.LogDataModel;
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.OnDNSCryptLogUpdatedListener;
import pan.alexander.tordnscrypt.domain.log_reader.itpd.OnITPDHtmlUpdatedListener;
import pan.alexander.tordnscrypt.domain.log_reader.tor.OnTorLogUpdatedListener;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.iptables.IptablesRules;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ITPD_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_DNSCRYPT_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_ITPD_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_TOR_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class ModulesStateLoop implements Runnable,
        OnDNSCryptLogUpdatedListener, OnTorLogUpdatedListener, OnITPDHtmlUpdatedListener {

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

    private ModuleState savedDNSCryptState = UNDEFINED;
    private ModuleState savedTorState = UNDEFINED;
    private ModuleState savedItpdState = UNDEFINED;

    private final SharedPreferences sharedPreferences;

    private final Lazy<Handler> handler;

    private int savedIptablesCommandsHash = 0;

    private final DNSCryptInteractorInterface dnsCryptInteractor;
    private final TorInteractorInterface torInteractor;
    private final ITPDInteractorInterface itpdInteractor;
    private final Lazy<PreferenceRepository> preferenceRepository;

    ModulesStateLoop(ModulesService modulesService) {
        //Delay in sec before service can stop
        stopCounter = STOP_COUNTER_DELAY;

        this.modulesService = modulesService;

        modulesStatus = ModulesStatus.getInstance();

        iptablesRules = new ModulesIptablesRules(modulesService);

        contextUIDUpdater = new ContextUIDUpdater(modulesService);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(modulesService);

        handler = App.instance.daggerComponent.getHandler();

        LogReaderInteractors logReaderInteractors = LogReaderInteractors.Companion.getInteractor();
        dnsCryptInteractor = logReaderInteractors;
        torInteractor = logReaderInteractors;
        itpdInteractor = logReaderInteractors;

        preferenceRepository = App.instance.daggerComponent.getPreferenceRepository();

        restoreModulesSavedState();
    }

    @Override
    public synchronized void run() {

        try {

            if (modulesStatus == null) {
                return;
            }

            OperationMode operationMode = modulesStatus.getMode();

            boolean rootIsAvailable = modulesStatus.isRootAvailable();
            boolean useModulesWithRoot = modulesStatus.isUseModulesWithRoot();
            boolean contextUIDUpdateRequested = modulesStatus.isContextUIDUpdateRequested();

            if (!(useModulesWithRoot && operationMode == ROOT_MODE)) {
                updateModulesState(
                        modulesStatus.getDnsCryptState(),
                        modulesStatus.getTorState(),
                        modulesStatus.getItpdState()
                );
            }

            updateFixTTLRules();

            updateIptablesRules(
                    modulesStatus.getDnsCryptState(),
                    modulesStatus.getTorState(),
                    modulesStatus.getItpdState(),
                    operationMode,
                    rootIsAvailable,
                    useModulesWithRoot
            );

            if (contextUIDUpdateRequested) {
                updateContextUID(
                        modulesStatus.getDnsCryptState(),
                        modulesStatus.getTorState(),
                        modulesStatus.getItpdState()
                );
            }

            if (stopCounter <= 0) {

                denySystemDNS();

                Log.i(LOG_TAG, "ModulesStateLoop stopCounter is zero. Stop service.");
                modulesStatus.setContextUIDUpdateRequested(false);
                safeStopModulesService();
            }

            slowDownModulesStateTimerIfRequired();

        } catch (Exception e) {
            if (handler != null) {
                handler.get().post(() -> Toast.makeText(modulesService, R.string.wrong, Toast.LENGTH_SHORT).show());
            }
            Log.e(LOG_TAG, "ModulesStateLoop exception " + e.getMessage() + " " + e.getCause());
        }

    }

    private void updateModulesState(ModuleState dnsCryptState, ModuleState torState, ModuleState itpdState) {
        if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
            if (dnsCryptState == STOPPED || dnsCryptState == UNDEFINED) {
                modulesStatus.setDnsCryptState(ModuleState.RUNNING);
                stopCounter = STOP_COUNTER_DELAY;
            }
        } else {
            if (dnsCryptState == RUNNING || dnsCryptState == UNDEFINED) {
                modulesStatus.setDnsCryptState(STOPPED);
            }
        }

        if (torThread != null && torThread.isAlive()) {
            if (torState == STOPPED || torState == UNDEFINED) {
                modulesStatus.setTorState(ModuleState.RUNNING);
                stopCounter = STOP_COUNTER_DELAY;
            }
        } else {
            if (torState == RUNNING || torState == UNDEFINED) {
                modulesStatus.setTorState(STOPPED);
            }
        }

        if (itpdThread != null && itpdThread.isAlive()) {
            if (itpdState == STOPPED || itpdState == UNDEFINED) {
                modulesStatus.setItpdState(ModuleState.RUNNING);
                stopCounter = STOP_COUNTER_DELAY;
            }
        } else {
            if (itpdState == RUNNING || itpdState == UNDEFINED) {
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
        /* For testing purposes
        Log.i(LOG_TAG, String.format("DNSCrypt is %s Tor is %s I2P is %s\n" +
                        "Operation mode %s Use modules with Root %s " +
                        "dnsReady %s torReady %s i2pReady %s",
                dnsCryptState, torState, itpdState,
                operationMode, useModulesWithRoot,
                modulesStatus.isDnsCryptReady(), modulesStatus.isTorReady(), modulesStatus.isItpdReady()));*/

        if (dnsCryptState != savedDNSCryptState
                || torState != savedTorState
                || itpdState != savedItpdState
                || modulesStatus.isIptablesRulesUpdateRequested()) {
            Log.i(LOG_TAG, String.format("DNSCrypt is %s Tor is %s I2P is %s\n" +
                            "Operation mode %s Use modules with Root %s",
                    dnsCryptState, torState, itpdState,
                    operationMode, useModulesWithRoot));

            if (dnsCryptState == RESTARTING) {
                setDNSCryptReady(false);

                if (dnsCryptInteractor != null) {
                    dnsCryptInteractor.addOnDNSCryptLogUpdatedListener(this);
                }
            }
            if (torState == RESTARTING) {
                setTorReady(false);

                if (torInteractor != null) {
                    torInteractor.addOnTorLogUpdatedListener(this);
                }
            }
            if (itpdState == RESTARTING) {
                setITPDReady(false);

                if (itpdInteractor != null) {
                    itpdInteractor.addOnITPDHtmlUpdatedListener(this);
                }
            }

            if (dnsCryptState != STOPPED && dnsCryptState != RUNNING) {
                return;
            } else if (torState != STOPPED && torState != RUNNING) {
                return;
            } else if (itpdState != STOPPED && itpdState != RUNNING) {
                return;
            } else if (iptablesUpdateTemporaryBlocked) {
                return;
            }

            if (savedDNSCryptState != dnsCryptState) {

                saveDNSCryptState(dnsCryptState);

                if (dnsCryptState == RUNNING) {
                    if (dnsCryptInteractor != null) {
                        dnsCryptInteractor.addOnDNSCryptLogUpdatedListener(this);
                    }
                } else {
                    if (dnsCryptInteractor != null) {
                        dnsCryptInteractor.removeOnDNSCryptLogUpdatedListener(this);
                    }
                    setDNSCryptReady(false);
                    denySystemDNS();
                }
            }

            if (savedTorState != torState) {

                saveTorState(torState);

                if (torState == RUNNING) {
                    if (torInteractor != null) {
                        torInteractor.addOnTorLogUpdatedListener(this);
                    }
                } else {
                    if (torInteractor != null) {
                        torInteractor.removeOnTorLogUpdatedListener(this);
                    }
                    setTorReady(false);
                    denySystemDNS();
                }
            }

            if (savedItpdState != itpdState) {
                saveITPDState(itpdState);

                if (itpdState == RUNNING) {
                    if (itpdInteractor != null) {
                        itpdInteractor.addOnITPDHtmlUpdatedListener(this);
                    }
                } else {
                    if (itpdInteractor != null) {
                        itpdInteractor.removeOnITPDHtmlUpdatedListener(this);
                    }
                    setITPDReady(false);
                }
            }


            if (modulesStatus.isIptablesRulesUpdateRequested()) {
                modulesStatus.setIptablesRulesUpdateRequested(false);
            }

            boolean vpnServiceEnabled = sharedPreferences.getBoolean(VPN_SERVICE_ENABLED, false);

            if (iptablesRules != null && rootIsAvailable && operationMode == ROOT_MODE) {
                List<String> commands = iptablesRules.configureIptables(dnsCryptState, torState, itpdState);
                int hashCode = commands.hashCode();

                if (hashCode == savedIptablesCommandsHash && !iptablesRules.isLastIptablesCommandsReturnError()) {
                    commands = iptablesRules.fastUpdate();
                }

                savedIptablesCommandsHash = hashCode;

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

            if (isFixTTL()) {
                if (((dnsCryptState == STOPPED && torState == STOPPED) || useModulesWithRoot) && vpnServiceEnabled) {
                    ServiceVPNHelper.stop("All modules stopped", modulesService);
                } else if (vpnServiceEnabled
                        /*Do not reload service during ARP attack to prevent loop*/
                        && !ArpScanner.INSTANCE.getDhcpGatewayAttackDetected()
                        && !ArpScanner.INSTANCE.getArpAttackDetected()) {
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
                handler.get().postDelayed(() -> {
                    iptablesUpdateTemporaryBlocked = false;
                    ModulesAux.makeModulesStateExtraLoop(modulesService);
                }, 8000);
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
            } else if (dnsCryptState == RUNNING && !modulesStatus.isDnsCryptReady()) {
                return;
            } else if (torState == RUNNING && !modulesStatus.isTorReady()) {
                return;
            } else if (itpdState == RUNNING && !modulesStatus.isItpdReady()) {
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
        String savedDNSCryptStateStr = preferenceRepository.get().getStringPreference(SAVED_DNSCRYPT_STATE_PREF);
        if (!savedDNSCryptStateStr.isEmpty()) {
            savedDNSCryptState = ModuleState.valueOf(savedDNSCryptStateStr);
        }

        String savedTorStateStr = preferenceRepository.get().getStringPreference(SAVED_TOR_STATE_PREF);
        if (!savedTorStateStr.isEmpty()) {
            savedTorState = ModuleState.valueOf(savedTorStateStr);
        }

        String savedITPDStateStr = preferenceRepository.get().getStringPreference(SAVED_ITPD_STATE_PREF);
        if (!savedITPDStateStr.isEmpty()) {
            savedItpdState = ModuleState.valueOf(savedITPDStateStr);
        }
    }

    private void startVPNService() {

        //Start VPN service if it is not started by modules presenters

        final Intent prepareIntent = VpnService.prepare(modulesService);

        if (handler != null && prepareIntent == null) {
            handler.get().postDelayed(() -> {
                if (modulesService != null && modulesStatus != null && sharedPreferences != null
                        && !sharedPreferences.getBoolean(VPN_SERVICE_ENABLED, false)
                        && (modulesStatus.getDnsCryptState() == RUNNING || modulesStatus.getTorState() == RUNNING)) {
                    sharedPreferences.edit().putBoolean(VPN_SERVICE_ENABLED, true).apply();
                    ServiceVPNHelper.start("ModulesStateLoop start VPN service", modulesService);
                }
            }, 10000);
        }
    }

    private void safeStopModulesService() {
        handler.get().post(() -> {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                modulesService.stopForeground(true);
            }

            modulesService.stopSelf();
        });
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

    void clearIptablesCommandHash() {
        savedIptablesCommandsHash = 0;
    }

    void removeHandlerTasks() {
        if (iptablesRules != null) {
            iptablesRules.unregisterReceiver();
        }

        if (handler != null) {
            handler.get().removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void onDNSCryptLogUpdated(@NonNull LogDataModel dnsCryptLogData) {
        if (dnsCryptLogData.getStartedSuccessfully()) {
            setDNSCryptReady(true);
            denySystemDNS();
            if (dnsCryptInteractor != null) {
                dnsCryptInteractor.removeOnDNSCryptLogUpdatedListener(this);
            }
        }
    }

    private void saveDNSCryptState(ModuleState dnsCryptState) {
        savedDNSCryptState = dnsCryptState;
        preferenceRepository.get().setStringPreference(SAVED_DNSCRYPT_STATE_PREF, dnsCryptState.toString());
    }

    private void setDNSCryptReady(boolean ready) {
        preferenceRepository.get().setBoolPreference(DNSCRYPT_READY_PREF, ready);
        modulesStatus.setDnsCryptReady(ready);

        //If DNSCrypt is ready, app will use DNSCrypt DNS instead of Tor Exit node DNS in VPN mode
        if (ready && modulesStatus.isTorReady() && (modulesStatus.getMode() == VPN_MODE || isFixTTL())) {
            ServiceVPNHelper.reload("Use DNSCrypt DNS instead of Tor", modulesService);
        }
    }

    @Override
    public void onTorLogUpdated(@NonNull LogDataModel torLogData) {
        if (torLogData.getStartedSuccessfully()) {
            setTorReady(true);
            denySystemDNS();
            if (torInteractor != null) {
                torInteractor.removeOnTorLogUpdatedListener(this);
            }
        }
    }

    private void saveTorState(ModuleState torState) {
        savedTorState = torState;
        preferenceRepository.get().setStringPreference(SAVED_TOR_STATE_PREF, torState.toString());
    }

    private void setTorReady(boolean ready) {
        preferenceRepository.get().setBoolPreference(TOR_READY_PREF, ready);
        modulesStatus.setTorReady(ready);
    }

    private synchronized void denySystemDNS() {

        if (modulesStatus.isSystemDNSAllowed()) {
            if (modulesStatus.getMode() == ROOT_MODE) {
                modulesStatus.setSystemDNSAllowed(false);
                ModulesIptablesRules.denySystemDNS(modulesService);
            }

            if (modulesStatus.getMode() == VPN_MODE || isFixTTL()) {
                modulesStatus.setSystemDNSAllowed(false);
                ServiceVPNHelper.reload("DNSCrypt Deny system DNS", modulesService);
            }
        }
    }

    @Override
    public void onITPDHtmlUpdated(@NonNull LogDataModel itpdLogData) {
        if (itpdLogData.getStartedSuccessfully()) {
            setITPDReady(true);
            if (itpdInteractor != null) {
                itpdInteractor.removeOnITPDHtmlUpdatedListener(this);
            }
        }
    }

    private void saveITPDState(ModuleState itpdState) {
        savedItpdState = itpdState;
        preferenceRepository.get().setStringPreference(SAVED_ITPD_STATE_PREF, itpdState.toString());
    }

    private void setITPDReady(boolean ready) {
        preferenceRepository.get().setBoolPreference(ITPD_READY_PREF, ready);
        modulesStatus.setItpdReady(ready);
    }

    @Override
    public boolean isActive() {
        return ModulesService.serviceIsRunning;
    }

    private boolean isFixTTL() {
        return modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();
    }

    private void slowDownModulesStateTimerIfRequired() {
        if (!modulesStatus.isUseModulesWithRoot()
                && (modulesStatus.getDnsCryptState() == RUNNING && modulesStatus.isDnsCryptReady() || modulesStatus.getDnsCryptState() == STOPPED)
                && (modulesStatus.getTorState() == RUNNING && modulesStatus.isTorReady() || modulesStatus.getTorState() == STOPPED)
                && (modulesStatus.getItpdState() == RUNNING && modulesStatus.isItpdReady() || modulesStatus.getItpdState() == STOPPED)
                && !(modulesStatus.getDnsCryptState() == STOPPED && modulesStatus.getTorState() == STOPPED && modulesStatus.getItpdState() == STOPPED)
                && !App.instance.isAppForeground()) {
            modulesService.slowdownTimer();
        }
    }
}
