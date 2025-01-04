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

package pan.alexander.tordnscrypt.dnscrypt_fragment;

import static androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Spanned;
import android.view.ScaleGestureDetector;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.dialogs.RequestIgnoreBatteryOptimizationDialog;
import pan.alexander.tordnscrypt.dialogs.RequestIgnoreDataRestrictionDialog;
import pan.alexander.tordnscrypt.domain.connection_records.ConnectionRecordsInteractorInterface;
import pan.alexander.tordnscrypt.domain.log_reader.DNSCryptInteractorInterface;
import pan.alexander.tordnscrypt.domain.connection_records.OnConnectionRecordsUpdatedListener;
import pan.alexander.tordnscrypt.domain.log_reader.LogDataModel;
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.OnDNSCryptLogUpdatedListener;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CONNECTION_LOGS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DNSCRYPT_OUTBOUND_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.IGNORE_SYSTEM_DNS;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.UNDEFINED;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_OUTBOUND_PROXY;

import javax.inject.Inject;
import javax.inject.Named;

public class DNSCryptFragmentPresenter implements DNSCryptFragmentPresenterInterface,
        OnDNSCryptLogUpdatedListener, OnConnectionRecordsUpdatedListener {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<DNSCryptInteractorInterface> dnsCryptInteractor;
    @Inject
    public Lazy<ConnectionRecordsInteractorInterface> connectionRecordsInteractor;
    @Inject
    @Named(DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;

    private Context context;

    private DNSCryptFragmentView view;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private ModuleState fixedModuleState = STOPPED;
    private volatile boolean dnsCryptLogAutoScroll = true;

    private volatile LogDataModel savedLogData;
    private volatile int savedLinesLength;
    private volatile String savedConnectionRecords = "";
    private boolean fixedDNSCryptReady;
    private boolean fixedDNSCryptError;

    private ScaleGestureDetector scaleGestureDetector;

    public DNSCryptFragmentPresenter(DNSCryptFragmentView view) {
        App.getInstance()
                .getSubcomponentsManager()
                .initLogReaderDaggerSubcomponent()
                .inject(this);
        this.view = view;
    }

    public void onStart() {
        if (!isActive()) {
            return;
        }

        context = view.getFragmentActivity();

        if (isDNSCryptInstalled()) {
            setDNSCryptInstalled(true);

            ModuleState currentModuleState = modulesStatus.getDnsCryptState();

            if (currentModuleState == RUNNING || ModulesAux.isDnsCryptSavedStateRunning()) {


                if (isDNSCryptReady()) {
                    setDnsCryptRunning();
                    setDNSCryptProgressBarIndeterminate(false);
                    setFixedReadyState(true);
                    setFixedErrorState(false);
                } else {
                    setDnsCryptStarting();
                    setDNSCryptProgressBarIndeterminate(true);
                }

            } else if (currentModuleState == STARTING || currentModuleState == RESTARTING) {
                setDnsCryptStarting();
                setDNSCryptProgressBarIndeterminate(true);
            } else if (currentModuleState == STOPPING) {
                setDnsCryptStopping();
                setDNSCryptProgressBarIndeterminate(true);
            } else if (currentModuleState == FAULT) {
                setDnsCryptSomethingWrong();
                setDNSCryptProgressBarIndeterminate(false);
            } else if (currentModuleState == STOPPED) {
                setDNSCryptProgressBarIndeterminate(false);
                setDnsCryptStopped();
                //modulesStatus.setDnsCryptState(STOPPED);
            }

            if (currentModuleState != STOPPED
                    && currentModuleState != FAULT) {
                displayLog();
            }

        } else {
            setDNSCryptInstalled(false);
        }

        registerZoomGestureDetector();
    }

    public void onStop() {

        if (view == null) {
            return;
        }

        if (!view.getFragmentActivity().isChangingConfigurations()) {
            stopDisplayLog();

            dnsCryptLogAutoScroll = true;
            fixedModuleState = STOPPED;
            savedLogData = null;
            savedLinesLength = 0;
            savedConnectionRecords = "";
            fixedDNSCryptReady = false;
            fixedDNSCryptError = false;
        }

        view = null;
    }

    @Override
    public boolean isDNSCryptInstalled() {
        return preferenceRepository.get()
                .getBoolPreference("DNSCrypt Installed");
    }

    @Override
    public synchronized void displayLog() {

        dnsCryptInteractor.get().addOnDNSCryptLogUpdatedListener(this);

        if (modulesStatus.getMode() == VPN_MODE
                || modulesStatus.getMode() == ROOT_MODE
                || isFixTTL()) {
            connectionRecordsInteractor.get().addOnConnectionRecordsUpdatedListener(this);
        }

        savedLogData = null;

        savedLinesLength = 0;
    }

    @Override
    public void stopDisplayLog() {
        if (dnsCryptInteractor != null) {
            dnsCryptInteractor.get().removeOnDNSCryptLogUpdatedListener(this);
        }

        if (connectionRecordsInteractor != null) {
            connectionRecordsInteractor.get().removeOnConnectionRecordsUpdatedListener(this);
        }

        savedLogData = null;

        savedLinesLength = 0;
    }

    private void setDnsCryptStarting() {
        if (isActive()) {
            view.setDNSCryptStatus(R.string.tvDNSStarting, R.color.textModuleStatusColorStarting);
        }
    }

    private void setDnsCryptRunning() {
        if (!isActive()) {
            return;
        }

        view.setDNSCryptStatus(R.string.tvDNSRunning, R.color.textModuleStatusColorRunning);
        view.setStartButtonText(R.string.btnDNSCryptStop);
    }

    private void setDnsCryptStopping() {
        if (isActive()) {
            view.setDNSCryptStatus(R.string.tvDNSStopping, R.color.textModuleStatusColorStopping);
        }
    }

    @Override
    public void setDnsCryptStopped() {
        if (!isActive()) {
            return;
        }

        view.setDNSCryptStatus(R.string.tvDNSStop, R.color.textModuleStatusColorStopped);
        view.setStartButtonText(R.string.btnDNSCryptStart);
        view.setDNSCryptLogViewText();

        setFixedReadyState(false);
        setFixedErrorState(false);
    }

    @Override
    public void setDnsCryptInstalling() {
        if (isActive()) {
            view.setDNSCryptStatus(R.string.tvDNSInstalling, R.color.textModuleStatusColorInstalling);
        }
    }

    @Override
    public void setDnsCryptInstalled() {
        if (isActive()) {
            view.setDNSCryptStatus(R.string.tvDNSInstalled, R.color.textModuleStatusColorInstalled);
        }
    }

    @Override
    public void setDNSCryptStartButtonEnabled(boolean enabled) {
        if (isActive()) {
            view.setDNSCryptStartButtonEnabled(enabled);
        }
    }

    @Override
    public void setDNSCryptProgressBarIndeterminate(boolean indeterminate) {
        if (isActive()) {
            view.setDNSCryptProgressBarIndeterminate(indeterminate);
        }
    }

    private void setDNSCryptInstalled(boolean installed) {
        if (!isActive()) {
            return;
        }

        if (installed) {
            setDNSCryptStartButtonEnabled(true);
        } else {
            view.setDNSCryptStatus(R.string.tvDNSNotInstalled, R.color.textModuleStatusColorAlert);
        }
    }

    @Override
    public void setDnsCryptSomethingWrong() {
        if (isActive()) {
            view.setDNSCryptStatus(R.string.wrong, R.color.textModuleStatusColorAlert);
            modulesStatus.setDnsCryptState(FAULT);
        }
    }

    @Override
    public void onDNSCryptLogUpdated(@NonNull LogDataModel dnsCryptLogData) {

        String lastLines = dnsCryptLogData.getLines();

        int linesLength = lastLines.length();

        if (dnsCryptLogData.equals(savedLogData) && savedLinesLength == linesLength) {
            return;
        }

        if (lastLines.isEmpty()) {
            return;
        }

        Spanned htmlLines;
        if (savedConnectionRecords.isEmpty()) {
            htmlLines = HtmlCompat.fromHtml(dnsCryptLogData.getLines(), FROM_HTML_MODE_LEGACY);
        } else {
            htmlLines = HtmlCompat.fromHtml(dnsCryptLogData.getLines() + "<br />" + savedConnectionRecords, FROM_HTML_MODE_LEGACY);
        }

        if (!isActive()) {
            return;
        }

        view.getFragmentActivity().runOnUiThread(() -> {

            if (!isActive()) {
                return;
            }

            if (savedLinesLength != linesLength && dnsCryptLogAutoScroll) {

                view.setDNSCryptLogViewText(htmlLines);
                view.scrollDNSCryptLogViewToBottom();

                savedLinesLength = linesLength;
            }

            if (dnsCryptLogData.equals(savedLogData)) {
                return;
            }

            savedLogData = dnsCryptLogData;

            if (isFixedReadyState() && !isDNSCryptReady() && !isUseModulesWithRoot()) {
                setFixedReadyState(false);
            }

            if (dnsCryptLogData.getStartedSuccessfully()) {
                dnsCryptStartedSuccessfully();
            } else if (dnsCryptLogData.getStartedWithError()) {
                dnsCryptStartedWithError(dnsCryptLogData);
            }

            refreshDNSCryptState();

        });

    }

    private void dnsCryptStartedSuccessfully() {

        if (isFixedReadyState() || !isActive()) {
            return;
        }

        if (modulesStatus.getDnsCryptState() == STARTING
                || modulesStatus.getDnsCryptState() == RUNNING) {

            setDNSCryptProgressBarIndeterminate(false);

            setFixedReadyState(true);
            setFixedErrorState(false);
            setDnsCryptRunning();
        }
    }

    private void dnsCryptStartedWithError(LogDataModel logData) {
        if (isFixedErrorState() || !isActive()) {
            return;
        }

        FragmentManager fragmentManager = view.getFragmentFragmentManager();

        //If Tor is ready, app will use Tor Exit node DNS in VPN mode
        if (fragmentManager != null && !(modulesStatus.isTorReady() && modulesStatus.getMode() == VPN_MODE)) {
            if (defaultPreferences.get().getBoolean(DNSCRYPT_OUTBOUND_PROXY, false)) {
                showCheckProxyMessage(fragmentManager);
            } else {
                showCannotConnectMessage(fragmentManager);
            }
        }

        setFixedErrorState(true);

        loge("DNSCrypt Error: " + logData.getLines());
    }

    private void showCheckProxyMessage(FragmentManager fragmentManager) {
        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                context, context.getString(R.string.helper_tor_check_proxy), "helper_dnscrypt_check_proxy");
        if (notificationHelper != null) {
            notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
        }
    }

    private void showCannotConnectMessage(FragmentManager fragmentManager) {
        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                context, context.getString(R.string.helper_dnscrypt_no_internet), "helper_dnscrypt_no_internet");
        if (notificationHelper != null) {
            notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
        }
    }

    @Override
    public void onConnectionRecordsUpdated(@NonNull String connectionRecords) {
        String logLines = "";
        if (savedLogData != null) {
            logLines = savedLogData.getLines();
        }
        displayDnsResponses(logLines, connectionRecords);
    }

    private void displayDnsResponses(String savedLogLines, String connectionRecords) {

        if (modulesStatus.getMode() != VPN_MODE
                && modulesStatus.getMode() != ROOT_MODE
                && !isFixTTL()
                || isRealTimeLogsDisabled()) {
            if (!savedConnectionRecords.isEmpty()) {
                savedConnectionRecords = "";

                Spanned htmlLines = HtmlCompat.fromHtml(savedLogLines, FROM_HTML_MODE_LEGACY);

                if (!isActive()) {
                    return;
                }

                view.getFragmentActivity().runOnUiThread(() -> {
                    if (isActive()) {
                        view.setDNSCryptLogViewText(htmlLines);
                        view.scrollDNSCryptLogViewToBottom();
                    }
                });
            }

            return;
        }

        if (!dnsCryptLogAutoScroll) {
            return;
        }

        if (connectionRecords.equals(savedConnectionRecords) && !savedLogLines.isEmpty()) {
            return;
        }

        Spanned htmlLines = HtmlCompat.fromHtml(savedLogLines + "<br />" + connectionRecords, FROM_HTML_MODE_LEGACY);

        if (!isActive()) {
            return;
        }

        view.getFragmentActivity().runOnUiThread(() -> {
            if (isActive()) {
                if (dnsCryptLogAutoScroll) {
                    view.setDNSCryptLogViewText(htmlLines);
                    view.scrollDNSCryptLogViewToBottom();
                    if (!savedLogLines.isEmpty()) {
                        savedConnectionRecords = connectionRecords;
                    }
                }
            } else {
                savedConnectionRecords = "";
            }
        });
    }

    /* For testing purposes
    String strDiffCalc(String s1, String s2) {

        if (s1.isEmpty() || s2.isEmpty()) {
            return null;
        }

        if (s1.length() > s2.length()) {
            return s1.substring(s2.length() - 1);
        } else if (s2.length() > s1.length()) {
            return s2.substring(s1.length() - 1);
        } else {
            return null;
        }
    }*/

    @Override
    public void refreshDNSCryptState() {

        if (!isActive()) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getDnsCryptState();

        if (currentModuleState.equals(fixedModuleState) && currentModuleState != STOPPED) {
            return;
        }

        if (currentModuleState == RUNNING || currentModuleState == STARTING) {

            if (isFixedReadyState()) {
                setDnsCryptRunning();
                setDNSCryptProgressBarIndeterminate(false);
            } else {
                setDnsCryptStarting();
                setDNSCryptProgressBarIndeterminate(true);
            }

            ServiceVPNHelper.prepareVPNServiceIfRequired(view.getFragmentActivity(), modulesStatus);

            setDNSCryptStartButtonEnabled(true);

            ModulesAux.saveDNSCryptStateRunning(true);

            view.setStartButtonText(R.string.btnDNSCryptStop);
        } else if (currentModuleState == RESTARTING) {
            setDnsCryptStarting();
            setDNSCryptProgressBarIndeterminate(true);
            setFixedReadyState(false);
        } else if (currentModuleState == STOPPING) {
            setDnsCryptStopping();
            setDNSCryptProgressBarIndeterminate(true);
        } else if (currentModuleState == STOPPED) {

            stopDisplayLog();

            if (ModulesAux.isDnsCryptSavedStateRunning()) {
                setDNSCryptStoppedBySystem();
            } else {
                setDnsCryptStopped();
            }

            setDNSCryptProgressBarIndeterminate(false);

            ModulesAux.saveDNSCryptStateRunning(false);

            setDNSCryptStartButtonEnabled(true);
        }

        fixedModuleState = currentModuleState;
    }

    private void setDNSCryptStoppedBySystem() {
        if (!isActive()) {
            return;
        }

        setDnsCryptStopped();

        FragmentManager fragmentManager = view.getFragmentFragmentManager();

        modulesStatus.setDnsCryptState(STOPPED);

        ModulesAux.requestModulesStatusUpdate(context);

        if (fragmentManager != null) {
            DialogFragment notification = NotificationDialogFragment.newInstance(R.string.helper_dnscrypt_stopped);
            notification.show(fragmentManager, "NotificationDialogFragment");
        }

        loge(context.getString(R.string.helper_dnscrypt_stopped));

    }

    private void runDNSCrypt() {
        if (isActive()) {
            if (!modulesStatus.isTorReady()) {
                allowSystemDNS();
            }
            ModulesRunner.runDNSCrypt(context);
        }
    }

    private void allowSystemDNS() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        if ((!modulesStatus.isRootAvailable() || !modulesStatus.isUseModulesWithRoot())
                && !sharedPreferences.getBoolean(IGNORE_SYSTEM_DNS, false)) {
            modulesStatus.setSystemDNSAllowed(true);
        }
    }

    private void stopDNSCrypt() {
        if (!isActive()) {
            return;
        }

        if (connectionRecordsInteractor != null &&
                (modulesStatus.getMode() == VPN_MODE
                        || modulesStatus.getMode() == ROOT_MODE
                        || isFixTTL())) {
            connectionRecordsInteractor.get().clearConnectionRecords();
        }

        ModulesKiller.stopDNSCrypt(context);
    }

    private boolean isDNSCryptReady() {
        return modulesStatus.isDnsCryptReady();
    }

    public void startButtonOnClick() {
        if (!isActive()) {
            return;
        }

        Activity activity = view.getFragmentActivity();

        if (activity instanceof MainActivity && ((MainActivity) activity).childLockActive) {
            Toast.makeText(activity, activity.getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return;
        }


        setDNSCryptStartButtonEnabled(false);


        if (modulesStatus.getDnsCryptState() != RUNNING) {

            if (modulesStatus.isContextUIDUpdateRequested()
                    || modulesStatus.getDnsCryptState() == UNDEFINED) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                setDNSCryptStartButtonEnabled(true);
                return;
            }

            setDnsCryptStarting();

            runDNSCrypt();

            displayLog();

            showIgnoreRestrictionsDialog();
        } else if (modulesStatus.getDnsCryptState() == RUNNING) {
            setDnsCryptStopping();
            stopDNSCrypt();
        }

        setDNSCryptProgressBarIndeterminate(true);
    }

    private void showIgnoreRestrictionsDialog() {
        DialogFragment batteryOptimizationDialog = RequestIgnoreBatteryOptimizationDialog.getInstance(
                context, preferenceRepository.get()
        );

        FragmentManager fragmentManager = view.getFragmentFragmentManager();
        if (batteryOptimizationDialog != null && !fragmentManager.isStateSaved()) {
            batteryOptimizationDialog.show(fragmentManager, RequestIgnoreBatteryOptimizationDialog.TAG);
            return;
        }

        DialogFragment dataRestrictionDialog = RequestIgnoreDataRestrictionDialog.getInstance(
                context, preferenceRepository.get()
        );

        if (dataRestrictionDialog != null && !fragmentManager.isStateSaved()) {
            dataRestrictionDialog.show(fragmentManager, RequestIgnoreDataRestrictionDialog.TAG);
        }
    }

    public void dnsCryptLogAutoScrollingAllowed(boolean allowed) {
        dnsCryptLogAutoScroll = allowed;
    }

    private void registerZoomGestureDetector() {

        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.OnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
                setLogsTextSize(scaleGestureDetector.getScaleFactor());
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector scaleGestureDetector) {
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector scaleGestureDetector) {
            }
        });
    }

    private void setLogsTextSize(float scale) {
        float logsTextSizeMin = context.getResources().getDimension(R.dimen.fragment_log_text_size);
        float logsTextSize = (float) Math.max(logsTextSizeMin, Math.min(TopFragment.logsTextSize * scale, logsTextSizeMin * 1.5));
        TopFragment.logsTextSize = logsTextSize;

        if (view != null) {
            view.setLogsTextSize(logsTextSize);
        }
    }

    public ScaleGestureDetector getScaleGestureDetector() {
        return scaleGestureDetector;
    }

    @Override
    public synchronized boolean isActive() {

        if (view != null) {
            Activity activity = view.getFragmentActivity();
            if (activity != null) {
                return !activity.isFinishing();
            }
        }

        return false;
    }

    private boolean isFixTTL() {
        return modulesStatus.isFixTTL() && (modulesStatus.getMode() == ROOT_MODE)
                && !modulesStatus.isUseModulesWithRoot();
    }

    private synchronized boolean isFixedReadyState() {
        return fixedDNSCryptReady;
    }

    private synchronized void setFixedReadyState(boolean ready) {
        fixedDNSCryptReady = ready;
    }

    private synchronized boolean isFixedErrorState() {
        return fixedDNSCryptError;
    }

    private synchronized void setFixedErrorState(boolean error) {
        fixedDNSCryptError = error;
    }

    private boolean isUseModulesWithRoot() {
        return modulesStatus.isUseModulesWithRoot() && modulesStatus.getMode() == ROOT_MODE;
    }

    private boolean isRealTimeLogsDisabled() {
        return !defaultPreferences.get().getBoolean(CONNECTION_LOGS, true);
    }
}
