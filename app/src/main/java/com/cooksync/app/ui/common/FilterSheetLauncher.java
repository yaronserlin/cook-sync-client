package com.cooksync.app.ui.common;

import androidx.fragment.app.FragmentManager;

import com.cooksync.app.ui.recipe.FiltersBottomSheetDialogFragment;

import java.util.List;
import java.util.Set;

/**
 * Builds, seeds, and shows a {@link FiltersBottomSheetDialogFragment} against a ViewModel's
 * current filter state. Every screen that offers the shared sort/difficulty/tags/rating/time
 * filter sheet (Home, Search, My Recipes, Favorites) previously duplicated the same
 * construct/seed/show sequence; this centralizes it so the sheet is always seeded and wired
 * up identically everywhere it appears.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 07/08/2026
 */
public final class FilterSheetLauncher {

    /**
     * The subset of filter state a screen's ViewModel must expose to pre-seed the filter
     * sheet with whatever is currently active. Every filter-capable ViewModel already exposes
     * these exact getters, so implementing this interface is a no-op declaration for them.
     */
    public interface FilterState {
        String getCurrentSort();

        String getCurrentDifficulty();

        Set<String> getSelectedTags();

        Double getCurrentMinRating();

        Integer getCurrentMaxTotalTimeMinutes();
    }

    private FilterSheetLauncher() {
    }

    /**
     * Builds a {@link FiltersBottomSheetDialogFragment}, seeds it with {@code availableTags}
     * and the current values from {@code state}, attaches {@code onApplied}, and shows it.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param fragmentManager the host screen's fragment manager
     * @param availableTags tag names known to the app, for the sheet's tag chips
     * @param state the ViewModel supplying the sheet's initial selection
     * @param onApplied invoked with the chosen values when the user taps "Apply"
     */
    public static void show(FragmentManager fragmentManager, List<String> availableTags, FilterState state,
                             FiltersBottomSheetDialogFragment.OnFiltersAppliedListener onApplied) {
        FiltersBottomSheetDialogFragment dialog = new FiltersBottomSheetDialogFragment();
        dialog.setAvailableTags(availableTags);
        dialog.setInitialState(state.getCurrentSort(), state.getCurrentDifficulty(), state.getSelectedTags(),
                state.getCurrentMinRating(), state.getCurrentMaxTotalTimeMinutes());
        dialog.setOnFiltersAppliedListener(onApplied);
        dialog.show(fragmentManager, "filters");
    }
}
