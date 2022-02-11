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

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.update.UpdateService;

public class NewUpdateDialogFragment extends ExtendedDialogFragment {

    public static final String TAG_NOT_FRAG = "NewUpdateDialogFragment";

    private String mMessageToDisplay = "";
    private String updateStr = "";
    private String updateFile = "";
    private String hash = "";

    public static NewUpdateDialogFragment newInstance(String message, String updateStr, String updateFile, String hash) {

        NewUpdateDialogFragment updateDialog = new NewUpdateDialogFragment();
        Bundle args = new Bundle();
        args.putString("message", message);
        args.putString("updateStr", updateStr);
        args.putString("updateFile", updateFile);
        args.putString("hash", hash);
        updateDialog.setArguments(args);

        return updateDialog;
    }

    @Override
    public AlertDialog.Builder assignBuilder() {

        if (getActivity() == null) {
            return null;
        }

        if (getArguments() != null) {
            mMessageToDisplay = getArguments().getString("message");
            updateStr = getArguments().getString("updateStr");
            updateFile = getArguments().getString("updateFile");
            hash = getArguments().getString("hash");
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);
        alertDialog.setMessage(mMessageToDisplay)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> {
                    Intent intent = new Intent(getActivity(), UpdateService.class);
                    intent.setAction(UpdateService.DOWNLOAD_ACTION);
                    intent.putExtra("url", "https://invizible.net/?wpdmdl="+updateStr);
                    intent.putExtra("file", updateFile);
                    intent.putExtra("hash",hash);

                    if (getActivity() != null) {
                        getActivity().startService(intent);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dismiss());

        return alertDialog;
    }
}
