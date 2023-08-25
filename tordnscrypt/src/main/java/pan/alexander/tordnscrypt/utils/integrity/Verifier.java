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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.integrity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.settings.PathVars;

import static pan.alexander.tordnscrypt.TopFragment.appVersion;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;


@Singleton
public class Verifier {
    public Lazy<PathVars> pathVars;
    public Context context;
    private volatile String apkSignature;

    @Inject
    public Verifier(Context context, Lazy<PathVars> pathVars) {
        this.context = context;
        this.pathVars = pathVars;
    }

    private String getApkSignatureZip() throws Exception {

        File apkFile = new File(context.getApplicationInfo().sourceDir);

        ZipFile zipFile = new ZipFile(apkFile);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry ze = entries.nextElement();
            String name = ze.getName().toUpperCase();
            if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA"))) {
                try(InputStream inputStream = zipFile.getInputStream(ze);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    byte[] byteSign = baos.toByteArray();
                    byteSign = CertificateFactory.getInstance("X509").generateCertificate(new ByteArrayInputStream(byteSign)).getEncoded();
                    return Base64.encodeToString(MessageDigest.getInstance("md5").digest(byteSign), Base64.DEFAULT);
                } finally {
                    zipFile.close();
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unused")
    private String getApkSignatureZipModern() throws Exception {
        File apkFile = new File(context.getApplicationInfo().sourceDir);
        ZipFile zipFile = new ZipFile(apkFile);
        ZipEntry ze = zipFile.getEntry("META-INF/CERT.RSA");
        if (ze == null)
            return null;
        InputStream inputStream = zipFile.getInputStream(ze);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        baos.close();
        inputStream.close();
        zipFile.close();
        byte[] byteSign = baos.toByteArray();
        byteSign = CertificateFactory.getInstance("X509").generateCertificate(new ByteArrayInputStream(byteSign)).getEncoded();
        return Base64.encodeToString(MessageDigest.getInstance("md5").digest(byteSign), Base64.DEFAULT);
    }


    //The arguement is your public key's value that is deal with md5 and base64
    public String getApkSignature() throws Exception {
        PackageManager packageManager = this.context.getPackageManager();
        String strPackagename = this.context.getPackageName();

        @SuppressLint("PackageManagerGetSignatures")
        Signature[] signatureArray = packageManager.getPackageInfo(strPackagename, PackageManager.GET_SIGNATURES).signatures;

        byte[] byteSign = signatureArray[0].toByteArray();
        byteSign = CertificateFactory.getInstance("X509").generateCertificate(new ByteArrayInputStream(byteSign)).getEncoded();
        //String strSign = new String(Base64.encode(MessageDigest.getInstance("md5").digest(byteSign), 19));
        return Base64.encodeToString(MessageDigest.getInstance("md5").digest(byteSign), Base64.DEFAULT);
    }

    public String decryptStr(String text, String key, String vector) throws Exception {
        key = key.substring(key.length() - 16);
        // Create key and cipher
        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        // decrypt the text
        byte[] ivBytes = vector.substring(vector.length() - 16).getBytes();
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(ivBytes));
        byte[] decrypted = Base64.decode(text.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        if (appVersion.endsWith("d")) {
            return new String(decrypted);
        }
        return new String(cipher.doFinal(decrypted));
    }

    public void encryptStr(String text, String key, String vector) {

        try {
            if (TopFragment.debug) {
                key = key.substring(key.length() - 16);
                // Create key and cipher
                Key aesKey = new SecretKeySpec(key.getBytes(), "AES");

                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                byte[] ivBytes = vector.substring(vector.length() - 16).getBytes();
                // encrypt the text
                cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(ivBytes));
                byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

                File f = new File(pathVars.get().getAppDataDir() + "/logs");

                if (f.mkdirs() && f.setReadable(true) && f.setWritable(true)) {
                    Log.i(LOG_TAG, "encryptStr log dir created");
                } else {
                    Log.e(LOG_TAG, "encryptStr Unable to create and chmod log dir");
                }

                PrintWriter writer = new PrintWriter(
                        new BufferedWriter(
                                new FileWriter(
                                        pathVars.get().getAppDataDir() + "/logs/EncryptedStr.txt", true
                                )
                        )
                );
                writer.println(text);
                writer.println(Base64.encodeToString(encrypted, Base64.DEFAULT));
                writer.println("********************");
                writer.close();
            }


        } catch (Exception e) {
            Log.e(LOG_TAG, "encryptStr Failed " + e.getMessage() + " " + e.getCause());
        }
    }

    public String getWrongSign() {
        return context.getString(R.string.encoded).trim();
    }

    public String getAppSignature() throws Exception {
        if (apkSignature == null) {
            synchronized (Verifier.class) {
                if (apkSignature == null) {
                    apkSignature = getApkSignatureZip();
                }
            }
        }
        return apkSignature;
    }

}
