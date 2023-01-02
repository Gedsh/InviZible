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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.itpd_fragment;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
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
import pan.alexander.tordnscrypt.utils.root.RootExecService;

import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static pan.alexander.tordnscrypt.TopFragment.ITPDVersion;
import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;


public class ITPDRunFragment extends Fragment implements ITPDFragmentView, View.OnClickListener,
        ViewTreeObserver.OnScrollChangedListener, View.OnTouchListener {

    private Button btnITPDStart;
    private TextView tvITPDStatus;
    private ProgressBar pbITPD;
    private TextView tvITPDLog;
    private TextView tvITPDinfoLog;
    private ScrollView svITPDLog;

    private ITPDFragmentPresenter presenter;
    private ITPDFragmentReceiver receiver;

    public ITPDRunFragment() {
    }

    @SuppressLint("SetTextI18n")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_itpd_run, container, false);

        btnITPDStart = view.findViewById(R.id.btnITPDStart);

        //Not required for a portrait orientation, so return
        if (btnITPDStart == null) {
            return view;
        }

        btnITPDStart.setOnClickListener(this);

        pbITPD = view.findViewById(R.id.pbITPD);

        svITPDLog = view.findViewById(R.id.svITPDLog);

        //Fix auto scrolling on launch
        if (svITPDLog != null) {
            svITPDLog.postDelayed(() ->
            {
                if (svITPDLog != null) {
                    ViewTreeObserver observer = svITPDLog.getViewTreeObserver();

                    if (observer != null) {
                        observer.addOnScrollChangedListener(this);
                    }
                }

            }, 5000);
        }

        tvITPDLog = view.findViewById(R.id.tvITPDLog);
        tvITPDLog.setMovementMethod(ScrollingMovementMethod.getInstance());

        tvITPDinfoLog = view.findViewById(R.id.tvITPDinfoLog);

        tvITPDStatus = view.findViewById(R.id.tvI2PDStatus);

        setITPDLogViewText();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        //MainFragment do this job for portrait orientation, so return
        if (btnITPDStart == null) {
            return;
        }

        presenter = new ITPDFragmentPresenter(this);

        receiver = new ITPDFragmentReceiver(this, presenter);

        Context context = getActivity();
        if (context != null) {
            IntentFilter intentFilterBckgIntSer = new IntentFilter(RootExecService.COMMAND_RESULT);
            IntentFilter intentFilterTopFrg = new IntentFilter(TOP_BROADCAST);

            LocalBroadcastManager.getInstance(context).registerReceiver(receiver, intentFilterBckgIntSer);
            LocalBroadcastManager.getInstance(context).registerReceiver(receiver, intentFilterTopFrg);

            presenter.onStart();
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

        try {
            Context context = getActivity();
            if (context != null && receiver != null) {
                LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "ITPDRunFragment onStop exception " + e.getMessage() + " " + e.getCause());
        }

        if (presenter != null) {
            presenter.onStop();
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (svITPDLog != null) {
            svITPDLog.setOnTouchListener(null);

            ViewTreeObserver observer = svITPDLog.getViewTreeObserver();

            if (observer != null) {
                observer.removeOnScrollChangedListener(this);
            }
        }

        btnITPDStart = null;
        tvITPDStatus = null;
        pbITPD = null;
        tvITPDLog = null;
        tvITPDinfoLog = null;
        svITPDLog = null;

        receiver = null;

        presenter = null;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnITPDStart) {
            presenter.startButtonOnClick();
        }
    }

    @Override
    public void setITPDStatus(int resourceText, int resourceColor) {
        tvITPDStatus.setText(resourceText);
        tvITPDStatus.setTextColor(getResources().getColor(resourceColor));
    }

    @Override
    public void setITPDStartButtonEnabled(boolean enabled) {
        if (btnITPDStart.isEnabled() && !enabled) {
            btnITPDStart.setEnabled(false);
        } else if (!btnITPDStart.isEnabled() && enabled) {
            btnITPDStart.setEnabled(true);
        }
    }

    @Override
    public void setStartButtonText(int textId) {
        btnITPDStart.setText(textId);
    }

    @Override
    public void setITPDProgressBarIndeterminate(boolean indeterminate) {
        if (!pbITPD.isIndeterminate() && indeterminate) {
            pbITPD.setIndeterminate(true);
        } else if (pbITPD.isIndeterminate() && !indeterminate) {
            pbITPD.setIndeterminate(false);
        }
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void setITPDLogViewText() {
        tvITPDLog.setText(getText(R.string.tvITPDDefaultLog) + " " + ITPDVersion);
    }

    @Override
    public void setITPDLogViewText(Spanned text) {
        tvITPDLog.setText(text);
    }

    @Override
    @SuppressLint("SetTextI18n")
    public void setITPDInfoLogText() {
        tvITPDinfoLog.setText("");
    }

    @Override
    public void setITPDInfoLogText(Spanned text) {

        tvITPDinfoLog.setText(text);

        Rect hitRect = new Rect();
        svITPDLog.getHitRect(hitRect);

        if (hitRect.height() == 0) {
            return;
        }

        if (hitRect.height() <= tvITPDinfoLog.getMinHeight()) {
            if (svITPDLog.getVisibility() == View.VISIBLE) {
                svITPDLog.setVisibility(View.INVISIBLE);
            }
        } else {
            if (svITPDLog.getVisibility() == View.INVISIBLE) {
                svITPDLog.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void setLogsTextSize(float size) {
        if (tvITPDLog != null) {
            tvITPDLog.setTextSize(COMPLEX_UNIT_PX, size);
        }

        if (tvITPDinfoLog != null) {
            tvITPDinfoLog.setTextSize(COMPLEX_UNIT_PX, size);
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

    @Override
    public Activity getFragmentActivity() {
        return getActivity();
    }

    @Override
    public FragmentManager getFragmentFragmentManager() {
        return getParentFragmentManager();
    }

    public ITPDFragmentPresenterInterface getPresenter() {
        Activity activity = getActivity();
        if (presenter == null && activity instanceof MainActivity && ((MainActivity) activity).getMainFragment() != null) {
            presenter = ((MainActivity) activity).getMainFragment().getITPDFragmentPresenter();
        }

        return presenter;
    }

    @Override
    public void onScrollChanged() {
        if (presenter != null && svITPDLog != null) {
            presenter.itpdLogAutoScrollingAllowed(!svITPDLog.canScrollVertically(1)
                    || !svITPDLog.canScrollVertically(-1));
        }
    }

    @Override
    public void scrollITPDLogViewToBottom() {
        if (svITPDLog == null) {
            return;
        }

        svITPDLog.post(() -> {

            if (svITPDLog == null) {
                return;
            }

            svITPDLog.computeScroll();

            int delta = 0;

            int childIndex = svITPDLog.getChildCount() - 1;

            if (childIndex < 0) {
                return;
            }

            View lastChild = svITPDLog.getChildAt(childIndex);

            if (lastChild != null) {
                int bottom = lastChild.getBottom() + svITPDLog.getPaddingBottom();
                int sy = svITPDLog.getScrollY();
                int sh = svITPDLog.getHeight();
                delta = bottom - (sy + sh);
            }

            if (delta > 0) {
                svITPDLog.smoothScrollBy(0, delta);
            }
        });
    }
}
