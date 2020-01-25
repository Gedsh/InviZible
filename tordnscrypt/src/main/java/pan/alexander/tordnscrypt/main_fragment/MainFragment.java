package pan.alexander.tordnscrypt.main_fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.Objects;

import pan.alexander.tordnscrypt.MainActivity;
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
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.ITPDVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.TorVersion;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPING;

public class MainFragment extends Fragment implements DNSCryptFragmentView, TorFragmentView, ITPDFragmentView, View.OnClickListener, CompoundButton.OnCheckedChangeListener {
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

    private ModulesStatus modulesStatus = ModulesStatus.getInstance();

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

        if (getActivity() == null) {
            return view;
        }

        boolean hideIp = new PrefManager(getActivity()).getBoolPref("HideIp");
        boolean protectDns = new PrefManager(getActivity()).getBoolPref("ProtectDns");
        boolean accessITP = new PrefManager(getActivity()).getBoolPref("AccessITP");

        if (!hideIp && !protectDns && !accessITP) {
            new PrefManager(getActivity()).setBoolPref("HideIp", true);
            new PrefManager(getActivity()).setBoolPref("ProtectDns", true);
            new PrefManager(getActivity()).setBoolPref("AccessITP", false);
        } else {
            chbHideIpMainFragment.setChecked(hideIp);
            chbProtectDnsMainFragment.setChecked(protectDns);
            chbAccessITPMainFragment.setChecked(accessITP);
        }

        chbHideIpMainFragment.setOnCheckedChangeListener(this);
        chbProtectDnsMainFragment.setOnCheckedChangeListener(this);
        chbAccessITPMainFragment.setOnCheckedChangeListener(this);

        initDNSCryptFragmentPresenter();
        initTorFragmentPresenter();
        initITPDFragmentPresenter();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        dnsCryptFragmentPresenter.onStart(getActivity());
        torFragmentPresenter.onStart(getActivity());
        itpdFragmentPresenter.onStart(getActivity());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            if (dnsCryptFragmentReceiver != null) {
                Objects.requireNonNull(getActivity()).unregisterReceiver(dnsCryptFragmentReceiver);
            }

            if (torFragmentReceiver != null) {
                Objects.requireNonNull(getActivity()).unregisterReceiver(torFragmentReceiver);
            }

