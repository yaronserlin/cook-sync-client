package com.cooksync.app.ui.recipe.wizard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.local.RecipeDraftStore;
import com.cooksync.app.data.repository.MediaRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.data.repository.UnitRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.common.BaseViewModel;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.recipe.DescriptionBlockDTO;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.tags.TagResponse;
import com.dtos.response.unit.UnitResponse;

import java.util.List;
import java.util.Objects;

/**
 * Activity-scoped ViewModel shared by every step fragment of {@link AddRecipeWizardActivity}.
 * Owns the single {@link RecipeDraft} being authored, exposes it to each fragment for reading
 * and in-place mutation of its own slice, and orchestrates every network call the wizard needs
 * (tags, units, Cloudinary signatures, publishing) plus local draft persistence via
 * {@link RecipeDraftStore}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public class AddRecipeViewModel extends BaseViewModel {

    private final RecipeRepository recipeRepository;
    private final TagRepository tagRepository;
    private final UnitRepository unitRepository;
    private final MediaRepository mediaRepository;

    private RecipeDraft draft = new RecipeDraft();

    private final MutableLiveData<ApiResult<List<TagResponse>>> tagsResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<UnitResponse>>> unitsResult = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiResult<RecipeResponse>>> publishResult = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.common.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param recipeRepository the repository used to publish the finished recipe
     * @param tagRepository the repository used to load/create tags
     * @param unitRepository the repository used to load measurement units
     * @param mediaRepository the repository used to request Cloudinary upload signatures
     */
    public AddRecipeViewModel(RecipeRepository recipeRepository, TagRepository tagRepository,
                               UnitRepository unitRepository, MediaRepository mediaRepository) {
        this.recipeRepository = recipeRepository;
        this.tagRepository = tagRepository;
        this.unitRepository = unitRepository;
        this.mediaRepository = mediaRepository;
    }

    // ── Draft lifecycle ──────────────────────────────────────────────

    /**
     * Returns whether a locally saved draft exists to resume, without loading it.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return {@code true} if a resumable draft is stored on-device
     */
    public boolean hasResumableDraft() {
        return RecipeDraftStore.hasDraft();
    }

    /**
     * Loads the stored draft into this ViewModel if one exists, otherwise starts a fresh one.
     * Called once, when {@link AddRecipeWizardActivity} first launches.
     *
     * Complexity:
     * Time: O(n) where n is the serialized draft size
     * Space: O(n)
     */
    public void loadDraftIfPresent() {
        RecipeDraft stored = RecipeDraftStore.load();
        draft = stored != null ? stored : new RecipeDraft();
    }

    /** @return the draft currently being authored */
    public RecipeDraft getDraft() {
        return draft;
    }

    /**
     * Records which step the user last reached, so resuming a saved draft reopens there.
     *
     * @param step the current wizard step index (0-3)
     */
    public void setLastReachedStep(int step) {
        draft.lastReachedStep = step;
    }

    /** Persists the current draft locally. Purely local — no network call, instant. */
    public void saveDraftLocally() {
        draft.savedAtMillis = System.currentTimeMillis();
        RecipeDraftStore.save(draft);
    }

    /** Discards the current draft, both in-memory and on-device. */
    public void discardDraft() {
        RecipeDraftStore.clear();
        draft = new RecipeDraft();
    }

    // ── Basics (step 1) ──────────────────────────────────────────────

    public void setTitle(String title) {
        draft.title = title;
    }

    /**
     * @return the draft's description blocks (TEXT and IMAGE, in author-defined order) — the
     *         live list, so a {@link WizardDescriptionBlockAdapter} can reorder it directly
     */
    public List<DescriptionBlockDTO> getDescriptionBlocks() {
        return draft.descriptionBlocks;
    }

    /**
     * Updates a TEXT block's content. {@link DescriptionBlockDTO} is an immutable record, so
     * this replaces the block in place with a copy carrying the new text.
     */
    public void updateDescriptionBlockText(DescriptionBlockDTO block, String text) {
        replaceDescriptionBlock(block, new DescriptionBlockDTO(block.type(), text, block.imageUrl(), block.caption()));
    }

    /**
     * Updates an IMAGE block's caption. {@link DescriptionBlockDTO} is an immutable record, so
     * this replaces the block in place with a copy carrying the new caption.
     */
    public void setDescriptionImageCaption(DescriptionBlockDTO block, String caption) {
        replaceDescriptionBlock(block, new DescriptionBlockDTO(block.type(), block.text(), block.imageUrl(), caption));
    }

    /**
     * Replaces {@code oldBlock} with {@code newBlock} at the same position (matched by
     * {@code equals} — in practice unique per block since each TEXT/IMAGE block carries its own
     * content/URL).
     */
    private void replaceDescriptionBlock(DescriptionBlockDTO oldBlock, DescriptionBlockDTO newBlock) {
        int index = draft.descriptionBlocks.indexOf(oldBlock);
        if (index >= 0) {
            draft.descriptionBlocks.set(index, newBlock);
        }
    }

    /**
     * Appends an inline description photo, referenced for now by its local picked-file URI —
     * nothing is uploaded to Cloudinary until {@link #publish()}, matching the wizard's "nothing
     * leaves the device before Publish" rule. Matching the design's own copy ("Photos sit inline
     * where you place them. The first one becomes the cover if you don't set one."), this becomes
     * the cover photo too if none was explicitly chosen yet.
     *
     * @param localUri the picked image's local (file://) URI, as a string
     */
    public void addDescriptionImage(String localUri) {
        draft.descriptionBlocks.add(new DescriptionBlockDTO("IMAGE", null, localUri, null));
        if (draft.primaryImageUrl == null || draft.primaryImageUrl.isEmpty()) {
            draft.primaryImageUrl = localUri;
        }
    }

    public void removeDescriptionImage(DescriptionBlockDTO block) {
        draft.descriptionBlocks.remove(block);
    }

    /**
     * Removes an already-empty TEXT block the author backspaced out of — the inverse of
     * {@link #splitDescriptionTextBlock}. A single remaining block is never removed, so the
     * description always has somewhere to type.
     *
     * @return the index focus should move to (the previous block, so backspacing merges into it
     *         like a text editor), or -1 if {@code block} wasn't removed
     */
    public int removeDescriptionTextBlock(DescriptionBlockDTO block) {
        if (draft.descriptionBlocks.size() <= 1) return -1;
        int index = draft.descriptionBlocks.indexOf(block);
        if (index < 0) return -1;
        draft.descriptionBlocks.remove(index);
        return Math.max(0, index - 1);
    }

    /**
     * Splits a TEXT block into two at the point the author pressed Enter, so each paragraph is
     * its own block and inline photos can be dragged between them — the same effect as
     * {@link #addDescriptionTextBlock()}, but authored inline while typing rather than via a
     * separate button.
     *
     * @param block the TEXT block being edited
     * @param beforeText the text kept in {@code block}, before the cursor
     * @param afterText the text moved into the new block, after the cursor
     * @return the index the new block was inserted at, or -1 if {@code block} was no longer present
     */
    public int splitDescriptionTextBlock(DescriptionBlockDTO block, String beforeText, String afterText) {
        int index = draft.descriptionBlocks.indexOf(block);
        if (index < 0) return -1;
        draft.descriptionBlocks.set(index, new DescriptionBlockDTO(block.type(), beforeText, block.imageUrl(), block.caption()));
        draft.descriptionBlocks.add(index + 1, new DescriptionBlockDTO("TEXT", afterText, null, null));
        return index + 1;
    }

    /** @return the draft's plain-text description, i.e. every TEXT block joined together */
    public String getDescriptionSummary() {
        StringBuilder builder = new StringBuilder();
        for (DescriptionBlockDTO block : draft.descriptionBlocks) {
            if ("TEXT".equals(block.type()) && block.text() != null && !block.text().isBlank()) {
                if (builder.length() > 0) builder.append(' ');
                builder.append(block.text().trim());
            }
        }
        return builder.toString();
    }

    public void setPrepTimeMinutes(Integer minutes) {
        draft.prepTimeMinutes = minutes;
    }

    public void setCookTimeMinutes(Integer minutes) {
        draft.cookTimeMinutes = minutes;
    }

    public void setServings(Integer servings) {
        draft.servings = servings;
    }

    public void setDifficulty(String difficulty) {
        draft.difficulty = difficulty;
    }

    /**
     * @param localUri the picked cover photo's local (file://) URI, as a string — not
     *                 uploaded until {@link #publish()}
     */
    public void setCoverImageUrl(String localUri) {
        draft.primaryImageUrl = localUri;
    }

    public void addTag(TagResponse tag) {
        if (draft.tags.stream().noneMatch(t -> Objects.equals(t.id(), tag.id()))) {
            draft.tags.add(tag);
        }
    }

    public void removeTag(TagResponse tag) {
        draft.tags.removeIf(t -> Objects.equals(t.id(), tag.id()));
    }

    public void loadTags() {
        tagRepository.getAllTags(tagsResult);
    }

    /**
     * Marks a not-yet-existing tag name as selected, purely locally — matching the rest of the
     * wizard's "nothing hits the server before Publish" rule. The tag is only actually created
     * (via {@link TagRepository#createTag}) as part of {@link #publish()}.
     *
     * @param name the new tag's name
     */
    public void addPendingTag(String name) {
        if (!draft.pendingNewTagNames.contains(name)) {
            draft.pendingNewTagNames.add(name);
        }
    }

    public void removePendingTag(String name) {
        draft.pendingNewTagNames.remove(name);
    }

    /** @return names of tags the user selected that don't exist server-side yet */
    public List<String> getPendingTagNames() {
        return draft.pendingNewTagNames;
    }

    public LiveData<ApiResult<List<TagResponse>>> getTagsResult() { return tagsResult; }

    // ── Ingredients (step 2) ─────────────────────────────────────────

    public List<RecipeDraft.DraftIngredient> getIngredients() {
        return draft.ingredients;
    }

    public RecipeDraft.DraftIngredient addIngredient() {
        RecipeDraft.DraftIngredient ingredient = new RecipeDraft.DraftIngredient();
        draft.ingredients.add(ingredient);
        return ingredient;
    }

    /** Removes an ingredient and un-links it from every instruction step that referenced it. */
    public void removeIngredient(RecipeDraft.DraftIngredient ingredient) {
        draft.ingredients.remove(ingredient);
        for (RecipeDraft.DraftInstruction instruction : draft.instructions) {
            instruction.linkedIngredientTmpIds.remove(ingredient.tmpId);
        }
    }

    public void loadUnits() {
        unitRepository.getAllUnits(unitsResult);
    }

    public LiveData<ApiResult<List<UnitResponse>>> getUnitsResult() { return unitsResult; }

    // ── Instructions (step 3) ────────────────────────────────────────

    public List<RecipeDraft.DraftInstruction> getInstructions() {
        return draft.instructions;
    }

    public RecipeDraft.DraftInstruction addInstruction() {
        RecipeDraft.DraftInstruction instruction = new RecipeDraft.DraftInstruction();
        draft.instructions.add(instruction);
        return instruction;
    }

    public void removeInstruction(RecipeDraft.DraftInstruction instruction) {
        draft.instructions.remove(instruction);
    }

    // ── Cloudinary uploads (deferred to Publish time) ────────────────

    /**
     * One picked-but-not-yet-uploaded image, identifying exactly where its resulting Cloudinary
     * URL needs to be written back once uploaded. Actually performing the upload requires a
     * {@link android.content.Context} (the Cloudinary Android SDK is inherently
     * Context-dependent), so — matching this app's MVVM boundary — {@link AddRecipeWizardActivity}
     * owns the upload loop itself and calls {@link #resolvePendingImageUpload} as each finishes;
     * this ViewModel only tracks *where* each pending image lives in the draft.
     */
    public static final class PendingImageUpload {
        private enum Kind { COVER, DESCRIPTION_BLOCK, INSTRUCTION }

        private final Kind kind;
        private final String localUri;
        private final DescriptionBlockDTO descriptionBlock;
        private final RecipeDraft.DraftInstruction instruction;

        private PendingImageUpload(Kind kind, String localUri, DescriptionBlockDTO descriptionBlock,
                                    RecipeDraft.DraftInstruction instruction) {
            this.kind = kind;
            this.localUri = localUri;
            this.descriptionBlock = descriptionBlock;
            this.instruction = instruction;
        }

        /** @return the picked image's local (file://) URI, as a string */
        public String getLocalUri() {
            return localUri;
        }
    }

    /**
     * @return every description/cover/instruction image still referenced by a local
     *         local URI, i.e. not yet uploaded — empty once nothing is pending
     */
    public List<PendingImageUpload> collectPendingImageUploads() {
        List<PendingImageUpload> pending = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (DescriptionBlockDTO block : draft.descriptionBlocks) {
            if ("IMAGE".equals(block.type()) && isLocalUri(block.imageUrl())) {
                pending.add(new PendingImageUpload(PendingImageUpload.Kind.DESCRIPTION_BLOCK, block.imageUrl(), block, null));
                seen.add(block.imageUrl());
            }
        }
        if (isLocalUri(draft.primaryImageUrl) && !seen.contains(draft.primaryImageUrl)) {
            pending.add(new PendingImageUpload(PendingImageUpload.Kind.COVER, draft.primaryImageUrl, null, null));
        }
        for (RecipeDraft.DraftInstruction instruction : draft.instructions) {
            if (isLocalUri(instruction.imageUrl)) {
                pending.add(new PendingImageUpload(PendingImageUpload.Kind.INSTRUCTION, instruction.imageUrl, null, instruction));
            }
        }
        return pending;
    }

    private static boolean isLocalUri(String value) {
        return value != null && (value.startsWith("content://") || value.startsWith("file://"));
    }

    /**
     * Writes a just-uploaded image's secure URL back into the draft, replacing whichever local
     * URI {@code pending} referenced (including the cover photo, if it happened to be the same
     * local URI via the description-photo cover fallback).
     *
     * @param pending the resolved upload, as returned by {@link #collectPendingImageUploads()}
     * @param uploadedUrl the secure HTTPS URL Cloudinary returned
     */
    public void resolvePendingImageUpload(PendingImageUpload pending, String uploadedUrl) {
        if (Objects.equals(draft.primaryImageUrl, pending.localUri)) {
            draft.primaryImageUrl = uploadedUrl;
        }
        switch (pending.kind) {
            case DESCRIPTION_BLOCK -> replaceDescriptionBlock(pending.descriptionBlock, new DescriptionBlockDTO(
                    pending.descriptionBlock.type(), pending.descriptionBlock.text(), uploadedUrl, pending.descriptionBlock.caption()));
            case INSTRUCTION -> pending.instruction.imageUrl = uploadedUrl;
            case COVER -> { /* already handled by the primaryImageUrl check above */ }
        }
    }

    /**
     * Requests a fresh Cloudinary upload signature into a caller-owned {@code resultTarget},
     * exactly like every {@code *Repository} call in the app.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param resultTarget LiveData target to post the outcome
     */
    public void requestUploadSignature(MutableLiveData<ApiResult<CloudinarySignatureResponse>> resultTarget) {
        mediaRepository.getUploadSignature(resultTarget);
    }

    // ── Review & publish (step 4) ────────────────────────────────────

    /** @return the visibility currently selected ("PUBLIC" or "PRIVATE") */
    public String getVisibility() { return draft.visibility; }

    public void setVisibility(String visibility) {
        draft.visibility = visibility;
    }

    /**
     * Validates and publishes the draft. Assumes {@link AddRecipeWizardActivity} has already
     * resolved every {@link #collectPendingImageUploads()} entry (i.e. every image is a real
     * Cloudinary URL, not a local URI) before calling this. Any tag names picked via
     * "Create tag" are only created server-side now, at the start of publishing — not when the
     * user typed them — so an abandoned draft never leaves orphaned tags or uploaded images
     * behind. On success, the local draft is cleared since it now exists on the server.
     *
     * Complexity:
     * Time: O(t) network round-trips for t pending tags, plus one to publish
     * Space: O(1)
     */
    public void publish() {
        if (!RecipeDraftValidator.isStepValid(draft, RecipeDraftValidator.STEP_REVIEW)) {
            publishResult.setValue(new Event<>(new ApiResult.Error<>("Please complete every required field first.", null)));
            return;
        }
        resolveNextPendingTagThenPublish();
    }

    /** Resolves {@link RecipeDraft#pendingNewTagNames} one at a time, then submits the recipe. */
    private void resolveNextPendingTagThenPublish() {
        if (draft.pendingNewTagNames.isEmpty()) {
            submitRecipe();
            return;
        }
        String name = draft.pendingNewTagNames.get(0);
        MutableLiveData<ApiResult<TagResponse>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<TagResponse> success) {
                draft.tags.add(success.getData());
                draft.pendingNewTagNames.remove(0);
                resolveNextPendingTagThenPublish();
            } else if (apiResult instanceof ApiResult.Error<TagResponse> error) {
                publishResult.setValue(new Event<>(new ApiResult.Error<>(error.getMessage(), error.getCause())));
            }
        });
        tagRepository.createTag(name, result);
    }

    private void submitRecipe() {
        MutableLiveData<ApiResult<RecipeResponse>> result = new MutableLiveData<>();
        observeOnce(result, apiResult -> {
            if (apiResult instanceof ApiResult.Success<RecipeResponse>) {
                RecipeDraftStore.clear();
            }
            publishResult.setValue(new Event<>(apiResult));
        });
        recipeRepository.createRecipe(RecipeDraftMapper.toDto(draft), result);
    }

    public LiveData<Event<ApiResult<RecipeResponse>>> getPublishResult() { return publishResult; }
}
