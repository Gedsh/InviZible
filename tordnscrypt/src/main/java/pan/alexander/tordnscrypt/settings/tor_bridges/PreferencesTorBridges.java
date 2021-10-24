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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.app.Activity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.dialogs.UpdateDefaultBridgesDialog;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;
import pan.alexander.tordnscrypt.utils.enums.BridgeType;
import pan.alexander.tordnscrypt.utils.enums.BridgesSelector;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALWAYS_SHOW_HELP_MESSAGES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DEFAULT_BRIDGES_OBFS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.OWN_BRIDGES_OBFS;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.meek_lite;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.vanilla;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.obfs3;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.obfs4;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.scramblesuit;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.snowflake;
import static pan.alexander.tordnscrypt.utils.enums.BridgeType.undefined;
import static pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants.readTextFile;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;

import javax.inject.Inject;


public class PreferencesTorBridges extends Fragment implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener,
        OnTextFileOperationsCompleteListener, PreferencesBridges {
    public final static String snowFlakeBridgesDefault = "3";
    public final static String snowFlakeBridgesOwn = "4";

    private final String torConfTag = "pan.alexander.tordnscrypt/app_data/tor/tor.conf";
    private final String defaultBridgesOperationTag = "pan.alexander.tordnscrypt/abstract_default_bridges_operation";
    private final String ownBridgesOperationTag = "pan.alexander.tordnscrypt/abstract_own_bridges_operation";
    private final String addBridgesTag = "pan.alexander.tordnscrypt/abstract_add_bridges";
    private final String addRequestedBridgesTag = "pan.alexander.tordnscrypt/abstract_add_requested_bridges";

    private final List<String> tor_conf = new ArrayList<>();
    private final Set<String> currentBridges = new HashSet<>();
    private final List<String> anotherBridges = new ArrayList<>();
    private final List<ObfsBridge> bridgeList = new ArrayList<>();

    private RadioButton rbNoBridges;
    private RadioButton rbDefaultBridges;
    private RadioButton rbOwnBridges;
    private Spinner spDefaultBridges;
    private Spinner spOwnBridges;
    private TextView tvBridgesListEmpty;
    private RecyclerView rvBridges;
    private BridgeAdapter bridgeAdapter;

    private String appDataDir;
    private String obfsPath;
    private String snowflakePath;
    private String currentBridgesFilePath;
    private String bridgesDefaultFilePath;
    private String bridgesCustomFilePath;

    private BridgeType currentBridgesType = undefined;
    private String requestedBridgesToAdd;
    private BridgesSelector savedBridgesSelector;
    private Future<?> verifyDefaultBridgesTask;
    private Handler handler;
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;


    public PreferencesTorBridges() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.instance.daggerComponent.inject(this);

        super.onCreate(savedInstanceState);

        setRetainInstance(true);

        Context context = getActivity();

        if (context== null) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(context);

        appDataDir = pathVars.getAppDataDir();
        obfsPath = pathVars.getObfsPath();
        snowflakePath = pathVars.getSnowflakePath();

        currentBridgesFilePath = appDataDir + "/app_data/tor/bridges_default.lst";
        bridgesDefaultFilePath = appDataDir + "/app_data/tor/bridges_default.lst";
        bridgesCustomFilePath = appDataDir + "/app_data/tor/bridges_custom.lst";

        Looper looper = Looper.getMainLooper();
        if (looper != null) {
            handler = new Handler(looper);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        activity.setTitle(R.string.pref_fast_use_tor_bridges);

        View view = inflater.inflate(R.layout.fragment_preferences_tor_bridges, container, false);

        rbNoBridges = view.findViewById(R.id.rbNoBridges);

        rbDefaultBridges = view.findViewById(R.id.rbDefaultBridges);

        spDefaultBridges = view.findViewById(R.id.spDefaultBridges);
        spDefaultBridges.setPrompt(getString(R.string.pref_fast_use_tor_bridges_obfs));

        rbOwnBridges = view.findViewById(R.id.rbOwnBridges);

        spOwnBridges = view.findViewById(R.id.spOwnBridges);
        spOwnBridges.setPrompt(getString(R.string.pref_fast_use_tor_bridges_obfs));

        Button btnRequestBridges = view.findViewById(R.id.btnRequestBridges);
        btnRequestBridges.setOnClickListener(this);
        Button btnAddBridges = view.findViewById(R.id.btnAddBridges);
        btnAddBridges.setOnClickListener(this);

        tvBridgesListEmpty = view.findViewById(R.id.tvBridgesListEmpty);

        rvBridges = view.findViewById(R.id.rvBridges);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(activity);
        rvBridges.setLayoutManager(mLayoutManager);

        FileManager.setOnFileOperationCompleteListener(this);

        FileManager.readTextFile(activity, appDataDir + "/app_data/tor/tor.conf", torConfTag);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Context context = getActivity();

        if (context == null || !isAdded()) {
            return;
        }

        PreferenceRepository preferences = preferenceRepository.get();

        if (!preferences.getStringPreference(DEFAULT_BRIDGES_OBFS).isEmpty())
            spDefaultBridges.setSelection(Integer.parseInt(preferences.getStringPreference(DEFAULT_BRIDGES_OBFS)));

        if (!preferences.getStringPreference(OWN_BRIDGES_OBFS).isEmpty())
            spOwnBridges.setSelection(Integer.parseInt(preferences.getStringPreference(OWN_BRIDGES_OBFS)));


        Activity activity = getActivity();
        if (activity instanceof SettingsActivity) {
            bridgeAdapter = new BridgeAdapter((SettingsActivity) activity, getParentFragmentManager(), this);
            rvBridges.setAdapter(bridgeAdapter);
        }

        boolean useNoBridges = preferences.getBoolPreference("useNoBridges");
        boolean useDefaultBridges = preferences.getBoolPreference("useDefaultBridges");
        boolean useOwnBridges = preferences.getBoolPreference("useOwnBridges");

        if (!useNoBridges && !useDefaultBridges && !useOwnBridges) {
            tvBridgesListEmpty.setVisibility(View.GONE);
            savedBridgesSelector = BridgesSelector.NO_BRIDGES;
        } else if (useNoBridges) {
            noBridgesOperation();
            savedBridgesSelector = BridgesSelector.NO_BRIDGES;
        } else if (useDefaultBridges) {
            FileManager.readTextFile(context, currentBridgesFilePath, defaultBridgesOperationTag);
            rbDefaultBridges.setChecked(true);
            savedBridgesSelector = BridgesSelector.DEFAULT_BRIDGES;
        } else {
            currentBridgesFilePath = bridgesCustomFilePath;
            FileManager.readTextFile(context, currentBridgesFilePath, ownBridgesOperationTag);
            rbOwnBridges.setChecked(true);
            savedBridgesSelector = BridgesSelector.OWN_BRIDGES;
        }

        if (!preferences.getBoolPreference("doNotShowNewDefaultBridgesDialog")) {
            verifyDefaultBridgesTask = verifyNewDefaultBridgesExist(context, useDefaultBridges);
        }

        rbNoBridges.setOnCheckedChangeListener(this);
        rbDefaultBridges.setOnCheckedChangeListener(this);
        rbOwnBridges.setOnCheckedChangeListener(this);
        spDefaultBridges.setOnItemSelectedListener(this);
        spOwnBridges.setOnItemSelectedListener(this);

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
                    saveUseBridgesPreferences(context, true, false, false);
                    break;
                case DEFAULT_BRIDGES:
                    saveUseBridgesPreferences(context, false, true, false);
                    break;
                case OWN_BRIDGES:
                    saveUseBridgesPreferences(context, false, false, true);
                    break;
                default:
                    saveUseBridgesPreferences(context, false, false, false);
                    break;
            }
        }

        List<String> torConfCleaned = new ArrayList<>();

        for (int i = 0; i < tor_conf.size(); i++) {
            String line = tor_conf.get(i);
            if ((line.contains("#")
                    || (!line.contains("Bridge ")
                    && !line.contains("ClientTransportPlugin ")
                    && !line.contains("UseBridges ")))
                    && !line.isEmpty()) {
                torConfCleaned.add(line);
            }
        }

        String currentBridgesTypeToSave;
        if (currentBridgesType.equals(vanilla)) {
            currentBridgesTypeToSave = "";
        } else {
            currentBridgesTypeToSave = currentBridgesType.toString();
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean saveExtendedLogs = sharedPreferences.getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false);
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

            torConfCleaned.add("UseBridges 1");

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
                            + snowflakePath + " -url https://snowflake-broker.torproject.net.global.prod.fastly.net/" +
                            " -front cdn.sstatic.net -ice " + stunServers.toString() + " -max 1" + saveLogsString;
                } else {
                    clientTransportPlugin = "ClientTransportPlugin " + currentBridgesTypeToSave + " exec "
                            + obfsPath;
                }


                torConfCleaned.add(clientTransportPlugin);
            }

            for (String currentBridge: currentBridges) {

                if (currentBridgesType == vanilla) {
                    if (!currentBridge.isEmpty() && !currentBridge.contains(obfs4.toString())
                            && !currentBridge.contains(obfs3.toString()) && !currentBridge.contains(scramblesuit.toString())
                            && !currentBridge.contains(meek_lite.toString()) && !currentBridge.contains(snowflake.toString())) {
                        torConfCleaned.add("Bridge " + currentBridge);
                    }
                } else {
                    if (!currentBridge.isEmpty() && currentBridge.contains(currentBridgesType.toString())) {
                        torConfCleaned.add("Bridge " + currentBridge);
                    }
                }

            }

        } else {
            torConfCleaned.add("UseBridges 0");
        }

        if (torConfCleaned.size() == tor_conf.size() && torConfCleaned.containsAll(tor_conf)) {
            return;
        }

        FileManager.writeToTextFile(context, appDataDir + "/app_data/tor/tor.conf", torConfCleaned, "ignored");

        ///////////////////////Tor restart/////////////////////////////////////////////
        boolean torRunning = ModulesStatus.getInstance().getTorState() == RUNNING;

        if (torRunning) {
            ModulesRestarter.restartTor(context);
            Toast.makeText(context, getText(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        FileManager.deleteOnFileOperationCompleteListener(this);

        rbNoBridges.setOnCheckedChangeListener(null);
        rbNoBridges = null;

        rbDefaultBridges.setOnCheckedChangeListener(null);
        rbDefaultBridges = null;

        rbOwnBridges.setOnCheckedChangeListener(null);
        rbOwnBridges = null;

        spDefaultBridges.setOnItemSelectedListener(null);
        spDefaultBridges = null;

        spOwnBridges.setOnItemSelectedListener(null);
        spOwnBridges = null;

        tvBridgesListEmpty = null;
        rvBridges = null;
        bridgeAdapter = null;
        savedBridgesSelector = null;
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

        requestedBridgesToAdd = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btnAddBridges) {
            FileManager.readTextFile(getActivity(), appDataDir + "/app_data/tor/bridges_custom.lst", addBridgesTag);
        } else if (id == R.id.btnRequestBridges) {
            GetNewBridges getNewBridges = new GetNewBridges(new WeakReference<>((SettingsActivity) getActivity()));
            getNewBridges.selectTransport();
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
                currentBridgesFilePath = bridgesCustomFilePath;
                FileManager.writeToTextFile(getActivity(), currentBridgesFilePath, bridgesListNew, "ignored");

                if (getActivity() == null || getActivity().isFinishing()) {
                    return;
                }

                boolean useOwnBridges = preferenceRepository.get().getBoolPreference("useOwnBridges");
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

    private void addRequestedBridges(String bridgesToAdd, List<String> savedCustomBridges) {
        List<String> bridgesListNew = new ArrayList<>();
        String[] bridgesArrNew = bridgesToAdd.split("\n");

        if (bridgesArrNew.length != 0) {
            for (String brgNew : bridgesArrNew) {
                if (!brgNew.isEmpty()) {
                    bridgesListNew.add(brgNew.trim());
                }
            }

            if (savedCustomBridges != null) {
                List<String> retainList = new ArrayList<>(savedCustomBridges);
                retainList.retainAll(bridgesListNew);
                bridgesListNew.removeAll(retainList);
                savedCustomBridges.addAll(bridgesListNew);
                bridgesListNew = savedCustomBridges;
            }


            Collections.sort(bridgesListNew);
            currentBridgesFilePath = bridgesCustomFilePath;
            FileManager.writeToTextFile(getActivity(), currentBridgesFilePath, bridgesListNew, "ignored");

            if (!bridgesToAdd.isEmpty()) {
                if (bridgesToAdd.contains("obfs4")) {
                    if (!spOwnBridges.getSelectedItem().toString().equals("obfs4")) {
                        spOwnBridges.setSelection(0);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridgesToAdd.contains("obfs3")) {
                    if (!spOwnBridges.getSelectedItem().toString().equals("obfs3")) {
                        spOwnBridges.setSelection(1);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridgesToAdd.contains("scramblesuit")) {
                    if (!spOwnBridges.getSelectedItem().toString().equals("scramblesuit")) {
                        spOwnBridges.setSelection(2);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridgesToAdd.contains("meek_lite")) {
                    if (!spOwnBridges.getSelectedItem().toString().equals("meek_lite")) {
                        spOwnBridges.setSelection(3);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridgesToAdd.contains("snowflake")) {
                    if (!spOwnBridges.getSelectedItem().toString().equals("snowflake")) {
                        spOwnBridges.setSelection(4);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else {
                    if (!spOwnBridges.getSelectedItem().toString().equals("vanilla")) {
                        spOwnBridges.setSelection(5);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                }

            }

            if (getActivity() == null || getActivity().isFinishing()) {
                return;
            }

            boolean useOwnBridges = preferenceRepository.get().getBoolPreference("useOwnBridges");

            if (!useOwnBridges) {
                currentBridges.clear();
                rbOwnBridges.performClick();
            }
        }
    }

    public void readSavedCustomBridges(String bridgesToAdd) {
        requestedBridgesToAdd = bridgesToAdd;

        currentBridgesFilePath = bridgesCustomFilePath;

        FileManager.readTextFile(getActivity(), currentBridgesFilePath, addRequestedBridgesTag);
    }

    private void noBridgesOperation() {
        rbDefaultBridges.setChecked(false);
        rbOwnBridges.setChecked(false);

        bridgeList.clear();
        currentBridges.clear();
        if (bridgeAdapter != null)
            bridgeAdapter.notifyDataSetChanged();

        tvBridgesListEmpty.setVisibility(View.GONE);
    }

    private void defaultBridgesOperation(List<String> bridgesDefault) {
        rbNoBridges.setChecked(false);
        rbOwnBridges.setChecked(false);

        bridgeList.clear();
        anotherBridges.clear();

        BridgeType obfsTypeSp = BridgeType.valueOf(spDefaultBridges.getSelectedItem().toString());

        if (bridgesDefault == null)
            return;

        for (String line : bridgesDefault) {
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

        if (bridgeAdapter != null)
            bridgeAdapter.notifyDataSetChanged();

        if (bridgeList.isEmpty()) {
            tvBridgesListEmpty.setVisibility(View.VISIBLE);
        } else {
            tvBridgesListEmpty.setVisibility(View.GONE);
        }
    }

    private void ownBridgesOperation(List<String> bridgesCustom) {
        rbNoBridges.setChecked(false);
        rbDefaultBridges.setChecked(false);

        bridgeList.clear();
        anotherBridges.clear();

        BridgeType obfsTypeSp = BridgeType.valueOf(spOwnBridges.getSelectedItem().toString());

        if (bridgesCustom == null)
            return;

        for (String line : bridgesCustom) {
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

        if (bridgeAdapter != null)
            bridgeAdapter.notifyDataSetChanged();

        if (bridgeList.isEmpty()) {
            tvBridgesListEmpty.setVisibility(View.VISIBLE);
        } else {
            tvBridgesListEmpty.setVisibility(View.GONE);
        }
    }

    @Override
    public void OnFileOperationComplete(FileOperationsVariants currentFileOperation, boolean fileOperationResult, String path, String tag, List<String> lines) {

        Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (fileOperationResult && currentFileOperation == readTextFile) {
            switch (tag) {
                case torConfTag:
                    if (lines == null || lines.isEmpty()) {
                        return;
                    }

                    tor_conf.clear();
                    currentBridges.clear();

                    for (String line: lines) {
                        if (!line.trim().isEmpty()) {
                            tor_conf.add(line);
                        }
                    }

                    for (int i = 0; i < tor_conf.size(); i++) {
                        String line = tor_conf.get(i);
                        if (!line.contains("#") && line.contains("Bridge ")) {
                            currentBridges.add(line.replace("Bridge ", "").trim());
                        }
                    }

                    if (!currentBridges.isEmpty()) {
                        String testBridge = currentBridges.toString();
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
                    if (handler != null && bridges_lst != null) {
                        handler.post(() -> addBridges(bridges_lst));
                    }
                    break;
                }
                case defaultBridgesOperationTag: {
                    final List<String> savedDefaultBridges = lines;
                    if (handler != null &&  savedDefaultBridges != null) {
                        handler.post(() -> defaultBridgesOperation(savedDefaultBridges));
                    }
                    break;
                }
                case ownBridgesOperationTag: {
                    final List<String> savedCustomBridges = lines;
                    if (handler != null && savedCustomBridges != null) {
                        handler.post(() -> ownBridgesOperation(savedCustomBridges));
                    }
                    break;
                }
                case addRequestedBridgesTag: {
                    final List<String> savedCustomBridges = lines;
                    if (handler != null && savedCustomBridges != null) {
                        handler.post(() -> addRequestedBridges(requestedBridgesToAdd, savedCustomBridges));
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
    public Set<String> getCurrentBridges() {
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
    public String getBridgesFilePath() {
        return currentBridgesFilePath;
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

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean newValue) {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        int id = compoundButton.getId();

        if (id == R.id.rbNoBridges) {
            if (newValue) {

                saveUseBridgesPreferences(context, true, false, false);

                noBridgesOperation();
            }
        } else if (id == R.id.rbDefaultBridges) {
            if (newValue) {

                saveUseBridgesPreferences(context, false, true, false);

                currentBridgesFilePath = bridgesDefaultFilePath;

                FileManager.readTextFile(context, currentBridgesFilePath, defaultBridgesOperationTag);
            }
        } else if (id == R.id.rbOwnBridges) {
            if (newValue) {

                saveUseBridgesPreferences(context, false, false, true);

                currentBridgesFilePath = bridgesCustomFilePath;

                FileManager.readTextFile(context, currentBridgesFilePath, ownBridgesOperationTag);
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

        Context context = getActivity();

        if (context == null) {
            return;
        }

        int id = adapterView.getId();

        if (id == R.id.spDefaultBridges) {
            preferenceRepository.get().setStringPreference(DEFAULT_BRIDGES_OBFS, String.valueOf(i));
            if (rbDefaultBridges.isChecked()) {
                currentBridgesFilePath = bridgesDefaultFilePath;
                FileManager.readTextFile(context, currentBridgesFilePath, defaultBridgesOperationTag);
            }
        } else if (id == R.id.spOwnBridges) {
            preferenceRepository.get().setStringPreference(OWN_BRIDGES_OBFS, String.valueOf(i));
            if (rbOwnBridges.isChecked()) {
                currentBridgesFilePath = bridgesCustomFilePath;
                FileManager.readTextFile(context, currentBridgesFilePath, ownBridgesOperationTag);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private void saveUseBridgesPreferences(Context context,
                                        boolean useNoBridges,
                                        boolean useDefaultBridges,
                                        boolean useOwnBridges) {
        preferenceRepository.get().setBoolPreference("useNoBridges", useNoBridges);
        preferenceRepository.get().setBoolPreference("useDefaultBridges", useDefaultBridges);
        preferenceRepository.get().setBoolPreference("useOwnBridges", useOwnBridges);
    }
}
