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

package pan.alexander.tordnscrypt.settings.tor_bridges;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SNOWFLAKE_RENDEZVOUS;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import pan.alexander.tordnscrypt.R;

public class SnowflakeConfigurator {

    private static final int AMP_CACHE = 1;
    private static final int FASTLY = 2;

    private final Context context;
    private final Lazy<SharedPreferences> defaultPreferences;

    @Inject
    public SnowflakeConfigurator(
            Context context,
            @Named(DEFAULT_PREFERENCES_NAME)
            Lazy<SharedPreferences> defaultPreferences
    ) {
        this.context = context;
        this.defaultPreferences = defaultPreferences;
    }
    
    String getConfiguration(String currentBridge) {
        return getConfiguration(currentBridge, 0, "");
    }

    public String getConfiguration(String currentBridge, String stunServers) {
        return getConfiguration(currentBridge, 0, stunServers);
    }

    public String getConfiguration(String currentBridge, int rendezvousType) {
        return getConfiguration(currentBridge, rendezvousType, "");
    }

    private String getConfiguration(String currentBridge, int rendezvousType, String stunServers) {
        StringBuilder bridgeBuilder = new StringBuilder();
        bridgeBuilder.append(currentBridge);
        if (!currentBridge.contains(" url=")) {
            bridgeBuilder.append(" url=").append(getURL(rendezvousType));
        }
        if (!currentBridge.contains(" front=") && !currentBridge.contains(" fronts=")) {
            bridgeBuilder.append(" fronts=").append(getFront(rendezvousType));
        }
        if (!currentBridge.contains(" ice=")) {
            bridgeBuilder.append(" ice=").append(getStunServers(stunServers));
        }
        if (!currentBridge.contains(" utls-imitate=")) {
            bridgeBuilder.append(" utls-imitate=").append(getUtlsClientID());
        }
        return bridgeBuilder.toString();
    }

    private String getURL(int rendezvousType) {
        int rendezvous = getRendezvous(rendezvousType);
        if (rendezvous == AMP_CACHE) {
            return "https://snowflake-broker.torproject.net/"
                    + " ampcache=https://cdn.ampproject.org/";
        } else if (rendezvous == FASTLY) {
            return "https://snowflake-broker.torproject.net.global.prod.fastly.net/";
        } else {
            return "";
        }
    }

    private String getFront(int rendezvousType) {
        int rendezvous = getRendezvous(rendezvousType);
        if (rendezvous == AMP_CACHE) {
            return "www.google.com,accounts.google.com";
        } else if (rendezvous == FASTLY) {
            return "foursquare.com,github.githubassets.com";
        } else {
            return "";
        }
    }

    private int getRendezvous(int rendezvousType) {
        int rendezvous;
        if (rendezvousType == 0) {
            rendezvous = Integer.parseInt(
                    defaultPreferences.get().getString(SNOWFLAKE_RENDEZVOUS, "1")
            );
        } else {
            rendezvous = rendezvousType;
        }
        return rendezvous;
    }

    private String getStunServers(String servers) {

        String stunServers;
        if (servers.isEmpty()) {
            String defaultStunServers = TextUtils.join(
                    ",", context.getResources().getStringArray(R.array.tor_snowflake_stun_servers)
            );

            stunServers = defaultPreferences.get().getString(
                    "pref_tor_snowflake_stun",
                    defaultStunServers
            );

            if (stunServers != null && stunServers.equals("stun.l.google.com:19302")) {
                stunServers = null;
            }

            if (stunServers == null) {
                stunServers = defaultStunServers;
                defaultPreferences.get().edit().putString("pref_tor_snowflake_stun", stunServers).apply();
            }
        } else {
            stunServers = servers;
        }

        StringBuilder stunServerBuilder = new StringBuilder();
        String[] stunServersArr = stunServers.split(", ?");

        Pattern pattern = Pattern.compile(".+\\..+:\\d+");
        for (String server : stunServersArr) {
            Matcher matcher = pattern.matcher(server);
            if (matcher.matches()) {
                stunServerBuilder.append("stun:").append(server.trim()).append(",");
            }
        }
        stunServerBuilder.deleteCharAt(stunServerBuilder.lastIndexOf(","));

        return stunServerBuilder.toString();
    }

    @SuppressWarnings("unused")
    private String getUtlsClientID() {
        final String hellorandomizedalpn = "hellorandomizedalpn";
        final String hellorandomizednoalpn = "hellorandomizednoalpn";
        final String hellofirefox_auto = "hellofirefox_auto";
        final String hellofirefox_55 = "hellofirefox_55";
        final String hellofirefox_56 = "hellofirefox_56";
        final String hellofirefox_63 = "hellofirefox_63";
        final String hellofirefox_65 = "hellofirefox_65";
        final String hellochrome_auto = "hellochrome_auto";
        final String hellochrome_58 = "hellochrome_58";
        final String hellochrome_62 = "hellochrome_62";
        final String hellochrome_70 = "hellochrome_70";
        final String hellochrome_72 = "hellochrome_72";
        final String helloios_auto = "helloios_auto";
        final String helloios_11_1 = "helloios_11_1";
        final String helloios_12_1 = "helloios_12_1";

        return hellorandomizedalpn;
    }
}
