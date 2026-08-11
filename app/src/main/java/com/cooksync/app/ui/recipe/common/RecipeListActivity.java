package com.cooksync.app.ui.recipe.common;
import com.cooksync.app.ui.recipe.myrecipes.MyRecipesActivity;
import com.cooksync.app.ui.recipe.favorites.FavoriteRecipesActivity;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;

import com.cooksync.app.R;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.common.NoResultsStateHelper;
import com.cooksync.app.ui.home.HomeActivity;
import com.cooksync.app.ui.recipe.wizard.AddRecipeWizardActivity;
import com.cooksync.app.ui.settings.SettingsActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared base for the "My Recipes" and "Favorites" screens, which are visually identical in
 * the design (title/subtitle, search, Filters button + chips, recipe list, bottom nav) and
 * differ only in their data source and which chips/trailing action they configure. Centralizing
 * the common view-binding, bottom-nav tab switching, skeleton toggling, and chip-building here
 * keeps the two subclasses from drifting apart into two copies of the same screen.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public abstract class RecipeListActivity extends BaseActivity {

    protected TextView tvTitle;
    protected TextView tvSubtitle;
    protected SearchView searchView;
    protected MaterialButton btnFilters;
    protected LinearLayout chipContainer;
    protected RecyclerView rvList;
    protected View emptyState;
    protected ImageView ivEmptyIcon;
    protected TextView tvEmptyTitle;
    protected TextView tvEmptySubtitle;
    protected View noResultsState;
    /** Exposed so subclasses can anchor an {@link com.cooksync.app.ui.common.OrganicToast} above it. */
    protected BottomNavigationView bottomNav;
    private ChipGroup cgRemovableConstraints;
    private View btnClearAll;
    private FloatingActionButton fabAddRecipe;

    /** Which bottom-nav item corresponds to this screen. */
    @IdRes
    protected abstract int getSelectedNavItemId();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_list);

        tvTitle = findViewById(R.id.tv_list_title);
        tvSubtitle = findViewById(R.id.tv_list_subtitle);
        searchView = findViewById(R.id.search_view);
        btnFilters = findViewById(R.id.btn_filters);
        chipContainer = findViewById(R.id.chip_container);
        rvList = findViewById(R.id.rv_list);
        emptyState = findViewById(R.id.empty_state);
        ivEmptyIcon = findViewById(R.id.iv_empty_icon);
        tvEmptyTitle = findViewById(R.id.tv_empty_title);
        tvEmptySubtitle = findViewById(R.id.tv_empty_subtitle);
        noResultsState = findViewById(R.id.no_results_state);
        cgRemovableConstraints = noResultsState.findViewById(R.id.cg_removable_constraints);
        btnClearAll = noResultsState.findViewById(R.id.btn_clear_all);

        setupSkeleton(R.id.skeleton_view);

        setupBottomNav();
        setupCreateRecipeFab();
    }

    /**
     * Shows the "Create recipe" FAB — present on every tabbed screen per the design (Home, My
     * Recipes, Favorites, Profile all show it via the shared bottom-nav chrome), so this layout
     * (shared by My Recipes and Favorites) always shows and wires it up.
     */
    private void setupCreateRecipeFab() {
        fabAddRecipe = findViewById(R.id.fab_add_recipe);
        fabAddRecipe.setVisibility(View.VISIBLE);
        fabAddRecipe.setOnClickListener(v -> Navigator.start(this, AddRecipeWizardActivity.class));
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(getSelectedNavItemId());
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == getSelectedNavItemId()) {
                return true;
            }
            Class<? extends AppCompatActivity> target;
            if (id == R.id.nav_home) {
                target = HomeActivity.class;
            } else if (id == R.id.nav_my_recipes) {
                target = MyRecipesActivity.class;
            } else if (id == R.id.nav_favorites) {
                target = FavoriteRecipesActivity.class;
            } else if (id == R.id.nav_settings) {
                target = SettingsActivity.class;
            } else {
                return false;
            }
            Intent extras = new Intent();
            extras.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Navigator.start(this, target, extras);
            Navigator.finish(this);
            return true;
        });
    }

    /**
     * Toggles between the shimmer skeleton and the real list.
     *
     * @param show {@code true} to show the skeleton and hide the list
     */
    protected void showSkeleton(boolean show) {
        showSkeleton(show, rvList);
        if (searchView != null) searchView.setVisibility(show ? View.GONE : View.VISIBLE);
        if (btnFilters != null) btnFilters.setVisibility(show ? View.GONE : View.VISIBLE);
        if (chipContainer != null) chipContainer.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * Wires {@link #searchView} to invoke {@code onQueryChange} on every submit and every
     * keystroke, matching the "live filter as you type" search behavior shared by My Recipes
     * and Favorites (unlike {@code SearchActivity}, neither screen debounces or hits the
     * network — both filter an already-loaded local list).
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param onQueryChange invoked with the current query text on submit and on every change
     */
    protected void setupSearchListener(Consumer<String> onQueryChange) {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                onQueryChange.accept(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                onQueryChange.accept(newText);
                return true;
            }
        });
    }

    /**
     * Registers the "Clear all" action for the shared no-results state (see
     * {@link #showNoResultsState}). Must be called once, before the first
     * {@link #showNoResultsState} call.
     *
     * @param onClearAll invoked when the no-results state's "Clear all" button is tapped
     */
    protected void setOnClearAllClickListener(Runnable onClearAll) {
        btnClearAll.setOnClickListener(v -> onClearAll.run());
    }

    /**
     * Shows the no-results state (hiding {@link #rvList} and the generic {@link #emptyState}),
     * populated with one removable chip per active search/filter constraint — used when the
     * library has recipes but the current search/filters matched none of them, as opposed to
     * the library being genuinely empty.
     *
     * @param constraints every currently active constraint, in display order
     */
    protected void showNoResultsState(List<NoResultsStateHelper.Constraint> constraints) {
        rvList.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        noResultsState.setVisibility(View.VISIBLE);
        NoResultsStateHelper.populate(getLayoutInflater(), cgRemovableConstraints, btnClearAll, constraints);
    }

    /** Hides the no-results state, e.g. once a search/filter change yields results again. */
    protected void hideNoResultsState() {
        noResultsState.setVisibility(View.GONE);
    }

    /**
     * Builds and appends one pill-shaped filter chip to {@link #chipContainer}, matching the
     * styling of the shared "Filters · N" button's active/inactive states.
     *
     * @param label the chip's display text
     * @param active whether the chip should render in its selected state
     * @param onClick invoked when the chip is tapped
     * @return the created chip, so callers can restyle it later (e.g. on selection change)
     */
    @NonNull
    protected TextView addChip(String label, boolean active, Runnable onClick) {
        TextView chip = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(36));
        params.setMarginEnd(dpToPx(8));
        chip.setLayoutParams(params);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dpToPx(14), 0, dpToPx(14), 0);
        chip.setTextSize(12.5f);
        chip.setTypeface(chip.getTypeface(), Typeface.BOLD);
        chip.setText(label);
        styleChip(chip, active);
        chip.setOnClickListener(v -> onClick.run());
        chipContainer.addView(chip);
        return chip;
    }

    /**
     * Restyles an existing chip's active/inactive appearance without rebuilding it.
     *
     * @param chip the chip to restyle
     * @param active whether it should render in its selected state
     */
    protected void styleChip(TextView chip, boolean active) {
        chip.setBackgroundResource(active ? R.drawable.bg_filters_active : R.drawable.bg_tag_neutral);
        chip.setTextColor(getColor(active ? R.color.color_bg : R.color.color_text));
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
