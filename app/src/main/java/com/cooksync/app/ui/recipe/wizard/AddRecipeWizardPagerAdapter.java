package com.cooksync.app.ui.recipe.wizard;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Supplies {@link AddRecipeWizardActivity}'s four steps — Basics, Ingredients, Instructions,
 * Review — to its {@link androidx.viewpager2.widget.ViewPager2}, following the same
 * {@link FragmentStateAdapter} pattern {@code AdminPagerAdapter} uses for the Admin Console's
 * tabs.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class AddRecipeWizardPagerAdapter extends FragmentStateAdapter {

    public static final int STEP_BASICS = RecipeDraftValidator.STEP_BASICS;
    public static final int STEP_INGREDIENTS = RecipeDraftValidator.STEP_INGREDIENTS;
    public static final int STEP_INSTRUCTIONS = RecipeDraftValidator.STEP_INSTRUCTIONS;
    public static final int STEP_REVIEW = RecipeDraftValidator.STEP_REVIEW;
    public static final int STEP_COUNT = 4;

    public AddRecipeWizardPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @Override
    public int getItemCount() {
        return STEP_COUNT;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return switch (position) {
            case STEP_BASICS -> new WizardBasicsFragment();
            case STEP_INGREDIENTS -> new WizardIngredientsFragment();
            case STEP_INSTRUCTIONS -> new WizardInstructionsFragment();
            default -> new WizardReviewFragment();
        };
    }
}
