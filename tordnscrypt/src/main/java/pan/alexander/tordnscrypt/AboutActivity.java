package pan.alexander.tordnscrypt;
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

import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class AboutActivity extends LangAppCompatActivity implements View.OnClickListener {


    @SuppressLint({"NewApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
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

        TextView tvHelpBuildNo = findViewById(R.id.tvHelpBuildNo);
        TextView tvHelpBuildDate = findViewById(R.id.tvHelpBuildDate);
        tvHelpBuildNo.setText(BuildConfig.VERSION_NAME);

        Date buildDate = BuildConfig.BUILD_TIME;
        tvHelpBuildDate.setText(DateFormat.getDateInstance(DateFormat.SHORT).format(buildDate));

        findViewById(R.id.dnscryptLicense).setOnClickListener(this);
        findViewById(R.id.torLicense).setOnClickListener(this);
        findViewById(R.id.itpdLicense).setOnClickListener(this);
        findViewById(R.id.libsuperuserLicense).setOnClickListener(this);
        findViewById(R.id.androidShellLicense).setOnClickListener(this);
        findViewById(R.id.netGuardLicense).setOnClickListener(this);
        findViewById(R.id.filepickerLicense).setOnClickListener(this);
        findViewById(R.id.busyboxLicense).setOnClickListener(this);
    }

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

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomAlertDialogTheme);
            LayoutInflater layoutInflater = getLayoutInflater();
            View inflatedView = layoutInflater.inflate(R.layout.licenses_scrollable_text, null, false);
            TextView licenseText = inflatedView.findViewById(R.id.tvLicense);
            licenseText.setText(outputText);

            builder.setTitle(title);

            builder.setPositiveButton(R.string.ok, (dialogInterface, i1) -> dialogInterface.dismiss());

            builder.setView(inflatedView);

            builder.show();
        }
        catch (IOException e)
        {
            Log.e(LOG_TAG, "AboutActivity exception " + e.getMessage() + " " + e.getCause());
            try {
                raw.close();
                byteArrayOutputStream.close();
            } catch (IOException e1) {
                Log.e(LOG_TAG, "AboutActivity exception " + e1.getMessage() + " " + e1.getCause());
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.dnscryptLicense:
                showLicense(R.string.about_license_dnscrypt,R.raw.dnscrypt_license,false);
                break;
            case R.id.torLicense:
                showLicense(R.string.about_license_tor,R.raw.tor_license,false);
                break;
            case R.id.itpdLicense:
                showLicense(R.string.about_license_itpd,R.raw.itpd_license,false);
                break;
            case R.id.libsuperuserLicense:
                showLicense(R.string.about_license_libsupeuser,R.raw.libsuperuser_license,true);
                break;
            case R.id.androidShellLicense:
                showLicense(R.string.about_license_AndroidShell,R.raw.androidshell_license,true);
                break;
            case R.id.netGuardLicense:
                showLicense(R.string.about_license_NetGuard,R.raw.netguard_license,false);
                break;
            case R.id.filepickerLicense:
                showLicense(R.string.about_license_filepicker,R.raw.filepicker_license,true);
                break;
            case R.id.busyboxLicense:
                showLicense(R.string.about_license_busybox,R.raw.busybox_license,false);
                break;
        }
    }
}
