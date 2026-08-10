package com.cooksync.app.ui.admin;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Supplies {@link AdminConsoleActivity}'s three moderation tabs — Reports, Tags, Users — to
 * its {@link androidx.viewpager2.widget.ViewPager2}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class AdminPagerAdapter extends FragmentStateAdapter {

    public static final int TAB_REPORTS = 0;
    public static final int TAB_TAGS = 1;
    public static final int TAB_USERS = 2;

    public AdminPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case TAB_REPORTS -> new AdminReportsFragment();
            case TAB_TAGS -> new AdminTagsFragment();
            default -> new AdminUsersFragment();
        };
    }
}
