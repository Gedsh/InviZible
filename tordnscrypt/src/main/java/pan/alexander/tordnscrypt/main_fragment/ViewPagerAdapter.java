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

package pan.alexander.tordnscrypt.main_fragment;

import static pan.alexander.tordnscrypt.utils.enums.ModuleState.RUNNING;
import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.ArrayList;
import java.util.List;

import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSCryptRunFragment;
import pan.alexander.tordnscrypt.itpd_fragment.ITPDRunFragment;
import pan.alexander.tordnscrypt.modules.ModulesStatus;
import pan.alexander.tordnscrypt.tor_fragment.TorRunFragment;

public class ViewPagerAdapter extends FragmentPagerAdapter {

    public final static int MAIN_SCREEN_FRAGMENT_QUANTITY = 4;

    private final ModulesStatus modulesStatus = ModulesStatus.getInstance();
    private final ArrayList<ViewPagerFragment> viewPagerFragments = new ArrayList<>(
            MAIN_SCREEN_FRAGMENT_QUANTITY
    );

    public ViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
        super(fm, behavior);
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        if (viewPagerFragments.isEmpty()) {
            addFragments(true);
        }
        Fragment fragment = viewPagerFragments.get(position).fragment;
        if (fragment == null) {
            loge("ViewPagerAdapter getItem for " + viewPagerFragments.get(position).title.toString() + " is null");
            addFragments(true);
        }
        return viewPagerFragments.get(position).fragment;
    }

    @Override
    public int getCount() {
        if (viewPagerFragments.isEmpty()) {
            addFragments(true);
        }
        return viewPagerFragments.size();
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        if (viewPagerFragments.isEmpty()) {
            addFragments(false);
        }
        if (fragment instanceof MainFragment) {
            viewPagerFragments.set(
                    getFragmentPositionByTitle(PagerTitle.MAIN),
                    new ViewPagerFragment(PagerTitle.MAIN, fragment)
            );
        } else if (fragment instanceof DNSCryptRunFragment) {
            viewPagerFragments.set(
                    getFragmentPositionByTitle(PagerTitle.DNS),
                    new ViewPagerFragment(PagerTitle.DNS, fragment)
            );
        } else if (fragment instanceof TorRunFragment) {
            viewPagerFragments.set(
                    getFragmentPositionByTitle(PagerTitle.TOR),
                    new ViewPagerFragment(PagerTitle.TOR, fragment)
            );
        } else if (fragment instanceof ITPDRunFragment) {
            viewPagerFragments.set(
                    getFragmentPositionByTitle(PagerTitle.I2P),
                    new ViewPagerFragment(PagerTitle.I2P, fragment)
            );
        }
        return fragment;
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        if (viewPagerFragments.isEmpty()) {
            addFragments(true);
        }
        Fragment fragment = (Fragment) object;
        for (int i = 0; i < viewPagerFragments.size(); i++) {
            if (fragment.equals(viewPagerFragments.get(i).fragment)) {
                return i;
            }
        }
        return super.getItemPosition(object);
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        if (viewPagerFragments.isEmpty()) {
            addFragments(true);
        }
        return viewPagerFragments.get(position).title.toString();
    }

    @Override
    public void notifyDataSetChanged() {
        List<ViewPagerFragment> fragments = new ArrayList<>(viewPagerFragments);
        viewPagerFragments.clear();
        viewPagerFragments.addAll(sorted(fragments));
        super.notifyDataSetChanged();
    }

    private int getFragmentPositionByTitle(PagerTitle title) {
        for (int i = 0; i < viewPagerFragments.size(); i++) {
            if (viewPagerFragments.get(i).title.equals(title)) {
                return i;
            }
        }
        loge("ViewPagerAdapter unable to find fragment with title " + title.toString());
        return -1;
    }

    public void addFragments(boolean initialise) {
        viewPagerFragments.clear();
        List<ViewPagerFragment> fragments = initiateAndGetFragments(initialise);
        viewPagerFragments.addAll(sorted(fragments));
    }

    private List<ViewPagerFragment> initiateAndGetFragments(boolean initialise) {

        List<ViewPagerFragment> fragments = new ArrayList<>();

        MainFragment mainFragment;
        if (initialise) {
            mainFragment = new MainFragment();
        } else {
            mainFragment = null;
        }
        DNSCryptRunFragment dnsCryptRunFragment;
        if (initialise) {
            dnsCryptRunFragment = new DNSCryptRunFragment();
        } else {
            dnsCryptRunFragment = null;
        }
        TorRunFragment torRunFragment;
        if (initialise) {
            torRunFragment = new TorRunFragment();
        } else {
            torRunFragment = null;
        }
        ITPDRunFragment itpdRunFragment;
        if (initialise) {
            itpdRunFragment = new ITPDRunFragment();
        } else {
            itpdRunFragment = null;
        }

        fragments.add(new ViewPagerFragment(ViewPagerAdapter.PagerTitle.MAIN, mainFragment));
        fragments.add(new ViewPagerFragment(ViewPagerAdapter.PagerTitle.DNS, dnsCryptRunFragment));
        fragments.add(new ViewPagerFragment(ViewPagerAdapter.PagerTitle.TOR, torRunFragment));
        fragments.add(new ViewPagerFragment(ViewPagerAdapter.PagerTitle.I2P, itpdRunFragment));

        return fragments;
    }

    private List<ViewPagerFragment> sorted(List<ViewPagerFragment> fragments) {

        ViewPagerFragment mainFragment = null;
        ViewPagerFragment dnsCryptRunFragment = null;
        ViewPagerFragment torRunFragment = null;
        ViewPagerFragment itpdRunFragment = null;

        boolean dnsCryptRunning = isDNSCryptRunning();
        boolean torRunning = isTorRunning();
        boolean itpdRunning = isI2PDRunning();

        List<ViewPagerFragment> sortedFragments = new ArrayList<>();

        for (ViewPagerFragment fragment: fragments) {
            if (PagerTitle.MAIN.equals(fragment.title)) {
                mainFragment = fragment;
            } else if (PagerTitle.DNS.equals(fragment.title)) {
                dnsCryptRunFragment = fragment;
            } else if (PagerTitle.TOR.equals(fragment.title)) {
                torRunFragment = fragment;
            } else if (PagerTitle.I2P.equals(fragment.title)) {
                itpdRunFragment = fragment;
            }
        }

        sortedFragments.add(mainFragment);

        if (dnsCryptRunning) {
            sortedFragments.add(dnsCryptRunFragment);
        }
        if (torRunning) {
            sortedFragments.add(torRunFragment);
        }
        if (itpdRunning) {
            sortedFragments.add(itpdRunFragment);
        }

        if (!dnsCryptRunning) {
            sortedFragments.add(dnsCryptRunFragment);
        }
        if (!torRunning) {
            sortedFragments.add(torRunFragment);
        }
        if (!itpdRunning) {
            sortedFragments.add(itpdRunFragment);
        }

        return sortedFragments;
    }

    private boolean isDNSCryptRunning() {
        return modulesStatus.getDnsCryptState() == RUNNING;
    }

    private boolean isTorRunning() {
        return modulesStatus.getTorState() == RUNNING;
    }

    private boolean isI2PDRunning() {
        return modulesStatus.getItpdState() == RUNNING;
    }

    public static class ViewPagerFragment {
        private final PagerTitle title;
        private final Fragment fragment;

        public ViewPagerFragment(PagerTitle title, Fragment fragment) {
            this.title = title;
            this.fragment = fragment;
        }
    }

    public enum PagerTitle {
        MAIN,
        DNS,
        TOR,
        I2P
    }
}
