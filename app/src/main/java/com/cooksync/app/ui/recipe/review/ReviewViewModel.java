package com.cooksync.app.ui.recipe.review;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.base.BaseViewModel;

/**
 * ViewModel for {@link ReviewActivity}. Validates the rating/title client-side and delegates
 * submission to {@link RecipeRepository}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class ReviewViewModel extends BaseViewModel {

    private final RecipeRepository repository;

    private final MutableLiveData<ApiResult<Void>> submitResult = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> validationError = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with the given {@link RecipeRepository}, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param repository the repository used for the submit-review call
     */
    public ReviewViewModel(RecipeRepository repository) {
        this.repository = repository;
    }

    /**
     * Validates the rating and title, then submits the review. Fails silently into
     * {@link #validationError} if either check doesn't pass.
     *
     * Complexity:
     * Time: O(n) where n is the combined length of the title and comment
     * Space: O(1)
     *
     * @param recipeId the recipe being reviewed
     * @param rating the star rating, expected 1-5
     * @param rawTitle raw text from the review-title field
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

    /** @return observable submit result (Loading → Success/Error) */
    public LiveData<ApiResult<Void>> getSubmitResult() { return submitResult; }
    /** @return one-shot client-side validation errors, to surface as a Toast */
    public LiveData<Event<String>> getValidationError() { return validationError; }
}
