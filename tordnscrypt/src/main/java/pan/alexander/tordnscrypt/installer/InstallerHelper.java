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

package pan.alexander.tordnscrypt.installer;

import static pan.alexander.tordnscrypt.di.SharedPreferencesModule.DEFAULT_PREFERENCES_NAME;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import pan.alexander.tordnscrypt.R;

public class InstallerHelper {

    private final Context context;
    private final SharedPreferences defaultPreferences;

    @Inject
    public InstallerHelper(Context context, @Named(DEFAULT_PREFERENCES_NAME) SharedPreferences defaultPreferences) {
        this.context = context;
        this.defaultPreferences = defaultPreferences;
    }

    @SuppressLint("SdCardPath")
    public List<String> prepareDNSCryptForGP(List<String> lines) {

        defaultPreferences.edit().putBoolean("require_nofilter", true).apply();

        ArrayList<String> prepared = new ArrayList<>();

        for (String line : lines) {

            if (line.contains("blacklist_file")) {
                line = "";
            } else if (line.contains("whitelist_file")) {
                line = "";
            } else if (line.contains("blocked_names_file")) {
                line = "";
            } else if (line.contains("blocked_ips_file")) {
                line = "";
            } else if (line.matches("(^| )\\{ ?server_name([ =]).+")) {
                line = "";
            } else if (line.matches("(^| )server_names([ =]).+")) {
                String[] servers = context.getResources().getStringArray(R.array.default_dnscrypt_servers_gp);
                line = "server_names = ['" + TextUtils.join("', '", servers) + "']";
            } else if (line.contains("require_nofilter")) {
                line = "require_nofilter = true";
            }

            if (!line.isEmpty()) {
                prepared.add(line);
            }
        }

        return prepared;
    }
}
