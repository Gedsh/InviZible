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
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import pan.alexander.tordnscrypt.R;

public class NotificationDialogFragment extends ExtendedDialogFragment {
    private int message = 0;
    private String messageStr = "";

    public static DialogFragment newInstance(int messageResource) {

        NotificationDialogFragment infoDialog = new NotificationDialogFragment();
        Bundle args = new Bundle();
        args.putInt("message", messageResource);
        infoDialog.setArguments(args);
        return infoDialog;
    }

    public static DialogFragment newInstance(String message) {

        NotificationDialogFragment infoDialog = new NotificationDialogFragment();
        Bundle args = new Bundle();
        args.putString("messageStr", message);
        infoDialog.setArguments(args);
        return infoDialog;
    }

    @Override
    public AlertDialog.Builder assignBuilder() {

        if (getActivity() == null) {
            return null;
        }

        if (getArguments() != null) {
            message = getArguments().getInt("message");
            messageStr = getArguments().getString("messageStr");
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme);

        if (message == 0) {
            alertDialog.setMessage(messageStr);
        } else {
            alertDialog.setMessage(message);
        }

        alertDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dismiss();
            }
        });

        return alertDialog;
    }
}
