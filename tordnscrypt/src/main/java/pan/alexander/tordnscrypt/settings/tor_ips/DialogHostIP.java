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

import androidx.appcompat.app.AlertDialog;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import pan.alexander.tordnscrypt.R;

public abstract class DialogHostIP extends AlertDialog.Builder {

    private final UnlockTorIpsFrag unlockTorIpsFrag;
    private final ArrayList<HostIP> unlockHostIP;

    public DialogHostIP(Context context, @NonNull UnlockTorIpsFrag unlockTorIpsFrag, int themeResId) {
        super(context, themeResId);
        this.unlockTorIpsFrag = unlockTorIpsFrag;
        this.unlockHostIP = unlockTorIpsFrag.unlockHostIP;
    }

    boolean isTextIP(String text) {
        return text.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}");
    }

    void resolveHostOrIP(HostIP hostIP, int position) {
        boolean active = hostIP.active;

        if (hostIP.inputHost) {
            String host = hostIP.host;
            try {
                String ip = unlockTorIpsFrag.genIPByHost(host);
                unlockHostIP.set(position, new HostIP(host, ip, true, false, active));
            } catch (Exception ignored) {
                String ip = unlockTorIpsFrag.getString(R.string.pref_fast_unlock_host_wrong);
                unlockHostIP.set(position, new HostIP(host, ip, true, false, active));
            }
        } else if (hostIP.inputIP) {
            String ip = hostIP.IP;
            try {
                String host = unlockTorIpsFrag.getHostByIP(ip);
                unlockHostIP.set(position, new HostIP(host, ip, false, true, active));
            } catch (Exception ignored) {
                String host = " ";
                unlockHostIP.set(position, new HostIP(host, ip, false, true, active));
            }
        }

        updateViewData(unlockTorIpsFrag, position);
    }

    abstract void updateViewData(UnlockTorIpsFrag unlockTorIpsFrag, int position);
}
