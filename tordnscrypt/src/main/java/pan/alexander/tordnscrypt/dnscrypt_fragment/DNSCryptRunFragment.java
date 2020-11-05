package pan.alexander.tordnscrypt.dnscrypt_fragment;
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

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.utils.RootExecService;

import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static pan.alexander.tordnscrypt.TopFragment.DNSCryptVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;


public class DNSCryptRunFragment extends Fragment implements DNSCryptFragmentView, View.OnClickListener,
        ViewTreeObserver.OnScrollChangedListener, View.OnTouchListener {


    private Button btnDNSCryptStart;
    private TextView tvDNSStatus;
    private ProgressBar pbDNSCrypt;
    private TextView tvDNSCryptLog;
    private ScrollView svDNSCryptLog;
    private BroadcastReceiver receiver;

    private DNSCryptFragmentPresenter presenter;


    public DNSCryptRunFragment() {
    }


    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_dnscrypt_run, container, false);

        btnDNSCryptStart = view.findViewById(R.id.btnDNSCryptStart);

        //Not required for a portrait orientation, so return
        if (btnDNSCryptStart == null) {
            return view;
        }

        btnDNSCryptStart.setOnClickListener(this);
        btnDNSCryptStart.requestFocus();

        pbDNSCrypt = view.findViewById(R.id.pbDNSCrypt);

        tvDNSCryptLog = view.findViewById(R.id.tvDNSCryptLog);

        svDNSCryptLog = view.findViewById(R.id.svDNSCryptLog);
        svDNSCryptLog.setOnTouchListener(this);
        svDNSCryptLog.getViewTreeObserver().addOnScrollChangedListener(this);

        tvDNSStatus = view.findViewById(R.id.tvDNSStatus);

        setDNSCryptLogViewText();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        //MainFragment do this job for portrait orientation, so return
        if (btnDNSCryptStart == null) {
            return;
        }

        presenter = new DNSCryptFragmentPresenter(this);

        receiver = new DNSCryptFragmentReceiver(this, presenter);

        if (getActivity() != null) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(receiver, intentFilterBckgIntSer);
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(receiver, intentFilterTopFrg);

            presenter.onStart(getActivity());
        }

    }

    @Override
    public void onResume() {
        super.onResume();

        if (TopFragment.logsTextSize != 0f) {
            setLogsTextSize(TopFragment.logsTextSize);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() == null) {
            return;
        }

        try {
            if (receiver != null) {
                LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "DNSCryptRunFragment onStop exception " + e.getMessage() + " " + e.getCause());
        }

        if (presenter != null) {
            presenter.onStop(getActivity());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        btnDNSCryptStart = null;
        tvDNSStatus = null;
        pbDNSCrypt = null;
        tvDNSCryptLog = null;
        svDNSCryptLog = null;

        receiver = null;

        presenter = null;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnDNSCryptStart) {
            presenter.startButtonOnClick(getActivity());
        }
    }

    @Override
    public void setDNSCryptStatus(int resourceText, int resourceColor) {
        tvDNSStatus.setText(resourceText);
        tvDNSStatus.setTextColor(getResources().getColor(resourceColor));
    }

    @Override
    public void setDNSCryptStartButtonEnabled(boolean enabled) {
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
    public void setDNSCryptProgressBarIndeterminate(boolean indeterminate) {
        if (!pbDNSCrypt.isIndeterminate() && indeterminate) {
            pbDNSCrypt.setIndeterminate(true);
        } else if (pbDNSCrypt.isIndeterminate() && !indeterminate){
            pbDNSCrypt.setIndeterminate(false);
        }
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void setDNSCryptLogViewText() {
        tvDNSCryptLog.setText(getText(R.string.tvDNSDefaultLog) + " " + DNSCryptVersion);
    }

    @Override
    public void setDNSCryptLogViewText(Spanned text) {
        tvDNSCryptLog.setText(text);
    }

    @Override
    public Activity getFragmentActivity() {
        return getActivity();
    }

    @Override
    public FragmentManager getFragmentFragmentManager() {
        return getParentFragmentManager();
    }

    public DNSCryptFragmentPresenterCallbacks getPresenter() {
        if (presenter == null && getActivity() instanceof MainActivity && ((MainActivity)getActivity()).getMainFragment() != null) {
            presenter = ((MainActivity)getActivity()).getMainFragment().getDnsCryptFragmentPresenter();
        }

        return presenter;
    }

    @Override
    public void onScrollChanged() {
        if (presenter != null && svDNSCryptLog != null) {
            presenter.dnsCryptLogAutoScrollingAllowed(!svDNSCryptLog.canScrollVertically(1)
                    || !svDNSCryptLog.canScrollVertically(-1));
        }
    }

    @Override
    public void scrollDNSCryptLogViewToBottom() {
        if (svDNSCryptLog == null) {
            return;
        }

        svDNSCryptLog.post(() -> {
            int delta = 0;

            if (svDNSCryptLog == null) {
                return;
            }

            int childIndex= svDNSCryptLog.getChildCount() - 1;

            if (childIndex < 0) {
                return;
            }

            View lastChild = svDNSCryptLog.getChildAt(childIndex);

            if (lastChild != null) {
                int bottom = lastChild.getBottom() + svDNSCryptLog.getPaddingBottom();
                int sy = svDNSCryptLog.getScrollY();
                int sh = svDNSCryptLog.getHeight();
                delta = bottom - (sy + sh);
            }

            if (delta > 0) {
                svDNSCryptLog.smoothScrollBy(0, delta);
            }
        });
    }

    @Override
    public void setLogsTextSize(float size) {
        if (tvDNSCryptLog != null) {
            tvDNSCryptLog.setTextSize(COMPLEX_UNIT_PX, size);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (presenter != null && motionEvent.getPointerCount() == 2) {
            ScaleGestureDetector detector = presenter.getScaleGestureDetector();
            if (detector != null) {
                detector.onTouchEvent(motionEvent);
                return true;
            }
        }
        return false;
    }
}
