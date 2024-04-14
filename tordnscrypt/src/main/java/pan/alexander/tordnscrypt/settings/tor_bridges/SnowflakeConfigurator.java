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
import static pan.alexander.tordnscrypt.utils.logger.Logger.logw;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.SNOWFLAKE_RENDEZVOUS;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.STUN_SERVERS;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.Lazy;
import pan.alexander.tordnscrypt.R;

public class SnowflakeConfigurator {

    private static final int SOCKS_ARGUMENT_MAX_LENGTH = 510;

    private static final int AMP_CACHE = 1;
    private static final int FASTLY = 2;
    private static final int CDN77 = 3;
    private static final int AZURE = 4;
    private static final int AMAZON = 5;

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
        if (!currentBridge.contains(" url=") && !isSqsBridge(currentBridge, rendezvousType)) {
            bridgeBuilder.append(" url=").append(getURL(rendezvousType));
        }
        if (!currentBridge.contains(" front=")
                && !currentBridge.contains(" fronts=")
                && !isSqsBridge(currentBridge, rendezvousType)) {
            bridgeBuilder.append(" fronts=").append(getFront(rendezvousType));
        }
        if (!currentBridge.contains(" utls-imitate=")) {
            bridgeBuilder.append(" utls-imitate=").append(getUtlsClientID());
        }
        if (!currentBridge.contains(" sqsqueue=") && isSqsBridge(currentBridge, rendezvousType)) {
            bridgeBuilder.append(" sqsqueue=").append(getSqsQueue());
        }
        if (!currentBridge.contains(" sqscreds=") && isSqsBridge(currentBridge, rendezvousType)) {
            bridgeBuilder.append(" sqscreds=").append(getSqsCredits());
        }

        if (!currentBridge.contains(" ice=")) {
            bridgeBuilder.append(" ice=");

            List<String> stunServersReady = getStunServers(stunServers);
            String stunServersLine = TextUtils.join(",", stunServersReady);
            while (bridgeBuilder.length() + stunServersLine.length() > SOCKS_ARGUMENT_MAX_LENGTH
                    && stunServersReady.size() > 1) {
                String stun = stunServersReady.remove(stunServersReady.size() - 1);
                logw("Shorten too long snowflake line. Removed " + stun);
                stunServersLine = TextUtils.join(",", stunServersReady);
            }

            bridgeBuilder.append(stunServersLine);
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
        } else if (rendezvous == CDN77) {
            return "https://1098762253.rsc.cdn77.org/";
        } else if (rendezvous == AZURE) {
            return "https://snowflake-broker.azureedge.net/";
        } else {
            return "https://snowflake-broker.azureedge.net/";
        }
    }

    private String getFront(int rendezvousType) {
        int rendezvous = getRendezvous(rendezvousType);
        if (rendezvous == AMP_CACHE) {
            return "www.google.com,cdn.ampproject.org";
        } else if (rendezvous == FASTLY) {
            return "github.githubassets.com,www.shazam.com,www.cosmopolitan.com,www.esquire.com";
        } else if (rendezvous == CDN77) {
            return "docs.plesk.com,www.phpmyadmin.net,app.datapacket.com";
        } else if (rendezvous == AZURE) {
            return "ajax.aspnetcdn.com";
        } else {
            return "ajax.aspnetcdn.com";
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

    private List<String> getStunServers(String servers) {

        String stunServers;
        if (servers.isEmpty()) {
            String defaultStunServers = TextUtils.join(
                    ",", context.getResources().getStringArray(R.array.tor_snowflake_stun_servers)
            );

            stunServers = defaultPreferences.get().getString(
                    STUN_SERVERS,
                    defaultStunServers
            );

            if (stunServers != null && stunServers.equals("stun.l.google.com:19302")) {
                stunServers = null;
            }

            if (stunServers == null || stunServers.isEmpty()) {
                stunServers = defaultStunServers;
                defaultPreferences.get().edit().putString(STUN_SERVERS, stunServers).apply();
            }
        } else {
            stunServers = servers;
        }

        List<String> stunServersReady = new ArrayList<>();
        String[] stunServersArr = stunServers.split(", ?");

        Pattern pattern = Pattern.compile(".+\\..+:\\d+");
        for (String server : stunServersArr) {
            Matcher matcher = pattern.matcher(server);
            if (matcher.matches()) {
                stunServersReady.add("stun:" + server.trim());
            }
        }

        return stunServersReady;
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

    private String getSqsQueue() {
        return "https://sqs.us-east-1.amazonaws.com/893902434899/snowflake-broker";
    }

    private String getSqsCredits() {
        return "eyJhd3MtYWNjZXNzLWtleS1pZCI6IkFLSUE1QUlGNFdKSlhTN1lIRUczIiwiYXdzLXNlY3JldC1rZXkiOiI3U0RNc0pBNHM1RitXZWJ1L3pMOHZrMFFXV0lsa1c2Y1dOZlVsQ0tRIn0=";
    }

    private boolean isSqsBridge(String bridge, int rendezvousType) {
        int rendezvous = getRendezvous(rendezvousType);
        return bridge.contains(" sqsqueue=") || !bridge.contains(" url=") && rendezvous == AMAZON;
    }
}
