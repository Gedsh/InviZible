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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.update;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;

import androidx.preference.PreferenceManager;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import javax.crypto.Cipher;
import javax.inject.Inject;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.BuildConfig;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;
import pan.alexander.tordnscrypt.utils.web.HttpsConnectionManager;

import static pan.alexander.tordnscrypt.dialogs.Registration.wrongRegistrationCode;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;

public class UpdateCheck {

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<HttpsConnectionManager> httpsConnectionManager;
    @Inject
    public Lazy<Verifier> verifier;
    @Inject
    public Lazy<CachedExecutor> cachedExecutor;
    @Inject
    public Lazy<PathVars> pathVars;

    private static final int CONNECT_TIMEOUT = 30;
    private static final int READ_TIMEOUT = 30;

    private final TopFragment topFragment;
    private final Context context;
    private volatile static PublicKey publicKey;
    private volatile static PrivateKey privateKey;

    public UpdateCheck(TopFragment topFragment) {
        App.getInstance().getDaggerComponent().inject(this);
        this.topFragment = topFragment;
        this.context = topFragment.getContext();
    }

    /*public byte[] RSAEncrypt(final String plain) throws NoSuchAlgorithmException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.genKeyPair();
        publicKey = kp.getPublic();
        privateKey = kp.getPrivate();

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(plain.getBytes());
    }*/

