package pan.alexander.tordnscrypt;
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

import static pan.alexander.tordnscrypt.utils.bootcomplete.BootCompleteManager.ALWAYS_ON_VPN;
import static pan.alexander.tordnscrypt.utils.bootcomplete.BootCompleteManager.SHELL_SCRIPT_CONTROL;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dagger.Lazy;
import pan.alexander.tordnscrypt.utils.bootcomplete.BootCompleteManager;

import javax.inject.Inject;

public class BootCompleteReceiver extends BroadcastReceiver {

    public static final String MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";
    private static final String BOOT_COMPLETE = "android.intent.action.BOOT_COMPLETED";
    private static final String QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";
    private static final String HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON";
    private static final String REBOOT = "android.intent.action.REBOOT";

    @Inject
    public Lazy<BootCompleteManager> bootCompleteManager;

    @Override
    public void onReceive(final Context context, Intent intent) {

        App.getInstance().getDaggerComponent().inject(this);

        String action = intent.getAction();

        if (action == null) {
            return;
        }

        if (action.equalsIgnoreCase(BOOT_COMPLETE)
                || action.equalsIgnoreCase(QUICKBOOT_POWERON)
                || action.equalsIgnoreCase(HTC_QUICKBOOT_POWERON)
                || action.equalsIgnoreCase(REBOOT)
                || action.equalsIgnoreCase(MY_PACKAGE_REPLACED)
                || action.equals(ALWAYS_ON_VPN)
                || action.equals(SHELL_SCRIPT_CONTROL)) {
            bootCompleteManager.get().performAction(context, intent);
        }
    }
}
