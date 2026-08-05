package com.cooksync.app.ui.recipe;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.RecipeRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.dtos.response.recipe.RecipePreviewResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the data state for the dedicated {@link SearchActivity}: running a keyword search
 * against the public recipe catalog and toggling favorites on the results.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class SearchViewModel extends ViewModel {

    private final RecipeRepository recipeRepository;

    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> searchResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> favoritesResult = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> errorEvent = new MutableLiveData<>();

    private List<RecipePreviewResponse> currentResults = new ArrayList<>();

    public SearchViewModel() {
        this.recipeRepository = new RecipeRepositoryImpl();
    }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getSearchResult() {
        return searchResult;
    }

    public LiveData<ApiResult<List<RecipePreviewResponse>>> getFavoritesResult() {
        return favoritesResult;
    }

    public LiveData<Event<String>> getErrorEvent() {
        return errorEvent;
    }

    /**
     * Runs a keyword search against the public recipe catalog.
     *
     * Complexity:
     * Time: O(1) to dispatch; the network/db work happens off the main thread
     * Space: O(1)
     *
     * @param query the search text
     */
    public void search(String query) {
        if (query == null || query.isBlank()) {
            currentResults = new ArrayList<>();
            searchResult.setValue(new ApiResult.Success<>(currentResults));
            return;
        }
        searchResult.setValue(new ApiResult.Loading<>());
        MutableLiveData<ApiResult<List<RecipePreviewResponse>>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                currentResults = success.getData();
            }
            searchResult.setValue(apiResult);
        });
        recipeRepository.searchRecipes(query, result);
    }

    public void loadFavorites() {
        recipeRepository.getFavorites(favoritesResult);
    }

    /**
     * Optimistically toggles a recipe's favorite state, then fires the corresponding
     * add/remove call. If the server call fails, the optimistic change is rolled back and
     * {@link #errorEvent} is emitted.
     *
     * @param recipeId the id of the recipe to favorite/unfavorite
     */
    public void toggleFavorite(String recipeId) {
        List<RecipePreviewResponse> previous =
                favoritesResult.getValue() instanceof ApiResult.Success<List<RecipePreviewResponse>> success
                        ? new ArrayList<>(success.getData())
                        : new ArrayList<>();

        boolean isFavorite = previous.stream().anyMatch(r -> r.id().equals(recipeId));
        List<RecipePreviewResponse> optimistic = new ArrayList<>(previous);
        MutableLiveData<ApiResult<Void>> writeResult = new MutableLiveData<>();

        if (isFavorite) {
            optimistic.removeIf(r -> r.id().equals(recipeId));
            favoritesResult.setValue(new ApiResult.Success<>(optimistic));
            recipeRepository.removeFavorite(recipeId, writeResult);
        } else {
            currentResults.stream()
                    .filter(r -> r.id().equals(recipeId))
                    .findFirst()
                    .ifPresent(optimistic::add);
            favoritesResult.setValue(new ApiResult.Success<>(optimistic));
            recipeRepository.addFavorite(recipeId, writeResult);
        }

        observeOnce(writeResult, result -> {
            if (result instanceof ApiResult.Error<Void> error) {
                favoritesResult.setValue(new ApiResult.Success<>(previous));
                errorEvent.setValue(new Event<>(error.getMessage()));
            }
        });
    }

    private <T> void observeOnce(MutableLiveData<ApiResult<T>> liveData, Consumer<ApiResult<T>> onSettled) {
        liveData.observeForever(new Observer<>() {
            @Override
            public void onChanged(ApiResult<T> value) {
                if (value instanceof ApiResult.Loading) {
                    return;
                }
                liveData.removeObserver(this);
                onSettled.accept(value);
            }
        });
    }
}
