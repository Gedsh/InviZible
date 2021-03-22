package pan.alexander.tordnscrypt.itpd_fragment;

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
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.Objects;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.domain.ITPDInteractorInterface;
import pan.alexander.tordnscrypt.domain.entities.LogDataModel;
import pan.alexander.tordnscrypt.domain.LogReaderInteractors;
import pan.alexander.tordnscrypt.domain.log_reader.itpd.OnITPDHtmlUpdatedListener;
import pan.alexander.tordnscrypt.domain.log_reader.itpd.OnITPDLogUpdatedListener;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.FileShortener;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.TopFragment.ITPDVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.FAULT;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STARTING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

public class ITPDFragmentPresenter implements ITPDFragmentPresenterInterface,
        OnITPDLogUpdatedListener, OnITPDHtmlUpdatedListener {

    private boolean runI2PDWithRoot = false;

    private Context context;
    private ITPDFragmentView view;
    private String appDataDir;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private ModuleState fixedModuleState;
    private boolean itpdLogAutoScroll = true;
    private ScaleGestureDetector scaleGestureDetector;

    private ITPDInteractorInterface itpdInteractor;
    private int previousLastLinesLength;
    private boolean fixedITPDReady;


    public ITPDFragmentPresenter(ITPDFragmentView view) {
        this.view = view;
    }

    public void onStart() {
        if (!isActive()) {
            return;
        }

        context = view.getFragmentActivity();

        PathVars pathVars = PathVars.getInstance(context);
        appDataDir = pathVars.getAppDataDir();

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        runI2PDWithRoot = shPref.getBoolean("swUseModulesRoot", false);

        if (isITPDInstalled()) {
            setITPDInstalled(true);

            ModuleState currentModuleState = modulesStatus.getItpdState();

            if (currentModuleState == RUNNING || ModulesAux.isITPDSavedStateRunning(context)) {

                if (isITPDReady()) {
                    setITPDRunning();
                    setITPDProgressBarIndeterminate(false);
                    setFixedReadyState(true);
                } else {
                    setITPDStarting();
                    setITPDProgressBarIndeterminate(true);
                }

            } else if (currentModuleState == STARTING || currentModuleState == RESTARTING) {
                setITPDStarting();
                setITPDProgressBarIndeterminate(true);
            } else if (currentModuleState == STOPPING) {
                setITPDStopping();
                setITPDProgressBarIndeterminate(true);
            } else if (currentModuleState == FAULT) {
                setITPDSomethingWrong();
                setITPDProgressBarIndeterminate(false);
            } else {
                setITPDProgressBarIndeterminate(false);
                setITPDStopped();
                modulesStatus.setItpdState(STOPPED);
            }

            if (currentModuleState != STOPPED
                    && currentModuleState != FAULT) {
                displayLog();
            }

        } else {
            setITPDInstalled(false);
        }

        registerZoomGestureDetector();
    }

    public void onStop() {

        stopDisplayLog();

        view = null;
    }

    private void setITPDStarting() {
        if (isActive()) {
            view.setITPDStatus(R.string.tvITPDStarting, R.color.textModuleStatusColorStarting);
        }
    }

    @Override
    public void setITPDRunning() {
        if (isActive()) {
            view.setITPDStatus(R.string.tvITPDRunning, R.color.textModuleStatusColorRunning);
            view.setStartButtonText(R.string.btnITPDStop);
        }
    }

    private void setITPDStopping() {
        if (isActive()) {
            view.setITPDStatus(R.string.tvITPDStopping, R.color.textModuleStatusColorStopping);
        }
    }

    @Override
    public void setITPDStopped() {
        if (!isActive()) {
            return;
        }

        view.setITPDStatus(R.string.tvITPDStop, R.color.textModuleStatusColorStopped);
        view.setStartButtonText(R.string.btnITPDStart);
        view.setITPDLogViewText();

        view.setITPDInfoLogText();

        setFixedReadyState(false);
    }

    @Override
    public void setITPDInstalling() {
        if (isActive()) {
            view.setITPDStatus(R.string.tvITPDInstalling, R.color.textModuleStatusColorInstalling);
        }
    }

    @Override
    public void setITPDInstalled() {
        if (isActive()) {
            view.setITPDStatus(R.string.tvITPDInstalled, R.color.textModuleStatusColorInstalled);
        }
    }

    @Override
    public void setITPDStartButtonEnabled(boolean enabled) {
        if (isActive()) {
            view.setITPDStartButtonEnabled(enabled);
        }
    }

    @Override
    public void setITPDProgressBarIndeterminate(boolean indeterminate) {
        if (isActive()) {
            view.setITPDProgressBarIndeterminate(indeterminate);
        }
    }

    private void setITPDInstalled(boolean installed) {
        if (!isActive()) {
            return;
        }

        if (installed) {
            setITPDStartButtonEnabled(true);
        } else {
            view.setITPDStatus(R.string.tvITPDNotInstalled, R.color.textModuleStatusColorAlert);
        }
    }

    @Override
    public void setITPDSomethingWrong() {
        if (isActive()) {
            view.setITPDStatus(R.string.wrong, R.color.textModuleStatusColorAlert);
            modulesStatus.setItpdState(FAULT);
        }
    }

    @Override
    public boolean isITPDInstalled() {
        return new PrefManager(context).getBoolPref("I2PD Installed");
    }

    @Override
    public void refreshITPDState() {

        if (!isActive()) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getItpdState();

        if (currentModuleState.equals(fixedModuleState) && currentModuleState != STOPPED) {
            return;
        }

        if (currentModuleState == RUNNING) {

            if (isITPDReady()) {
                setITPDRunning();
                setITPDProgressBarIndeterminate(false);
            } else {
                setITPDStarting();
                setITPDProgressBarIndeterminate(true);
            }

            setITPDStartButtonEnabled(true);

            ModulesAux.saveITPDStateRunning(context, true);

            view.setStartButtonText(R.string.btnITPDStop);
        } else if (currentModuleState == RESTARTING) {
            setITPDStarting();
            setITPDProgressBarIndeterminate(true);
        } else if (currentModuleState == STOPPED) {
            stopDisplayLog();

            if (ModulesAux.isITPDSavedStateRunning(context)) {
                setITPDStoppedBySystem();
            } else {
                setITPDStopped();
            }

            setITPDProgressBarIndeterminate(false);

            ModulesAux.saveITPDStateRunning(context, false);

            setITPDStartButtonEnabled(true);
        }

        fixedModuleState = currentModuleState;
    }


    private void setITPDStoppedBySystem() {

        setITPDStopped();

        if (isActive()) {

            modulesStatus.setItpdState(STOPPED);

            ModulesAux.requestModulesStatusUpdate(context);

            FragmentManager fragmentManager = view.getFragmentFragmentManager();
            if (fragmentManager != null) {
                DialogFragment notification = NotificationDialogFragment.newInstance(R.string.helper_itpd_stopped);
                notification.show(fragmentManager, "NotificationDialogFragment");
            }

            Log.e(LOG_TAG, context.getString(R.string.helper_itpd_stopped));
        }

    }

    @Override
    public synchronized void displayLog() {

        if (itpdInteractor == null) {
            itpdInteractor = LogReaderInteractors.Companion.getInteractor();
        }

        itpdInteractor.addOnITPDLogUpdatedListener(this);
        itpdInteractor.addOnITPDHtmlUpdatedListener(this);

        previousLastLinesLength = 0;
    }

    @Override
    public void stopDisplayLog() {
        if (itpdInteractor != null) {
            itpdInteractor.removeOnITPDLogUpdatedListener(this);
            itpdInteractor.removeOnITPDHtmlUpdatedListener(this);
        }
    }

    private boolean isITPDReady() {
        return modulesStatus.isItpdReady();
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

        setITPDStartButtonEnabled(false);

        if (modulesStatus.getItpdState() != RUNNING) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                setITPDStartButtonEnabled(true);
                return;
            }

            copyCertificatesNoRootMethod();

            setITPDStarting();

            runITPD();

            displayLog();
        } else if (modulesStatus.getItpdState() == RUNNING) {

            setITPDStopping();

            stopITPD();

            FileShortener.shortenTooTooLongFile(appDataDir + "/logs/i2pd.log");
        }

        setITPDProgressBarIndeterminate(true);
    }

    private void runITPD() {
        if (isActive()) {
            ModulesRunner.runITPD(context);
        }
    }

    private void stopITPD() {
        if (isActive()) {
            ModulesKiller.stopITPD(context);
        }
    }

    private void copyCertificatesNoRootMethod() {

        if (!isActive() || runI2PDWithRoot) {
            return;
        }

        final String certificateSource = appDataDir + "/app_data/i2pd/certificates";
        final String certificateFolder = appDataDir + "/i2pd_data/certificates";
        final String certificateDestination = appDataDir + "/i2pd_data";

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {

            File certificateFolderDir = new File(certificateFolder);

            if (certificateFolderDir.isDirectory()
                    && certificateFolderDir.listFiles() != null
                    && Objects.requireNonNull(certificateFolderDir.listFiles()).length > 0) {
                return;
            }

            FileOperations.copyFolderSynchronous(context, certificateSource, certificateDestination);
            Log.i(LOG_TAG, "Copy i2p certificates");
        });
    }

    public void itpdLogAutoScrollingAllowed(boolean allowed) {
        itpdLogAutoScroll = allowed;
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
    public void onITPDLogUpdated(@NonNull LogDataModel itpdLogData) {
        final String lastLines = itpdLogData.getLines();

        if (lastLines.isEmpty()) {
            return;
        }

        Spanned htmlLastLines = Html.fromHtml(lastLines);

        if (!isActive() || htmlLastLines == null) {
            return;
        }

        view.getFragmentActivity().runOnUiThread(() -> {

            if (!isActive()) {
                return;
            }

            if (previousLastLinesLength != lastLines.length() && itpdLogAutoScroll) {
                view.setITPDInfoLogText(htmlLastLines);
                view.scrollITPDLogViewToBottom();
                previousLastLinesLength = lastLines.length();
            }
        });
    }

    @Override
    public void onITPDHtmlUpdated(@NonNull LogDataModel itpdHtmlData) {
        String htmlData = itpdHtmlData.getLines();

        if (htmlData.isEmpty()) {
            htmlData = context.getResources().getString(R.string.tvITPDDefaultLog) + " " + ITPDVersion;
        }

        Spanned htmlDataLines;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            htmlDataLines = Html.fromHtml(htmlData, Html.FROM_HTML_MODE_LEGACY);
        } else {
            htmlDataLines = Html.fromHtml(htmlData);
        }

        if (!isActive()) {
            return;
        }

        view.getFragmentActivity().runOnUiThread(() -> {

            if (!isActive()) {
                return;
            }

            if (htmlDataLines != null) {
                view.setITPDLogViewText(htmlDataLines);
            }

            if (fixedITPDReady && !isITPDReady()) {
                setFixedReadyState(false);
            }

            if (itpdHtmlData.getStartedSuccessfully() && !fixedITPDReady) {
                setFixedReadyState(true);
                setITPDRunning();
                setITPDProgressBarIndeterminate(false);
            }

            refreshITPDState();
        });
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

    public void setFixedReadyState(boolean ready) {
        this.fixedITPDReady = ready;
    }
}
