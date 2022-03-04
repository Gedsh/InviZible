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

import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_DEFAULT_BRIDGES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_NO_BRIDGES;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.USE_OWN_BRIDGES;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import dagger.Lazy;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.settings.SettingsActivity;
import pan.alexander.tordnscrypt.dialogs.NotificationDialogFragment;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.utils.enums.BridgeType;
import pan.alexander.tordnscrypt.utils.enums.BridgesSelector;
import pan.alexander.tordnscrypt.utils.filemanager.FileManager;

public class BridgeAdapter extends RecyclerView.Adapter<BridgeAdapter.BridgeViewHolder> {

    private final Lazy<PreferenceRepository> preferenceRepository;
    private final SettingsActivity activity;
    private final FragmentManager fragmentManager;
    private final LayoutInflater lInflater;
    private final PreferencesBridges preferencesBridges;

    BridgeAdapter(SettingsActivity activity,
                  FragmentManager fragmentManager,
                  Lazy<PreferenceRepository> preferenceRepository,
                  PreferencesBridges preferencesBridges
    ) {
        this.activity = activity;
        this.fragmentManager = fragmentManager;
        this.preferencesBridges = preferencesBridges;
        this.lInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.preferenceRepository = preferenceRepository;
    }

    @NonNull
    @Override
    public BridgeAdapter.BridgeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = lInflater.inflate(R.layout.item_bridge, parent, false);
        return new BridgeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BridgeAdapter.BridgeViewHolder holder, int position) {
        holder.bind(position);
    }

    @Override
    public int getItemCount() {
        return preferencesBridges.getBridgeList().size();
    }

    private ObfsBridge getItem(int position) {
        return preferencesBridges.getBridgeList().get(position);
    }

    private void setActive(int position, boolean active) {
        List<ObfsBridge> bridgeList = preferencesBridges.getBridgeList();
        ObfsBridge brg = bridgeList.get(position);
        brg.active = active;
        bridgeList.set(position, brg);
    }

    class BridgeViewHolder extends RecyclerView.ViewHolder implements CompoundButton.OnCheckedChangeListener,
            View.OnClickListener {

        private final TextView tvBridge;
        private final TextView tvPing;
        private final SwitchCompat swBridge;

        BridgeViewHolder(@NonNull View itemView) {
            super(itemView);

            tvBridge = itemView.findViewById(R.id.tvBridge);
            tvPing = itemView.findViewById(R.id.tvBridgePing);
            swBridge = itemView.findViewById(R.id.swBridge);
            swBridge.setOnCheckedChangeListener(this);

            ImageButton ibtnBridgeDel = itemView.findViewById(R.id.ibtnBridgeDel);
            ibtnBridgeDel.setOnClickListener(this);
            CardView cardBridge = itemView.findViewById(R.id.cardBridge);
            cardBridge.setOnClickListener(this);
        }

        private void bind(int position) {
            List<ObfsBridge> bridgeList = preferencesBridges.getBridgeList();

            if (bridgeList == null || bridgeList.isEmpty()
                    || position < 0 || position >= bridgeList.size()) {
                return;
            }

            ObfsBridge obfsBridge = bridgeList.get(position);

            String[] bridgeIP = obfsBridge.bridge.split(" ");

            if (bridgeIP.length == 0) {
                return;
            }

            String tvBridgeText;
            if ((bridgeIP[0].contains("obfs3") || bridgeIP[0].contains("obfs4")
                    || bridgeIP[0].contains("scramblesuit") || bridgeIP[0].contains("meek_lite")
                    || bridgeIP[0].contains("snowflake"))
                    && bridgeIP.length > 1) {
                tvBridgeText = bridgeIP[0] + " " + bridgeIP[1];
            } else {
                tvBridgeText = bridgeIP[0];
            }

            if (bridgeIP[0].contains("meek_lite")
                    || bridgeIP[0].contains("snowflake")
                    || obfsBridge.ping == 0) {
                tvPing.setVisibility(View.GONE);
            } else {
                tvPing.setText(formatPing(obfsBridge.ping));
                tvPing.setVisibility(View.VISIBLE);
            }

            tvBridge.setText(tvBridgeText);
            swBridge.setChecked(obfsBridge.active);
        }

        private String formatPing(int ping) {
            if (ping < 0) {
                return "> 1 s";
            } else {
                return ping + " ms";
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }
            final Set<String> currentBridges = preferencesBridges.getCurrentBridges();


            if (isChecked) {

                boolean useNoBridges = preferenceRepository.get().getBoolPreference(USE_NO_BRIDGES);
                boolean useDefaultBridges = preferenceRepository.get().getBoolPreference(USE_DEFAULT_BRIDGES);
                boolean useOwnBridges = preferenceRepository.get().getBoolPreference(USE_OWN_BRIDGES);

                BridgesSelector currentBridgesSelector;
                if (!useNoBridges && !useDefaultBridges && !useOwnBridges) {
                    currentBridgesSelector = BridgesSelector.NO_BRIDGES;
                } else if (useNoBridges) {
                    currentBridgesSelector = BridgesSelector.NO_BRIDGES;
                } else if (useDefaultBridges) {
                    currentBridgesSelector = BridgesSelector.DEFAULT_BRIDGES;
                } else {
                    currentBridgesSelector = BridgesSelector.OWN_BRIDGES;
                }

                BridgeType obfsType = getItem(position).obfsType;
                if (!obfsType.equals(preferencesBridges.getCurrentBridgesType())) {
                    currentBridges.clear();
                    setCurrentBridgesType(obfsType);
                }

                if (!currentBridgesSelector.equals(preferencesBridges.getSavedBridgesSelector())) {
                    currentBridges.clear();
                    setSavedBridgesSelector(currentBridgesSelector);
                }

                currentBridges.add(getItem(position).bridge);
            } else {
                currentBridges.remove(getItem(position).bridge);
            }

            setActive(position, isChecked);
        }

        @Override
        public void onClick(View v) {

            int position = getBindingAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            int id = v.getId();
            if (id == R.id.cardBridge) {
                editBridge(position);
            } else if (id == R.id.ibtnBridgeDel) {
                deleteBridge(position);
            }
        }
    }

    private void editBridge(final int position) {

        if (activity == null || preferencesBridges == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.CustomAlertDialogTheme);
        builder.setTitle(R.string.pref_fast_use_tor_bridges_edit);

        List<ObfsBridge> bridgeList = preferencesBridges.getBridgeList();
        String bridges_file_path = preferencesBridges.getBridgesFilePath();

        if (bridgeList == null || position >= bridgeList.size()) {
            return;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        @SuppressLint("InflateParams") final View inputView = inflater.inflate(R.layout.edit_text_for_dialog, null, false);
        final EditText input = inputView.findViewById(R.id.etForDialog);
        input.setSingleLine(false);
        String brgEdit = bridgeList.get(position).bridge;
        BridgeType obfsTypeEdit = bridgeList.get(position).obfsType;
        final boolean activeEdit = bridgeList.get(position).active;
        if (activeEdit && fragmentManager != null) {
            DialogFragment commandResult
                    = NotificationDialogFragment.newInstance(activity.getString(R.string.pref_fast_use_tor_bridges_deactivate));
            commandResult.show(fragmentManager, "NotificationDialogFragment");
            return;
        }
        input.setText(brgEdit, TextView.BufferType.EDITABLE);
        builder.setView(inputView);

        builder.setPositiveButton(activity.getText(R.string.ok), (dialog, i) -> {
            if (preferencesBridges.getBridgeAdapter() == null || position >= bridgeList.size()) {
                return;
            }

            String inputText = input.getText().toString();

            ObfsBridge brg = new ObfsBridge(inputText, obfsTypeEdit, false);
            bridgeList.set(position, brg);
            preferencesBridges.getBridgeAdapter().notifyItemChanged(position);

            List<String> tmpList = new LinkedList<>();
            for (ObfsBridge tmpObfs : bridgeList) {
                tmpList.add(tmpObfs.bridge);
            }
            tmpList.addAll(preferencesBridges.getAnotherBridges());
            Collections.sort(tmpList);
            if (bridges_file_path != null)
                FileManager.writeToTextFile(activity, bridges_file_path, tmpList, "ignored");
        });
        builder.setNegativeButton(activity.getText(R.string.cancel), (dialog, i) -> dialog.cancel());
        builder.show();
    }

    private void deleteBridge(int position) {
        if (preferencesBridges == null || preferencesBridges.getBridgeAdapter() == null) {
            return;
        }

        List<ObfsBridge> bridgeList = preferencesBridges.getBridgeList();
        String bridges_file_path = preferencesBridges.getBridgesFilePath();

        if (bridgeList == null || position >= bridgeList.size()) {
            return;
        }

        if (bridgeList.get(position).active && fragmentManager != null) {
            DialogFragment commandResult
                    = NotificationDialogFragment.newInstance(activity.getString(R.string.pref_fast_use_tor_bridges_deactivate));
            commandResult.show(fragmentManager, "NotificationDialogFragment");
            return;
        }
        bridgeList.remove(position);
        preferencesBridges.getBridgeAdapter().notifyItemRemoved(position);

        List<String> tmpList = new ArrayList<>();
        for (ObfsBridge tmpObfs : bridgeList) {
            tmpList.add(tmpObfs.bridge);
        }
        tmpList.addAll(preferencesBridges.getAnotherBridges());
        Collections.sort(tmpList);
        if (bridges_file_path != null)
            FileManager.writeToTextFile(activity, bridges_file_path, tmpList, "ignored");
    }

    private void setCurrentBridgesType(BridgeType type) {
        preferencesBridges.setCurrentBridgesType(type);
    }

    private void setSavedBridgesSelector(BridgesSelector selector) {
        preferencesBridges.setSavedBridgesSelector(selector);
    }
}
