package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.domain.ApiResult;
import com.dtos.response.PagedResponse;
import com.dtos.response.note.NoteResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;

import java.util.List;

/**
 * Interface contract for recipe-related data operations.
 * Handles fetching the public feed, searching, tag-based filtering, and recipe details.
 * Also manages the user's personal favorites and private notes.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public interface RecipeRepository {

    /**
     * Fetches a paginated page of public recipes for the home discovery feed.
     *
     * @param page page index (0-based)
     * @param size number of items per page
     * @param resultTarget LiveData target to post the outcome
     */
    void getPublicFeed(int page, int size, MutableLiveData<ApiResult<PagedResponse<RecipePreviewResponse>>> resultTarget);

    /**
     * Searches for public recipes matching a query.
     *
     * @param query search text
     * @param resultTarget LiveData target to post the outcome
     */
    void searchRecipes(String query, MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget);

    /**
     * Fetches public recipes filtered by a specific tag.
     *
     * @param tagName name of the tag
     * @param resultTarget LiveData target to post the outcome
     */
    void getRecipesByTag(String tagName, MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget);

    /**
     * Fetches full details for a specific recipe.
     *
     * @param recipeId unique ID of the recipe
     * @param resultTarget LiveData target to post the outcome
     */
    void getRecipeDetail(String recipeId, MutableLiveData<ApiResult<RecipeResponse>> resultTarget);

    /**
     * Fetches the user's personal recipes marked as favorites.
     *
     * @param resultTarget LiveData target to post the outcome
     */
    void getFavorites(MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget);

    /**
     * Adds a recipe to the user's favorites.
     *
     * @param recipeId recipe ID to favorite
     * @param resultTarget LiveData target to post the outcome
     */
    void addFavorite(String recipeId, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Removes a recipe from the user's favorites.
     *
     * @param recipeId recipe ID to unfavorite
     * @param resultTarget LiveData target to post the outcome
     */
    void removeFavorite(String recipeId, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Fetches the personal private note for a specific recipe if it exists.
     *
     * @param recipeId recipe ID
     * @param resultTarget LiveData target to post the outcome
     */
    void getPersonalNote(String recipeId, MutableLiveData<ApiResult<NoteResponse>> resultTarget);
}
