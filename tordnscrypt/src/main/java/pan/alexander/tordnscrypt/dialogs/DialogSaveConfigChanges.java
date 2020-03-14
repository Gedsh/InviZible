package pan.alexander.tordnscrypt.dialogs;

import android.content.Intent;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

public class DialogSaveConfigChanges extends ExtendedDialogFragment {

    private String fileText;
    private String filePath;
    private String moduleName;

    public static DialogFragment newInstance() {
        return new DialogSaveConfigChanges();
    }

    @Override
    public AlertDialog.Builder assignBuilder() {
        if (getActivity() == null) {
            return null;
        }

        if (getArguments() != null) {
            fileText = getArguments().getString("fileText");
            filePath = getArguments().getString("filePath");
            moduleName = getArguments().getString("moduleName");
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);

        alertDialog.setTitle(R.string.warning);
        alertDialog.setMessage(R.string.config_changes_dialog_message);

        alertDialog.setPositiveButton(R.string.save_changes, (dialog, id) -> {
            if (filePath != null && fileText != null) {
                List<String> lines = Arrays.asList(fileText.split(System.lineSeparator()));
                FileOperations.writeToTextFile(getActivity(), filePath, lines, "ignored");
                restartModuleIfRequired();
                reopenSettings();
            }
        });

        alertDialog.setNegativeButton(R.string.discard_changes, (dialog, id) -> dialog.cancel());

        return alertDialog;
    }

    private void restartModuleIfRequired() {
        if (getActivity() == null) {
            return;
        }

        boolean dnsCryptRunning = new PrefManager(getActivity()).getBoolPref("DNSCrypt Running");
        boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");
        boolean itpdRunning = new PrefManager(getActivity()).getBoolPref("I2PD Running");

        if (dnsCryptRunning && "DNSCrypt".equals(moduleName)) {
            ModulesRestarter.restartDNSCrypt(getActivity());
        } else if (torRunning && "Tor".equals(moduleName)) {
            ModulesRestarter.restartTor(getActivity());
        } else if (itpdRunning && "ITPD".equals(moduleName)) {
            ModulesRestarter.restartITPD(getActivity());

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            boolean torTethering = sharedPreferences.getBoolean("pref_common_tor_tethering", false) && torRunning;
            boolean itpdTethering = sharedPreferences.getBoolean("pref_common_itpd_tethering", false);
            boolean routeAllThroughTorTether = sharedPreferences.getBoolean("pref_common_tor_route_all", false);

            if (torTethering && routeAllThroughTorTether && itpdTethering) {
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(true);
                ModulesAux.requestModulesStatusUpdate(getActivity());
            }
        }
    }

    private void reopenSettings() {
        if (getActivity() == null) {
            return;
        }

        Intent intent = new Intent(getActivity(), SettingsActivity.class);

        if ("DNSCrypt".equals(moduleName)) {
            intent.setAction("DNS_Pref");
        } else if ("Tor".equals(moduleName)) {
            intent.setAction("Tor_Pref");
        } else if ("ITPD".equals(moduleName)) {
            intent.setAction("I2PD_Pref");
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        getActivity().overridePendingTransition(0, 0);
        getActivity().finish();

        getActivity().overridePendingTransition(0, 0);
        startActivity(intent);
    }
}
