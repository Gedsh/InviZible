package pan.alexander.tordnscrypt.dnscrypt_fragment;

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

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.domain.ConnectionRecordsInteractorInterface;
import pan.alexander.tordnscrypt.domain.DNSCryptInteractorInterface;
import pan.alexander.tordnscrypt.domain.connection_records.OnConnectionRecordsUpdatedListener;
import pan.alexander.tordnscrypt.domain.entities.LogDataModel;
import pan.alexander.tordnscrypt.domain.LogReaderInteractors;
import pan.alexander.tordnscrypt.domain.log_reader.dnscrypt.OnDNSCryptLogUpdatedListener;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.vpn.service.ServiceVPNHelper;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

public class DNSCryptFragmentPresenter implements DNSCryptFragmentPresenterInterface,
        OnDNSCryptLogUpdatedListener, OnConnectionRecordsUpdatedListener {

    private Context context;

    private DNSCryptFragmentView view;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private ModuleState fixedModuleState;
    private volatile boolean dnsCryptLogAutoScroll = true;

    private DNSCryptInteractorInterface dnsCryptInteractor;
    private ConnectionRecordsInteractorInterface connectionRecordsInteractor;
    private volatile LogDataModel savedLogData;
    private volatile int savedLinesLength;
    private volatile String savedConnectionRecords = "";
    private boolean fixedDNSCryptReady;
    private boolean fixedDNSCryptError;

    private ScaleGestureDetector scaleGestureDetector;

    public DNSCryptFragmentPresenter(DNSCryptFragmentView view) {
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

            if (currentModuleState == RUNNING || ModulesAux.isDnsCryptSavedStateRunning(context)) {


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
            } else {
                setDNSCryptProgressBarIndeterminate(false);
                setDnsCryptStopped();
                modulesStatus.setDnsCryptState(STOPPED);
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

        if (view.getFragmentActivity().isFinishing()) {
            stopDisplayLog();
        }

        view = null;
    }

    @Override
    public boolean isDNSCryptInstalled() {
        return new PrefManager(context).getBoolPref("DNSCrypt Installed");
    }

    @Override
    public synchronized void displayLog() {

        if (dnsCryptInteractor == null) {
            dnsCryptInteractor = LogReaderInteractors.Companion.getInteractor();
        }

        if (connectionRecordsInteractor == null) {
            connectionRecordsInteractor = LogReaderInteractors.Companion.getInteractor();
        }

        dnsCryptInteractor.addOnDNSCryptLogUpdatedListener(this);

        if (modulesStatus.getMode() == VPN_MODE || isFixTTL()) {
            connectionRecordsInteractor.addOnConnectionRecordsUpdatedListener(this);
        }

        savedLogData = null;

        savedLinesLength = 0;
    }

    @Override
    public void stopDisplayLog() {
        if (dnsCryptInteractor != null) {
            dnsCryptInteractor.removeOnDNSCryptLogUpdatedListener(this);
        }

        if (connectionRecordsInteractor != null) {
            connectionRecordsInteractor.removeOnConnectionRecordsUpdatedListener(this);
        }

        savedLogData = null;

        savedLinesLength = 0;
    }

    private void setDnsCryptStarting() {
        if (isActive()) {
            view.setDNSCryptStatus(R.string.tvDNSStarting, R.color.textModuleStatusColorStarting);
        }
    }

    @Override
    public void setDnsCryptRunning() {
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
            htmlLines = Html.fromHtml(dnsCryptLogData.getLines());
        } else {
            htmlLines = Html.fromHtml(dnsCryptLogData.getLines() + "<br />" + savedConnectionRecords);
        }

        if (!isActive() || htmlLines == null) {
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

            if (isFixedReadyState() && !isDNSCryptReady()) {
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

            if (!modulesStatus.isUseModulesWithRoot()) {
                setDNSCryptProgressBarIndeterminate(false);
            }

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
            NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                    context, context.getString(R.string.helper_dnscrypt_no_internet), "helper_dnscrypt_no_internet");
            if (notificationHelper != null) {
                notificationHelper.show(fragmentManager, NotificationHelper.TAG_HELPER);
            }
        }

        setFixedErrorState(true);

        Log.e(LOG_TAG, "DNSCrypt Error: " + logData.getLines());
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

        if (modulesStatus.getMode() != VPN_MODE && !isFixTTL()) {
            if (!savedConnectionRecords.isEmpty()) {
                savedConnectionRecords = "";

                Spanned htmlLines = Html.fromHtml(savedLogLines);

                if (!isActive()) {
                    return;
                }

                view.getFragmentActivity().runOnUiThread(() -> {
                    if (isActive() && htmlLines != null) {
                        view.setDNSCryptLogViewText(htmlLines);
                        view.scrollDNSCryptLogViewToBottom();
                    }
                });
            }

            return;
        }

        if (connectionRecordsInteractor != null && modulesStatus.getDnsCryptState() == RESTARTING) {
            connectionRecordsInteractor.clearConnectionRecords();
            return;
        }

        if (!dnsCryptLogAutoScroll) {
            return;
        }

        if (connectionRecords.equals(savedConnectionRecords)) {
            return;
        }

        Spanned htmlLines = Html.fromHtml(savedLogLines + "<br />" + connectionRecords);

        if (!isActive()) {
            return;
        }

        view.getFragmentActivity().runOnUiThread(() -> {
            if (isActive()) {
                if (htmlLines != null && dnsCryptLogAutoScroll) {
                    view.setDNSCryptLogViewText(htmlLines);
                    view.scrollDNSCryptLogViewToBottom();
                    savedConnectionRecords = connectionRecords;
                }
            } else {
                savedConnectionRecords = "";
            }
        });
    }

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

            ModulesAux.saveDNSCryptStateRunning(context, true);

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

            if (ModulesAux.isDnsCryptSavedStateRunning(context)) {
                setDNSCryptStoppedBySystem();
            } else {
                setDnsCryptStopped();
            }

            setDNSCryptProgressBarIndeterminate(false);

            ModulesAux.saveDNSCryptStateRunning(context, false);

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

        Log.e(LOG_TAG, context.getString(R.string.helper_dnscrypt_stopped));

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
                && !sharedPreferences.getBoolean("ignore_system_dns", false)) {
            modulesStatus.setSystemDNSAllowed(true);
        }
    }

    private void stopDNSCrypt() {
        if (!isActive()) {
            return;
        }

        if (connectionRecordsInteractor != null && (modulesStatus.getMode() == VPN_MODE || isFixTTL())) {
            connectionRecordsInteractor.clearConnectionRecords();
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

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                setDNSCryptStartButtonEnabled(true);
                return;
            }

            setDnsCryptStarting();

            runDNSCrypt();

            displayLog();
        } else if (modulesStatus.getDnsCryptState() == RUNNING) {
            setDnsCryptStopping();
            stopDNSCrypt();
        }

        setDNSCryptProgressBarIndeterminate(true);
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
}
