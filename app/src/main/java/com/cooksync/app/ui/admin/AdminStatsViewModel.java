package com.cooksync.app.ui.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AdminRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseViewModel;
import com.dtos.response.admin.AdminStatsResponse;

/**
 * Owns the Admin Console's system-wide moderation stats ({@code GET /api/admin/stats}), used
 * by {@link AdminConsoleActivity} to drive the Reports and Users tab badge counts. This is a
 * dashboard-level concern rather than any single tab's — the same {@link AdminStatsResponse}
 * feeds two different tabs' badges — so it is not owned by {@link AdminReportsViewModel} or
 * {@link AdminUsersViewModel}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class AdminStatsViewModel extends BaseViewModel {

    private final AdminRepository adminRepository;

    private final MutableLiveData<ApiResult<AdminStatsResponse>> statsResult = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with the given repository, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param adminRepository the repository used to fetch the moderation/content stats
     */
    public AdminStatsViewModel(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    /** @return observable moderation/content stats (Loading → Success/Error) */
    public LiveData<ApiResult<AdminStatsResponse>> getStatsResult() {
        return statsResult;
    }

    /** Fetches the moderation/content stats shown in the header badge and tab badges. */
    public void loadStats() {
        adminRepository.getStats(statsResult);
    }
}
