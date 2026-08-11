package com.cooksync.app.ui.recipe.myrecipes;
import com.cooksync.app.ui.recipe.common.*;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.data.datasource.local.RecipeDraftStore;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.FilterSheetLauncher;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.common.NoResultsStateHelper;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.recipe.detail.RecipeDetailActivity;
import com.cooksync.app.ui.recipe.wizard.AddRecipeWizardActivity;
import com.cooksync.app.data.model.recipe.RecipeDraft;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists every recipe (published or private) the current user has authored, with search,
 * sort/difficulty/tag filtering, a Public/Private chip filter, and per-recipe management
 * actions (toggle visibility, delete) via an overflow menu.
 *
 * @author Yaron Serlin
 * @version 1.2
 * @since 04/08/2026
 */
public class MyRecipesActivity extends RecipeListActivity {

    private MyRecipesViewModel viewModel;
    private RecipeRowCardAdapter adapter;
    private List<String> loadedTagNames = new ArrayList<>();

    private TextView chipAll;
    private TextView chipPublic;
    private TextView chipPrivate;

    @IdRes
    @Override
    protected int getSelectedNavItemId() {
        return R.id.nav_my_recipes;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(MyRecipesViewModel.class);

        initViews();
        setupObservers();

        showSkeleton(true);
        viewModel.loadMyRecipes();
        viewModel.loadTags();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showResumableDraftIfAny();
        viewModel.loadMyRecipes();
    }

    /**
     * Shows the pinned "resumable draft" card when a local, unpublished draft exists (see
     * {@link RecipeDraftStore}), matching the design's singular "Resumable draft pinned above
     * published recipes." Re-checked on every {@link #onResume()} since the draft can be
     * created, resumed, or discarded from {@link AddRecipeWizardActivity} in between visits.
     */
    private void showResumableDraftIfAny() {
        View draftCard = findViewById(R.id.draft_card);
        if (!RecipeDraftStore.hasDraft()) {
            draftCard.setVisibility(View.GONE);
            return;
        }
        // Dashed shape-drawable strokes don't reliably render under hardware acceleration.
        draftCard.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        RecipeDraft draft = RecipeDraftStore.load();
        String title = draft.title == null || draft.title.trim().isEmpty()
                ? getString(R.string.my_recipes_draft_untitled) : draft.title;
        ((TextView) draftCard.findViewById(R.id.tv_draft_title)).setText(title);

        CharSequence savedAgo = draft.savedAtMillis > 0
                ? android.text.format.DateUtils.getRelativeTimeSpanString(draft.savedAtMillis)
                : "";
        ((TextView) draftCard.findViewById(R.id.tv_draft_subtitle)).setText(
                getString(R.string.wizard_draft_step_of_format, draft.lastReachedStep + 1, savedAgo));

        View.OnClickListener resume = v -> Navigator.start(this, AddRecipeWizardActivity.class);
        draftCard.setOnClickListener(resume);
        draftCard.findViewById(R.id.btn_resume_draft).setOnClickListener(resume);
        draftCard.setVisibility(View.VISIBLE);
    }

    private void initViews() {
        ivEmptyIcon.setImageResource(R.drawable.ic_book);
        tvEmptyTitle.setText(R.string.my_recipes_empty_title);
        tvEmptySubtitle.setText(R.string.my_recipes_empty_subtitle);
        searchView.setQueryHint(getString(R.string.my_recipes_search_hint));

        adapter = new RecipeRowCardAdapter();
        adapter.setTrailingAction(RecipeRowCardAdapter.TrailingAction.OPTIONS_MENU);
        adapter.setShowVisibilityBadge(true);
        adapter.setListener(new RecipeRowCardAdapter.Listener() {
            @Override
            public void onRecipeClick(RecipePreviewResponse recipe) {
                Intent intent = new Intent();
                intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.id());
                Navigator.start(MyRecipesActivity.this, RecipeDetailActivity.class, intent);
            }

            @Override
            public void onTrailingActionClick(RecipePreviewResponse recipe, View anchor) {
                showOptionsMenu(recipe, anchor);
            }
        });
        rvList.setAdapter(adapter);

        setupSearchListener(viewModel::search);

        btnFilters.setOnClickListener(v ->
                FilterSheetLauncher.show(getSupportFragmentManager(), loadedTagNames, viewModel,
                        (sortBy, difficulty, tags, minRating, maxTotalTimeMinutes) -> {
                            viewModel.applyFilters(sortBy, difficulty, tags, minRating, maxTotalTimeMinutes);
                            updateFilterButton();
                        }));

        chipAll = addChip(getString(R.string.filter_all), true, () -> selectVisibility("ALL"));
        chipPublic = addChip(getString(R.string.filter_public), false, () -> selectVisibility("PUBLIC"));
        chipPrivate = addChip(getString(R.string.filter_private), false, () -> selectVisibility("PRIVATE"));

