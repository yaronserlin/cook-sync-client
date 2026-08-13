package com.cooksync.app.data.repository.impl;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.datasource.remote.ApiService;
import com.cooksync.app.data.datasource.remote.RetrofitClient;
import com.cooksync.app.data.repository.AdminRepository;
import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.domain.ApiResult;
import com.dtos.request.tags.TagMergeRequestDTO;
import com.dtos.response.PagedResponse;
import com.dtos.response.admin.AdminStatsResponse;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.dtos.response.admin.ReportedReviewResponse;
import com.dtos.response.user.UserResponse;

/**
 * Concrete implementation of {@link AdminRepository} that delegates calls to the remote
 * {@link ApiService} and manages execution on a background thread pool (inherited from
 * {@link BaseRepository}).
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class AdminRepositoryImpl extends BaseRepository implements AdminRepository {

    private final ApiService apiService;

    public AdminRepositoryImpl() {
        this.apiService = RetrofitClient.getInstance();
    }

    @Override
    public void getStats(MutableLiveData<ApiResult<AdminStatsResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getAdminStats())));
    }

    @Override
    public void getUsers(int page, int size, String q, Boolean enabled, String sortBy, String direction,
                          MutableLiveData<ApiResult<PagedResponse<UserResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(
                executeCall(apiService.getAdminUsers(page, size, q, enabled, sortBy, direction))));
    }

    @Override
    public void getReportedReviews(int page, int size,
                                    MutableLiveData<ApiResult<PagedResponse<ReportedReviewResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getReportedReviews(page, size))));
    }

    @Override
    public void dismissReport(String reviewId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.dismissReport(reviewId))));
    }

    @Override
    public void enableUser(String userId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.enableUser(userId))));
    }

    @Override
    public void disableUser(String userId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.disableUser(userId))));
    }

    @Override
    public void deleteUser(String userId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.deleteUser(userId))));
    }

    @Override
    public void getDuplicateTagGroups(int page, int size,
                                       MutableLiveData<ApiResult<PagedResponse<DuplicateTagGroupResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getDuplicateTagGroups(page, size))));
    }

    @Override
    public void mergeTags(String sourceTagId, String targetTagId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        TagMergeRequestDTO request = new TagMergeRequestDTO(sourceTagId, targetTagId);
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.mergeTags(request))));
    }
}
