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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.assistance.AccelerateDevelop;

public class AskAccelerateDevelop extends ExtendedDialogFragment {
    public static DialogFragment getInstance() {
        return new AskAccelerateDevelop();
    }


    @Override
    public AlertDialog.Builder assignBuilder() {

        if (getActivity() == null) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);
        builder.setMessage(R.string.buy_premium_gp)
                .setTitle(getString(R.string.premium))
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    if (getActivity() != null && getActivity() instanceof MainActivity) {
                        MainActivity mainActivity = (MainActivity) getActivity();
                       if (mainActivity.accelerateDevelop != null) {
                           Handler handler = new Handler(Looper.getMainLooper());
                           handler.post(() -> {
                               if (mainActivity.accelerateDevelop != null) {
                                   mainActivity.accelerateDevelop.launchBilling(AccelerateDevelop.mSkuId);
                               }
                           });
                       }
                    }

                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dismiss());

        return builder;
    }
}
