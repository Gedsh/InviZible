package pan.alexander.tordnscrypt.settings.tor_bridges;
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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.FutureTask;

import javax.inject.Inject;
import javax.net.ssl.HttpsURLConnection;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.SelectBridgesTransport;
import pan.alexander.tordnscrypt.dialogs.ShowBridgesCodeImage;
import pan.alexander.tordnscrypt.dialogs.ShowBridgesDialog;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitDialogBridgesRequest;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.wakelock.WakeLocksManager;

import static pan.alexander.tordnscrypt.utils.Constants.LOOPBACK_ADDRESS;
import static pan.alexander.tordnscrypt.utils.Constants.TOR_BROWSER_USER_AGENT;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;

@SuppressWarnings("BusyWait")
public class GetNewBridges implements GetNewBridgesCallbacks {

    private volatile static WeakReference<PleaseWaitDialogBridgesRequest> dialogPleaseWait;

    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CachedExecutor cachedExecutor;

    private static final int READTIMEOUT = 180;
    private static final int CONNECTTIMEOUT = 180;
    private static String transport;
    private static WakeLocksManager wakeLocksManager;

    private SettingsActivity activity;
    private HttpsURLConnection httpsURLConnection;
    private BufferedReader bufferedReader;

    public GetNewBridges(WeakReference<SettingsActivity> activityWeakReference) {
        App.getInstance().getDaggerComponent().inject(this);
        this.activity = activityWeakReference.get();
    }

