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

import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.AGREEMENT_ACCEPTED;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;

import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;

public class AgreementDialog extends ExtendedDialogFragment {

    private boolean exit;

    public static AgreementDialog newInstance() {
        return new AgreementDialog();
    }

    @Override
    public AlertDialog.Builder assignBuilder() {

        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(activity);

        LayoutInflater lInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        PreferenceRepository preferences = App.getInstance().getDaggerComponent().getPreferenceRepository().get();

        if (lInflater == null) {
            preferences.setBoolPreference(AGREEMENT_ACCEPTED, true);
            return null;
        }

        View view = lInflater.inflate(R.layout.agreement_layout, null, false);

        if (view == null) {
            preferences.setBoolPreference(AGREEMENT_ACCEPTED, true);
            return null;
        }

        MaterialButton buttonAccept = view.findViewById(R.id.buttonAcceptAgreement);
        MaterialButton buttonDecline = view.findViewById(R.id.buttonDeclineAgreement);

        alertDialog.setView(view);

        alertDialog.setCancelable(false);

        buttonAccept.setOnClickListener(v -> {
            exit = false;
            preferences.setBoolPreference(AGREEMENT_ACCEPTED, true);
            OnAgreementAcceptedListener listener = getListener(getActivity().getSupportFragmentManager());
            if (listener != null) {
                listener.onAgreementAccepted();
            }
            dismiss();
        });

        buttonDecline.setOnClickListener(v -> {
            exit = true;
            dismiss();
        });

        return alertDialog;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);

        Activity activity = getActivity();
        if (exit && activity != null) {
            activity.finish();
        }
    }

    private OnAgreementAcceptedListener getListener(FragmentManager manager) {
        for (Fragment fragment : manager.getFragments()) {
            if (fragment instanceof OnAgreementAcceptedListener) {
                return (OnAgreementAcceptedListener) fragment;
            }
            getListener(fragment.getChildFragmentManager());
        }
        return null;
    }

    public interface OnAgreementAcceptedListener {
        void onAgreementAccepted();
    }
}
