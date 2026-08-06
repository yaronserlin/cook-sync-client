package com.cooksync.app.ui.detail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.RecipeRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.dtos.response.note.NoteResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;

import java.util.List;

/**
 * Manages data state for {@link RecipeDetailActivity}.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 04/08/2026
 */
public class RecipeDetailViewModel extends ViewModel {

    private final RecipeRepository repository;

    private final MutableLiveData<ApiResult<RecipeResponse>> recipeResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<NoteResponse>>> notesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> favoritesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> noteSaveResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> reviewActionResult = new MutableLiveData<>();

    public RecipeDetailViewModel() {
        this.repository = new RecipeRepositoryImpl();
    }

    public LiveData<ApiResult<RecipeResponse>> getRecipeResult() {
        return recipeResult;
    }

    /**
     * Every private note attached to the recipe: the recipe-wide note (if any) plus one per
     * annotated instruction step, distinguished by {@link NoteResponse#instructionId()}.
     */
    public LiveData<ApiResult<List<NoteResponse>>> getNotesResult() {
        return notesResult;
    }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getFavoritesResult() {
        return favoritesResult;
    }

    public LiveData<ApiResult<Void>> getNoteSaveResult() {
        return noteSaveResult;
    }

    /** Outcome of the most recent review delete or report action. */
    public LiveData<ApiResult<Void>> getReviewActionResult() {
        return reviewActionResult;
    }

    public void loadRecipe(String recipeId) {
        repository.getRecipeDetail(recipeId, recipeResult);
    }

    public void loadNotes(String recipeId) {
        repository.getAllPersonalNotes(recipeId, notesResult);
    }

    public void loadFavorites() {
        repository.getFavorites(favoritesResult);
    }

    public void toggleFavorite(String recipeId, boolean currentlyFavorite) {
        if (currentlyFavorite) {
            repository.removeFavorite(recipeId, new MutableLiveData<>());
        } else {
            repository.addFavorite(recipeId, new MutableLiveData<>());
        }
    }

    /**
     * Creates or updates a private note on the recipe, or on one of its instruction steps.
     *
     * @param recipeId the recipe the note belongs to
     * @param instructionId the instruction step the note is attached to, or {@code null} for
     *                      a recipe-wide note
     * @param note the note text
     */
    public void saveNote(String recipeId, String instructionId, String note) {
        repository.saveNote(recipeId, instructionId, note, noteSaveResult);
    }

    /**
     * Deletes a private note.
     *
     * @param noteId the ID of the note to delete
     */
    public void deleteNote(String noteId) {
        repository.deleteNote(noteId, noteSaveResult);
    }

    /**
     * Deletes a review the current user authored.
     *
     * @param reviewId the ID of the review to delete
     */
    public void deleteReview(String reviewId) {
        repository.deleteReview(reviewId, reviewActionResult);
    }

    /**
     * Reports a review authored by another user.
     *
     * @param reviewId the ID of the review being reported
     * @param reason the report reason ("SPAM", "ABUSE", or "OFF_TOPIC")
     * @param comment optional supplementary notes
     */
    public void reportReview(String reviewId, String reason, String comment) {
        repository.reportReview(reviewId, reason, comment, reviewActionResult);
    }
}
