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

package pan.alexander.tordnscrypt.settings.tor_ips;

import static pan.alexander.tordnscrypt.utils.Constants.IPv4_REGEX;
import static pan.alexander.tordnscrypt.utils.Constants.IPv6_REGEX;

import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import pan.alexander.tordnscrypt.R;

public abstract class DialogDomainIp extends AlertDialog.Builder {

    private final WeakReference<UnlockTorIpsFragment> unlockTorIpsFrag;

    public DialogDomainIp(@NonNull WeakReference<UnlockTorIpsFragment> unlockTorIpsFrag) {
        super(unlockTorIpsFrag.get().requireContext());
        this.unlockTorIpsFrag = unlockTorIpsFrag;
    }

    boolean isTextIP(String text) {
        return text.matches(IPv4_REGEX) || text.matches(IPv6_REGEX);
    }

    void resolveHostOrIP(DomainIpEntity domainIp) {
        UnlockTorIpsFragment fragment = unlockTorIpsFrag.get();
        if (fragment == null) {
            return;
        }

        boolean includeIPv6 = fragment.isIncludeIPv6Addresses();

        boolean active = domainIp.isActive();

        if (domainIp instanceof DomainEntity) {
            String domain = ((DomainEntity) domainIp).getDomain();
            try {
                Set<String> ips = fragment.viewModel.resolveDomain(domain, includeIPv6);
                if (ips.isEmpty()) {
                    throw new Exception();
                }
                fragment.viewModel.addDomainIp(
                        new DomainEntity(
                                domain,
                                ips,
                                active
                        ), includeIPv6
                );
            } catch (Exception ignored) {
                String ip = unlockTorIpsFrag.get().getString(R.string.pref_fast_unlock_host_wrong);
                fragment.viewModel.addDomainIp(
                        new DomainEntity(
                                domain,
                                new HashSet<>(Collections.singletonList(ip)),
                                active
                        ), includeIPv6
                );
            }
        } else if (domainIp instanceof IpEntity) {
            String ip = ((IpEntity) domainIp).getIp();
            try {
                String host = fragment.viewModel.reverseResolve(ip);
                if (host.equals(ip)) {
                    throw new Exception();
                }
                fragment.viewModel.addDomainIp(
                        new IpEntity(ip, host, active), includeIPv6
                );
            } catch (Exception ignored) {
                String host = "";
                fragment.viewModel.addDomainIp(
                        new IpEntity(ip, host, active), includeIPv6
                );
            }
        }
    }
}
