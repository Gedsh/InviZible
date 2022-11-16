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

    Copyright 2019-2022 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
import pan.alexander.tordnscrypt.dialogs.BridgesCaptchaDialogFragment;
import pan.alexander.tordnscrypt.dialogs.BridgesReadyDialogFragment;
import pan.alexander.tordnscrypt.dialogs.ExtendedDialogFragment;
import pan.alexander.tordnscrypt.dialogs.SelectBridgesTransportDialogFragment;
import pan.alexander.tordnscrypt.dialogs.progressDialogs.PleaseWaitDialogBridgesRequest;
import pan.alexander.tordnscrypt.domain.bridges.BridgePingData;
import pan.alexander.tordnscrypt.domain.bridges.BridgePingResult;
import pan.alexander.tordnscrypt.domain.bridges.PingCheckComplete;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.UpdateDefaultBridgesDialog;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesRestarter;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.enums.BridgeType;
import pan.alexander.tordnscrypt.utils.enums.BridgesSelector;
import pan.alexander.tordnscrypt.utils.enums.FileOperationsVariants;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;
import pan.alexander.tordnscrypt.utils.filemanager.OnTextFileOperationsCompleteListener;

import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.DEFAULT_BRIDGES_OBFS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.RELAY_BRIDGES_REQUESTED;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.OWN_BRIDGES_OBFS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_DEFAULT_BRIDGES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_NO_BRIDGES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_OWN_BRIDGES;
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


