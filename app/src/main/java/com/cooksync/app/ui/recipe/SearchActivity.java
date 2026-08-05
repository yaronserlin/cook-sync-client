package com.cooksync.app.ui.recipe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.home.RecipeCardAdapter;
import com.dtos.response.recipe.RecipePreviewResponse;

import java.util.List;

/**
 * Dedicated recipe search screen, reached by tapping the search field on {@link
 * com.cooksync.app.ui.home.HomeActivity}. Runs a keyword search against the public recipe
 * catalog and displays results in the same card format as the Home feed.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class SearchActivity extends AppCompatActivity {

    private SearchViewModel viewModel;
    private RecipeCardAdapter recipeAdapter;

    private SearchView searchView;
    private RecyclerView rvResults;
    private TextView tvResultsSummary;
    private TextView tvEmptyState;
    private View progress;
    private boolean hasSearched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        initViews();
        setupAdapter();
        setupObservers();

        viewModel.loadFavorites();
        searchView.setIconified(false);
        searchView.requestFocus();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        searchView = findViewById(R.id.search_view);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                hasSearched = true;
                viewModel.search(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    hasSearched = false;
                    viewModel.search(null);
                }
                return false;
            }
        });

        rvResults = findViewById(R.id.rv_results);
        tvResultsSummary = findViewById(R.id.tv_results_summary);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        progress = findViewById(R.id.progress);
    }

    private void setupAdapter() {
        recipeAdapter = new RecipeCardAdapter();
        recipeAdapter.setOnRecipeClickListener(new RecipeCardAdapter.OnRecipeClickListener() {
            @Override
            public void onRecipeClick(String recipeId) {
                Intent intent = new Intent(SearchActivity.this, com.cooksync.app.ui.detail.RecipeDetailActivity.class);
                intent.putExtra(com.cooksync.app.ui.detail.RecipeDetailActivity.EXTRA_RECIPE_ID, recipeId);
                startActivity(intent);
            }

            @Override
            public void onFavoriteClick(String recipeId) {
                viewModel.toggleFavorite(recipeId);
            }
        });
        rvResults.setAdapter(recipeAdapter);
    }

    private void setupObservers() {
        viewModel.getSearchResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                progress.setVisibility(View.VISIBLE);
                tvEmptyState.setVisibility(View.GONE);
            } else if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                progress.setVisibility(View.GONE);
                List<RecipePreviewResponse> recipes = success.getData();
                recipeAdapter.setRecipes(recipes);
                updateSummaryAndEmptyState(recipes);
            } else if (result instanceof ApiResult.Error<?> error) {
                progress.setVisibility(View.GONE);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getFavoritesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                recipeAdapter.setFavorites(success.getData());
            }
        });

        viewModel.getErrorEvent().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Shows the "N recipes found" summary and toggles between the RecyclerView, the initial
     * search prompt, and a "no results" message depending on whether a query has been run.
     *
     * @param recipes the current result set
     */
    private void updateSummaryAndEmptyState(List<RecipePreviewResponse> recipes) {
        boolean hasResults = !recipes.isEmpty();
        rvResults.setVisibility(hasResults ? View.VISIBLE : View.GONE);

        if (!hasSearched) {
            tvResultsSummary.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(R.string.search_empty_prompt);
        } else if (hasResults) {
            tvResultsSummary.setVisibility(View.VISIBLE);
            String summary = recipes.size() + (recipes.size() == 1 ? " recipe found" : " recipes found");
            tvResultsSummary.setText(summary);
            tvEmptyState.setVisibility(View.GONE);
        } else {
            tvResultsSummary.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText(R.string.search_no_results);
        }
    }
}
