package com.cooksync.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseViewModel;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.user.UserResponse;

import java.util.List;

/**
 * ViewModel for {@link UserProfileActivity}. Fetches another user's public profile (avatar,
 * name, city, bio) by ID, then conditionally loads their public recipes/favorites once the
 * profile itself confirms which of the two privacy toggles are enabled — never firing a network
 * call for a section that will render as hidden anyway.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 12/08/2026
 */
public class UserProfileViewModel extends BaseViewModel {

    private final AuthRepository authRepository;
    private final RecipeRepository recipeRepository;

    private final MutableLiveData<ApiResult<UserResponse>> profileResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> recipesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> favoritesResult = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param authRepository the repository used to fetch the target user's public profile
     * @param recipeRepository the repository used to fetch the target user's public recipes/favorites
     */
    public UserProfileViewModel(AuthRepository authRepository, RecipeRepository recipeRepository) {
        this.authRepository = authRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * Loads the public profile of the given user, then — once it resolves — loads whichever of
     * their recipes/favorites sections the returned privacy flags say should be shown.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param userId the ID of the user whose profile should be fetched
     */
    public void loadProfile(String userId) {
        observeOnce(profileResult, result -> {
            if (result instanceof ApiResult.Success<UserResponse> success) {
                UserResponse user = success.getData();
                if (user.showRecipesPublicly()) {
                    recipeRepository.getPublicRecipesForUser(userId, recipesResult);
                }
                if (user.showFavoritesPublicly()) {
                    recipeRepository.getPublicFavoritesForUser(userId, favoritesResult);
                }
            }
        });
        authRepository.getUserProfile(userId, profileResult);
    }

    /** @return observable public-profile fetch result (Loading → Success/Error) */
    public LiveData<ApiResult<UserResponse>> getProfileResult() { return profileResult; }
    /** @return observable public-recipes fetch result, only ever fired if the profile allows it */
    public LiveData<ApiResult<List<RecipePreviewResponse>>> getRecipesResult() { return recipesResult; }
    /** @return observable public-favorites fetch result, only ever fired if the profile allows it */
    public LiveData<ApiResult<List<RecipePreviewResponse>>> getFavoritesResult() { return favoritesResult; }
}
