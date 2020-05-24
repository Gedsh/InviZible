package pan.alexander.tordnscrypt.settings.tor_ips;
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
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.dialogs.NotificationHelper;
import pan.alexander.tordnscrypt.modules.ModulesService;
import pan.alexander.tordnscrypt.settings.PathVars;
import pan.alexander.tordnscrypt.utils.PrefManager;
import pan.alexander.tordnscrypt.utils.Verifier;
import pan.alexander.tordnscrypt.utils.file_operations.FileOperations;
import pan.alexander.tordnscrypt.modules.ModulesStatus;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.TopFragment.appSign;
import static pan.alexander.tordnscrypt.TopFragment.wrongSign;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;

public class UnlockTorIpsFrag extends Fragment {

    private RecyclerView rvListHostip;
    private RecyclerView.Adapter rvAdapter;
    private ArrayList<HostIP> unlockHostIP;
    private FloatingActionButton floatingBtnAddTorIPs;
    private String appDataDir;
    private Boolean isChanged = false;
    private boolean routeAllThroughTorDevice = true;
    private boolean routeAllThroughTorTether = false;

    private String unlockHostsStr;
    private String unlockIPsStr;
    private String deviceOrTether = "";


    public UnlockTorIpsFrag() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_preferences_tor_ips, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getActivity() == null) {
            return;
        }

        PathVars pathVars = PathVars.getInstance(getActivity());
        appDataDir = pathVars.getAppDataDir();


        ////////////////////////////////////////////////////////////////////////////////////
        ///////////////////////Reverse logic when route all through Tor!///////////////////
        //////////////////////////////////////////////////////////////////////////////////
        SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        routeAllThroughTorDevice = shPref.getBoolean("pref_fast_all_through_tor", true);
        routeAllThroughTorTether = shPref.getBoolean("pref_common_tor_route_all", false);

        if (this.getArguments() != null) {
            deviceOrTether = this.getArguments().getString("deviceOrTether");
        }

        ArrayList<String> unlockHosts;
        ArrayList<String> unlockIPs;

        if (deviceOrTether == null)
            return;

        if (deviceOrTether.equals("device")) {
            if (!routeAllThroughTorDevice) {
                getActivity().setTitle(R.string.pref_tor_unlock);
                unlockHostsStr = "unlockHosts";
                unlockIPsStr = "unlockIPs";
            } else {
                getActivity().setTitle(R.string.pref_tor_clearnet);
                unlockHostsStr = "clearnetHosts";
                unlockIPsStr = "clearnetIPs";
            }
        } else if (deviceOrTether.equals("tether")) {
            if (!routeAllThroughTorTether) {
                getActivity().setTitle(R.string.pref_tor_unlock);
                unlockHostsStr = "unlockHostsTether";
                unlockIPsStr = "unlockIPsTether";
            } else {
                getActivity().setTitle(R.string.pref_tor_clearnet);
                unlockHostsStr = "clearnetHostsTether";
                unlockIPsStr = "clearnetIPsTether";
            }
        }


        Set<String> setUnlockHosts = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
        unlockHosts = new ArrayList<>(setUnlockHosts);
        Set<String> setUnlockIPs = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
        unlockIPs = new ArrayList<>(setUnlockIPs);


        unlockHostIP = new ArrayList<>();
        rvListHostip = getActivity().findViewById(R.id.rvTorIPs);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        rvListHostip.setLayoutManager(mLayoutManager);


        GetHostIP getHostIP = new GetHostIP(unlockHosts, unlockIPs);
        getHostIP.execute();

        if (ModulesService.executorService == null || ModulesService.executorService.isShutdown()) {
            ModulesService.executorService = Executors.newCachedThreadPool();
        }

        ModulesService.executorService.submit(() -> {
            try {
                Verifier verifier = new Verifier(getActivity());
                String appSignAlt = verifier.getApkSignature();
                if (!verifier.decryptStr(wrongSign, appSign, appSignAlt).equals(TOP_BROADCAST)) {
                    NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                            getActivity(), getText(R.string.verifier_error).toString(), "123");
                    if (notificationHelper != null && isAdded()) {
                        notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                    }
                }

            } catch (Exception e) {
                NotificationHelper notificationHelper = NotificationHelper.setHelperMessage(
                        getActivity(), getText(R.string.verifier_error).toString(), "168");
                if (notificationHelper != null && isAdded()) {
                    notificationHelper.show(getParentFragmentManager(), NotificationHelper.TAG_HELPER);
                }
                Log.e(LOG_TAG, "UnlockTorIpsFrag fault " + e.getMessage() + " " + e.getCause() + System.lineSeparator() +
                        Arrays.toString(e.getStackTrace()));
            }
        });

        if (getActivity() == null) {
            return;
        }

        floatingBtnAddTorIPs = getActivity().findViewById(R.id.floatingbtnAddTorIPs);
        floatingBtnAddTorIPs.setAlpha(0.8f);
        floatingBtnAddTorIPs.setOnClickListener(v -> addHostIPDialog());
        floatingBtnAddTorIPs.requestFocus();

    }

    @Override
    public void onStop() {
        super.onStop();

        if (getActivity() == null) {
            return;
        }

        if (unlockHostIP == null || !isChanged) return;

        List<String> ipsToUnlock = new LinkedList<>();
        for (int i = 0; i < unlockHostIP.size(); i++) {
            if (unlockHostIP.get(i).active) {
                String[] arr = unlockHostIP.get(i).IP.split(", ");
                for (String ip : arr) {
                    if (ip.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}"))
                        ipsToUnlock.add(ip);
                }
            }
        }

        if (!isChanged) return;

        //////////////////////////////////////////////////////////////////////////////////////
        //////////////When open this fragment to add sites for internal applications/////////
        /////////////////////////////////////////////////////////////////////////////////////
        if (deviceOrTether.equals("device")) {
            if (!routeAllThroughTorDevice) {
                FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/unlock", ipsToUnlock, "ignored");
            } else {
                FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/clearnet", ipsToUnlock, "ignored");
            }

            //////////////////////////////////////////////////////////////////////////////////////
            //////////////When open this fragment to add sites for external tether devices/////////
            /////////////////////////////////////////////////////////////////////////////////////
        } else if (deviceOrTether.equals("tether")) {
            if (!routeAllThroughTorTether) {
                FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/unlock_tether", ipsToUnlock, "ignored");
            } else {
                FileOperations.writeToTextFile(getActivity(), appDataDir + "/app_data/tor/clearnet_tether", ipsToUnlock, "ignored");
            }
        }

        ModulesStatus modulesStatus = ModulesStatus.getInstance();
        modulesStatus.setIptablesRulesUpdateRequested(getActivity(), true);
        //ModulesAux.requestModulesStatusUpdate(getActivity());

        Toast.makeText(getActivity(), getText(R.string.toastSettings_saved), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }



    public class HostIPAdapter extends RecyclerView.Adapter<UnlockTorIpsFrag.HostIPAdapter.HostIPViewHolder> {

        ArrayList<HostIP> unlockHostIP;
        LayoutInflater lInflater = (LayoutInflater) requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        HostIPAdapter(ArrayList<HostIP> unlockHostIP) {
            this.unlockHostIP = unlockHostIP;
        }

        @NonNull
        @Override
        public HostIPAdapter.HostIPViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = lInflater.inflate(R.layout.item_tor_ips, parent, false);
            return new HostIPViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull HostIPAdapter.HostIPViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return unlockHostIP.size();
        }

        HostIP getItem(int position) {
            return unlockHostIP.get(position);
        }

        void delItem(int position) {

            if (getActivity() == null) {
                return;
            }

            isChanged = true;

            if (getItem(position).inputIP) {
                Set<String> ipSet;
                ipSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);

                if (getItem(position).active) {
                    ipSet.remove(getItem(position).IP);
                } else {
                    ipSet.remove("#" + getItem(position).IP);
                }
                new PrefManager(getActivity()).setSetStrPref(unlockIPsStr, ipSet);

            } else if (getItem(position).inputHost) {
                Set<String> hostSet;
                hostSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);

                if (getItem(position).active) {
                    hostSet.remove(getItem(position).host);
                } else {
                    hostSet.remove("#" + getItem(position).host);
                }
                new PrefManager(getActivity()).setSetStrPref(unlockHostsStr, hostSet);

            }
            unlockHostIP.remove(position);
            notifyDataSetChanged();
        }

        void setActive(int position, boolean active) {
            HostIP hip = unlockHostIP.get(position);
            hip.active = active;
        }

        class HostIPViewHolder extends RecyclerView.ViewHolder {

            TextView tvTorItemHost;
            TextView tvTorItemIP;
            SwitchCompat swTorItem;
            ImageButton imbtnTorItem;
            LinearLayoutCompat llHostIP;
            LinearLayoutCompat llHostIPRoot;

            HostIPViewHolder(View itemView) {
                super(itemView);

                tvTorItemHost = itemView.findViewById(R.id.tvTorItemHost);
                tvTorItemIP = itemView.findViewById(R.id.tvTorItemIP);
                swTorItem = itemView.findViewById(R.id.swTorItem);
                swTorItem.setOnCheckedChangeListener(onCheckedChangeListener);
                swTorItem.setOnFocusChangeListener(onFocusChangeListener);
                imbtnTorItem = itemView.findViewById(R.id.imbtnTorItem);
                imbtnTorItem.setOnClickListener(onClickListener);
                imbtnTorItem.setOnFocusChangeListener(onFocusChangeListener);
                llHostIP = itemView.findViewById(R.id.llHostIP);
                llHostIP.setOnClickListener(onClickListener);
                llHostIP.setFocusable(true);
                llHostIP.setOnFocusChangeListener(onFocusChangeListener);
                llHostIPRoot = itemView.findViewById(R.id.llHostIPRoot);

            }

            void bind(int position) {
                if (!getItem(position).host.isEmpty()) {
                    tvTorItemHost.setText(getItem(position).host);
                    tvTorItemHost.setVisibility(View.VISIBLE);
                } else {
                    tvTorItemHost.setVisibility(View.GONE);
                }

                if (getItem(position).active) {
                    tvTorItemIP.setText(getItem(getAdapterPosition()).IP);
                } else {
                    tvTorItemIP.setText(getText(R.string.pref_tor_unlock_disabled));
                }
                swTorItem.setChecked(getItem(position).active);
                llHostIP.setEnabled(getItem(position).active);

                if (position == getItemCount() - 1) {
                    llHostIPRoot.setPadding(0, 0, 0, floatingBtnAddTorIPs.getHeight());
                } else {
                    llHostIPRoot.setPadding(0, 0, 0, 0);
                }
            }

            View.OnClickListener onClickListener = v -> {
                switch (v.getId()) {
                    case R.id.imbtnTorItem:
                        delItem(getAdapterPosition());
                        break;
                    case R.id.llHostIP:
                        editHostIPDialog(getAdapterPosition());
                        break;
                }

            };

            CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                    if (getActivity() == null) {
                        return;
                    }

                    setActive(getAdapterPosition(), isChecked);
                    llHostIP.setEnabled(isChecked);
                    isChanged = true;
                    if (isChecked) {
                        tvTorItemIP.setText(getItem(getAdapterPosition()).IP);

                        if (getItem(getAdapterPosition()).inputIP) {
                            Set<String> ipsSet;
                            ipsSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
                            String oldIP = getItem(getAdapterPosition()).IP;
                            ipsSet.remove("#" + oldIP);
                            ipsSet.add(oldIP.replace("#", ""));
                            new PrefManager(getActivity()).setSetStrPref(unlockIPsStr, ipsSet);

                        } else if (getItem(getAdapterPosition()).inputHost) {
                            Set<String> hostsSet;
                            hostsSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
                            String oldHost = getItem(getAdapterPosition()).host;
                            hostsSet.remove("#" + oldHost);
                            hostsSet.add(oldHost.replace("#", ""));
                            new PrefManager(getActivity()).setSetStrPref(unlockHostsStr, hostsSet);
                        }

                    } else {
                        tvTorItemIP.setText(getText(R.string.pref_tor_unlock_disabled));

                        if (getItem(getAdapterPosition()).inputIP) {
                            Set<String> ipsSet;
                            ipsSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
                            String oldIP = getItem(getAdapterPosition()).IP;
                            ipsSet.remove(oldIP);
                            ipsSet.add("#" + oldIP);
                            new PrefManager(getActivity()).setSetStrPref(unlockIPsStr, ipsSet);
                        } else if (getItem(getAdapterPosition()).inputHost) {
                            Set<String> hostsSet;
                            hostsSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
                            String oldHost = getItem(getAdapterPosition()).host;
                            hostsSet.remove(oldHost);
                            hostsSet.add("#" + oldHost);
                            new PrefManager(getActivity()).setSetStrPref(unlockHostsStr, hostsSet);
                        }
                    }
                }
            };

            View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean inFocus) {
                    if (view.getId() == R.id.llHostIP) {
                        if (inFocus) {
                            view.setBackgroundColor(getResources().getColor(R.color.colorSecond));
                            rvListHostip.smoothScrollToPosition(getAdapterPosition());
                        } else {
                            view.setBackgroundColor(getResources().getColor(R.color.colorFirst));
                        }
                    } else {
                        if (inFocus) {
                            rvListHostip.smoothScrollToPosition(getAdapterPosition());
                        }

                    }


                }
            };

            void editHostIPDialog(final int position) {

                if (getActivity() == null) {
                    return;
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);
                builder.setTitle(R.string.pref_tor_unlock_edit);

                LayoutInflater inflater = getActivity().getLayoutInflater();
                @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
                final EditText input = inputView.findViewById(R.id.etForDialog);

                String oldHost = "";
                String oldIP = "";

                if (unlockHostIP.get(position).inputHost) {
                    oldHost = unlockHostIP.get(position).host;
                    input.setText(oldHost, TextView.BufferType.EDITABLE);
                } else if (unlockHostIP.get(position).inputIP) {
                    oldIP = unlockHostIP.get(position).IP;
                    input.setText(oldIP, TextView.BufferType.EDITABLE);
                }
                builder.setView(inputView);

                final String finalOldIP = oldIP;
                final String finalOldHost = oldHost;
                builder.setPositiveButton("OK", (dialog, which) -> {
                    isChanged = true;
                    if (input.getText().toString().matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")) {
                        String host = getText(R.string.please_wait).toString();
                        unlockHostIP.set(position, new HostIP(host, input.getText().toString(), false, true, true));
                        Set<String> ipsSet;
                        ipsSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
                        ipsSet.remove(finalOldIP);
                        ipsSet.add(input.getText().toString());
                        new PrefManager(getActivity()).setSetStrPref(unlockIPsStr, ipsSet);
                    } else {
                        String IP = getText(R.string.please_wait).toString();
                        String host = input.getText().toString();
                        if (!host.startsWith("http")) host = "https://" + host;
                        unlockHostIP.set(position, new HostIP(host, IP, true, false, true));
                        Set<String> hostsSet;
                        hostsSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
                        hostsSet.remove(finalOldHost);
                        hostsSet.add(host);
                        new PrefManager(getActivity()).setSetStrPref(unlockHostsStr, hostsSet);
                    }

                    if (ModulesService.executorService == null || ModulesService.executorService.isShutdown()) {
                        ModulesService.executorService = Executors.newCachedThreadPool();
                    }

                    ModulesService.executorService.submit(() -> getHostOrIp(position, false, true));

                    rvAdapter.notifyItemChanged(position);
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();
            }

        }
    }

    private void addHostIPDialog() {

        if (getActivity() == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialogTheme);

        if (deviceOrTether.equals("device")) {
            if (!routeAllThroughTorDevice) {
                builder.setTitle(R.string.pref_tor_unlock);
            } else {
                builder.setTitle(R.string.pref_tor_clearnet);
            }
        } else if (deviceOrTether.equals("tether")) {
            if (!routeAllThroughTorTether) {
                builder.setTitle(R.string.pref_tor_unlock);
            } else {
                builder.setTitle(R.string.pref_tor_clearnet);
            }
        }


        LayoutInflater inflater = getActivity().getLayoutInflater();
        @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
        final EditText input = inputView.findViewById(R.id.etForDialog);

        builder.setView(inputView);

        builder.setCancelable(false);

        builder.setPositiveButton("OK", (dialog, which) -> {

            if (getActivity() == null) {
                return;
            }

            isChanged = true;
            if (input.getText().toString().matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")) {
                unlockHostIP.add(new HostIP(getText(R.string.please_wait).toString(), input.getText().toString(), false, true, true));
                Set<String> ipsSet;
                ipsSet = new PrefManager(getActivity()).getSetStrPref(unlockIPsStr);
                ipsSet.add(input.getText().toString());
                new PrefManager(getActivity()).setSetStrPref(unlockIPsStr, ipsSet);
            } else {
                String host = input.getText().toString();
                if (!host.startsWith("http")) host = "https://" + host;
                unlockHostIP.add(new HostIP(host, getText(R.string.please_wait).toString(), true, false, true));
                Set<String> hostsSet;
                hostsSet = new PrefManager(getActivity()).getSetStrPref(unlockHostsStr);
                hostsSet.add(host);
                new PrefManager(getActivity()).setSetStrPref(unlockHostsStr, hostsSet);
            }

            if (ModulesService.executorService == null || ModulesService.executorService.isShutdown()) {
                ModulesService.executorService = Executors.newCachedThreadPool();
            }

            ModulesService.executorService.submit(() -> getHostOrIp(unlockHostIP.size() - 1, true, false));
            rvAdapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @SuppressLint("StaticFieldLeak")
    class GetHostIP extends AsyncTask<Void, Integer, Void> {

        ArrayList<String> unlockHosts;
        ArrayList<String> unlockIPs;

        GetHostIP(ArrayList<String> unlockHosts, ArrayList<String> unlockIPs) {
            this.unlockHosts = unlockHosts;
            this.unlockIPs = unlockIPs;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (!unlockHosts.isEmpty()) {
                for (String host : unlockHosts) {
                    String hostClear = host.replace("#", "");
                    unlockHostIP.add(new HostIP(hostClear, getText(R.string.please_wait).toString(), true, false, !host.trim().startsWith("#")));
                }
            }
            if (!unlockIPs.isEmpty()) {
                for (String IPs : unlockIPs) {
                    String IPsClear = IPs.replace("#", "");
                    unlockHostIP.add(new HostIP(getText(R.string.please_wait).toString(), IPsClear, false, true, !IPs.trim().startsWith("#")));
                }
            }

            rvAdapter = new HostIPAdapter(unlockHostIP);
            rvListHostip.setAdapter(rvAdapter);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 0; i < unlockHostIP.size(); i++) {
                getHostOrIp(i, false, false);
                publishProgress(i);
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            if (rvAdapter == null) {
                return;
            }

            rvAdapter.notifyDataSetChanged();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }


    }

    private void getHostOrIp(final int position, final boolean addHostIP, final boolean editHostIP) {
        if (unlockHostIP == null || rvAdapter == null || rvListHostip == null) {
            return;
        }

        boolean active = unlockHostIP.get(position).active;
        if (unlockHostIP.get(position).inputHost && getActivity() != null) {
            String host = unlockHostIP.get(position).host;
            try {
                InetAddress[] addresses = InetAddress.getAllByName(new URL(host).getHost());
                StringBuilder sb = new StringBuilder();
                for (InetAddress address : addresses) {
                    sb.append(address.getHostAddress()).append(", ");
                }

                String ip = sb.substring(0, sb.length() - 2);
                if (unlockHostIP != null && position < unlockHostIP.size()) {
                    unlockHostIP.set(position, new HostIP(host, ip, true, false, active));
                }

                getActivity().runOnUiThread(() -> {
                    if (addHostIP) {
                        rvAdapter.notifyItemChanged(position);
                        rvListHostip.scrollToPosition(position);
                    } else if (editHostIP) {
                        rvAdapter.notifyItemChanged(position);
                    }

                });
            } catch (UnknownHostException | MalformedURLException e) {

                if (unlockHostIP == null || rvAdapter == null || rvListHostip == null) {
                    return;
                }

                String ip = getString(R.string.pref_fast_unlock_host_wrong);
                unlockHostIP.set(position, new HostIP(host, ip, true, false, active));
                Log.e(LOG_TAG, "UnlockTorIpsFrag getHostOrIp exception " + e.getMessage() + " " + e.getCause());

                if (getActivity() != null && rvAdapter != null && rvListHostip != null) {
                    getActivity().runOnUiThread(() -> {
                        if (addHostIP) {
                            rvAdapter.notifyItemChanged(position);
                            rvListHostip.scrollToPosition(position);
                        } else if (editHostIP) {
                            rvAdapter.notifyItemChanged(position);
                        }
                    });
                }
            }
        } else if (unlockHostIP.get(position).inputIP && getActivity() != null) {
            String IP = unlockHostIP.get(position).IP;
            String host;
            try {
                InetAddress addr = InetAddress.getByName(IP);
                host = addr.getCanonicalHostName();

                if (unlockHostIP != null && position < unlockHostIP.size()) {
                    unlockHostIP.set(position, new HostIP(host, IP, false, true, active));
                }

                if (getActivity() != null && rvAdapter != null && rvListHostip != null) {
                    getActivity().runOnUiThread(() -> {
                        if (addHostIP) {
                            rvAdapter.notifyDataSetChanged();
                            rvListHostip.scrollToPosition(position);
                        } else if (editHostIP) {
                            rvAdapter.notifyItemChanged(position);
                        }

                    });
                }

            } catch (IOException e) {

                if (unlockHostIP == null || rvAdapter == null || rvListHostip == null || getActivity() == null) {
                    return;
                }

                if (position < unlockHostIP.size()) {
                    host = " ";
                    unlockHostIP.set(position, new HostIP(host, IP, false, true, active));
                }

                Log.e(LOG_TAG, "UnlockTorIpsFrag getHostOrIp exception " + e.getMessage() + " " + e.getCause());

                getActivity().runOnUiThread(() -> {
                    if (addHostIP) {
                        rvAdapter.notifyDataSetChanged();
                        rvListHostip.scrollToPosition(position);
                    } else if (editHostIP) {
                        rvAdapter.notifyItemChanged(position);
                    }
                });
            }
        }
    }

}
