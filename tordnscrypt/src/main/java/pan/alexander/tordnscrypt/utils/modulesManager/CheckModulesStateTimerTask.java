package pan.alexander.tordnscrypt.utils.modulesManager;

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

import android.util.Log;

import java.util.TimerTask;

import pan.alexander.tordnscrypt.utils.enums.ModuleState;
import pan.alexander.tordnscrypt.utils.modulesStatus.ModulesStatus;

import static pan.alexander.tordnscrypt.utils.RootExecService.LOG_TAG;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RESTARTED;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.enums.ModuleState.STOPPED;

public class CheckModulesStateTimerTask extends TimerTask {
    private ModulesStatus modulesStatus = ModulesStatus.getInstance();

    private Thread dnsCryptThread;
    private Thread torThread;
    private Thread itpdThread;

    @Override
    public void run() {
        if (modulesStatus == null) {
            return;
        }

        if (dnsCryptThread != null && dnsCryptThread.isAlive()) {
            if (modulesStatus.getDnsCryptState() == STOPPED
                    || modulesStatus.getDnsCryptState() == RESTARTED) {

                modulesStatus.setDnsCryptState(ModuleState.RUNNING);
            }
        } else {
            if (modulesStatus.getDnsCryptState() == RUNNING
                    || modulesStatus.getDnsCryptState() == RESTARTED) {

                modulesStatus.setDnsCryptState(STOPPED);
            }
        }

        if (torThread != null && torThread.isAlive()) {
            if (modulesStatus.getTorState() == STOPPED
                    || modulesStatus.getTorState() == RESTARTED) {

                modulesStatus.setTorState(ModuleState.RUNNING);
            }
        } else {
            if (modulesStatus.getTorState() == RUNNING
                    || modulesStatus.getTorState() == RESTARTED) {

                modulesStatus.setTorState(STOPPED);
            }
        }

        if (itpdThread != null && itpdThread.isAlive()) {
            if (modulesStatus.getItpdState() == STOPPED
                    || modulesStatus.getItpdState() == RESTARTED) {

                modulesStatus.setItpdState(ModuleState.RUNNING);
            }
        } else {
            if (modulesStatus.getItpdState() == RUNNING
                    || modulesStatus.getItpdState() == RESTARTED) {

                modulesStatus.setItpdState(STOPPED);
            }
        }

        Log.i(LOG_TAG, "DNSCrypt is " + modulesStatus.getDnsCryptState() +
                " Tor is " + modulesStatus.getTorState() + " I2P is " + modulesStatus.getItpdState());
    }

    void setDnsCryptThread(Thread dnsCryptThread) {
        this.dnsCryptThread = dnsCryptThread;
    }

    void setTorThread(Thread torThread) {
        this.torThread = torThread;
    }

    void setItpdThread(Thread itpdThread) {
        this.itpdThread = itpdThread;
    }
}
