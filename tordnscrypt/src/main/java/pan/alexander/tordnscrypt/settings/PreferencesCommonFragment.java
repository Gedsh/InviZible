package pan.alexander.tordnscrypt.settings;
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
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.TopFragment;
import pan.alexander.tordnscrypt.utils.NoRootService;
import pan.alexander.tordnscrypt.utils.NotificationHelper;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

/**
 * A simple {@link Fragment} subclass.
 */
public class PreferencesCommonFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {
    String torTransPort;
    BufferedReader br = null;
    FileInputStream fstream = null;
    PrintWriter writer;

    public PreferencesCommonFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource

        addPreferencesFromResource(R.xml.preferences_common);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(R.string.drawer_menu_commonSettings);

        Preference swShowNotification = findPreference("swShowNotification");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            swShowNotification.setEnabled(false);
        }
        swShowNotification.setOnPreferenceChangeListener(this);

        Preference swTorTethering = findPreference("pref_common_tor_tethering");
        swTorTethering.setOnPreferenceChangeListener(this);

        Preference swRouteAllThroughTor = findPreference("pref_common_tor_route_all");
        swRouteAllThroughTor.setOnPreferenceChangeListener(this);

        Preference swITPDTethering = findPreference("pref_common_itpd_tethering");
        swITPDTethering.setOnPreferenceChangeListener(this);

        Preference pref_common_block_http = findPreference("pref_common_block_http");
        pref_common_block_http.setOnPreferenceChangeListener(this);

        PathVars pathVars = new PathVars(getActivity());
        torTransPort = pathVars.torTransPort;

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        if (shPref.getBoolean("pref_common_tor_route_all",false)) {
            findPreference("prefTorSiteUnlockTether").setEnabled(false);
        } else {
            findPreference("prefTorSiteUnlockTether").setEnabled(true);
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Verifier verifier = new Verifier(getActivity());
                    String appSign = verifier.getApkSignatureZipModern();
                    String appSignAlt = verifier.getApkSignature();
                    if (!verifier.decryptStr(wrongSign,appSign,appSignAlt).equals(TOP_BROADCAST)) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(),getText(R.string.verifier_error).toString(),"5889");
                        if (notificationHelper != null) {
                            notificationHelper.show(getFragmentManager(),NotificationHelper.TAG_HELPER);
                        }
                    }

                } catch (Exception e) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(),getText(R.string.verifier_error).toString(),"5804");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(),NotificationHelper.TAG_HELPER);
                    }
                    Log.e(LOG_TAG,"PreferencesCommonFragment fault "+e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                            Arrays.toString(e.getStackTrace()));
                }
            }
        });
        thread.start();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case "swShowNotification":
                if (!Boolean.valueOf(newValue.toString())) {
                    Intent intent = new Intent(getActivity(), NoRootService.class);
                    intent.setAction(NoRootService.actionDismissNotification);
                    getActivity().startService(intent);
                    InfoNotificationProtectService infoNotification = new InfoNotificationProtectService();
                    infoNotification.show(getFragmentManager(), "dialogProtectService");
                }
                break;
            case "pref_common_tor_tethering":
                allowTorTethering(Boolean.valueOf(newValue.toString()));
                if (new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    TopFragment.NotificationDialogFragment commandResult =
                            TopFragment.NotificationDialogFragment.newInstance(getText(R.string.pref_common_restart_tor).toString());
                    commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                }
                break;
            case "pref_common_itpd_tethering":
                allowITPDTethering(Boolean.valueOf(newValue.toString()));
                if (new PrefManager(getActivity()).getBoolPref("I2PD Running")) {
                    TopFragment.NotificationDialogFragment commandResult =
                            TopFragment.NotificationDialogFragment.newInstance(getText(R.string.pref_common_restart_itpd).toString());
                    commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                }
                break;
            case "pref_common_tor_route_all":
                if (Boolean.valueOf(newValue.toString())) {
                    findPreference("prefTorSiteUnlockTether").setEnabled(false);
                } else {
                    findPreference("prefTorSiteUnlockTether").setEnabled(true);
                }
                if (new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    TopFragment.NotificationDialogFragment commandResult =
                            TopFragment.NotificationDialogFragment.newInstance(getText(R.string.pref_common_restart_tor).toString());
                    commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                }
                break;
            case "pref_common_block_http":
                if (new PrefManager(getActivity()).getBoolPref("DNSCrypt Running")) {
                    TopFragment.NotificationDialogFragment commandResult =
                            TopFragment.NotificationDialogFragment.newInstance(getText(R.string.pref_common_restart_dnscrypt).toString());
                    commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                } else if (new PrefManager(getActivity()).getBoolPref("Tor Running")) {
                    TopFragment.NotificationDialogFragment commandResult =
                            TopFragment.NotificationDialogFragment.newInstance(getText(R.string.pref_common_restart_tor).toString());
                    commandResult.show(getFragmentManager(),TopFragment.NotificationDialogFragment.TAG_NOT_FRAG);
                }
                break;
        }
        return true;
    }

    private void allowTorTethering(boolean allow) {

        boolean itpdTethering = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("pref_common_itpd_tethering",false);
        PathVars pathVars = new PathVars(getActivity());
        String iptablesPath = pathVars.iptablesPath;
        String itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        String[] tetheringCommands;
        if (!allow) {
            if (!itpdTethering) {
                tetheringCommands = new String[]{
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward"
                };
            } else {
                tetheringCommands = new String[]{
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i wlan0 -p tcp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -A tordnscrypt_prerouting -i wlan0 -p udp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -A tordnscrypt_forward -p udp --dport 53 -j ACCEPT"
                };
            }
            RootCommands rootCommands = new RootCommands(tetheringCommands);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.NullMark);
            RootExecService.performAction(getActivity(),intent);
        }


        try {
            String appUID = new PrefManager(getActivity()).getStrPref("appUID");
            pathVars = new PathVars(getActivity());
            String filePath = pathVars.appDataDir+"/app_data/tor/tor.conf";
            String[] commands = {pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + filePath,
                    "restorecon " + filePath};
            RootCommands rootCommands = new RootCommands(commands);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.NullMark);
            RootExecService.performAction(getActivity(),intent);


            File f = new File(filePath);
            if (f.isFile() && f.setReadable(true) && f.setWritable(true))
                Log.i(LOG_TAG,"PreferencesCommonFragment allowTorTethering Pass");

            fstream = new FileInputStream(filePath);
            br = new BufferedReader(new InputStreamReader(fstream));
            List<String> lines = new LinkedList<>();

            for(String tmp; (tmp = br.readLine()) != null;) {
                if (allow && tmp.contains("TransPort")) {
                    tmp = "TransPort " + "0.0.0.0:" + torTransPort;
                } else if (!allow && tmp.contains("TransPort")) {
                    tmp = tmp.replaceAll(" .+:"," ");
                }
                lines.add(tmp);
            }
            if (fstream!= null)fstream.close();
            fstream = null;
            if (br != null)br.close();
            br = null;

            writer = new PrintWriter(filePath);
            for (String line:lines)
                writer.println(line);
            writer.close();
            writer = null;

        } catch (IOException e) {
            Log.e(LOG_TAG,"PreferencesCommonFragment allowTorTethering Exception " + e.getMessage());
        } finally {
            try {
                if (fstream!= null)fstream.close();
                if (br != null)br.close();
                if (writer != null)writer.close();
            } catch (IOException ex) {
                Log.e(LOG_TAG,"PreferencesCommonFragment allowTorTethering Error when close file" + ex.getMessage());
            }
        }

    }

    private void allowITPDTethering(boolean allow) {
        PathVars pathVars = new PathVars(getActivity());
        String iptablesPath = pathVars.iptablesPath;
        String itpdHttpProxyPort = pathVars.itpdHttpProxyPort;
        String[] tetheringCommands;
        boolean torTethering = PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("pref_common_tor_tethering",false);
        if (!allow) {
            if (!torTethering) {
                tetheringCommands = new String[]{
                        iptablesPath + "iptables -t nat -F tordnscrypt_prerouting",
                        iptablesPath + "iptables -F tordnscrypt_forward",
                        iptablesPath + "iptables -t nat -D PREROUTING -j tordnscrypt_prerouting",
                        iptablesPath + "iptables -D FORWARD -j tordnscrypt_forward"
                };
            } else {
                tetheringCommands = new String[]{
                        iptablesPath + "iptables -t nat -D tordnscrypt_prerouting -i wlan0 -p tcp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                        iptablesPath + "iptables -t nat -D tordnscrypt_prerouting -i wlan0 -p udp -d 10.191.0.1 -j REDIRECT --to-ports "+ itpdHttpProxyPort,
                };
            }

            RootCommands rootCommands = new RootCommands(tetheringCommands);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.NullMark);
            RootExecService.performAction(getActivity(),intent);
        }


        try {
            pathVars = new PathVars(getActivity());
            String appUID = new PrefManager(getActivity()).getStrPref("appUID");
            String filePath = pathVars.appDataDir+"/app_data/i2pd/i2pd.conf";
            String[] commands = {pathVars.busyboxPath + "chown -R " + appUID + "." + appUID + " " + filePath,
                    "restorecon " + filePath};
            RootCommands rootCommands = new RootCommands(commands);
            Intent intent = new Intent(getActivity(), RootExecService.class);
            intent.setAction(RootExecService.RUN_COMMAND);
            intent.putExtra("Commands",rootCommands);
            intent.putExtra("Mark", RootExecService.NullMark);
            RootExecService.performAction(getActivity(),intent);


            File f = new File(filePath);
            if (f.isFile() && f.setReadable(true) && f.setWritable(true))
                Log.i(LOG_TAG,"PreferencesCommonFragment allowITPDTethering Pass");

            fstream = new FileInputStream(filePath);
            br = new BufferedReader(new InputStreamReader(fstream));
            List<String> lines = new LinkedList<>();
            String head = "";

            for(String tmp; (tmp = br.readLine()) != null;) {
                if (tmp.matches("\\[.+]"))
                head = tmp.replace("[","").replace("]","");
                if (head.equals("httpproxy") && allow && tmp.contains("address")) {
                    tmp = tmp.replace("127.0.0.1","0.0.0.0");
                } else if (head.equals("httpproxy") && !allow&& tmp.contains("address")) {
                    tmp = tmp.replace("0.0.0.0","127.0.0.1");
                }
                lines.add(tmp);
            }
            if (fstream!= null)fstream.close();
            fstream = null;
            if (br != null)br.close();
            br = null;

            writer = new PrintWriter(filePath);
            for (String line:lines)
                writer.println(line);
            writer.close();
            writer = null;

        } catch (IOException e) {
            Log.e(LOG_TAG,"PreferencesCommonFragment allowITPDTethering Exception " + e.getMessage());
        } finally {
            try {
                if (fstream!= null)fstream.close();
                if (br != null)br.close();
                if (writer != null)writer.close();
            } catch (IOException ex) {
                Log.e(LOG_TAG,"PreferencesCommonFragment allowITPDTethering Error when close file" + ex.getMessage());
            }
        }

    }

    public static class InfoNotificationProtectService extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.pref_common_notification_helper)
                    .setTitle(R.string.helper_dialog_title)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final String packageName = getActivity().getPackageName();
                            final PowerManager pm = (PowerManager) getActivity().getSystemService(Context.POWER_SERVICE);
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (pm != null && !pm.isIgnoringBatteryOptimizations(packageName))) {
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                    getActivity().startActivity(intent);
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG,"PreferencesCommonFragment InfoNotificationProtectService Exception "+e.getMessage());
                            }
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }


}
