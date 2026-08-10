package com.cooksync.app.data.model.recipe;

/**
 * Stateless validation rules for a {@link RecipeDraft}, mirroring the constraints declared on
 * the server's {@code RecipeCreateRequestDTO} (and its nested ingredient/instruction DTOs) so a
 * draft that passes here always passes server-side validation too. Single source of truth for
 * both per-step gating (whether the wizard's "Next" button is enabled) and the review step's
 * readiness checklist.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public final class RecipeDraftValidator {

    /** Wizard step index for Basics. */
    public static final int STEP_BASICS = 0;
    /** Wizard step index for Ingredients. */
    public static final int STEP_INGREDIENTS = 1;
    /** Wizard step index for Instructions. */
    public static final int STEP_INSTRUCTIONS = 2;
    /** Wizard step index for Review. */
    public static final int STEP_REVIEW = 3;

    private RecipeDraftValidator() {
    }

    /**
     * Checks whether a single wizard step's required fields are filled in.
     *
     * Complexity:
     * Time: O(n) where n is the number of ingredients/instructions in the draft
     * Space: O(1)
     *
     * @param draft the draft to validate
     * @param step one of the {@code STEP_*} constants
     * @return {@code true} if the step's required fields are all valid
     */
    public static boolean isStepValid(RecipeDraft draft, int step) {
        return switch (step) {
            case STEP_BASICS -> isBasicsValid(draft);
            case STEP_INGREDIENTS -> isIngredientsValid(draft);
            case STEP_INSTRUCTIONS -> isInstructionsValid(draft);
            case STEP_REVIEW -> isBasicsValid(draft) && isIngredientsValid(draft) && isInstructionsValid(draft);
            default -> false;
        };
    }

    private static boolean isBasicsValid(RecipeDraft draft) {
        return isTitleValid(draft)
                && isDifficultySet(draft)
                && draft.prepTimeMinutes != null && draft.prepTimeMinutes >= 0
                && draft.cookTimeMinutes != null && draft.cookTimeMinutes >= 0
                && draft.servings != null && draft.servings >= 1;
    }

    /** @return {@code true} if the recipe title has been filled in */
    public static boolean isTitleValid(RecipeDraft draft) {
        return draft.title != null && !draft.title.trim().isEmpty();
    }

    /** @return {@code true} if a difficulty level has been chosen */
    public static boolean isDifficultySet(RecipeDraft draft) {
        return draft.difficulty != null && !draft.difficulty.isEmpty();
    }

    private static boolean isIngredientsValid(RecipeDraft draft) {
        if (draft.ingredients.isEmpty()) {
            return false;
        }
        for (RecipeDraft.DraftIngredient ingredient : draft.ingredients) {
            if (!isIngredientValid(ingredient)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIngredientValid(RecipeDraft.DraftIngredient ingredient) {
        return ingredient.name != null && !ingredient.name.trim().isEmpty()
                && parsePositiveQuantity(ingredient.quantity) != null
                && ingredient.unitId != null && !ingredient.unitId.isEmpty();
    }

    private static boolean isInstructionsValid(RecipeDraft draft) {
        if (draft.instructions.isEmpty()) {
            return false;
        }
        for (RecipeDraft.DraftInstruction instruction : draft.instructions) {
            if (instruction.description == null || instruction.description.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a raw quantity string into a positive {@link Double}, or {@code null} if it isn't
     * a valid positive number (matching the server's {@code @Positive} constraint).
     *
     * Complexity:
     * Time: O(n) where n is the string length
     * Space: O(1)
     *
     * @param rawQuantity the raw text entered for an ingredient's quantity
     * @return the parsed value, or {@code null} if invalid
     */
    public static Double parsePositiveQuantity(String rawQuantity) {
        if (rawQuantity == null || rawQuantity.trim().isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(rawQuantity.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @param draft the draft to inspect
     * @return {@code true} if every ingredient row has a name, positive quantity, and unit
     */
    public static boolean areAllIngredientsValid(RecipeDraft draft) {
        return isIngredientsValid(draft);
    }

    /**
     * @param draft the draft to inspect
     * @return how many ingredient rows currently pass validation
     */
    public static int countValidIngredients(RecipeDraft draft) {
        int count = 0;
        for (RecipeDraft.DraftIngredient ingredient : draft.ingredients) {
            if (isIngredientValid(ingredient)) count++;
        }
        return count;
    }

    /**
     * @param draft the draft to inspect
     * @return {@code true} if every instruction step has a non-blank description
     */
    public static boolean areAllInstructionsValid(RecipeDraft draft) {
        return isInstructionsValid(draft);
    }

    /**
     * @param draft the draft to inspect
     * @return how many instruction steps have a timer set
     */
    public static int countInstructionsWithTimer(RecipeDraft draft) {
        int count = 0;
        for (RecipeDraft.DraftInstruction instruction : draft.instructions) {
            if (instruction.hasTimer) count++;
        }
        return count;
    }
}
