package pan.alexander.tordnscrypt.settings.tor_apps;

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

    Copyright 2019-2020 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import android.graphics.drawable.Drawable;

import java.util.Objects;

public class AppUnlock implements Comparable<AppUnlock> {
    String name;
    String pack;
    String uid;
    Drawable icon;
    private boolean system;
    boolean active;

    AppUnlock(String name, String pack, String uid, Drawable icon, boolean system, boolean active) {
        this.name = name;
        this.pack = pack;
        this.uid = uid;
        this.icon = icon;
        this.system = system;
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppUnlock appUnlock = (AppUnlock) o;
        return system == appUnlock.system &&
                active == appUnlock.active &&
                name.equals(appUnlock.name) &&
                pack.equals(appUnlock.pack) &&
                uid.equals(appUnlock.uid) &&
                icon.equals(appUnlock.icon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, pack, uid, icon, system, active);
    }

    @Override
    public int compareTo(AppUnlock appUnlock) {
        if (!this.active && appUnlock.active) {
            return 1;
        } else if (this.active && !appUnlock.active) {
            return -1;
        } else {
            return this.name.toLowerCase().compareTo(appUnlock.name.toLowerCase());
        }
    }
}
