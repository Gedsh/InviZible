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

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.dialogs;

import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ITPD_TETHERING;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.TOR_TETHERING;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import java.util.Arrays;
import java.util.List;

import pan.alexander.tordnscrypt.R;
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

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(activity);

        alertDialog.setTitle(R.string.warning);
        alertDialog.setMessage(R.string.config_changes_dialog_message);

        alertDialog.setPositiveButton(R.string.save_changes, (dialog, id) -> {
            if (filePath != null && fileText != null) {
                List<String> lines = Arrays.asList(fileText.split("\n"));
                FileManager.writeToTextFile(activity, filePath, lines, "ignored");
                restartModuleIfRequired();

                pressBack(dialog);
            }
        });

        alertDialog.setNegativeButton(
                R.string.discard_changes,
                (dialog, id) -> pressBack(dialog)
        );

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
            boolean itpdTethering = sharedPreferences.getBoolean(ITPD_TETHERING, false);
            boolean routeAllThroughTorTether = sharedPreferences.getBoolean("pref_common_tor_route_all", false);

            if (torTethering && routeAllThroughTorTether && itpdTethering) {
                ModulesStatus.getInstance().setIptablesRulesUpdateRequested(context, true);
            }
        }
    }

    private void pressBack(DialogInterface dialog) {
        dialog.dismiss();

        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.getSupportFragmentManager().popBackStack();
        }
    }
}
