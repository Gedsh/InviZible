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

package pan.alexander.tordnscrypt.modules;

import android.content.Context;

import androidx.annotation.NonNull;

import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.enums.OperationMode;

import static pan.alexander.tordnscrypt.utils.enums.ModuleState.UNDEFINED;

public final class ModulesStatus {

    private volatile ModuleState dnsCryptState = UNDEFINED;
    private volatile ModuleState torState = UNDEFINED;
    private volatile ModuleState itpdState = UNDEFINED;

    private volatile boolean rootAvailable = false;
    private volatile boolean useModulesWithRoot;
    private volatile boolean requestIptablesUpdate;
    private volatile boolean requestFixTTLRulesUpdate;
    private volatile boolean requestContextUIDUpdate;
    private volatile boolean fixTTL;
    private volatile OperationMode mode;
    private volatile boolean systemDNSAllowed;
    private volatile boolean dnsCryptReady;
    private volatile boolean torReady;
    private volatile boolean itpdReady;
    private volatile boolean deviceInteractive = true;

    private static volatile ModulesStatus modulesStatus;

    private ModulesStatus() {
    }

    @NonNull
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

    public ModuleState getDnsCryptState() {
        return dnsCryptState;
    }

    public ModuleState getTorState() {
        return torState;
    }

    public ModuleState getItpdState() {
        return itpdState;
    }

    public void setDnsCryptState(ModuleState dnsCryptState) {
        this.dnsCryptState = dnsCryptState;
    }

    public void setTorState(ModuleState torState) {
        this.torState = torState;
    }

    public void setItpdState(ModuleState itpdState) {
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

    synchronized boolean isIptablesRulesUpdateRequested() {
        return requestIptablesUpdate;
    }

    public synchronized void setIptablesRulesUpdateRequested(final boolean requestIptablesUpdate) {
        this.requestIptablesUpdate = requestIptablesUpdate;
    }

    public synchronized void setIptablesRulesUpdateRequested(Context context, final boolean requestIptablesUpdate) {
        this.requestIptablesUpdate = requestIptablesUpdate;
        ModulesAux.makeModulesStateExtraLoop(context);
    }

    boolean isFixTTLRulesUpdateRequested() {
        return requestFixTTLRulesUpdate;
    }

    void setFixTTLRulesUpdateRequested(final boolean requestFixTTLRulesUpdate) {
        this.requestFixTTLRulesUpdate = requestFixTTLRulesUpdate;
    }

    public void setFixTTLRulesUpdateRequested(Context context, final boolean requestFixTTLRulesUpdate) {
        setFixTTLRulesUpdateRequested(requestFixTTLRulesUpdate);
        ModulesAux.makeModulesStateExtraLoop(context);
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

    public boolean isSystemDNSAllowed() {
        return systemDNSAllowed;
    }

    public void setSystemDNSAllowed(boolean systemDNSAllowed) {
        this.systemDNSAllowed = systemDNSAllowed;
    }

    public boolean isDnsCryptReady() {
        return dnsCryptReady;
    }

    public void setDnsCryptReady(boolean dnsCryptReady) {
        this.dnsCryptReady = dnsCryptReady;
    }

    public boolean isTorReady() {
        return torReady;
    }

    public void setTorReady(boolean torReady) {
        this.torReady = torReady;
    }

    public boolean isItpdReady() {
        return itpdReady;
    }

    public void setItpdReady(boolean itpdReady) {
        this.itpdReady = itpdReady;
    }

    public boolean isDeviceInteractive() {
        return deviceInteractive;
    }

    public void setDeviceInteractive(boolean interactive) {
        this.deviceInteractive = interactive;
    }
}