            if (itpdFragmentReceiver != null) {
                Objects.requireNonNull(getActivity()).unregisterReceiver(itpdFragmentReceiver);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "MainFragment onDestroy exception " + e.getMessage() + " " + e.getCause());
        }

        dnsCryptFragmentPresenter.onDestroy(getActivity());
        torFragmentPresenter.onDestroy();
        itpdFragmentPresenter.onDestroy();
    }


    @Override
    public void onClick(View v) {

        if (getActivity() == null) {
            return;
        }

        if (!isDNSCryptInstalled(getActivity())
                || !isTorInstalled(getActivity())
                || !isITPDInstalled(getActivity())) {
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
                    dnsCryptFragmentPresenter.startButtonOnClick(getActivity());
                }

                if (chbHideIpMainFragment.isChecked()) {
                    torFragmentPresenter.startButtonOnClick(getActivity());
                }

                if (chbAccessITPMainFragment.isChecked()) {
                    itpdFragmentPresenter.startButtonOnClick(getActivity());
                }
            } else {
                if (modulesStatus.getDnsCryptState() != STOPPED) {
                    dnsCryptFragmentPresenter.startButtonOnClick(getActivity());
                }

                if (modulesStatus.getTorState() != STOPPED) {
                    torFragmentPresenter.startButtonOnClick(getActivity());
                }

                if (modulesStatus.getItpdState() != STOPPED) {
                    itpdFragmentPresenter.startButtonOnClick(getActivity());
                }
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

        if (getActivity() == null) {
            return;
        }

        if (isControlLocked(getActivity())) {
            return;
        }

        if (modulesStatus.getDnsCryptState() != STOPPED
                || modulesStatus.getTorState() != STOPPED
                || modulesStatus.getItpdState() != STOPPED) {

            switch (buttonView.getId()) {
                case R.id.chbProtectDnsMainFragment:
                    if (modulesStatus.getDnsCryptState() != STOPPED && !isChecked) {
                        dnsCryptFragmentPresenter.startButtonOnClick(getActivity());
                    } else if (modulesStatus.getDnsCryptState() == STOPPED && isChecked) {
                        dnsCryptFragmentPresenter.startButtonOnClick(getActivity());
                    }
                    break;
                case R.id.chbHideIpMainFragment:
                    if (modulesStatus.getTorState() != STOPPED && !isChecked) {
                        torFragmentPresenter.startButtonOnClick(getActivity());
                    } else if (modulesStatus.getTorState() == STOPPED && isChecked) {
                        torFragmentPresenter.startButtonOnClick(getActivity());
                    }
                    break;
                case R.id.chbAccessITPMainFragment:
                    if (modulesStatus.getItpdState() != STOPPED && !isChecked) {
                        itpdFragmentPresenter.startButtonOnClick(getActivity());
                    } else if (modulesStatus.getItpdState() == STOPPED && isChecked) {
                        itpdFragmentPresenter.startButtonOnClick(getActivity());
                    }
                    break;
            }
        }

        switch (buttonView.getId()) {
            case R.id.chbProtectDnsMainFragment:
                new PrefManager(getActivity()).setBoolPref("ProtectDns", isChecked);
                break;
            case R.id.chbHideIpMainFragment:
                new PrefManager(getActivity()).setBoolPref("HideIp", isChecked);
                break;
            case R.id.chbAccessITPMainFragment:
                new PrefManager(getActivity()).setBoolPref("AccessITP", isChecked);
                break;
        }
    }

    private void refreshStartButtonText() {

        if (getActivity() == null) {
            return;
        }

        Drawable mainStartButtonDrawable;

        if (modulesStatus.getDnsCryptState() == STOPPED
                && modulesStatus.getTorState() == STOPPED
                && modulesStatus.getItpdState() == STOPPED) {

            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
                mainStartButtonDrawable = getActivity().getResources().getDrawable(R.drawable.button_main_selector, getActivity().getTheme());
            } else {
                mainStartButtonDrawable = getActivity().getResources().getDrawable(R.drawable.button_main_selector);
            }

            btnStartMainFragment.setText(getText(R.string.main_fragment_button_start));

            btnStartMainFragment.setBackground(mainStartButtonDrawable);

        } else {

            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
                mainStartButtonDrawable = getActivity().getResources().getDrawable(R.drawable.button_main_selector_active, getActivity().getTheme());
            } else {
                mainStartButtonDrawable = getActivity().getResources().getDrawable(R.drawable.button_main_selector_active);
            }

            btnStartMainFragment.setText(getText(R.string.main_fragment_button_stop));

            btnStartMainFragment.setBackground(mainStartButtonDrawable);

            if (modulesStatus.getDnsCryptState() != STOPPING
                    && modulesStatus.getTorState() != STOPPING
                    && modulesStatus.getItpdState() != STOPPING) {

                if (modulesStatus.getDnsCryptState() == STOPPED) {
                    setChbProtectDnsMainFragment(false);
                } else {
                    setChbProtectDnsMainFragment(true);
                }

                if (modulesStatus.getTorState() == STOPPED) {
                    setChbHideIpMainFragment(false);
                } else {
                    setChbHideIpMainFragment(true);
                }

                if (modulesStatus.getItpdState() == STOPPED) {
                    setChbAccessITPMainFragment(false);
                } else {
                    setChbAccessITPMainFragment(true);
                }
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

    private void initDNSCryptFragmentPresenter() {
        dnsCryptFragmentPresenter = new DNSCryptFragmentPresenter(this);

        dnsCryptFragmentReceiver = new DNSCryptFragmentReceiver(this, dnsCryptFragmentPresenter);

        if (getActivity() != null) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            getActivity().registerReceiver(dnsCryptFragmentReceiver, intentFilterBckgIntSer);
            getActivity().registerReceiver(dnsCryptFragmentReceiver, intentFilterTopFrg);
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

    @SuppressLint("SetTextI18n")
    @Override
    public void setDNSCryptLogViewText() {
        if (getActivity() != null && tvDNSCryptLog == null && svDNSCryptLog == null) {
            tvDNSCryptLog = getActivity().findViewById(R.id.tvDNSCryptLog);
            svDNSCryptLog = getActivity().findViewById(R.id.svDNSCryptLog);
        }

        if (tvDNSCryptLog != null && svDNSCryptLog != null) {
            tvDNSCryptLog.setText(getText(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);
            tvDNSCryptLog.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            svDNSCryptLog.setLayoutParams(params);
        }
    }

    @Override
    public void setDNSCryptLogViewText(Spanned text) {
        if (getActivity() != null && tvDNSCryptLog == null && svDNSCryptLog == null) {
            tvDNSCryptLog = getActivity().findViewById(R.id.tvDNSCryptLog);
            svDNSCryptLog = getActivity().findViewById(R.id.svDNSCryptLog);
        }

        if (tvDNSCryptLog != null && svDNSCryptLog != null) {
            tvDNSCryptLog.setText(text);
            tvDNSCryptLog.setGravity(Gravity.NO_GRAVITY);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.BOTTOM;
            svDNSCryptLog.setLayoutParams(params);
            scrollToBottom(svDNSCryptLog);
        }
    }


    private void setChbHideIpMainFragment(boolean checked) {
        if (!chbHideIpMainFragment.isChecked() && checked) {
            chbHideIpMainFragment.setChecked(true);
        } else if (chbHideIpMainFragment.isChecked() && !checked) {
            chbHideIpMainFragment.setChecked(false);
        }
    }

    private void initTorFragmentPresenter() {
        torFragmentPresenter = new TorFragmentPresenter(this);

        torFragmentReceiver = new TorFragmentReceiver(this, torFragmentPresenter);

        if (getActivity() != null) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            getActivity().registerReceiver(torFragmentReceiver, intentFilterBckgIntSer);
            getActivity().registerReceiver(torFragmentReceiver, intentFilterTopFrg);
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

    @SuppressLint("SetTextI18n")
    @Override
    public void setTorLogViewText() {
        if (getActivity() != null && tvTorLog == null && svTorLog == null) {
            tvTorLog = getActivity().findViewById(R.id.tvTorLog);
            svTorLog = getActivity().findViewById(R.id.svTorLog);
        }

        if (tvTorLog != null && svTorLog != null) {
            tvTorLog.setText(getText(R.string.tvTorDefaultLog) + " " + TorVersion);
            tvTorLog.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.CENTER;
            svTorLog.setLayoutParams(params);
        }
    }

    @Override
    public void setTorLogViewText(Spanned text) {
        if (getActivity() != null && tvTorLog == null && svTorLog == null) {
            tvTorLog = getActivity().findViewById(R.id.tvTorLog);
            svTorLog = getActivity().findViewById(R.id.svTorLog);
        }

        if (tvTorLog != null && svTorLog != null) {
            tvTorLog.setText(text);
            tvTorLog.setGravity(Gravity.NO_GRAVITY);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.BOTTOM;
            svTorLog.setLayoutParams(params);
            scrollToBottom(svTorLog);
        }
    }


    private void initITPDFragmentPresenter() {
        itpdFragmentPresenter = new ITPDFragmentPresenter(this);

        itpdFragmentReceiver = new ITPDFragmentReceiver(this, itpdFragmentPresenter);

        if (getActivity() != null) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            getActivity().registerReceiver(itpdFragmentReceiver, intentFilterBckgIntSer);
            getActivity().registerReceiver(itpdFragmentReceiver, intentFilterTopFrg);
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
        if (getActivity() != null && tvITPDLog == null) {
            tvITPDLog = getActivity().findViewById(R.id.tvITPDLog);
            clITPDLog = getActivity().findViewById(R.id.clITPDLog);
        }

        if (tvITPDLog != null) {
            tvITPDLog.setText(getText(R.string.tvITPDDefaultLog) + " " + ITPDVersion);
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
        if (getActivity() != null && tvITPDLog == null) {
            tvITPDLog = getActivity().findViewById(R.id.tvITPDLog);
            clITPDLog = getActivity().findViewById(R.id.clITPDLog);
        }

        if (tvITPDLog != null) {
            tvITPDLog.setText(text);
            tvITPDLog.setGravity(Gravity.NO_GRAVITY);

            if (clITPDLog != null) {
                ConstraintSet set = new ConstraintSet();
                set.clone(clITPDLog);
                set.clear(tvITPDLog.getId(), ConstraintSet.TOP);
                set.applyTo(clITPDLog);
            }
        }
    }

    @Override
    public void setITPDInfoLogText() {
        if (getActivity() != null && tvITPDInfoLog == null && svITPDLog == null) {
            tvITPDInfoLog = getActivity().findViewById(R.id.tvITPDinfoLog);
            svITPDLog = getActivity().findViewById(R.id.svITPDLog);
        }

        if (tvITPDInfoLog!= null && svITPDLog != null) {
            tvITPDInfoLog.setText("");
        }
    }

    @Override
    public void setITPDInfoLogText(Spanned text) {
        if (getActivity() != null && tvITPDInfoLog== null && svITPDLog == null) {
            tvITPDInfoLog = getActivity().findViewById(R.id.tvITPDinfoLog);
            svITPDLog = getActivity().findViewById(R.id.svITPDLog);
        }

        if (tvITPDInfoLog != null && svITPDLog != null) {
            tvITPDInfoLog.setText(text);
            scrollToBottom(svITPDLog);
        }
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
        return getFragmentManager();
    }

    private synchronized void scrollToBottom(ScrollView scrollView) {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
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
            return new PrefManager(context).getBoolPref("DNSCrypt Installed");
        }
        return false;
    }

    private boolean isTorInstalled(Context context) {
        if (context != null) {
            return new PrefManager(context).getBoolPref("Tor Installed");
        }
        return false;
    }

    private boolean isITPDInstalled(Context context) {
        if (context != null) {
            return new PrefManager(context).getBoolPref("I2PD Installed");
        }
        return false;
    }

    private boolean isControlLocked(Context context) {
        if (context instanceof MainActivity && ((MainActivity) context).childLockActive) {
            Toast.makeText(context, context.getText(R.string.action_mode_dialog_locked), Toast.LENGTH_LONG).show();
            return true;
        }

        return false;
    }
}
