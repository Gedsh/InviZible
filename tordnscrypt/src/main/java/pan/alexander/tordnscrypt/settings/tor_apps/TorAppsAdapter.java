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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import pan.alexander.tordnscrypt.R;

class TorAppsAdapter extends RecyclerView.Adapter<TorAppsAdapter.TorAppsViewHolder> {
    UnlockTorAppsFragment fragment;
    LayoutInflater lInflater;

    TorAppsAdapter(UnlockTorAppsFragment fragment) {
        this.fragment = fragment;
        this.lInflater = fragment.getLayoutInflater();
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
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return fragment.appsUnlock.size();
    }

    ApplicationData getItem(int position) {
        return fragment.appsUnlock.get(position);
    }

    void setActive(int position, boolean active) {

        ApplicationData appUnlock = fragment.appsUnlock.get(position);
        appUnlock.setActive(active);
        fragment.appsUnlock.set(position, appUnlock);

        if (fragment.savedAppsUnlockWhenSearch != null) {
            for (int i = 0; i < fragment.savedAppsUnlockWhenSearch.size(); i++) {
                ApplicationData appUnlockSaved = fragment.savedAppsUnlockWhenSearch.get(i);
                if (appUnlockSaved.equals(appUnlock)) {
                    appUnlockSaved.setActive(active);
                    fragment.savedAppsUnlockWhenSearch.set(i, appUnlockSaved);
                }
            }
        }
    }

    protected class TorAppsViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnFocusChangeListener {
        private final Context context;
        private final ImageView imgTorApp;
        private final TextView tvTorAppName;
        private final TextView tvTorAppPackage;
        private final SwitchCompat swTorApp;

        private TorAppsViewHolder(View itemView) {
            super(itemView);

            context = fragment.getContext();

            imgTorApp = itemView.findViewById(R.id.imgTorApp);

            tvTorAppName = itemView.findViewById(R.id.tvTorAppName);
            tvTorAppPackage = itemView.findViewById(R.id.tvTorAppPackage);

            swTorApp = itemView.findViewById(R.id.swTorApp);
            swTorApp.setFocusable(false);
            swTorApp.setOnClickListener(this);

            if (context == null) {
                return;
            }

            CardView cardTorApps = itemView.findViewById(R.id.cardTorApp);
            cardTorApps.setCardBackgroundColor(context.getResources().getColor(R.color.colorFirst));
            cardTorApps.setOnClickListener(this);
            cardTorApps.setFocusable(true);
            cardTorApps.setOnFocusChangeListener(this);
        }

        private void bind(int position) {
            if (position < 0 || position > getItemCount() - 1 || context == null) {
                return;
            }

            ApplicationData app = getItem(position);

            tvTorAppName.setText(app.toString());
            if (app.getSystem()) {
                tvTorAppName.setTextColor(ContextCompat.getColor(context, R.color.colorAlert));
            } else {
                tvTorAppName.setTextColor(ContextCompat.getColor(context, R.color.textModuleStatusColorStopped));
            }
            imgTorApp.setImageDrawable(app.getIcon());
            String pack = String.format("[%s] %s", app.getUid(), app.getPack());
            tvTorAppPackage.setText(pack);
            swTorApp.setChecked(app.getActive());
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();

            if (position < 0) {
                return;
            }

            boolean appActive = getItem(position).getActive();
            setActive(position, !appActive);
            fragment.mAdapter.notifyDataSetChanged();
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
    }
}

