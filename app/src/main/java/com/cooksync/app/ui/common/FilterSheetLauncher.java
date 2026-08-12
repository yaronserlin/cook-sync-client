package com.cooksync.app.ui.common;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import androidx.fragment.app.FragmentManager;

import com.cooksync.app.ui.recipe.common.FiltersBottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

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

        /** Drops the active difficulty filter alone, leaving every other constraint untouched. */
        void removeDifficulty();

        /** Drops a single selected tag alone, leaving every other constraint untouched. */
        void removeTag(String tagName);

        /** Drops the active minimum-rating filter alone, leaving every other constraint untouched. */
        void removeMinRating();

        /** Drops the active total-time filter alone, leaving every other constraint untouched. */
        void removeMaxTotalTime();

        /**
         * How many of the four shared filter dimensions (difficulty, tags, rating, time) are
         * currently active — sort is deliberately excluded since one sort option is always
         * selected. Every screen's "Filters · N" button badge reads this instead of
         * recomputing the same sum itself.
         *
         * Complexity:
         * Time: O(1)
         * Space: O(1)
         *
         * @return the count of active shared filter dimensions
         */
        default int getActiveFilterCount() {
            return (getCurrentDifficulty() != null ? 1 : 0) + getSelectedTags().size()
                    + (getCurrentMinRating() != null ? 1 : 0)
                    + (getCurrentMaxTotalTimeMinutes() != null ? 1 : 0);
        }

        /**
         * Builds one removable {@link NoResultsStateHelper.Constraint} per active shared filter
         * dimension (difficulty, each selected tag, total time, minimum rating), each wired to
         * remove itself via this {@code FilterState}. Screen-specific extras that aren't part of
         * this shared state (a search query, a visibility toggle, an "only with notes" flag) are
         * the caller's responsibility to prepend/append, since those don't exist here.
         *
         * <p>Label formatting for time/rating is injected rather than done here, since a
         * {@code FilterState} is implemented by a ViewModel and must stay free of Android
         * {@code Context}/resource access.</p>
         *
         * Complexity:
         * Time: O(k) where k is the number of active filter dimensions
         * Space: O(k)
         *
         * @param timeLabel formats a total-time-minutes value into its chip label
         * @param ratingLabel formats a minimum-rating value into its chip label
         * @return the active shared constraints, in display order
         */
        default List<NoResultsStateHelper.Constraint> buildRemovableConstraints(
                Function<Integer, String> timeLabel, Function<Double, String> ratingLabel) {
            List<NoResultsStateHelper.Constraint> constraints = new ArrayList<>();
            String difficulty = getCurrentDifficulty();
            if (difficulty != null) {
                constraints.add(new NoResultsStateHelper.Constraint(difficulty, this::removeDifficulty));
            }
            for (String tag : getSelectedTags()) {
                constraints.add(new NoResultsStateHelper.Constraint(tag, () -> removeTag(tag)));
            }
            Integer maxTotalTimeMinutes = getCurrentMaxTotalTimeMinutes();
            if (maxTotalTimeMinutes != null) {
                constraints.add(new NoResultsStateHelper.Constraint(
                        timeLabel.apply(maxTotalTimeMinutes), this::removeMaxTotalTime));
            }
            Double minRating = getCurrentMinRating();
            if (minRating != null) {
                constraints.add(new NoResultsStateHelper.Constraint(
                        ratingLabel.apply(minRating), this::removeMinRating));
            }
            return constraints;
        }
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
