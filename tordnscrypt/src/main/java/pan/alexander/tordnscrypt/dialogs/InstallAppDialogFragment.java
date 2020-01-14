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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;

public class InstallAppDialogFragment extends ExtendedDialogFragment {

    public static DialogFragment getInstance() {
        return new InstallAppDialogFragment();
    }


    @Override
    public AlertDialog.Builder assignBuilder() {

        if (getActivity() == null) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);
        builder.setMessage(R.string.install_message)
                .setTitle(getString(R.string.install))
                .setPositiveButton(R.string.install, (dialog, which) -> {
                    if (getActivity() != null && getActivity() instanceof MainActivity) {
                        MainActivity mainActivity = (MainActivity) getActivity();
                        TopFragment topFragment = mainActivity.getTopFragment();
                        topFragment.startInstallation();
                    }

                })
                .setNegativeButton(R.string.exit, (dialog, id) -> {
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                });

        return builder;
    }
}
