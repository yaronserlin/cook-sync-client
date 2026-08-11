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

    /**
     * Fetches the most-used tags across all recipes, ranked by descending recipe count, for the
     * recipe wizard's "Popular tags" row.
     *
     * @param limit maximum number of popular tags to return
     * @param resultTarget LiveData target to post the outcome
     */
    void getPopularTags(int limit, MutableLiveData<ApiResult<List<TagResponse>>> resultTarget);

    /**
     * Creates a new custom tag, or returns the existing one if a tag with the same name
     * (case-insensitive) already exists. Used by the "Create tag" action in the recipe
     * creation wizard's tag autocomplete.
     *
     * @param name the tag's display name
     * @param resultTarget LiveData target to post the outcome
     */
    void createTag(String name, MutableLiveData<ApiResult<TagResponse>> resultTarget);
}