    public void requestCodeImage(@NonNull final String transport) {

        GetNewBridges.transport = transport;

        FutureTask<Object> threadRequestCodeImage = new FutureTask<>(() -> {

            Bitmap codeImage = null;
            final String captcha_challenge_field_value;
            try {

                lockWakeLock();

                Proxy proxy = null;
                if (ModulesStatus.getInstance().getTorState() == RUNNING) {
                    proxy = new Proxy(
                            Proxy.Type.SOCKS,
                            new InetSocketAddress(
                                    LOOPBACK_ADDRESS,
                                    Integer.parseInt(pathVars.get().getTorSOCKSPort())
                            )
                    );
                }

                URL url = new URL("https://bridges.torproject.org/bridges?transport=" + transport);

                if (proxy == null) {
                    httpsURLConnection = (HttpsURLConnection) url.openConnection();
                } else {
                    httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    httpsURLConnection.setHostnameVerifier((hostname, session) -> true);
                }

                httpsURLConnection.setConnectTimeout(1000 * CONNECTTIMEOUT);
                httpsURLConnection.setReadTimeout(1000 * READTIMEOUT);
                httpsURLConnection.setRequestMethod("GET");
                httpsURLConnection.setRequestProperty("User-Agent", TOR_BROWSER_USER_AGENT);
                httpsURLConnection.connect();

                final int code = httpsURLConnection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    bufferedReader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
                    String inputLine;
                    boolean imageFound = false;
                    boolean keywordFound = false;


                    while ((inputLine = bufferedReader.readLine()) != null && !Thread.currentThread().isInterrupted()) {
                        if (inputLine.contains("data:image/jpeg;base64") && !imageFound) {
                            String[] imgCodeBase64 = inputLine.replace("data:image/jpeg;base64,", "").split("\"");

                            if (imgCodeBase64.length < 4) {
                                throw new IllegalStateException("Tor Project web site error");
                            }

                            byte[] data = Base64.decode(imgCodeBase64[3], Base64.DEFAULT);

                            codeImage = BitmapFactory.decodeByteArray(data, 0, data.length);

                            imageFound = true;

                            if (codeImage == null) {
                                throw new IllegalStateException("Tor Project web site error");
                            }


                        } else if (inputLine.contains("captcha_challenge_field")) {
                            keywordFound = true;
                        } else if (inputLine.contains("value") && keywordFound) {

                            String[] secretCodeArr = inputLine.split("\"");
                            if (secretCodeArr.length > 1) {
                                captcha_challenge_field_value = secretCodeArr[1];

                                final Bitmap finalCodeImage = codeImage;

                                activity = getCurrentActivity();

                                while (activity != null && !activity.isDestroyed() && dialogPleaseWait.get().isStateSaved()) {
                                    Thread.sleep(1000);
                                }

                                activity = getCurrentActivity();

                                if (activity == null || activity.isDestroyed()) {
                                    throw new IllegalStateException("requestCodeImage Activity destroyed");
                                }

                                activity.runOnUiThread(() -> {
                                    activity = getCurrentActivity();

                                    if (activity != null && !activity.isDestroyed()) {
                                        dismissProgressDialog();
                                        showCodeImage(finalCodeImage, captcha_challenge_field_value);
                                    }
                                });
                                break;
                            } else {
                                throw new IllegalStateException("Tor Project web site error");
                            }

                        }

                    }
                } else {
                    throw new IllegalStateException("Tor Project web site unavailable! " + code);
                }

                bufferedReader.close();
                httpsURLConnection.disconnect();

                releaseWakeLock();

            } catch (final Exception e) {
                Log.e(LOG_TAG, "requestCodeImage function fault " + e.getMessage());

                releaseWakeLock();

                activity = getCurrentActivity();

                try {
                    if (httpsURLConnection != null) httpsURLConnection.disconnect();
                    if (bufferedReader != null) bufferedReader.close();

                    while (activity != null && !activity.isDestroyed() && dialogPleaseWait.get().isStateSaved()) {
                        Thread.sleep(1000);
                    }
                } catch (Exception ignored) {}

                activity = getCurrentActivity();

                if (activity == null || activity.isDestroyed()) {
                    return null;
                }

                activity.runOnUiThread(() -> {

                    activity = getCurrentActivity();

                    if (activity == null || activity.isDestroyed()) {
                        return;
                    }

                    if (e.getMessage() != null && !e.getMessage().contains("thread")) {
                        dismissProgressDialog();
                        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            return null;
        });

        if (dialogPleaseWait != null && dialogPleaseWait.get() != null)
            dialogPleaseWait.get().setThreadRequest(threadRequestCodeImage);

        cachedExecutor.submit(threadRequestCodeImage);
    }

    public void showProgressDialog() {
        dialogPleaseWait = new WeakReference<>(new PleaseWaitDialogBridgesRequest());
        dialogPleaseWait.get().show(activity.getSupportFragmentManager(), "PleaseWaitDialogBridgesRequest");
    }

    private void dismissProgressDialog() {
        PleaseWaitDialogBridgesRequest dialog = null;
        if (dialogPleaseWait != null) {
            dialog = dialogPleaseWait.get();
        }

        if (dialog != null && dialog.isAdded() && !dialog.isStateSaved()) {
            dialog.dismiss();
        }
    }

    private synchronized SettingsActivity getCurrentActivity() {
        if (dialogPleaseWait != null && dialogPleaseWait.get() != null
                && dialogPleaseWait.get().isAdded()) {
            return (SettingsActivity) dialogPleaseWait.get().getActivity();
        }

        return null;
    }

    void selectTransport() {
        SelectBridgesTransport selectBridgesTransport = SelectBridgesTransport.INSTANCE.getInstance(new WeakReference<>(this));

        if (selectBridgesTransport != null) {
            selectBridgesTransport.show(activity.getSupportFragmentManager(), "SelectBridgesTransport");
        }
    }

    private synchronized void showCodeImage(Bitmap codeImage, final String secretCode) {
        DialogFragment showBridgesCodeImage = ShowBridgesCodeImage.INSTANCE.getInstance(codeImage, secretCode, new WeakReference<>(this));

        if (showBridgesCodeImage != null) {
            showBridgesCodeImage.show(activity.getSupportFragmentManager(), "ShowBridgesCodeImage");
        }
    }

    private synchronized void showBridges(final String bridges) {
        DialogFragment dialog = ShowBridgesDialog.INSTANCE.getInstance(bridges);
        dialog.show(activity.getSupportFragmentManager(), "ShowBridgesDialog");
    }

    private synchronized void lockWakeLock() {

        wakeLocksManager = WakeLocksManager.getInstance();

        if (activity != null && !activity.isDestroyed()
                && !wakeLocksManager.isWiFiWakeLockHeld() && !wakeLocksManager.isPowerWakeLockHeld()) {
            wakeLocksManager.managePowerWakelock(activity, true);
            wakeLocksManager.manageWiFiLock(activity, true);
        } else {
            wakeLocksManager = null;
        }
    }

    private synchronized void releaseWakeLock() {
        if (wakeLocksManager != null) {
            wakeLocksManager.stopPowerWakelock();
            wakeLocksManager.stopWiFiLock();
            wakeLocksManager = null;
        }
    }

    public void requestNewBridges(@NonNull final String imageCode, @NonNull final String secretCode) {
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////If wrong image code try again/////////////////////////////////////////////////////////////////////////
        //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        FutureTask<Object> threadRequestBridges = new FutureTask<>(() -> {
            Bitmap codeImage = null;
            final String captcha_challenge_field_value;
            try {

                lockWakeLock();

                Proxy proxy = null;
                if (ModulesStatus.getInstance().getTorState() == RUNNING) {
                    proxy = new Proxy(
                            Proxy.Type.SOCKS,
                            new InetSocketAddress(
                                    LOOPBACK_ADDRESS,
                                    Integer.parseInt(pathVars.get().getTorSOCKSPort())
                            )
                    );
                }

                String query = "captcha_challenge_field=" + secretCode +
                        "&captcha_response_field=" + imageCode +
                        "&submit=submit";
                URL url = new URL("https://bridges.torproject.org/bridges?transport=" + transport);

                if (proxy == null) {
                    httpsURLConnection = (HttpsURLConnection) url.openConnection();
                } else {
                    httpsURLConnection = (HttpsURLConnection) url.openConnection(proxy);
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    httpsURLConnection.setHostnameVerifier((hostname, session) -> true);
                }

                //Set to POST
                httpsURLConnection.setDoOutput(true);
                httpsURLConnection.setRequestMethod("POST");
                httpsURLConnection.setRequestProperty("Content-Length", String.valueOf(query.getBytes().length));
                httpsURLConnection.setRequestProperty("User-Agent", TOR_BROWSER_USER_AGENT);
                httpsURLConnection.setConnectTimeout(1000 * CONNECTTIMEOUT);
                httpsURLConnection.setReadTimeout(1000 * READTIMEOUT);

                Writer writer = new OutputStreamWriter(httpsURLConnection.getOutputStream());

                try {
                    writer.write(query);
                    writer.flush();
                } catch (IOException e) {
                    writer.close();
                    throw new IllegalStateException("requestNewBridges exception " + e.getMessage() + " " + e.getCause());
                }

                final int code = httpsURLConnection.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {

                    bufferedReader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));

                    String inputLine;
                    boolean keyWordBridge = false;
                    boolean wrongImageCode = false;
                    boolean imageFound = false;
                    boolean keywordCaptchaFound = false;
                    List<String> newBridges = new LinkedList<>();

                    final StringBuilder sb = new StringBuilder();
                    while ((inputLine = bufferedReader.readLine()) != null && !Thread.currentThread().isInterrupted()) {

                        if (inputLine.contains("id=\"bridgelines\"") && !wrongImageCode) {
                            keyWordBridge = true;
                        } else if (inputLine.contains("<br />") && keyWordBridge && !wrongImageCode) {
                            newBridges.add(inputLine.replace("<br />", "").trim());
                        } else if (!inputLine.contains("<br />") && keyWordBridge && !wrongImageCode) {
                            break;
                        } else if (inputLine.contains("captcha-submission-container")) {
                            wrongImageCode = true;
                        } else if (wrongImageCode) {
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////If wrong image code try again/////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

                            if (inputLine.contains("data:image/jpeg;base64") && !imageFound) {
                                String[] imgCodeBase64 = inputLine.replace("data:image/jpeg;base64,", "").split("\"");

                                if (imgCodeBase64.length < 4) {
                                    throw new IllegalStateException("Tor Project web site error");
                                }

                                byte[] data = Base64.decode(imgCodeBase64[3], Base64.DEFAULT);

                                codeImage = BitmapFactory.decodeByteArray(data, 0, data.length);

                                imageFound = true;

                                if (codeImage == null) {
                                    throw new IllegalStateException("Tor Project web site error");
                                }

                            } else if (inputLine.contains("captcha_challenge_field")) {
                                keywordCaptchaFound = true;
                            } else if (inputLine.contains("value") && keywordCaptchaFound) {
                                String[] secretCodeArr = inputLine.split("\"");
                                if (secretCodeArr.length > 1) {
                                    captcha_challenge_field_value = secretCodeArr[1];

                                    final Bitmap finalCodeImage = codeImage;

                                    activity = getCurrentActivity();

                                    while (activity != null && !activity.isDestroyed() && dialogPleaseWait.get().isStateSaved()) {
                                        Thread.sleep(1000);
                                    }

                                    activity = getCurrentActivity();

                                    if (activity == null || activity.isDestroyed()) {
                                        throw new IllegalStateException("requestCodeImage Activity destroyed");
                                    }

                                    activity.runOnUiThread(() -> {
                                        activity = getCurrentActivity();

                                        if (activity != null && !activity.isDestroyed()) {
                                            dismissProgressDialog();
                                            showCodeImage(finalCodeImage, captcha_challenge_field_value);
                                        }
                                    });
                                    break;
                                } else {
                                   throw new IllegalStateException("Tor Project web site error!");
                                }

                            }
                        }
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
                    }

                    if (keyWordBridge && !wrongImageCode) {

                        for (String bridge : newBridges) {
                            sb.append(bridge).append((char) 10);
                        }

                        activity = getCurrentActivity();

                        while (activity != null && !activity.isDestroyed() && dialogPleaseWait.get().isStateSaved()) {
                            Thread.sleep(1000);
                        }

                        activity = getCurrentActivity();

                        if (activity == null || activity.isDestroyed()) {
                            throw new IllegalStateException("requestCodeImage Activity destroyed");
                        }

                        activity.runOnUiThread(() -> {
                            activity = getCurrentActivity();

                            if (activity != null && !activity.isDestroyed()) {
                                dismissProgressDialog();
                                showBridges(sb.toString());
                            }
                        });

                    } else if (!keyWordBridge && !wrongImageCode) {
                        throw new IllegalStateException("Tor Project web site error!");
                    }

                } else {
                    throw new IllegalStateException("Tor Project web site unavailable! " + code);
                }

                bufferedReader.close();
                httpsURLConnection.disconnect();

                releaseWakeLock();

            } catch (final Exception e) {
                Log.e(LOG_TAG, "requestNewBridges function fault " + e.getMessage());

                releaseWakeLock();

                activity = getCurrentActivity();

                try {
                    if (httpsURLConnection != null) httpsURLConnection.disconnect();
                    if (bufferedReader != null) bufferedReader.close();

                    while (activity != null && !activity.isDestroyed() && dialogPleaseWait.get().isStateSaved()) {
                        Thread.sleep(1000);
                    }
                } catch (Exception ignored) {}

                activity = getCurrentActivity();

                if (activity == null || activity.isDestroyed()) {
                    return null;
                }

                activity.runOnUiThread(() -> {

                    activity = getCurrentActivity();

                    if (activity == null || activity.isDestroyed()) {
                        return;
                    }

                    if (e.getMessage() != null && !e.getMessage().contains("thread")) {
                        dismissProgressDialog();
                        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

            }
            return null;
        });

        if (dialogPleaseWait != null && dialogPleaseWait.get() != null)
            dialogPleaseWait.get().setThreadRequest(threadRequestBridges);

        cachedExecutor.submit(threadRequestBridges);
    }
}
