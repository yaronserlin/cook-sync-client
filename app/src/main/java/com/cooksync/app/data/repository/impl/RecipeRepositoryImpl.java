package com.cooksync.app.data.repository.impl;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.datasource.remote.ApiService;
import com.cooksync.app.data.datasource.remote.RetrofitClient;
import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.dtos.request.recipe.RecipeCreateRequestDTO;
import com.dtos.response.ApiResponse;
import com.dtos.response.PagedResponse;
import com.dtos.response.note.NoteResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;

import java.util.List;

/**
 * Concrete implementation of {@link RecipeRepository} that delegates calls to the remote
 * {@link ApiService} and manages execution on a background thread pool (inherited from
 * {@link BaseRepository}).
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class RecipeRepositoryImpl extends BaseRepository implements RecipeRepository {

    private final ApiService apiService;

    public RecipeRepositoryImpl() {
        this.apiService = RetrofitClient.getInstance();
    }

    @Override
    public void getPublicFeed(int page, int size, MutableLiveData<ApiResult<PagedResponse<RecipePreviewResponse>>> resultTarget) {
        executeAsync(apiService.getPublicFeed(page, size), resultTarget);
    }

    @Override
    public void searchRecipes(String query, int page, int size, MutableLiveData<ApiResult<PagedResponse<RecipePreviewResponse>>> resultTarget) {
        executeAsync(apiService.searchRecipes(query, null, null, page, size), resultTarget);
    }

    @Override
    public void getRecipesByTag(String tagName, int page, int size, MutableLiveData<ApiResult<PagedResponse<RecipePreviewResponse>>> resultTarget) {
        executeAsync(apiService.getRecipesByTag(tagName, page, size), resultTarget);
    }

    @Override
    public void getRecipeDetail(String recipeId, MutableLiveData<ApiResult<RecipeResponse>> resultTarget) {
        executeAsync(apiService.getRecipeDetail(recipeId), resultTarget);
    }

    @Override
    public void createRecipe(RecipeCreateRequestDTO request, MutableLiveData<ApiResult<RecipeResponse>> resultTarget) {
        executeAsync(apiService.createRecipe(request), resultTarget);
    }

    @Override
    public void updateRecipe(String recipeId, RecipeCreateRequestDTO request, MutableLiveData<ApiResult<RecipeResponse>> resultTarget) {
        executeAsync(apiService.updateRecipe(recipeId, request), resultTarget);
    }

    @Override
    public void getFavorites(MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget) {
        fetchAsync(apiService::getFavorites, resultTarget);
    }

    @Override
    public void addFavorite(String recipeId, MutableLiveData<ApiResult<Void>> resultTarget) {
        executeAsync(apiService.addFavorite(recipeId), resultTarget);
    }

    @Override
    public void removeFavorite(String recipeId, MutableLiveData<ApiResult<Void>> resultTarget) {
        executeAsync(apiService.removeFavorite(recipeId), resultTarget);
    }

    @Override
    public void getPersonalNote(String recipeId, MutableLiveData<ApiResult<NoteResponse>> resultTarget) {
        executeAsync(apiService.getPersonalNote(recipeId), resultTarget);
    }

    @Override
    public void getAllPersonalNotes(String recipeId, MutableLiveData<ApiResult<List<NoteResponse>>> resultTarget) {
        fetchAsync((page, size) -> apiService.getAllPersonalNotes(recipeId, page, size), resultTarget);
    }

    @Override
    public void saveNote(String recipeId, String instructionId, String note, MutableLiveData<ApiResult<Void>> resultTarget) {
        java.util.UUID recipeUuid = java.util.UUID.fromString(recipeId);
        java.util.UUID instructionUuid = instructionId == null ? null : java.util.UUID.fromString(instructionId);
        com.dtos.request.note.NoteRequestDTO request = new com.dtos.request.note.NoteRequestDTO(recipeUuid, instructionUuid, note);
        executeAsync(apiService.saveNote(request), resultTarget);
    }

    @Override
    public void deleteNote(String noteId, MutableLiveData<ApiResult<Void>> resultTarget) {
        executeAsync(apiService.deleteNote(noteId), resultTarget);
    }

    @Override
    public void getMyRecipes(MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget) {
        fetchAsync(apiService::getMyRecipes, resultTarget);
    }

    @Override
    public void getPublicRecipesForUser(String userId, MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget) {
        fetchAsync((page, size) -> apiService.getPublicUserRecipes(userId, page, size), resultTarget);
    }

    @Override
    public void getPublicFavoritesForUser(String userId, MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget) {
        fetchAsync((page, size) -> apiService.getPublicUserFavorites(userId, page, size), resultTarget);
    }

    @Override
    public void deleteRecipe(String recipeId, MutableLiveData<ApiResult<Void>> resultTarget) {
        executeAsync(apiService.deleteRecipe(recipeId), resultTarget);
    }

    @Override
    public void updateRecipeVisibility(String recipeId, String visibility, MutableLiveData<ApiResult<RecipeResponse>> resultTarget) {
        com.dtos.request.recipe.RecipeVisibilityUpdateRequestDTO request =
                new com.dtos.request.recipe.RecipeVisibilityUpdateRequestDTO(visibility);
        executeAsync(apiService.updateRecipeVisibility(recipeId, request), resultTarget);
    }

    @Override
    public void submitReview(String recipeId, double rating, String title, String comment,
                              MutableLiveData<ApiResult<Void>> resultTarget) {
        com.dtos.request.review.ReviewRequestDTO request =
                new com.dtos.request.review.ReviewRequestDTO(rating, title, comment);
        executeAsync(apiService.submitReview(recipeId, request), resultTarget);
    }

    @Override
    public void deleteReview(String reviewId, MutableLiveData<ApiResult<Void>> resultTarget) {
        executeAsync(apiService.deleteReview(reviewId), resultTarget);
    }

    @Override
    public void reportReview(String reviewId, String reason, String comment,
                              MutableLiveData<ApiResult<Void>> resultTarget) {
        com.dtos.request.review.ReportReviewRequestDTO request =
                new com.dtos.request.review.ReportReviewRequestDTO(reason, comment);
        executeAsync(apiService.reportReview(reviewId, request), resultTarget);
    }
}
