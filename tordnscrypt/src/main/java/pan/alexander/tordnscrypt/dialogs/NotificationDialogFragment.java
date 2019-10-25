package pan.alexander.tordnscrypt.dialogs.simpleTextDialogs;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.ExtendedDialogFragment;

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
