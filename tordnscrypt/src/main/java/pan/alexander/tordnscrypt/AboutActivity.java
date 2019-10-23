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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Objects;

public class AboutActivity extends LangAppCompatActivity implements View.OnClickListener {


    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
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

        findViewById(R.id.dnscryptLicence).setOnClickListener(this);
        findViewById(R.id.torLicence).setOnClickListener(this);
        findViewById(R.id.itpdLicence).setOnClickListener(this);
        findViewById(R.id.libsuperuserLicence).setOnClickListener(this);
        findViewById(R.id.androidShellLicence).setOnClickListener(this);
        findViewById(R.id.filepickerLicence).setOnClickListener(this);
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

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.CustomDialogTheme);
            LayoutInflater layoutInflater = getLayoutInflater();
            View inflatedView = layoutInflater.inflate(R.layout.licenses_scrollable_text, null, false);
            TextView licenseText = inflatedView.findViewById(R.id.tvLicense);
            licenseText.setText(outputText);

            builder.setTitle(title);

            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });

            builder.setView(inflatedView);

            AlertDialog view  = builder.show();
            Objects.requireNonNull(view.getWindow()).getDecorView().setBackgroundColor(Color.TRANSPARENT);
        }
        catch (IOException e)
        {
            e.printStackTrace();
            try {
                raw.close();
                byteArrayOutputStream.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.dnscryptLicence:
                showLicense(R.string.about_license_dnscrypt,R.raw.dnscrypt_license,false);
                break;
            case R.id.torLicence:
                showLicense(R.string.about_license_tor,R.raw.tor_license,false);
                break;
            case R.id.itpdLicence:
                showLicense(R.string.about_license_itpd,R.raw.itpd_license,false);
                break;
            case R.id.libsuperuserLicence:
                showLicense(R.string.about_license_libsupeuser,R.raw.libsuperuser_license,true);
                break;
            case R.id.androidShellLicence:
                showLicense(R.string.about_license_AndroidShell,R.raw.androidshell_license,true);
                break;
            case R.id.filepickerLicence:
                showLicense(R.string.about_license_filepicker,R.raw.filepicker_license,true);
                break;
        }
    }
}
