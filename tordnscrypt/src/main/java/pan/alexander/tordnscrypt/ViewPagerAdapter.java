package pan.alexander.tordnscrypt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.ArrayList;

public class ViewPagerAdapter extends FragmentPagerAdapter {

    private ArrayList<ViewPagerFragment> viewPagerFragments = new ArrayList<>(4);

    ViewPagerAdapter(@NonNull FragmentManager fm, int behavior) {
        super(fm, behavior);
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return viewPagerFragments.get(position).fragment;
    }

    @Override
    public int getCount() {
        return viewPagerFragments.size();
    }

    void addFragment(ViewPagerFragment fragment) {
        viewPagerFragments.add(fragment);
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return viewPagerFragments.get(position).title;
    }

    class ViewPagerFragment {
        private String title;
        private Fragment fragment;

        ViewPagerFragment(String title, Fragment fragment) {
            this.title = title;
            this.fragment = fragment;
        }
    }
}
