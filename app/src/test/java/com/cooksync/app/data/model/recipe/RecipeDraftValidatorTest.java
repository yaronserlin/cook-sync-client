package com.cooksync.app.data.model.recipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link RecipeDraftValidator}. Covers per-step gating (whether the wizard's
 * "Next" button should be enabled) and the underlying field-level rules it's built from, since
 * a draft that passes here must also pass the server's mirrored constraints.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class RecipeDraftValidatorTest {

    private static RecipeDraft validBasics() {
        RecipeDraft draft = new RecipeDraft();
        draft.title = "Tomato Soup";
        draft.difficulty = "EASY";
        draft.prepTimeMinutes = 10;
        draft.cookTimeMinutes = 20;
        draft.servings = 4;
        return draft;
    }

    private static RecipeDraft.DraftIngredient validIngredient() {
        RecipeDraft.DraftIngredient ingredient = new RecipeDraft.DraftIngredient();
        ingredient.name = "Tomato";
        ingredient.quantity = "2";
        ingredient.unitId = "unit-1";
        return ingredient;
    }

    private static RecipeDraft.DraftInstruction validInstruction() {
        RecipeDraft.DraftInstruction instruction = new RecipeDraft.DraftInstruction();
        instruction.description = "Simmer the tomatoes for 20 minutes.";
        return instruction;
    }

    // ── isStepValid(STEP_BASICS) ─────────────────────────────────────

    @Test
    public void isStepValid_basics_true_whenEveryRequiredFieldSet() {
        assertTrue(RecipeDraftValidator.isStepValid(validBasics(), RecipeDraftValidator.STEP_BASICS));
    }

    @Test
    public void isStepValid_basics_false_whenTitleBlank() {
        RecipeDraft draft = validBasics();
        draft.title = "   ";
        assertFalse(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_BASICS));
    }

    @Test
    public void isStepValid_basics_false_whenDifficultyUnset() {
        RecipeDraft draft = validBasics();
        draft.difficulty = null;
        assertFalse(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_BASICS));
    }

    @Test
    public void isStepValid_basics_false_whenServingsBelowOne() {
        RecipeDraft draft = validBasics();
        draft.servings = 0;
        assertFalse(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_BASICS));
    }

    @Test
    public void isTitleValid_false_forNullOrWhitespace() {
        RecipeDraft draft = new RecipeDraft();
        draft.title = null;
        assertFalse(RecipeDraftValidator.isTitleValid(draft));
        draft.title = "   ";
        assertFalse(RecipeDraftValidator.isTitleValid(draft));
    }

    // ── isStepValid(STEP_INGREDIENTS) ────────────────────────────────

    @Test
    public void isStepValid_ingredients_true_withOneValidRow() {
        RecipeDraft draft = new RecipeDraft();
        draft.ingredients.add(validIngredient());
        assertTrue(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_INGREDIENTS));
    }

    @Test
    public void isStepValid_ingredients_false_whenAllRowsBlank() {
        RecipeDraft draft = new RecipeDraft();
        draft.ingredients.add(new RecipeDraft.DraftIngredient());
        assertFalse(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_INGREDIENTS));
    }

    @Test
    public void isStepValid_ingredients_false_whenNonBlankRowMissingUnit() {
        RecipeDraft draft = new RecipeDraft();
        RecipeDraft.DraftIngredient ingredient = validIngredient();
        ingredient.unitId = null;
        draft.ingredients.add(ingredient);
        assertFalse(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_INGREDIENTS));
    }

    @Test
    public void isIngredientBlank_true_forUntouchedRow() {
        assertTrue(RecipeDraftValidator.isIngredientBlank(new RecipeDraft.DraftIngredient()));
    }

    @Test
    public void isIngredientBlank_false_onceAnyFieldFilled() {
        RecipeDraft.DraftIngredient ingredient = new RecipeDraft.DraftIngredient();
        ingredient.name = "Salt";
        assertFalse(RecipeDraftValidator.isIngredientBlank(ingredient));
    }

    @Test
    public void countValidIngredients_countsOnlyFullyValidRows() {
        RecipeDraft draft = new RecipeDraft();
        draft.ingredients.add(validIngredient());
        draft.ingredients.add(new RecipeDraft.DraftIngredient());
        RecipeDraft.DraftIngredient invalid = validIngredient();
        invalid.unitId = null;
        draft.ingredients.add(invalid);
        assertEquals(1, RecipeDraftValidator.countValidIngredients(draft));
    }

    // ── parsePositiveQuantity ─────────────────────────────────────────

    @Test
    public void parsePositiveQuantity_parsesValidPositiveNumber() {
        assertEquals(Double.valueOf(2.5), RecipeDraftValidator.parsePositiveQuantity("2.5"));
    }

    @Test
    public void parsePositiveQuantity_null_forZeroOrNegative() {
        assertNull(RecipeDraftValidator.parsePositiveQuantity("0"));
        assertNull(RecipeDraftValidator.parsePositiveQuantity("-1"));
    }

    @Test
    public void parsePositiveQuantity_null_forNonNumericOrBlank() {
        assertNull(RecipeDraftValidator.parsePositiveQuantity("abc"));
        assertNull(RecipeDraftValidator.parsePositiveQuantity(""));
        assertNull(RecipeDraftValidator.parsePositiveQuantity(null));
    }

    // ── isStepValid(STEP_INSTRUCTIONS) ───────────────────────────────

    @Test
    public void isStepValid_instructions_true_withOneValidRow() {
        RecipeDraft draft = new RecipeDraft();
        draft.instructions.add(validInstruction());
        assertTrue(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_INSTRUCTIONS));
    }

    @Test
    public void isStepValid_instructions_false_whenNonBlankRowHasNoDescription() {
        RecipeDraft draft = new RecipeDraft();
        RecipeDraft.DraftInstruction instruction = new RecipeDraft.DraftInstruction();
        instruction.hasTimer = true; // makes the row non-blank, but description is still empty
        draft.instructions.add(instruction);
        assertFalse(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_INSTRUCTIONS));
    }

    @Test
    public void isInstructionBlank_false_whenTimerSetEvenWithoutDescription() {
        RecipeDraft.DraftInstruction instruction = new RecipeDraft.DraftInstruction();
        instruction.hasTimer = true;
        assertFalse(RecipeDraftValidator.isInstructionBlank(instruction));
    }

    @Test
    public void countInstructionsWithTimer_countsOnlyTimedSteps() {
        RecipeDraft draft = new RecipeDraft();
        RecipeDraft.DraftInstruction timed = validInstruction();
        timed.hasTimer = true;
        draft.instructions.add(timed);
        draft.instructions.add(validInstruction());
        assertEquals(1, RecipeDraftValidator.countInstructionsWithTimer(draft));
    }

    // ── isStepValid(STEP_REVIEW) ──────────────────────────────────────

    @Test
    public void isStepValid_review_true_onlyWhenEveryPriorStepValid() {
        RecipeDraft draft = validBasics();
        draft.ingredients.add(validIngredient());
        draft.instructions.add(validInstruction());
        assertTrue(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_REVIEW));
    }

    @Test
    public void isStepValid_review_false_whenInstructionsStepIncomplete() {
        RecipeDraft draft = validBasics();
        draft.ingredients.add(validIngredient());
        // No instructions added.
        assertFalse(RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_REVIEW));
    }

    @Test
    public void isStepValid_unknownStep_returnsFalse() {
        assertFalse(RecipeDraftValidator.isStepValid(validBasics(), 99));
    }
}
