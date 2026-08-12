package com.cooksync.app.ui.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.cooksync.app.data.repository.AdminRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.testutil.ApiResultAnswers;
import com.dtos.response.admin.AdminStatsResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit tests for {@link AdminStatsViewModel}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class AdminStatsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AdminRepository adminRepository;
    private AdminStatsViewModel viewModel;

    @Before
    public void setUp() {
        adminRepository = mock(AdminRepository.class);
        viewModel = new AdminStatsViewModel(adminRepository);
    }

    @Test
    public void loadStats_publishesSuccess() {
        AdminStatsResponse stats = new AdminStatsResponse(4, 120, 300, 45, 60);
        doAnswer(ApiResultAnswers.success(stats)).when(adminRepository).getStats(any());

        viewModel.loadStats();

        ApiResult<AdminStatsResponse> result = viewModel.getStatsResult().getValue();
        assertTrue(result instanceof ApiResult.Success<AdminStatsResponse>);
        assertEquals(stats, ((ApiResult.Success<AdminStatsResponse>) result).getData());
    }
}
