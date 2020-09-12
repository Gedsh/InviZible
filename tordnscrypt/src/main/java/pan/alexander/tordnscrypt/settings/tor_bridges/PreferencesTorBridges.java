package pan.alexander.tordnscrypt.settings.tor_bridges;
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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.dialogs.UpdateDefaultBridgesDialog;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.enums.BridgeType;
import pan.alexander.tordnscrypt.utils.enums.BridgesSelector;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.utils.file_operations.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.meek_lite;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.vanilla;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.obfs3;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.obfs4;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.scramblesuit;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.snowflake;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.undefined;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;


public class PreferencesTorBridges extends Fragment implements View.OnClickListener, OnTextFileOperationsCompleteListener, PreferencesBridges {
    public final static String snowFlakeBridgesDefault = "3";
    public final static String snowFlakeBridgesOwn = "4";
    private RadioButton rbNoBridges;
    private RadioButton rbDefaultBridges;
    private Spinner spDefaultBridges;
    private RadioButton rbOwnBridges;
    private Spinner spOwnBridges;
    private TextView tvBridgesListEmpty;
    private RecyclerView rvBridges;
    private String appDataDir;
    private String obfsPath;
    private String snowflakePath;
    private List<String> tor_conf;
    private List<String> tor_conf_orig;
    private List<ObfsBridge> bridgeList;
    private List<String> currentBridges;
    private List<String> anotherBridges;
    private BridgeType currentBridgesType = undefined;
    private BridgeAdapter bridgeAdapter;
    private String bridges_file_path;
    private String bridges_custom_file_path;
    private final String torConfTag = "pan.alexander.tordnscrypt/app_data/tor/tor.conf";
    private final String defaultBridgesOperationTag = "pan.alexander.tordnscrypt/abstract_default_bridges_operation";
    private final String ownBridgesOperationTag = "pan.alexander.tordnscrypt/abstract_own_bridges_operation";
    private final String addBridgesTag = "pan.alexander.tordnscrypt/abstract_add_bridges";
    private final String addRequestedBridgesTag = "pan.alexander.tordnscrypt/abstract_add_requested_bridges";
    private String requestedBridgesToAdd;
    private BridgesSelector savedBridgesSelector;
    private Future<?> verifyDefaultBridgesTask;
    private Handler handler;


    public PreferencesTorBridges() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getActivity();

        if (context== null) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(context);

        appDataDir = pathVars.getAppDataDir();
        obfsPath = pathVars.getObfsPath();
        snowflakePath = pathVars.getSnowflakePath();


        bridges_custom_file_path = appDataDir + "/app_data/tor/bridges_custom.lst";
        bridges_file_path = appDataDir + "/app_data/tor/bridges_default.lst";

        tor_conf = new ArrayList<>();
        tor_conf_orig = new ArrayList<>();

