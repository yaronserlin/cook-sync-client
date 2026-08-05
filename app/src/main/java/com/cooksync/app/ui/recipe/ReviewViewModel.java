package com.cooksync.app.ui.recipe;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.RecipeRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;

/**
 * ViewModel for {@link ReviewActivity}. Validates the rating/title client-side and delegates
 * submission to {@link RecipeRepository}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class ReviewViewModel extends ViewModel {

    private final RecipeRepository repository;

    private final MutableLiveData<ApiResult<Void>> submitResult = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> validationError = new MutableLiveData<>();

    public ReviewViewModel() {
        this.repository = new RecipeRepositoryImpl();
    }

    /**
     * Validates and submits a review for a recipe.
     *
     * Complexity:
     * Time: O(n) where n is the length of the title/comment text
     * Space: O(1)
     *
     * @param recipeId the ID of the recipe being reviewed
     * @param rating the star rating selected (1–5)
     * @param rawTitle raw text from the title field
     * @param rawComment raw text from the optional comment field
     */
    public void submitReview(String recipeId, int rating, String rawTitle, String rawComment) {
        if (rating < 1 || rating > 5) {
            validationError.setValue(new Event<>("Please select a star rating"));
            return;
        }
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty()) {
            validationError.setValue(new Event<>("Please give your review a title"));
            return;
        }
        String comment = rawComment == null ? "" : rawComment.trim();
        repository.submitReview(recipeId, rating, title, comment.isEmpty() ? null : comment, submitResult);
    }

    /** @return observable result of the review submission */
    public LiveData<ApiResult<Void>> getSubmitResult() { return submitResult; }

    /** @return one-shot client-side validation errors, to surface as a Toast */
    public LiveData<Event<String>> getValidationError() { return validationError; }
}
