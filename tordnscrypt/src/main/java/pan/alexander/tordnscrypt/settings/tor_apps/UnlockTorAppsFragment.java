package pan.alexander.tordnscrypt.settings.tor_apps;
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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.CachedExecutor;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.TorRefreshIPsWork;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.ROOT_MODE;
import static pan.alexander.tordnscrypt.utils.enums.OperationMode.VPN_MODE;


public class UnlockTorAppsFragment extends Fragment implements CompoundButton.OnCheckedChangeListener, SearchView.OnQueryTextListener {
    private boolean isChanged;
    private RecyclerView.Adapter mAdapter;
    private ProgressBar pbTorApp;
    private String unlockAppsStr;
    private ArrayList<AppUnlock> appsUnlock;
    private ArrayList<AppUnlock> savedAppsUnlockWhenSearch = null;
    private FutureTask<?> futureTask;


    public UnlockTorAppsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_preferences_tor_apps, container, false);

        ((SwitchCompat) view.findViewById(R.id.swTorAppSellectorAll)).setOnCheckedChangeListener(this);
        ((SearchView) view.findViewById(R.id.searhTorApp)).setOnQueryTextListener(this);
        pbTorApp = view.findViewById(R.id.pbTorApp);

        return view;
    }


    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() == null) {
            return;
        }


        ////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////Reverse logic when route all through Tor!///////////////////
        //////////////////////////////////////////////////////////////////////////////////


        isChanged = false;
        appsUnlock = new ArrayList<>();

        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean routeAllThroughTorDevice = shPref.getBoolean("pref_fast_all_through_tor", true);

        if (!routeAllThroughTorDevice) {
            requireActivity().setTitle(R.string.pref_tor_unlock_app);

            /*if (ModulesStatus.getInstance().getMode() == ROOT_MODE) {
                requireActivity().setTitle(R.string.pref_tor_unlock_app);
            } else {
                requireActivity().setTitle(R.string.pref_routing_unlock_app);
            }*/

            unlockAppsStr = "unlockApps";
        } else {
            requireActivity().setTitle(R.string.pref_tor_clearnet_app);

            /*if (ModulesStatus.getInstance().getMode() == ROOT_MODE) {
                requireActivity().setTitle(R.string.pref_tor_clearnet_app);
            } else {
                requireActivity().setTitle(R.string.pref_routing_clearnet_app);
            }*/
            unlockAppsStr = "clearnetApps";
        }

        Set<String> setUnlockApps = new PrefManager(requireActivity()).getSetStrPref(unlockAppsStr);
        ArrayList<String> unlockAppsArrListSaved = new ArrayList<>(setUnlockApps);

        RecyclerView rvListTorApps = getActivity().findViewById(R.id.rvTorApps);
        rvListTorApps.requestFocus();
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        rvListTorApps.setLayoutManager(mLayoutManager);

        mAdapter = new TorAppsAdapter();
        rvListTorApps.setAdapter(mAdapter);

        getDeviceApps(getActivity(), mAdapter, unlockAppsArrListSaved);

        CachedExecutor.INSTANCE.getExecutorService().submit(() -> {
            try {
                Verifier verifier = new Verifier(getActivity());
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.verifier_error).toString(), "11");
                    if (notificationHelper != null && isAdded()) {
                        notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.verifier_error).toString(), "188");
                if (notificationHelper != null && isAdded()) {
                    notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                }
                Log.e(LOG_TAG, "UnlockTorAppsFragment fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() == null) {
            return;
        }

        if (futureTask != null && futureTask.cancel(true)) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(getActivity());
        String appDataDir = pathVars.getAppDataDir();

        if (!isChanged)
            return;

        if (savedAppsUnlockWhenSearch != null) {
            appsUnlock = savedAppsUnlockWhenSearch;
        }

        Set<String> setAppUIDtoSave = new HashSet<>();
        for (int i = 0; i < appsUnlock.size(); i++) {
            AppUnlock app = appsUnlock.get(i);
            if (app.active)
                setAppUIDtoSave.add(app.uid);
        }
        new PrefManager(getActivity()).setSetStrPref(unlockAppsStr, setAppUIDtoSave);

        List<String> listAppUIDtoSave = new LinkedList<>(setAppUIDtoSave);
        FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/" + unlockAppsStr, listAppUIDtoSave, "ignored");
        Toast.makeText(getActivity(), getString(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show();

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        if (modulesStatus.getMode() == ROOT_MODE) {
            /////////////Refresh iptables rules/////////////////////////
            TorRefreshIPsWork torRefreshIPsWork = new TorRefreshIPsWork(getActivity(), null);
            torRefreshIPsWork.refreshIPs();
        } else if (modulesStatus.getMode() == VPN_MODE) {
            modulesStatus.setIptablesRulesUpdateRequested(getActivity(), true);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean active) {
        if (compoundButton.getId() == R.id.swTorAppSellectorAll && mAdapter != null && appsUnlock != null) {
            if (active) {
                for (int i = 0; i < appsUnlock.size(); i++) {
                    AppUnlock app = appsUnlock.get(i);
                    app.active = true;
                    appsUnlock.set(i, app);
                }
            } else {
                for (int i = 0; i < appsUnlock.size(); i++) {
                    AppUnlock app = appsUnlock.get(i);
                    app.active = false;
                    appsUnlock.set(i, app);
                }
            }
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String s) {

        if (s == null || s.isEmpty()) {
            if (savedAppsUnlockWhenSearch != null) {
                appsUnlock = savedAppsUnlockWhenSearch;
                savedAppsUnlockWhenSearch = null;
                mAdapter.notifyDataSetChanged();
            }
            return true;
        }

        if (savedAppsUnlockWhenSearch == null) {
            savedAppsUnlockWhenSearch = new ArrayList<>(appsUnlock);
        }

        appsUnlock.clear();

        for (int i = 0; i < savedAppsUnlockWhenSearch.size(); i++) {
            AppUnlock app = savedAppsUnlockWhenSearch.get(i);
            if (app.name.toLowerCase().contains(s.toLowerCase().trim())
                    || app.pack.toLowerCase().contains(s.toLowerCase().trim())) {
                appsUnlock.add(app);
            }
        }
        mAdapter.notifyDataSetChanged();

        return true;
    }

    private class TorAppsAdapter extends RecyclerView.Adapter<TorAppsAdapter.TorAppsViewHolder> {
        LayoutInflater lInflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        TorAppsAdapter() {
        }

        @NonNull
        @Override
        public TorAppsAdapter.TorAppsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
            View view = lInflater.inflate(R.layout.item_tor_app, parent, false);
            return new TorAppsViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TorAppsViewHolder torAppsViewHolder, int position) {
            torAppsViewHolder.bind(position);
        }

        @Override
        public int getItemCount() {
            return appsUnlock.size();
        }

        AppUnlock getItem(int position) {
            return appsUnlock.get(position);
        }

        void setActive(int position, boolean active) {
            AppUnlock appUnlock = appsUnlock.get(position);
            appUnlock.active = active;
            appsUnlock.set(position, appUnlock);

            if (savedAppsUnlockWhenSearch != null) {
                for (int i = 0; i < savedAppsUnlockWhenSearch.size(); i++) {
                    AppUnlock appUnlockSaved = savedAppsUnlockWhenSearch.get(i);
                    if (appUnlockSaved.pack.equals(appUnlock.pack)) {
                        savedAppsUnlockWhenSearch.set(i, appUnlock);
                    }
                }
            }
        }

        private class TorAppsViewHolder extends RecyclerView.ViewHolder {
            private ImageView imgTorApp;
            private TextView tvTorAppName;
            private TextView tvTorAppPackage;
            private SwitchCompat swTorApp;
            private CardView cardTorApps;
            private LinearLayoutCompat lLayoutTorApps;
            private CardView cardTorAppFragment;

            private TorAppsViewHolder(View itemView) {
                super(itemView);

                imgTorApp = itemView.findViewById(R.id.imgTorApp);
                tvTorAppName = itemView.findViewById(R.id.tvTorAppName);
                tvTorAppPackage = itemView.findViewById(R.id.tvTorAppPackage);
                swTorApp = itemView.findViewById(R.id.swTorApp);
                cardTorApps = itemView.findViewById(R.id.cardTorApp);
                cardTorApps.setFocusable(true);
                lLayoutTorApps = itemView.findViewById(R.id.llayoutTorApps);

                if (getActivity() != null) {
                    cardTorAppFragment = getActivity().findViewById(R.id.cardTorAppFragment);
                }

                CompoundButton.OnCheckedChangeListener onCheckedChangeListener = (compoundButton, newValue) -> {
                    setActive(getAdapterPosition(), newValue);
                    isChanged = true;
                };
                swTorApp.setOnCheckedChangeListener(onCheckedChangeListener);
                swTorApp.setFocusable(false);


                View.OnClickListener onClickListener = view -> {
                    int appPosition = getAdapterPosition();
                    boolean appActive = getItem(appPosition).active;
                    setActive(appPosition, !appActive);
                    mAdapter.notifyItemChanged(appPosition);
                    isChanged = true;
                };

                cardTorApps.setOnClickListener(onClickListener);
                View.OnFocusChangeListener onFocusChangeListener = (view, inFocus) -> {
                    if (inFocus) {
                        ((CardView) view).setCardBackgroundColor(getResources().getColor(R.color.colorSecond));
                    } else {
                        ((CardView) view).setCardBackgroundColor(getResources().getColor(R.color.colorFirst));
                    }
                };
                cardTorApps.setOnFocusChangeListener(onFocusChangeListener);
                cardTorApps.setCardBackgroundColor(getResources().getColor(R.color.colorFirst));
            }

            private void bind(int position) {
                AppUnlock app = getItem(position);

                if (position == 0 && cardTorAppFragment != null) {
                    lLayoutTorApps.setPaddingRelative(0, cardTorAppFragment.getHeight() + 10, 0, 0);
                } else {
                    lLayoutTorApps.setPaddingRelative(0, 0, 0, 0);
                }

                tvTorAppName.setText(app.name);
                imgTorApp.setImageDrawable(app.icon);
                tvTorAppPackage.setText(app.pack);
                swTorApp.setChecked(app.active);
                if (app.pack.contains(getString(R.string.package_name))) {
                    swTorApp.setEnabled(false);
                    cardTorApps.setEnabled(false);
                } else {
                    swTorApp.setEnabled(true);
                    cardTorApps.setEnabled(true);
                }
            }

        }
    }


    private void getDeviceApps(final Context context, final RecyclerView.Adapter adapter, final ArrayList<String> unlockAppsArrListSaved) {

        pbTorApp.setIndeterminate(true);
        pbTorApp.setVisibility(View.VISIBLE);

        final PackageManager pMgr = context.getPackageManager();

        List<ApplicationInfo> lAppInfo = pMgr.getInstalledApplications(0);

        final Iterator<ApplicationInfo> itAppInfo = lAppInfo.iterator();

        futureTask = new FutureTask<>(() -> {
            while (itAppInfo.hasNext()) {
                ApplicationInfo aInfo = itAppInfo.next();
                boolean appUseInternet = false;
                boolean system = false;
                boolean active = false;

                try {
                    PackageInfo pInfo = pMgr.getPackageInfo(aInfo.packageName, PackageManager.GET_PERMISSIONS);

                    if (pInfo != null && pInfo.requestedPermissions != null) {
                        for (String permInfo : pInfo.requestedPermissions) {
                            if (permInfo.equals("android.permission.INTERNET")) {
                                appUseInternet = true;
                                break;
                            }
                        }

                    }

                } catch (Exception e) {
                    Log.e(LOG_TAG, "UnlockTorAppsFragment getDeviceApps exception " + e.getMessage() + " " + e.getCause());
                }

                if ((aInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1) {
                    //System app
                    appUseInternet = true;
                    system = true;
                }


                if (appUseInternet) {
                    String name;
                    try {
                        name = pMgr.getApplicationLabel(aInfo).toString();
                    } catch (Exception e) {
                        name = aInfo.packageName;
                    }
                    String pack = aInfo.packageName;
                    String uid = String.valueOf(aInfo.uid);
                    Drawable icon = pMgr.getApplicationIcon(aInfo);


                    if (unlockAppsArrListSaved.contains(uid)) {
                        active = true;
                    }

                    final AppUnlock app = new AppUnlock(name, pack, uid, icon, system, active);

                    if (getActivity() == null)
                        return null;

                    getActivity().runOnUiThread(() -> {
                        appsUnlock.add(app);
                        Collections.sort(appsUnlock, (appUnlock, t1) -> appUnlock.name.toLowerCase().compareTo(t1.name.toLowerCase()));
                        adapter.notifyDataSetChanged();
                    });

                }
            }

            if (getActivity() == null) {
                return null;
            }

            getActivity().runOnUiThread(() -> {
                pbTorApp.setIndeterminate(false);
                pbTorApp.setVisibility(View.GONE);
            });

            System.gc();

            return null;
        });

        CachedExecutor.INSTANCE.getExecutorService().submit(futureTask);

    }

}
