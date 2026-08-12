package com.cooksync.app.ui.admin;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.cooksync.app.data.repository.UnitRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.testutil.ApiResultAnswers;
import com.dtos.request.unit.UnitRequestDTO;
import com.dtos.response.unit.UnitResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for {@link AdminUnitsViewModel}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class AdminUnitsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private UnitRepository unitRepository;
    private AdminUnitsViewModel viewModel;

    @Before
    public void setUp() {
        unitRepository = mock(UnitRepository.class);
        viewModel = new AdminUnitsViewModel(unitRepository);
    }

    @Test
    public void loadUnits_publishesSuccess() {
        List<UnitResponse> units = List.of(
                new UnitResponse("u1", "kg", "Kilogram", "2026-01-01", "2026-01-01"));
        doAnswer(ApiResultAnswers.success(units)).when(unitRepository).getAllUnits(any());

        viewModel.loadUnits();

        ApiResult<List<UnitResponse>> result = viewModel.getUnitsResult().getValue();
        assertTrue(result instanceof ApiResult.Success<List<UnitResponse>>);
        assertTrue(((ApiResult.Success<List<UnitResponse>>) result).getData().contains(units.get(0)));
    }

    @Test
    public void createUnit_forwardsNameAndCodeToRepository() {
        viewModel.createUnit("Teaspoon", "tsp");

        verify(unitRepository).createUnit(
                eq(new UnitRequestDTO("Teaspoon", "tsp")), any());
    }

    @Test
    public void deleteUnit_forwardsIdToRepository() {
        viewModel.deleteUnit("u1");

        verify(unitRepository).deleteUnit(eq("u1"), any());
    }
}
