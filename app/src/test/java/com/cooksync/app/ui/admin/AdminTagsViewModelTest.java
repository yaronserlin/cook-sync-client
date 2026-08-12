package com.cooksync.app.ui.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.cooksync.app.data.repository.AdminRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.testutil.ApiResultAnswers;
import com.dtos.response.PagedResponse;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.dtos.response.admin.TagVariantResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link AdminTagsViewModel}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class AdminTagsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AdminRepository adminRepository;
    private AdminTagsViewModel viewModel;

    private final TagVariantResponse keptVariant = new TagVariantResponse("t1", "Tomato", 5);
    private final TagVariantResponse duplicateVariant = new TagVariantResponse("t2", "tomatoes", 2);
    private final DuplicateTagGroupResponse group =
            new DuplicateTagGroupResponse("tomato", List.of(keptVariant, duplicateVariant));

    @Before
    public void setUp() {
        adminRepository = mock(AdminRepository.class);
        viewModel = new AdminTagsViewModel(adminRepository);
    }

    @Test
    public void loadDuplicateTagGroups_publishesFirstPage() {
        PagedResponse<DuplicateTagGroupResponse> page =
                new PagedResponse<>(List.of(group), 0, 20, 1, 1, true);
        doAnswer(ApiResultAnswers.success(page))
                .when(adminRepository).getDuplicateTagGroups(eq(0), eq(20), any());

        viewModel.loadDuplicateTagGroups();

        ApiResult<List<DuplicateTagGroupResponse>> result = viewModel.getTagGroupsResult().getValue();
        assertTrue(result instanceof ApiResult.Success<List<DuplicateTagGroupResponse>>);
        assertEquals(List.of(group), ((ApiResult.Success<List<DuplicateTagGroupResponse>>) result).getData());
    }

    @Test
    public void mergeGroup_removesGroupFromListImmediately() {
        loadOnePageContainingGroup();

        viewModel.mergeGroup(group, List.of("t1", "t2"), "t1");

        ApiResult<List<DuplicateTagGroupResponse>> result = viewModel.getTagGroupsResult().getValue();
        assertTrue(((ApiResult.Success<List<DuplicateTagGroupResponse>>) result).getData().isEmpty());
    }

    @Test
    public void mergeGroup_undoneBeforeFlush_restoresGroup_andNeverCallsMergeTags() {
        loadOnePageContainingGroup();

        viewModel.mergeGroup(group, List.of("t1", "t2"), "t1");
        viewModel.undoMergeGroup(group);

        ApiResult<List<DuplicateTagGroupResponse>> result = viewModel.getTagGroupsResult().getValue();
        assertEquals(List.of(group), ((ApiResult.Success<List<DuplicateTagGroupResponse>>) result).getData());
        verify(adminRepository, never()).mergeTags(any(), any(), any());
    }

    @Test
    public void mergeGroup_flushedWithoutUndo_mergesEveryNonKeptVariantIntoKeptTag() {
        loadOnePageContainingGroup();
        doAnswer(ApiResultAnswers.success((Void) null))
                .when(adminRepository).mergeTags(eq("t2"), eq("t1"), any());

        viewModel.mergeGroup(group, List.of("t1", "t2"), "t1");
        viewModel.onCleared();

        verify(adminRepository).mergeTags(eq("t2"), eq("t1"), any());
        ApiResult<Void> mergeResult = ((ApiResult.Success<Void>) viewModel.getTagMergeResult().getValue().getContentIfNotHandled());
        assertTrue(mergeResult instanceof ApiResult.Success<Void>);
    }

    private void loadOnePageContainingGroup() {
        PagedResponse<DuplicateTagGroupResponse> page =
                new PagedResponse<>(List.of(group), 0, 20, 1, 1, true);
        doAnswer(ApiResultAnswers.success(page))
                .when(adminRepository).getDuplicateTagGroups(eq(0), eq(20), any());
        viewModel.loadDuplicateTagGroups();
    }
}
