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

package pan.alexander.tordnscrypt.settings.tor_apps;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import dagger.Lazy;
import kotlinx.coroutines.Job;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.di.SharedPreferencesModule;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager;
import pan.alexander.tordnscrypt.utils.executors.CoroutineExecutor;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.proxy.ProxyFragmentKt.CLEARNET_APPS_FOR_PROXY;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_BYPASS_VPN;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.APPS_DIRECT_UDP;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CLEARNET_APPS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.FIREWALL_SHOWS_ALL_APPS;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.UNLOCK_APPS;

import javax.inject.Inject;
import javax.inject.Named;


public class UnlockTorAppsFragment extends Fragment
        implements InstalledApplicationsManager.OnAppAddListener,
        ChipGroup.OnCheckedChangeListener, SearchView.OnQueryTextListener {

    private Chip chipTorAppsUser;
    private Chip chipTorAppsSystem;
    private Chip chipTorAppsAll;
    private Chip chipTorAppsSortUid;
    private ProgressBar pbTorApp;

    private AppBarLayout appBarTorApps;
    RecyclerView rvListTorApps;
    RecyclerView.Adapter<TorAppsAdapter.TorAppsViewHolder> mAdapter;

    final CopyOnWriteArrayList<TorAppData> appsUnlock = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<TorAppData> savedAppsUnlockWhenSearch = null;
    private Set<String> setUnlockApps;
    @Nullable
    private Set<String> setDirectUdpApps;
    @Nullable
    private Set<String> setVpnBypassApps;

    String unlockAppsStr;
    private Job task;
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private volatile boolean appsListComplete = false;
    private String searchText;
    @Inject
    @Named(SharedPreferencesModule.DEFAULT_PREFERENCES_NAME)
    public Lazy<SharedPreferences> defaultPreferences;
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public CoroutineExecutor executor;
    @Inject
    public Lazy<Handler> handler;
    @Inject
    public Lazy<Verifier> verifierLazy;


    public UnlockTorAppsFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        App.getInstance().getDaggerComponent().inject(this);
        super.onCreate(savedInstanceState);

        Context context = getActivity();

        if (context == null) {
            return;
        }

        setRetainInstance(true);

        ////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////Reverse logic when route all through Tor!///////////////////
        //////////////////////////////////////////////////////////////////////////////////

        boolean routeAllThroughTorDevice = defaultPreferences.get().getBoolean(ALL_THROUGH_TOR, true);
        boolean bypassAppsProxy = getArguments() != null && getArguments().getBoolean("proxy");

        if (bypassAppsProxy) {
            unlockAppsStr = CLEARNET_APPS_FOR_PROXY;
        } else if (!routeAllThroughTorDevice) {
            unlockAppsStr = UNLOCK_APPS;
        } else {
            unlockAppsStr = CLEARNET_APPS;
        }

        setUnlockApps = preferenceRepository.get().getStringSetPreference(unlockAppsStr);

        if (!bypassAppsProxy) {
            setDirectUdpApps = preferenceRepository.get().getStringSetPreference(APPS_DIRECT_UDP);
            setVpnBypassApps = preferenceRepository.get().getStringSetPreference(APPS_BYPASS_VPN);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view;
        try {
            view = inflater.inflate(R.layout.fragment_preferences_tor_apps, container, false);
        } catch (Exception e) {
            loge("UnlockTorAppsFragment onCreateView", e);
            throw e;
        }

        ChipGroup chipGroupTorApps = view.findViewById(R.id.chipGroupTorApps);
        chipGroupTorApps.setOnCheckedChangeListener(this);
        chipTorAppsUser = view.findViewById(R.id.chipTorAppsUser);
        chipTorAppsSystem = view.findViewById(R.id.chipTorAppsSystem);
        chipTorAppsAll = view.findViewById(R.id.chipTorAppsAll);

        ChipGroup chipGroupTorAppsSort = view.findViewById(R.id.chipGroupTorAppsSort);
        chipGroupTorAppsSort.setOnCheckedChangeListener(this);
        chipTorAppsSortUid = view.findViewById(R.id.chipTorAppsSortUid);

        pbTorApp = view.findViewById(R.id.pbTorApp);

        appBarTorApps = view.findViewById(R.id.appBarTorApps);

        rvListTorApps = view.findViewById(R.id.rvTorApps);

        searchText = null;

        if (chipTorAppsSystem.isChecked()) {
            chipSelectSystemApps();
        } else if (chipTorAppsUser.isChecked()) {
            chipSelectUserApps();
        } else if (chipTorAppsAll.isChecked()) {
            chipSelectAllApps();
        }

        return view;
    }


    @Override
    public void onResume() {
        super.onResume();

        Context context = getActivity();

        if (context == null) {
            return;
        }

        rvListTorApps.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(context) {

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
                if (!rvListTorApps.isInTouchMode()) {
                    onScrollWhenInNonTouchMode(dy);
                }
                return super.scrollVerticallyBy(dy, recycler, state);
            }

            private void onScrollWhenInNonTouchMode(int dy) {
                appBarTorApps.setExpanded(dy <= 0, true);
            }
        };
        rvListTorApps.setLayoutManager(mLayoutManager);

        mAdapter = new TorAppsAdapter(this);
        mAdapter.setHasStableIds(true);
        rvListTorApps.setAdapter(mAdapter);

        getDeviceApps(setUnlockApps);

        executor.submit("UnlockTorAppsFragment verifier", () -> {
            try {
                Verifier verifier = verifierLazy.get();
                String appSign = verifier.getAppSignature();
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(verifier.getWrongSign(), appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            context, getString(R.string.verifier_error), "11");
                    if (notificationHelper != null && isAdded()) {
                        handler.get().post(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        context, getString(R.string.verifier_error), "188");
                if (notificationHelper != null && isAdded()) {
                    handler.get().post(() -> notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER));
                }
                loge("UnlockTorAppsFragment fault", e, true);
            }
            return null;
        });
    }

    @Override
    public void onStop() {
        super.onStop();

        Context context = getActivity();

        if (context == null || !appsListComplete) {
            return;
        }

        if (savedAppsUnlockWhenSearch != null) {
            appsUnlock.clear();
            appsUnlock.addAll(savedAppsUnlockWhenSearch);
        }

        Set<String> setTorifyUidToSave = new HashSet<>();
        Set<String> setDirectUdpUidToSave = new HashSet<>();
        Set<String> setBypassVpnAppsToSave = new HashSet<>();
        for (int i = 0; i < appsUnlock.size(); i++) {
            TorAppData app = appsUnlock.get(i);
            if (app.getTorifyApp()) {
                setTorifyUidToSave.add(String.valueOf(app.getUid()));
            }
            if (setDirectUdpApps != null && app.getDirectUdp()) {
                setDirectUdpUidToSave.add(String.valueOf(app.getUid()));
            }
            if (setVpnBypassApps != null && app.getExcludeFromAll()) {
                setBypassVpnAppsToSave.add(app.getPack());
            }
        }

        boolean settingsChanged = false;

        if (!setTorifyUidToSave.equals(setUnlockApps)) {
            preferenceRepository.get().setStringSetPreference(unlockAppsStr, setTorifyUidToSave);
            setUnlockApps.clear();
            setUnlockApps.addAll(setTorifyUidToSave);
            settingsChanged = true;
        }

        if (setDirectUdpApps != null && !setDirectUdpUidToSave.equals(setDirectUdpApps)) {
            preferenceRepository.get().setStringSetPreference(APPS_DIRECT_UDP, setDirectUdpUidToSave);
            setDirectUdpApps.clear();
            setDirectUdpApps.addAll(setDirectUdpUidToSave);
            settingsChanged = true;
        }

        if (setVpnBypassApps != null && !setBypassVpnAppsToSave.equals(setVpnBypassApps)) {
            preferenceRepository.get().setStringSetPreference(APPS_BYPASS_VPN, setBypassVpnAppsToSave);
            setVpnBypassApps.clear();
            setVpnBypassApps.addAll(setBypassVpnAppsToSave);
            settingsChanged = true;
        }

        if (!settingsChanged) {
            return;
        }

        Toast.makeText(context, getString(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show();

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        if (modulesStatus.getMode() == ROOT_MODE || modulesStatus.getMode() == VPN_MODE) {
            modulesStatus.setIptablesRulesUpdateRequested(context, true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        chipTorAppsUser = null;
        chipTorAppsSystem = null;
        chipTorAppsAll = null;
        chipTorAppsSortUid = null;
        appBarTorApps = null;
        rvListTorApps = null;
        mAdapter = null;
        pbTorApp = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (task != null) {
            task.cancel(new CancellationException());
        }

        handler.get().removeCallbacksAndMessages(null);
    }

    @Override
    public void onCheckedChanged(@NonNull ChipGroup group, int checkedId) {
        if (rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        if (checkedId == R.id.chipTorAppsUser) {
            chipSelectUserApps();
        } else if (checkedId == R.id.chipTorAppsSystem) {
            chipSelectSystemApps();
        } else if (checkedId == R.id.chipTorAppsAll) {
            chipSelectAllApps();
        } else if (checkedId == R.id.chipTorAppsSortName) {
            sortByName();
        } else if (checkedId == R.id.chipTorAppsSortUid) {
            sortByUid();
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        if (mAdapter == null || rvListTorApps == null || rvListTorApps.isComputingLayout() || !appsListComplete) {
            return false;
        }

        searchApps(s);

        mAdapter.notifyDataSetChanged();

        return true;
    }

    @Override
    public boolean onQueryTextChange(String s) {

        if (mAdapter == null || rvListTorApps == null || rvListTorApps.isComputingLayout() || !appsListComplete) {
            return false;
        }

        searchApps(s);

        mAdapter.notifyDataSetChanged();

        return true;
    }

    private void searchApps(String text) {
        searchText = text;

        if (rvListTorApps == null || rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        boolean allAppsSelected = chipTorAppsAll.isChecked();
        boolean systemAppsSelected = chipTorAppsSystem.isChecked();
        boolean userAppsSelected = chipTorAppsUser.isChecked();

        if (text == null || text.isEmpty()) {
            if (savedAppsUnlockWhenSearch != null) {
                appsUnlock.clear();
                appsUnlock.addAll(savedAppsUnlockWhenSearch);
                savedAppsUnlockWhenSearch = null;
            }

            if (systemAppsSelected) {
                chipSelectSystemApps();
            } else if (userAppsSelected) {
                chipSelectUserApps();
            } else if (allAppsSelected) {
                chipSelectAllApps();
            }

            return;
        }

        if (savedAppsUnlockWhenSearch == null) {
            savedAppsUnlockWhenSearch = new CopyOnWriteArrayList<>(appsUnlock);
        }

        appsUnlock.clear();

        for (int i = 0; i < savedAppsUnlockWhenSearch.size(); i++) {
            TorAppData app = savedAppsUnlockWhenSearch.get(i);
            if (app.toString().toLowerCase().contains(text.toLowerCase().trim())
                    || app.getPack().toLowerCase().contains(text.toLowerCase().trim())) {
                if (allAppsSelected
                        || systemAppsSelected && app.getSystem()
                        || userAppsSelected && !app.getSystem()) {
                    appsUnlock.add(app);
                }
            }
        }
    }

    private void sortByName() {
        if (rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        TorAppData.sortByName(appsUnlock);
        TorAppData.sortByName(savedAppsUnlockWhenSearch);
    }

    private void sortByUid() {
        if (rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        TorAppData.sortByUid(appsUnlock);
        TorAppData.sortByUid(savedAppsUnlockWhenSearch);
    }

    private void chipSelectAllApps() {
        filterApps(null);
    }

    private void chipSelectSystemApps() {
        filterApps(true);
    }

    private void chipSelectUserApps() {
        filterApps(false);
    }

    private void filterApps(Boolean filterSystem) {
        if (rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        if (savedAppsUnlockWhenSearch == null) {
            savedAppsUnlockWhenSearch = new CopyOnWriteArrayList<>(appsUnlock);
        }

        appsUnlock.clear();

        for (TorAppData applicationData : savedAppsUnlockWhenSearch) {
            if (filterSystem == null
                    || filterSystem && applicationData.getSystem()
                    || !filterSystem && !applicationData.getSystem()) {
                if (searchText == null || searchText.isEmpty()) {
                    appsUnlock.add(applicationData);
                } else if (applicationData.toString().toLowerCase().contains(searchText.toLowerCase().trim())
                        || applicationData.getPack().toLowerCase().contains(searchText.toLowerCase().trim())) {
                    appsUnlock.add(applicationData);
                }
            }
        }
    }

    @Override
    public void onAppAdded(@NotNull ApplicationData application) {
        if (rvListTorApps == null || mAdapter == null
                || rvListTorApps.isComputingLayout() || appsListComplete) {
            return;
        }

        rvListTorApps.post(() -> {
            if (rvListTorApps == null || mAdapter == null
                    || rvListTorApps.isComputingLayout() || appsListComplete) {
                return;
            }

            TorAppData torAppData = TorAppData.mapToTorAppData(application);
            if (setDirectUdpApps != null) {
                torAppData.setDirectUdp(setDirectUdpApps.contains(String.valueOf(application.getUid())));
            }
            if (setVpnBypassApps != null) {
                torAppData.setExcludeFromAll(setVpnBypassApps.contains(application.getPack()));
            }
            appsUnlock.add(0, torAppData);
            mAdapter.notifyItemInserted(0);
            rvListTorApps.scrollToPosition(0);
        });
    }

    private void getDeviceApps(final Set<String> unlockAppsArrListSaved) {

        if (!appsUnlock.isEmpty()) {
            return;
        }

        task = executor.submit("UnlockTorAppsFragment getDeviceApps", () -> {
            try {

                reentrantLock.lockInterruptibly();

                if (appsUnlock.isEmpty()) {

                    if (handler != null && pbTorApp != null) {
                        handler.get().post(() -> {
                            if (pbTorApp != null) {
                                pbTorApp.setIndeterminate(true);
                                pbTorApp.setVisibility(View.VISIBLE);
                            }
                        });
                    }

                    appsListComplete = false;

                    List<ApplicationData> installedApps = new InstalledApplicationsManager.Builder()
                            .setOnAppAddListener(this)
                            .activeApps(unlockAppsArrListSaved)
                            .setIconRequired()
                            .build()
                            .getInstalledApps();

                    boolean showAllApps = defaultPreferences.get().getBoolean(FIREWALL_SHOWS_ALL_APPS, false);
                    if (!showAllApps) {
                        installedApps = filterUserAppsWithoutInternetPermission(installedApps);
                    }

                    while (rvListTorApps != null && rvListTorApps.isComputingLayout()) {
                        TimeUnit.MILLISECONDS.sleep(100);
                    }

                    appsListComplete = true;

                    appsUnlock.clear();
                    for (ApplicationData data : installedApps) {
                        TorAppData torAppData = TorAppData.mapToTorAppData(data);
                        if (setDirectUdpApps != null) {
                            torAppData.setDirectUdp(setDirectUdpApps.contains(String.valueOf(data.getUid())));
                        }
                        if (setVpnBypassApps != null) {
                            torAppData.setExcludeFromAll(setVpnBypassApps.contains(data.getPack()));
                        }
                        appsUnlock.add(torAppData);
                    }

                    if (handler != null && pbTorApp != null) {
                        handler.get().post(() -> {
                            if (pbTorApp != null && mAdapter != null) {
                                pbTorApp.setIndeterminate(false);
                                pbTorApp.setVisibility(View.GONE);

                                if (chipTorAppsSystem.isChecked()) {
                                    chipSelectSystemApps();
                                } else if (chipTorAppsUser.isChecked()) {
                                    chipSelectUserApps();
                                } else if (chipTorAppsAll.isChecked()) {
                                    chipSelectAllApps();
                                }

                                if (chipTorAppsSortUid.isChecked()) {
                                    sortByUid();
                                } else {
                                    sortByName();
                                }

                                mAdapter.notifyDataSetChanged();
                            }
                        });
                    }

                }

            } catch (Exception e) {
                loge("UnlockTorAppsFragment getDeviceApps", e, true);
            } finally {
                if (reentrantLock.isLocked()) {
                    reentrantLock.unlock();
                }
            }

            System.gc();

            return null;
        });

    }

    private List<ApplicationData> filterUserAppsWithoutInternetPermission(List<ApplicationData> installedApps) {
        List<ApplicationData> filteredApps = new ArrayList<>(installedApps.size());
        for (ApplicationData app : installedApps) {
            if (app.getSystem() || app.getHasInternetPermission()) {
                filteredApps.add(app);
            }
        }
        return filteredApps;
    }
}
