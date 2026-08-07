package com.cooksync.app.ui.common;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.AuthRepositoryImpl;
import com.cooksync.app.data.repository.MediaRepository;
import com.cooksync.app.data.repository.MediaRepositoryImpl;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.RecipeRepositoryImpl;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.data.repository.TagRepositoryImpl;
import com.cooksync.app.ui.auth.ForgotPasswordViewModel;
import com.cooksync.app.ui.auth.LoginViewModel;
import com.cooksync.app.ui.auth.RegisterViewModel;
import com.cooksync.app.ui.home.HomeViewModel;
import com.cooksync.app.ui.profile.ProfileViewModel;
import com.cooksync.app.ui.recipe.cooking.CookingModeViewModel;
import com.cooksync.app.ui.recipe.detail.RecipeDetailViewModel;
import com.cooksync.app.ui.recipe.list.FavoritesViewModel;
import com.cooksync.app.ui.recipe.list.MyRecipesViewModel;
import com.cooksync.app.ui.recipe.review.ReviewViewModel;
import com.cooksync.app.ui.recipe.search.SearchViewModel;

/**
 * Factory for constructing ViewModels with their required repository dependencies, following
 * the Dependency Inversion principle: feature ViewModels declare their repository
 * dependencies as constructor parameters instead of instantiating {@code *RepositoryImpl}
 * classes themselves, and this factory is the single place that wires concrete
 * implementations in. Used via {@code new ViewModelProvider(this, new ViewModelFactory())}
 * from every Activity that owns a ViewModel.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public class ViewModelFactory implements ViewModelProvider.Factory {

    private final AuthRepository authRepository;
    private final RecipeRepository recipeRepository;
    private final TagRepository tagRepository;
    private final MediaRepository mediaRepository;

    /**
     * Constructs the factory, eagerly creating the shared repository instances it hands out
     * to every ViewModel it builds.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public ViewModelFactory() {
        this.authRepository = new AuthRepositoryImpl();
        this.recipeRepository = new RecipeRepositoryImpl();
        this.tagRepository = new TagRepositoryImpl();
        this.mediaRepository = new MediaRepositoryImpl();
    }

    /**
     * Instantiates the requested ViewModel type with its required repositories injected.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param <T> the requested ViewModel type
     * @param modelClass the class of the ViewModel to create
     * @return a fully constructed instance of {@code modelClass}
     * @throws IllegalArgumentException if {@code modelClass} is not a known ViewModel type
     */
    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(recipeRepository, tagRepository);
        } else if (modelClass.isAssignableFrom(LoginViewModel.class)) {
            return (T) new LoginViewModel(authRepository);
        } else if (modelClass.isAssignableFrom(RegisterViewModel.class)) {
            return (T) new RegisterViewModel(authRepository);
        } else if (modelClass.isAssignableFrom(ForgotPasswordViewModel.class)) {
            return (T) new ForgotPasswordViewModel(authRepository);
        } else if (modelClass.isAssignableFrom(SearchViewModel.class)) {
            return (T) new SearchViewModel(recipeRepository, tagRepository);
        } else if (modelClass.isAssignableFrom(RecipeDetailViewModel.class)) {
            return (T) new RecipeDetailViewModel(recipeRepository);
        } else if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(authRepository, mediaRepository);
        } else if (modelClass.isAssignableFrom(CookingModeViewModel.class)) {
            return (T) new CookingModeViewModel(recipeRepository);
        } else if (modelClass.isAssignableFrom(FavoritesViewModel.class)) {
            return (T) new FavoritesViewModel(recipeRepository, tagRepository);
        } else if (modelClass.isAssignableFrom(MyRecipesViewModel.class)) {
            return (T) new MyRecipesViewModel(recipeRepository, tagRepository);
        } else if (modelClass.isAssignableFrom(ReviewViewModel.class)) {
            return (T) new ReviewViewModel(recipeRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
