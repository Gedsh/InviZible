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

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import javax.inject.Inject;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesAux;

public class UpdateModulesDialogFragment extends ExtendedDialogFragment {

    public static DialogFragment getInstance() {
        return new UpdateModulesDialogFragment();
    }

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public AlertDialog.Builder assignBuilder() {

        if (getActivity() == null) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);

        builder.setMessage(R.string.update_core_message)
                .setTitle(R.string.update_core_title)
                .setPositiveButton(R.string.update_core_yes, (dialog, which) -> {

                    if (getActivity() == null) {
                        return;
                    }

                    ModulesAux.stopModulesIfRunning(getActivity());

                    if (getActivity() != null) {
                        preferenceRepository.get().setBoolPreference("DNSCrypt Installed",false);
                        preferenceRepository.get().setBoolPreference("Tor Installed",false);
                        preferenceRepository.get().setBoolPreference("I2PD Installed",false);
                        DialogFragment dialogShowSU = NotificationDialogFragment.newInstance(R.string.update_core_restart);
                        if (getFragmentManager() != null) {
                            dialogShowSU.show(getFragmentManager(), "NotificationDialogFragment");
                        }
                    }

                })
                .setNegativeButton(R.string.update_core_no, (dialog, id) -> dismiss())
                .setNeutralButton(R.string.update_core_not_show_again, (dialogInterface, i) -> {
                    if (getActivity() != null) {
                        preferenceRepository.get().setBoolPreference("UpdateNotAllowed",true);
                    }
                });

        return builder;
    }
}
