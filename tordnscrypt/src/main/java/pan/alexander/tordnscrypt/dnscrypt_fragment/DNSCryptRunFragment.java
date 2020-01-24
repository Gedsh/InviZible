package pan.alexander.tordnscrypt.dnscrypt;
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
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;


public class DNSCryptRunFragment extends Fragment implements DNSCryptFragmentView, View.OnClickListener {


    private Button btnDNSCryptStart = null;
    private TextView tvDNSStatus = null;
    private ProgressBar pbDNSCrypt = null;
    private TextView tvDNSCryptLog = null;

    private DNSCryptFragmentPresenter presenter;


    public DNSCryptRunFragment() {
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        presenter = new DNSCryptFragmentPresenter(this);

        BroadcastReceiver receiver = new DNSCryptFragmentReceiver(this, presenter);

        if (getActivity() != null) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            getActivity().registerReceiver(receiver, intentFilterBckgIntSer);
            getActivity().registerReceiver(receiver, intentFilterTopFrg);
        }


    }

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_dnscrypt_run, container, false);

        if (getActivity() == null) {
            return view;
        }

        btnDNSCryptStart = view.findViewById(R.id.btnDNSCryptStart);
        btnDNSCryptStart.setOnClickListener(this);

        pbDNSCrypt = view.findViewById(R.id.pbDNSCrypt);

        tvDNSCryptLog = view.findViewById(R.id.tvDNSCryptLog);
        tvDNSCryptLog.setMovementMethod(ScrollingMovementMethod.getInstance());
        setLogViewText();

        tvDNSStatus = view.findViewById(R.id.tvDNSStatus);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        presenter.onStart(getActivity());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        presenter.onDestroy(getActivity());
    }

    @Override
    public void onClick(View v) {
        presenter.startButtonOnClick(getActivity(), v);
    }

    @Override
    public void setDnsCryptStarting() {
        setDNSCryptStatus(R.string.tvDNSStarting, R.color.textModuleStatusColorStarting);
    }

    @Override
    public void setDnsCryptRunning() {
        setDNSCryptStatus(R.string.tvDNSRunning, R.color.textModuleStatusColorRunning);
        btnDNSCryptStart.setText(R.string.btnDNSCryptStop);
    }

    @Override
    public void setDnsCryptStopping() {
        setDNSCryptStatus(R.string.tvDNSStopping, R.color.textModuleStatusColorStopping);
    }

    @Override
    public void setDnsCryptStopped() {
        setDNSCryptStatus(R.string.tvDNSStop, R.color.textModuleStatusColorStopped);
        btnDNSCryptStart.setText(R.string.btnDNSCryptStart);
        setLogViewText();
    }

    @Override
    public void setDNSCryptInstalled(boolean installed) {
        if (installed) {
            btnDNSCryptStart.setEnabled(true);
        } else {
            tvDNSStatus.setText(getText(R.string.tvDNSNotInstalled));
        }
    }

    @Override
    public void setDnsCryptSomethingWrong() {
        setDNSCryptStatus(R.string.wrong, R.color.textModuleStatusColorAlert);
    }

    @Override
    public void setDNSCryptStatus(int resourceText, int resourceColor) {
        tvDNSStatus.setText(resourceText);
        tvDNSStatus.setTextColor(getResources().getColor(resourceColor));
    }

    @Override
    public void setStartButtonEnabled(boolean enabled) {
        if (btnDNSCryptStart.isEnabled() && !enabled) {
            btnDNSCryptStart.setEnabled(false);
        } else if (!btnDNSCryptStart.isEnabled() && enabled) {
            btnDNSCryptStart.setEnabled(true);
        }
    }

    @Override
    public void setStartButtonText(int textId) {
        btnDNSCryptStart.setText(textId);
    }

    @Override
    public void setProgressBarIndeterminate(boolean indeterminate) {
        if (!pbDNSCrypt.isIndeterminate() && indeterminate) {
            pbDNSCrypt.setIndeterminate(true);
        } else if (pbDNSCrypt.isIndeterminate() && !indeterminate){
            pbDNSCrypt.setIndeterminate(false);
        }
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void setLogViewText() {
        tvDNSCryptLog.setText(getText(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);
        tvDNSCryptLog.setGravity(Gravity.CENTER);
        tvDNSCryptLog.setLayoutParams(new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tvDNSCryptLog.scrollTo(0, 0);
    }

    @Override
    public void setLogViewText(Spanned text) {
        tvDNSCryptLog.setText(text);
        tvDNSCryptLog.setGravity(Gravity.BOTTOM);
        tvDNSCryptLog.setLayoutParams(new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    public Activity getFragmentActivity() {
        return getActivity();
    }

    @Override
    public FragmentManager getDNSCryptFragmentManager() {
        return getFragmentManager();
    }

}
