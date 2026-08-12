package com.cooksync.app.ui.base;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.data.repository.AdminRepository;
import com.cooksync.app.data.repository.impl.AdminRepositoryImpl;
import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.impl.AuthRepositoryImpl;
import com.cooksync.app.data.repository.MediaRepository;
import com.cooksync.app.data.repository.impl.MediaRepositoryImpl;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.impl.RecipeRepositoryImpl;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.data.repository.impl.TagRepositoryImpl;
import com.cooksync.app.data.repository.UnitRepository;
import com.cooksync.app.data.repository.impl.UnitRepositoryImpl;
import com.cooksync.app.ui.admin.AdminReportsViewModel;
import com.cooksync.app.ui.admin.AdminStatsViewModel;
import com.cooksync.app.ui.admin.AdminTagsViewModel;
import com.cooksync.app.ui.admin.AdminUnitsViewModel;
import com.cooksync.app.ui.admin.AdminUsersViewModel;
import com.cooksync.app.ui.auth.ForgotPasswordViewModel;
import com.cooksync.app.ui.auth.LoginViewModel;
import com.cooksync.app.ui.auth.RegisterViewModel;
import com.cooksync.app.ui.auth.UserProfileViewModel;
import com.cooksync.app.ui.home.HomeViewModel;
import com.cooksync.app.ui.recipe.cooking.CookingModeViewModel;
import com.cooksync.app.ui.recipe.detail.RecipeDetailViewModel;
import com.cooksync.app.ui.recipe.favorites.FavoritesViewModel;
import com.cooksync.app.ui.recipe.myrecipes.MyRecipesViewModel;
import com.cooksync.app.ui.recipe.review.ReviewViewModel;
import com.cooksync.app.ui.recipe.search.SearchViewModel;
import com.cooksync.app.ui.recipe.wizard.AddRecipeViewModel;
import com.cooksync.app.ui.settings.SettingsViewModel;

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
    private final AdminRepository adminRepository;
    private final UnitRepository unitRepository;

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
        this.adminRepository = new AdminRepositoryImpl();
        this.unitRepository = new UnitRepositoryImpl();
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
        } else if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(authRepository, mediaRepository, recipeRepository);
        } else if (modelClass.isAssignableFrom(CookingModeViewModel.class)) {
            return (T) new CookingModeViewModel(recipeRepository);
        } else if (modelClass.isAssignableFrom(FavoritesViewModel.class)) {
            return (T) new FavoritesViewModel(recipeRepository, tagRepository);
        } else if (modelClass.isAssignableFrom(MyRecipesViewModel.class)) {
            return (T) new MyRecipesViewModel(recipeRepository, tagRepository);
        } else if (modelClass.isAssignableFrom(ReviewViewModel.class)) {
            return (T) new ReviewViewModel(recipeRepository);
        } else if (modelClass.isAssignableFrom(AdminStatsViewModel.class)) {
            return (T) new AdminStatsViewModel(adminRepository);
        } else if (modelClass.isAssignableFrom(AdminReportsViewModel.class)) {
            return (T) new AdminReportsViewModel(adminRepository, recipeRepository);
        } else if (modelClass.isAssignableFrom(AdminTagsViewModel.class)) {
            return (T) new AdminTagsViewModel(adminRepository);
        } else if (modelClass.isAssignableFrom(AdminUsersViewModel.class)) {
            return (T) new AdminUsersViewModel(adminRepository);
        } else if (modelClass.isAssignableFrom(AdminUnitsViewModel.class)) {
            return (T) new AdminUnitsViewModel(unitRepository);
        } else if (modelClass.isAssignableFrom(AddRecipeViewModel.class)) {
            return (T) new AddRecipeViewModel(recipeRepository, tagRepository, unitRepository, mediaRepository);
        } else if (modelClass.isAssignableFrom(UserProfileViewModel.class)) {
            return (T) new UserProfileViewModel(authRepository, recipeRepository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
