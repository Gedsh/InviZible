package pan.alexander.tordnscrypt.dialogs;

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

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

import javax.inject.Inject;

import pan.alexander.tordnscrypt.App;

public abstract class ExtendedDialogFragment extends DialogFragment {

    @Inject
    public Handler handler;

    private int waitForCloseCounter = 3;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
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

        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = assignBuilder();
        if (builder != null) {
            return builder.create();
        } else {
            Log.e(LOG_TAG, "ExtendedDialogFragment fault: please assignBuilder first");
            return super.onCreateDialog(savedInstanceState);
        }
    }

    @Override
    public void show(FragmentManager manager, String tag) {
        try {
            FragmentTransaction ft = manager.beginTransaction();
            ft.add(this, tag);
            ft.commitAllowingStateLoss();
        } catch (IllegalStateException e) {
            Log.w(LOG_TAG, "ExtendedDialogFragment Exception " + e.getMessage() + " " + e.getCause());
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
