package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.remote.ApiService;
import com.cooksync.app.data.remote.RetrofitClient;
import com.cooksync.app.domain.ApiResult;
import com.dtos.response.tags.TagResponse;

import java.util.List;

/**
 * Concrete implementation of {@link TagRepository} for remote data access, using the shared
 * call-execution machinery from {@link BaseRepository}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class TagRepositoryImpl extends BaseRepository implements TagRepository {

    private final ApiService apiService;

    public TagRepositoryImpl() {
        this.apiService = RetrofitClient.getInstance();
    }

    @Override
    public void getAllTags(MutableLiveData<ApiResult<List<TagResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getAllTags())));
    }
}
