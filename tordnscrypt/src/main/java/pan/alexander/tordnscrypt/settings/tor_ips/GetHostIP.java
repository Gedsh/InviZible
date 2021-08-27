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
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

import pan.alexander.tordnscrypt.R;

import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

class GetHostIP implements Runnable {

    private final WeakReference<UnlockTorIpsFrag> unlockTorIpsFragWeakReference;
    private final ArrayList<HostIP> unlockHostIP;

    private static final long TIME_DIFFERENCE = 1000;
    private long savedTime = System.currentTimeMillis();

    GetHostIP(UnlockTorIpsFrag unlockTorIpsFrag, ArrayList<HostIP> unlockHostIP) {
        this.unlockTorIpsFragWeakReference = new WeakReference<>(unlockTorIpsFrag);
        this.unlockHostIP = unlockHostIP;
    }


    @Override
    public void run() {
        try {

            if (unlockTorIpsFragWeakReference.get() == null) {
                return;
            }

            UnlockTorIpsFrag unlockTorIpsFrag = unlockTorIpsFragWeakReference.get();

            for (int i = 0; i < unlockHostIP.size(); i++) {

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                resolveHostOrIP(unlockTorIpsFrag, i);

                long currentTime = System.currentTimeMillis();
                if (currentTime - savedTime > TIME_DIFFERENCE) {
                    updateViewData(unlockTorIpsFrag);
                    savedTime = currentTime;
                }
            }

            updateViewData(unlockTorIpsFrag);
        } catch (Exception e) {
            Log.e(LOG_TAG, "GetHostIP exception " + e.getMessage()
                    + " " + e.getCause() + " " + Arrays.toString(e.getStackTrace()));
        }

    }

    private void resolveHostOrIP(UnlockTorIpsFrag unlockTorIpsFrag, int position) {
        HostIP hostIP = unlockHostIP.get(position);
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
    }

    private void updateViewData(UnlockTorIpsFrag unlockTorIpsFrag) {
        Activity activity = unlockTorIpsFrag.getActivity();
        if (activity != null && !activity.isFinishing() && !Thread.currentThread().isInterrupted()) {
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing()) {
                    unlockTorIpsFrag.rvAdapter.notifyDataSetChanged();
                }
            });
        }
    }
}
