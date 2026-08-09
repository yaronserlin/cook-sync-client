package com.cooksync.app.ui.recipe.wizard;

import com.dtos.response.recipe.DescriptionBlockDTO;
import com.dtos.response.tags.TagResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plain mutable holder for the recipe currently being authored in {@link AddRecipeWizardActivity}.
 * Every wizard step (basics, ingredients, instructions, review) reads and writes a disjoint
 * slice of this object through {@link AddRecipeViewModel}, and the whole object is what
 * {@link com.cooksync.app.data.local.RecipeDraftStore} serializes for "Save draft"/resume, and
 * what {@link RecipeDraftMapper} converts into a {@code RecipeCreateRequestDTO} for publishing.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class RecipeDraft {

    /** One row of {@link #ingredients}, keyed by a client-generated {@link #tmpId} so
     *  {@link DraftInstruction#linkedIngredientTmpIds} can reference it before the recipe (and
     *  its ingredients) exist server-side. */
    public static class DraftIngredient {
        public String tmpId = UUID.randomUUID().toString();
        public String name = "";
        public String quantity = "";
        public String unitId;
        public String unitLabel;
    }

    /** One step of {@link #instructions}. */
    public static class DraftInstruction {
        public String description = "";
        public boolean hasTimer = false;
        public Integer timeSeconds;
        public List<String> linkedIngredientTmpIds = new ArrayList<>();
        public String imageUrl;
    }

    public String editingRecipeId = null;
    public String title = "";
    public List<DescriptionBlockDTO> descriptionBlocks = new ArrayList<>();
    /** Null means "not yet entered" — prep/cook/servings are all mandatory before publishing. */
    public Integer prepTimeMinutes;
    public Integer cookTimeMinutes;
    public Integer servings;
    public String difficulty;
    public List<TagResponse> tags = new ArrayList<>();
    /** Tag names the user picked that don't exist yet — created server-side only at publish time. */
    public List<String> pendingNewTagNames = new ArrayList<>();
    public List<DraftIngredient> ingredients = new ArrayList<>();
    public List<DraftInstruction> instructions = new ArrayList<>();
    public String primaryImageUrl;
    public String visibility = "PRIVATE";

    /** Which wizard step (0-3) the user last reached, so resuming a saved draft reopens there. */
    public int lastReachedStep = 0;

    /** Epoch millis of the last "Save draft", used for the pinned draft card's relative timestamp. */
    public long savedAtMillis = 0L;

    /**
     * Converts an existing server {@link com.dtos.response.recipe.RecipeResponse} into an
     * in-progress {@link RecipeDraft} ready for editing in the wizard.
     *
     * Complexity:
     * Time: O(n + m) where n is ingredient count and m is instruction step count
     * Space: O(n + m)
     *
     * @param recipe the server recipe model to edit
     * @return equivalent RecipeDraft prepopulated with recipe attributes
     */
    public static RecipeDraft fromRecipeResponse(com.dtos.response.recipe.RecipeResponse recipe) {
        RecipeDraft draft = new RecipeDraft();
        draft.editingRecipeId = recipe.id();
        draft.title = recipe.title() != null ? recipe.title() : "";
        draft.difficulty = recipe.difficulty();
        draft.visibility = recipe.visibility() != null ? recipe.visibility() : "PRIVATE";
        draft.prepTimeMinutes = recipe.prepTimeMinutes();
        draft.cookTimeMinutes = recipe.cookTimeMinutes();
        draft.servings = recipe.servings();
        draft.primaryImageUrl = recipe.primaryImageUrl();

        if (recipe.descriptionBlocks() != null) {
            draft.descriptionBlocks = new ArrayList<>(recipe.descriptionBlocks());
        }

        if (recipe.tags() != null) {
            draft.tags = new ArrayList<>(recipe.tags());
        }

        java.util.Map<String, String> ingredientIdToTmpId = new java.util.HashMap<>();
        if (recipe.ingredients() != null) {
            for (com.dtos.response.ingredient.IngredientResponse ing : recipe.ingredients()) {
                DraftIngredient item = new DraftIngredient();
                if (ing.id() != null) {
                    item.tmpId = ing.id();
                }
                ingredientIdToTmpId.put(ing.id(), item.tmpId);
                item.name = ing.name() != null ? ing.name() : "";
                item.quantity = ing.quantity() != null ? ing.quantity().stripTrailingZeros().toPlainString() : "";
                if (ing.unit() != null) {
                    item.unitId = ing.unit().id();
                    item.unitLabel = ing.unit().name() + (ing.unit().code() != null ? " (" + ing.unit().code() + ")" : "");
                }
                draft.ingredients.add(item);
            }
        }

        if (recipe.instructions() != null) {
            List<com.dtos.response.instruction.InstructionResponse> sortedSteps = new ArrayList<>(recipe.instructions());
            sortedSteps.sort(java.util.Comparator.comparingInt(com.dtos.response.instruction.InstructionResponse::stepNumber));

            for (com.dtos.response.instruction.InstructionResponse inst : sortedSteps) {
                DraftInstruction item = new DraftInstruction();
                item.description = inst.description() != null ? inst.description() : "";
                item.hasTimer = Boolean.TRUE.equals(inst.hasTimer());
                item.timeSeconds = inst.timeSeconds();
                item.imageUrl = inst.imageUrl();

                if (inst.ingredients() != null) {
                    for (com.dtos.response.ingredient.IngredientResponse linked : inst.ingredients()) {
                        String tmpId = ingredientIdToTmpId.get(linked.id());
                        if (tmpId != null) {
                            item.linkedIngredientTmpIds.add(tmpId);
                        }
                    }
                }
                draft.instructions.add(item);
            }
        }

        return draft;
    }
}
