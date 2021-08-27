package pan.alexander.tordnscrypt.main_fragment;

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
import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.itpd_fragment.ITPDFragmentPresenter;
import pan.alexander.tordnscrypt.itpd_fragment.ITPDFragmentReceiver;
import pan.alexander.tordnscrypt.itpd_fragment.ITPDFragmentView;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSCryptFragmentPresenter;
import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSCryptFragmentReceiver;
import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSCryptFragmentView;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.tor_fragment.TorFragmentPresenter;
import pan.alexander.tordnscrypt.tor_fragment.TorFragmentReceiver;
import pan.alexander.tordnscrypt.tor_fragment.TorFragmentView;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.root.RootExecService;

import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.ITPDVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.TorVersion;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.UNDEFINED;

import javax.inject.Inject;

public class MainFragment extends Fragment implements DNSCryptFragmentView, TorFragmentView, ITPDFragmentView,
        View.OnClickListener, CompoundButton.OnCheckedChangeListener, ViewTreeObserver.OnScrollChangedListener,
        View.OnTouchListener {
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;

    private Button btnStartMainFragment;
    private CheckBox chbHideIpMainFragment;
    private CheckBox chbProtectDnsMainFragment;
    private CheckBox chbAccessITPMainFragment;
    private TextView tvDNSMainFragment;
    private TextView tvTorMainFragment;
    private TextView tvITPDMainFragment;
    private ProgressBar pbDNSMainFragment;
    private ProgressBar pbTorMainFragment;
    private ProgressBar pbITPDMainFragment;

    private DNSCryptFragmentPresenter dnsCryptFragmentPresenter;
    private DNSCryptFragmentReceiver dnsCryptFragmentReceiver;

    private TorFragmentPresenter torFragmentPresenter;
    private TorFragmentReceiver torFragmentReceiver;

    private ITPDFragmentPresenter itpdFragmentPresenter;
    private ITPDFragmentReceiver itpdFragmentReceiver;

    private TextView tvDNSCryptLog;
    private ScrollView svDNSCryptLog;

    private TextView tvTorLog;
    private ScrollView svTorLog;

    private TextView tvITPDLog;
    private TextView tvITPDInfoLog;
    private ScrollView svITPDLog;
    private ConstraintLayout clITPDLog;

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    private boolean orientationLandscape;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.instance.daggerComponent.inject(this);

        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_fragment, container, false);

        btnStartMainFragment = view.findViewById(R.id.btnStartMainFragment);
        btnStartMainFragment.setOnClickListener(this);

        chbHideIpMainFragment = view.findViewById(R.id.chbHideIpMainFragment);

        chbProtectDnsMainFragment = view.findViewById(R.id.chbProtectDnsMainFragment);

        chbAccessITPMainFragment = view.findViewById(R.id.chbAccessITPMainFragment);

        tvDNSMainFragment = view.findViewById(R.id.tvDNSMainFragment);
        tvTorMainFragment = view.findViewById(R.id.tvTorMainFragment);
        tvITPDMainFragment = view.findViewById(R.id.tvITPDMainFragment);

        pbDNSMainFragment = view.findViewById(R.id.pbDNSMainFragment);
        pbTorMainFragment = view.findViewById(R.id.pbTorMainFragment);
        pbITPDMainFragment = view.findViewById(R.id.pbITPDMainFragment);

        Context context = getActivity();
        if (context == null) {
            return view;
        }

        PreferenceRepository preferences = preferenceRepository.get();

        orientationLandscape = Utils.INSTANCE.getScreenOrientation(getActivity()) == Configuration.ORIENTATION_LANDSCAPE;

        boolean hideIp = preferences.getBoolPreference("HideIp");
        boolean protectDns = preferences.getBoolPreference("ProtectDns");
        boolean accessITP = preferences.getBoolPreference("AccessITP");

        if (!hideIp && !protectDns && !accessITP) {
            preferences.setBoolPreference("HideIp", true);
            preferences.setBoolPreference("ProtectDns", true);
            preferences.setBoolPreference("AccessITP", false);
        } else {
            chbHideIpMainFragment.setChecked(hideIp);
            chbProtectDnsMainFragment.setChecked(protectDns);
            chbAccessITPMainFragment.setChecked(accessITP);
        }

        chbHideIpMainFragment.setOnCheckedChangeListener(this);
        chbProtectDnsMainFragment.setOnCheckedChangeListener(this);
        chbAccessITPMainFragment.setOnCheckedChangeListener(this);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        Context context = getActivity();
        if (context == null || orientationLandscape) {
            return;
        }

        initDNSCryptFragmentPresenter(context);
        initTorFragmentPresenter(context);
        initITPDFragmentPresenter(context);

        dnsCryptFragmentPresenter.onStart();
        torFragmentPresenter.onStart();
        itpdFragmentPresenter.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();
        if (context == null) {
            return;
        }

        try {
            if (dnsCryptFragmentReceiver != null) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(dnsCryptFragmentReceiver);
            }

            if (torFragmentReceiver != null) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(torFragmentReceiver);
            }

            if (itpdFragmentReceiver != null) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(itpdFragmentReceiver);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "MainFragment onStop exception " + e.getMessage() + " " + e.getCause());
        }

        if (dnsCryptFragmentPresenter != null) {
            dnsCryptFragmentPresenter.onStop();
        }
        if (torFragmentPresenter != null) {
            torFragmentPresenter.onStop();
        }
        if (itpdFragmentPresenter != null) {
            itpdFragmentPresenter.onStop();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (svDNSCryptLog != null) {
            svDNSCryptLog.setOnTouchListener(null);
            svDNSCryptLog.getViewTreeObserver().removeOnScrollChangedListener(this);
        }
        if (svTorLog != null) {
            svTorLog.setOnTouchListener(null);
            svTorLog.getViewTreeObserver().removeOnScrollChangedListener(this);
        }
        if (svITPDLog != null) {
            svITPDLog.setOnTouchListener(null);
            svITPDLog.getViewTreeObserver().removeOnScrollChangedListener(this);
        }

        btnStartMainFragment = null;
        chbHideIpMainFragment = null;
        chbProtectDnsMainFragment = null;
        chbAccessITPMainFragment = null;
        tvDNSMainFragment = null;
        tvTorMainFragment = null;
        tvITPDMainFragment = null;
        pbDNSMainFragment = null;
        pbTorMainFragment = null;
        pbITPDMainFragment = null;

        tvDNSCryptLog = null;
        svDNSCryptLog = null;

        tvTorLog = null;
        svTorLog = null;

        tvITPDLog = null;
        tvITPDInfoLog = null;
        svITPDLog = null;
        clITPDLog = null;

        dnsCryptFragmentPresenter = null;
        dnsCryptFragmentReceiver = null;

        torFragmentPresenter = null;
        torFragmentReceiver = null;

        itpdFragmentPresenter = null;
        itpdFragmentReceiver = null;
    }

    @Override
    public void onClick(View v) {

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (dnsCryptFragmentPresenter == null
                || torFragmentPresenter == null
                || itpdFragmentPresenter == null
                || orientationLandscape) {
            return;
        }

        if (!isDNSCryptInstalled(context)
                || !isTorInstalled(context)
                || !isITPDInstalled(context)) {
            return;
        }

        if (isControlLocked(getActivity())) {
            return;
        }

        if (v.getId() == R.id.btnStartMainFragment) {

            if (modulesStatus.getDnsCryptState() == STOPPED
                    && modulesStatus.getTorState() == STOPPED
                    && modulesStatus.getItpdState() == STOPPED) {

                if (chbProtectDnsMainFragment.isChecked()) {
                    dnsCryptFragmentPresenter.startButtonOnClick();
                }

                if (chbHideIpMainFragment.isChecked()) {
                    torFragmentPresenter.startButtonOnClick();
                }

                if (chbAccessITPMainFragment.isChecked()) {
                    itpdFragmentPresenter.startButtonOnClick();
                }
            } else {
                if (modulesStatus.getDnsCryptState() != STOPPED) {
                    dnsCryptFragmentPresenter.startButtonOnClick();
                }

                if (modulesStatus.getTorState() != STOPPED) {
                    torFragmentPresenter.startButtonOnClick();
                }

                if (modulesStatus.getItpdState() != STOPPED) {
                    itpdFragmentPresenter.startButtonOnClick();
                }
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

        Context context = getActivity();
        if (context == null) {
            return;
        }

        if (buttonView == null
                || dnsCryptFragmentPresenter == null
                || torFragmentPresenter == null
                || itpdFragmentPresenter == null
                || orientationLandscape) {
            return;
        }

        if (isControlLocked(getActivity())) {
            return;
        }

        if (modulesStatus.getDnsCryptState() != STOPPED
                || modulesStatus.getTorState() != STOPPED
                || modulesStatus.getItpdState() != STOPPED) {

            int id = buttonView.getId();
            if (id == R.id.chbProtectDnsMainFragment) {
                if (modulesStatus.getDnsCryptState() != STOPPED && !isChecked) {
                    dnsCryptFragmentPresenter.startButtonOnClick();
                } else if (modulesStatus.getDnsCryptState() == STOPPED && isChecked) {
                    dnsCryptFragmentPresenter.startButtonOnClick();
                }
            } else if (id == R.id.chbHideIpMainFragment) {
                if (modulesStatus.getTorState() != STOPPED && !isChecked) {
                    torFragmentPresenter.startButtonOnClick();
                } else if (modulesStatus.getTorState() == STOPPED && isChecked) {
                    torFragmentPresenter.startButtonOnClick();
                }
            } else if (id == R.id.chbAccessITPMainFragment) {
                if (modulesStatus.getItpdState() != STOPPED && !isChecked) {
                    itpdFragmentPresenter.startButtonOnClick();
                } else if (modulesStatus.getItpdState() == STOPPED && isChecked) {
                    itpdFragmentPresenter.startButtonOnClick();
                }
            }
        }

        PreferenceRepository preferences = preferenceRepository.get();

        int id = buttonView.getId();
        if (id == R.id.chbProtectDnsMainFragment) {
            preferences.setBoolPreference("ProtectDns", isChecked);
        } else if (id == R.id.chbHideIpMainFragment) {
            preferences.setBoolPreference("HideIp", isChecked);
        } else if (id == R.id.chbAccessITPMainFragment) {
            preferences.setBoolPreference("AccessITP", isChecked);
        }
    }

    private void refreshStartButtonText() {

        Context context = getActivity();
        if (context == null || orientationLandscape) {
            return;
        }

        Drawable mainStartButtonDrawable;

        if ((modulesStatus.getDnsCryptState() == STOPPED || modulesStatus.getDnsCryptState() == UNDEFINED)
                && (modulesStatus.getTorState() == STOPPED || modulesStatus.getTorState() == UNDEFINED)
                && (modulesStatus.getItpdState() == STOPPED || modulesStatus.getItpdState() == UNDEFINED)) {

            mainStartButtonDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.button_main_selector, context.getTheme());

            btnStartMainFragment.setText(getText(R.string.main_fragment_button_start));

            btnStartMainFragment.setBackground(mainStartButtonDrawable);

        } else {

            mainStartButtonDrawable = ResourcesCompat.getDrawable(context.getResources(), R.drawable.button_main_selector_active, context.getTheme());

            btnStartMainFragment.setText(getText(R.string.main_fragment_button_stop));

            btnStartMainFragment.setBackground(mainStartButtonDrawable);

            if (modulesStatus.getDnsCryptState() != STOPPING
                    && modulesStatus.getTorState() != STOPPING
                    && modulesStatus.getItpdState() != STOPPING) {

                setChbProtectDnsMainFragment(modulesStatus.getDnsCryptState() != STOPPED);

                setChbHideIpMainFragment(modulesStatus.getTorState() != STOPPED);

                setChbAccessITPMainFragment(modulesStatus.getItpdState() != STOPPED);
            }
        }
    }

    private void setChbProtectDnsMainFragment(boolean checked) {
        if (!chbProtectDnsMainFragment.isChecked() && checked) {
            chbProtectDnsMainFragment.setChecked(true);
        } else if (chbProtectDnsMainFragment.isChecked() && !checked) {
            chbProtectDnsMainFragment.setChecked(false);
        }
    }

    private void initDNSCryptFragmentPresenter(Context context) {
        dnsCryptFragmentPresenter = new DNSCryptFragmentPresenter(this);

        dnsCryptFragmentReceiver = new DNSCryptFragmentReceiver(this, dnsCryptFragmentPresenter);

        if (!orientationLandscape) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            LocalBroadcastManager.getInstance(context).registerReceiver(dnsCryptFragmentReceiver, intentFilterBckgIntSer);
            LocalBroadcastManager.getInstance(context).registerReceiver(dnsCryptFragmentReceiver, intentFilterTopFrg);
        }
    }

    @Override
    public void setDNSCryptStatus(int resourceText, int resourceColor) {
        tvDNSMainFragment.setText(resourceText);
        tvDNSMainFragment.setTextColor(getResources().getColor(resourceColor));
    }

    @Override
    public void setDNSCryptStartButtonEnabled(boolean enabled) {
        if (chbProtectDnsMainFragment.isEnabled() && !enabled) {
            chbProtectDnsMainFragment.setEnabled(false);
        } else if (!chbProtectDnsMainFragment.isEnabled() && enabled) {
            chbProtectDnsMainFragment.setEnabled(true);
        }
    }

    @Override
    public void setDNSCryptProgressBarIndeterminate(boolean indeterminate) {
        if (!pbDNSMainFragment.isIndeterminate() && indeterminate) {
            pbDNSMainFragment.setIndeterminate(true);
        } else if (pbDNSMainFragment.isIndeterminate() && !indeterminate) {
            pbDNSMainFragment.setIndeterminate(false);
        }
    }

    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void setDNSCryptLogViewText() {
        Activity activity = getActivity();
        if (activity != null && tvDNSCryptLog == null && svDNSCryptLog == null && !orientationLandscape) {
            tvDNSCryptLog = activity.findViewById(R.id.tvDNSCryptLog);
            svDNSCryptLog = activity.findViewById(R.id.svDNSCryptLog);

            if (svDNSCryptLog != null) {
                svDNSCryptLog.setOnTouchListener(this);
                svDNSCryptLog.getViewTreeObserver().addOnScrollChangedListener(this);
            }
        }

        if (tvDNSCryptLog != null && svDNSCryptLog != null) {
            tvDNSCryptLog.setText(getText(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);
            if (TopFragment.logsTextSize != 0f) {
                tvDNSCryptLog.setTextSize(COMPLEX_UNIT_PX, TopFragment.logsTextSize);
            }
            tvDNSCryptLog.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            svDNSCryptLog.setLayoutParams(params);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void setDNSCryptLogViewText(Spanned text) {
        Activity activity = getActivity();
        if (activity != null && tvDNSCryptLog == null && svDNSCryptLog == null && !orientationLandscape) {
            tvDNSCryptLog = activity.findViewById(R.id.tvDNSCryptLog);
            svDNSCryptLog = activity.findViewById(R.id.svDNSCryptLog);

            if (svDNSCryptLog != null) {
                svDNSCryptLog.setOnTouchListener(this);
                svDNSCryptLog.getViewTreeObserver().addOnScrollChangedListener(this);
            }
        }

        if (tvDNSCryptLog != null && svDNSCryptLog != null) {
            tvDNSCryptLog.setText(text);
            if (TopFragment.logsTextSize != 0f) {
                tvDNSCryptLog.setTextSize(COMPLEX_UNIT_PX, TopFragment.logsTextSize);
            }
            tvDNSCryptLog.setGravity(Gravity.NO_GRAVITY);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.BOTTOM;
            svDNSCryptLog.setLayoutParams(params);
        }
    }

    @Override
    public void scrollDNSCryptLogViewToBottom() {
        scrollToBottom(svDNSCryptLog);
    }


    private void setChbHideIpMainFragment(boolean checked) {
        if (!chbHideIpMainFragment.isChecked() && checked) {
            chbHideIpMainFragment.setChecked(true);
        } else if (chbHideIpMainFragment.isChecked() && !checked) {
            chbHideIpMainFragment.setChecked(false);
        }
    }

    private void initTorFragmentPresenter(Context context) {
        torFragmentPresenter = new TorFragmentPresenter(this);

        torFragmentReceiver = new TorFragmentReceiver(this, torFragmentPresenter);

        if (!orientationLandscape) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            LocalBroadcastManager.getInstance(context).registerReceiver(torFragmentReceiver, intentFilterBckgIntSer);
            LocalBroadcastManager.getInstance(context).registerReceiver(torFragmentReceiver, intentFilterTopFrg);
        }
    }

    @Override
    public void setTorStatus(int resourceText, int resourceColor) {
        tvTorMainFragment.setText(resourceText);
        tvTorMainFragment.setTextColor(getResources().getColor(resourceColor));
    }

    @Override
    public void setTorStatus(String text, int resourceColor) {
        tvTorMainFragment.setText(text);
        tvTorMainFragment.setTextColor(getResources().getColor(resourceColor));
    }

    @Override
    public void setTorStartButtonEnabled(boolean enabled) {
        if (chbHideIpMainFragment.isEnabled() && !enabled) {
            chbHideIpMainFragment.setEnabled(false);
        } else if (!chbHideIpMainFragment.isEnabled() && enabled) {
            chbHideIpMainFragment.setEnabled(true);
        }
    }

    @Override
    public void setTorProgressBarIndeterminate(boolean indeterminate) {
        if (!pbTorMainFragment.isIndeterminate() && indeterminate) {
            pbTorMainFragment.setIndeterminate(true);
        } else if (pbTorMainFragment.isIndeterminate() && !indeterminate) {
            pbTorMainFragment.setIndeterminate(false);
        }
    }

    @Override
    public void setTorProgressBarProgress(int progress) {
        pbTorMainFragment.setProgress(progress);
    }

    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void setTorLogViewText() {
        Activity activity = getActivity();
        if (activity != null && tvTorLog == null && svTorLog == null && !orientationLandscape) {
            tvTorLog = activity.findViewById(R.id.tvTorLog);
            svTorLog = activity.findViewById(R.id.svTorLog);

            if (svTorLog != null) {
                svTorLog.setOnTouchListener(this);
                svTorLog.getViewTreeObserver().addOnScrollChangedListener(this);
            }
        }

        if (tvTorLog != null && svTorLog != null) {
            tvTorLog.setText(getText(R.string.tvTorDefaultLog) + " " + TorVersion);
            if (TopFragment.logsTextSize != 0f) {
                tvTorLog.setTextSize(COMPLEX_UNIT_PX, TopFragment.logsTextSize);
            }
            tvTorLog.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            svTorLog.setLayoutParams(params);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void setTorLogViewText(Spanned text) {
        Activity activity = getActivity();
        if (activity != null && tvTorLog == null && svTorLog == null && !orientationLandscape) {
            tvTorLog = activity.findViewById(R.id.tvTorLog);
            svTorLog = activity.findViewById(R.id.svTorLog);

            if (svTorLog != null) {
                svTorLog.setOnTouchListener(this);
                svTorLog.getViewTreeObserver().addOnScrollChangedListener(this);
            }
        }

        if (tvTorLog != null && svTorLog != null) {
            tvTorLog.setText(text);
            if (TopFragment.logsTextSize != 0f) {
                tvTorLog.setTextSize(COMPLEX_UNIT_PX, TopFragment.logsTextSize);
            }
            tvTorLog.setGravity(Gravity.NO_GRAVITY);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.BOTTOM;
            svTorLog.setLayoutParams(params);
        }
    }

    @Override
    public void scrollTorLogViewToBottom() {
        scrollToBottom(svTorLog);
    }

    private void initITPDFragmentPresenter(Context context) {
        itpdFragmentPresenter = new ITPDFragmentPresenter(this);

        itpdFragmentReceiver = new ITPDFragmentReceiver(this, itpdFragmentPresenter);

        if (!orientationLandscape) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            LocalBroadcastManager.getInstance(context).registerReceiver(itpdFragmentReceiver, intentFilterBckgIntSer);
            LocalBroadcastManager.getInstance(context).registerReceiver(itpdFragmentReceiver, intentFilterTopFrg);
        }
    }

    @Override
    public void setITPDStatus(int resourceText, int resourceColor) {
        tvITPDMainFragment.setText(resourceText);
        tvITPDMainFragment.setTextColor(getResources().getColor(resourceColor));
    }

    @Override
    public void setITPDStartButtonEnabled(boolean enabled) {
        if (chbAccessITPMainFragment.isEnabled() && !enabled) {
            chbAccessITPMainFragment.setEnabled(false);
        } else if (!chbAccessITPMainFragment.isEnabled() && enabled) {
            chbAccessITPMainFragment.setEnabled(true);
        }
    }

    @Override
    public void setITPDProgressBarIndeterminate(boolean indeterminate) {
        if (!pbITPDMainFragment.isIndeterminate() && indeterminate) {
            pbITPDMainFragment.setIndeterminate(true);
        } else if (pbITPDMainFragment.isIndeterminate() && !indeterminate) {
            pbITPDMainFragment.setIndeterminate(false);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void setITPDLogViewText() {
        Activity activity = getActivity();
        if (activity != null && tvITPDLog == null && !orientationLandscape) {
            tvITPDLog = activity.findViewById(R.id.tvITPDLog);
            clITPDLog = activity.findViewById(R.id.clITPDLog);
        }

        if (tvITPDLog != null) {
            tvITPDLog.setText(getText(R.string.tvITPDDefaultLog) + " " + ITPDVersion);
            if (TopFragment.logsTextSize != 0f) {
                tvITPDLog.setTextSize(COMPLEX_UNIT_PX, TopFragment.logsTextSize);
            }
            tvITPDLog.setGravity(Gravity.CENTER);

            if (clITPDLog != null) {
                ConstraintSet set = new ConstraintSet();
                set.clone(clITPDLog);
                set.connect(tvITPDLog.getId(), ConstraintSet.TOP, clITPDLog.getId(), ConstraintSet.TOP);
                set.applyTo(clITPDLog);
            }
        }
    }

    @Override
    public void setITPDLogViewText(Spanned text) {
        Activity activity = getActivity();
        if (activity != null && tvITPDLog == null && !orientationLandscape) {
            tvITPDLog = activity.findViewById(R.id.tvITPDLog);
            clITPDLog = activity.findViewById(R.id.clITPDLog);
        }

        if (tvITPDLog != null) {
            tvITPDLog.setText(text);
            if (TopFragment.logsTextSize != 0f) {
                tvITPDLog.setTextSize(COMPLEX_UNIT_PX, TopFragment.logsTextSize);
            }
            tvITPDLog.setGravity(Gravity.NO_GRAVITY);

            if (clITPDLog != null) {
                ConstraintSet set = new ConstraintSet();
                set.clone(clITPDLog);
                set.clear(tvITPDLog.getId(), ConstraintSet.TOP);
                set.applyTo(clITPDLog);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void setITPDInfoLogText() {
        Activity activity = getActivity();
        if (activity != null && tvITPDInfoLog == null && svITPDLog == null && !orientationLandscape) {
            tvITPDInfoLog = activity.findViewById(R.id.tvITPDinfoLog);
            svITPDLog = activity.findViewById(R.id.svITPDLog);

            if (svITPDLog != null) {
                svITPDLog.setOnTouchListener(this);
                svITPDLog.getViewTreeObserver().addOnScrollChangedListener(this);
            }
        }

        if (tvITPDInfoLog != null && svITPDLog != null) {
            tvITPDInfoLog.setText("");
            if (TopFragment.logsTextSize != 0f) {
                tvITPDInfoLog.setTextSize(COMPLEX_UNIT_PX, TopFragment.logsTextSize);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void setITPDInfoLogText(Spanned text) {
        Activity activity = getActivity();
        if (activity != null && tvITPDInfoLog == null && svITPDLog == null && !orientationLandscape) {
            tvITPDInfoLog = activity.findViewById(R.id.tvITPDinfoLog);
            svITPDLog = activity.findViewById(R.id.svITPDLog);

            if (svITPDLog != null) {
                svITPDLog.setOnTouchListener(this);
                svITPDLog.getViewTreeObserver().addOnScrollChangedListener(this);
            }
        }

        if (tvITPDInfoLog != null && svITPDLog != null) {
            tvITPDInfoLog.setText(text);
            if (TopFragment.logsTextSize != 0f) {
                tvITPDInfoLog.setTextSize(COMPLEX_UNIT_PX, TopFragment.logsTextSize);
            }
        }
    }

    @Override
    public void scrollITPDLogViewToBottom() {
        scrollToBottom(svITPDLog);
    }

    private void setChbAccessITPMainFragment(boolean checked) {
        if (!chbAccessITPMainFragment.isChecked() && checked) {
            chbAccessITPMainFragment.setChecked(true);
        } else if (chbAccessITPMainFragment.isChecked() && !checked) {
            chbAccessITPMainFragment.setChecked(false);
        }
    }

    @Override
    public void setStartButtonText(int textId) {
        refreshStartButtonText();
    }

    @Override
    public Activity getFragmentActivity() {
        return getActivity();
    }

    @Override
    public FragmentManager getFragmentFragmentManager() {
        return getParentFragmentManager();
    }

    private synchronized void scrollToBottom(ScrollView scrollView) {

        if (scrollView == null) {
            return;
        }

        scrollView.post(() -> {

            scrollView.computeScroll();

            int delta = 0;

            int childIndex = scrollView.getChildCount() - 1;

            if (childIndex < 0) {
                return;
            }

            View lastChild = scrollView.getChildAt(childIndex);

            if (lastChild != null) {
                int bottom = lastChild.getBottom() + scrollView.getPaddingBottom();
                int sy = scrollView.getScrollY();
                int sh = scrollView.getHeight();
                delta = bottom - (sy + sh);
            }

            if (delta > 0) {
                scrollView.smoothScrollBy(0, delta);
            }
        });
    }

    public DNSCryptFragmentPresenter getDnsCryptFragmentPresenter() {
        return dnsCryptFragmentPresenter;
    }

    public TorFragmentPresenter getTorFragmentPresenter() {
        return torFragmentPresenter;
    }

    public ITPDFragmentPresenter getITPDFragmentPresenter() {
        return itpdFragmentPresenter;
    }

    private boolean isDNSCryptInstalled(Context context) {
        if (context != null) {
            return preferenceRepository.get().getBoolPreference("DNSCrypt Installed");
        }
        return false;
    }

    private boolean isTorInstalled(Context context) {
        if (context != null) {
            return preferenceRepository.get().getBoolPreference("Tor Installed");
        }
        return false;
    }

    private boolean isITPDInstalled(Context context) {
        if (context != null) {
            return preferenceRepository.get().getBoolPreference("I2PD Installed");
        }
        return false;
    }

    private boolean isControlLocked(Activity activity) {
        if (activity instanceof MainActivity && ((MainActivity) activity).childLockActive) {
            Toast.makeText(activity, activity.getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return true;
        }

        return false;
    }

    @Override
    public void onScrollChanged() {

        if (dnsCryptFragmentPresenter != null && svDNSCryptLog != null) {
            dnsCryptFragmentPresenter.dnsCryptLogAutoScrollingAllowed(!svDNSCryptLog.canScrollVertically(1)
                    || !svDNSCryptLog.canScrollVertically(-1));
        }

        if (torFragmentPresenter != null && svTorLog != null) {
            torFragmentPresenter.torLogAutoScrollingAllowed(!svTorLog.canScrollVertically(1)
                    || !svTorLog.canScrollVertically(-1));
        }

        if (itpdFragmentPresenter != null && svITPDLog != null) {
            itpdFragmentPresenter.itpdLogAutoScrollingAllowed(!svITPDLog.canScrollVertically(1)
                    || !svITPDLog.canScrollVertically(-1));
        }
    }

    @Override
    public void setLogsTextSize(float size) {
        if (tvDNSCryptLog != null) {
            tvDNSCryptLog.setTextSize(COMPLEX_UNIT_PX, size);
        }

        if (tvTorLog != null) {
            tvTorLog.setTextSize(COMPLEX_UNIT_PX, size);
        }

        if (tvITPDLog != null) {
            tvITPDLog.setTextSize(COMPLEX_UNIT_PX, size);
        }

        if (tvITPDInfoLog != null) {
            tvITPDInfoLog.setTextSize(COMPLEX_UNIT_PX, size);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {

        if (motionEvent.getPointerCount() != 2) {
            return false;
        }

        if (view.getId() == R.id.svDNSCryptLog && dnsCryptFragmentPresenter != null) {
            ScaleGestureDetector detector = dnsCryptFragmentPresenter.getScaleGestureDetector();
            if (detector != null) {
                detector.onTouchEvent(motionEvent);
                return true;
            }
        } else if (view.getId() == R.id.svTorLog && torFragmentPresenter != null) {
            ScaleGestureDetector detector = torFragmentPresenter.getScaleGestureDetector();
            if (detector != null) {
                detector.onTouchEvent(motionEvent);
                return true;
            }
        } else if (view.getId() == R.id.svITPDLog && itpdFragmentPresenter != null) {
            ScaleGestureDetector detector = itpdFragmentPresenter.getScaleGestureDetector();
            if (detector != null) {
                detector.onTouchEvent(motionEvent);
                return true;
            }
        }


        return false;
    }
}
