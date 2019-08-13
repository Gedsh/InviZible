package pan.alexander.tordnscrypt;
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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.NotificationHelper;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Registration;
import pan.alexander.tordnscrypt.utils.RootCommands;
import pan.alexander.tordnscrypt.utils.RootExecService;
import pan.alexander.tordnscrypt.utils.UpdateCheck;
import pan.alexander.tordnscrypt.utils.UpdateService;
import pan.alexander.tordnscrypt.utils.Verifier;


public class TopFragment extends Fragment implements View.OnClickListener {


    public static String DNSCryptVersion = "2.0.25";
    public static String TorVersion = "4.0.4";
    public static String ITPDVersion = "2.27.0";

    public static String appProcVersion = "armv7a";
    public static String appVersion = "lite";
    final static String LOG_TAG = RootExecService.LOG_TAG;
    public boolean rootOK = false;
    public boolean bbOK = false;
    public  String verSU = null;
    public  String verBB = null;
    private String suVersion = null;
    private List<String> suResult = null;
    private List<String> bbResult = null;
    private View view = null;
    public static String TOP_BROADCAST = "pan.alexander.tordnscrypt.action.TOP_BROADCAST";
    private Timer timer;
    private static DialogInterface dialogInterface;
    public static String appSign;
    public final int UPDATES_CHECK_INTERVAL_HOURS = 24;
    public UpdateCheck updateCheck;
    public static String wrongSign;
    public static boolean debug = false;



    public TopFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

        appVersion = getString(R.string.appVersion);
        appProcVersion = getString(R.string.appProcVersion);

