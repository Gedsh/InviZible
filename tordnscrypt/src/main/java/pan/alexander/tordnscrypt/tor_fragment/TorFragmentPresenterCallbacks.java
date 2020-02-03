package pan.alexander.tordnscrypt.tor_fragment;

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

import android.content.Context;

public interface TorFragmentPresenterCallbacks {
    boolean isTorInstalled(Context context);
    boolean isSavedTorStatusRunning(Context context);
    void saveTorStatusRunning(Context context, boolean running);
    void displayLog(int period);
    void stopDisplayLog();
    void refreshTorState(Context context);
    void setTorSomethingWrong();
    void setTorRunning();
    void setTorStopped(Context context);
    void startRefreshTorUnlockIPs(Context context);
    void setTorInstalling();
    void setTorInstalled();
    void setTorStartButtonEnabled(boolean enabled);
    void setTorProgressBarIndeterminate(boolean indeterminate);
}
