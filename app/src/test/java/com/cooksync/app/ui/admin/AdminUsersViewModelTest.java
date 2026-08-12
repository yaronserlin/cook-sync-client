package com.cooksync.app.ui.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.cooksync.app.domain.Event;
import com.cooksync.app.testutil.ApiResultAnswers;
import com.dtos.response.PagedResponse;
import com.dtos.response.user.UserResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link AdminUsersViewModel}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class AdminUsersViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AdminRepository adminRepository;
    private AdminUsersViewModel viewModel;

    private final UserResponse activeUser = new UserResponse("u1", "Ada", "Lovelace",
            "ada@example.com", false, null, "2026-01-01", "2026-01-01", true, "ACTIVE",
            null, null, true, true);

    @Before
    public void setUp() {
        adminRepository = mock(AdminRepository.class);
        viewModel = new AdminUsersViewModel(adminRepository);
    }

    @Test
    public void refreshUsers_publishesFirstPage() {
        loadOnePageContainingActiveUser();

        ApiResult<List<UserResponse>> result = viewModel.getUsersResult().getValue();
        assertTrue(result instanceof ApiResult.Success<List<UserResponse>>);
        assertEquals(List.of(activeUser), ((ApiResult.Success<List<UserResponse>>) result).getData());
        assertEquals(1, viewModel.getUsersTotalElements());
    }

    @Test
    public void setUserEnabled_disable_appliesOptimisticPatch_andFiresUserDisabledEvent() {
        loadOnePageContainingActiveUser();

        viewModel.setUserEnabled(activeUser, false);

        UserResponse patched = firstUser();
        assertFalse(patched.enabled());
        assertEquals("SUSPENDED", patched.status());

        Event<String> event = viewModel.getUserDisabledEvent().getValue();
        assertEquals("u1", event.getContentIfNotHandled());
    }

    @Test
    public void setUserEnabled_disable_undoneBeforeFlush_restoresRow_andNeverCallsRepository() {
        loadOnePageContainingActiveUser();

        viewModel.setUserEnabled(activeUser, false);
        viewModel.undoSetUserEnabled(activeUser);

        UserResponse restored = firstUser();
        assertTrue(restored.enabled());
        assertEquals("ACTIVE", restored.status());
        verify(adminRepository, never()).disableUser(any(), any());
    }

    @Test
    public void setUserEnabled_disable_serverErrorAfterFlush_rollsBackAndSignalsResync() {
        loadOnePageContainingActiveUser();
        doAnswer(ApiResultAnswers.<Void>error("network error"))
                .when(adminRepository).disableUser(eq("u1"), any());

        viewModel.setUserEnabled(activeUser, false);
        viewModel.onCleared();

        UserResponse rolledBack = firstUser();
        assertTrue(rolledBack.enabled());
        assertEquals("ACTIVE", rolledBack.status());
        assertTrue(viewModel.getReportsResyncNeeded().getValue().getContentIfNotHandled());
    }

    private UserResponse firstUser() {
        ApiResult<List<UserResponse>> result = viewModel.getUsersResult().getValue();
        return ((ApiResult.Success<List<UserResponse>>) result).getData().get(0);
    }

    private void loadOnePageContainingActiveUser() {
        PagedResponse<UserResponse> page = new PagedResponse<>(List.of(activeUser), 0, 20, 1, 1, true);
        doAnswer(ApiResultAnswers.success(page))
                .when(adminRepository).getUsers(eq(0), eq(20), any(), any(), any(), any(), any());
        viewModel.refreshUsers(null, null);
    }
}
