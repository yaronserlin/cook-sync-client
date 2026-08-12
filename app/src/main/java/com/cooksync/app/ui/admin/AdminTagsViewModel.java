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
import com.dtos.response.admin.DuplicateTagGroupResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages the Admin Console's Tags tab: the paginated duplicate-tag groups and the merge
 * flow that consolidates every variant in a group into one canonical tag.
 *
 * <p>{@link #mergeGroup} follows the app's "act now, send later" undo pattern (see
 * {@link com.cooksync.app.ui.home.HomeViewModel#toggleFavorite}): the group disappears from
 * the list immediately, then — unless undone within {@link BaseRepository#UNDO_WINDOW_MS} via
 * {@link #pendingActions} — each variant is merged with its own {@code POST /tags/merge}
 * call.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class AdminTagsViewModel extends BaseViewModel {

    private static final int TAG_GROUPS_PAGE_SIZE = 20;

    private final AdminRepository adminRepository;
    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    private final MutableLiveData<ApiResult<List<DuplicateTagGroupResponse>>> tagGroupsResult = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiResult<Void>>> tagMergeResult = new MutableLiveData<>();
    private final List<DuplicateTagGroupResponse> allTagGroups = new ArrayList<>();
    private int tagGroupsPage = 0;
    private boolean tagGroupsLastPage = false;

    /**
     * Constructs the ViewModel with the given repository, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param adminRepository the repository used for the duplicate-tags list and merge calls
     */
    public AdminTagsViewModel(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public LiveData<ApiResult<List<DuplicateTagGroupResponse>>> getTagGroupsResult() { return tagGroupsResult; }
    public LiveData<Event<ApiResult<Void>>> getTagMergeResult() { return tagMergeResult; }

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
     * disappears from the list immediately, then — unless undone within
     * {@link BaseRepository#UNDO_WINDOW_MS} — each variant is merged with its own
     * {@code POST /tags/merge} call, one at a time (the server endpoint only accepts a single
     * source/target pair).
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
            if (!Objects.equals(id, keepTagId)) {
                sourceIds.add(id);
            }
        }
        pendingActions.schedule(mergeGroupKey(group), BaseRepository.UNDO_WINDOW_MS,
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
     * Flushes any still-pending merge immediately rather than dropping it, so navigating away
     * before the undo window elapses doesn't silently discard it.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        pendingActions.flushAll();
    }
}
