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
     * Fetches the complete set of tags defined in the system. The server paginates this
     * endpoint, but the client loops through every page internally (see
     * {@link TagRepositoryImpl}) since callers need the full catalog for the tag chip row and
     * typeahead tag-name matching, not a scrollable subset.
     *
     * @param resultTarget LiveData target to post the outcome
     */
    void getAllTags(MutableLiveData<ApiResult<List<TagResponse>>> resultTarget);
}