    private String RSADecrypt(final String encryptedText) {
        try {
            byte[] encryptedBytes = Base64.decode(encryptedText, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            showUpdateMessageAndSaveResult(R.string.update_fault);
            loge("RSADecrypt function fault", e);
        }
        return "";
    }

    private KeyPair generateRSAKeyPair() throws Exception {
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4);
        KeyPairGenerator generator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            generator = KeyPairGenerator.getInstance("RSA");
        } else {
            generator = KeyPairGenerator.getInstance("RSA", "BC");
        }
        generator.initialize(spec, secureRandom);
        return generator.generateKeyPair();
    }


    private String RSASign(final String appSignature) {
        try {
            KeyPair kp = generateRSAKeyPair();
            publicKey = kp.getPublic();
            privateKey = kp.getPrivate();

            String signature = appSignature.trim() +
                    convertKeyForPHP(publicKey.getEncoded()).trim() +
                    pathVars.get().getAppProcVersion().trim() +
                    pathVars.get().getAppVersion().trim() +
                    "submit";

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(signature.getBytes(StandardCharsets.UTF_8));
            String hexBytes = bin2hex(digest);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            byte[] encryptedBytes = cipher.doFinal(hexBytes.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
        } catch (Exception e) {
            showUpdateMessageAndSaveResult(R.string.update_fault);
            loge("RSASign function fault", e);
        }
        return null;
    }

    private String bin2hex(byte[] data) {
        return String.format("%0" + (data.length * 2) + "X", new BigInteger(1, data)).toLowerCase();
    }


    private void compareVersions(String serverAnswer) {
        if (!serverAnswer.toLowerCase().contains(pathVars.get().getAppProcVersion().toLowerCase())) {
            showUpdateMessageAndSaveResult(R.string.update_fault);
            loge("compareVersions function fault " + serverAnswer);
            return;
        }

        serverAnswer = serverAnswer.toLowerCase().replace(pathVars.get().getAppProcVersion().toLowerCase(), "").trim();
        String[] modulesArr = serverAnswer.split(";");
        if (modulesArr.length < 1) {
            showUpdateMessageAndSaveResult(R.string.update_fault);
            logw("compareVersions function fault modulesArr length < 1");
            return;
        }

        String[] iproArr = modulesArr[0].split(":");

        if (iproArr.length < 4) {
            showUpdateMessageAndSaveResult(R.string.update_fault);
            logw("compareVersions function fault iproArr length < 4");
            return;
        }

        if (!iproArr[1].matches("\\d+\\.+\\d+\\.\\d+")) {
            showUpdateMessageAndSaveResult(R.string.update_fault);
            logw("compareVersions function fault iproArr version regexp mismatch");
            return;
        }

        if (!iproArr[2].matches("\\d{3}")) {
            showUpdateMessageAndSaveResult(R.string.update_fault);
            logw("compareVersions function fault iproArr pass regexp mismatch");
            return;
        }

        if (!iproArr[3].matches("\\w{8}")) {
            showUpdateMessageAndSaveResult(R.string.update_fault);
            logw("compareVersions function fault iproArr hash regexp mismatch");
            return;
        }

        Activity activity = topFragment.getActivity();
        if (activity == null) {
            return;
        }

        int currentIPROversion = Integer.parseInt(BuildConfig.VERSION_NAME.replaceAll("\\D+", ""));

        if (currentIPROversion < Integer.parseInt(iproArr[1].replaceAll("\\D+", ""))
                || pathVars.get().getAppVersion().startsWith("l")) {
            String message;
            if (pathVars.get().getAppVersion().endsWith("e")) {
                message = activity.getString(R.string.thanks_for_donate);
                pathVars.get().setAppVersion(this, "pfrzo".replace("f", "").replace("z", ""));

                SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(activity);
                SharedPreferences.Editor editor = sPref.edit();
                editor.putBoolean("pref_fast_auto_update", true);
                editor.apply();
            } else {
                message = activity.getString(R.string.update_ipro_has_apdate) + " "
                        + activity.getString(R.string.update_new_version) + " " + iproArr[1];
            }
            String iproName = "InviZible_" + pathVars.get().getAppVersion().toUpperCase() + "_ver." + iproArr[1] + "_" + pathVars.get().getAppProcVersion() + ".apk";
            String iproUpdateStr = iproArr[2];
            String iproHash = iproArr[3];


            topFragment.downloadUpdate(iproName, iproUpdateStr, message, iproHash);
            return;
        }

        wrongRegistrationCode = true;

        showUpdateMessageAndSaveResult(R.string.update_check_no_update);
    }

    private String convertKeyForPHP(byte[] key) {
        return Base64.encodeToString(key, Base64.DEFAULT);
    }

    public synchronized Future<?> requestUpdateData(final String domainName) {
        if (pathVars.get().getAppVersion().endsWith("p") || pathVars.get().getAppVersion().startsWith("f")) {
            return null;
        }

        return cachedExecutor.get().submit(() -> {
            String serverAnswerEncoded = "";
            String serverAnswer = "";

            try {
                String rsaSign = RSASign(verifier.get().getAppSignature());

                if (rsaSign == null) {
                    showUpdateMessageAndSaveResult(R.string.update_fault);
                    loge("RSASign(appSign) returns null");
                    return;
                }

                String registrationCode = preferenceRepository.get().getStringPreference("registrationCode");

                HashMap<String, String> request = new HashMap<>();
                request.put("sign", rsaSign);
                request.put("key", convertKeyForPHP(publicKey.getEncoded()));
                request.put("app_proc_version", pathVars.get().getAppProcVersion());
                request.put("app_version", pathVars.get().getAppVersion());
                request.put("registration_code", registrationCode.replaceAll("\\W", ""));
                request.put("submit", "submit");


                String url = domainName + "/ru/update/";

                HttpsConnectionManager connectionManager = httpsConnectionManager.get();
                connectionManager.setConnectTimeoutSec(CONNECT_TIMEOUT);
                connectionManager.setReadTimeoutSec(READ_TIMEOUT);
                List<String> answer = connectionManager.post(url, request);
                serverAnswerEncoded = TextUtils.join("\n", answer);

                if (serverAnswerEncoded.isEmpty()) {
                    throw new IllegalStateException("requestUpdateData function fault - server answer is empty");
                } else if (serverAnswerEncoded.contains("fault")) {
                    if (serverAnswerEncoded.contains("wrong code")) {
                        showUpdateMessageAndSaveResult(R.string.update_wrong_code, R.string.update_fault);
                        preferenceRepository.get().setStringPreference("updateTimeLast", "");
                        wrongRegistrationCode = true;
                        loge("requestUpdateData function fault - server returns wrong code");
                        return;
                    } else if (serverAnswerEncoded.contains("over 3 activations")) {
                        showUpdateMessageAndSaveResult(R.string.update_over_three_activations, R.string.update_fault);
                        wrongRegistrationCode = true;
                        loge("requestUpdateData function fault - server returns over 3 activations");
                        return;
                    } else if (serverAnswerEncoded.contains("over 5 times")) {
                        showUpdateMessageAndSaveResult(R.string.update_over_five_times, R.string.update_fault);
                        loge("requestUpdateData function fault - server returns over 5 times");
                        return;
                    } else {
                        throw new IllegalStateException("requestUpdateData function fault - server returns fault");
                    }
                }


                serverAnswer = RSADecrypt(serverAnswerEncoded);

                if (!serverAnswer.isEmpty()) {
                    compareVersions(serverAnswer);
                }

            } catch (CancellationException e) {
                logw("UpdateCheck requestUpdateData", e);
            } catch (Exception e) {
                showUpdateMessageAndSaveResult(R.string.update_check_warning, R.string.update_check_warning_menu);
                loge("UpdateCheck requestUpdateData fault " + e.getMessage()
                        + "; serverAnswerEncoded " + serverAnswerEncoded
                        + "; serverAnswer " + serverAnswer);
            }
        });
    }

    private void showUpdateMessageAndSaveResult(int messageRes) {
        String message = context.getString(messageRes);
        preferenceRepository.get().setStringPreference("LastUpdateResult", message);

        showUpdateMessage(message);
    }

    private void showUpdateMessageAndSaveResult(int messageRes, int toSaveRes) {
        String message = context.getString(messageRes);
        String toSave = context.getString(toSaveRes);
        preferenceRepository.get().setStringPreference("LastUpdateResult", toSave);

        showUpdateMessage(message);
    }

    private void showUpdateMessage(String message) {
        Activity activity = topFragment.getActivity();
        if (activity != null && !activity.isFinishing()) {
            if (topFragment.checkUpdatesDialog != null && topFragment.checkUpdatesDialog.isAdded()) {
                topFragment.showUpdateMessage(activity, message);
            }
        }
    }
}
