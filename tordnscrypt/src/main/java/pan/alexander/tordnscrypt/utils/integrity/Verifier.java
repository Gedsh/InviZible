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

    Copyright 2019-2026 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.utils.integrity;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Base64;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.settings.PathVars;

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logi;


@Singleton
public class Verifier {
    private static final byte[] APK_SIG_BLOCK_MAGIC = "APK Sig Block 42".getBytes(StandardCharsets.US_ASCII);
    private static final int APK_SIG_SCHEME_V2_ID = 0x7109871a;
    private static final int APK_SIG_SCHEME_V3_ID = 0xf05368c0;
    private static final int APK_SIG_SCHEME_V31_ID = 0x1b93ad61;
    private static final int PROOF_OF_ROTATION_ATTR_ID = 0x3ba06f8c;

    public Lazy<PathVars> pathVars;
    public Context context;
    private volatile String apkSignature;
    private volatile String packageSignature;

    @Inject
    public Verifier(Context context, Lazy<PathVars> pathVars) {
        this.context = context;
        this.pathVars = pathVars;
    }

    public String getAppSignature() throws Exception {
        if (isFdroidBuild()) return "";
        String signature = apkSignature;
        if (signature == null) {
            synchronized (this) {
                signature = apkSignature;
                if (signature == null) {
                    signature = getApkSignatureZip();
                    if (signature == null) {
                        return getApkSignature();
                    }
                    apkSignature = signature;
                }
            }
        }
        return signature;
    }

    public String getApkSignature() throws Exception {
        if (isFdroidBuild()) return "";
        String signature = packageSignature;
        if (signature == null) {
            synchronized (this) {
                signature = packageSignature;
                if (signature == null) {
                    signature = getOldestPackageSignature();
                    packageSignature = signature;
                }
            }
        }
        return signature;
    }

    public synchronized void invalidateSignatureCache() {
        apkSignature = null;
        packageSignature = null;
    }

    @SuppressWarnings("deprecation")
    private String getOldestPackageSignature() throws Exception {
        PackageManager packageManager = this.context.getPackageManager();
        String packageName = this.context.getPackageName();

        Signature[] signatureArray;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo packageInfo = packageManager.getPackageInfo(
                    packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signingInfo = packageInfo.signingInfo;
            if (signingInfo == null || signingInfo.hasMultipleSigners()) {
                throw new SecurityException("Missing signing history or multiple APK signers");
            }
            signatureArray = signingInfo.getSigningCertificateHistory();
        } else {
            signatureArray = packageManager.getPackageInfo(
                    packageName, PackageManager.GET_SIGNATURES).signatures;
            if (signatureArray != null && signatureArray.length > 1) {
                throw new SecurityException("Multiple APK signers");
            }
        }
        if (signatureArray == null || signatureArray.length == 0 || signatureArray[0] == null) {
            throw new SecurityException("Missing APK signing certificate");
        }

        byte[] byteSign = signatureArray[0].toByteArray();
        byteSign = CertificateFactory.getInstance("X509").generateCertificate(new ByteArrayInputStream(byteSign)).getEncoded();
        //String strSign = new String(Base64.encode(MessageDigest.getInstance("md5").digest(byteSign), 19));
        return Base64.encodeToString(MessageDigest.getInstance("md5").digest(byteSign), Base64.DEFAULT);
    }

