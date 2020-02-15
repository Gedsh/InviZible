package pan.alexander.tordnscrypt.modules;

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

import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public final class ModulesStatus {

    private ModuleState dnsCryptState = STOPPED;
    private ModuleState torState = STOPPED;
    private ModuleState itpdState = STOPPED;

    private volatile boolean rootAvailable = false;
    private volatile boolean useModulesWithRoot;
    private volatile boolean requestIptablesUpdate;
    private volatile boolean requestContextUIDUpdate;
    private volatile boolean fixTTL;
    private volatile OperationMode mode;

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

    public void setUseModulesWithRoot(final boolean useModulesWithRoot) {
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

    void setRootAvailable(boolean rootIsAvailable) {
        this.rootAvailable = rootIsAvailable;
    }

    boolean isIptablesRulesUpdateRequested() {
        return requestIptablesUpdate;
    }

    public void setIptablesRulesUpdateRequested(final boolean requestIptablesUpdate) {
        this.requestIptablesUpdate = requestIptablesUpdate;
    }

    public boolean isContextUIDUpdateRequested() {
        return requestContextUIDUpdate;
    }

    public void setContextUIDUpdateRequested(boolean requestContextUIDUpdate) {
        this.requestContextUIDUpdate = requestContextUIDUpdate;
    }

    public boolean isFixTTL() {
        return fixTTL && rootAvailable;
    }

    public void setFixTTL(boolean fixTTL) {
        this.fixTTL = fixTTL;
    }

    public OperationMode getMode() {
        return mode;
    }

    public void setMode(OperationMode mode) {
        this.mode = mode;
    }
}
