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

import static pan.alexander.tordnscrypt.utils.Constants.IP_REGEX;

import androidx.appcompat.app.AlertDialog;

import android.app.Activity;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import pan.alexander.tordnscrypt.R;

public abstract class DialogDomainIp extends AlertDialog.Builder {

    private final WeakReference<UnlockTorIpsFragment> unlockTorIpsFrag;
    private final List<DomainIpEntity> domainIps;

    public DialogDomainIp(@NonNull WeakReference<UnlockTorIpsFragment> unlockTorIpsFrag,
                          int themeResId) {
        super(unlockTorIpsFrag.get().requireContext(), themeResId);
        this.unlockTorIpsFrag = unlockTorIpsFrag;
        this.domainIps = unlockTorIpsFrag.get().domainIps;
    }

    boolean isTextIP(String text) {
        return text.matches(IP_REGEX);
    }

    void resolveHostOrIP(DomainIpEntity domainIp, int position) {
        boolean active = domainIp.isActive();

        if (domainIp instanceof DomainEntity) {
            String domain = ((DomainEntity) domainIp).getDomain();
            try {
                Set<String> ips = unlockTorIpsFrag.get().viewModel.resolveDomain(domain);
                if (ips.isEmpty()) {
                    throw new Exception();
                }
                domainIps.set(position, new DomainEntity(domain, ips, active));
            } catch (Exception ignored) {
                String ip = unlockTorIpsFrag.get().getString(R.string.pref_fast_unlock_host_wrong);
                domainIps.set(position,
                        new DomainEntity(
                                domain,
                                new HashSet<>(Collections.singletonList(ip)),
                                active
                        )
                );
            }
        } else if (domainIp instanceof IpEntity) {
            String ip = ((IpEntity) domainIp).getIp();
            try {
                String host = unlockTorIpsFrag.get().viewModel.reverseResolve(ip);
                if (host.equals(ip)) {
                    throw new Exception();
                }
                domainIps.set(position, new IpEntity(ip, host, active));
            } catch (Exception ignored) {
                String host = "";
                domainIps.set(position, new IpEntity(ip, host, active));
            }
        }

        updateViewData(unlockTorIpsFrag, position);
    }

    void updateViewData(WeakReference<UnlockTorIpsFragment> unlockTorIpsFrag, int position) {
        UnlockTorIpsFragment fragment = unlockTorIpsFrag.get();
        if (fragment == null) {
            return;
        }

        Activity activity = fragment.getActivity();
        if (activity != null && !activity.isFinishing() && !Thread.currentThread().isInterrupted()) {
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing()) {
                    fragment.rvAdapter.notifyDataSetChanged();
                    fragment.rvListHostIP.scrollToPosition(position);
                }
            });
        }
    }
}
