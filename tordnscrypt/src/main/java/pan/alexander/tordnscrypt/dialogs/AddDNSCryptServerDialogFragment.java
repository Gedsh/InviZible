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

import android.app.Dialog;
import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.dnscrypt_servers.DNSServerItem;

import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

public class AddDNSCryptServerDialogFragment extends ExtendedDialogFragment {

    private EditText etOwnServerName;
    private EditText etOwnServerDescription;
    private EditText etOwnServerSDNS;

    private OnServerAddedListener onServerAddedListener;

    public static AddDNSCryptServerDialogFragment getInstance() {
        return new AddDNSCryptServerDialogFragment();
    }

    public interface OnServerAddedListener {
        void onServerAdded(DNSServerItem dnsServerItem);
    }

    public void setOnServerAddListener(OnServerAddedListener onServerAddedListener) {
        this.onServerAddedListener = onServerAddedListener;
    }

    @Override
    public AlertDialog.Builder assignBuilder() {
        if (getActivity() == null) {
            return null;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);
        builder.setTitle(R.string.add_custom_server_title)
                .setView(R.layout.add_own_server)
                .setPositiveButton(R.string.ok, (dialog, which) -> {

                    if (etOwnServerName != null
                            && etOwnServerDescription != null
                            && etOwnServerSDNS != null) {

                        if (!saveOwnDNSCryptServer(getActivity()) && isAdded()) {
                            DialogFragment dialogFragment = NotificationDialogFragment.newInstance(R.string.add_custom_server_error);
                            dialogFragment.show(getParentFragmentManager(), "add_custom_server_error");
                        }
                    }

                })
                .setNegativeButton(R.string.cancel, (dialog, id) -> dismiss());

        return builder;
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog = getDialog();

        if (dialog != null) {
            etOwnServerName = dialog.findViewById(R.id.etOwnServerName);
            etOwnServerDescription = dialog.findViewById(R.id.etOwnServerDescription);
            etOwnServerSDNS = dialog.findViewById(R.id.etOwnServerSDNS);
        }
    }

    private boolean saveOwnDNSCryptServer(Context context) {
        Log.i(LOG_TAG, "Save Own DNSCrypt server");

        String etOwnServerNameText = etOwnServerName.getText().toString().trim();
        String etOwnServerDescriptionText = etOwnServerDescription.getText().toString().trim();
        String etOwnServerSDNSText = etOwnServerSDNS.getText().toString().trim().replace("sdns://", "");

        if (etOwnServerNameText.isEmpty() || etOwnServerSDNSText.length() < 8) {
            return false;
        }

        try {
            Base64.decode(etOwnServerSDNSText.substring(0, 7).getBytes(), 16);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Trying to add wrong DNSCrypt server "
                    + etOwnServerNameText + " " + etOwnServerDescriptionText
                    + " " + etOwnServerSDNSText);
            return false;
        }

        try {
            DNSServerItem item = new DNSServerItem(context, etOwnServerNameText, etOwnServerDescriptionText, etOwnServerSDNSText);
            item.setOwnServer(true);

            if (onServerAddedListener != null) {
                onServerAddedListener.onServerAdded(item);
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Trying to add wrong DNSCrypt server " + e.getMessage() + " "
                    + etOwnServerNameText + " " + etOwnServerDescriptionText
                    + " " + etOwnServerSDNSText);
            return false;
        }

        return true;
    }
}
