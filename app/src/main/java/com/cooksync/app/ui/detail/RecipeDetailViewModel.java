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
 * @version 1.0
 * @since 04/08/2026
 */
public class RecipeDetailViewModel extends ViewModel {

    private final RecipeRepository repository;

    private final MutableLiveData<ApiResult<RecipeResponse>> recipeResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<NoteResponse>> noteResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> favoritesResult = new MutableLiveData<>();

    public RecipeDetailViewModel() {
        this.repository = new RecipeRepositoryImpl();
    }

    public LiveData<ApiResult<RecipeResponse>> getRecipeResult() {
        return recipeResult;
    }

    public LiveData<ApiResult<NoteResponse>> getNoteResult() {
        return noteResult;
    }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getFavoritesResult() {
        return favoritesResult;
    }

    public void loadRecipe(String recipeId) {
        repository.getRecipeDetail(recipeId, recipeResult);
    }

    public void loadNote(String recipeId) {
        repository.getPersonalNote(recipeId, noteResult);
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
}
