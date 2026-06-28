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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.about;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;

import static pan.alexander.tordnscrypt.assistance.AccelerateDevelop.accelerated;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.GP_DATA;

import javax.inject.Inject;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.BuildConfig;
import pan.alexander.tordnscrypt.LangAppCompatActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.settings.PathVars;

public class AboutActivity extends LangAppCompatActivity implements View.OnClickListener {

    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;

    private ClipboardManager clipboard;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {// API 5+ solution
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(R.string.drawer_menu_about);

        TextView tvVersion = findViewById(R.id.tvPremiumStatus);
        if (pathVars.get().getAppVersion().endsWith("p")) {
            if (accelerated) {
                tvVersion.setText(R.string.premium_version);
            } else if (!preferenceRepository.get().getStringPreference(GP_DATA).isEmpty()) {
                tvVersion.setText(R.string.refunded_version);
                tvVersion.setOnClickListener(this);
            }
        } else if (pathVars.get().getAppVersion().startsWith("p")) {
            tvVersion.setText(R.string.premium_version);
        }

        TextView tvHelpBuildNo = findViewById(R.id.tvHelpBuildNo);
        TextView tvHelpBuildDate = findViewById(R.id.tvHelpBuildDate);
        tvHelpBuildNo.setText(BuildConfig.VERSION_NAME);

        Date buildDate = BuildConfig.BUILD_TIME;
        tvHelpBuildDate.setText(DateFormat.getDateInstance(DateFormat.SHORT).format(buildDate));

        TextView tvDonateTitle = findViewById(R.id.tvDonateTitle);
        TextView tvDonateBitcoin = findViewById(R.id.tvDonateBitcoin);
        TextView tvDonateTon = findViewById(R.id.tvDonateTon);
        TextView tvDonateMonero = findViewById(R.id.tvDonateMonero);
        if (pathVars.get().getAppVersion().endsWith("p")) {
            tvDonateTitle.setVisibility(View.GONE);
            tvDonateBitcoin.setVisibility(View.GONE);
            tvDonateTon.setVisibility(View.GONE);
            tvDonateMonero.setVisibility(View.GONE);
        } else {
            String btc = getBitcoinUri();
            String ton = getTonUri();
            String xmr = getMoneroUri();
            if (btc.isEmpty() && ton.isEmpty() && xmr.isEmpty()) {
                tvDonateTitle.setVisibility(View.GONE);
            }
            if (!btc.isEmpty()) {
                tvDonateBitcoin.setText(getBitcoinUri());
                tvDonateBitcoin.setOnClickListener(this);
            } else {
                tvDonateBitcoin.setVisibility(View.GONE);
            }
            if (!ton.isEmpty()) {
                tvDonateTon.setText(getTonUri());
                tvDonateTon.setOnClickListener(this);
            } else {
                tvDonateTon.setVisibility(View.GONE);
            }
            if (!xmr.isEmpty()) {
                tvDonateMonero.setText(getMoneroUri());
                tvDonateMonero.setOnClickListener(this);
            } else {
                tvDonateMonero.setVisibility(View.GONE);
            }
        }

        findViewById(R.id.dnscryptLicense).setOnClickListener(this);
        findViewById(R.id.torLicense).setOnClickListener(this);
        findViewById(R.id.itpdLicense).setOnClickListener(this);
        findViewById(R.id.libsuperuserLicense).setOnClickListener(this);
        findViewById(R.id.androidShellLicense).setOnClickListener(this);
        findViewById(R.id.netGuardLicense).setOnClickListener(this);
        findViewById(R.id.filepickerLicense).setOnClickListener(this);
        findViewById(R.id.busyboxLicense).setOnClickListener(this);
    }

    @SuppressLint("InflateParams")
    public void showLicense(int title, int resource, boolean isApache) {

        InputStream raw = getResources().openRawResource(resource);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        int i;
        try
        {
            i = raw.read();
            while (i != -1)
            {
                byteArrayOutputStream.write(i);
                i = raw.read();
            }
            raw.close();
            byteArrayOutputStream.close();

            String outputText = byteArrayOutputStream.toString();

            if (isApache) {
                raw = getResources().openRawResource(R.raw.apache_license);
                byteArrayOutputStream = new ByteArrayOutputStream();

                i = raw.read();
                while (i != -1)
                {
                    byteArrayOutputStream.write(i);
                    i = raw.read();
                }
                raw.close();
                byteArrayOutputStream.close();

                outputText = outputText + byteArrayOutputStream.toString();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            LayoutInflater layoutInflater = getLayoutInflater();
            View inflatedView;
            try {
                inflatedView = layoutInflater.inflate(R.layout.licenses_scrollable_text, null, false);
            } catch (Exception e) {
                loge("AboutActivity showLicense", e);
                throw e;
            }
            TextView licenseText = inflatedView.findViewById(R.id.tvLicense);
            licenseText.setText(outputText);

            builder.setTitle(title);

            builder.setPositiveButton(R.string.ok, (dialogInterface, i1) -> dialogInterface.dismiss());

            builder.setView(inflatedView);

            builder.show();
        }
        catch (IOException e)
        {
            loge("AboutActivity", e);
            try {
                raw.close();
                byteArrayOutputStream.close();
            } catch (IOException e1) {
                loge("AboutActivity", e1);
            }
        }
    }

    private String getBitcoinUri() {
        try {
            return decodeBase64(getString(R.string.btc).trim());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String getTonUri() {
        try {
            return decodeBase64(getString(R.string.ton).trim());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String getMoneroUri() {
        try {
            return decodeBase64(getString(R.string.xmr).trim());
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String decodeBase64(final String base64) {
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        String unwrap = new String(bytes, StandardCharsets.UTF_8);
        bytes = Base64.decode(unwrap, Base64.DEFAULT);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void copyToClipboard(String text) {
        String label = "Donate";
        String clipboardText = text;
        if (text.contains(":")) {
            label = text.substring(0, text.indexOf(":")).toUpperCase();
            clipboardText = text.substring(text.indexOf(":") + 1);
        }
        ClipData clip = ClipData.newPlainText(label, clipboardText);
        clipboard.setPrimaryClip(clip);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.tvDonateBitcoin) {
            copyToClipboard(getBitcoinUri());
        } else if (id == R.id.tvDonateMonero) {
            copyToClipboard(getMoneroUri());
        } else if (id == R.id.tvDonateTon) {
            copyToClipboard(getTonUri());
        } else if (id == R.id.dnscryptLicense) {
            showLicense(R.string.about_license_dnscrypt, R.raw.dnscrypt_license, false);
        } else if (id == R.id.torLicense) {
            showLicense(R.string.about_license_tor, R.raw.tor_license, false);
        } else if (id == R.id.itpdLicense) {
            showLicense(R.string.about_license_itpd, R.raw.itpd_license, false);
        } else if (id == R.id.libsuperuserLicense) {
            showLicense(R.string.about_license_libsupeuser, R.raw.libsuperuser_license, true);
        } else if (id == R.id.androidShellLicense) {
            showLicense(R.string.about_license_AndroidShell, R.raw.androidshell_license, true);
        } else if (id == R.id.netGuardLicense) {
            showLicense(R.string.about_license_NetGuard, R.raw.netguard_license, false);
        } else if (id == R.id.filepickerLicense) {
            showLicense(R.string.about_license_filepicker, R.raw.filepicker_license, true);
        } else if (id == R.id.busyboxLicense) {
            showLicense(R.string.about_license_busybox, R.raw.busybox_license, false);
        }
    }
}