        setOnClearAllClickListener(() -> {
            viewModel.applyFilters("Newest", null, new ArrayList<>(), null, null);
            searchView.setQuery("", true);
            updateFilterButton();
        });
    }

    private void setupObservers() {
        viewModel.getRecipesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                showSkeleton(false);
                List<RecipePreviewResponse> recipes = success.getData();
                adapter.setRecipes(recipes);

                tvTitle.setText(getString(R.string.my_recipes_title_format, viewModel.getPublishedCount()));
                updateFilterButton();

                if (!recipes.isEmpty()) {
                    hideNoResultsState();
                    emptyState.setVisibility(View.GONE);
                    rvList.setVisibility(View.VISIBLE);
                } else if (!viewModel.hasAnyRecipes()) {
                    // Genuinely no recipes yet — the static "No recipes yet" empty state.
                    emptyState.setVisibility(View.VISIBLE);
                    rvList.setVisibility(View.GONE);
                } else {
                    // Recipes exist, but the active search/filters matched none of them.
                    emptyState.setVisibility(View.GONE);
                    showNoResultsState(buildRemovableConstraints());
                }
            } else if (result instanceof ApiResult.Error<List<RecipePreviewResponse>> error) {
                showSkeleton(false);
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getTagsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<TagResponse>> success) {
                loadedTagNames = success.getData().stream().map(TagResponse::name).collect(java.util.stream.Collectors.toList());
            }
        });

        // Delete waits for the server before updating the list, so both outcomes are reported
        // here. Visibility toggling below stays optimistic: it only fires for a deferred call
        // that reached the server and failed, since a success needs no signal — the list
        // already reflects it, and its own "is now public/private" toast (with Undo) is shown
        // immediately from showOptionsMenu() instead of from here.
        viewModel.getDeleteResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<Void>) {
                OrganicToast.showSuccess(this, bottomNav, getString(R.string.recipe_deleted));
            } else if (result instanceof ApiResult.Error<Void> error) {
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getVisibilityResult().observe(this, result -> {
            if (result instanceof ApiResult.Error<RecipeResponse> error) {
                showError(error.getMessage(), bottomNav);
            }
        });

        setupPublishProgressObserver();
    }

    private void setupPublishProgressObserver() {
        com.cooksync.app.data.service.RecipePublishManager.getInstance().getPublishState().observe(this, state -> {
            if (state == null || state.status == com.cooksync.app.data.service.RecipePublishManager.PublishState.Status.IDLE) {
                View card = findViewById(R.id.card_publish_progress);
                if (card != null) card.setVisibility(View.GONE);
                return;
            }

            View card = findViewById(R.id.card_publish_progress);
            if (card == null) return;
            card.setVisibility(View.VISIBLE);

            android.widget.ProgressBar spinner = card.findViewById(R.id.pb_publish_spinner);
            android.widget.ImageView checkIcon = card.findViewById(R.id.iv_publish_success_icon);
            TextView tvTitle = card.findViewById(R.id.tv_publish_title);
            TextView tvSubtitle = card.findViewById(R.id.tv_publish_subtitle);
            TextView tvPercent = card.findViewById(R.id.tv_publish_percent);
            com.google.android.material.progressindicator.LinearProgressIndicator bar = card.findViewById(R.id.pb_publish_bar);

            switch (state.status) {
                case UPLOADING -> {
                    spinner.setVisibility(View.VISIBLE);
                    checkIcon.setVisibility(View.GONE);
                    tvTitle.setText("Publishing recipe...");
                    tvSubtitle.setText(state.message != null ? state.message : "Uploading media...");
                    tvPercent.setText(state.progress + "%");
                    bar.setProgress(state.progress);
                }
                case PUBLISHING -> {
                    spinner.setVisibility(View.VISIBLE);
                    checkIcon.setVisibility(View.GONE);
                    tvTitle.setText("Publishing recipe...");
                    tvSubtitle.setText(state.message != null ? state.message : "Processing recipe details...");
                    tvPercent.setText(state.progress + "%");
                    bar.setProgress(state.progress);
                }
                case SUCCESS -> {
                    spinner.setVisibility(View.GONE);
                    checkIcon.setVisibility(View.VISIBLE);
                    tvTitle.setText("Recipe published!");
                    tvSubtitle.setText("Available in My Recipes");
                    tvPercent.setText("100%");
                    bar.setProgress(100);

                    viewModel.loadMyRecipes();
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        card.setVisibility(View.GONE);
                        com.cooksync.app.data.service.RecipePublishManager.getInstance().resetState();
                    }, 3000);
                }
                case ERROR -> {
                    spinner.setVisibility(View.GONE);
                    checkIcon.setVisibility(View.GONE);
                    tvTitle.setText("Publishing failed");
                    tvSubtitle.setText(state.error != null ? state.error : "Failed to publish recipe");
                    tvPercent.setText("");
                    bar.setProgress(0);
                }
            }
        });
    }

    private void selectVisibility(String visibility) {
        viewModel.setVisibilityFilter(visibility);
        styleChip(chipAll, "ALL".equals(visibility));
        styleChip(chipPublic, "PUBLIC".equals(visibility));
        styleChip(chipPrivate, "PRIVATE".equals(visibility));
    }

    /**
     * Updates the "Filters · N" button to reflect the currently active non-default filters
     * (difficulty, tags, minimum rating, and/or total time — sort is ignored since one sort
     * option is always selected). Reads the active filters directly from {@link #viewModel}.
     */
    private void updateFilterButton() {
        int count = (viewModel.getCurrentDifficulty() != null ? 1 : 0)
                + viewModel.getSelectedTags().size()
                + (viewModel.getCurrentMinRating() != null ? 1 : 0)
                + (viewModel.getCurrentMaxTotalTimeMinutes() != null ? 1 : 0);
        boolean active = count > 0;

        btnFilters.setText(getString(R.string.filters_count_format, count));
        btnFilters.setBackgroundTintList(ColorStateList.valueOf(
                active ? getColor(R.color.color_accent) : getColor(R.color.color_neutral_300)));
        btnFilters.setTextColor(active ? getColor(R.color.color_bg) : getColor(R.color.color_text));

        ColorStateList tint = ColorStateList.valueOf(active ? getColor(R.color.color_bg) : getColor(R.color.color_accent));
        btnFilters.setIconTint(tint);
    }

    /**
     * Builds the list of currently active search/filter constraints for the no-results state,
     * mirroring {@code SearchActivity}'s equivalent.
     */
    private List<NoResultsStateHelper.Constraint> buildRemovableConstraints() {
        List<NoResultsStateHelper.Constraint> constraints = new ArrayList<>();

        String query = viewModel.getCurrentQuery();
        if (query != null) {
            constraints.add(new NoResultsStateHelper.Constraint(
                    "\"" + query + "\"", () -> searchView.setQuery("", true)));
        }
        String difficulty = viewModel.getCurrentDifficulty();
        if (difficulty != null) {
            constraints.add(new NoResultsStateHelper.Constraint(difficulty, () -> {
                viewModel.removeDifficulty();
                updateFilterButton();
            }));
        }
        for (String tag : viewModel.getSelectedTags()) {
            constraints.add(new NoResultsStateHelper.Constraint(tag, () -> {
                viewModel.removeTag(tag);
                updateFilterButton();
            }));
        }
        Integer maxTotalTimeMinutes = viewModel.getCurrentMaxTotalTimeMinutes();
        if (maxTotalTimeMinutes != null) {
            constraints.add(new NoResultsStateHelper.Constraint(
                    getString(R.string.filters_applied_time_format, maxTotalTimeMinutes), () -> {
                        viewModel.removeMaxTotalTime();
                        updateFilterButton();
                    }));
        }
        Double minRating = viewModel.getCurrentMinRating();
        if (minRating != null) {
            constraints.add(new NoResultsStateHelper.Constraint(
                    getString(R.string.filters_applied_rating_format, minRating), () -> {
                        viewModel.removeMinRating();
                        updateFilterButton();
                    }));
        }
        return constraints;
    }

    private void showOptionsMenu(RecipePreviewResponse recipe, View anchor) {
        boolean isPublic = "PUBLIC".equalsIgnoreCase(recipe.visibility());

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_my_recipe_options, popup.getMenu());
        popup.getMenu().findItem(R.id.action_toggle_visibility)
                .setTitle(isPublic ? R.string.action_make_private : R.string.action_make_public);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit_recipe) {
                androidx.lifecycle.MutableLiveData<ApiResult<RecipeResponse>> target = new androidx.lifecycle.MutableLiveData<>();
                target.observe(this, res -> {
                    if (res instanceof ApiResult.Success<RecipeResponse> s) {
                        AddRecipeWizardActivity.startEdit(MyRecipesActivity.this, s.getData());
                    } else if (res instanceof ApiResult.Error<RecipeResponse> err) {
                        showError(err.getMessage(), bottomNav);
                    }
                });
                viewModel.fetchRecipeDetail(recipe.id(), target);
                return true;
            }
            if (id == R.id.action_toggle_visibility) {
                viewModel.toggleVisibility(recipe);
                String message = getString(isPublic ? R.string.recipe_now_private : R.string.recipe_now_public);
                OrganicToast.showWithAction(this, bottomNav, 0, message, getString(R.string.action_undo),
                        () -> viewModel.undoToggleVisibility(recipe));
                return true;
            }
            if (id == R.id.action_delete_recipe) {
                confirmDelete(recipe);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDelete(RecipePreviewResponse recipe) {
        OrganicConfirmDialog.show(this, getString(R.string.dialog_delete_recipe_title),
                getString(R.string.dialog_delete_recipe_message, recipe.title()),
                getString(R.string.action_delete), getString(R.string.action_cancel), true, () -> {
                    viewModel.deleteRecipe(recipe);
                });
    }
}
