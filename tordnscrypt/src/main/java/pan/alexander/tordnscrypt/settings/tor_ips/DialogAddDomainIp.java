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

package pan.alexander.tordnscrypt.settings.tor_ips;

import static pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFragment.DEVICE_VALUE;
import static pan.alexander.tordnscrypt.settings.tor_ips.UnlockTorIpsFragment.TETHER_VALUE;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.CancellationException;

import kotlinx.coroutines.Job;
import pan.alexander.tordnscrypt.R;

public class DialogAddDomainIp extends DialogDomainIp {
    private static Job resolvingJob;

    private final WeakReference<UnlockTorIpsFragment> unlockTorIpsFragment;

    public DialogAddDomainIp(@NonNull WeakReference<UnlockTorIpsFragment> unlockTorIpsFragment, int themeResId) {
        super(unlockTorIpsFragment, themeResId);
        this.unlockTorIpsFragment = unlockTorIpsFragment;
    }

    @NonNull
    @Override
    public AlertDialog create() {

        UnlockTorIpsFragment fragment = unlockTorIpsFragment.get();
        if (fragment == null) {
            return super.create();
        }

        setDialogTitle();

        LayoutInflater inflater = fragment.getLayoutInflater();
        final View inputView = inflater.inflate(R.layout.edit_text_for_dialog,
                (ViewGroup) fragment.getView(), false);

        final EditText input = inputView.findViewById(R.id.etForDialog);

        setView(inputView);

        setCancelable(false);

        setPositiveButton(R.string.ok, (dialog, which) -> {

            if (unlockTorIpsFragment.get() == null
                    || fragment.domainIpAdapter == null) {
                return;
            }

            String text = input.getText().toString();
            DomainIpEntity domainIp;
            if (isTextIP(text)) {
                domainIp = addIP(text);
            } else {
                domainIp = addHost(text);
            }

            if (resolvingJob != null) {
                resolvingJob.cancel(new CancellationException());
            }

            resolvingJob = fragment.coroutineExecutor.get().execute("DialogAddHostIP", () -> {
                resolveHostOrIP(domainIp);
                return null;
            });
        });

        setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());

        return super.create();
    }

    private DomainIpEntity addHost(String host) {

        if (!host.startsWith("http")) {
            host = "https://" + host;
        }

        DomainIpEntity domainIp = new DomainEntity(
                host,
                new HashSet<>(Collections.singletonList(unlockTorIpsFragment.get().getString(R.string.please_wait))),
                true
        );

        UnlockTorIpsFragment fragment = unlockTorIpsFragment.get();
        if (fragment != null) {
            fragment.viewModel.addDomainIp(domainIp);
            fragment.viewModel.addDomainToPreferences(host);
        }

        return domainIp;
    }

    private DomainIpEntity addIP(String ip) {

        DomainIpEntity domainIp = new IpEntity(ip, unlockTorIpsFragment.get().getString(R.string.please_wait), true);

        UnlockTorIpsFragment fragment = unlockTorIpsFragment.get();
        if (fragment != null) {
            fragment.viewModel.addDomainIp(domainIp);
            fragment.viewModel.addIpToPreferences(ip);
        }

        return domainIp;
    }

    private void setDialogTitle() {
        UnlockTorIpsFragment fragment = unlockTorIpsFragment.get();
        if (fragment == null) {
            return;
        }

        if (fragment.viewModel.getDeviceOrTether().equals(DEVICE_VALUE)) {
            if (!fragment.viewModel.getRouteAllThroughTorDevice()) {
                setTitle(R.string.pref_tor_unlock);
            } else {
                setTitle(R.string.pref_tor_clearnet);
            }
        } else if (fragment.viewModel.getDeviceOrTether().equals(TETHER_VALUE)) {
            if (!fragment.viewModel.getRouteAllThroughTorTether()) {
                setTitle(R.string.pref_tor_unlock);
            } else {
                setTitle(R.string.pref_tor_clearnet);
            }
        }
    }
}
