package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.domain.ApiResult;
import com.dtos.response.tags.TagResponse;

import java.util.List;

/**
 * Interface contract for tag-related data operations.
 * Fetches the list of all available categories/tags for navigation and filtering.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public interface TagRepository {

    /**
     * Fetches all tags defined in the system.
     *
     * @param resultTarget LiveData target to post the outcome
     */
    void getAllTags(MutableLiveData<ApiResult<List<TagResponse>>> resultTarget);
}
