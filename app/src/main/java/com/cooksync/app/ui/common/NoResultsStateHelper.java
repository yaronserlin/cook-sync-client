package com.cooksync.app.ui.common;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.view.LayoutInflater;
import android.view.View;

import com.cooksync.app.R;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/**
 * Populates the shared {@code layout_no_results_state.xml} include (icon, title, subtitle, and
 * one dismissible chip per active search constraint) so every recipe-list screen — Search,
 * Home, My Recipes, Favorites — gives the same "no results" feedback and lets the user remove
 * exactly the constraint(s) responsible, instead of each screen re-implementing its own variant.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public final class NoResultsStateHelper {

    private NoResultsStateHelper() {
    }

    /**
     * One active constraint (a search query, a selected tag, a difficulty, etc.) offered as a
     * removable chip in the no-results state.
     *
     * @param label the chip's display text
     * @param onRemove invoked when the chip's close icon is tapped
     */
    public record Constraint(String label, Runnable onRemove) {
    }

    /**
     * Rebuilds the no-results state's removable-constraint chips and toggles the "Clear all"
     * button, which only appears once there's more than one constraint (removing the sole chip
     * already clears everything).
     *
     * @param inflater a {@link LayoutInflater} for creating chip views
     * @param chipGroup the {@code cg_removable_constraints} ChipGroup from the included layout
     * @param clearAllButton the {@code btn_clear_all} button from the included layout
     * @param constraints every currently active constraint, in display order
     */
    public static void populate(LayoutInflater inflater, ChipGroup chipGroup, View clearAllButton,
                                 List<Constraint> constraints) {
        chipGroup.removeAllViews();
        for (Constraint constraint : constraints) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_removable_chip, chipGroup, false);
            chip.setText(constraint.label());
            chip.setOnCloseIconClickListener(v -> constraint.onRemove().run());
            chipGroup.addView(chip);
        }
        clearAllButton.setVisibility(constraints.size() > 1 ? View.VISIBLE : View.GONE);
    }
}
