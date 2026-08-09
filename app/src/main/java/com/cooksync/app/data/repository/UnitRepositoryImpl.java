package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.remote.ApiService;
import com.cooksync.app.data.remote.RetrofitClient;
import com.cooksync.app.domain.ApiResult;
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
}
