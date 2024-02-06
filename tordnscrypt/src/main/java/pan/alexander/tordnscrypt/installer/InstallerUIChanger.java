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

package pan.alexander.tordnscrypt.installer;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;

import android.util.Log;

import pan.alexander.tordnscrypt.dialogs.AgreementDialog;
import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSCryptRunFragment;
import pan.alexander.tordnscrypt.itpd_fragment.ITPDRunFragment;
import pan.alexander.tordnscrypt.MainActivity;
import pan.alexander.tordnscrypt.R;
import pan.alexander.tordnscrypt.tor_fragment.TorRunFragment;

import static pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG;

class InstallerUIChanger {
    private MainActivity mainActivity;
    private DNSCryptRunFragment dnsCryptRunFragment;
    private TorRunFragment torRunFragment;
    private ITPDRunFragment itpdRunFragment;

    InstallerUIChanger(MainActivity mainActivity) {
        getViews(mainActivity);
    }

    private void getViews(MainActivity mainActivity) {
        this.mainActivity = mainActivity;

        if (mainActivity == null) {
            throw new IllegalStateException("Installer: InstallerUIChanger is null, interrupt installation");
        }

        dnsCryptRunFragment = mainActivity.getDNSCryptRunFragment();
        torRunFragment = mainActivity.getTorRunFragment();
        itpdRunFragment = mainActivity.getITPDRunFragment();

        Log.i(LOG_TAG, "Installer: getViews() OK");
    }

    Runnable lockDrawerMenu(final boolean lock) {
       return () -> {
           DrawerLayout mDrawerLayout = mainActivity.findViewById(R.id.drawer_layout);
           if (lock) {
               mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
               Log.i(LOG_TAG, "Installer: DrawerMenu locked");
           } else {
               mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
               Log.i(LOG_TAG, "Installer: DrawerMenu unlocked");
           }
       };
    }

    Runnable setModulesStatusTextInstalling() {
        return () -> {
            if (dnsCryptRunFragment != null && dnsCryptRunFragment.getPresenter() != null) {
                dnsCryptRunFragment.getPresenter().setDnsCryptInstalling();

            }

            if (torRunFragment != null && torRunFragment.getPresenter() != null) {
                torRunFragment.getPresenter().setTorInstalling();
            }

            if (itpdRunFragment != null && itpdRunFragment.getPresenter() != null) {
                itpdRunFragment.getPresenter().setITPDInstalling();
            }

            Log.i(LOG_TAG, "Installer: setModulesStatusTextInstalling");
        };
    }

    Runnable setDnsCryptInstalledStatus() {
        return () -> {
            if (dnsCryptRunFragment != null && dnsCryptRunFragment.getPresenter() != null) {
                dnsCryptRunFragment.getPresenter().setDnsCryptInstalled();
            }
        };
    }

    Runnable setTorInstalledStatus() {
        return () -> {
            if (torRunFragment != null && torRunFragment.getPresenter() != null) {
                torRunFragment.getPresenter().setTorInstalled();
            }
        };
    }

    Runnable setItpdInstalledStatus() {
        return () -> {
            if (itpdRunFragment != null && itpdRunFragment.getPresenter() != null) {
                itpdRunFragment.getPresenter().setITPDInstalled();
            }
        };
    }

    Runnable setModulesStartButtonsDisabled() {
        return () -> {
            if (dnsCryptRunFragment != null && dnsCryptRunFragment.getPresenter() != null) {
                dnsCryptRunFragment.getPresenter().setDNSCryptStartButtonEnabled(false);
            }

            if (torRunFragment != null && torRunFragment.getPresenter() != null) {
                torRunFragment.getPresenter().setTorStartButtonEnabled(false);
            }

            if (itpdRunFragment != null && itpdRunFragment.getPresenter() != null) {
                itpdRunFragment.getPresenter().setITPDStartButtonEnabled(false);
            }

            Log.i(LOG_TAG, "Installer: setModulesStartButtonsDisabled");
        };
    }

