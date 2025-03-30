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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.modules;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
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
import pan.alexander.tordnscrypt.domain.log_reader.TorInteractorInterface;
import pan.alexander.tordnscrypt.domain.log_reader.LogDataModel;
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.OnDNSCryptLogUpdatedListener;
import pan.alexander.tordnscrypt.domain.log_reader.itpd.OnITPDHtmlUpdatedListener;
import pan.alexander.tordnscrypt.domain.log_reader.tor.OnTorLogUpdatedListener;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.iptables.IptablesRules;
import pan.alexander.tordnscrypt.iptables.ModulesIptablesRules;
import pan.alexander.tordnscrypt.nflog.NflogManager;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.connectivitycheck.ConnectivityCheckManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;
import pan.alexander.tordnscrypt.utils.workers.UpdateIPsManager;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CONNECTION_LOGS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ITPD_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_DNSCRYPT_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_ITPD_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SAVED_TOR_STATE_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_READY_PREF;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.VPN_SERVICE_ENABLED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.PROXY_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

import javax.inject.Inject;
import javax.inject.Named;

public class ModulesStateLoop implements Runnable,
        OnDNSCryptLogUpdatedListener, OnTorLogUpdatedListener, OnITPDHtmlUpdatedListener {

    //Depends on timer, currently 10 sec
    private static final int STOP_COUNTER_DELAY = 10;

    //Delay in sec before service can stop
    private static int stopCounter = STOP_COUNTER_DELAY;

    @Inject
    public DNSCryptInteractorInterface dnsCryptInteractor;
    @Inject
    public TorInteractorInterface torInteractor;
    @Inject
    public ITPDInteractorInterface itpdInteractor;
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public Lazy<NflogManager> nflogManager;
    @Inject
    public Lazy<ConnectivityCheckManager> connectivityCheckManager;
    @Inject
    public Lazy<ModulesStatusBroadcaster> modulesStatusBroadcaster;
    @Inject
    public Lazy<UpdateIPsManager> updateIPsManager;
    @Inject
    public Lazy<ModulesVersions> modulesVersions;

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
    private ModuleState savedFirewallState = UNDEFINED;

    private final SharedPreferences sharedPreferences;

    private int savedIptablesCommandsHash = 0;

    private volatile boolean nflogIsRunning = false;

    ModulesStateLoop(ModulesService modulesService) {
        App.getInstance()
                .getSubcomponentsManager()
                .initLogReaderDaggerSubcomponent()
                .inject(this);

        //Delay in sec before service can stop
        stopCounter = STOP_COUNTER_DELAY;

        this.modulesService = modulesService;

        modulesStatus = ModulesStatus.getInstance();

        iptablesRules = new ModulesIptablesRules(modulesService);

        contextUIDUpdater = new ContextUIDUpdater(modulesService);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(modulesService);

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
                    modulesStatus.getFirewallState(),
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
                if (modulesStatus.isRootAvailable()) {
                    nflogManager.get().stopNflog();
                }
                modulesStatus.setContextUIDUpdateRequested(false);
                App.getInstance().getSubcomponentsManager().releaseLogReaderScope();
                logi("ModulesStateLoop stopCounter is zero. Stop service.");
                safeStopModulesService();
                modulesStatus.setFirewallState(STOPPED, preferenceRepository.get());
            }

            slowDownModulesStateTimerIfRequired();

        } catch (Exception e) {
            if (handler != null) {
                handler.get().post(() -> Toast.makeText(modulesService, R.string.wrong, Toast.LENGTH_SHORT).show());
            }
            loge("ModulesStateLoop run()", e);
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

    private void updateIptablesRules(
            ModuleState dnsCryptState,
            ModuleState torState,
            ModuleState itpdState,
            ModuleState firewallState,
            OperationMode operationMode,
            boolean rootIsAvailable,
            boolean useModulesWithRoot) {
        /* For testing purposes
        logi(String.format("DNSCrypt is %s Tor is %s I2P is %s\n" +
                        "Operation mode %s Use modules with Root %s " +
                        "dnsReady %s torReady %s i2pReady %s",
                dnsCryptState, torState, itpdState,
                operationMode, useModulesWithRoot,
                modulesStatus.isDnsCryptReady(), modulesStatus.isTorReady(), modulesStatus.isItpdReady()));*/

        if (dnsCryptState != savedDNSCryptState
                || torState != savedTorState
                || itpdState != savedItpdState
                || firewallState != savedFirewallState
                || modulesStatus.isIptablesRulesUpdateRequested()) {
            logi(String.format("DNSCrypt is %s Tor is %s I2P is %s Firewall is %s\n" +
                            "Operation mode %s Use modules with Root %s",
                    dnsCryptState, torState, itpdState, firewallState,
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

            boolean nflogStop = false;

            if (savedDNSCryptState != dnsCryptState) {

                saveDNSCryptState(dnsCryptState);

                if (dnsCryptState == RUNNING) {
                    if (dnsCryptInteractor != null) {
                        dnsCryptInteractor.addOnDNSCryptLogUpdatedListener(this);
                    }
                    modulesStatusBroadcaster.get().broadcastDNSCryptRunning();
                    startNflogIfRootMode();
                } else {
                    if (dnsCryptInteractor != null) {
                        dnsCryptInteractor.removeOnDNSCryptLogUpdatedListener(this);
                    }
                    modulesStatusBroadcaster.get().broadcastDNSCryptStopped();
                    setDNSCryptReady(false);
                    denySystemDNS();
                    if (modulesStatus.getTorState() != RUNNING && modulesStatus.getFirewallState() != RUNNING) {
                        stopNflogIfRootMode();
                        nflogStop = true;
                    }
                }
            }

            if (savedTorState != torState) {

                saveTorState(torState);

                if (torState == RUNNING) {
                    if (torInteractor != null) {
                        torInteractor.addOnTorLogUpdatedListener(this);
                    }
                    modulesStatusBroadcaster.get().broadcastTorRunning();
                    if (modulesStatus.getDnsCryptState() != RUNNING) {
                        startNflogIfRootMode();
                    }
                } else {
                    if (torInteractor != null) {
                        torInteractor.removeOnTorLogUpdatedListener(this);
                    }
                    modulesStatusBroadcaster.get().broadcastTorStopped();
                    setTorReady(false);
                    denySystemDNS();
                    if (!nflogStop && modulesStatus.getDnsCryptState() != RUNNING
                            && modulesStatus.getFirewallState() != RUNNING) {
                        stopNflogIfRootMode();
                        nflogStop = true;
                    }
                }
            }

            if (savedItpdState != itpdState) {
                saveITPDState(itpdState);

                if (itpdState == RUNNING) {
                    if (itpdInteractor != null) {
                        itpdInteractor.addOnITPDHtmlUpdatedListener(this);
                    }
                    modulesStatusBroadcaster.get().broadcastI2PDRunning();
                } else {
                    if (itpdInteractor != null) {
                        itpdInteractor.removeOnITPDHtmlUpdatedListener(this);
                    }
                    modulesStatusBroadcaster.get().broadcastI2PDStopped();
                    setITPDReady(false);
                }
            }

            if (savedFirewallState != firewallState) {
                saveFirewallState(firewallState);
                if (firewallState == STARTING || firewallState == RUNNING) {
                    modulesStatusBroadcaster.get().broadcastFirewallRunning();
                    if (modulesStatus.getDnsCryptState() != RUNNING && modulesStatus.getTorState() != RUNNING) {
                        startNflogIfRootMode();
                    }
                } else if (firewallState == STOPPED) {
                    modulesStatusBroadcaster.get().broadcastFirewallStopped();
                    if (!nflogStop && modulesStatus.getDnsCryptState() != RUNNING
                            && modulesStatus.getTorState() != RUNNING) {
                        stopNflogIfRootMode();
                    }
                }
            }

            if (modulesStatus.isIptablesRulesUpdateRequested()) {
                modulesStatus.setIptablesRulesUpdateRequested(false);
            }

            boolean vpnServiceEnabled = sharedPreferences.getBoolean(VPN_SERVICE_ENABLED, false);

            if (iptablesRules != null && rootIsAvailable && operationMode == ROOT_MODE) {
                List<String> commands = iptablesRules.configureIptables(
                        dnsCryptState,
                        torState,
                        itpdState,
                        firewallState
                );
                int hashCode = commands.hashCode();

                if (hashCode == savedIptablesCommandsHash && !iptablesRules.isLastIptablesCommandsReturnError()) {
                    commands = iptablesRules.fastUpdate();
                }

                savedIptablesCommandsHash = hashCode;

                iptablesRules.sendToRootExecService(commands);

                logi("Iptables rules updated");

                stopCounter = STOP_COUNTER_DELAY;
            } else if (operationMode == VPN_MODE) {

                if (vpnServiceEnabled &&
                        dnsCryptState == STOPPED && torState == STOPPED
                        && (firewallState == STOPPED || firewallState == STOPPING)) {
                    ServiceVPNHelper.stop("All modules stopped", modulesService);
                    modulesVersions.get().refreshVersions(modulesService);
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
                        && !ArpScanner.getDhcpGatewayAttackDetected()
                        && !ArpScanner.getArpAttackDetected()) {
                    ServiceVPNHelper.reload("TTL is fixed", modulesService);
                } else {
                    startVPNService();
                }
            } else if ((operationMode == ROOT_MODE || operationMode == PROXY_MODE) && vpnServiceEnabled) {
                ServiceVPNHelper.stop("TTL stop fixing", modulesService);
            }

            //Avoid too frequent iptables update
            if (handler != null
                    && (dnsCryptState != STOPPED || torState != STOPPED || itpdState != STOPPED)) {
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
                && (itpdState == STOPPED || itpdState == FAULT)
                && (firewallState == STOPPING || firewallState == STOPPED)) {
            stopCounter--;
        }

    }

    private void updateContextUID(ModuleState dnsCryptState, ModuleState torState, ModuleState itpdState) {

        if (!modulesStatus.isRootAvailable()) {
            modulesStatus.setContextUIDUpdateRequested(false);
            logw("Modules Selinux context and UID not updated. Root is Not Available");
            return;
        }

        if (dnsCryptState != STOPPED || torState != STOPPED || itpdState != STOPPED) {
            ModulesAux.stopModulesIfRunning(modulesService);
            return;
        }

        modulesStatus.setContextUIDUpdateRequested(false);

        contextUIDUpdater.updateModulesContextAndUID();

        logi("Modules Selinux context and UID updated for "
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
                        && (modulesStatus.getDnsCryptState() == RUNNING
                        || modulesStatus.getTorState() == RUNNING
                        || modulesStatus.getFirewallState() == STARTING
                        || modulesStatus.getFirewallState() == RUNNING)) {
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
        if (dnsCryptLogData.getStartedSuccessfully()
                && modulesStatus.getDnsCryptState() == RUNNING) {
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

        boolean savedReady = modulesStatus.isDnsCryptReady();

        preferenceRepository.get().setBoolPreference(DNSCRYPT_READY_PREF, ready);
        modulesStatus.setDnsCryptReady(ready);
        if (ready) {
            modulesStatusBroadcaster.get().broadcastDNSCryptReady();
        }

        if (ready && !savedReady) {
            connectivityCheckManager.get().refreshConnectivityCheckIPs();
        }

        //If DNSCrypt is ready, app will use DNSCrypt DNS instead of Tor Exit node DNS in VPN mode
        if (ready && !savedReady && modulesStatus.isTorReady()
                && (modulesStatus.getMode() == VPN_MODE || isFixTTL())) {
            ServiceVPNHelper.reload("Use DNSCrypt DNS instead of Tor", modulesService);
        }
    }

    @Override
    public void onTorLogUpdated(@NonNull LogDataModel torLogData) {
        if (torLogData.getStartedSuccessfully()
                && modulesStatus.getTorState() == RUNNING) {
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
        boolean savedReady = modulesStatus.isTorReady();

        preferenceRepository.get().setBoolPreference(TOR_READY_PREF, ready);
        modulesStatus.setTorReady(ready);
        if (ready) {
            modulesStatusBroadcaster.get().broadcastTorReady();
        }

        if (ready && !savedReady) {
            updateIPsManager.get().startRefreshTorUnlockIPs();
        }
    }

    private synchronized void denySystemDNS() {

        if (modulesStatus.isSystemDNSAllowed()) {
            if (modulesStatus.getMode() == ROOT_MODE) {
                modulesStatus.setSystemDNSAllowed(false);
                ModulesIptablesRules.denySystemDNS(modulesService, pathVars.get());
            }

            if (modulesStatus.getMode() == VPN_MODE || isFixTTL()) {
                modulesStatus.setSystemDNSAllowed(false);
                ServiceVPNHelper.reload("DNSCrypt Deny system DNS", modulesService);
            }
        }
    }

    @Override
    public void onITPDHtmlUpdated(@NonNull LogDataModel itpdLogData) {
        if (itpdLogData.getStartedSuccessfully()
                && modulesStatus.getItpdState() == RUNNING) {
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
        if (ready) {
            modulesStatusBroadcaster.get().broadcastI2PDReady();
        }
    }

    private void saveFirewallState(ModuleState firewallState) {
        savedFirewallState = firewallState;
        if (firewallState == RUNNING) {
            ModulesAux.saveFirewallStateRunning(true);
        } else if (firewallState == STOPPED) {
            ModulesAux.saveFirewallStateRunning(false);
        }
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
                && !App.getInstance().isAppForeground()) {
            modulesService.slowdownTimer();
        }
    }

    @SuppressLint("UnsafeOptInUsageWarning")
    private void startNflogIfRootMode() {
        if (!nflogIsRunning && modulesStatus.getMode() == ROOT_MODE
                && !modulesStatus.isUseModulesWithRoot()
                && defaultPreferences.get().getBoolean(CONNECTION_LOGS, true)) {
            nflogIsRunning = true;
            nflogManager.get().startNflog();
        }
    }

    @SuppressLint("UnsafeOptInUsageWarning")
    private void stopNflogIfRootMode() {
        if (nflogIsRunning || modulesStatus.getMode() == ROOT_MODE && !modulesStatus.isFixTTL()) {
            nflogManager.get().stopNflog();
            nflogIsRunning = false;
            modulesVersions.get().refreshVersions(modulesService);
        }
    }
}
