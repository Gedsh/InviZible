package pan.alexander.tordnscrypt.assistance;

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

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import org.jetbrains.annotations.NotNull;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Cipher;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class AccelerateDevelop implements BillingClientStateListener {
    public final static String mSkuId = "invizible_premium_version";

    public static volatile boolean accelerated = true;

    private MainActivity activity;
    private final Context context;
    private final ReentrantLock lock = new ReentrantLock();
    private BillingClient mBillingClient;
    private final Map<String, SkuDetails> mSkuDetailsMap = new HashMap<>();
    private volatile boolean billingServiceConnected = false;
    private volatile String signedData;
    private volatile String signature;

    public AccelerateDevelop(MainActivity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
        this.signedData = new PrefManager(activity).getStrPref("gpData");
        this.signature = new PrefManager(activity).getStrPref("gpSign");
    }

    public void removeActivity() {
        this.activity = null;
    }

    public void initBilling() {
        mBillingClient = BillingClient.newBuilder(context)
                .enablePendingPurchases()
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> purchasesList) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchasesList != null) {
                            Log.i(LOG_TAG, "Purchases are updated");
                            handlePurchases(purchasesList);
                        }
                    }
                }).build();

        startBillingConnection();
    }

    public void launchBilling(String skuId) {
        if (billingServiceConnected) {

            SkuDetails skuDetails = mSkuDetailsMap.get(skuId);

            if (skuDetails != null && activity != null) {

                Log.i(LOG_TAG, "Launch billing");

                new PrefManager(context).setBoolPref("helper_no_show_pending_purchase", false);

                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(skuDetails)
                        .build();
                mBillingClient.launchBillingFlow(activity, billingFlowParams);
            } else {
                Log.w(LOG_TAG, "Launch billing but details map is empty");
            }

        } else {
            Log.w(LOG_TAG, "Launch billing but billing client is disconnected");
            startBillingConnection();
        }
    }

    @Override
    public void onBillingSetupFinished(BillingResult billingResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            billingServiceConnected = true;
            Log.i(LOG_TAG, "Billing setup is finished");
            //below you can query information about products and purchase
            querySkuDetails(); //query for products
            queryPurchases(); //query for purchases
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        //here when something went wrong, e.g. no internet connection
        billingServiceConnected = false;

        Log.w(LOG_TAG, "Billing service disconnected");

        if (!signedData.isEmpty() && !signature.isEmpty() && verifyValidSignature(signedData, signature)) {
            Log.w(LOG_TAG, "BillingServiceDisconnected but saved signature is correct. Allowing...");
            payComplete();
        } else {
            Log.w(LOG_TAG, "BillingServiceDisconnected. Skipping...");
            noPayment();
        }
    }

    private void querySkuDetails() {

        if (!billingServiceConnected) {
            Log.w(LOG_TAG, "QuerySkuDetails but billing client is disconnected");
            startBillingConnection();
            return;
        }

        SkuDetailsParams.Builder skuDetailsParamsBuilder = SkuDetailsParams.newBuilder();
        List<String> skuList = new ArrayList<>();
        skuList.add(mSkuId);
        skuDetailsParamsBuilder.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);

        mBillingClient.querySkuDetailsAsync(skuDetailsParamsBuilder.build(), new SkuDetailsResponseListener() {
            @Override
            public void onSkuDetailsResponse(@NonNull BillingResult billingResult, List<SkuDetails> skuDetailsList) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    if (!skuDetailsList.isEmpty()) {
                        for (SkuDetails skuDetails : skuDetailsList) {
                            mSkuDetailsMap.put(skuDetails.getSku(), skuDetails);
                        }
                    } else {
                        Log.w(LOG_TAG, "Query SKU details is OK, but SKU list is empty " + billingResult.getDebugMessage());
                    }

                } else {
                    Log.w(LOG_TAG, "Query SKU details warning " + billingResult.getResponseCode() + " " + billingResult.getDebugMessage());
                }
            }
        });
    }

    private void queryPurchases() {
        mBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(@NonNull @NotNull BillingResult billingResult, @NonNull @NotNull List<Purchase> purchasesList) {
                //if the purchase has already been made to give the goods
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(purchasesList);
                } else if (!signedData.isEmpty() && !signature.isEmpty() && verifyValidSignature(signedData, signature)) {
                    Log.w(LOG_TAG, "Query purchases fault: "+ billingResult.getDebugMessage() + " But saved signature is correct. Allowing...");
                    payComplete();
                } else {
                    Log.w(LOG_TAG, "Query purchases fault: " + billingResult.getDebugMessage() + " Skipping...");
                    noPayment();
                }
            }
        });
    }

    private void handlePurchases(@NonNull List<Purchase> purchasesList) {
        if (!billingServiceConnected) {
            Log.w(LOG_TAG, "HandlePurchases but billing client is disconnected");
            startBillingConnection();
            return;
        }

        if (purchasesList.isEmpty()) {

            if (!signedData.isEmpty() && !signature.isEmpty() && verifyValidSignature(signedData, signature)) {
                Log.w(LOG_TAG, "Purchases list is empty but saved signature is correct. Allowing...");
                payComplete();
            } else {
                Log.w(LOG_TAG, "Purchases list is empty. Skipping...");
                noPayment();
            }

            return;
        }

        for (int i = 0; i < purchasesList.size(); i++) {

            try {

                Purchase purchase = purchasesList.get(i);

                if (purchase == null) {
                    continue;
                }

                List<String> purchaseIds = purchase.getSkus();
                int purchaseState = purchase.getPurchaseState();
                boolean acknowledged = purchase.isAcknowledged();

                if (purchaseIds.contains(mSkuId) && purchaseState == Purchase.PurchaseState.PURCHASED) {

                    if (!verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
                        if (!acknowledged && activity != null) {
                            activity.runOnUiThread(() -> {
                                DialogFragment dialogFragment = NotificationDialogFragment.newInstance(R.string.wrong_purchase_signature_gp);
                                if (!activity.isDestroyed()) {
                                    dialogFragment.show(activity.getSupportFragmentManager(), "wrong_purchase_signature");
                                }
                            });
                        }

                        Log.w(LOG_TAG, "Got a purchase: " + purchase + "; but signature is bad. Skipping...");
                        noPayment();
                        return;
                    }

                    if (!acknowledged) {
                        AcknowledgePurchaseParams acknowledgePurchaseParams =
                                AcknowledgePurchaseParams.newBuilder()
                                        .setPurchaseToken(purchase.getPurchaseToken())
                                        .build();
                        mBillingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                            @Override
                            public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                    Log.i(LOG_TAG, "Purchase is acknowledged " + purchase.getSkus());

                                    if (activity != null) {
                                        activity.runOnUiThread(() -> {

                                            String thanks = activity.getString(R.string.thanks_for_donate_gp);
                                            if (!thanks.contains(".")) {
                                                return;
                                            }

                                            DialogFragment dialogFragment = NotificationDialogFragment.newInstance(
                                                    thanks.substring(0, thanks.indexOf(".")));
                                            if (!activity.isDestroyed()) {
                                                dialogFragment.show(activity.getSupportFragmentManager(), "thanks_for_donate");
                                            }
                                        });
                                    }
                                } else {
                                    Log.i(LOG_TAG, "Purchase is not acknowledged " + purchase.getSkus() + " " + billingResult.getDebugMessage());
                                }
                            }
                        });
                    }

                    payComplete();
                    return;
                } else if (purchaseIds.contains(mSkuId) && purchaseState == Purchase.PurchaseState.PENDING) {
                    Log.i(LOG_TAG, "Purchase is pending " + purchase.getSkus());

                    if (activity != null) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                context, activity.getString(R.string.pending_purchase)
                                        + " " + TextUtils.join(", ", purchase.getSkus())
                                        + " " + purchase.getOrderId(), "pending_purchase");
                        if (notificationHelper != null && !activity.isDestroyed()) {
                            notificationHelper.show(activity.getSupportFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                    payComplete();
                    return;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "AccelerateDevelop handlePurchase Exception " + e.getMessage() + " " + e.getCause());
            }
        }

        noPayment();
    }

    private void startBillingConnection() {
        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                if (lock.tryLock()) {
                    mBillingClient.startConnection(AccelerateDevelop.this);

                    TimeUnit.SECONDS.sleep(1);

                    if (!billingServiceConnected && !signedData.isEmpty() && !signature.isEmpty()
                            && verifyValidSignature(signedData, signature)) {
                        Log.w(LOG_TAG, "BillingService connection failed but saved signature is correct. Allowing...");
                        payComplete();
                    } else if (!billingServiceConnected) {
                        Log.w(LOG_TAG, "BillingService connection failed. Skipping...");
                        noPayment();
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "AccelerateDevelop startBillingConnection Exception "
                        + e.getMessage() + " " + e.getCause() + " " + Arrays.toString(e.getStackTrace()));
            } finally {
                lock.unlock();
            }
        });
    }

    private boolean verifyValidSignature(String signedData, String signature) {
        boolean result = false;

        try {
            PublicKey pkey = getAPKKey();
            Signature sig = Signature.getInstance("SHA1withRSA");
            //Signature sig = Signature.getInstance("RSASSA-PKCS1-v1_5");
            sig.initVerify(pkey);
            sig.update(signedData.getBytes());

            if (sig.verify(Base64.decode(signature, Base64.DEFAULT))) {
                result = true;
            } else {
                Log.e(LOG_TAG, "AccelerateDevelop signature is wrong " + signature);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "AccelerateDevelop verifyValidSignature Exception " + e.getMessage() + " " + e.getCause());
        }

        if (result) {
            this.signedData = signedData;
            this.signature = signature;
            new PrefManager(context).setStrPref("gpData", signedData);
            new PrefManager(context).setStrPref("gpSign", signature);
        }

        return result;
    }

    private byte[] RSADecrypt(final String encryptedText, final Key key) {
        byte[] result = new byte[]{0};
        try {
            byte[] encryptedBytes = Base64.decode(encryptedText, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            result = cipher.doFinal(encryptedBytes);
        } catch (Exception e) {
            Log.e(LOG_TAG, "RSADecrypt function fault " + e.getMessage());
        }
        return result;
    }

    private PublicKey getAPKKey() throws Exception {
        byte[] decodedKey = Base64.decode(context.getString(R.string.gp_property), Base64.DEFAULT);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
    }

    private void payComplete() {
        accelerated = true;
        Log.i(LOG_TAG, "Payment completed");
    }

    private void noPayment() {
        accelerated = false;
        Log.i(LOG_TAG, "No acceleration");
    }
}
