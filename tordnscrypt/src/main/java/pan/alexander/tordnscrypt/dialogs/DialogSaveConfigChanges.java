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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.modules.ModulesAux;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

public class DialogSaveConfigChanges extends ExtendedDialogFragment {

    private String fileText;
    private String filePath;
    private String moduleName;

    public static DialogFragment newInstance() {
        return new DialogSaveConfigChanges();
    }

    @Override
    public AlertDialog.Builder assignBuilder() {
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        if (getArguments() != null) {
            fileText = getArguments().getString("fileText");
            filePath = getArguments().getString("filePath");
            moduleName = getArguments().getString("moduleName");
        }

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);

        alertDialog.setTitle(R.string.warning);
        alertDialog.setMessage(R.string.config_changes_dialog_message);

        alertDialog.setPositiveButton(R.string.save_changes, (dialog, id) -> {
            if (filePath != null && fileText != null) {
                List<String> lines = Arrays.asList(fileText.split("\n"));
                FileManager.writeToTextFile(activity, filePath, lines, "ignored");
                restartModuleIfRequired();

                Looper looper = Looper.getMainLooper();
                Handler handler = null;
                if (looper != null) {
                    handler = new Handler(looper);
                }

                if (handler != null) {
                    handler.postDelayed(() -> reopenSettings(activity), 300);
                }
            }
        });

        alertDialog.setNegativeButton(R.string.discard_changes, (dialog, id) -> dialog.cancel());

        return alertDialog;
    }

    private void restartModuleIfRequired() {

        Context context = getActivity();
        if (context == null) {
            return;
        }

        boolean dnsCryptRunning = ModulesAux.isDnsCryptSavedStateRunning();
        boolean torRunning = ModulesAux.isTorSavedStateRunning();
        boolean itpdRunning = ModulesAux.isITPDSavedStateRunning();

        if (dnsCryptRunning && "DNSCrypt".equals(moduleName)) {
            ModulesRestarter.restartDNSCrypt(context);
        } else if (torRunning && "Tor".equals(moduleName)) {
            ModulesRestarter.restartTor(context);
        } else if (itpdRunning && "ITPD".equals(moduleName)) {
            ModulesRestarter.restartITPD(context);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            boolean torTethering = sharedPreferences.getBoolean(TOR_TETHERING, false) && torRunning;
            boolean itpdTethering = sharedPreferences.getBoolean("pref_common_itpd_tethering", false);
            boolean routeAllThroughTorTether = sharedPreferences.getBoolean("pref_common_tor_route_all", false);

            if (torTethering && routeAllThroughTorTether && itpdTethering) {
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
            }
        }
    }

    private void reopenSettings(Activity activity) {

        if (activity == null) {
            return;
        }

        Intent intent = new Intent(activity, SettingsActivity.class);

        if ("DNSCrypt".equals(moduleName)) {
            intent.setAction("DNS_Pref");
        } else if ("Tor".equals(moduleName)) {
            intent.setAction("Tor_Pref");
        } else if ("ITPD".equals(moduleName)) {
            intent.setAction("I2PD_Pref");
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        activity.overridePendingTransition(0, 0);
        activity.finish();

        activity.overridePendingTransition(0, 0);
        activity.startActivity(intent);
    }
}
