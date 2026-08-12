package com.cooksync.app.data.repository.impl;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.datasource.remote.ApiService;
import com.cooksync.app.data.datasource.remote.RetrofitClient;
import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.data.repository.UnitRepository;
import com.cooksync.app.domain.ApiResult;
import com.dtos.request.unit.UnitRequestDTO;
import com.dtos.response.unit.UnitResponse;

import java.util.List;

/**
 * Concrete implementation of {@link UnitRepository} for remote data access, using the shared
 * call-execution machinery from {@link BaseRepository}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class UnitRepositoryImpl extends BaseRepository implements UnitRepository {

    private final ApiService apiService;

    public UnitRepositoryImpl() {
        this.apiService = RetrofitClient.getInstance();
    }

    @Override
    public void getAllUnits(MutableLiveData<ApiResult<List<UnitResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(fetchAllPages(apiService::getUnits)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createUnit(UnitRequestDTO request, MutableLiveData<ApiResult<UnitResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.createUnit(request))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteUnit(String id, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.deleteUnit(id))));
    }
}
