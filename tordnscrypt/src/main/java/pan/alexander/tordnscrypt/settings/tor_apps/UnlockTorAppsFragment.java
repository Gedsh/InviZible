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

    Copyright 2019-2023 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.tor_apps;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;
import pan.alexander.tordnscrypt.utils.apps.InstalledApplicationsManager;
import pan.alexander.tordnscrypt.utils.integrity.Verifier;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.proxy.ProxyFragmentKt.CLEARNET_APPS_FOR_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALL_THROUGH_TOR;
import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;

import javax.inject.Inject;


public class UnlockTorAppsFragment extends Fragment implements InstalledApplicationsManager.OnAppAddListener,
        CompoundButton.OnCheckedChangeListener, ChipGroup.OnCheckedChangeListener, SearchView.OnQueryTextListener {

    public static final String UNLOCK_APPS = "unlockApps";
    public static final String CLEARNET_APPS = "clearnetApps";

    private Chip chipTorAppsUser;
    private Chip chipTorAppsSystem;
    private Chip chipTorAppsAll;
    private Chip chipTorAppsSortUid;
    private ProgressBar pbTorApp;
    private RecyclerView rvListTorApps;
    RecyclerView.Adapter<TorAppsAdapter.TorAppsViewHolder> mAdapter;

    final CopyOnWriteArrayList<ApplicationData> appsUnlock = new CopyOnWriteArrayList<>();
    CopyOnWriteArrayList<ApplicationData> savedAppsUnlockWhenSearch = null;
    private Set<String> setUnlockApps;

    private String unlockAppsStr;
    private FutureTask<?> futureTask;
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private volatile boolean appsListComplete = false;
    private String searchText;
    @Inject
    public Lazy<PreferenceRepository> preferenceRepository;
    @Inject
    public CachedExecutor cachedExecutor;
    @Inject
    public Lazy<Handler> handler;


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

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean routeAllThroughTorDevice = shPref.getBoolean(ALL_THROUGH_TOR, true);
        boolean bypassAppsProxy = getArguments() != null && getArguments().getBoolean("proxy");

        if (bypassAppsProxy) {
            unlockAppsStr = CLEARNET_APPS_FOR_PROXY;
        } else if (!routeAllThroughTorDevice) {
            unlockAppsStr = UNLOCK_APPS;
        } else {
            unlockAppsStr = CLEARNET_APPS;
        }

        setUnlockApps = preferenceRepository.get().getStringSetPreference(unlockAppsStr);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_preferences_tor_apps, container, false);

        ChipGroup chipGroupTorApps = view.findViewById(R.id.chipGroupTorApps);
        chipGroupTorApps.setOnCheckedChangeListener(this);
        chipTorAppsUser = view.findViewById(R.id.chipTorAppsUser);
        chipTorAppsSystem = view.findViewById(R.id.chipTorAppsSystem);
        chipTorAppsAll = view.findViewById(R.id.chipTorAppsAll);

        ChipGroup chipGroupTorAppsSort = view.findViewById(R.id.chipGroupTorAppsSort);
        chipGroupTorAppsSort.setOnCheckedChangeListener(this);
        chipTorAppsSortUid = view.findViewById(R.id.chipTorAppsSortUid);

        pbTorApp = view.findViewById(R.id.pbTorApp);

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

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(context);
        rvListTorApps.setLayoutManager(mLayoutManager);

        mAdapter = new TorAppsAdapter(this);
        mAdapter.setHasStableIds(true);
        rvListTorApps.setAdapter(mAdapter);

        getDeviceApps(context, setUnlockApps);

        cachedExecutor.submit(() -> {
            try {
                Verifier verifier = new Verifier(context);
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
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
                Log.e(LOG_TAG, "UnlockTorAppsFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
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

        Set<String> setAppUIDtoSave = new HashSet<>();
        for (int i = 0; i < appsUnlock.size(); i++) {
            ApplicationData app = appsUnlock.get(i);
            if (app.getActive())
                setAppUIDtoSave.add(String.valueOf(app.getUid()));
        }

        if (setAppUIDtoSave.equals(setUnlockApps)) {
            return;
        }

        preferenceRepository.get().setStringSetPreference(unlockAppsStr, setAppUIDtoSave);

        setUnlockApps.clear();
        setUnlockApps.addAll(setAppUIDtoSave);

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
        rvListTorApps = null;
        mAdapter = null;
        pbTorApp = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (futureTask != null && futureTask.cancel(true)) {
            return;
        }

        handler.get().removeCallbacksAndMessages(null);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean active) {
        if (compoundButton.getId() == R.id.menu_switch && rvListTorApps != null
                && !rvListTorApps.isComputingLayout() && mAdapter != null && appsListComplete) {
            if (active) {
                for (int i = 0; i < appsUnlock.size(); i++) {
                    ApplicationData app = appsUnlock.get(i);
                    app.setActive(true);
                    appsUnlock.set(i, app);
                }
            } else {
                for (int i = 0; i < appsUnlock.size(); i++) {
                    ApplicationData app = appsUnlock.get(i);
                    app.setActive(false);
                    appsUnlock.set(i, app);
                }
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onCheckedChanged(ChipGroup group, int checkedId) {
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
            }

            return;
        }

        if (savedAppsUnlockWhenSearch == null) {
            savedAppsUnlockWhenSearch = new CopyOnWriteArrayList<>(appsUnlock);
        }

        appsUnlock.clear();

        for (int i = 0; i < savedAppsUnlockWhenSearch.size(); i++) {
            ApplicationData app = savedAppsUnlockWhenSearch.get(i);
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

        ApplicationData.Companion.sortListBy(appsUnlock, ApplicationData::compareTo);
        ApplicationData.Companion.sortListBy(savedAppsUnlockWhenSearch, ApplicationData::compareTo);
    }

    private void sortByUid() {
        if (rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        ApplicationData.Companion.sortListBy(appsUnlock, (o1, o2) -> {
            if (!o1.getActive() && o2.getActive()) {
                return 1;
            } else if (o1.getActive() && !o2.getActive()) {
                return -1;
            } else {
                return o1.getUid() - o2.getUid();
            }
        });

        ApplicationData.Companion.sortListBy(savedAppsUnlockWhenSearch, (o1, o2) -> {
            if (!o1.getActive() && o2.getActive()) {
                return 1;
            } else if (o1.getActive() && !o2.getActive()) {
                return -1;
            } else {
                return o1.getUid() - o2.getUid();
            }
        });
    }

    private void chipSelectAllApps() {
        if (rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        if (savedAppsUnlockWhenSearch != null) {
            if (searchText == null || searchText.trim().isEmpty()) {
                appsUnlock.clear();
                appsUnlock.addAll(savedAppsUnlockWhenSearch);
                savedAppsUnlockWhenSearch = null;
            } else {
                searchApps(searchText);
            }
        }
    }

    private void chipSelectSystemApps() {
        if (rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        if (savedAppsUnlockWhenSearch == null) {
            savedAppsUnlockWhenSearch = new CopyOnWriteArrayList<>(appsUnlock);
        }

        appsUnlock.clear();

        for (ApplicationData applicationData : savedAppsUnlockWhenSearch) {
            if (applicationData.getSystem()) {
                if (searchText == null || searchText.isEmpty()) {
                    appsUnlock.add(applicationData);
                } else if (applicationData.toString().toLowerCase().contains(searchText.toLowerCase().trim())
                        || applicationData.getPack().toLowerCase().contains(searchText.toLowerCase().trim())) {
                    appsUnlock.add(applicationData);
                }
            }
        }
    }

    private void chipSelectUserApps() {
        if (rvListTorApps.isComputingLayout() || !appsListComplete) {
            return;
        }

        if (savedAppsUnlockWhenSearch == null) {
            savedAppsUnlockWhenSearch = new CopyOnWriteArrayList<>(appsUnlock);
        }

        appsUnlock.clear();

        for (ApplicationData applicationData : savedAppsUnlockWhenSearch) {
            if (!applicationData.getSystem()) {
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
        if (handler == null || rvListTorApps == null
                || mAdapter == null || rvListTorApps.isComputingLayout() || appsListComplete) {
            return;
        }

        appsUnlock.add(0, application);

        handler.get().post(() -> {
            if (rvListTorApps == null || mAdapter == null
                    || rvListTorApps.isComputingLayout() || appsListComplete) {
                return;
            }

            mAdapter.notifyDataSetChanged();
        });
    }

    private void getDeviceApps(final Context context, final Set<String> unlockAppsArrListSaved) {

        if (!appsUnlock.isEmpty()) {
            return;
        }

        futureTask = new FutureTask<>(() -> {

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

                    while (rvListTorApps != null && rvListTorApps.isComputingLayout()) {
                        TimeUnit.MILLISECONDS.sleep(100);
                    }

                    appsListComplete = true;

                    appsUnlock.clear();
                    appsUnlock.addAll(installedApps);

                    if (handler != null && pbTorApp != null) {
                        handler.get().post(() -> {
                            if (pbTorApp != null && mAdapter != null) {
                                pbTorApp.setIndeterminate(false);
                                pbTorApp.setVisibility(View.GONE);

                                if (chipTorAppsSortUid.isChecked()) {
                                    sortByUid();
                                }

                                if (chipTorAppsSystem.isChecked()) {
                                    chipSelectSystemApps();
                                } else if (chipTorAppsUser.isChecked()) {
                                    chipSelectUserApps();
                                }

                                mAdapter.notifyDataSetChanged();
                            }
                        });
                    }

                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "UnlockTorAppsFragment getDeviceApps exception " + e.getMessage()
                        + "\n" + e.getCause() + "\n" + Arrays.toString(e.getStackTrace()));
            } finally {
                if (reentrantLock.isLocked()) {
                    reentrantLock.unlock();
                }
            }

            System.gc();

            return null;
        });

        cachedExecutor.submit(futureTask);

    }
}
