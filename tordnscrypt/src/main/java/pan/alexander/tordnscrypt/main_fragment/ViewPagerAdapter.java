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

import static pan.alexander.tordnscrypt.utils.logger.Logger.loge;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.ArrayList;

import pan.alexander.tordnscrypt.dnscrypt_fragment.DNSCryptRunFragment;
import pan.alexander.tordnscrypt.itpd_fragment.ITPDRunFragment;
import pan.alexander.tordnscrypt.tor_fragment.TorRunFragment;

public class ViewPagerAdapter extends FragmentPagerAdapter {

    private final ArrayList<ViewPagerFragment> viewPagerFragments = new ArrayList<>(4);

    public ViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
        super(fm, behavior);
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        Fragment fragment = viewPagerFragments.get(position).fragment;
        if (fragment == null) {
            loge("ViewPagerAdapter getItem for " + viewPagerFragments.get(position).title.toString() + " is null");
        }
        return viewPagerFragments.get(position).fragment;
    }

    @Override
    public int getCount() {
        return viewPagerFragments.size();
    }

    public void addFragment(ViewPagerFragment fragment) {
        viewPagerFragments.add(fragment);
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
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
        return viewPagerFragments.get(position).title.toString();
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