@SuppressLint("UnsafeOptInUsageWarning")
public class PreferencesTorBridges extends Fragment implements View.OnClickListener,
        CompoundButton.OnCheckedChangeListener, AdapterView.OnItemSelectedListener,
        OnTextFileOperationsCompleteListener, PreferencesBridges, SwipeRefreshLayout.OnRefreshListener {
    public final static String SNOWFLAKE_BRIDGES_DEFAULT = "3";
    public final static String SNOWFLAKE_BRIDGES_OWN = "4";

    private final static int DEFAULT_VANILLA_BRIDGES_DISPLAY_COUNT = 5;

    private final String TOR_CONF_FLAG = "pan.alexander.tordnscrypt/app_data/tor/tor.conf";
    private final String DEFAULT_BRIDGES_OPERATION_TAG = "pan.alexander.tordnscrypt/abstract_default_bridges_operation";
    private final String OWN_BRIDGES_OPERATION_TAG = "pan.alexander.tordnscrypt/abstract_own_bridges_operation";
    private final String ADD_BRIDGES_TAG = "pan.alexander.tordnscrypt/abstract_add_bridges";
    private final String ADD_REQUESTED_BRIDGES_TAG = "pan.alexander.tordnscrypt/abstract_add_requested_bridges";

    private final List<String> tor_conf = new ArrayList<>();
    private final Set<String> bridgesInUse = new HashSet<>();
    private final List<String> bridgesInappropriateType = new ArrayList<>();
    private final List<ObfsBridge> bridgesToDisplay = new ArrayList<>();

    private RadioButton rbNoBridges;
    private RadioButton rbDefaultBridges;
    private RadioButton rbOwnBridges;
    private Spinner spDefaultBridges;
    private Spinner spOwnBridges;
    private TextView tvBridgesListEmpty;
    private RecyclerView rvBridges;
    private BridgeAdapter bridgeAdapter;
    private SwipeRefreshLayout swipeRefreshBridges;

    private String appDataDir;
    private String obfsPath;
    private String currentBridgesFilePath;
    private String bridgesDefaultFilePath;
    private String bridgesCustomFilePath;

    private BridgeType currentBridgesType = undefined;
    private String requestedBridgesToAdd;
    private BridgesSelector savedBridgesSelector;
    private Future<?> verifyDefaultBridgesTask;

    private PreferencesTorBridgesViewModel viewModel;
    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();

    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public Lazy<PathVars> pathVars;
    @Inject
    public CachedExecutor cachedExecutor;
    @Inject
    public Lazy<Handler> handlerLazy;
    @Inject
    public Lazy<SnowflakeConfigurator> snowflakeConfigurator;
    @Inject
    public ViewModelProvider.Factory viewModelFactory;
    @Inject
    public Lazy<SelectBridgesTransportDialogFragment> selectBridgesTransportDialogFragment;
    @Inject
    public Lazy<PleaseWaitDialogBridgesRequest> pleaseWaitDialogBridgesRequest;
    @Inject
    public Lazy<BridgesCaptchaDialogFragment> bridgesCaptchaDialogFragment;
    @Inject
    public Lazy<BridgesReadyDialogFragment> bridgesReadyDialogFragment;

    public PreferencesTorBridges() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);

        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this, viewModelFactory).get(PreferencesTorBridgesViewModel.class);

        appDataDir = pathVars.get().getAppDataDir();
        obfsPath = pathVars.get().getObfsPath();

        currentBridgesFilePath = appDataDir + "/app_data/tor/bridges_default.lst";
        bridgesDefaultFilePath = appDataDir + "/app_data/tor/bridges_default.lst";
        bridgesCustomFilePath = appDataDir + "/app_data/tor/bridges_custom.lst";
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

        swipeRefreshBridges = view.findViewById(R.id.swipeRefreshBridges);
        swipeRefreshBridges.setOnRefreshListener(this);

        FileManager.setOnFileOperationCompleteListener(this);

        FileManager.readTextFile(activity, appDataDir + "/app_data/tor/tor.conf", TOR_CONF_FLAG);

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
            bridgeAdapter = new BridgeAdapter(
                    (SettingsActivity) activity,
                    getParentFragmentManager(),
                    preferenceRepository,
                    this
            );
            rvBridges.setAdapter(bridgeAdapter);
        }

        boolean useNoBridges = preferences.getBoolPreference(USE_NO_BRIDGES);
        boolean useDefaultBridges = preferences.getBoolPreference(USE_DEFAULT_BRIDGES);
        boolean useOwnBridges = preferences.getBoolPreference(USE_OWN_BRIDGES);

        if (!useNoBridges && !useDefaultBridges && !useOwnBridges) {
            tvBridgesListEmpty.setVisibility(View.GONE);
            savedBridgesSelector = BridgesSelector.NO_BRIDGES;
        } else if (useNoBridges) {
            noBridgesOperation();
            savedBridgesSelector = BridgesSelector.NO_BRIDGES;
        } else if (useDefaultBridges) {
            FileManager.readTextFile(context, currentBridgesFilePath, DEFAULT_BRIDGES_OPERATION_TAG);
            rbDefaultBridges.setChecked(true);
            savedBridgesSelector = BridgesSelector.DEFAULT_BRIDGES;
        } else {
            currentBridgesFilePath = bridgesCustomFilePath;
            FileManager.readTextFile(context, currentBridgesFilePath, OWN_BRIDGES_OPERATION_TAG);
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

        observeDialogsFlow();
        observeTimeouts();
        observeDefaultVanillaBridges();
        observeErrors();
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();

        if (context == null) {
            return;
        }

        if (!bridgesInUse.isEmpty()) {
            switch (savedBridgesSelector) {
                case NO_BRIDGES:
                    saveUseBridgesPreferences(true, false, false);
                    break;
                case DEFAULT_BRIDGES:
                    saveUseBridgesPreferences(false, true, false);
                    break;
                case OWN_BRIDGES:
                    saveUseBridgesPreferences(false, false, true);
                    break;
                default:
                    saveUseBridgesPreferences(false, false, false);
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

        if (!bridgesInUse.isEmpty() && !currentBridgesType.equals(undefined)) {

            torConfCleaned.add("UseBridges 1");

            if (!currentBridgesType.equals(vanilla)) {

                String clientTransportPlugin;
                if (currentBridgesType.equals(snowflake)) {
                    clientTransportPlugin = snowflakeConfigurator.get().getConfiguration();
                } else {
                    clientTransportPlugin = "ClientTransportPlugin " + currentBridgesTypeToSave + " exec "
                            + obfsPath;
                }

                torConfCleaned.add(clientTransportPlugin);
            }

            for (String currentBridge : bridgesInUse) {

                if (currentBridgesType == vanilla) {
                    if (!currentBridge.isEmpty() && !currentBridge.contains(obfs4.toString())
                            && !currentBridge.contains(obfs3.toString()) && !currentBridge.contains(scramblesuit.toString())
                            && !currentBridge.contains(meek_lite.toString()) && !currentBridge.contains(snowflake.toString())) {
                        torConfCleaned.add("Bridge " + currentBridge);
                    }
                } else {
                    if (!currentBridge.isEmpty() && currentBridge.contains(currentBridgesType.toString())) {
                        if (currentBridgesType.equals(snowflake)) {
                            torConfCleaned.add(
                                    "Bridge " + currentBridge
                                            + " utls-imitate="
                                            + snowflakeConfigurator.get().getUtlsClientID()

                            );
                        } else {
                            torConfCleaned.add("Bridge " + currentBridge);
                        }
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

        restartTorIfRequired(context);

    }

    private void restartTorIfRequired(Context context) {
        if (modulesStatus.getTorState() == RUNNING) {
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
        swipeRefreshBridges = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        handlerLazy.get().removeCallbacksAndMessages(null);

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
            FileManager.readTextFile(getActivity(), appDataDir + "/app_data/tor/bridges_custom.lst", ADD_BRIDGES_TAG);
        } else if (id == R.id.btnRequestBridges) {
            viewModel.showSelectRequestBridgesTypeDialog();
        }
    }

    private void observeDialogsFlow() {
        viewModel.getDialogsFlowLiveData().observe(getViewLifecycleOwner(), dialogsState -> {
            if (dialogsState instanceof DialogsFlowState.PleaseWaitDialog) {
                showPleaseWaitDialog();
            } else if (dialogsState instanceof DialogsFlowState.SelectBridgesTransportDialog) {
                showSelectBridgesTransportDialog();
            } else if (dialogsState instanceof DialogsFlowState.CaptchaDialog) {
                showCaptchaDialog(
                        ((DialogsFlowState.CaptchaDialog) dialogsState).getTransport(),
                        ((DialogsFlowState.CaptchaDialog) dialogsState).getCaptcha(),
                        ((DialogsFlowState.CaptchaDialog) dialogsState).getSecretCode()
                );
            } else if (dialogsState instanceof DialogsFlowState.BridgesReadyDialog) {
                showBridgesReadyDialog(
                        ((DialogsFlowState.BridgesReadyDialog) dialogsState).getBridges()
                );
            } else if (dialogsState instanceof DialogsFlowState.NoDialogs) {
                dismissRequestBridgesDialogs();
            } else if (dialogsState instanceof DialogsFlowState.ErrorMessage) {
                showErrorMessage(((DialogsFlowState.ErrorMessage) dialogsState).getMessage());
            }
        });
    }

    private void showPleaseWaitDialog() {
        String tag = PleaseWaitDialogBridgesRequest.class.getCanonicalName();
        PleaseWaitDialogBridgesRequest dialog =
                (PleaseWaitDialogBridgesRequest) getChildFragmentManager().findFragmentByTag(tag);
        if (dialog == null || !dialog.isAdded()) {
            pleaseWaitDialogBridgesRequest.get().show(getChildFragmentManager(), tag);
        }
    }

    private void showSelectBridgesTransportDialog() {
        String tag = SelectBridgesTransportDialogFragment.class.getCanonicalName();
        SelectBridgesTransportDialogFragment dialog =
                (SelectBridgesTransportDialogFragment) getChildFragmentManager().findFragmentByTag(tag);
        if (dialog == null || !dialog.isAdded()) {
            selectBridgesTransportDialogFragment.get().show(getChildFragmentManager(), tag);
        }
    }

    private void showCaptchaDialog(String transport, Bitmap captcha, String secretCode) {
        String tag = BridgesCaptchaDialogFragment.class.getCanonicalName();
        BridgesCaptchaDialogFragment dialog =
                (BridgesCaptchaDialogFragment) getChildFragmentManager().findFragmentByTag(tag);
        if (dialog == null || !dialog.isAdded()) {
            dialog = bridgesCaptchaDialogFragment.get();
            dialog.setTransport(transport);
            dialog.setCaptcha(captcha);
            dialog.setSecretCode(secretCode);
            dialog.show(getChildFragmentManager(), tag);
        }
    }

    private void showBridgesReadyDialog(String bridges) {
        String tag = BridgesReadyDialogFragment.class.getCanonicalName();
        BridgesReadyDialogFragment dialog =
                (BridgesReadyDialogFragment) getChildFragmentManager().findFragmentByTag(tag);
        if (dialog == null || !dialog.isAdded()) {
            dialog = bridgesReadyDialogFragment.get();
            dialog.setBridges(bridges);
            dialog.show(getChildFragmentManager(), tag);
        }
    }

    private void dismissRequestBridgesDialogs() {
        FragmentManager fragmentManager = getChildFragmentManager();
        fragmentManager.executePendingTransactions();
        for (Fragment dialog : fragmentManager.getFragments()) {
            if (dialog instanceof ExtendedDialogFragment) {
                ((ExtendedDialogFragment) dialog).dismiss();
            }
        }
    }

    private void showErrorMessage(String message) {
        dismissRequestBridgesDialogs();
        Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    private void observeTimeouts() {
        viewModel.getTimeoutLiveData().observe(getViewLifecycleOwner(), bridgePingData ->
                doActionAndUpdateRecycler(() -> {

                    for (BridgePingResult bridgePing : bridgePingData) {
                        if (bridgePing instanceof BridgePingData) {
                            for (ObfsBridge obfsBridge : bridgesToDisplay) {
                                if (obfsBridge.bridge.hashCode() == ((BridgePingData) bridgePing).getBridgeHash()) {
                                    obfsBridge.ping = ((BridgePingData) bridgePing).getPing();
                                }
                            }
                        }
                    }

                    if (bridgePingData.contains(PingCheckComplete.INSTANCE)) {
                        sortBridgesByPing();
                        limitDisplayedBridgesInCaseOfDefaultVanillaBridges();
                        swipeRefreshBridges.setRefreshing(false);
                    } else {
                        sortBridgesByPing();

                        if (!swipeRefreshBridges.isRefreshing()) {
                            swipeRefreshBridges.setRefreshing(true);
                        }
                    }

                }));
    }

    private void limitDisplayedBridgesInCaseOfDefaultVanillaBridges() {
        if (areDefaultVanillaBridgesSelected()
                && bridgesToDisplay.size() > DEFAULT_VANILLA_BRIDGES_DISPLAY_COUNT) {
            Iterator<ObfsBridge> iterator = bridgesToDisplay.listIterator();
            int counter = 0;
            while (iterator.hasNext()) {
                iterator.next();
                if (++counter > DEFAULT_VANILLA_BRIDGES_DISPLAY_COUNT) {
                    iterator.remove();
                }
            }
        }
    }

    private void observeDefaultVanillaBridges() {
        viewModel.getDefaultVanillaBridgesLiveData().observe(getViewLifecycleOwner(), (bridges) -> {
            swipeRefreshBridges.setRefreshing(false);
            if (areDefaultVanillaBridgesSelected()) {
                defaultBridgesOperation(bridges);
            }
        });
    }

    private void sortBridgesByPing() {
        Collections.sort(bridgesToDisplay, new BridgePingComparator());
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
            if (inputLinesStr.contains(obfs4.toString())) {
                inputBridgesType = obfs4.toString();
                pattern = Pattern.compile("^obfs4 +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+ +cert=.+ +iat-mode=\\d");
            } else if (inputLinesStr.contains(obfs3.toString())) {
                inputBridgesType = obfs3.toString();
                pattern = Pattern.compile("^obfs3 +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+");
            } else if (inputLinesStr.contains(scramblesuit.toString())) {
                inputBridgesType = scramblesuit.toString();
                pattern = Pattern.compile("^scramblesuit +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+( +password=\\w+)?");
            } else if (inputLinesStr.contains(meek_lite.toString())) {
                inputBridgesType = meek_lite.toString();
                pattern = Pattern.compile("^meek_lite +(\\d{1,3}\\.){3}\\d{1,3}:\\d+ +\\w+ +url=https://[\\w./]+ +front=[\\w./]+");
            } else if (inputLinesStr.contains(snowflake.toString())) {
                inputBridgesType = snowflake.toString();
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

                boolean useOwnBridges = preferenceRepository.get().getBoolPreference(USE_OWN_BRIDGES);
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
                if (bridgesToAdd.contains(obfs4.toString())) {
                    if (!spOwnBridges.getSelectedItem().toString().equals(obfs4.toString())) {
                        spOwnBridges.setSelection(0);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridgesToAdd.contains(obfs3.toString())) {
                    if (!spOwnBridges.getSelectedItem().toString().equals(obfs3.toString())) {
                        spOwnBridges.setSelection(1);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridgesToAdd.contains(scramblesuit.toString())) {
                    if (!spOwnBridges.getSelectedItem().toString().equals(scramblesuit.toString())) {
                        spOwnBridges.setSelection(2);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridgesToAdd.contains(meek_lite.toString())) {
                    if (!spOwnBridges.getSelectedItem().toString().equals(meek_lite.toString())) {
                        spOwnBridges.setSelection(3);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else if (bridgesToAdd.contains(snowflake.toString())) {
                    if (!spOwnBridges.getSelectedItem().toString().equals(snowflake.toString())) {
                        spOwnBridges.setSelection(4);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                } else {
                    if (!spOwnBridges.getSelectedItem().toString().equals(vanilla.toString())) {
                        spOwnBridges.setSelection(5);
                    } else {
                        ownBridgesOperation(bridgesListNew);
                    }
                }

            }

            if (getActivity() == null || getActivity().isFinishing()) {
                return;
            }

            boolean useOwnBridges = preferenceRepository.get().getBoolPreference(USE_OWN_BRIDGES);

            if (!useOwnBridges) {
                bridgesInUse.clear();
                rbOwnBridges.performClick();
            }
        }
    }

    public void readSavedCustomBridges(String bridgesToAdd) {
        requestedBridgesToAdd = bridgesToAdd;

        currentBridgesFilePath = bridgesCustomFilePath;

        FileManager.readTextFile(getActivity(), currentBridgesFilePath, ADD_REQUESTED_BRIDGES_TAG);
    }

    private void noBridgesOperation() {
        doActionAndUpdateRecycler(() -> {
            checkNoBridgesRadioButton();

            viewModel.cancelRequestingRelayBridges();
            viewModel.cancelMeasuringTimeouts();
            swipeRefreshBridges.setRefreshing(false);

            bridgesToDisplay.clear();
            bridgesInUse.clear();

            tvBridgesListEmpty.setVisibility(View.GONE);
        });
    }

    private void defaultBridgesOperation(List<String> bridgesDefault) {
        doActionAndUpdateRecycler(() -> {
            checkDefaultBridgesRadioButton();

            cancelRequestingRelayBridgesIfRequired();

            bridgesToDisplay.clear();
            bridgesInappropriateType.clear();

            BridgeType obfsTypeSp = BridgeType.valueOf(spDefaultBridges.getSelectedItem().toString());

            if (bridgesDefault == null)
                return;

            separateBridges(bridgesDefault, obfsTypeSp);

            sortBridgesByPing();

            if (bridgesToDisplay.isEmpty()) {
                tvBridgesListEmpty.setVisibility(View.VISIBLE);
            } else {
                tvBridgesListEmpty.setVisibility(View.GONE);

                if (modulesStatus.getTorState() == STOPPED
                        || areDefaultVanillaBridgesSelected()) {
                    viewModel.measureTimeouts(bridgesToDisplay);
                }
            }
        });
    }

    private void ownBridgesOperation(List<String> bridgesCustom) {
        doActionAndUpdateRecycler(() -> {
            checkOwnBridgesRadioButton();

            cancelRequestingRelayBridgesIfRequired();

            bridgesToDisplay.clear();
            bridgesInappropriateType.clear();

            BridgeType obfsTypeSp = BridgeType.valueOf(spOwnBridges.getSelectedItem().toString());

            if (bridgesCustom == null)
                return;

            separateBridges(bridgesCustom, obfsTypeSp);

            sortBridgesByPing();

            if (bridgesToDisplay.isEmpty()) {
                tvBridgesListEmpty.setVisibility(View.VISIBLE);
            } else {
                tvBridgesListEmpty.setVisibility(View.GONE);

                if (modulesStatus.getTorState() == STOPPED) {
                    viewModel.measureTimeouts(bridgesToDisplay);
                }
            }
        });
    }

    private void cancelRequestingRelayBridgesIfRequired() {
        viewModel.cancelRequestingRelayBridges();
        swipeRefreshBridges.setRefreshing(false);
    }

    private void separateBridges(List<String> bridges, BridgeType obfsType) {
        for (String line : bridges) {
            ObfsBridge obfsBridge;
            if (!obfsType.equals(vanilla) && line.contains(obfsType.toString())) {
                obfsBridge = new ObfsBridge(line, obfsType, false);
                if (bridgesInUse.contains(line)) {
                    obfsBridge.active = true;
                }
                bridgesToDisplay.add(obfsBridge);
            } else if (obfsType.equals(vanilla) && isBridgeVanilla(line)) {
                obfsBridge = new ObfsBridge(line, obfsType, false);
                if (bridgesInUse.contains(line)) {
                    obfsBridge.active = true;
                }
                bridgesToDisplay.add(obfsBridge);
            } else {
                bridgesInappropriateType.add(line);
            }
        }
    }

    @Override
    public void OnFileOperationComplete(
            FileOperationsVariants currentFileOperation,
            boolean fileOperationResult,
            String path,
            String tag,
            List<String> lines
    ) {

        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || handlerLazy == null) {
            return;
        }

        Handler handler = handlerLazy.get();

        if (fileOperationResult && currentFileOperation == readTextFile) {
            switch (tag) {
                case TOR_CONF_FLAG:
                    if (lines == null || lines.isEmpty()) {
                        return;
                    }

                    tor_conf.clear();
                    bridgesInUse.clear();

                    for (String line : lines) {
                        if (!line.trim().isEmpty()) {
                            tor_conf.add(line);
                        }
                    }

                    for (int i = 0; i < tor_conf.size(); i++) {
                        String line = tor_conf.get(i);
                        if (!line.contains("#") && line.contains("Bridge ")) {

                            if (line.contains(snowflake.toString())) {
                                line = line.replaceAll("utls-imitate.+?( |\\z)", "");
                            }

                            bridgesInUse.add(line.replace("Bridge ", "").trim());
                        }
                    }

                    if (!bridgesInUse.isEmpty()) {
                        String testBridge = bridgesInUse.toString();
                        if (testBridge.contains(obfs4.toString())) {
                            currentBridgesType = obfs4;
                        } else if (testBridge.contains(obfs3.toString())) {
                            currentBridgesType = obfs3;
                        } else if (testBridge.contains(scramblesuit.toString())) {
                            currentBridgesType = scramblesuit;
                        } else if (testBridge.contains(meek_lite.toString())) {
                            currentBridgesType = meek_lite;
                        } else if (testBridge.contains(snowflake.toString())) {
                            currentBridgesType = snowflake;
                        } else {
                            currentBridgesType = vanilla;
                        }
                    } else {
                        currentBridgesType = undefined;
                    }
                    break;
                case ADD_BRIDGES_TAG: {
                    final List<String> bridges_lst = lines;
                    if (handler != null && bridges_lst != null) {
                        handler.post(() -> addBridges(bridges_lst));
                    }
                    break;
                }
                case DEFAULT_BRIDGES_OPERATION_TAG: {
                    final List<String> savedDefaultBridges = lines;

                    if (areDefaultVanillaBridgesSelected()) {
                        savedDefaultBridges.addAll(bridgesInUse);
                    }

                    if (handler != null && savedDefaultBridges != null) {
                        handler.post(() -> defaultBridgesOperation(savedDefaultBridges));
                    }
                    break;
                }
                case OWN_BRIDGES_OPERATION_TAG: {
                    final List<String> savedCustomBridges = lines;
                    if (handler != null && savedCustomBridges != null) {
                        handler.post(() -> ownBridgesOperation(savedCustomBridges));
                    }
                    break;
                }
                case ADD_REQUESTED_BRIDGES_TAG: {
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
    public Set<String> getBridgesInUse() {
        return bridgesInUse;
    }

    @Override
    public List<ObfsBridge> getBridgesToDisplay() {
        return bridgesToDisplay;
    }

    @Override
    public BridgeAdapter getBridgeAdapter() {
        return bridgeAdapter;
    }

    @Override
    public List<String> getBridgesInappropriateType() {
        return bridgesInappropriateType;
    }

    @Override
    public String getBridgesFilePath() {
        return currentBridgesFilePath;
    }

    private Future<?> verifyNewDefaultBridgesExist(Context context, boolean useDefaultBridges) {

        return cachedExecutor.submit(() -> {
            File outputFile = new File(appDataDir + "/app_data/tor/bridges_default.lst");
            long installedBridgesSize = outputFile.length();

            try (ZipInputStream zipInputStream = new ZipInputStream(context.getAssets().open("tor.mp3"))) {

                ZipEntry zipEntry = zipInputStream.getNextEntry();

                while (zipEntry != null) {

                    String fileName = zipEntry.getName();
                    if (fileName.contains("bridges_default.lst") && zipEntry.getSize() != installedBridgesSize) {
                        if (isAdded() && handlerLazy != null) {
                            handlerLazy.get().post(() -> {
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
                loge("PreferencesTorBridges verifyNewDefaultBridgesExist", e);
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

                saveUseBridgesPreferences(true, false, false);

                saveRelayBridgesWereRequested(false);

                noBridgesOperation();
            }
        } else if (id == R.id.rbDefaultBridges) {
            if (newValue) {

                saveUseBridgesPreferences(false, true, false);

                currentBridgesFilePath = bridgesDefaultFilePath;

                if (areDefaultVanillaBridgesSelected()) {
                    if (areRelayBridgesWereRequested() && areBridgesVanilla(bridgesInUse)) {
                        FileManager.readTextFile(context, currentBridgesFilePath, DEFAULT_BRIDGES_OPERATION_TAG);
                    } else {
                        saveRelayBridgesWereRequested(true);
                        requestRelayBridges(true);
                    }
                    checkDefaultBridgesRadioButton();
                } else {
                    FileManager.readTextFile(context, currentBridgesFilePath, DEFAULT_BRIDGES_OPERATION_TAG);
                }
            }
        } else if (id == R.id.rbOwnBridges) {
            if (newValue) {

                saveUseBridgesPreferences(false, false, true);

                currentBridgesFilePath = bridgesCustomFilePath;

                FileManager.readTextFile(context, currentBridgesFilePath, OWN_BRIDGES_OPERATION_TAG);
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

                if (areDefaultVanillaBridgesSelected()) {
                    if (areRelayBridgesWereRequested() && areBridgesVanilla(bridgesInUse)) {
                        FileManager.readTextFile(context, currentBridgesFilePath, DEFAULT_BRIDGES_OPERATION_TAG);
                    } else {
                        saveRelayBridgesWereRequested(true);
                        requestRelayBridges(true);
                    }
                } else {
                    FileManager.readTextFile(context, currentBridgesFilePath, DEFAULT_BRIDGES_OPERATION_TAG);
                }
            }
        } else if (id == R.id.spOwnBridges) {
            preferenceRepository.get().setStringPreference(OWN_BRIDGES_OBFS, String.valueOf(i));
            if (rbOwnBridges.isChecked()) {
                currentBridgesFilePath = bridgesCustomFilePath;
                FileManager.readTextFile(context, currentBridgesFilePath, OWN_BRIDGES_OPERATION_TAG);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        //Stub
    }

    private void checkNoBridgesRadioButton() {
        rbDefaultBridges.setChecked(false);
        rbOwnBridges.setChecked(false);
    }

    private void checkDefaultBridgesRadioButton() {
        rbNoBridges.setChecked(false);
        rbOwnBridges.setChecked(false);
    }

    private void checkOwnBridgesRadioButton() {
        rbNoBridges.setChecked(false);
        rbDefaultBridges.setChecked(false);
    }

    public boolean areDefaultVanillaBridgesSelected() {
        BridgeType obfsType = BridgeType.valueOf(spDefaultBridges.getSelectedItem().toString());
        return obfsType == vanilla && rbDefaultBridges.isChecked();
    }

    private void saveUseBridgesPreferences(
            boolean useNoBridges,
            boolean useDefaultBridges,
            boolean useOwnBridges
    ) {
        preferenceRepository.get().setBoolPreference(USE_NO_BRIDGES, useNoBridges);
        preferenceRepository.get().setBoolPreference(USE_DEFAULT_BRIDGES, useDefaultBridges);
        preferenceRepository.get().setBoolPreference(USE_OWN_BRIDGES, useOwnBridges);
    }

    @Override
    public void onRefresh() {
        if (areDefaultVanillaBridgesSelected()) {
            requestRelayBridges(false);
        } else {
            viewModel.measureTimeouts(bridgesToDisplay);
            swipeRefreshBridges.setRefreshing(false);
        }
    }

    private void requestRelayBridges(boolean displayLoading) {

        if (displayLoading) {
            swipeRefreshBridges.setRefreshing(true);
        }

        doActionAndUpdateRecycler(() -> {
            bridgesToDisplay.clear();
            viewModel.requestRelayBridges();
        });

    }

    public void saveRelayBridgesWereRequested(boolean requested) {
        preferenceRepository.get().setBoolPreference(RELAY_BRIDGES_REQUESTED, requested);
    }

    public boolean areRelayBridgesWereRequested() {
        return preferenceRepository.get().getBoolPreference(RELAY_BRIDGES_REQUESTED);
    }

    private boolean areBridgesVanilla(Set<String> bridges) {
        if (bridges.isEmpty()) {
            return false;
        }

        String bridgeLine = bridges.toArray(new String[0])[0];

        return isBridgeVanilla(bridgeLine);
    }

    private boolean isBridgeVanilla(String bridgeLine) {
        return !bridgeLine.contains(obfs4.toString())
                && !bridgeLine.contains(obfs3.toString())
                && !bridgeLine.contains(scramblesuit.toString())
                && !bridgeLine.contains(meek_lite.toString())
                && !bridgeLine.contains(snowflake.toString())
                && !bridgeLine.isEmpty();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void doActionAndUpdateRecycler(Runnable action) {
        handlerLazy.get().post(() -> {
            if (rvBridges != null
                    && !rvBridges.isComputingLayout()
                    && bridgeAdapter != null) {
                action.run();
                bridgeAdapter.notifyDataSetChanged();
            }
        });
    }

    private void observeErrors() {
        viewModel.getErrorsLiveData().observe(getViewLifecycleOwner(), error -> {
            swipeRefreshBridges.setRefreshing(false);
            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
        });
    }
}
