package com.cooksync.app.ui.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AdminRepository;
import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.util.PendingActionScheduler;
import com.dtos.response.PagedResponse;
import com.dtos.response.admin.ReportedReviewResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the Admin Console's Reports tab: the paginated reported-reviews moderation queue,
 * its client-side reason filter, and the Remove/Keep/ban-reviewer actions.
 *
 * <p>Every moderator action (remove/keep a report, suspend a reviewer) follows the app's
 * "act now, send later" undo pattern (see
 * {@link com.cooksync.app.ui.home.HomeViewModel#toggleFavorite}): the visible effect happens
 * immediately, the real network call is deferred by {@link BaseRepository#UNDO_WINDOW_MS} via
 * {@link #pendingActions}, and a matching {@code undo*} method cancels it before it's ever
 * sent. The hosting fragment is responsible for showing the undo-capable toast and wiring its
 * "Undo" tap to the matching {@code undo*} call.</p>
 *
 * <p>{@link #removeReportsForUser} is also called by {@link AdminConsoleActivity} when
 * {@link AdminUsersViewModel} disables a user, so a report just filed by/against a
 * newly-suspended reviewer doesn't linger in this tab's in-memory queue — see
 * {@link AdminUsersViewModel#getUserDisabledEvent()}.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class AdminReportsViewModel extends BaseViewModel {

    /** Reason-filter value meaning "no filter", matching the design's "All" chip. */
    public static final String REASON_ALL = "ALL";

    private static final int REPORTS_PAGE_SIZE = 20;

    private final AdminRepository adminRepository;
    private final RecipeRepository recipeRepository;
    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private final MutableLiveData<List<ReportedReviewResponse>> filteredReports = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Event<ApiResult<Void>>> reportActionResult = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> statsResyncNeeded = new MutableLiveData<>();
    private final List<ReportedReviewResponse> allReports = new ArrayList<>();
    private String reasonFilter = REASON_ALL;
    private int reportsPage = 0;
    private boolean reportsLastPage = false;

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param adminRepository the repository used for every admin-only endpoint
     * @param recipeRepository the repository reused for {@code deleteReview}, since removing a
     *                         reported review deletes the same entity a recipe-owner would
     */
    public AdminReportsViewModel(AdminRepository adminRepository, RecipeRepository recipeRepository) {
        this.adminRepository = adminRepository;
        this.recipeRepository = recipeRepository;
    }


    public LiveData<List<ReportedReviewResponse>> getFilteredReports() { return filteredReports; }
    public LiveData<Event<ApiResult<Void>>> getReportActionResult() { return reportActionResult; }
    public String getReasonFilter() { return reasonFilter; }

    /**
     * Fires once a remove/keep/ban action actually reaches the server (not at the moment of
     * its optimistic UI change), signalling that {@link AdminStatsViewModel#loadStats()} should
     * re-run so the "N open" reports badge — sourced from {@code AdminStatsResponse}, not this
     * tab's own list — reflects the new count. Deliberately tied to settlement rather than the
     * optimistic change so the badge doesn't drift ahead of the server if the action is undone.
     *
     * @return a one-shot event stream (payload is unused; only occurrence matters)
     */
    public LiveData<Event<Boolean>> getStatsResyncNeeded() { return statsResyncNeeded; }

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
        switch (reasonFilter) {
            case REASON_ALL -> filteredReports.postValue(new ArrayList<>(allReports));
            default -> {
                List<ReportedReviewResponse> filtered = new ArrayList<>();
                for (ReportedReviewResponse report : allReports) {
                    if (Objects.equals(reasonFilter, report.reason())) {
                        filtered.add(report);
                    }
                }
                filteredReports.postValue(filtered);
            }
        }
    }

    /**
     * The "Remove" action: hides the report immediately, then — unless undone within
     * {@link BaseRepository#UNDO_WINDOW_MS} — deletes the underlying review. Deleting the review already
     * takes it out of the reported-reviews query server-side, so no separate dismiss call is
     * needed (and none is made — a dismiss on an already-deleted review 404s).
     *
     * @param report the queued report to remove
     */
    public void removeReport(ReportedReviewResponse report) {
        allReports.remove(report);
        applyReasonFilter();
        pendingActions.schedule(removeReportKey(report), BaseRepository.UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> deleteResult = new MutableLiveData<>();
            observeOnce(deleteResult, result -> {
                if (result instanceof ApiResult.Error<Void> error) {
                    restoreReport(report);
                    reportActionResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
                } else if (result instanceof ApiResult.Success) {
                    statsResyncNeeded.postValue(new Event<>(true));
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
        pendingActions.schedule(keepReportKey(report), BaseRepository.UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
            observeOnce(result, apiResult -> {
                if (apiResult instanceof ApiResult.Error<Void> error) {
                    restoreReport(report);
                    reportActionResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
                } else if (apiResult instanceof ApiResult.Success) {
                    statsResyncNeeded.postValue(new Event<>(true));
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
     * within {@link BaseRepository#UNDO_WINDOW_MS}.
     *
     * @param report the queued report whose author should be suspended
     */
    public void banReporter(ReportedReviewResponse report) {
        removeReportsForUser(report.reviewerId());
        pendingActions.schedule(banReporterKey(report), BaseRepository.UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
            observeOnce(result, apiResult -> {
                if (apiResult instanceof ApiResult.Error<Void> error) {
                    loadReportedReviews();
                    reportActionResult.postValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
                } else if (apiResult instanceof ApiResult.Success) {
                    statsResyncNeeded.postValue(new Event<>(true));
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

    /**
     * Removes every queued report authored by the given user from the in-memory queue and
     * re-applies the current reason filter. Called both by {@link #banReporter} (the report
     * that triggered the ban, plus any other reports from the same reviewer) and, via
     * {@link AdminConsoleActivity}, whenever {@link AdminUsersViewModel} disables a user from
     * the Users tab.
     *
     * Complexity:
     * Time: O(n) where n is the number of currently loaded reports
     * Space: O(n)
     *
     * @param userId id of the reviewer whose reports should be hidden, a no-op if {@code null}
     */
    public void removeReportsForUser(String userId) {
        if (userId == null) return;
        allReports.removeIf(r -> userId.equals(r.reviewerId()));
        applyReasonFilter();
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
