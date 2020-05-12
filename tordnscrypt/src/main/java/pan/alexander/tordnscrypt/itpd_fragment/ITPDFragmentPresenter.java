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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.Html;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesKiller;
import pan.alexander.tordnscrypt.modules.ModulesRunner;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.OwnFileReader;
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

public class ITPDFragmentPresenter implements ITPDFragmentPresenterCallbacks {

    private boolean runI2PDWithRoot = false;
    private int displayLogPeriod = -1;

    private ITPDFragmentView view;
    private Timer timer;
    private String appDataDir;
    private volatile OwnFileReader logFile;
    private ModulesStatus modulesStatus;
    private ModuleState fixedModuleState;


    public ITPDFragmentPresenter(ITPDFragmentView view) {
        this.view = view;
    }

    public void onStart(Context context) {
        if (context == null || view == null) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(context);
        appDataDir = pathVars.getAppDataDir();

        modulesStatus = ModulesStatus.getInstance();

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        runI2PDWithRoot = shPref.getBoolean("swUseModulesRoot", false);

        logFile = new OwnFileReader(context, appDataDir + "/logs/i2pd.log");

        if (isITPDInstalled(context)) {
            setITPDInstalled(true);

            if (modulesStatus.getItpdState() == STOPPING){
                setITPDStopping();

                displayLog(10000);
            } else if (isSavedITPDStatusRunning(context) || modulesStatus.getItpdState() == RUNNING) {
                setITPDRunning();

                if (modulesStatus.getItpdState() != RESTARTING) {
                    modulesStatus.setItpdState(RUNNING);
                }

                displayLog(10000);
            } else {
                setITPDStopped();
                modulesStatus.setItpdState(STOPPED);
            }
        } else {
            setITPDInstalled(false);
        }
    }

    public void onStop() {
        stopDisplayLog();
        view = null;
    }

