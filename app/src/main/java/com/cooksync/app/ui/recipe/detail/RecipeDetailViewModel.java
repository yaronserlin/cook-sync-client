package com.cooksync.app.ui.recipe.detail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.BaseViewModel;
import com.cooksync.app.util.PendingActionScheduler;
import com.dtos.response.note.NoteResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;

import java.util.List;

/**
 * Manages data state for {@link RecipeDetailActivity}.
 *
 * @author Yaron Serlin
 * @version 1.2
 * @since 04/08/2026
 */
public class RecipeDetailViewModel extends BaseViewModel {

    /**
     * How long a review delete/report waits before actually reaching the server, giving the
     * "Undo" toast action a window to cancel it. Matches {@code OrganicToast}'s auto-dismiss
     * duration, since the undo action stops being reachable once the toast itself is gone.
     */
    private static final long UNDO_WINDOW_MS = 3200;

    /** Prefixes a report's pending-action key so it can't collide with a delete on the same review. */
    private static final String REPORT_KEY_PREFIX = "report:";

    private final RecipeRepository repository;

    private final MutableLiveData<ApiResult<RecipeResponse>> recipeResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<NoteResponse>>> notesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> favoritesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> noteSaveResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> reviewActionResult = new MutableLiveData<>();

    private final PendingActionScheduler pendingActions = new PendingActionScheduler();

    /**
     * Constructs the ViewModel with the given {@link RecipeRepository}, injected by
     * {@link com.cooksync.app.ui.common.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param repository the repository used for recipe/note/favorite/review calls
     */
    public RecipeDetailViewModel(RecipeRepository repository) {
        this.repository = repository;
    }

    public LiveData<ApiResult<RecipeResponse>> getRecipeResult() { return recipeResult; }

    /**
     * Every private note attached to the recipe: the recipe-wide note (if any) plus one per
     * annotated instruction step, distinguished by {@link NoteResponse#instructionId()}.
     */
    public LiveData<ApiResult<List<NoteResponse>>> getNotesResult() { return notesResult; }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getFavoritesResult() { return favoritesResult; }

    public LiveData<ApiResult<Void>> getNoteSaveResult() { return noteSaveResult; }

    /**
     * Fires only when a deferred review delete/report actually reaches the server and fails
     * (see {@link #deleteReview}/{@link #reportReview}) — a success needs no signal here since
     * the caller already reflects it optimistically, and an undone action never reaches the
     * server at all.
     */
    public LiveData<ApiResult<Void>> getReviewActionResult() { return reviewActionResult; }

    public void loadRecipe(String recipeId) {
        repository.getRecipeDetail(recipeId, recipeResult);
    }

    public void loadNotes(String recipeId) {
        repository.getAllPersonalNotes(recipeId, notesResult);
    }

    public void loadFavorites() {
        repository.getFavorites(favoritesResult);
    }

    /**
     * Toggles the recipe's favorite state. Adding is sent immediately; removing is deferred by
     * {@link #UNDO_WINDOW_MS} so a tap on the toast's "Undo" action (see
     * {@link #undoRemoveFavorite}) can cancel it before it's ever sent. If an add is requested
     * while its matching remove is still pending, the pending remove is simply cancelled
     * rather than sending an add for something the server still has.
     *
     * @param recipeId the recipe to favorite/unfavorite
     * @param currentlyFavorite whether it's currently favorited (i.e. {@code true} removes it)
     */
    public void toggleFavorite(String recipeId, boolean currentlyFavorite) {
        if (currentlyFavorite) {
            pendingActions.schedule(recipeId, UNDO_WINDOW_MS,
                    () -> repository.removeFavorite(recipeId, new MutableLiveData<>()));
        } else {
            if (pendingActions.cancel(recipeId)) {
                return;
            }
            repository.addFavorite(recipeId, new MutableLiveData<>());
        }
    }

    /**
     * Cancels a still-pending "remove from favorites" before it reaches the server.
     *
     * @param recipeId the id of the recipe whose favorite-remove should be undone
     * @return {@code true} if a removal was actually pending and got cancelled; {@code false} if
     *         the undo window already elapsed and the removal reached the server
     */
    public boolean undoRemoveFavorite(String recipeId) {
        return pendingActions.cancel(recipeId);
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
     * Deletes a review the current user authored. The caller is expected to hide the review
     * immediately; the actual server call is delayed by {@link #UNDO_WINDOW_MS} so a tap on the
     * toast's "Undo" action (see {@link #undoDeleteReview}) can cancel it before it's ever
     * sent — a delete the user undoes in time never reaches the server at all.
     *
     * @param reviewId the ID of the review to delete
     */
    public void deleteReview(String reviewId) {
        pendingActions.schedule(reviewId, UNDO_WINDOW_MS, () -> repository.deleteReview(reviewId, reviewActionResult));
    }

    /**
     * Cancels a still-pending delete before it reaches the server.
     *
     * @param reviewId the ID of the review whose delete should be cancelled
     * @return {@code true} if a delete was actually pending and got cancelled; {@code false} if
     *         the undo window already elapsed and the delete reached the server
     */
    public boolean undoDeleteReview(String reviewId) {
        return pendingActions.cancel(reviewId);
    }

    /**
     * Reports a review authored by another user. Deferred and undoable the same way as
     * {@link #deleteReview} — see {@link #undoReportReview}.
     *
     * @param reviewId the ID of the review being reported
     * @param reason the report reason ("SPAM", "ABUSE", or "OFF_TOPIC")
     * @param comment optional supplementary notes
     */
    public void reportReview(String reviewId, String reason, String comment) {
        pendingActions.schedule(REPORT_KEY_PREFIX + reviewId, UNDO_WINDOW_MS,
                () -> repository.reportReview(reviewId, reason, comment, reviewActionResult));
    }

    /**
     * Cancels a still-pending report before it reaches the server.
     *
     * @param reviewId the ID of the review whose report should be cancelled
     * @return {@code true} if a report was actually pending and got cancelled; {@code false} if
     *         the undo window already elapsed and the report reached the server
     */
    public boolean undoReportReview(String reviewId) {
        return pendingActions.cancel(REPORT_KEY_PREFIX + reviewId);
    }

    /**
     * Flushes any still-pending review actions immediately rather than dropping them, so
     * navigating away before the undo window elapses doesn't silently discard an action the
     * user never undid.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        pendingActions.flushAll();
    }
}
