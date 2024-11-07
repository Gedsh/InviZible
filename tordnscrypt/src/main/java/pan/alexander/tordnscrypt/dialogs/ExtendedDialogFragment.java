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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;

public abstract class ExtendedDialogFragment extends DialogFragment {

    public Handler handler;

    private int waitForOpenCounter = 2;
    private int waitForCloseCounter = 3;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        handler = new Handler(Looper.getMainLooper());
    }

    //Considering the use
    private void blurBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (getDialog() == null) {
                return;
            }
            Activity activity = getDialog().getOwnerActivity();
            if (activity == null) {
                return;
            }
            activity.getWindow().getDecorView().getRootView()
                    .setRenderEffect(
                            RenderEffect.createBlurEffect(
                                    5,
                                    5,
                                    Shader.TileMode.CLAMP
                            )
                    );
        }
    }

    private void unblurBackground() {
        if (getDialog() == null) {
            return;
        }
        Activity activity = getDialog().getOwnerActivity();
        if (activity == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity.getWindow().getDecorView().getRootView()
                    .setRenderEffect(null);
        }
    }

    @Override
    public void onDestroyView() {
        Dialog dialog = getDialog();
        // handles https://code.google.com/p/android/issues/detail?id=17423
        if (dialog != null && getRetainInstance()) {
            dialog.setDismissMessage(null);
        }

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        super.onDestroy();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = assignBuilder();
        if (builder != null) {
            return builder.create();
        } else {
            loge("ExtendedDialogFragment fault: please assignBuilder first");
            return super.onCreateDialog(savedInstanceState);
        }
    }

    @Override
    public void show(@NonNull FragmentManager manager, String tag) {
        try {
            showDialog(manager, tag);
        } catch (IllegalStateException | WindowManager.BadTokenException e) {
            logw("ExtendedDialogFragment show", e);

            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }

            waitForOpenCounter--;
            if (waitForOpenCounter > 0) {
                handler.post(() -> {
                    try {
                        showDialog(manager, tag);
                    } catch (Exception ex) {
                        logw("ExtendedDialogFragment show", ex);
                    }
                });
            } else if (waitForOpenCounter == 0) {
                handler.postDelayed(() -> {
                    try {
                        showDialog(manager, tag);
                    } catch (Exception ex) {
                        logw("ExtendedDialogFragment show", ex);
                    }
                }, 500);
            }
        }
    }

    private void showDialog(FragmentManager manager, String tag) {
        if (manager.isDestroyed()) {
            return;
        }
        manager.executePendingTransactions();
        Fragment fragment = manager.findFragmentByTag(tag);
        if (fragment == null || !fragment.isAdded()) {
            FragmentTransaction ft = manager.beginTransaction();
            ft.add(this, tag);
            ft.commitAllowingStateLoss();
        }
    }

    @Override
    public void dismiss() {
        if (isStateSaved()) {
            if (waitForCloseCounter > 0 && handler != null) {
                handler.postDelayed(this::dismiss, 100);
                waitForCloseCounter--;
            } else {
                super.dismissAllowingStateLoss();
            }
        } else {
            super.dismiss();
        }
    }

    public abstract AlertDialog.Builder assignBuilder();
}
