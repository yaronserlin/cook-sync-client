package com.cooksync.app.ui.recipe.wizard;

import com.dtos.request.ingredient.IngredientRequestDTO;
import com.dtos.request.instruction.InstructionRequestDTO;
import com.dtos.request.recipe.RecipeCreateRequestDTO;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Converts a publish-ready {@link RecipeDraft} into the {@code RecipeCreateRequestDTO} the
 * server's {@code POST /api/recipes} endpoint expects. Each {@link RecipeDraft.DraftIngredient}
 * carries a client-generated UUID {@code tmpId}, which doubles as the identifier
 * {@link RecipeDraft.DraftInstruction#linkedIngredientTmpIds} references — the server
 * correlates {@code IngredientRequestDTO#tmpId} against each instruction's
 * {@code ingredientIds} to link freshly created ingredients to the steps that use them.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public final class RecipeDraftMapper {

    private RecipeDraftMapper() {
    }

    /**
     * Maps a draft to its server request payload. Only called once the draft has passed
     * {@link RecipeDraftValidator#isStepValid(RecipeDraft, int)} for
     * {@link RecipeDraftValidator#STEP_REVIEW}.
     *
     * Complexity:
     * Time: O(n) where n is the combined number of ingredients and instructions
     * Space: O(n)
     *
     * @param draft the draft to convert
     * @return the equivalent create-recipe request payload
     */
    public static RecipeCreateRequestDTO toDto(RecipeDraft draft) {
        List<String> tagIds = new ArrayList<>();
        for (TagResponse tag : draft.tags) {
            tagIds.add(tag.id());
        }

        List<IngredientRequestDTO> ingredients = new ArrayList<>();
        for (RecipeDraft.DraftIngredient ingredient : draft.ingredients) {
            Double quantity = RecipeDraftValidator.parsePositiveQuantity(ingredient.quantity);
            ingredients.add(new IngredientRequestDTO(
                    ingredient.tmpId,
                    ingredient.name.trim(),
                    quantity == null ? 0 : quantity,
                    ingredient.unitId
            ));
        }

        List<InstructionRequestDTO> instructions = new ArrayList<>();
        for (int i = 0; i < draft.instructions.size(); i++) {
            RecipeDraft.DraftInstruction instruction = draft.instructions.get(i);
            List<UUID> ingredientIds = new ArrayList<>();
            for (String tmpId : instruction.linkedIngredientTmpIds) {
                ingredientIds.add(UUID.fromString(tmpId));
            }
            instructions.add(new InstructionRequestDTO(
                    i + 1,
                    instruction.description.trim(),
                    instruction.hasTimer,
                    instruction.hasTimer ? instruction.timeSeconds : null,
                    ingredientIds,
                    instruction.imageUrl
            ));
        }

        return new RecipeCreateRequestDTO(
                draft.title.trim(),
                draft.difficulty,
                draft.visibility,
                draft.prepTimeMinutes == null ? 0 : draft.prepTimeMinutes,
                draft.cookTimeMinutes == null ? 0 : draft.cookTimeMinutes,
                draft.servings == null ? 1 : draft.servings,
                tagIds,
                ingredients,
                instructions,
                draft.primaryImageUrl,
                draft.descriptionBlocks
        );
    }
}
