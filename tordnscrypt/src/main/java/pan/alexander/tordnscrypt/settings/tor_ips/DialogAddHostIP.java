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

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Set;

import dagger.Lazy;
import pan.alexander.tordnscrypt.App;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.domain.preferences.PreferenceRepository;
import pan.alexander.tordnscrypt.utils.executors.CachedExecutor;

public class DialogAddHostIP extends DialogHostIP {
    private final Context context;
    private final UnlockTorIpsFrag unlockTorIpsFrag;
    private final ArrayList<HostIP> unlockHostIP;
    private final String unlockHostsStr;
    private final String unlockIPsStr;
    private final Lazy<PreferenceRepository> preferenceRepository;

    public DialogAddHostIP(Context context, @NonNull UnlockTorIpsFrag unlockTorIpsFrag, int themeResId) {
        super(context,unlockTorIpsFrag, themeResId);
        this.context = context;
        this.unlockTorIpsFrag = unlockTorIpsFrag;
        this.unlockHostIP = unlockTorIpsFrag.unlockHostIP;
        this.unlockHostsStr = unlockTorIpsFrag.unlockHostsStr;
        this.unlockIPsStr = unlockTorIpsFrag.unlockIPsStr;
        this.preferenceRepository = App.instance.daggerComponent.getPreferenceRepository();
    }

    @NonNull
    @Override
    public AlertDialog create() {

        setDialogTitle();

        LayoutInflater inflater = unlockTorIpsFrag.getLayoutInflater();
        final View inputView = inflater.inflate(R.layout.edit_text_for_dialog,
                (ViewGroup) unlockTorIpsFrag.getView(), false);

        final EditText input = inputView.findViewById(R.id.etForDialog);

        setView(inputView);

        setCancelable(false);

        setPositiveButton(R.string.ok, (dialog, which) -> {

            if (unlockTorIpsFrag.rvAdapter == null
                    || unlockHostsStr == null
                    || unlockIPsStr == null) {
                return;
            }

            String text = input.getText().toString();
            HostIP hostIP;
            if (isTextIP(text)) {
                hostIP = addIP(text);
            } else {
                hostIP = addHost(text);
            }

            CachedExecutor.INSTANCE.getExecutorService().submit(() ->
                    resolveHostOrIP(hostIP, unlockHostIP.size() - 1));

            unlockTorIpsFrag.rvAdapter.notifyDataSetChanged();
        });

        setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        return super.create();
    }

    private HostIP addHost(String host) {

        if (!host.startsWith("http")) {
            host = "https://" + host;
        }

        HostIP hostIP = new HostIP(host, context.getString(R.string.please_wait), true, false, true);
        unlockHostIP.add(hostIP);
        Set<String> hostsSet = preferenceRepository.get().getStringSetPreference(unlockHostsStr);
        hostsSet.add(host);
        preferenceRepository.get().setStringSetPreference(unlockHostsStr, hostsSet);

        return hostIP;
    }

    private HostIP addIP(String ip) {

        HostIP hostIP = new HostIP(context.getString(R.string.please_wait), ip, false, true, true);
        unlockHostIP.add(hostIP);
        Set<String> ipsSet = preferenceRepository.get().getStringSetPreference(unlockIPsStr);
        ipsSet.add(ip);
        preferenceRepository.get().setStringSetPreference(unlockIPsStr, ipsSet);

        return hostIP;
    }

    @Override
    void updateViewData(UnlockTorIpsFrag unlockTorIpsFrag, int position) {
        Activity activity = unlockTorIpsFrag.getActivity();
        if (activity != null && !activity.isFinishing() && !Thread.currentThread().isInterrupted()) {
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing()) {
                    unlockTorIpsFrag.rvAdapter.notifyItemChanged(position);
                    unlockTorIpsFrag.rvListHostIP.scrollToPosition(position);
                }
            });
        }
    }

    private void setDialogTitle() {
        if (unlockTorIpsFrag.deviceOrTether.equals("device")) {
            if (!unlockTorIpsFrag.routeAllThroughTorDevice) {
                setTitle(R.string.pref_tor_unlock);
            } else {
                setTitle(R.string.pref_tor_clearnet);
            }
        } else if (unlockTorIpsFrag.deviceOrTether.equals("tether")) {
            if (!unlockTorIpsFrag.routeAllThroughTorTether) {
                setTitle(R.string.pref_tor_unlock);
            } else {
                setTitle(R.string.pref_tor_clearnet);
            }
        }
    }
}