        Chkroot ckroot = new Chkroot();
        ckroot.execute();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_top, container, false);

        TextView tvDeviceRoot =  view.findViewById(R.id.tvDeviceRoot);
        TextView tvBBox =  view.findViewById(R.id.tvBBox);

        tvDeviceRoot.setOnClickListener(this);
        tvBBox.setOnClickListener(this);

        return view;
    }

    public void onResume() {
        super.onResume();

        if (new PrefManager(getActivity()).getBoolPref("rootOK"))
            ((ImageView)view.findViewById(R.id.imDeviceRoot)).setImageResource(R.drawable.ic_top_good);

        if (new PrefManager(getActivity()).getBoolPref("bbOK"))
            ((ImageView)view.findViewById(R.id.imBBox)).setImageResource(R.drawable.ic_top_good);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (dialogInterface!=null){
            dialogInterface.dismiss();
        }
        if (updateCheck!=null && updateCheck.context!=null)
            updateCheck.context=null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer!=null)
            timer.cancel();
    }

    //Check if root available
    @SuppressLint("StaticFieldLeak")
    private class Chkroot extends AsyncTask<Void, Void, Void> {
        private boolean suAvailable = false;

        @Override
        protected void onPreExecute() {
            dialogInterface = modernProgressDialog();
        }

        @SuppressWarnings("deprecation")
        @Override
        protected Void doInBackground(Void... params) {
            try {
                suAvailable = Shell.SU.available();
            } catch (Exception e) {
                Log.e(LOG_TAG,"Top Fragment doInBackground suAvailable Exception "+e.getMessage() + " " + e.getCause());
                if (getActivity()!=null){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DialogFragment dialogCheckRoot = new CheckRoot();
                            dialogCheckRoot.show(getFragmentManager(), "dialogCheckRoot");
                        }
                    });
                }
            }

            if (suAvailable) {

                try {
                    suVersion = Shell.SU.version(false);
                    suResult = Shell.SU.run(new String[] {
                            "id"
                    });


                    bbResult = Shell.SU.run(new String[] {
                            "busybox | head -1"
                    });
                } catch (Exception e) {
                    Log.e(LOG_TAG,"Top Fragment doInBackground suParam Exception "+e.getMessage() + " " + e.getCause());
                    if (getActivity()!=null){
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                DialogFragment dialogCheckRoot = new CheckRoot();
                                dialogCheckRoot.show(getFragmentManager(), "dialogCheckRoot");
                            }
                        });
                    }
                }



                try {
                    Verifier verifier = new Verifier(getActivity());
                    appSign = verifier.getApkSignatureZipModern();
                    String appSignAlt = verifier.getApkSignature();
                    verifier.encryptStr(TOP_BROADCAST,appSign,appSignAlt);
                    wrongSign = getString(R.string.encoded).trim();
                    if (!verifier.decryptStr(wrongSign,appSign,appSignAlt).equals(TOP_BROADCAST)) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                getActivity(),getText(R.string.verifier_error).toString(),"1112");
                        if (notificationHelper != null) {
                            notificationHelper.show(getFragmentManager(),NotificationHelper.TAG_HELPER);
                        }
                    }

                } catch (Exception e) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(),getText(R.string.verifier_error).toString(),"2235");
                    if (notificationHelper != null) {
                        notificationHelper.show(getFragmentManager(),NotificationHelper.TAG_HELPER);
                    }
                    Log.e(LOG_TAG,"Top Fragment comparator fault "+e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                            Arrays.toString(e.getStackTrace()));
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (dialogInterface!=null)
                dialogInterface.dismiss();

            try {
                setSUinfo(suResult,suVersion);
                setBBinfo(bbResult);
                if(rootOK){
                    if(!new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("DNSCrypt Installed")
                            || !new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("Tor Installed")
                            || !new PrefManager(Objects.requireNonNull(getActivity())).getBoolPref("I2PD Installed")){
                        PreferenceManager.setDefaultValues(getActivity(),R.xml.preferences_common,true);
                        PreferenceManager.setDefaultValues(getActivity(),R.xml.preferences_dnscrypt,true);
                        PreferenceManager.setDefaultValues(getActivity(),R.xml.preferences_dnscrypt_servers,true);
                        PreferenceManager.setDefaultValues(getActivity(),R.xml.preferences_fast,true);
                        PreferenceManager.setDefaultValues(getActivity(),R.xml.preferences_tor,true);
                        PreferenceManager.setDefaultValues(getActivity(),R.xml.preferences_i2pd,true);

                        DialogFragment installAll = new InstallApp();
                        installAll.show(getFragmentManager(),"dialogInstall");

                        //For core update purposes
                        new PrefManager(getActivity()).setStrPref("DNSCryptVersion",DNSCryptVersion);
                        new PrefManager(getActivity()).setStrPref("TorVersion",TorVersion);
                        new PrefManager(getActivity()).setStrPref("ITPDVersion",ITPDVersion);
                        new PrefManager(getActivity()).setStrPref("DNSCrypt Servers","");
                        SharedPreferences sPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
                        SharedPreferences.Editor editor = sPref.edit();
                        editor.putBoolean("pref_common_tor_tethering",false);
                        editor.putBoolean("pref_common_itpd_tethering",false);
                        editor.apply();

                    } else {

                        String currentDNSCryptVersionStr = new PrefManager(getActivity()).getStrPref("DNSCryptVersion");
                        String currentTorVersionStr = new PrefManager(getActivity()).getStrPref("TorVersion");
                        String currentITPDVersionStr = new PrefManager(getActivity()).getStrPref("ITPDVersion");
                        if (!(currentDNSCryptVersionStr.isEmpty() && currentTorVersionStr.isEmpty() && currentITPDVersionStr.isEmpty())) {
                            int currentDNSCryptVersion = Integer.parseInt(currentDNSCryptVersionStr.replaceAll("\\D+", ""));
                            int currentTorVersion = Integer.parseInt(currentTorVersionStr.replaceAll("\\D+", ""));
                            int currentITPDVersion = Integer.parseInt(currentITPDVersionStr.replaceAll("\\D+", ""));

                            if ((currentDNSCryptVersion < Integer.parseInt(DNSCryptVersion.replaceAll("\\D+", ""))
                                    || currentTorVersion < Integer.parseInt(TorVersion.replaceAll("\\D+", ""))
                                    || currentITPDVersion < Integer.parseInt(ITPDVersion.replaceAll("\\D+", ""))
                                    && !new PrefManager(getActivity()).getBoolPref("UpdateNotAllowed"))) {
                                UpdateCore updateCore = new UpdateCore();
                                updateCore.show(getFragmentManager(), "UpdateCore");
                                return;
                            }
                        }

                        Intent intent = new Intent(TOP_BROADCAST);
                        getActivity().sendBroadcast(intent);
                        Log.i(LOG_TAG, "TopFragment Send TOP_BROADCAST");
                        if (timer!=null) timer.cancel();


                        ////////////////////////////CHECK UPDATES///////////////////////////////////////////
                       checkUpdates();

                       /////////////////////////////REGISTRATION////////////////////////////////////////////
                        if (appVersion.endsWith("e")) {
                            Handler handler = new Handler();
                            Runnable performRegistration = new Runnable() {
                                @Override
                                public void run() {
                                    Registration registration = new Registration(getActivity());
                                    registration.showDonateDialog();
                                }
                            };
                            handler.postDelayed(performRegistration, 5000);
                        }


                    }

                }

            } catch (Exception e) {
                DialogFragment dialogCheckRoot = new CheckRoot();
                dialogCheckRoot.show(getFragmentManager(), "dialogCheckRoot");
                Log.w(LOG_TAG, "Chkroot onPostExecute "+e.getMessage() + " " + e.getCause());
            }
        }
    }

    public void setSUinfo(List<String> fSuResult, String fSuVersion){
        //check image logic

        if (fSuResult!=null && fSuResult.size()!=0
                && fSuResult.toString().toLowerCase().contains("uid=0")
                && fSuResult.toString().toLowerCase().contains("gid=0")) {
            rootOK = true;
            new PrefManager(getActivity()).setBoolPref("rootOK",true);

            if (fSuVersion!=null && fSuVersion.length()!=0) {
                verSU = "Root is available." + (char) 10 +
                        "Super User Version: " + fSuVersion + (char) 10 +
                        fSuResult.get(0);
            } else {
                verSU = "Root is available." + (char) 10 +
                        "Super User Version: Unknown" +
                        fSuResult.get(0);
            }
            ((ImageView)view.findViewById(R.id.imDeviceRoot)).setImageResource(R.drawable.ic_top_good);
            Log.i(LOG_TAG,verSU);
        } else {
            rootOK = false;
            new PrefManager(getActivity()).setBoolPref("rootOK",false);
            TextView tvDeviceRoot=view.findViewById(R.id.tvDeviceRoot);
            tvDeviceRoot.setText(R.string.no_root);

            DialogFragment dialogCheckRoot = new CheckRoot();
            dialogCheckRoot.show(getFragmentManager(), "dialogCheckRoot");

        }
    }

    public void setBBinfo(List<String>fBbResult){
        //check image logic
        if (fBbResult != null && fBbResult.size()!=0){
            try {
                verBB = fBbResult.get(0);
            } catch (Exception e) {
                Log.e(LOG_TAG,"Error to check BusyBox" + e.toString() + " " + e.getCause());
                return;
            }
        } else {
            bbOK = false;
            new PrefManager(getActivity()).setBoolPref("bbOK",false);
            TextView tvBBox=view.findViewById(R.id.tvBBox);
            tvBBox.setText(R.string.no_bb);
            return;
        }



        if (!verBB.toLowerCase().contains("not found")) {
            bbOK = true;
            new PrefManager(getActivity()).setBoolPref("bbOK",true);
            ((ImageView)view.findViewById(R.id.imBBox)).setImageResource(R.drawable.ic_top_good);

            Log.i(LOG_TAG,"BusyBox is available " + verBB);

        } else {
            bbOK = false;
            new PrefManager(getActivity()).setBoolPref("bbOK",false);
            TextView tvBBox=view.findViewById(R.id.tvBBox);
            tvBBox.setText(R.string.no_bb);

        }
    }






    //Alert if root or busybox not available
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.tvDeviceRoot:
                if(!rootOK) {
                    DialogFragment dialogCheckRoot = new CheckRoot();
                    dialogCheckRoot.show(getFragmentManager(), "dialogCheckRoot");
                } else {
                    NotificationDialogFragment dialogShowSU = NotificationDialogFragment.newInstance(verSU);
                    dialogShowSU.show(getFragmentManager(), NotificationDialogFragment.TAG_NOT_FRAG);
                }

                break;
            case R.id.tvBBox:
                if(!bbOK) {
                    DialogFragment dialogCheckBB = new CheckBB();
                    dialogCheckBB.show(getFragmentManager(), "dialogCheckBB");
                } else {
                    NotificationDialogFragment dialogShowBB = NotificationDialogFragment.newInstance(verBB);
                    dialogShowBB.show(getFragmentManager(), NotificationDialogFragment.TAG_NOT_FRAG);
                }

                break;
        }
    }

    //Dialog Install
    public static class InstallApp extends DialogFragment {

        @Override
        public void show(FragmentManager manager, String tag) {
            try {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, tag);
                ft.commitAllowingStateLoss();
                //ft.commit();
            } catch (IllegalStateException e) {
                Log.w("TPDCLogs", "Top Frag InstallApp Exception "+e.getMessage() + " " + e.getCause());
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.install_title)
                    .setTitle(R.string.install)
                    .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentManager fm = getFragmentManager();
                            TopFragment frgTop = (TopFragment) fm.findFragmentById(R.id.Topfrg);
                            frgTop.startInstallation();
                        }
                    })
                    .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            getActivity().finish();
                        }
                    });
            return builder.create();
        }
    }

    //Dialog Root not available
    public static class CheckRoot extends DialogFragment {

        @Override
        public void show(FragmentManager manager, String tag) {
            try {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, tag);
                ft.commitAllowingStateLoss();
            } catch (IllegalStateException e) {
                Log.w(LOG_TAG, "Top Fragment CheckRoot Exception"+e.getMessage() + " " + e.getCause());
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.check_root)
                    .setTitle(R.string.sorry)
                    .setIcon(R.drawable.ic_no_root)
                    .setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            getActivity().finish();
                        }
                    });
            return builder.create();
        }
    }

    //Dialog BusyBox not available
    public static class CheckBB extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.install_bb)
                    .setTitle(R.string.warning)
                    .setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent callBB = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=stericson.busybox"));
                            startActivity(callBB);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dismiss();
                        }
                    });
            return builder.create();
        }
    }

    public static class NotificationDialogFragment extends DialogFragment {

        public static final String TAG_NOT_FRAG = "NotificationDialogFragment";

        private static String mMessageToDisplay;

        public static NotificationDialogFragment newInstance(String message) {

            NotificationDialogFragment infoDialog = new NotificationDialogFragment();
            mMessageToDisplay = message;
            return infoDialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
            alertDialog.setMessage(mMessageToDisplay)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dismiss();
                        }
                    });
            return alertDialog.create();
        }
    }

    private void requestStoragePermissions(MainActivity activity) {
        // Storage Permissions variables
        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int writePermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int readPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (writePermission != PackageManager.PERMISSION_GRANTED || readPermission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.INTERNET},
                    2);
        }
    }

    public void startInstallation() {

        requestStoragePermissions((MainActivity) getActivity());

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        timer = new Timer();

        timer.schedule(new TimerTask() {
            int loop = 0;

            @Override
            public void run() {

                Log.i(LOG_TAG, "TopFragment Timer loop = "+loop);

                if (++loop > 15) {
                    timer.cancel();
                    Log.w(LOG_TAG, "TopFragment Timer cancel, loop > 10");
                }

                getActivity().runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        int writePermission = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        int readPermission = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
                        if (rootOK && writePermission == PackageManager.PERMISSION_GRANTED && readPermission == PackageManager.PERMISSION_GRANTED) {

                            FragmentManager fm = getFragmentManager();
                            DNSCryptRunFragment frgDNScrypt = (DNSCryptRunFragment) fm.findFragmentById(R.id.DNSCryptfrg);
                            if (frgDNScrypt!=null) {
                                frgDNScrypt.installDNSCrypt();
                                Log.i(LOG_TAG, "TopFragment Timer startInstallation() frgDNScrypt.installDNSCrypt()");
                                if (timer!=null)timer.cancel();
                            }


                        }
                    }
                });
            }
        }, 3000, 1000);
    }

    private DialogInterface modernProgressDialog() {
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.root);
        builder.setMessage(R.string.root_available);
        builder.setIcon(R.drawable.ic_visibility_off_black_24dp);

        ProgressBar progressBar = new ProgressBar(getActivity(),null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        builder.setView(progressBar);
        builder.setCancelable(false);
        return builder.show();
    }

    public static class UpdateCore extends DialogFragment {

        @Override
        public void show(FragmentManager manager, String tag) {
            try {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, tag);
                ft.commitAllowingStateLoss();
            } catch (IllegalStateException e) {
                Log.w("TPDCLogs", "UpdateCore Dialog Exception "+e.getMessage() + " " + e.getCause());
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(R.string.update_core_message)
                    .setTitle(R.string.update_core_title)
                    .setPositiveButton(R.string.update_core_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();

                            PathVars pathVars = new PathVars(getActivity());
                            String iptablesPath = pathVars.iptablesPath;
                            String busyboxPath = pathVars.busyboxPath;
                            String[] commandsReset = new String[] {
                                    iptablesPath+ "iptables -t nat -F tordnscrypt_nat_output",
                                    iptablesPath+ "iptables -t nat -D OUTPUT -j tordnscrypt_nat_output || true",
                                    iptablesPath+ "iptables -F tordnscrypt",
                                    iptablesPath+ "iptables -D OUTPUT -j tordnscrypt || true",
                                    busyboxPath + "sleep 1",
                                    busyboxPath + "killall dnscrypt-proxy",
                                    busyboxPath + "killall tor",
                                    busyboxPath + "killall i2pd"
                            };
                            RootCommands rootCommands = new RootCommands(commandsReset);
                            Intent intentReset = new Intent(getActivity(), RootExecService.class);
                            intentReset.setAction(RootExecService.RUN_COMMAND);
                            intentReset.putExtra("Commands",rootCommands);
                            intentReset.putExtra("Mark", RootExecService.NullMark);
                            RootExecService.performAction(getActivity(),intentReset);

                            new PrefManager(getActivity()).setBoolPref("DNSCrypt Installed",false);
                            new PrefManager(getActivity()).setBoolPref("Tor Installed",false);
                            new PrefManager(getActivity()).setBoolPref("I2PD Installed",false);
                            NotificationDialogFragment dialogShowSU = NotificationDialogFragment.newInstance(getString(R.string.update_core_restart));
                            dialogShowSU.show(getFragmentManager(), NotificationDialogFragment.TAG_NOT_FRAG);
                        }
                    })
                    .setNegativeButton(R.string.update_core_no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dismiss();
                        }
                    })
                    .setNeutralButton(R.string.update_core_not_show_again, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dismiss();
                            new PrefManager(getActivity()).setBoolPref("UpdateNotAllowed",true);
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }

    public static class NewUpdateDialogFragment extends DialogFragment {

        public static final String TAG_NOT_FRAG = "NewUpdateDialogFragment";

        private String mMessageToDisplay;
        private String updateStr;
        private String updateFile;
        private String hash;

        public static NewUpdateDialogFragment newInstance(String message, String updateStr, String updateFile, String hash) {

            NewUpdateDialogFragment updateDialog = new NewUpdateDialogFragment();
            updateDialog.mMessageToDisplay = message;
            updateDialog.updateStr = updateStr;
            updateDialog.updateFile = updateFile;
            updateDialog.hash = hash;

            return updateDialog;
        }

        @Override
        public void show(FragmentManager manager, String tag) {
            try {
                FragmentTransaction ft = manager.beginTransaction();
                ft.add(this, tag);
                ft.commitAllowingStateLoss();
                //ft.commit();
            } catch (IllegalStateException e) {
                Log.w(LOG_TAG, "NewUpdateDialogFragment dialog Exception "+e.getMessage() + " " + e.getCause());
            }
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
            alertDialog.setMessage(mMessageToDisplay)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(getActivity(), UpdateService.class);
                            intent.setAction(UpdateService.DOWNLOAD_ACTION);
                            intent.putExtra("url", "https://invizible.net/?wpdmdl="+updateStr);
                            intent.putExtra("file", updateFile);
                            intent.putExtra("hash",hash);
                            getActivity().startService(intent);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dismiss();
                        }
                    });
            return alertDialog.create();
        }
    }

    public void checkUpdates() {
        SharedPreferences spref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean autoUpdate = spref.getBoolean("pref_fast_auto_update",true)
                && !appVersion.startsWith("l");
        if (autoUpdate) {
            boolean throughTorUpdate = spref.getBoolean("pref_fast through_tor_update",false);
            boolean torRunning = new PrefManager(getActivity()).getBoolPref("Tor Running");
            boolean torReady = new PrefManager(getActivity()).getBoolPref("Tor Ready");
            if (!throughTorUpdate || (torRunning && torReady)) {
                long updateTimeCurrent = System.currentTimeMillis();
                String updateTimeLastStr = new PrefManager(getActivity()).getStrPref("updateTimeLast");
                if (!updateTimeLastStr.isEmpty()) {
                    long updateTimeLast = Long.parseLong(updateTimeLastStr);
                    if (updateTimeCurrent - updateTimeLast > 1000 * 60 * 60 * UPDATES_CHECK_INTERVAL_HOURS)
                        checkNewVer();
                } else {
                    checkNewVer();
                }
            }
        }
    }

    public void checkNewVer(){
        Handler handler = new Handler();

        new PrefManager(getActivity()).setStrPref("LastUpdateResult","");

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity()==null)
                    return;
                new PrefManager(getActivity()).setStrPref("updateTimeLast",String.valueOf(System.currentTimeMillis()));

                updateCheck = new UpdateCheck(getActivity());
                try {
                    updateCheck.requestUpdateData("https://invizible.net",appSign);
                } catch (Exception e) {
                    if (getActivity() != null) {
                        new PrefManager(getActivity()).setStrPref("LastUpdateResult",getText(R.string.update_check_fault).toString());
                        if (MainActivity.modernDialog!=null)
                            ((MainActivity) getActivity()).showUpdateMessage(getText(R.string.update_check_fault).toString());
                    }
                    Log.e(LOG_TAG,"TopFragment Failed to requestUpdate() " + e.getMessage() + " " + e.getCause());
                }
            }
        };


        handler.postDelayed(runnable,1000);

    }

    public void downloadUpdate(String fileName,String updateStr, String message, String hash){
        if (getActivity()==null)
            return;

        if (MainActivity.modernDialog!=null) {
            MainActivity.modernDialog.dismiss();
            MainActivity.modernDialog = null;
        }

        new PrefManager(getActivity()).setStrPref("LastUpdateResult",getActivity().getText(R.string.update_found).toString());

        NewUpdateDialogFragment newUpdateDialogFragment = NewUpdateDialogFragment.newInstance(message,updateStr,fileName,hash);
        newUpdateDialogFragment.show(getFragmentManager(), NewUpdateDialogFragment.TAG_NOT_FRAG);
    }


}
