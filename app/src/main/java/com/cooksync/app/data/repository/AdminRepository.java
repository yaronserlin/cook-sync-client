package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.domain.ApiResult;
import com.dtos.response.PagedResponse;
import com.dtos.response.admin.AdminStatsResponse;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.dtos.response.admin.ReportedReviewResponse;
import com.dtos.response.user.UserResponse;

/**
 * Interface contract for administrative moderation operations: dashboard stats, the reported
 * reviews queue, duplicate-tag consolidation, and user account management. Backs the Admin
 * Console screen, only reachable by users with {@link com.cooksync.app.util.SessionManager#isAdmin()}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public interface AdminRepository {

    /**
     * Fetches system-wide moderation/content statistics for the Admin Console header.
     *
     * @param resultTarget LiveData target to post the outcome
     */
    void getStats(MutableLiveData<ApiResult<AdminStatsResponse>> resultTarget);

    /**
     * Fetches a paginated, searchable, sortable list of every registered user.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @param q optional search text matched against name/email, or {@code null}
     * @param enabled optional filter by account status, or {@code null} for both
     * @param sortBy one of "firstName", "lastName", "email", "createdAt"
     * @param direction "asc" or "desc"
     * @param resultTarget LiveData target to post the outcome
     */
    void getUsers(int page, int size, String q, Boolean enabled, String sortBy, String direction,
                  MutableLiveData<ApiResult<PagedResponse<UserResponse>>> resultTarget);

    /**
     * Fetches a paginated page of reviews currently flagged for moderation.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @param resultTarget LiveData target to post the outcome
     */
    void getReportedReviews(int page, int size,
                             MutableLiveData<ApiResult<PagedResponse<ReportedReviewResponse>>> resultTarget);

    /**
     * Dismisses a review's report(s) without deleting the review itself.
     *
     * @param reviewId the ID of the reported review
     * @param resultTarget LiveData target to post the outcome
     */
    void dismissReport(String reviewId, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Re-enables a previously disabled user account.
     *
     * @param userId the ID of the user to enable
     * @param resultTarget LiveData target to post the outcome
     */
    void enableUser(String userId, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Disables a user account, blocking sign-in.
     *
     * @param userId the ID of the user to disable
     * @param resultTarget LiveData target to post the outcome
     */
    void disableUser(String userId, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Permanently deletes a user account and everything it owns, bypassing the normal 30-day
     * self-service deletion grace period.
     *
     * @param userId the ID of the user to permanently delete
     * @param resultTarget LiveData target to post the outcome
     */
    void deleteUser(String userId, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Fetches a paginated page of tags that appear to be duplicates of one another.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @param resultTarget LiveData target to post the outcome
     */
    void getDuplicateTagGroups(int page, int size,
                                MutableLiveData<ApiResult<PagedResponse<DuplicateTagGroupResponse>>> resultTarget);

    /**
     * Merges a duplicate tag into a canonical target tag.
     *
     * @param sourceTagId the ID of the duplicate tag to remove
     * @param targetTagId the ID of the canonical tag to keep
     * @param resultTarget LiveData target to post the outcome
     */
    void mergeTags(String sourceTagId, String targetTagId, MutableLiveData<ApiResult<Void>> resultTarget);
}
