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

    Copyright 2019-2024 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.settings.tor_apps;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

import static pan.alexander.tordnscrypt.proxy.ProxyFragmentKt.CLEARNET_APPS_FOR_PROXY;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CLEARNET_APPS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.UNLOCK_APPS;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.utils.Utils;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

class TorAppsAdapter extends RecyclerView.Adapter<TorAppsAdapter.TorAppsViewHolder> {
    UnlockTorAppsFragment fragment;

    TorAppsAdapter(UnlockTorAppsFragment fragment) {
        this.fragment = fragment;
    }

    @NonNull
    @Override
    public TorAppsAdapter.TorAppsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_tor_app,
                parent,
                false
        );
        return new TorAppsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TorAppsViewHolder torAppsViewHolder, int position) {
        torAppsViewHolder.bind(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getUid();
    }

    @Override
    public int getItemCount() {
        return fragment.appsUnlock.size();
    }

    private TorAppData getItem(int position) {
        return fragment.appsUnlock.get(position);
    }

    class TorAppsViewHolder extends RecyclerView.ViewHolder
            implements View.OnClickListener, View.OnFocusChangeListener, CompoundButton.OnCheckedChangeListener {
        private final Context context;

        private final MaterialCardView cardTorApps ;
        private final ImageView imgTorApp;
        private final TextView tvTorAppName;
        private final TextView tvTorAppPackage;
        private final SwitchCompat swTorApp;
        private final Chip chipTorAppExclude;
        private final Chip chipTorAppDirectUdp;
        private final Chip chipTorAppExcludeFromAll;

        private TorAppsViewHolder(View itemView) {
            super(itemView);

            context = fragment.getContext();

            cardTorApps = itemView.findViewById(R.id.cardTorApp);

            imgTorApp = itemView.findViewById(R.id.imgTorApp);

            tvTorAppName = itemView.findViewById(R.id.tvTorAppName);
            tvTorAppPackage = itemView.findViewById(R.id.tvTorAppPackage);

            swTorApp = itemView.findViewById(R.id.swTorApp);
            chipTorAppExclude = itemView.findViewById(R.id.chipTorAppExclude);
            chipTorAppDirectUdp = itemView.findViewById(R.id.chipTorAppDirectUdp);
            chipTorAppExcludeFromAll = itemView.findViewById(R.id.chipTorAppExcludeFromAll);
            if (fragment.unlockAppsStr.equals(CLEARNET_APPS_FOR_PROXY)) {

                swTorApp.setVisibility(View.VISIBLE);
                swTorApp.setFocusable(false);
                swTorApp.setOnClickListener(this);

                chipTorAppExclude.setVisibility(View.GONE);
                chipTorAppDirectUdp.setVisibility(View.GONE);
                chipTorAppExcludeFromAll.setVisibility(View.GONE);


            } else {
                swTorApp.setVisibility(View.GONE);

                chipTorAppExclude.setOnCheckedChangeListener(this);
                chipTorAppDirectUdp.setOnCheckedChangeListener(this);
                chipTorAppExcludeFromAll.setOnCheckedChangeListener(this);
            }

            if (context != null && fragment.unlockAppsStr.equals(CLEARNET_APPS_FOR_PROXY)) {
                cardTorApps.setCardBackgroundColor(context.getResources().getColor(R.color.colorFirst));
                cardTorApps.setOnClickListener(this);
                cardTorApps.setFocusable(true);
                cardTorApps.setOnFocusChangeListener(this);
            }
        }

        private void bind(int position) {
            if (position < 0 || position > getItemCount() - 1 || context == null) {
                return;
            }

            TorAppData app = getItem(position);

            tvTorAppName.setText(TextUtils.join(", " ,app.getNames()));
            if (app.getSystem() && app.getHasInternetPermission()) {
                tvTorAppName.setTextColor(ContextCompat.getColor(context, R.color.colorAlert));
            } else if (app.getSystem()) {
                tvTorAppName.setTextColor(ContextCompat.getColor(context, R.color.systemAppWithoutInternetPermission));
            } else if (app.getSystem() || !app.getHasInternetPermission()) {
                tvTorAppName.setTextColor(ContextCompat.getColor(context, R.color.userAppWithoutInternetPermission));
            } else {
                tvTorAppName.setTextColor(ContextCompat.getColor(context, R.color.textModuleStatusColorStopped));
            }
            imgTorApp.setImageDrawable(app.getIcon());
            String pack = String.format("[%s] %s", app.getUid(), app.getPack());
            tvTorAppPackage.setText(pack);
            if (fragment.unlockAppsStr.equals(CLEARNET_APPS_FOR_PROXY)) {
                swTorApp.setChecked(app.getTorifyApp());
            } else {
                chipTorAppExclude.setChecked(app.getTorifyApp());
                chipTorAppDirectUdp.setChecked(app.getDirectUdp());
                chipTorAppExcludeFromAll.setChecked(app.getExcludeFromAll());

                if (fragment.unlockAppsStr.equals(CLEARNET_APPS)) {
                    chipTorAppExclude.setText(R.string.pref_fast_exclude_app_from_tor);
                } else {
                    chipTorAppExclude.setText(R.string.pref_fast_route_to_tor);
                }

                if (app.getExcludeFromAll()) {
                    TransitionManager.beginDelayedTransition((ViewGroup) chipTorAppExcludeFromAll.getParent());
                    chipTorAppExclude.setVisibility(View.GONE);
                    chipTorAppDirectUdp.setVisibility(View.GONE);
                } else if (app.getTorifyApp() && fragment.unlockAppsStr.equals(CLEARNET_APPS)
                        || !app.getTorifyApp() && fragment.unlockAppsStr.equals(UNLOCK_APPS)) {
                    TransitionManager.beginDelayedTransition((ViewGroup) chipTorAppDirectUdp.getParent());
                    chipTorAppExclude.setVisibility(View.VISIBLE);
                    chipTorAppDirectUdp.setVisibility(View.GONE);
                } else if (!app.getTorifyApp() && fragment.unlockAppsStr.equals(CLEARNET_APPS)
                        || app.getTorifyApp() && fragment.unlockAppsStr.equals(UNLOCK_APPS)) {
                    TransitionManager.beginDelayedTransition((ViewGroup) chipTorAppDirectUdp.getParent());
                    chipTorAppExclude.setVisibility(View.VISIBLE);
                    chipTorAppDirectUdp.setVisibility(View.VISIBLE);
                } else if (!app.getExcludeFromAll()) {
                    TransitionManager.beginDelayedTransition((ViewGroup) chipTorAppExcludeFromAll.getParent());
                    chipTorAppExclude.setVisibility(View.VISIBLE);
                    chipTorAppDirectUdp.setVisibility(View.VISIBLE);
                }

                boolean rootMode = ModulesStatus.getInstance().getMode() == OperationMode.ROOT_MODE;

                if (!rootMode && app.getExcludeFromAll()) {
                    cardTorApps.setStrokeWidth((int) Utils.dp2pixels(2));
                    cardTorApps.setStrokeColor(ContextCompat.getColor(context, R.color.colorChipIconBypassApp));
                } else {
                    cardTorApps.setStrokeWidth(0);
                    cardTorApps.setStrokeColor(ContextCompat.getColor(context, R.color.cardsColor));
                }

                if (rootMode) {
                    chipTorAppExcludeFromAll.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public void onClick(View v) {
            if (fragment.rvListTorApps.isComputingLayout()) {
                return;
            }
            int position = getBindingAdapterPosition();
            if (position == NO_POSITION) {
                return;
            }
            toggleTorifyApp(position);
        }

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (context == null) {
                return;
            }

            if (hasFocus) {
                ((CardView) view).setCardBackgroundColor(context.getResources().getColor(R.color.colorSecond));
            } else {
                ((CardView) view).setCardBackgroundColor(context.getResources().getColor(R.color.colorFirst));
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (fragment.rvListTorApps.isComputingLayout()) {
                return;
            }
            int position = getBindingAdapterPosition();
            if (position == NO_POSITION) {
                return;
            }
            int id = buttonView.getId();
            if (id == R.id.chipTorAppExclude) {
                toggleTorifyApp(position);
            } else if (id == R.id.chipTorAppDirectUdp) {
                toggleDirectUdp(position, isChecked);
            } else if (id == R.id.chipTorAppExcludeFromAll) {
                toggleExcludeFromAll(position, isChecked);
            }
        }
    }

    private void toggleTorifyApp(int position) {
        boolean torify = getItem(position).getTorifyApp();
        TorAppData appData = fragment.appsUnlock.get(position);
        appData.setTorifyApp(!torify);
        fragment.appsUnlock.set(position, appData);
        toggleSearchResults(appData);

        fragment.mAdapter.notifyItemChanged(position, new Object());
    }

    private void toggleDirectUdp(int position, boolean checked) {
        TorAppData appData = fragment.appsUnlock.get(position);
        appData.setDirectUdp(checked);
        fragment.appsUnlock.set(position, appData);
        toggleSearchResults(appData);

        fragment.mAdapter.notifyItemChanged(position, new Object());
    }

    private void toggleExcludeFromAll(int position, boolean checked) {
        TorAppData appData = fragment.appsUnlock.get(position);
        appData.setExcludeFromAll(checked);
        fragment.appsUnlock.set(position, appData);
        toggleSearchResults(appData);

        fragment.mAdapter.notifyItemChanged(position, new Object());
    }

    private void toggleSearchResults(TorAppData appData) {
        if (fragment.savedAppsUnlockWhenSearch != null) {
            for (int i = 0; i < fragment.savedAppsUnlockWhenSearch.size(); i++) {
                TorAppData appUnlockSaved = fragment.savedAppsUnlockWhenSearch.get(i);
                if (appUnlockSaved.equals(appData)) {
                    fragment.savedAppsUnlockWhenSearch.set(i, appData);
                }
            }
        }
    }
}

