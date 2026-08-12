package com.cooksync.app.ui.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AdminRepository;
import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.util.PendingActionScheduler;
import com.dtos.response.PagedResponse;
import com.dtos.response.user.UserResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages the Admin Console's Users tab: the paginated, searchable, sortable user directory
 * and the enable/disable moderation action.
 *
 * <p>{@link #setUserEnabled} follows the app's "act now, send later" undo pattern (see
 * {@link com.cooksync.app.ui.home.HomeViewModel#toggleFavorite}): the row updates immediately,
 * then — unless undone within {@link BaseRepository#UNDO_WINDOW_MS} via
 * {@link #pendingActions} — the real enable/disable call is sent.</p>
 *
 * <p>Suspending a user also has a side effect on the Reports tab (a just-suspended reviewer's
 * queued reports should stop showing up there), which this class does not own directly.
 * Instead it exposes {@link #getUserDisabledEvent()} and {@link #getReportsResyncNeeded()};
 * {@link AdminConsoleActivity} observes both and relays them to {@link AdminReportsViewModel},
 * keeping the two tabs' ViewModels independent and unit-testable.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class AdminUsersViewModel extends BaseViewModel {

    private static final int USERS_PAGE_SIZE = 20;

    private final AdminRepository adminRepository;
    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private final MutableLiveData<ApiResult<List<UserResponse>>> usersResult = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiResult<Void>>> userActionResult = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> userDisabledEvent = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> reportsResyncNeeded = new MutableLiveData<>();
    private final List<UserResponse> currentUsers = new ArrayList<>();
    private int usersPage = 0;
    private boolean usersLastPage = false;
    private long usersTotalElements = 0;
    private String usersQuery = null;
    private Boolean usersEnabledFilter = null;
    private String usersSortDirection = "desc";

    /**
     * Constructs the ViewModel with the given repository, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param adminRepository the repository used for the user directory and enable/disable calls
     */
    public AdminUsersViewModel(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public LiveData<ApiResult<List<UserResponse>>> getUsersResult() { return usersResult; }
    public LiveData<Event<ApiResult<Void>>> getUserActionResult() { return userActionResult; }
    public long getUsersTotalElements() { return usersTotalElements; }

    /**
     * Fires with the id of a user that was just optimistically disabled, so an observer can
     * hide their queued reports elsewhere in the console. See the class-level note on the
     * Reports-tab side effect.
     *
     * @return a one-shot event stream of disabled user ids
     */
    public LiveData<Event<String>> getUserDisabledEvent() { return userDisabledEvent; }

    /**
     * Fires when a {@link #setUserEnabled} call fails server-side after already having
     * optimistically hidden the affected user's reports, signalling that the Reports tab
     * should do a full reload to reconcile its state.
     *
     * @return a one-shot event stream (payload is unused; only occurrence matters)
     */
    public LiveData<Event<Boolean>> getReportsResyncNeeded() { return reportsResyncNeeded; }

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
     * within {@link BaseRepository#UNDO_WINDOW_MS} — the real enable/disable call is sent.
     * Disabling additionally fires {@link #getUserDisabledEvent()} so the Reports tab can hide
     * this user's queued reports.
     *
     * @param user the user row being toggled, captured before the optimistic change so
     *             {@link #undoSetUserEnabled} can restore its exact prior state
     * @param enabled the new enabled state to apply
     */
    public void setUserEnabled(UserResponse user, boolean enabled) {
        patchUserEnabled(user.id(), enabled, enabled ? "ACTIVE" : "SUSPENDED");
        if (!enabled) {
            userDisabledEvent.postValue(new Event<>(user.id()));
        }
        pendingActions.schedule(userStatusKey(user), BaseRepository.UNDO_WINDOW_MS, () -> {
            MutableLiveData<ApiResult<Void>> result = new MutableLiveData<>();
            observeOnce(result, apiResult -> {
                if (apiResult instanceof ApiResult.Error<Void> error) {
                    patchUserEnabled(user.id(), user.enabled(), user.status());
                    reportsResyncNeeded.postValue(new Event<>(true));
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
            if (Objects.equals(u.id(), userId)) {
                currentUsers.set(i, new UserResponse(u.id(), u.firstName(), u.lastName(), u.email(),
                        u.isAdmin(), u.avatarUrl(), u.createdAt(), u.updatedAt(), enabled, status,
                        u.city(), u.bio(), u.showRecipesPublicly(), u.showFavoritesPublicly()));
                break;
            }
        }
        usersResult.postValue(new ApiResult.Success<>(new ArrayList<>(currentUsers)));
    }

    /**
     * Flushes any still-pending action immediately rather than dropping it, so navigating away
     * before the undo window elapses doesn't silently discard it.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        pendingActions.flushAll();
    }
}
