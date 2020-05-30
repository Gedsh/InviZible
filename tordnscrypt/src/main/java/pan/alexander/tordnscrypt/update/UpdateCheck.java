package pan.alexander.tordnscrypt.update;
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
import android.util.Base64;
import android.util.Log;

import androidx.fragment.app.FragmentManager;
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

import javax.crypto.Cipher;

import pan.alexander.tordnscrypt.BuildConfig;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.HttpsRequest;
import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.TopFragment.appProcVersion;
import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.Registration.wrongRegistrationCode;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class UpdateCheck {
    public Context context;
    private static PublicKey publicKey;
    private static PrivateKey privateKey;

    public UpdateCheck(Context context) {
        this.context = context;
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
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
            }
            Log.e(LOG_TAG, "RSADecrypt function fault " + e.getMessage());
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
                    appProcVersion.trim() +
                    appVersion.trim() +
                    "submit";

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(signature.getBytes(StandardCharsets.UTF_8));
            String hexBytes = bin2hex(digest);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            byte[] encryptedBytes = cipher.doFinal(hexBytes.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
        } catch (Exception e) {
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
            }
            Log.e(LOG_TAG, "RSASign function fault " + e.getMessage());
        }
        return null;
    }

    private String bin2hex(byte[] data) {
        return String.format("%0" + (data.length * 2) + "X", new BigInteger(1, data)).toLowerCase();
    }


    private void compareVersions(String serverAnswer) {
        if (!serverAnswer.toLowerCase().contains(appProcVersion.toLowerCase())) {
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
                Log.e(LOG_TAG, "compareVersions function fault " + serverAnswer);
            }
            return;
        }

        serverAnswer = serverAnswer.toLowerCase().replace(appProcVersion.toLowerCase(), "").trim();
        String[] modulesArr = serverAnswer.split(";");
        if (modulesArr.length <1) {
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
            }
            Log.w(LOG_TAG, "compareVersions function fault modulesArr length < 1");
            return;
        }

        String[] iproArr = modulesArr[0].split(":");

        if (iproArr.length < 4) {
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
            }
            Log.w(LOG_TAG, "compareVersions function fault iproArr length < 4");
            return;
        }

        if (!iproArr[1].matches("\\d+\\.+\\d+\\.\\d+")) {
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
            }
            Log.w(LOG_TAG, "compareVersions function fault iproArr version regexp mismatch");
            return;
        }

        if (!iproArr[2].matches("\\d{3}")) {
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
            }
            Log.w(LOG_TAG, "compareVersions function fault iproArr pass regexp mismatch");
            return;
        }

        if (!iproArr[3].matches("\\w{8}")) {
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
            }
            Log.w(LOG_TAG, "compareVersions function fault iproArr hash regexp mismatch");
            return;
        }

        if (context == null)
            return;

        int currentIPROversion = Integer.parseInt(BuildConfig.VERSION_NAME.replaceAll("\\D+", ""));

        FragmentManager fm = null;
        try {
            fm = ((MainActivity) context).getSupportFragmentManager();
        } catch (Exception e) {
            if (context != null) {
                if (MainActivity.modernDialog != null)
                    ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
            }
            Log.e(LOG_TAG, "UpdateCheck compareVersions getFragmentManager exception " + e.getMessage());
        }


        if (currentIPROversion < Integer.parseInt(iproArr[1].replaceAll("\\D+", ""))
                || appVersion.startsWith("l")) {
            String message;
            if (appVersion.endsWith("e")) {
                message = context.getString(R.string.thanks_for_donate);
                appVersion = "pfrzo".replace("f", "").replace("z", "");

                SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(context);
                SharedPreferences.Editor editor = sPref.edit();
                editor.putBoolean("pref_fast_auto_update", true);
                editor.apply();
            } else {
                message = context.getString(R.string.update_ipro_has_apdate) + " "
                        + context.getString(R.string.update_new_version) + " " + iproArr[1];
            }
            String iproName = "InviZible_" + appVersion.toUpperCase() + "_ver." + iproArr[1] + "_" + appProcVersion + ".apk";
            String iproUpdateStr = iproArr[2];
            String iproHash = iproArr[3];


            if (fm != null) {
                TopFragment topFragment = (TopFragment) fm.findFragmentByTag("topFragmentTAG");
                if (topFragment != null) {
                    topFragment.downloadUpdate(iproName, iproUpdateStr, message, iproHash);
                    return;
                }

            }
        }

        if (context != null) {
            new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_check_no_update).toString());
            wrongRegistrationCode = true;
        }

        if (context != null && MainActivity.modernDialog != null) {
            ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_check_no_update).toString());
        }
    }

    private String convertKeyForPHP(byte[] key) {
        return Base64.encodeToString(key, Base64.DEFAULT);
    }

    public synchronized void requestUpdateData(final String domainName, final String appSign) {
        if (appVersion.endsWith("p") || appVersion.startsWith("f")) {
            return;
        }

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                String rsaSign = RSASign(appSign);

                if (rsaSign == null) {
                    if (context != null) {
                        if (MainActivity.modernDialog != null)
                            ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_fault).toString());
                        new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
                    }
                    Log.e(LOG_TAG, "RSASign(appSign) returns null");
                    return;
                }

                String registrationCode = new PrefManager(context).getStrPref("registrationCode");

                HashMap<String, String> request = new HashMap<>();
                request.put("sign", rsaSign);
                request.put("key", convertKeyForPHP(publicKey.getEncoded()));
                request.put("app_proc_version", appProcVersion);
                request.put("app_version", appVersion);
                request.put("registration_code", registrationCode.replaceAll("\\W", ""));
                request.put("submit", "submit");


                String url = domainName + "/ru/update";
                String serverAnswerEncoded = HttpsRequest.post(context, url, HttpsRequest.hashMapToUrl(request));

                //Uses for testing purposes:
                //((MainActivity) context).showUpdateMessage(serverAnswerEncoded);

                if (serverAnswerEncoded.isEmpty()) {
                    if (context != null) {
                        if (MainActivity.modernDialog != null)
                            ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_check_warning).toString());
                        new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_check_warning_menu).toString());
                    }
                    Log.e(LOG_TAG, "requestUpdateData function fault - server answer is empty");
                    return;
                } else if (serverAnswerEncoded.contains("fault")) {
                    if (serverAnswerEncoded.contains("wrong code")) {
                        if (context != null) {
                            ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_wrong_code).toString());
                            new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
                            new PrefManager(context).setStrPref("updateTimeLast", "");
                        }
                        wrongRegistrationCode = true;
                        Log.e(LOG_TAG, "requestUpdateData function fault - server returns wrong code");
                    } else if (serverAnswerEncoded.contains("over 3 activations")) {
                        if (context != null) {
                            ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_over_three_activations).toString());
                            new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
                        }
                        wrongRegistrationCode = true;
                        Log.e(LOG_TAG, "requestUpdateData function fault - server returns over 3 activations");
                    } else if (serverAnswerEncoded.contains("over 5 times")) {
                        if (context != null) {
                            ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_over_five_times).toString());
                            new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_fault).toString());
                        }
                        Log.e(LOG_TAG, "requestUpdateData function fault - server returns over 5 times");
                    } else {
                        if (context != null) {
                            if (MainActivity.modernDialog != null)
                                ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_check_warning).toString());
                            new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_check_warning_menu).toString());
                        }
                        Log.e(LOG_TAG, "requestUpdateData function fault - server returns fault");
                        return;
                    }
                }


                String serverAnswer = RSADecrypt(serverAnswerEncoded);

                if (!serverAnswer.isEmpty())
                    compareVersions(serverAnswer);

            } catch (Exception e) {
                if (context != null) {
                    if (MainActivity.modernDialog != null)
                        ((MainActivity) context).showUpdateMessage(context.getText(R.string.update_check_warning).toString());
                    new PrefManager(context).setStrPref("LastUpdateResult", context.getText(R.string.update_check_warning_menu).toString());
                }
                Log.e(LOG_TAG, "requestUpdateData function fault " + e.getMessage());
            }
        });
    }
}
