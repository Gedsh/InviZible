package pan.alexander.tordnscrypt.vpn.service;
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.VpnService;

import androidx.annotation.NonNull;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class BuilderVPN extends VpnService.Builder {
    private NetworkInfo networkInfo;
    private int mtu;
    private final List<String> listAddress = new ArrayList<>();
    private final List<String> listRoute = new ArrayList<>();
    private final List<InetAddress> listDns = new ArrayList<>();
    private final List<String> listDisallowed = new ArrayList<>();
    private final List<String> listAllowed = new ArrayList<>();
    private String performAllowedOrDisallowed = "";
    private boolean fixTTL;

    BuilderVPN(ServiceVPN serviceVPN) {
        serviceVPN.super();
        ConnectivityManager cm = (ConnectivityManager) serviceVPN.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            networkInfo = cm.getActiveNetworkInfo();
        }
    }

    @NonNull
    @Override
    public VpnService.Builder setMtu(int mtu) {
        this.mtu = mtu;
        super.setMtu(mtu);
        return this;
    }

    @NonNull
    @Override
    public BuilderVPN addAddress(@NonNull String address, int prefixLength) {
        listAddress.add(address + "/" + prefixLength);
        super.addAddress(address, prefixLength);
        return this;
    }

    @NonNull
    @Override
    public BuilderVPN addRoute(@NonNull String address, int prefixLength) {
        listRoute.add(address + "/" + prefixLength);
        super.addRoute(address, prefixLength);
        return this;
    }

    @NonNull
    @Override
    public BuilderVPN addRoute(InetAddress address, int prefixLength) {
        listRoute.add(address.getHostAddress() + "/" + prefixLength);
        super.addRoute(address, prefixLength);
        return this;
    }

    @NonNull
    @Override
    public BuilderVPN addDnsServer(@NonNull InetAddress address) {
        listDns.add(address);
        super.addDnsServer(address);
        return this;
    }

    @NonNull
    @Override
    public BuilderVPN addDisallowedApplication(@NonNull String packageName) throws PackageManager.NameNotFoundException {
        listDisallowed.add(packageName);
        performAllowedOrDisallowed = "disallowed";
        super.addDisallowedApplication(packageName);
        return this;
    }

    @NonNull
    @Override
    public VpnService.Builder addAllowedApplication(@NonNull String packageName) throws PackageManager.NameNotFoundException {
        listAllowed.add(packageName);
        performAllowedOrDisallowed = "allowed";
        super.addAllowedApplication(packageName);
        return this;
    }

    @SuppressWarnings("SameParameterValue")
    void setFixTTL(boolean fixTTL) {
        this.fixTTL = fixTTL;
    }

    @Override
    public boolean equals(Object obj) {

        if (obj == null) {
            return false;
        }

        if (this.getClass() != obj.getClass()) {
            return false;
        }

        BuilderVPN other = (BuilderVPN) obj;

        if (this.networkInfo == null || other.networkInfo == null ||
                this.networkInfo.getType() != other.networkInfo.getType()) {
            return false;
        }

        if (this.mtu != other.mtu) {
            return false;
        }

        if (!this.performAllowedOrDisallowed.equals(other.performAllowedOrDisallowed)) {
            return false;
        }

        if (this.fixTTL != other.fixTTL) {
            return false;
        }

        if (this.listAddress.size() != other.listAddress.size()) {
            return false;
        }

        if (this.listRoute.size() != other.listRoute.size()) {
            return false;
        }

        if (this.listDns.size() != other.listDns.size()) {
            return false;
        }

        if (this.listDisallowed.size() != other.listDisallowed.size()) {
            return false;
        }

        if (this.listAllowed.size() != other.listAllowed.size()) {
            return false;
        }

        for (String address : this.listAddress) {
            if (!other.listAddress.contains(address)) {
                return false;
            }
        }

        for (String route : this.listRoute) {
            if (!other.listRoute.contains(route)) {
                return false;
            }
        }

        for (InetAddress dns : this.listDns) {
            if (!other.listDns.contains(dns)) {
                return false;
            }
        }

        for (String pkg : this.listDisallowed) {
            if (!other.listDisallowed.contains(pkg)) {
                return false;
            }
        }

        for (String pkg : this.listAllowed) {
            if (!other.listAllowed.contains(pkg)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkInfo, mtu, listAddress, listRoute, listDns, listDisallowed, listAllowed, performAllowedOrDisallowed, fixTTL);
    }
}
