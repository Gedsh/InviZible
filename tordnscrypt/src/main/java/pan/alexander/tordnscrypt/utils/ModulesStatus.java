package pan.alexander.tordnscrypt.utils;

import android.content.Context;
import android.content.Intent;

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

    Copyright 2019 by Garmatin Oleksandr invizible.soft@gmail.com
*/

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;

public final class ModulesStatus {
    private static volatile ModulesStatus modulesStatus;

    private ModulesStatus() {
    }

    public static ModulesStatus getInstance() {
        if (modulesStatus == null) {
            synchronized (ModulesStatus.class) {
                if (modulesStatus == null) {
                    modulesStatus = new ModulesStatus();
                }
            }
        }
        return new ModulesStatus();
    }

    public void refresh(Context context) {
        Intent intent = new Intent(TOP_BROADCAST);
        context.sendBroadcast(intent);
    }
}
