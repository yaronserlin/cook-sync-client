package com.cooksync.app.ui.admin;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.os.Bundle;
import android.widget.TextView;

import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.util.SessionManager;
import com.dtos.response.admin.AdminStatsResponse;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;

/**
 * Admin-only moderation console: reported-reviews queue, duplicate-tag consolidation, and the
 * user directory, behind three tabs. Reachable only from the Settings screen's "Admin console"
 * row, which is itself hidden for non-admins; this Activity re-checks
 * {@link SessionManager#isAdmin()} on its own as defense in depth against a direct launch.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class AdminConsoleActivity extends BaseActivity {

    private AdminStatsViewModel statsViewModel;
    private AdminReportsViewModel reportsViewModel;
    private AdminTagsViewModel tagsViewModel;
    private AdminUsersViewModel usersViewModel;
    private AdminUnitsViewModel unitsViewModel;
    private TabLayout tabLayout;
    private TextView tvHeading;
    private TextView tvBadge;
    private int activeTab = AdminPagerAdapter.TAB_REPORTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.getInstance().isAdmin()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_admin_console);

        getWindow().setStatusBarColor(getColor(R.color.color_neutral_900));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        ViewModelProvider provider = new ViewModelProvider(this, new ViewModelFactory());
        statsViewModel = provider.get(AdminStatsViewModel.class);
        reportsViewModel = provider.get(AdminReportsViewModel.class);
        tagsViewModel = provider.get(AdminTagsViewModel.class);
        usersViewModel = provider.get(AdminUsersViewModel.class);
        unitsViewModel = provider.get(AdminUnitsViewModel.class);

        tvHeading = findViewById(R.id.tv_admin_heading);
        tvBadge = findViewById(R.id.tv_admin_badge);
        tabLayout = findViewById(R.id.tabs_admin);
        ViewPager2 viewPager = findViewById(R.id.vp_admin_tabs);

        findViewById(R.id.btn_admin_back).setOnClickListener(v -> finish());

        viewPager.setAdapter(new AdminPagerAdapter(this));
        String[] tabLabels = {
                getString(R.string.admin_tab_reports),
                getString(R.string.admin_tab_tags),
                getString(R.string.admin_tab_users),
                getString(R.string.admin_tab_units)
        };
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setCustomView(R.layout.item_admin_tab);
            TextView label = tab.getCustomView().findViewById(R.id.tv_admin_tab_label);
            label.setText(tabLabels[position]);
        }).attach();

        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            styleTab(tabLayout.getTabAt(i), i == activeTab);
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                activeTab = tab.getPosition();
                styleTab(tab, true);
                updateHeader();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                styleTab(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        observeViewModel();

        statsViewModel.loadStats();
        reportsViewModel.loadReportedReviews();
        tagsViewModel.loadDuplicateTagGroups();
        usersViewModel.refreshUsers(null, null);
        unitsViewModel.loadUnits();
    }

    /**
     * Applies the active/inactive pill fill and text colors to a tab's custom view, matching
     * {@code CookSync.Button.FilterChip}'s runtime-tint convention.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param tab the tab whose custom view to style
     * @param selected whether this tab is the currently active one
     */
    private void styleTab(TabLayout.Tab tab, boolean selected) {
        if (tab == null || tab.getCustomView() == null) return;
        int bgColor = getColor(selected ? R.color.color_neutral_100 : R.color.color_neutral_800);
        int fgColor = getColor(selected ? R.color.color_neutral_900 : R.color.color_neutral_300);
        tab.getCustomView().setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
        TextView label = tab.getCustomView().findViewById(R.id.tv_admin_tab_label);
        TextView count = tab.getCustomView().findViewById(R.id.tv_admin_tab_count);
        label.setTextColor(fgColor);
        count.setTextColor(fgColor);
    }

    /**
     * Wires the Reports/Tags/Users badges (both the tab pills and the header pill for whichever
     * tab is active) to their respective data sources. The Reports and Users badges are driven
     * exclusively by {@link AdminStatsResponse} (the system-wide, unfiltered totals from
     * {@code GET /api/admin/stats}) rather than by each tab's own filtered/paginated list, so
     * selecting a reason chip or an enabled-status chip never changes the badge — only actions
     * that actually change the underlying data do, via {@link AdminReportsViewModel#getStatsResyncNeeded()}
     * re-triggering {@code loadStats()} once a remove/keep/ban actually settles server-side (the
     * Users badge shows the total registered-account count, which suspending doesn't change, so
     * it has no equivalent resync hook). The Tags badge is the one exception to the
     * stats-endpoint rule: there is no "total duplicate pairs" stat, so it is driven directly by
     * the duplicates list itself, which already updates optimistically on merge.
     */
    private void observeViewModel() {
        usersViewModel.getUserDisabledEvent().observe(this, event -> {
            String userId = event.getContentIfNotHandled();
            if (userId != null) {
                reportsViewModel.removeReportsForUser(userId);
            }
        });
        usersViewModel.getReportsResyncNeeded().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                reportsViewModel.loadReportedReviews();
            }
        });
        reportsViewModel.getStatsResyncNeeded().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                statsViewModel.loadStats();
            }
        });
        statsViewModel.getStatsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<AdminStatsResponse> success) {
                AdminStatsResponse stats = success.getData();

                String reportsBadge = getString(R.string.admin_badge_reports, stats.reportedReviews());
                setTabCount(AdminPagerAdapter.TAB_REPORTS, reportsBadge);
                if (activeTab == AdminPagerAdapter.TAB_REPORTS) {
                    tvBadge.setText(reportsBadge);
                }

                String usersBadge = getString(R.string.admin_badge_users, stats.users());
                setTabCount(AdminPagerAdapter.TAB_USERS, usersBadge);
                if (activeTab == AdminPagerAdapter.TAB_USERS) {
                    tvBadge.setText(usersBadge);
                }
            }
        });
        tagsViewModel.getTagGroupsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<DuplicateTagGroupResponse>> success) {
                String badge = getString(R.string.admin_badge_tags, success.getData().size());
                setTabCount(AdminPagerAdapter.TAB_TAGS, badge);
                if (activeTab == AdminPagerAdapter.TAB_TAGS) {
                    tvBadge.setText(badge);
                }
            }
        });
        unitsViewModel.getUnitsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<com.dtos.response.unit.UnitResponse>> success) {
                String badge = getString(R.string.admin_badge_units, success.getData().size());
                setTabCount(AdminPagerAdapter.TAB_UNITS, badge);
                if (activeTab == AdminPagerAdapter.TAB_UNITS) {
                    tvBadge.setText(badge);
                }
            }
        });
        updateHeader();
    }

    private void setTabCount(int position, String count) {
        TabLayout.Tab tab = tabLayout.getTabAt(position);
        if (tab == null || tab.getCustomView() == null) return;
        TextView countView = tab.getCustomView().findViewById(R.id.tv_admin_tab_count);
        countView.setText(count);
    }

    private void updateHeader() {
        switch (activeTab) {
            case AdminPagerAdapter.TAB_TAGS -> tvHeading.setText(R.string.admin_heading_tags);
            case AdminPagerAdapter.TAB_USERS -> tvHeading.setText(R.string.admin_heading_users);
            case AdminPagerAdapter.TAB_UNITS -> tvHeading.setText(R.string.admin_heading_units);
            default -> tvHeading.setText(R.string.admin_heading_reports);
        }
        TabLayout.Tab tab = tabLayout.getTabAt(activeTab);
        if (tab != null && tab.getCustomView() != null) {
            TextView countView = tab.getCustomView().findViewById(R.id.tv_admin_tab_count);
            tvBadge.setText(countView.getText());
        }
    }
}
