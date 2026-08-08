package com.cooksync.app.ui.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AdminRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.common.BaseViewModel;
import com.cooksync.app.util.PendingActionScheduler;
import com.dtos.response.PagedResponse;
import com.dtos.response.admin.AdminStatsResponse;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.dtos.response.admin.ReportedReviewResponse;
import com.dtos.response.user.UserResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the data state for {@link AdminConsoleActivity} and its three tabs: dashboard
 * stats, the reported-reviews moderation queue, duplicate-tag consolidation, and the
 * paginated user directory. Shared across all three tabs via an activity-scoped
 * {@link androidx.lifecycle.ViewModelProvider} so switching tabs never re-fetches data
 * already loaded.
 *
 * <p>Every moderator action (remove/keep a report, suspend a reviewer, merge tags, suspend/
 * reactivate a user) follows the app's "act now, send later" undo pattern (see
 * {@link com.cooksync.app.ui.home.HomeViewModel#toggleFavorite}): the visible effect happens
 * immediately, the real network call is deferred by {@link #UNDO_WINDOW_MS} via
 * {@link #pendingActions}, and a matching {@code undo*} method cancels it before it's ever
 * sent. The hosting fragment is responsible for showing the undo-capable toast and wiring its
 * "Undo" tap to the matching {@code undo*} call.</p>
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 07/08/2026
 */
public class AdminViewModel extends BaseViewModel {

    /** Reason-filter value meaning "no filter", matching the design's "All" chip. */
    public static final String REASON_ALL = "ALL";

    private static final int USERS_PAGE_SIZE = 20;
    private static final int REPORTS_PAGE_SIZE = 20;
    private static final int TAG_GROUPS_PAGE_SIZE = 20;

    /** Matches {@code HomeViewModel}'s undo window / {@code OrganicToast}'s auto-dismiss duration. */
    private static final long UNDO_WINDOW_MS = 3200;

    private final AdminRepository adminRepository;
    private final RecipeRepository recipeRepository;
    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private final MutableLiveData<ApiResult<AdminStatsResponse>> statsResult = new MutableLiveData<>();

    private final MutableLiveData<List<ReportedReviewResponse>> filteredReports = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Event<ApiResult<Void>>> reportActionResult = new MutableLiveData<>();
    private final List<ReportedReviewResponse> allReports = new ArrayList<>();
    private String reasonFilter = REASON_ALL;
    private int reportsPage = 0;
    private boolean reportsLastPage = false;

    private final MutableLiveData<ApiResult<List<DuplicateTagGroupResponse>>> tagGroupsResult = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiResult<Void>>> tagMergeResult = new MutableLiveData<>();
    private final List<DuplicateTagGroupResponse> allTagGroups = new ArrayList<>();
    private int tagGroupsPage = 0;
    private boolean tagGroupsLastPage = false;

    private final MutableLiveData<ApiResult<List<UserResponse>>> usersResult = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiResult<Void>>> userActionResult = new MutableLiveData<>();
    private final List<UserResponse> currentUsers = new ArrayList<>();
    private int usersPage = 0;
    private boolean usersLastPage = false;
    private long usersTotalElements = 0;
    private String usersQuery = null;
    private Boolean usersEnabledFilter = null;
    private String usersSortDirection = "desc";

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.common.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param adminRepository the repository used for every admin-only endpoint
     * @param recipeRepository the repository reused for {@code deleteReview}, since removing a
     *                         reported review deletes the same entity a recipe-owner would
     */
    public AdminViewModel(AdminRepository adminRepository, RecipeRepository recipeRepository) {
        this.adminRepository = adminRepository;
        this.recipeRepository = recipeRepository;
    }

    public LiveData<ApiResult<AdminStatsResponse>> getStatsResult() { return statsResult; }
    public LiveData<List<ReportedReviewResponse>> getFilteredReports() { return filteredReports; }
    public LiveData<Event<ApiResult<Void>>> getReportActionResult() { return reportActionResult; }
    public LiveData<ApiResult<List<DuplicateTagGroupResponse>>> getTagGroupsResult() { return tagGroupsResult; }
    public LiveData<Event<ApiResult<Void>>> getTagMergeResult() { return tagMergeResult; }
    public LiveData<ApiResult<List<UserResponse>>> getUsersResult() { return usersResult; }
    public LiveData<Event<ApiResult<Void>>> getUserActionResult() { return userActionResult; }
    public String getReasonFilter() { return reasonFilter; }
    public boolean isUsersLastPage() { return usersLastPage; }
    public long getUsersTotalElements() { return usersTotalElements; }

    /** Fetches the moderation/content stats shown in the header badge and Reports stat card. */
    public void loadStats() {
        adminRepository.getStats(statsResult);
    }

    /** Resets pagination and reloads the reported-reviews queue from the first page. */
    public void loadReportedReviews() {
        reportsPage = 0;
        reportsLastPage = false;
        allReports.clear();
        fetchReportsPage();
    }

    /** Fetches the next page of reported reviews, a no-op if the last page is loaded or a fetch is in flight. */
    public void loadNextReportsPage() {
        if (reportsLastPage) {
            return;
        }
        reportsPage++;
        fetchReportsPage();
    }

    private void fetchReportsPage() {
        MutableLiveData<ApiResult<PagedResponse<ReportedReviewResponse>>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<PagedResponse<ReportedReviewResponse>> success) {
                PagedResponse<ReportedReviewResponse> page = success.getData();
                allReports.addAll(page.content());
                reportsLastPage = page.last();
                applyReasonFilter();
            } else if (apiResult instanceof ApiResult.Error<PagedResponse<ReportedReviewResponse>> error) {
                reportActionResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
            }
        });
        adminRepository.getReportedReviews(reportsPage, REPORTS_PAGE_SIZE, result);
    }

    /**
     * Counts how many queued reports match each report reason, for the filter chips' live
     * counts.
     *
     * @return an insertion-ordered map from reason (e.g. "SPAM") to matching report count
     */
    public Map<String, Long> getReasonCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReportedReviewResponse report : allReports) {
            String reason = report.reason();
            if (reason == null) continue;
            counts.merge(reason, 1L, Long::sum);
        }
        return counts;
    }

    /**
     * Selects which reason the report queue is filtered by and re-applies it over the
     * already-loaded queue (no network call — filtering is client-side).
     *
     * @param reason one of the {@code ReportReviewRequestDTO} reason codes, or
     *               {@link #REASON_ALL} to clear the filter
     */
    public void setReasonFilter(String reason) {
        this.reasonFilter = reason;
        applyReasonFilter();
    }

    private void applyReasonFilter() {
        if (REASON_ALL.equals(reasonFilter)) {
            filteredReports.postValue(new ArrayList<>(allReports));
        } else {
            List<ReportedReviewResponse> filtered = new ArrayList<>();
            for (ReportedReviewResponse report : allReports) {
                if (reasonFilter.equals(report.reason())) {
                    filtered.add(report);
                }
            }
            filteredReports.postValue(filtered);
        }
    }

    /**
     * The "Remove" action: hides the report immediately, then — unless undone within
     * {@link #UNDO_WINDOW_MS} — deletes the underlying review. Deleting the review already
     * takes it out of the reported-reviews query server-side, so no separate dismiss call is
     * needed (and none is made — a dismiss on an already-deleted review 404s).
     *
     * @param report the queued report to remove
     */
    public void removeReport(ReportedReviewResponse report) {
        allReports.remove(report);
        applyReasonFilter();
        pendingActions.schedule(removeReportKey(report), UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> deleteResult = new MutableLiveData<>();
            observeOnce(deleteResult, result -> {
                if (result instanceof ApiResult.Error<Void> error) {
                    restoreReport(report);
                    reportActionResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
                }
            });
            recipeRepository.deleteReview(report.id(), deleteResult);
        });
    }

    /**
     * Cancels a still-pending {@link #removeReport} before it reaches the server, restoring
     * the report to the queue. Does nothing if the undo window already elapsed.
     *
     * @param report the report whose removal should be undone
     */
    public void undoRemoveReport(ReportedReviewResponse report) {
        if (pendingActions.cancel(removeReportKey(report))) {
            restoreReport(report);
        }
    }

    /**
     * The "Keep" action: hides the report from the queue immediately, then — unless undone —
     * dismisses it server-side without touching the underlying review.
     *
     * @param report the queued report to keep
     */
    public void keepReport(ReportedReviewResponse report) {
        allReports.remove(report);
        applyReasonFilter();
        pendingActions.schedule(keepReportKey(report), UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
            observeOnce(result, apiResult -> {
                if (apiResult instanceof ApiResult.Error<Void> error) {
                    restoreReport(report);
                    reportActionResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
                }
            });
            adminRepository.dismissReport(report.id(), result);
        });
    }

    /**
     * Cancels a still-pending {@link #keepReport} before it reaches the server, restoring the
     * report to the queue. Does nothing if the undo window already elapsed.
     *
     * @param report the report whose dismissal should be undone
     */
    public void undoKeepReport(ReportedReviewResponse report) {
        if (pendingActions.cancel(keepReportKey(report))) {
            restoreReport(report);
        }
    }

    private void restoreReport(ReportedReviewResponse report) {
        if (!allReports.contains(report)) {
            allReports.add(report);
        }
        applyReasonFilter();
    }

    private String removeReportKey(ReportedReviewResponse report) { return "remove-report-" + report.id(); }
    private String keepReportKey(ReportedReviewResponse report) { return "keep-report-" + report.id(); }

    /**
     * The report card's ban-user icon action: suspends the reviewer's account, unless undone
     * within {@link #UNDO_WINDOW_MS}.
     *
     * @param report the queued report whose author should be suspended
     */
    public void banReporter(ReportedReviewResponse report) {
        pendingActions.schedule(banReporterKey(report), UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
            observeOnce(result, apiResult -> {
                if (apiResult instanceof ApiResult.Error<Void> error) {
                    reportActionResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
                }
            });
            adminRepository.disableUser(report.reviewerId(), result);
        });
    }

    /**
     * Cancels a still-pending {@link #banReporter} before it reaches the server. Does nothing
     * if the undo window already elapsed.
     *
     * @param report the report whose author-suspension should be undone
     */
    public void undoBanReporter(ReportedReviewResponse report) {
        pendingActions.cancel(banReporterKey(report));
    }

    private String banReporterKey(ReportedReviewResponse report) { return "ban-reporter-" + report.reviewerId(); }

    /** Resets pagination and reloads the duplicate tag groups from the first page. */
    public void loadDuplicateTagGroups() {
        tagGroupsPage = 0;
        tagGroupsLastPage = false;
        allTagGroups.clear();
        fetchTagGroupsPage();
    }

    /** Fetches the next page of duplicate tag groups, a no-op if the last page is loaded. */
    public void loadNextTagGroupsPage() {
        if (tagGroupsLastPage) {
            return;
        }
        tagGroupsPage++;
        fetchTagGroupsPage();
    }

    private void fetchTagGroupsPage() {
        MutableLiveData<ApiResult<PagedResponse<DuplicateTagGroupResponse>>> result = new MutableLiveData<>();
        tagGroupsResult.setValue(new ApiResult.Loading<>());
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<PagedResponse<DuplicateTagGroupResponse>> success) {
                PagedResponse<DuplicateTagGroupResponse> page = success.getData();
                allTagGroups.addAll(page.content());
                tagGroupsLastPage = page.last();
                tagGroupsResult.postValue(new ApiResult.Success<>(new ArrayList<>(allTagGroups)));
            } else if (apiResult instanceof ApiResult.Error<PagedResponse<DuplicateTagGroupResponse>> error) {
                tagGroupsResult.postValue(new ApiResult.Error<>(error.getMessage(), error.getCause()));
            }
        });
        adminRepository.getDuplicateTagGroups(tagGroupsPage, TAG_GROUPS_PAGE_SIZE, result);
    }

    /**
     * Merges every other variant in a duplicate group into the chosen canonical tag: the group
     * disappears from the list immediately, then — unless undone within {@link #UNDO_WINDOW_MS}
     * — each variant is merged with its own {@code POST /tags/merge} call, one at a time (the
     * server endpoint only accepts a single source/target pair).
     *
     * @param group the duplicate group being resolved, for optimistic list removal/restoration
     * @param groupVariantIds every tag id in the duplicate group, including {@code keepTagId}
     * @param keepTagId the canonical tag id chosen to survive the merge
     */
    public void mergeGroup(DuplicateTagGroupResponse group, List<String> groupVariantIds, String keepTagId) {
        allTagGroups.remove(group);
        tagGroupsResult.postValue(new ApiResult.Success<>(new ArrayList<>(allTagGroups)));

        List<String> sourceIds = new ArrayList<>();
        for (String id : groupVariantIds) {
            if (!id.equals(keepTagId)) {
                sourceIds.add(id);
            }
        }
        pendingActions.schedule(mergeGroupKey(group), UNDO_WINDOW_MS,
                () -> mergeNextInGroup(group, sourceIds, keepTagId, 0));
    }

    /**
     * Cancels a still-pending {@link #mergeGroup} before it reaches the server, restoring the
     * group to the duplicates list. Does nothing if the undo window already elapsed.
     *
     * @param group the group whose merge should be undone
     */
    public void undoMergeGroup(DuplicateTagGroupResponse group) {
        if (pendingActions.cancel(mergeGroupKey(group))) {
            restoreTagGroup(group);
        }
    }

    private void restoreTagGroup(DuplicateTagGroupResponse group) {
        if (!allTagGroups.contains(group)) {
            allTagGroups.add(group);
        }
        tagGroupsResult.postValue(new ApiResult.Success<>(new ArrayList<>(allTagGroups)));
    }

    private String mergeGroupKey(DuplicateTagGroupResponse group) { return "merge-group-" + group.normalizedName(); }

    private void mergeNextInGroup(DuplicateTagGroupResponse group, List<String> sourceIds, String targetTagId, int index) {
        if (index >= sourceIds.size()) {
            tagMergeResult.postValue(new Event<>(new ApiResult.Success<>(null)));
            return;
        }
        MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success) {
                mergeNextInGroup(group, sourceIds, targetTagId, index + 1);
            } else if (apiResult instanceof ApiResult.Error<Void> error) {
                restoreTagGroup(group);
                tagMergeResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
            }
        });
        adminRepository.mergeTags(sourceIds.get(index), targetTagId, result);
    }

    /**
     * Resets pagination and reloads the Users tab from the first page. Called whenever the
     * search text or enabled-filter chip changes.
     *
     * @param query search text over name/email, or {@code null}/blank for none
     * @param enabled the enabled-filter chip's value, or {@code null} for "All"
     */
    public void refreshUsers(String query, Boolean enabled) {
        this.usersQuery = (query == null || query.isBlank()) ? null : query.trim();
        this.usersEnabledFilter = enabled;
        this.usersPage = 0;
        this.usersLastPage = false;
        this.currentUsers.clear();
        fetchUsersPage();
    }

    /** Fetches the next page of users, a no-op if the last page is already loaded or a fetch is in flight. */
    public void loadNextUsersPage() {
        if (usersLastPage || usersResult.getValue() instanceof ApiResult.Loading) {
            return;
        }
        usersPage++;
        fetchUsersPage();
    }

    /** Flips the join-date sort direction and reloads the Users tab from the first page. */
    public void toggleUsersSortDirection() {
        usersSortDirection = "desc".equals(usersSortDirection) ? "asc" : "desc";
        usersPage = 0;
        usersLastPage = false;
        currentUsers.clear();
        fetchUsersPage();
    }

    private void fetchUsersPage() {
        MutableLiveData<ApiResult<PagedResponse<UserResponse>>> result = new MutableLiveData<>();
        usersResult.setValue(new ApiResult.Loading<>());
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<PagedResponse<UserResponse>> success) {
                PagedResponse<UserResponse> page = success.getData();
                currentUsers.addAll(page.content());
                usersLastPage = page.last();
                usersTotalElements = page.totalElements();
                usersResult.postValue(new ApiResult.Success<>(new ArrayList<>(currentUsers)));
            } else if (apiResult instanceof ApiResult.Error<PagedResponse<UserResponse>> error) {
                usersResult.postValue(new ApiResult.Error<>(error.getMessage(), error.getCause()));
            }
        });
        adminRepository.getUsers(usersPage, USERS_PAGE_SIZE, usersQuery, usersEnabledFilter,
                "createdAt", usersSortDirection, result);
    }

    /**
     * Enables or disables a user account: the row updates immediately, then — unless undone
     * within {@link #UNDO_WINDOW_MS} — the real enable/disable call is sent.
     *
     * @param user the user row being toggled, captured before the optimistic change so
     *             {@link #undoSetUserEnabled} can restore its exact prior state
     * @param enabled the new enabled state to apply
     */
    public void setUserEnabled(UserResponse user, boolean enabled) {
        patchUserEnabled(user.id(), enabled, enabled ? "ACTIVE" : "SUSPENDED");
        pendingActions.schedule(userStatusKey(user), UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
            observeOnce(result, apiResult -> {
                if (apiResult instanceof ApiResult.Error<Void> error) {
                    patchUserEnabled(user.id(), user.enabled(), user.status());
                    userActionResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
                }
            });
            if (enabled) {
                adminRepository.enableUser(user.id(), result);
            } else {
                adminRepository.disableUser(user.id(), result);
            }
        });
    }

    /**
     * Cancels a still-pending {@link #setUserEnabled} before it reaches the server, restoring
     * the row to its state prior to the toggle. Does nothing if the undo window already
     * elapsed.
     *
     * @param user the user row as it was <em>before</em> the toggle (its own {@code enabled}/
     *             {@code status} are the values to restore)
     */
    public void undoSetUserEnabled(UserResponse user) {
        if (pendingActions.cancel(userStatusKey(user))) {
            patchUserEnabled(user.id(), user.enabled(), user.status());
        }
    }

    private String userStatusKey(UserResponse user) { return "user-status-" + user.id(); }

    private void patchUserEnabled(String userId, boolean enabled, String status) {
        for (int i = 0; i < currentUsers.size(); i++) {
            UserResponse u = currentUsers.get(i);
            if (u.id().equals(userId)) {
                currentUsers.set(i, new UserResponse(u.id(), u.firstName(), u.lastName(), u.email(),
                        u.isAdmin(), u.avatarUrl(), u.createdAt(), u.updatedAt(), enabled, status,
                        u.city(), u.bio(), u.showRecipesPublicly(), u.showFavoritesPublicly()));
                break;
            }
        }
        usersResult.postValue(new ApiResult.Success<>(new ArrayList<>(currentUsers)));
    }

    /**
     * Flushes any still-pending actions immediately rather than dropping them, so navigating
     * away before an undo window elapses doesn't silently discard the action.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        pendingActions.flushAll();
    }
}
