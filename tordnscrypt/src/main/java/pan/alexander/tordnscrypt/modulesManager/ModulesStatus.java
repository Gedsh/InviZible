package pan.alexander.tordnscrypt.modulesManager;

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

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.TimeUnit;

import pan.alexander.tordnscrypt.utils.enums.ModuleState;

import static pan.alexander.tordnscrypt.TopFragment.TOP_BROADCAST;
import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public final class ModulesStatus {

    private ModuleState dnsCryptState = STOPPED;
    private ModuleState torState = STOPPED;
    private ModuleState itpdState = STOPPED;

    private volatile boolean rootAvailable = false;
    private volatile boolean useModulesWithRoot;
    private volatile boolean requestIptablesUpdate;

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
        return modulesStatus;
    }

    public void refreshViews(Context context) {
        if (isUseModulesWithRoot()) {
            Intent intent = new Intent(TOP_BROADCAST);
            context.sendBroadcast(intent);
        } else {
            ModulesVersions.getInstance().refreshVersions(context);
        }

    }

    public void setUseModulesWithRoot(boolean useModulesWithRoot) {
        this.useModulesWithRoot = useModulesWithRoot;
    }

    public synchronized ModuleState getDnsCryptState() {
        return dnsCryptState;
    }

    public synchronized ModuleState getTorState() {
        return torState;
    }

    public synchronized ModuleState getItpdState() {
        return itpdState;
    }

    public synchronized void setDnsCryptState(ModuleState dnsCryptState) {
        this.dnsCryptState = dnsCryptState;
    }

    public synchronized void setTorState(ModuleState torState) {
        this.torState = torState;
    }

    public synchronized void setItpdState(ModuleState itpdState) {
        this.itpdState = itpdState;
    }

    public boolean isUseModulesWithRoot() {
        return useModulesWithRoot;
    }

    public boolean isRootAvailable() {
        return rootAvailable;
    }

    public void setRootAvailable(boolean rootIsAvailable) {
        this.rootAvailable = rootIsAvailable;
    }

    boolean isIptablesRulesUpdateRequested() {
        return requestIptablesUpdate;
    }

    public void setIptablesRulesUpdateRequested(final boolean requestIptablesUpdate) {
        if (requestIptablesUpdate) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    makeDelay();

                    ModulesStatus.this.requestIptablesUpdate = true;
                }

            }).start();
        } else {
            this.requestIptablesUpdate = false;
        }


    }

    private void makeDelay() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "ModulesStatus setIptablesRulesUpdateRequested makeDelay interrupted! " + e.getMessage() + " " + e.getCause());
        }
    }
}
