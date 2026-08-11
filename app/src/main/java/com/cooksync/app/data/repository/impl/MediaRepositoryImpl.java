package com.cooksync.app.data.repository.impl;
import com.cooksync.app.data.datasource.remote.*;
import com.cooksync.app.data.datasource.local.*;
import com.cooksync.app.data.repository.*;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.datasource.remote.ApiService;
import com.cooksync.app.data.datasource.remote.RetrofitClient;
import com.cooksync.app.domain.ApiResult;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;

/**
 * Concrete implementation of {@link MediaRepository} that delegates to the remote REST API
 * via Retrofit.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class MediaRepositoryImpl extends BaseRepository implements MediaRepository {

    private final ApiService apiService;

    /**
     * Constructs the repository using the shared authenticated Retrofit service.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public MediaRepositoryImpl() {
        this.apiService = RetrofitClient.getInstance();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getUploadSignature(MutableLiveData<ApiResult<CloudinarySignatureResponse>> resultTarget) {
        getUploadSignature(null, null, resultTarget);
    }

    @Override
    public void getUploadSignature(String folder, String publicId, MutableLiveData<ApiResult<CloudinarySignatureResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getMediaSignature(folder, publicId))));
    }
}
