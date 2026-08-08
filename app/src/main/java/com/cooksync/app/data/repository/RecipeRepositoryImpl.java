package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.remote.ApiService;
import com.cooksync.app.data.remote.RetrofitClient;
import com.cooksync.app.domain.ApiResult;
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
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getPublicFeed(page, size))));
    }

    @Override
    public void searchRecipes(String query, int page, int size, MutableLiveData<ApiResult<PagedResponse<RecipePreviewResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.searchRecipes(query, null, null, page, size))));
    }

    @Override
    public void getRecipesByTag(String tagName, int page, int size, MutableLiveData<ApiResult<PagedResponse<RecipePreviewResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getRecipesByTag(tagName, page, size))));
    }

    @Override
    public void getRecipeDetail(String recipeId, MutableLiveData<ApiResult<RecipeResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getRecipeDetail(recipeId))));
    }

    @Override
    public void getFavorites(MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(fetchAllPages(apiService::getFavorites)));
    }

    @Override
    public void addFavorite(String recipeId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.addFavorite(recipeId))));
    }

    @Override
    public void removeFavorite(String recipeId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.removeFavorite(recipeId))));
    }

    @Override
    public void getPersonalNote(String recipeId, MutableLiveData<ApiResult<NoteResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getPersonalNote(recipeId))));
    }

    @Override
    public void getAllPersonalNotes(String recipeId, MutableLiveData<ApiResult<List<NoteResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(
                fetchAllPages((page, size) -> apiService.getAllPersonalNotes(recipeId, page, size))));
    }

    @Override
    public void saveNote(String recipeId, String instructionId, String note, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        java.util.UUID recipeUuid = java.util.UUID.fromString(recipeId);
        java.util.UUID instructionUuid = instructionId == null ? null : java.util.UUID.fromString(instructionId);
        com.dtos.request.note.NoteRequestDTO request = new com.dtos.request.note.NoteRequestDTO(recipeUuid, instructionUuid, note);
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.saveNote(request))));
    }

    @Override
    public void deleteNote(String noteId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.deleteNote(noteId))));
    }

    @Override
    public void getMyRecipes(MutableLiveData<ApiResult<List<RecipePreviewResponse>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(fetchAllPages(apiService::getMyRecipes)));
    }

    @Override
    public void deleteRecipe(String recipeId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.deleteRecipe(recipeId))));
    }

    @Override
    public void updateRecipeVisibility(String recipeId, String visibility, MutableLiveData<ApiResult<RecipeResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        com.dtos.request.recipe.RecipeVisibilityUpdateRequestDTO request =
                new com.dtos.request.recipe.RecipeVisibilityUpdateRequestDTO(visibility);
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.updateRecipeVisibility(recipeId, request))));
    }

    @Override
    public void submitReview(String recipeId, double rating, String title, String comment,
                              MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        com.dtos.request.review.ReviewRequestDTO request =
                new com.dtos.request.review.ReviewRequestDTO(rating, title, comment);
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.submitReview(recipeId, request))));
    }

    @Override
    public void deleteReview(String reviewId, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.deleteReview(reviewId))));
    }

    @Override
    public void reportReview(String reviewId, String reason, String comment,
                              MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        com.dtos.request.review.ReportReviewRequestDTO request =
                new com.dtos.request.review.ReportReviewRequestDTO(reason, comment);
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.reportReview(reviewId, request))));
    }
}
