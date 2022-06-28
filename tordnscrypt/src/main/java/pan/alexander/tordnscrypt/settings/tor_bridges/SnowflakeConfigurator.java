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

package pan.alexander.tordnscrypt.settings.tor_bridges;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;
import static pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.ALWAYS_SHOW_HELP_MESSAGES;
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
import pan.alexander.tordnscrypt.settings.PathVars;

public class SnowflakeConfigurator {

    private static final int AMP_CACHE = 1;
    private static final int FASTLY = 2;

    private final Context context;
    private final Lazy<SharedPreferences> defaultPreferences;
    private final Lazy<PathVars> pathVars;

    @Inject
    public SnowflakeConfigurator(
            Context context,
            @Named(DEFAULT_PREFERENCES_NAME)
            Lazy<SharedPreferences> defaultPreferences,
            Lazy<PathVars> pathVars
    ) {
        this.context = context;
        this.defaultPreferences = defaultPreferences;
        this.pathVars = pathVars;
    }

    public String getConfiguration() {
        return "ClientTransportPlugin " + getConfiguration(0, "");
    }

    public String getConfiguration(int rendezvous) {
        return getConfiguration(rendezvous, "");
    }

    public String getConfiguration(String stunServers) {
        return getConfiguration(0, stunServers);
    }

    private String getConfiguration(int rendezvousType, String servers) {

        String appDataDir = pathVars.get().getAppDataDir();
        String snowflakePath = pathVars.get().getSnowflakePath();

        String stunServers;
        if (servers.isEmpty()) {
            stunServers = getStunServers();
        } else {
            stunServers = servers;
        }

        StringBuilder stunServerBuilder = new StringBuilder();
        String[] stunServersArr = stunServers.split(", ?");

        Pattern pattern = Pattern.compile(".+\\..+:\\d+");
        for (String server : stunServersArr) {
            Matcher matcher = pattern.matcher(server);
            if (matcher.matches())
            stunServerBuilder.append("stun:").append(server.trim()).append(",");
        }
        stunServerBuilder.deleteCharAt(stunServerBuilder.lastIndexOf(","));

        boolean saveExtendedLogs = defaultPreferences.get()
                .getBoolean(ALWAYS_SHOW_HELP_MESSAGES, false);
        String saveLogsString = "";
        if (saveExtendedLogs) {
            saveLogsString = " -log " + appDataDir + "/logs/Snowflake.log";
        }

        int rendezvous;
        if (rendezvousType == 0) {
            rendezvous = Integer.parseInt(
                    defaultPreferences.get().getString(SNOWFLAKE_RENDEZVOUS, "1")
            );
        } else {
            rendezvous = rendezvousType;
        }

        if (rendezvous == AMP_CACHE) {
            return "snowflake exec "
                    + snowflakePath + " -url https://snowflake-broker.torproject.net/"
                    + " -ampcache https://cdn.ampproject.org/" +
                    " -front www.google.com -ice " + stunServerBuilder + " -max 1" + saveLogsString;
        } else if (rendezvous == FASTLY) {
            return "snowflake exec "
                    + snowflakePath + " -url https://snowflake-broker.torproject.net.global.prod.fastly.net/" +
                    " -front cdn.sstatic.net -ice " + stunServerBuilder + " -max 1" + saveLogsString;
        } else {
            return "";
        }
    }

    private String getStunServers() {
        String defaultStunServers = TextUtils.join(
                ",", context.getResources().getStringArray(R.array.tor_snowflake_stun_servers)
        );

        String stunServers = defaultPreferences.get().getString(
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

        return stunServers;
    }
}
