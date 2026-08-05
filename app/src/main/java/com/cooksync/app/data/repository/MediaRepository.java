package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.domain.ApiResult;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;

/**
 * Declares the contract for fetching signed Cloudinary upload credentials, used by any screen
 * that needs to upload an image directly from the client (profile avatar, recipe photos).
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public interface MediaRepository {

    /**
     * Fetches a fresh signed upload signature from the server.
     *
     * @param resultTarget live data target the result will be posted to
     */
    void getUploadSignature(MutableLiveData<ApiResult<CloudinarySignatureResponse>> resultTarget);
}