    private void setITPDStarting() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setITPDStatus(R.string.tvITPDStarting, R.color.textModuleStatusColorStarting);
    }

    @Override
    public void setITPDRunning() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setITPDStatus(R.string.tvITPDRunning, R.color.textModuleStatusColorRunning);
        view.setStartButtonText(R.string.btnITPDStop);
    }

    private void setITPDStopping() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setITPDStatus(R.string.tvITPDStopping, R.color.textModuleStatusColorStopping);
    }

    @Override
    public void setITPDStopped() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setITPDStatus(R.string.tvITPDStop, R.color.textModuleStatusColorStopped);
        view.setStartButtonText(R.string.btnITPDStart);
        view.setITPDLogViewText();

        view.setITPDInfoLogText();
    }

    @Override
    public void setITPDInstalling() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setITPDStatus(R.string.tvITPDInstalling, R.color.textModuleStatusColorInstalling);
    }

    @Override
    public void setITPDInstalled() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setITPDStatus(R.string.tvITPDInstalled, R.color.textModuleStatusColorInstalled);
    }

    @Override
    public void setITPDStartButtonEnabled(boolean enabled) {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setITPDStartButtonEnabled(enabled);
    }

    @Override
    public void setITPDProgressBarIndeterminate(boolean indeterminate) {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        view.setITPDProgressBarIndeterminate(indeterminate);
    }

    private void setITPDInstalled(boolean installed) {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
            return;
        }

        if (installed) {
            view.setITPDStartButtonEnabled(true);
        } else {
            view.setITPDStatus(R.string.tvITPDNotInstalled, R.color.textModuleStatusColorAlert);
        }
    }

    @Override
    public void setITPDSomethingWrong() {
        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || modulesStatus == null) {
            return;
        }

        view.setITPDStatus(R.string.wrong, R.color.textModuleStatusColorAlert);
        modulesStatus.setItpdState(FAULT);
    }

    @Override
    public boolean isITPDInstalled(Context context) {
        if (context != null) {
            return new PrefManager(context).getBoolPref("I2PD Installed");
        }
        return false;
    }

    @Override
    public boolean isSavedITPDStatusRunning(Context context) {
        if (context != null) {
            return new PrefManager(context).getBoolPref("I2PD Running");
        }
        return false;
    }

    @Override
    public void saveITPDStatusRunning(Context context, boolean running) {
        if (context != null) {
            new PrefManager(context).setBoolPref("I2PD Running", running);
        }
    }

    @Override
    public void refreshITPDState(Context context) {

        if (context == null || modulesStatus == null || view == null) {
            return;
        }

        ModuleState currentModuleState = modulesStatus.getItpdState();

        if (currentModuleState.equals(fixedModuleState) && currentModuleState != STOPPED) {
            return;
        }


        if (currentModuleState == STARTING) {

            displayLog(1000);

        } else if (currentModuleState == RUNNING) {

            setITPDRunning();

            view.setITPDStartButtonEnabled(true);

            saveITPDStatusRunning(context, true);

            view.setITPDProgressBarIndeterminate(false);

        } else if (currentModuleState == STOPPED) {

            stopDisplayLog();

            if (isSavedITPDStatusRunning(context)) {
                setITPDStoppedBySystem(context);
            } else {
                setITPDStopped();
            }



            saveITPDStatusRunning(context, false);

            view.setITPDStartButtonEnabled(true);
        }

        fixedModuleState = currentModuleState;
    }


    private void setITPDStoppedBySystem(Context context) {

        setITPDStopped();

        if (context != null && view != null && modulesStatus != null) {

            modulesStatus.setItpdState(STOPPED);

            ModulesAux.requestModulesStatusUpdate(context);

            if (view.getFragmentFragmentManager() != null && !view.getFragmentActivity().isFinishing()) {
                DialogFragment notification = NotificationDialogFragment.newInstance(R.string.helper_itpd_stopped);
                notification.show(view.getFragmentFragmentManager(), "NotificationDialogFragment");
            }

            Log.e(LOG_TAG, context.getText(R.string.helper_itpd_stopped).toString());
        }

    }

    @Override
    public void displayLog(int period) {

        if (period == displayLogPeriod) {
            return;
        }

        displayLogPeriod = period;

        if (timer != null) {
            timer.purge();
            timer.cancel();
        }

        timer = new Timer();


        timer.schedule(new TimerTask() {
            int loop = 0;
            String previousLastLines = "";

            @Override
            public void run() {

                try {
                    if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || logFile == null) {
                        return;
                    }

                    final String lastLines = logFile.readLastLines();

                    final String htmlData = readITPDStatusFromHTML(view.getFragmentActivity());

                    if (++loop > 30) {
                        loop = 0;
                        displayLog(10000);
                    }

                    if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing()) {
                        return;
                    }

                    view.getFragmentActivity().runOnUiThread(() -> {

                        if (view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || lastLines == null || lastLines.isEmpty()) {
                            return;
                        }

                        if (!previousLastLines.equals(lastLines)) {
                            view.setITPDInfoLogText(Html.fromHtml(lastLines));
                            previousLastLines = lastLines;
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            view.setITPDLogViewText(Html.fromHtml(htmlData, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            view.setITPDLogViewText(Html.fromHtml(htmlData));
                        }

                        refreshITPDState(view.getFragmentActivity());
                    });
                } catch (Exception e) {
                    Log.e(LOG_TAG, "ITPDFragmentPresenter timer run() exception " + e.getMessage() + " " + e.getCause());
                }
            }
        }, 1000, period);

    }

    @Override
    public void stopDisplayLog() {
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;

            displayLogPeriod = -1;
        }
    }

    private String readITPDStatusFromHTML(Context context) {
        String htmlData = context.getResources().getString(R.string.tvITPDDefaultLog) + " " + ITPDVersion;
        try {
            StringBuilder sb = new StringBuilder();

            URL url = new URL("http://127.0.0.1:7070/");
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setRequestMethod("GET");  //OR  huc.setRequestMethod ("HEAD");
            huc.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9.0.1; " +
                    "Mi Mi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Mobile Safari/537.36");
            huc.setConnectTimeout(1000);
            huc.connect();
            int code = huc.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                huc.disconnect();
                return htmlData;
            }


            BufferedReader in;
            in = new BufferedReader(
                    new InputStreamReader(
                            url.openStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (inputLine.contains("<b>Network status:</b>") || inputLine.contains("<b>Tunnel creation success rate:</b>") ||
                        inputLine.contains("<b>Received:</b> ") || inputLine.contains("<b>Sent:</b>") || inputLine.contains("<b>Transit:</b>") ||
                        inputLine.contains("<b>Routers:</b>") || inputLine.contains("<b>Client Tunnels:</b>") || inputLine.contains("<b>Uptime:</b>")) {
                    inputLine = inputLine.replace("<div class=right>", "");
                    inputLine = inputLine.replace("<br>", "<br />");
                    sb.append(inputLine);
                }
            }
            in.close();
            huc.disconnect();
            htmlData = sb.toString();
            if (htmlData.contains("<br />")) {
                htmlData = htmlData.substring(0, htmlData.lastIndexOf( "<br />"));
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to read I2PD html" + e.toString());
        }

        return htmlData;
    }

    public void startButtonOnClick(Context context) {
        if (context == null || view == null || view.getFragmentActivity() == null || view.getFragmentActivity().isFinishing() || modulesStatus == null) {
            return;
        }

        if (((MainActivity) context).childLockActive) {
            Toast.makeText(context, context.getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return;
        }

        view.setITPDStartButtonEnabled(false);

        //cleanLogFileNoRootMethod();

        if (!new PrefManager(Objects.requireNonNull(context)).getBoolPref("I2PD Running")
                && new PrefManager(Objects.requireNonNull(context)).getBoolPref("Tor Running")
                && !new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                view.setITPDStartButtonEnabled(true);
                return;
            }

            copyCertificatesNoRootMethod(context);

            setITPDStarting();

            runITPD(context);

            displayLog(1000);
        } else if (!new PrefManager(Objects.requireNonNull(context)).getBoolPref("I2PD Running") &&
                !new PrefManager(context).getBoolPref("Tor Running")
                && !new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                view.setITPDStartButtonEnabled(true);
                return;
            }

            copyCertificatesNoRootMethod(context);

            setITPDStarting();

            runITPD(context);

            displayLog(1000);
        } else if (!new PrefManager(Objects.requireNonNull(context)).getBoolPref("I2PD Running") &&
                !new PrefManager(context).getBoolPref("Tor Running")
                && new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                view.setITPDStartButtonEnabled(true);
                return;
            }

            copyCertificatesNoRootMethod(context);

            setITPDStarting();

            runITPD(context);

            displayLog(1000);
        } else if (!new PrefManager(Objects.requireNonNull(context)).getBoolPref("I2PD Running") &&
                new PrefManager(context).getBoolPref("Tor Running")
                && new PrefManager(context).getBoolPref("DNSCrypt Running")) {

            if (modulesStatus.isContextUIDUpdateRequested()) {
                Toast.makeText(context, R.string.please_wait, Toast.LENGTH_SHORT).show();
                view.setITPDStartButtonEnabled(true);
                return;
            }

            copyCertificatesNoRootMethod(context);

            setITPDStarting();

            runITPD(context);

            displayLog(1000);
        } else if (new PrefManager(Objects.requireNonNull(context)).getBoolPref("I2PD Running")) {

            setITPDStopping();

            stopITPD(context);

            OwnFileReader ofr = new OwnFileReader(context, appDataDir + "/logs/i2pd.log");

            ofr.shortenToToLongFile();
        }

        view.setITPDProgressBarIndeterminate(true);
    }

    private void runITPD(Context context) {

        if (context == null) {
            return;
        }

        ModulesRunner.runITPD(context);
    }

    private void stopITPD(Context context) {

        if (context == null) {
            return;
        }

        ModulesKiller.stopITPD(context);
    }

    private void copyCertificatesNoRootMethod(Context context) {

        if (context == null || runI2PDWithRoot) {
            return;
        }

        final String certificateSource = appDataDir + "/app_data/i2pd/certificates";
        final String certificateFolder = appDataDir + "/i2pd_data/certificates";
        final String certificateDestination = appDataDir + "/i2pd_data";

        File certificateFolderDir = new File(certificateFolder);

        if (certificateFolderDir.isDirectory()
                && certificateFolderDir.listFiles() != null
                && Objects.requireNonNull(certificateFolderDir.listFiles()).length > 0) {
            return;
        }

        new Thread(() -> {
            FileOperations.copyFolderSynchronous(context, certificateSource, certificateDestination);
            Log.i(LOG_TAG, "Copy i2p certificates");
        }).start();
    }
}
