package pan.alexander.tordnscrypt.utils;
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.settings.tor_bridges.PreferencesTorBridges;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;

public class GetNewBridges {

    public static DialogInterface dialogPleaseWait;

    private static final int READTIMEOUT = 180;
    private static final int CONNECTTIMEOUT = 180;
    private static String transport;

    private Activity activity;
    private Thread threadRequestCodeImage;
    private Thread threadRequestBridges;

    public GetNewBridges(Activity activity) {
        this.activity = activity;
    }

    private void requestCodeImage(final String transport) {

        GetNewBridges.transport = transport;

        threadRequestCodeImage = new Thread(() -> {
            Bitmap codeImage = null;
            final String captcha_challenge_field_value;
            try {
                Proxy proxy = null;
                if (ModulesStatus.getInstance().getTorState() == RUNNING) {
                    PathVars pathVars = PathVars.getInstance(activity);
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", Integer.parseInt(pathVars.getTorHTTPTunnelPort())));
                }

                URL url = new URL("https://bridges.torproject.org/bridges?transport=" + transport);

                HttpsURLConnection con;
                if (proxy == null) {
                    con = (HttpsURLConnection) url.openConnection();
                } else {
                    con = (HttpsURLConnection) url.openConnection(proxy);
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    con.setHostnameVerifier((hostname, session) -> true);
                }

                con.setConnectTimeout(1000 * CONNECTTIMEOUT);
                con.setReadTimeout(1000 * READTIMEOUT);
                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36");
                con.connect();

                final int code = con.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(
                                    con.getInputStream()));
                    String inputLine;
                    boolean imageFound = false;
                    boolean keywordFound = false;


                    while ((inputLine = in.readLine()) != null) {
                        if (inputLine.contains("data:image/jpeg;base64") && !imageFound) {
                            String[] imgCodeBase64 = inputLine.replace("data:image/jpeg;base64,", "").split("\"");

                            if (imgCodeBase64.length < 4) {
                                activity.runOnUiThread(() -> {
                                    if (dialogPleaseWait != null) {
                                        dialogPleaseWait.cancel();
                                    }
                                    Toast.makeText(activity, "Tor Project web site error!", Toast.LENGTH_LONG).show();
                                    Log.e(LOG_TAG, "Tor Project web site error");
                                });
                                return;
                            }

                            byte[] data = Base64.decode(imgCodeBase64[3], Base64.DEFAULT);

                            codeImage = BitmapFactory.decodeByteArray(data, 0, data.length);

                            imageFound = true;

                            if (codeImage == null)
                                return;


                        } else if (inputLine.contains("captcha_challenge_field")) {
                            keywordFound = true;
                        } else if (inputLine.contains("value") && keywordFound) {
                            String[] secretCodeArr = inputLine.split("\"");
                            if (secretCodeArr.length > 1) {
                                captcha_challenge_field_value = secretCodeArr[1];

                                final Bitmap finalCodeImage = codeImage;
                                activity.runOnUiThread(() -> {
                                    if (dialogPleaseWait != null) {
                                        dialogPleaseWait.cancel();
                                    }

                                    showCodeImage(finalCodeImage, captcha_challenge_field_value);
                                });
                                break;
                            } else {
                                activity.runOnUiThread(() -> {
                                    if (dialogPleaseWait != null) {
                                        dialogPleaseWait.cancel();
                                    }
                                    Toast.makeText(activity, "Tor Project web site error!", Toast.LENGTH_LONG).show();
                                    Log.e(LOG_TAG, "Tor Project web site error");
                                });
                                return;
                            }

                        }

                    }

                    in.close();
                } else {
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "Tor Project web site unavailable! " + code, Toast.LENGTH_LONG).show();
                        Log.e(LOG_TAG, "Tor Project web site unavailable! " + code);
                    });
                }
                con.disconnect();
            } catch (final IOException e) {
                activity.runOnUiThread(() -> {
                    if (dialogPleaseWait != null)
                        dialogPleaseWait.dismiss();
                    Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                Log.e(LOG_TAG, "requestCodeImage function fault " + e.getMessage());
            }
        });

        threadRequestCodeImage.start();
    }


    private DialogInterface modernProgressDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle(R.string.pref_fast_use_tor_bridges_request_dialog);
        builder.setMessage(R.string.please_wait);
        builder.setIcon(R.drawable.ic_visibility_off_black_24dp);

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setBackgroundResource(R.drawable.background_10dp_padding);
        progressBar.setBackgroundResource(R.drawable.background_10dp_padding);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {
            try {
                if (threadRequestCodeImage.isAlive())
                    threadRequestCodeImage.interrupt();
                if (threadRequestBridges.isAlive())
                    threadRequestBridges.interrupt();
            } catch (Exception e) {
                Log.e(LOG_TAG, "GetNewBridges modernProgressDialog exception " + e.getMessage() + " " + e.getCause());
            }

            dialogInterface.cancel();
        });
        return builder.show();
    }

    public void selectTransport() {

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        LayoutInflater layoutInflater = (LayoutInflater) Objects.requireNonNull(activity).getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (layoutInflater == null)
            return;

        @SuppressLint("InflateParams") View view = layoutInflater.inflate(R.layout.select_tor_transport, null);
        if (view == null)
            return;

        final RadioGroup rbgTorTransport = view.findViewById(R.id.rbgTorTransport);

        builder.setView(view);

        builder.setTitle(R.string.pref_fast_use_tor_bridges_transport_select);

        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            switch (rbgTorTransport.getCheckedRadioButtonId()) {
                case R.id.rbObfsNone:
                    dialogPleaseWait = modernProgressDialog();
                    requestCodeImage("0");
                    break;
                case R.id.rbObfs3:
                    dialogPleaseWait = modernProgressDialog();
                    requestCodeImage("obfs3");
                    break;
                case R.id.rbObfs4:
                    dialogPleaseWait = modernProgressDialog();
                    requestCodeImage("obfs4");
                    break;
                case R.id.rbObfsScrambleSuit:
                    dialogPleaseWait = modernProgressDialog();
                    requestCodeImage("scramblesuit");
                    break;
            }
        });

        builder.setNegativeButton(R.string.cancel, (dialog, i) -> dialog.cancel());

        builder.show();
    }

    private void showCodeImage(Bitmap codeImage, final String secretCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        LayoutInflater layoutInflater = (LayoutInflater) Objects.requireNonNull(activity).getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (layoutInflater == null)
            return;

        @SuppressLint("InflateParams") View view = layoutInflater.inflate(R.layout.tor_transport_code_image, null);
        if (view == null)
            return;

        ImageView imgCode = view.findViewById(R.id.imgCode);
        final EditText etCode = view.findViewById(R.id.etCode);

        imgCode.setImageBitmap(codeImage);

        builder.setView(view);

        builder.setPositiveButton(R.string.ok, (dialogInterface, i) -> {
            requestNewBridges(etCode.getText().toString(), secretCode);
            dialogPleaseWait = modernProgressDialog();
        });

        builder.setNegativeButton(R.string.cancel, (dialog, i) -> dialog.cancel());
        builder.show();
    }

    private void requestNewBridges(final String imageCode, final String secretCode) {
        threadRequestBridges = new Thread(() -> {
            Bitmap codeImage = null;
            final String captcha_challenge_field_value;
            try {
                Proxy proxy = null;
                if (ModulesStatus.getInstance().getTorState() == RUNNING) {
                    PathVars pathVars = PathVars.getInstance(activity);
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", Integer.parseInt(pathVars.getTorHTTPTunnelPort())));
                }

                String query = "captcha_challenge_field=" + secretCode +
                        "&captcha_response_field=" + imageCode +
                        "&submit=submit";
                URL url = new URL("https://bridges.torproject.org/bridges?transport=" + transport);

                HttpsURLConnection con;
                if (proxy == null) {
                    con = (HttpsURLConnection) url.openConnection();
                } else {
                    con = (HttpsURLConnection) url.openConnection(proxy);
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    con.setHostnameVerifier((hostname, session) -> true);
                }

                //Set to POST
                con.setDoOutput(true);
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Length", String.valueOf(query.getBytes().length));
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9.0.1; " +
                        "Mi Mi) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Mobile Safari/537.36");
                con.setConnectTimeout(1000 * CONNECTTIMEOUT);
                con.setReadTimeout(1000 * READTIMEOUT);
                Writer writer = new OutputStreamWriter(con.getOutputStream());
                writer.write(query);
                writer.flush();
                writer.close();


                final int code = con.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(
                                    con.getInputStream()));
                    String inputLine;
                    boolean keyWordBridge = false;
                    boolean wrongImageCode = false;
                    boolean imageFound = false;
                    boolean keywordCaptchaFound = false;
                    List<String> newBridges = new LinkedList<>();

                    final StringBuilder sb = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
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
                                    activity.runOnUiThread(() -> {
                                        if (dialogPleaseWait != null)
                                            dialogPleaseWait.dismiss();
                                        Toast.makeText(activity, "Tor Project web site error!", Toast.LENGTH_LONG).show();
                                        Log.e(LOG_TAG, "Tor Project web site error");
                                    });
                                    return;
                                }

                                byte[] data = Base64.decode(imgCodeBase64[3], Base64.DEFAULT);

                                codeImage = BitmapFactory.decodeByteArray(data, 0, data.length);

                                imageFound = true;

                                if (codeImage == null)
                                    return;


                            } else if (inputLine.contains("captcha_challenge_field")) {
                                keywordCaptchaFound = true;
                            } else if (inputLine.contains("value") && keywordCaptchaFound) {
                                String[] secretCodeArr = inputLine.split("\"");
                                if (secretCodeArr.length > 1) {
                                    captcha_challenge_field_value = secretCodeArr[1];

                                    final Bitmap finalCodeImage = codeImage;
                                    activity.runOnUiThread(() -> {
                                        if (dialogPleaseWait != null) {
                                            dialogPleaseWait.cancel();
                                        }

                                        showCodeImage(finalCodeImage, captcha_challenge_field_value);
                                    });
                                    break;
                                } else {
                                    activity.runOnUiThread(() -> {
                                        if (dialogPleaseWait != null)
                                            dialogPleaseWait.dismiss();
                                        Toast.makeText(activity, "Tor Project web site error!", Toast.LENGTH_LONG).show();
                                        Log.e(LOG_TAG, "Tor Project web site error");
                                    });
                                    return;
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
                        activity.runOnUiThread(() -> {
                            if (dialogPleaseWait != null)
                                dialogPleaseWait.dismiss();
                            showBridges(sb.toString());
                        });
                    } else if (!keyWordBridge && !wrongImageCode) {
                        activity.runOnUiThread(() -> {
                            if (dialogPleaseWait != null)
                                dialogPleaseWait.dismiss();
                            Toast.makeText(activity, "Tor Project web site error!", Toast.LENGTH_LONG).show();
                            Log.e(LOG_TAG, "Tor Project web site error");
                        });
                    }
                    in.close();
                } else {
                    activity.runOnUiThread(() -> {
                        if (dialogPleaseWait != null)
                            dialogPleaseWait.dismiss();
                        Toast.makeText(activity, "Tor Project web site unavailable! " + code, Toast.LENGTH_LONG).show();
                        Log.e(LOG_TAG, "Tor Project web site unavailable! " + code);
                    });
                }
                con.disconnect();


            } catch (final IOException e) {
                activity.runOnUiThread(() -> {
                    if (dialogPleaseWait != null)
                        dialogPleaseWait.dismiss();
                    Toast.makeText(activity, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                Log.e(LOG_TAG, "requestNewBridges function fault " + e.getMessage());
            }
        });
        threadRequestBridges.start();
    }

    private void showBridges(final String bridges) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);

        TextView tvBridges = new TextView(activity);
        tvBridges.setBackgroundResource(R.drawable.background_10dp_padding);
        tvBridges.setTextIsSelectable(true);
        tvBridges.setSingleLine(false);
        tvBridges.setVerticalScrollBarEnabled(true);
        tvBridges.setText(bridges);

        builder.setTitle(R.string.pref_fast_use_tor_bridges_show_dialog);
        builder.setView(tvBridges);

        builder.setPositiveButton(R.string.pref_fast_use_tor_bridges_add_dialog, (dialogInterface, i) -> {
            FragmentManager fm = ((SettingsActivity) activity).getSupportFragmentManager();
            PreferencesTorBridges frgPreferencesTorBridges = (PreferencesTorBridges) fm.findFragmentByTag("PreferencesTorBridges");
            if (frgPreferencesTorBridges != null) {
                frgPreferencesTorBridges.readCurrentCustomBridges(bridges);
            } else {
                Toast.makeText(activity, "Unable to save bridges!", Toast.LENGTH_LONG).show();
            }

        });

        builder.setNegativeButton(R.string.pref_fast_use_tor_bridges_close_dialog, (dialogInterface, i) -> dialogInterface.cancel());

        builder.show();
    }
}
