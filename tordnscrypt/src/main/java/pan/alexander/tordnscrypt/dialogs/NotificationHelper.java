package pan.alexander.tordnscrypt.utils;
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Objects;

import pan.alexander.tordnscrypt.R;

public class NotificationHelper extends DialogFragment {

    private String tag = "";
    private static String message = "";
    public static final String TAG_HELPER = "pan.alexander.tordnscrypt.HELPER_NOTIFICATION";
    private static NotificationHelper notificationHelper = null;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomDialogTheme);
        builder.setMessage(message)
                .setTitle(R.string.helper_dialog_title)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                        notificationHelper = null;
                    }
                })
                .setNegativeButton(R.string.dont_show, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        new PrefManager(getActivity()).setBoolPref("helper_no_show_"+tag,true);
                        dismiss();
                        notificationHelper = null;
                    }
                });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Objects.requireNonNull(getDialog().getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    public static NotificationHelper setHelperMessage(Context context, String message, String preferenceTag) {

        try {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            if ((!new PrefManager(context).getBoolPref("helper_no_show_"+preferenceTag)
                    || sharedPreferences.getBoolean("pref_common_show_help",false)
                    || preferenceTag.matches("\\d+"))
                    && notificationHelper == null) {
                notificationHelper = new NotificationHelper();
                notificationHelper.tag = preferenceTag;
                NotificationHelper.message = message;
                return notificationHelper;
            }
        } catch (Exception e) {
            Log.e("TPDClogs",e.getMessage());
        }

        return null;
    }

}
