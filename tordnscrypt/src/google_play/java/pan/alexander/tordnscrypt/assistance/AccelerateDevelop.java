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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

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
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.utils.PrefManager;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class AccelerateDevelop implements BillingClientStateListener {
    public final static String mSkuId = "invizible_premium_version";

    public static boolean accelerated = false;

    private MainActivity activity;
    private BillingClient mBillingClient;
    private Map<String, SkuDetails> mSkuDetailsMap = new HashMap<>();
    private boolean billingServiceConnected = false;

    public AccelerateDevelop(MainActivity activity) {
        this.activity = activity;
    }

    public void initBilling() {
        mBillingClient = BillingClient.newBuilder(activity)
                .enablePendingPurchases()
                .setListener(new PurchasesUpdatedListener() {
                    @Override
                    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> purchasesList) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchasesList != null) {
                            Log.i(LOG_TAG, "Purchases are updated");
                            handlePurchases(purchasesList);
                        }
                    }
                }).build();

        new Thread(() -> {
            mBillingClient.startConnection(AccelerateDevelop.this);
        }).start();
    }

    public void launchBilling(String skuId) {
        if (billingServiceConnected) {

            if (!mSkuDetailsMap.isEmpty()) {

                Log.i(LOG_TAG, "Launch billing");

                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                        .setSkuDetails(mSkuDetailsMap.get(skuId))
                        .build();
                mBillingClient.launchBillingFlow(activity, billingFlowParams);
            } else {
                Log.w(LOG_TAG, "Launch billing but details map is empty");
            }

        } else {
            Log.w(LOG_TAG, "Launch billing but billing client is disconnected");
            mBillingClient.startConnection(this);
        }
    }

    @Override
    public void onBillingSetupFinished(BillingResult billingResult) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            billingServiceConnected = true;
            Log.i(LOG_TAG, "Billing setup is finished");
            //below you can query information about products and purchase
            querySkuDetails(); //query for products
            List<Purchase> purchasesList = queryPurchases(); //query for purchases

            //if the purchase has already been made to give the goods
            handlePurchases(purchasesList);
        }
    }

    @Override
    public void onBillingServiceDisconnected() {
        //here when something went wrong, e.g. no internet connection
        billingServiceConnected = false;

        Log.w(LOG_TAG, "Billing service disconnected");

        String signedData = new PrefManager(activity).getStrPref("gpData");
        String signature = new PrefManager(activity).getStrPref("gpSign");

        if (!signedData.isEmpty() && !signature.isEmpty() && verifyValidSignature(signedData, signature)) {
            payComplete();
        }
    }

    private void querySkuDetails() {

        if (!billingServiceConnected) {
            mBillingClient.startConnection(this);
            return;
        }

        SkuDetailsParams.Builder skuDetailsParamsBuilder = SkuDetailsParams.newBuilder();
        List<String> skuList = new ArrayList<>();
        skuList.add(mSkuId);
        skuDetailsParamsBuilder.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);

        mBillingClient.querySkuDetailsAsync(skuDetailsParamsBuilder.build(), new SkuDetailsResponseListener() {
            @Override
            public void onSkuDetailsResponse(BillingResult billingResult, List<SkuDetails> skuDetailsList) {
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

    private List<Purchase> queryPurchases() {
        Purchase.PurchasesResult purchasesResult = mBillingClient.queryPurchases(BillingClient.SkuType.INAPP);
        return purchasesResult.getPurchasesList();
    }

    private void handlePurchases(@NonNull List<Purchase> purchasesList) {
        if (!billingServiceConnected) {
            mBillingClient.startConnection(this);
            return;
        }

        if (purchasesList.isEmpty()) {
            String signedData = new PrefManager(activity).getStrPref("gpData");
            String signature = new PrefManager(activity).getStrPref("gpSign");

            if (!signedData.isEmpty() && !signature.isEmpty() && verifyValidSignature(signedData, signature)) {
                Log.w(LOG_TAG, "Purchases list is empty but saved signature is correct. Allowing...");
                payComplete();
            } else {
                Log.w(LOG_TAG, "Purchases list is empty. Skipping...");
            }

            return;
        }

        for (int i = 0; i < purchasesList.size(); i++) {
            Purchase purchase = purchasesList.get(i);
            String purchaseId = purchase.getSku();
            int purchaseState = purchase.getPurchaseState();
            boolean acknowledged = purchase.isAcknowledged();

            if(TextUtils.equals(mSkuId, purchaseId) && purchaseState == Purchase.PurchaseState.PURCHASED) {

                if (!verifyValidSignature(purchase.getOriginalJson(), purchase.getSignature())) {
                    if (!acknowledged && activity != null) {
                        activity.runOnUiThread(() -> {
                            DialogFragment dialogFragment = NotificationDialogFragment.newInstance(R.string.wrong_purchase_signature_gp);
                            if (dialogFragment != null && activity != null) {
                                dialogFragment.show(activity.getSupportFragmentManager(), "wrong_purchase_signature");
                            }
                        });
                    }

                    Log.w(LOG_TAG, "Got a purchase: " + purchase + "; but signature is bad. Skipping...");
                    return;
                }

                if (!acknowledged) {
                    AcknowledgePurchaseParams acknowledgePurchaseParams =
                            AcknowledgePurchaseParams.newBuilder()
                                    .setPurchaseToken(purchase.getPurchaseToken())
                                    .build();
                    mBillingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                        @Override
                        public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                Log.i(LOG_TAG, "Purchase is acknowledged " + purchase.getSku());

                                if (activity != null) {
                                    activity.runOnUiThread(() -> {

                                        if (activity == null) {
                                            return;
                                        }

                                        String thanks = activity.getString(R.string.thanks_for_donate);
                                        if (!thanks.contains(".")) {
                                            return;
                                        }

                                        DialogFragment dialogFragment = NotificationDialogFragment.newInstance(
                                                thanks.substring(0, thanks.indexOf(".")));
                                        if (dialogFragment != null && activity != null) {
                                            dialogFragment.show(activity.getSupportFragmentManager(), "thanks_for_donate");
                                        }
                                    });
                                }
                            } else {
                                Log.i(LOG_TAG, "Purchase not acknowledged " + purchase.getSku() + billingResult.getDebugMessage());
                            }
                        }
                    });
                }

                payComplete();
            } else if (purchaseState == Purchase.PurchaseState.PENDING) {
                Log.i(LOG_TAG, "Purchase is pending " + purchase.getSku());

                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        activity, activity.getText(R.string.pending_purchase).toString()
                                + " " + purchase.getSku()
                                + " " + purchase.getOrderId(), "pending_purchase");
                if (notificationHelper != null) {
                    notificationHelper.show(activity.getSupportFragmentManager(), NotificationHelper.TAG_HELPER);
                }
            }
        }
    }

    private boolean verifyValidSignature(String signedData, String signature) {
        new PrefManager(activity).setStrPref("gpData", signedData);
        new PrefManager(activity).setStrPref("gpSign", signature);

        boolean result = false;
        try {
            PublicKey pkey = getAPKKey();
            Signature sig = Signature.getInstance("SHA1withRSA");
            //Signature sig = Signature.getInstance("RSASSA-PKCS1-v1_5");
            sig.initVerify(pkey);
            sig.update(signedData.getBytes());

            if(sig.verify(Base64.decode(signature, Base64.DEFAULT))) {
                result = true;
            } else {
                Log.e(LOG_TAG, "AccelerateDevelop signature is wrong " + signature);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "AccelerateDevelop verifyValidSignature Exception " + e.getMessage() + " " + e.getCause());
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

    private PublicKey getAPKKey() throws Exception{
        byte[] decodedKey = Base64.decode(activity.getString(R.string.gp_property), Base64.DEFAULT);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
    }

    private void payComplete() {
        accelerated = true;
        Log.i(LOG_TAG, "Payment completed");
    }
}