    Runnable startModulesProgressBarIndeterminate() {
        return () -> {
            if (dnsCryptRunFragment != null && dnsCryptRunFragment.getPresenter() != null) {
                dnsCryptRunFragment.getPresenter().setDNSCryptProgressBarIndeterminate(true);
            }

            if (torRunFragment != null && torRunFragment.getPresenter() != null) {
                torRunFragment.getPresenter().setTorProgressBarIndeterminate(true);
            }

            if (itpdRunFragment != null && itpdRunFragment.getPresenter() != null) {
                itpdRunFragment.getPresenter().setITPDProgressBarIndeterminate(true);
            }

            Log.i(LOG_TAG, "Installer: startModulesProgressBarIndeterminate");
        };
    }

    Runnable stopModulesProgressBarIndeterminate() {
        return () -> {
            if (dnsCryptRunFragment != null && dnsCryptRunFragment.getPresenter() != null) {
                dnsCryptRunFragment.getPresenter().setDNSCryptProgressBarIndeterminate(false);
            }

            if (torRunFragment != null && torRunFragment.getPresenter() != null) {
                torRunFragment.getPresenter().setTorProgressBarIndeterminate(false);
            }

            if (itpdRunFragment != null && itpdRunFragment.getPresenter() != null) {
                itpdRunFragment.getPresenter().setITPDProgressBarIndeterminate(false);
            }

            Log.i(LOG_TAG, "Installer: stopModulesProgressBarIndeterminate");
        };
    }

    Runnable dnsCryptProgressBarIndeterminate(final boolean indeterminate) {
        return () -> {
            if (dnsCryptRunFragment != null && dnsCryptRunFragment.getPresenter() != null) {
                dnsCryptRunFragment.getPresenter().setDNSCryptProgressBarIndeterminate(indeterminate);
            }
        };
    }

    Runnable torProgressBarIndeterminate(final boolean indeterminate) {
        return () -> {
            if (torRunFragment != null && torRunFragment.getPresenter() != null) {
                torRunFragment.getPresenter().setTorProgressBarIndeterminate(indeterminate);
            }
        };
    }
    Runnable itpdProgressBarIndeterminate(final boolean indeterminate) {
        return () -> {
            if (itpdRunFragment != null && itpdRunFragment.getPresenter() != null) {
                itpdRunFragment.getPresenter().setITPDProgressBarIndeterminate(indeterminate);
            }
        };
    }


    Runnable setModulesStartButtonsEnabled() {
        return () -> {
            if (dnsCryptRunFragment != null && dnsCryptRunFragment.getPresenter() != null) {
                dnsCryptRunFragment.getPresenter().setDNSCryptStartButtonEnabled(true);
            }

            if (torRunFragment != null && torRunFragment.getPresenter() != null) {
                torRunFragment.getPresenter().setTorStartButtonEnabled(true);
            }

            if (itpdRunFragment != null && itpdRunFragment.getPresenter() != null) {
                itpdRunFragment.getPresenter().setITPDStartButtonEnabled(true);
            }

            Log.i(LOG_TAG, "Installer: setModulesStartButtonsEnabled");
        };
    }

    Runnable showDialogAfterInstallation() {
        return () -> {
            DialogFragment agreementDialog = AgreementDialog.newInstance();
            agreementDialog.setCancelable(false);
            agreementDialog.show(mainActivity.getSupportFragmentManager(), "AgreementDialog");
        };
    }

    Runnable setModulesStatusTextError() {
        return () -> {
            if (dnsCryptRunFragment != null && dnsCryptRunFragment.getPresenter() != null) {
                dnsCryptRunFragment.getPresenter().setDnsCryptSomethingWrong();
            }

            if (torRunFragment != null && torRunFragment.getPresenter() != null) {
                torRunFragment.getPresenter().setTorSomethingWrong();
            }

            if (itpdRunFragment != null && itpdRunFragment.getPresenter() != null) {
                itpdRunFragment.getPresenter().setITPDSomethingWrong();
            }

            Log.i(LOG_TAG, "Installer: setModulesStatusTextError");
        };
    }

    void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }
}
