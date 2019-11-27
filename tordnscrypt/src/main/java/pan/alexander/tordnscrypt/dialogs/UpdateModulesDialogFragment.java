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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.content.DialogInterface;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.modules.ModulesKiller;

public class UpdateModulesDialogFragment extends ExtendedDialogFragment {

    public static DialogFragment getInstance() {
        return new UpdateModulesDialogFragment();
    }

    @Override
    public AlertDialog.Builder assignBuilder() {

        if (getActivity() == null) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme);

        builder.setMessage(R.string.update_core_message)
                .setTitle(R.string.update_core_title)
                .setPositiveButton(R.string.update_core_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (getActivity() == null) {
                            return;
                        }

                        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");
                        boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");
                        boolean itpdRunning = new PrefManager(getActivity()).getBoolPref("I2PD Running");

                        if (dnsCryptRunning) {
                            new PrefManager(getActivity()).setBoolPref("DNSCrypt Running", false);
                            ModulesKiller.stopDNSCrypt(getActivity());
                        }

                        if (torRunning) {
                            new PrefManager(getActivity()).setBoolPref("Tor Running", false);
                            ModulesKiller.stopTor(getActivity());
                        }

                        if (itpdRunning) {
                            new PrefManager(getActivity()).setBoolPref("I2PD Running", false);
                            ModulesKiller.stopITPD(getActivity());
                        }


                        if (getActivity() != null) {
                            new PrefManager(getActivity()).setBoolPref("DNSCrypt Installed",false);
                            new PrefManager(getActivity()).setBoolPref("Tor Installed",false);
                            new PrefManager(getActivity()).setBoolPref("I2PD Installed",false);
                            DialogFragment dialogShowSU = NotificationDialogFragment.newInstance(R.string.update_core_restart);
                            if (getFragmentManager() != null) {
                                dialogShowSU.show(getFragmentManager(), "NotificationDialogFragment");
                            }
                        }

                    }
                })
                .setNegativeButton(R.string.update_core_no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dismiss();
                    }
                })
                .setNeutralButton(R.string.update_core_not_show_again, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (getActivity() != null) {
                            new PrefManager(getActivity()).setBoolPref("UpdateNotAllowed",true);
                        }
                    }
                });

        return builder;
    }
}
