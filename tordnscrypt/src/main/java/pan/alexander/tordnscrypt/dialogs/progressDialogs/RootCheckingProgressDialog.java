package pan.alexander.tordnscrypt.dialogs;

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.widget.ProgressBar;

import pan.alexander.tordnscrypt.R;

public class ModernProgressDialog extends ExtendedDialogFragment {
    int titleResource;
    int messageResource;
    int iconResource;
    int styleResource;
    boolean cancelable = true;

    public static DialogFragment getInstance(String title, String message, int iconResource, int styleResource, boolean cancelable) {
        DialogFragment modernProgressDialog = new ModernProgressDialog();

        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        args.putInt("iconResource", iconResource);
        args.putInt("styleResource", styleResource);
        args.putBoolean("cancelable", cancelable);

        modernProgressDialog.setArguments(args);

        return modernProgressDialog;
    }

    @Override
    public AlertDialog.Builder assignBuilder() {
        Bundle args = getArguments();
        if (args != null) {
            titleResource = args.getInt("title");
            messageResource = args.getInt("message");
            iconResource = args.getInt("iconResource");
            styleResource = args.getInt("styleResource");
            cancelable = args.getBoolean("cancelable");
        }


        if (getActivity() == null) {
            return null;
        }

        AlertDialog.Builder builder;
        if (styleResource != 0) {
            builder = new AlertDialog.Builder(getActivity());
        } else {
            builder = new AlertDialog.Builder(getActivity(), styleResource);
        }

        if (titleResource != 0) {
            builder.setTitle(titleResource);
        }

        if (messageResource != 0) {
            builder.setMessage(messageResource);
        }

        if (iconResource != 0) {
            builder.setIcon(iconResource);
        }

        ProgressBar progressBar = new ProgressBar(getActivity(),null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setBackgroundResource(R.drawable.background_10dp_padding);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);

        builder.setCancelable(cancelable);

        return builder;
    }
}