    public String decryptStr(String text, String key, String vector) throws Exception {
        if (isFdroidBuild()) {
            return new String(Base64.decode(text.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT));
        }
        key = key.substring(key.length() - 16);
        // Create key and cipher
        Key aesKey = new SecretKeySpec(key.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        // decrypt the text
        byte[] ivBytes = vector.substring(vector.length() - 16).getBytes();
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new IvParameterSpec(ivBytes));
        byte[] decrypted = Base64.decode(text.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        return new String(cipher.doFinal(decrypted));
    }

    public void encryptStr(String text, String key, String vector) {

        try {
            if (TopFragment.debug) {
                if (isFdroidBuild()) return;
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
                    logi("encryptStr log dir created");
                } else {
                    loge("encryptStr Unable to create and chmod log dir");
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
            loge("encryptStr Failed", e);
        }
    }

    public String getWrongSign() {
        return context.getString(R.string.encoded).trim();
    }

    private boolean isFdroidBuild() {
        return "fd".equals(context.getString(R.string.appVersion));
    }

    private String getApkSignatureZip() throws Exception {
        try {
            File apkFile = new File(context.getApplicationInfo().sourceDir);
            byte[] certDer = extractSigningCertFromApk(apkFile);
            if (certDer != null) {
                byte[] md5Digest = MessageDigest.getInstance("MD5").digest(certDer);
                return Base64.encodeToString(md5Digest, Base64.DEFAULT);
            }
        } catch (Exception e) {
            loge("Verifier unable to get signature from zip. Use the conventional method instead.");
        }
        return null;
    }

    private static byte[] extractSigningCertFromApk(File apkFile) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(apkFile, "r")) {
            long fileLen = raf.length();
            if (fileLen < 22) return null;

            // 1. APKs normally have no ZIP comment, so try the final 22 bytes first.
            byte[] tail = new byte[22];
            raf.seek(fileLen - tail.length);
            raf.readFully(tail);
            int eocdOffsetInTail = findEocdOffset(tail);
            if (eocdOffsetInTail == -1) {
                // A ZIP comment can contain up to 65535 bytes.
                tail = new byte[(int) Math.min(fileLen, 65535 + 22)];
                raf.seek(fileLen - tail.length);
                raf.readFully(tail);
                eocdOffsetInTail = findEocdOffset(tail);
            }

            if (eocdOffsetInTail == -1) return null;

            // 2. Read Central Directory offset (uint32 at EOCD + 16)
            ByteBuffer eocdBuf = ByteBuffer.wrap(tail, eocdOffsetInTail, 22)
                    .order(ByteOrder.LITTLE_ENDIAN);
            long cdOffset = eocdBuf.getInt(eocdOffsetInTail + 16) & 0xFFFFFFFFL;
            if (cdOffset < 32 || cdOffset > fileLen) return null;

            // 3. Read 24-byte footer right before Central Directory: [8-byte size][16-byte magic]
            byte[] footer = new byte[24];
            raf.seek(cdOffset - 24);
            raf.readFully(footer);

            for (int i = 0; i < 16; i++) {
                if (footer[8 + i] != APK_SIG_BLOCK_MAGIC[i]) {
                    return null; // Not signed with v2/v3
                }
            }

            long blockSizeFooter = ByteBuffer.wrap(footer, 0, 8)
                    .order(ByteOrder.LITTLE_ENDIAN).getLong();
            if (blockSizeFooter < 24 || blockSizeFooter > cdOffset - 8
                    || blockSizeFooter > Integer.MAX_VALUE - 8) return null;
            long blockStart = cdOffset - 8 - blockSizeFooter;
            if (blockStart < 0) return null;

            // 4. Read the entire APK Signing Block and verify header size matches footer size
            int pairsAreaSize = (int) (blockSizeFooter - 24);
            byte[] blockData = new byte[8 + pairsAreaSize];
            raf.seek(blockStart);
            raf.readFully(blockData);

            ByteBuffer buf = ByteBuffer.wrap(blockData).order(ByteOrder.LITTLE_ENDIAN);
            long blockSizeHeader = buf.getLong();
            if (blockSizeHeader != blockSizeFooter) return null;

            // 5. Iterate through length-prefixed ID-value pairs
            ByteBuffer v2Block = null;
            ByteBuffer v3Block = null;
            ByteBuffer v31Block = null;

            while (buf.remaining() >= 12) {
                long pairLen = buf.getLong();
                if (pairLen < 4 || pairLen > buf.remaining()) break;

                int id = buf.getInt();
                int valueLen = (int) (pairLen - 4);

                if (id == APK_SIG_SCHEME_V31_ID || id == APK_SIG_SCHEME_V3_ID
                        || id == APK_SIG_SCHEME_V2_ID) {
                    ByteBuffer value = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
                    value.limit(valueLen);
                    if (id == APK_SIG_SCHEME_V31_ID) {
                        v31Block = value;
                    } else if (id == APK_SIG_SCHEME_V3_ID) {
                        v3Block = value;
                    } else {
                        v2Block = value;
                    }
                }
                buf.position(buf.position() + valueLen);
            }

            // v3.1 contains the latest rotation history; pair ordering is irrelevant.
            if (v31Block != null) return parseOldestCertFromScheme(v31Block, true);
            if (v3Block != null) return parseOldestCertFromScheme(v3Block, true);
            return v2Block == null ? null : parseOldestCertFromScheme(v2Block, false);
        }
    }

    private static int findEocdOffset(byte[] tail) {
        for (int i = tail.length - 22; i >= 0; i--) {
            if (tail[i] == 0x50 && tail[i + 1] == 0x4b
                    && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
                int commentLength = (tail[i + 20] & 0xff) | ((tail[i + 21] & 0xff) << 8);
                if (i + 22 + commentLength == tail.length) return i;
            }
        }
        return -1;
    }

    private static byte[] parseOldestCertFromScheme(ByteBuffer buf, boolean v3) {
        ByteBuffer signers = readLengthPrefixedSlice(buf);
        ByteBuffer signer = readLengthPrefixedSlice(signers);
        ByteBuffer signedData = readLengthPrefixedSlice(signer);
        readLengthPrefixedSlice(signedData); // Digests
        ByteBuffer certificates = readLengthPrefixedSlice(signedData);
        ByteBuffer certificate = readLengthPrefixedSlice(certificates);

        if (v3) {
            signedData.getInt(); // minSDK
            signedData.getInt(); // maxSDK
            ByteBuffer attributes = readLengthPrefixedSlice(signedData);
            while (attributes.hasRemaining()) {
                ByteBuffer attribute = readLengthPrefixedSlice(attributes);
                if (attribute.getInt() == PROOF_OF_ROTATION_ATTR_ID) {
                    // Proof-of-rotation version 1 stores levels oldest first.
                    if (attribute.getInt() != 1) {
                        throw new IllegalArgumentException("Unsupported signing history version");
                    }
                    ByteBuffer oldestLevel = readLengthPrefixedSlice(attribute);
                    ByteBuffer oldestSignedData = readLengthPrefixedSlice(oldestLevel);
                    certificate = readLengthPrefixedSlice(oldestSignedData);
                    break;
                }
            }
        }

        if (!certificate.hasRemaining()) {
            throw new IllegalArgumentException("Empty signing certificate");
        }
        byte[] certDer = new byte[certificate.remaining()];
        certificate.get(certDer);
        return certDer;
    }

    private static ByteBuffer readLengthPrefixedSlice(ByteBuffer source) {
        int length = source.getInt();
        if (length < 0 || length > source.remaining()) {
            throw new IllegalArgumentException("Invalid signing block field length");
        }
        ByteBuffer field = source.slice().order(ByteOrder.LITTLE_ENDIAN);
        field.limit(length);
        source.position(source.position() + length);
        return field;
    }

}
