package com.cooksync.app.data.model.recipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.dtos.request.ingredient.IngredientRequestDTO;
import com.dtos.request.instruction.InstructionRequestDTO;
import com.dtos.request.recipe.RecipeCreateRequestDTO;
import com.dtos.response.tags.TagResponse;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

/**
 * Unit tests for {@link RecipeDraftMapper}. Covers the field mapping itself, and the two rules
 * that only make sense at mapping time: blank ingredient/instruction rows are dropped, and
 * instruction step numbers are renumbered sequentially over the remaining, non-blank rows.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public class RecipeDraftMapperTest {

    private static RecipeDraft draftWithBasics() {
        RecipeDraft draft = new RecipeDraft();
        draft.title = "  Tomato Soup  ";
        draft.difficulty = "EASY";
        draft.visibility = "PUBLIC";
        draft.prepTimeMinutes = 10;
        draft.cookTimeMinutes = 20;
        draft.servings = 4;
        return draft;
    }

    @Test
    public void toDto_mapsBasicFields_andTrimsTitle() {
        RecipeCreateRequestDTO dto = RecipeDraftMapper.toDto(draftWithBasics());

        assertEquals("Tomato Soup", dto.title());
        assertEquals("EASY", dto.difficulty());
        assertEquals("PUBLIC", dto.visibility());
        assertEquals(10, dto.prepTimeMinutes());
        assertEquals(20, dto.cookTimeMinutes());
        assertEquals(4, dto.servings());
    }

    @Test
    public void toDto_mapsTagIds_fromDraftTags() {
        RecipeDraft draft = draftWithBasics();
        draft.tags.add(new TagResponse("tag-1", "Soup", null, null));
        draft.tags.add(new TagResponse("tag-2", "Vegetarian", null, null));

        RecipeCreateRequestDTO dto = RecipeDraftMapper.toDto(draft);

        assertEquals(List.of("tag-1", "tag-2"), dto.tagIds());
    }

    @Test
    public void toDto_skipsBlankIngredientRows() {
        RecipeDraft draft = draftWithBasics();
        RecipeDraft.DraftIngredient valid = new RecipeDraft.DraftIngredient();
        valid.name = "Tomato";
        valid.quantity = "2";
        valid.unitId = "unit-1";
        draft.ingredients.add(valid);
        draft.ingredients.add(new RecipeDraft.DraftIngredient()); // untouched placeholder row

        RecipeCreateRequestDTO dto = RecipeDraftMapper.toDto(draft);

        assertEquals(1, dto.ingredients().size());
        IngredientRequestDTO mapped = dto.ingredients().get(0);
        assertEquals("Tomato", mapped.name());
        assertEquals(2.0, mapped.quantity(), 0.0001);
        assertEquals("unit-1", mapped.unitId());
        assertEquals(valid.tmpId, mapped.tmpId());
    }

    @Test
    public void toDto_skipsBlankInstructionRows_andRenumbersRemainingSteps() {
        RecipeDraft draft = draftWithBasics();
        draft.instructions.add(new RecipeDraft.DraftInstruction()); // blank, dropped
        RecipeDraft.DraftInstruction first = new RecipeDraft.DraftInstruction();
        first.description = "Chop the tomatoes.";
        draft.instructions.add(first);
        RecipeDraft.DraftInstruction second = new RecipeDraft.DraftInstruction();
        second.description = "Simmer for 20 minutes.";
        draft.instructions.add(second);

        RecipeCreateRequestDTO dto = RecipeDraftMapper.toDto(draft);

        assertEquals(2, dto.instructions().size());
        assertEquals(1, dto.instructions().get(0).stepNumber());
        assertEquals("Chop the tomatoes.", dto.instructions().get(0).description());
        assertEquals(2, dto.instructions().get(1).stepNumber());
    }

    @Test
    public void toDto_omitsTimeSeconds_whenInstructionHasNoTimer() {
        RecipeDraft draft = draftWithBasics();
        RecipeDraft.DraftInstruction instruction = new RecipeDraft.DraftInstruction();
        instruction.description = "Chop the tomatoes.";
        instruction.hasTimer = false;
        instruction.timeSeconds = 300; // stale value from a timer the user removed
        draft.instructions.add(instruction);

        InstructionRequestDTO mapped = RecipeDraftMapper.toDto(draft).instructions().get(0);

        assertNull(mapped.timeSeconds());
    }

    @Test
    public void toDto_includesTimeSeconds_whenInstructionHasTimer() {
        RecipeDraft draft = draftWithBasics();
        RecipeDraft.DraftInstruction instruction = new RecipeDraft.DraftInstruction();
        instruction.description = "Simmer.";
        instruction.hasTimer = true;
        instruction.timeSeconds = 300;
        draft.instructions.add(instruction);

        InstructionRequestDTO mapped = RecipeDraftMapper.toDto(draft).instructions().get(0);

        assertTrue(mapped.hasTimer());
        assertEquals(Integer.valueOf(300), mapped.timeSeconds());
    }

    @Test
    public void toDto_mapsLinkedIngredientTmpIds_toUuidList() {
        RecipeDraft draft = draftWithBasics();
        RecipeDraft.DraftIngredient ingredient = new RecipeDraft.DraftIngredient();
        ingredient.name = "Tomato";
        ingredient.quantity = "2";
        ingredient.unitId = "unit-1";
        draft.ingredients.add(ingredient);

        RecipeDraft.DraftInstruction instruction = new RecipeDraft.DraftInstruction();
        instruction.description = "Add the tomato.";
        instruction.linkedIngredientTmpIds.add(ingredient.tmpId);
        draft.instructions.add(instruction);

        InstructionRequestDTO mapped = RecipeDraftMapper.toDto(draft).instructions().get(0);

        assertEquals(List.of(UUID.fromString(ingredient.tmpId)), mapped.ingredientIds());
    }
}