        bridgeList = new ArrayList<>();
        currentBridges = new ArrayList<>();
        anotherBridges = new ArrayList<>();

        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            handler = new Handler(looper);
        }

        FileOperations.setOnFileOperationCompleteListener(this);

        FileOperations.readTextFile(context, appDataDir + "/app_data/tor/tor.conf", torConfTag);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_preferences_tor_bridges, container, false);

        rbNoBridges = view.findViewById(R.id.rbNoBridges);
        rbNoBridges.setOnCheckedChangeListener(onCheckedChangeListener);

        rbDefaultBridges = view.findViewById(R.id.rbDefaultBridges);
        rbDefaultBridges.setOnCheckedChangeListener(onCheckedChangeListener);

        spDefaultBridges = view.findViewById(R.id.spDefaultBridges);
        spDefaultBridges.setOnItemSelectedListener(onItemSelectedListener);
        spDefaultBridges.setPrompt(getString(R.string.pref_fast_use_tor_bridges_obfs));

        rbOwnBridges = view.findViewById(R.id.rbOwnBridges);
        rbOwnBridges.setOnCheckedChangeListener(onCheckedChangeListener);

        spOwnBridges = view.findViewById(R.id.spOwnBridges);
        spOwnBridges.setOnItemSelectedListener(onItemSelectedListener);
        spOwnBridges.setPrompt(getString(R.string.pref_fast_use_tor_bridges_obfs));

        Button btnRequestBridges = view.findViewById(R.id.btnRequestBridges);
        btnRequestBridges.setOnClickListener(this);
        Button btnAddBridges = view.findViewById(R.id.btnAddBridges);
        btnAddBridges.setOnClickListener(this);

        tvBridgesListEmpty = view.findViewById(R.id.tvBridgesListEmpty);

        rvBridges = view.findViewById(R.id.rvBridges);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        rvBridges.setLayoutManager(mLayoutManager);

        if (getActivity() != null) {
            getActivity().setTitle(R.string.pref_fast_use_tor_bridges);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Context context = getActivity();

        if (context == null || !isAdded()) {
            return;
        }

        if (!new PrefManager(context).getStrPref("defaultBridgesObfs").isEmpty())
            spDefaultBridges.setSelection(Integer.parseInt(new PrefManager(context).getStrPref("defaultBridgesObfs")));

        if (!new PrefManager(context).getStrPref("ownBridgesObfs").isEmpty())
            spOwnBridges.setSelection(Integer.parseInt(new PrefManager(context).getStrPref("ownBridgesObfs")));


        bridgeAdapter = new BridgeAdapter((SettingsActivity) getActivity(), getParentFragmentManager(), this);
        rvBridges.setAdapter(bridgeAdapter);

        boolean useNoBridges = new PrefManager(context).getBoolPref("useNoBridges");
        boolean useDefaultBridges = new PrefManager(context).getBoolPref("useDefaultBridges");
        boolean useOwnBridges = new PrefManager(context).getBoolPref("useOwnBridges");

        if (!useNoBridges && !useDefaultBridges && !useOwnBridges) {
            rbNoBridges.setChecked(true);
            tvBridgesListEmpty.setVisibility(View.GONE);
            savedBridgesSelector = BridgesSelector.NO_BRIDGES;
        } else if (useNoBridges) {
            noBridgesOperation();
            rbNoBridges.setChecked(true);
            savedBridgesSelector = BridgesSelector.NO_BRIDGES;
        } else if (useDefaultBridges) {
            FileOperations.readTextFile(context, bridges_file_path, defaultBridgesOperationTag);
            rbDefaultBridges.setChecked(true);
            savedBridgesSelector = BridgesSelector.DEFAULT_BRIDGES;
        } else {
            bridges_file_path = appDataDir + "/app_data/tor/bridges_custom.lst";
            FileOperations.readTextFile(context, bridges_file_path, ownBridgesOperationTag);
            rbOwnBridges.setChecked(true);
            savedBridgesSelector = BridgesSelector.OWN_BRIDGES;
        }

        if (!new PrefManager(context).getBoolPref("doNotShowNewDefaultBridgesDialog")) {
            verifyDefaultBridgesTask = verifyNewDefaultBridgesExist(context, useDefaultBridges);
        }

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                Verifier verifier = new Verifier(context);
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {

                    if (isAdded()) {
                        NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                                context, getString(R.string.verifier_error), "3458");
                        if (notificationHelper != null) {
                            notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                        }
                    }
                }

            } catch (Exception e) {
                if (isAdded()) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, getString(R.string.verifier_error), "64539");
                    if (notificationHelper != null) {
                        notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }
                Log.e(LOG_TAG, "PreferencesTorBridges fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });

    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();

        if (context == null) {
            return;
        }

        if (!currentBridges.isEmpty()) {
            switch (savedBridgesSelector) {
                case NO_BRIDGES:
                    new PrefManager(context).setBoolPref("useNoBridges", true);
                    new PrefManager(context).setBoolPref("useDefaultBridges", false);
                    new PrefManager(context).setBoolPref("useOwnBridges", false);
                    break;
                case DEFAULT_BRIDGES:
                    new PrefManager(context).setBoolPref("useNoBridges", false);
                    new PrefManager(context).setBoolPref("useDefaultBridges", true);
                    new PrefManager(context).setBoolPref("useOwnBridges", false);
                    break;
                case OWN_BRIDGES:
                    new PrefManager(context).setBoolPref("useNoBridges", false);
                    new PrefManager(context).setBoolPref("useDefaultBridges", false);
                    new PrefManager(context).setBoolPref("useOwnBridges", true);
                    break;
                default:
                    new PrefManager(context).setBoolPref("useNoBridges", false);
                    new PrefManager(context).setBoolPref("useDefaultBridges", false);
                    new PrefManager(context).setBoolPref("useOwnBridges", false);
                    break;
            }
        }

        List<String> tor_conf_clean = new ArrayList<>();

        for (int i = 0; i < tor_conf.size(); i++) {
            String line = tor_conf.get(i);
            if ((line.contains("#") || (!line.contains("Bridge ") && !line.contains("ClientTransportPlugin "))) && !line.isEmpty()) {
                tor_conf_clean.add(line);
            }
        }

        tor_conf = tor_conf_clean;

        String currentBridgesTypeToSave;
        if (currentBridgesType.equals(vanilla)) {
            currentBridgesTypeToSave = "";
        } else {
            currentBridgesTypeToSave = currentBridgesType.toString();
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean saveExtendedLogs = sharedPreferences.getBoolean("pref_common_show_help", false);
        String saveLogsString = "";
        if (saveExtendedLogs) {
            saveLogsString = " -log " + appDataDir + "/logs/Snowflake.log";
        }

        String stunServer;
        stunServer = sharedPreferences.getString("pref_tor_snowflake_stun",
                "stun.l.google.com:19302," +
                        "stun.voip.blackberry.com:3478," +
                        "stun.altar.com.pl:3478," +
                        "stun.antisip.com:3478," +
                        "stun.bluesip.net:3478," +
                        "stun.dus.net:3478," +
                        "stun.epygi.com:3478," +
                        "stun.sonetel.com:3478," +
                        "stun.sonetel.net:3478," +
                        "stun.stunprotocol.org:3478," +
                        "stun.uls.co.za:3478," +
                        "stun.voipgate.com:3478," +
                        "stun.voys.nl:3478");

        if (stunServer != null && stunServer.equals("stun.l.google.com:19302")) {
            stunServer = null;
        }

        if (stunServer == null) {
            stunServer = "stun.l.google.com:19302," +
                    "stun.voip.blackberry.com:3478," +
                    "stun.altar.com.pl:3478," +
                    "stun.antisip.com:3478," +
                    "stun.bluesip.net:3478," +
                    "stun.dus.net:3478," +
                    "stun.epygi.com:3478," +
                    "stun.sonetel.com:3478," +
                    "stun.sonetel.net:3478," +
                    "stun.stunprotocol.org:3478," +
                    "stun.uls.co.za:3478," +
                    "stun.voipgate.com:3478," +
                    "stun.voys.nl:3478";
            sharedPreferences.edit().putString("pref_tor_snowflake_stun", stunServer).apply();
        }

        if (!currentBridges.isEmpty() && !currentBridgesType.equals(undefined)) {

            if (!currentBridgesType.equals(vanilla)) {

                String clientTransportPlugin;
                if (currentBridgesType.equals(snowflake)) {
                    StringBuilder stunServers = new StringBuilder();
                    String[] stunServersArr = stunServer.split(", ?");

                    for (String server : stunServersArr) {
                        stunServers.append("stun:").append(server.trim()).append(",");
                    }

                    stunServers.deleteCharAt(stunServers.lastIndexOf(","));

                    clientTransportPlugin = "ClientTransportPlugin " + currentBridgesTypeToSave + " exec "
                            + snowflakePath + " -url https://snowflake-broker.azureedge.net/" +
                            " -front ajax.aspnetcdn.com -ice " + stunServers.toString() + " -max 3" + saveLogsString;
                } else {
                    clientTransportPlugin = "ClientTransportPlugin " + currentBridgesTypeToSave + " exec "
                            + obfsPath;
                }


                tor_conf.add(clientTransportPlugin);
            }

            for (int i = 0; i < currentBridges.size(); i++) {
                String currentBridge = currentBridges.get(i);

                if (currentBridgesType == vanilla) {
                    if (!currentBridge.isEmpty() && !currentBridge.contains(obfs4.toString())
                            && !currentBridge.contains(obfs3.toString()) && !currentBridge.contains(scramblesuit.toString())
                            && !currentBridge.contains(meek_lite.toString()) && !currentBridge.contains(snowflake.toString())) {
                        tor_conf.add("Bridge " + currentBridge);
                    }
                } else {
                    if (!currentBridge.isEmpty() && currentBridge.contains(currentBridgesType.toString())) {
                        tor_conf.add("Bridge " + currentBridge);
                    }
                }

            }
        } else {
            for (int i = 0; i < tor_conf.size(); i++) {
                if (tor_conf.get(i).contains("UseBridges")) {
                    String line = tor_conf.get(i);
                    String result = line.replace("1", "0");
                    if (!result.equals(line)) {
                        tor_conf.set(i, result);
                    }
                }
            }
        }

        if (Arrays.equals(tor_conf.toArray(), tor_conf_orig.toArray()))
            return;

        FileOperations.writeToTextFile(context, appDataDir + "/app_data/tor/tor.conf", tor_conf, "ignored");

        ///////////////////////Tor restart/////////////////////////////////////////////
        boolean torRunning = new PrefManager(context).getBoolPref("Tor Running");

        if (torRunning) {
            ModulesRestarter.restartTor(context);
            Toast.makeText(context, getText(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }

        if (verifyDefaultBridgesTask != null && !verifyDefaultBridgesTask.isCancelled()) {
            verifyDefaultBridgesTask.cancel(false);
            verifyDefaultBridgesTask = null;
        }

        FileOperations.deleteOnFileOperationCompleteListener();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnAddBridges:
                FileOperations.readTextFile(getActivity(), appDataDir + "/app_data/tor/bridges_custom.lst", addBridgesTag);
                break;
            case R.id.btnRequestBridges:
                GetNewBridges getNewBridges = new GetNewBridges(new WeakReference<>((SettingsActivity) getActivity()));
                getNewBridges.selectTransport();
                break;
        }
    }

    private void addBridges(final List<String> persistList) {

        if (getActivity() == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);
        LayoutInflater inflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
        final EditText input = inputView.findViewById(R.id.etForDialog);
        input.setSingleLine(false);
        builder.setView(inputView);

        builder.setPositiveButton(getText(R.string.ok), (dialogInterface, i) -> {
            List<String> bridgesListNew = new ArrayList<>();

            String inputLinesStr = input.getText().toString().trim();

            String inputBridgesType = "";
            Pattern pattern = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+");
            if (inputLinesStr.contains("obfs4")) {
                inputBridgesType = "obfs4";
                pattern = Pattern.compile("^obfs4 +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+ +cert=.+ +iat-mode=\\d");
            } else if (inputLinesStr.contains("obfs3")) {
                inputBridgesType = "obfs3";
                pattern = Pattern.compile("^obfs3 +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+");
            } else if (inputLinesStr.contains("scramblesuit")) {
                inputBridgesType = "scramblesuit";
                pattern = Pattern.compile("^scramblesuit +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+( +password=\\w+)?");
            } else if (inputLinesStr.contains("meek_lite")) {
                inputBridgesType = "meek_lite";
                pattern = Pattern.compile("^meek_lite +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+ +url=https://[\\w./]+ +front=[\\w./]+");
            } else if (inputLinesStr.contains("snowflake")) {
                inputBridgesType = "snowflake";
                pattern = Pattern.compile("^snowflake +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+");
            }

            String[] bridgesArrNew;
            if (inputBridgesType.isEmpty()) {
                bridgesArrNew = inputLinesStr.replaceAll("[^\\w\\n:+=/. -]", " ").replaceAll(" +", " ").split("\n");
            } else {
                bridgesArrNew = inputLinesStr.replaceAll("[^\\w:+=/. -]", " ").replaceAll(" +", " ").split(inputBridgesType);
            }

            if (bridgesArrNew.length != 0) {
                for (String brgNew : bridgesArrNew) {
                    if (!brgNew.isEmpty() && inputBridgesType.isEmpty()) {
                        Matcher matcher = pattern.matcher(brgNew.trim());
                        if (matcher.matches()) {
                            bridgesListNew.add(brgNew.trim());
                        }
                    } else if (!brgNew.isEmpty()) {
                        Matcher matcher = pattern.matcher(inputBridgesType + " " + brgNew.trim());
                        if (matcher.matches()) {
                            bridgesListNew.add(inputBridgesType + " " + brgNew.trim());
                        }
                    }
                }

                if (persistList != null) {
                    List<String> retainList = new ArrayList<>(persistList);
                    retainList.retainAll(bridgesListNew);
                    bridgesListNew.removeAll(retainList);
                    persistList.addAll(bridgesListNew);
                    bridgesListNew = persistList;
                }


                Collections.sort(bridgesListNew);
                FileOperations.writeToTextFile(getActivity(), bridges_custom_file_path, bridgesListNew, "ignored");

                if (getActivity() == null) {
                    return;
                }

                boolean useOwnBridges = new PrefManager(getActivity()).getBoolPref("useOwnBridges");
                if (useOwnBridges) {
                    ownBridgesOperation(bridgesListNew);
                } else {
                    rbOwnBridges.performClick();
                }
            }
        });

        builder.setNegativeButton(getText(R.string.cancel), (dialog, i) -> dialog.cancel());
        builder.setTitle(R.string.pref_fast_use_tor_bridges_add);
        builder.show();
    }

    private void addRequestedBridges(String bridges, List<String> persistList) {
        List<String> bridgesListNew = new ArrayList<>();
        String[] bridgesArrNew = bridges.split(System.lineSeparator());

        if (bridgesArrNew.length != 0) {
            for (String brgNew : bridgesArrNew) {
                if (!brgNew.isEmpty()) {
                    bridgesListNew.add(brgNew.trim());
                }
            }

            if (persistList != null) {
                List<String> retainList = new ArrayList<>(persistList);
                retainList.retainAll(bridgesListNew);
                bridgesListNew.removeAll(retainList);
                persistList.addAll(bridgesListNew);
                bridgesListNew = persistList;
            }


            Collections.sort(bridgesListNew);
            FileOperations.writeToTextFile(getActivity(), bridges_custom_file_path, bridgesListNew, "ignored");

            if (!bridges.isEmpty()) {
                if (bridges.contains("obfs4")) {
                    //currentBridgesType = obfs4;
                    if (!spOwnBridges.getSelectedItem().toString().equals("obfs4")) {
                        spOwnBridges.setSelection(0);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridges.contains("obfs3")) {
                    //currentBridgesType = obfs3;
                    if (!spOwnBridges.getSelectedItem().toString().equals("obfs3")) {
                        spOwnBridges.setSelection(1);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridges.contains("scramblesuit")) {
                    //currentBridgesType = scramblesuit;
                    if (!spOwnBridges.getSelectedItem().toString().equals("scramblesuit")) {
                        spOwnBridges.setSelection(2);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridges.contains("meek_lite")) {
                    //currentBridgesType = meek_lite;
                    if (!spOwnBridges.getSelectedItem().toString().equals("meek_lite")) {
                        spOwnBridges.setSelection(3);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridges.contains("snowflake")) {
                    //currentBridgesType = snowflake;
                    if (!spOwnBridges.getSelectedItem().toString().equals("snowflake")) {
                        spOwnBridges.setSelection(4);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else {
                    //currentBridgesType = vanilla;
                    if (!spOwnBridges.getSelectedItem().toString().equals("vanilla")) {
                        spOwnBridges.setSelection(5);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                }

            }

            if (getActivity() == null) {
                return;
            }

            boolean useOwnBridges = new PrefManager(getActivity()).getBoolPref("useOwnBridges");

            if (!useOwnBridges) {
                currentBridges.clear();
                rbOwnBridges.performClick();
            }
        }
    }

    public void readCurrentCustomBridges(String bridges) {
        requestedBridgesToAdd = bridges;

        FileOperations.readTextFile(getActivity(), bridges_custom_file_path, addRequestedBridgesTag);
    }


    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean newValue) {

            if (getActivity() == null) {
                return;
            }

            switch (compoundButton.getId()) {
                case R.id.rbNoBridges:
                    if (newValue) {
                        new PrefManager(getActivity()).setBoolPref("useNoBridges", true);
                        new PrefManager(getActivity()).setBoolPref("useDefaultBridges", false);
                        new PrefManager(getActivity()).setBoolPref("useOwnBridges", false);

                        noBridgesOperation();

                        for (int i = 0; i < tor_conf.size(); i++) {
                            if (tor_conf.get(i).contains("UseBridges")) {
                                String line = tor_conf.get(i);
                                String result = line.replace("1", "0");
                                if (!result.equals(line)) {
                                    tor_conf.set(i, result);
                                }
                            }
                        }
                    }

                    break;
                case R.id.rbDefaultBridges:
                    if (newValue) {
                        new PrefManager(getActivity()).setBoolPref("useNoBridges", false);
                        new PrefManager(getActivity()).setBoolPref("useDefaultBridges", true);
                        new PrefManager(getActivity()).setBoolPref("useOwnBridges", false);

                        bridges_file_path = appDataDir + "/app_data/tor/bridges_default.lst";

                        FileOperations.readTextFile(getActivity(), bridges_file_path, defaultBridgesOperationTag);

                        for (int i = 0; i < tor_conf.size(); i++) {
                            if (tor_conf.get(i).contains("UseBridges")) {
                                String line = tor_conf.get(i);
                                String result = line.replace("0", "1");
                                if (!result.equals(line)) {
                                    tor_conf.set(i, result);
                                }
                            }
                        }
                    }

                    break;
                case R.id.rbOwnBridges:
                    if (newValue) {
                        new PrefManager(getActivity()).setBoolPref("useNoBridges", false);
                        new PrefManager(getActivity()).setBoolPref("useDefaultBridges", false);
                        new PrefManager(getActivity()).setBoolPref("useOwnBridges", true);

                        bridges_file_path = appDataDir + "/app_data/tor/bridges_custom.lst";

                        FileOperations.readTextFile(getActivity(), bridges_file_path, ownBridgesOperationTag);

                        for (int i = 0; i < tor_conf.size(); i++) {
                            if (tor_conf.get(i).contains("UseBridges")) {
                                String line = tor_conf.get(i);
                                String result = line.replace("0", "1");
                                if (!result.equals(line)) {
                                    tor_conf.set(i, result);
                                }
                            }
                        }
                    }

                    break;
            }

        }
    };

    private void noBridgesOperation() {
        rbDefaultBridges.setChecked(false);
        rbOwnBridges.setChecked(false);

        bridgeList.clear();
        currentBridges.clear();
        bridgeAdapter.notifyDataSetChanged();

        tvBridgesListEmpty.setVisibility(View.GONE);
    }

    private void defaultBridgesOperation(List<String> bridges_default) {
        rbNoBridges.setChecked(false);
        rbOwnBridges.setChecked(false);

        bridgeList.clear();
        anotherBridges.clear();

        BridgeType obfsTypeSp = BridgeType.valueOf(spDefaultBridges.getSelectedItem().toString());

        if (bridges_default == null)
            return;

        for (String line : bridges_default) {
            ObfsBridge obfsBridge;
            if (line.contains(obfsTypeSp.toString())) {
                obfsBridge = new ObfsBridge(line, obfsTypeSp, false);
                if (currentBridges.contains(line)) {
                    obfsBridge.active = true;
                }
                bridgeList.add(obfsBridge);
            } else {
                anotherBridges.add(line);
            }
        }

        bridgeAdapter.notifyDataSetChanged();

        if (bridgeList.isEmpty()) {
            tvBridgesListEmpty.setVisibility(View.VISIBLE);
        } else {
            tvBridgesListEmpty.setVisibility(View.GONE);
        }
    }

    private void ownBridgesOperation(List<String> bridges_custom) {
        rbNoBridges.setChecked(false);
        rbDefaultBridges.setChecked(false);

        bridgeList.clear();
        anotherBridges.clear();

        BridgeType obfsTypeSp = BridgeType.valueOf(spOwnBridges.getSelectedItem().toString());

        if (bridges_custom == null)
            return;

        for (String line : bridges_custom) {
            ObfsBridge obfsBridge;
            if (!obfsTypeSp.equals(vanilla) && line.contains(obfsTypeSp.toString())) {
                obfsBridge = new ObfsBridge(line, obfsTypeSp, false);
                if (currentBridges.contains(line)) {
                    obfsBridge.active = true;
                }
                bridgeList.add(obfsBridge);
            } else if (obfsTypeSp.equals(vanilla) && !line.contains("obfs4") && !line.contains("obfs3")
                    && !line.contains("scramblesuit") && !line.contains("meek_lite") && !line.contains("snowflake")
                    && !line.isEmpty()) {
                obfsBridge = new ObfsBridge(line, obfsTypeSp, false);
                if (currentBridges.contains(line)) {
                    obfsBridge.active = true;
                }
                bridgeList.add(obfsBridge);
            } else {
                anotherBridges.add(line);
            }
        }

        bridgeAdapter.notifyDataSetChanged();

        if (bridgeList.isEmpty()) {
            tvBridgesListEmpty.setVisibility(View.VISIBLE);
        } else {
            tvBridgesListEmpty.setVisibility(View.GONE);
        }
    }

    private AdapterView.OnItemSelectedListener onItemSelectedListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

            if (getActivity() == null) {
                return;
            }

            switch (adapterView.getId()) {
                case R.id.spDefaultBridges:
                    new PrefManager(getActivity()).setStrPref("defaultBridgesObfs", String.valueOf(i));
                    if (rbDefaultBridges.isChecked()) {
                        bridges_file_path = appDataDir + "/app_data/tor/bridges_default.lst";
                        FileOperations.readTextFile(getActivity(), bridges_file_path, defaultBridgesOperationTag);
                    }
                    break;
                case R.id.spOwnBridges:
                    new PrefManager(getActivity()).setStrPref("ownBridgesObfs", String.valueOf(i));
                    if (rbOwnBridges.isChecked()) {
                        bridges_file_path = appDataDir + "/app_data/tor/bridges_custom.lst";
                        FileOperations.readTextFile(getActivity(), bridges_file_path, ownBridgesOperationTag);
                    }
                    break;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    };

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag, List<String> lines) {

        if (getActivity() == null) {
            return;
        }

        if (fileOperationResult && currentFileOperation == readTextFile) {
            switch (tag) {
                case torConfTag:
                    tor_conf = lines;

                    if (tor_conf == null) return;

                    tor_conf_orig.addAll(tor_conf);

                    for (int i = 0; i < tor_conf.size(); i++) {
                        String line = tor_conf.get(i);
                        if (!line.contains("#") && line.contains("Bridge ")) {
                            currentBridges.add(line.replace("Bridge ", "").trim());
                        }
                    }

                    if (!currentBridges.isEmpty()) {
                        String testBridge = currentBridges.get(0);
                        if (testBridge.contains("obfs4")) {
                            currentBridgesType = obfs4;
                        } else if (testBridge.contains("obfs3")) {
                            currentBridgesType = obfs3;
                        } else if (testBridge.contains("scramblesuit")) {
                            currentBridgesType = scramblesuit;
                        } else if (testBridge.contains("meek_lite")) {
                            currentBridgesType = meek_lite;
                        } else if (testBridge.contains("snowflake")) {
                            currentBridgesType = snowflake;
                        } else {
                            currentBridgesType = vanilla;
                        }
                    } else {
                        currentBridgesType = undefined;
                    }
                    break;
                case addBridgesTag: {
                    final List<String> bridges_lst = lines;
                    if (bridges_lst != null) {
                        getActivity().runOnUiThread(() -> addBridges(bridges_lst));
                    }
                    break;
                }
                case defaultBridgesOperationTag: {
                    final List<String> bridges_lst = lines;
                    if (bridges_lst != null) {
                        getActivity().runOnUiThread(() -> defaultBridgesOperation(bridges_lst));
                    }
                    break;
                }
                case ownBridgesOperationTag: {
                    final List<String> bridges_lst = lines;
                    if (bridges_lst != null) {
                        getActivity().runOnUiThread(() -> ownBridgesOperation(bridges_lst));
                    }
                    break;
                }
                case addRequestedBridgesTag: {
                    final List<String> bridges_lst = lines;
                    if (bridges_lst != null) {
                        getActivity().runOnUiThread(() -> addRequestedBridges(requestedBridgesToAdd, bridges_lst));
                    }
                    break;
                }
            }
        }
    }

    @Override
    public BridgeType getCurrentBridgesType() {
        return currentBridgesType;
    }

    @Override
    public BridgesSelector getSavedBridgesSelector() {
        return savedBridgesSelector;
    }

    @Override
    public void setSavedBridgesSelector(BridgesSelector selector) {
        this.savedBridgesSelector = selector;
    }

    @Override
    public void setCurrentBridgesType(BridgeType type) {
        this.currentBridgesType = type;
    }

    @Override
    public List<String> getCurrentBridges() {
        return currentBridges;
    }

    @Override
    public List<ObfsBridge> getBridgeList() {
        return bridgeList;
    }

    @Override
    public BridgeAdapter getBridgeAdapter() {
        return bridgeAdapter;
    }

    @Override
    public List<String> getAnotherBridges() {
        return anotherBridges;
    }

    @Override
    public String get_bridges_file_path() {
        return bridges_file_path;
    }

    private Future<?> verifyNewDefaultBridgesExist(Context context, boolean useDefaultBridges) {

        return CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            File outputFile = new File(appDataDir + "/app_data/tor/bridges_default.lst");
            long installedBridgesSize = outputFile.length();

            try (ZipInputStream zipInputStream = new ZipInputStream(context.getAssets().open("tor.mp3"))) {

                ZipEntry zipEntry = zipInputStream.getNextEntry();

                while (zipEntry != null) {

                    String fileName = zipEntry.getName();
                    if (fileName.contains("bridges_default.lst") && zipEntry.getSize() != installedBridgesSize) {
                        if (isAdded() && handler != null) {
                            handler.post(() -> {
                                AlertDialog dialog = UpdateDefaultBridgesDialog.DIALOG.getDialog(getActivity(), useDefaultBridges);
                                if (isAdded() && dialog != null) {
                                    dialog.show();
                                }
                            });
                        }
                        break;
                    }


                    zipEntry = zipInputStream.getNextEntry();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "PreferencesTorBridges verifyNewDefaultBridgesExist exception " + e.getMessage() + " " + e.getCause());
            }
        });
    }

}
