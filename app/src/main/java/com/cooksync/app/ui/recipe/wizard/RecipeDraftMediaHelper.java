package com.cooksync.app.ui.recipe.wizard;

import com.dtos.response.recipe.DescriptionBlockDTO;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Helper class for collecting and resolving pending Cloudinary image uploads within a {@link RecipeDraft}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 09/08/2026
 */
public final class RecipeDraftMediaHelper {

    /** One picked-but-not-yet-uploaded image item in a draft. */
    public static final class PendingImageUpload {
        public enum Kind { COVER, DESCRIPTION_BLOCK, INSTRUCTION }

        private final Kind kind;
        private final String localUri;
        private final DescriptionBlockDTO descriptionBlock;
        private final RecipeDraft.DraftInstruction instruction;

        public PendingImageUpload(Kind kind, String localUri, DescriptionBlockDTO descriptionBlock,
                                  RecipeDraft.DraftInstruction instruction) {
            this.kind = kind;
            this.localUri = localUri;
            this.descriptionBlock = descriptionBlock;
            this.instruction = instruction;
        }

        public String getLocalUri() {
            return localUri;
        }

        public Kind getKind() {
            return kind;
        }
    }

    private RecipeDraftMediaHelper() {
        // Utility
    }

    /**
     * Collects every local (file:// or content://) image reference in the draft that needs upload.
     *
     * @param draft target recipe draft
     * @return list of pending image upload descriptors
     */
    public static List<PendingImageUpload> collectPendingImageUploads(RecipeDraft draft) {
        List<PendingImageUpload> pending = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (draft.descriptionBlocks != null) {
            for (DescriptionBlockDTO block : draft.descriptionBlocks) {
                if ("IMAGE".equals(block.type()) && isLocalUri(block.imageUrl())) {
                    pending.add(new PendingImageUpload(PendingImageUpload.Kind.DESCRIPTION_BLOCK, block.imageUrl(), block, null));
                    seen.add(block.imageUrl());
                }
            }
        }
        if (isLocalUri(draft.primaryImageUrl) && !seen.contains(draft.primaryImageUrl)) {
            pending.add(new PendingImageUpload(PendingImageUpload.Kind.COVER, draft.primaryImageUrl, null, null));
        }
        if (draft.instructions != null) {
            for (RecipeDraft.DraftInstruction instruction : draft.instructions) {
                if (isLocalUri(instruction.imageUrl)) {
                    pending.add(new PendingImageUpload(PendingImageUpload.Kind.INSTRUCTION, instruction.imageUrl, null, instruction));
                }
            }
        }
        return pending;
    }

    /**
     * Replaces a local image URI in the draft with its resulting secure HTTPS Cloudinary URL.
     *
     * @param draft target recipe draft
     * @param pending pending image descriptor
     * @param uploadedUrl secure Cloudinary HTTPS URL
     */
    public static void resolvePendingImageUpload(RecipeDraft draft, PendingImageUpload pending, String uploadedUrl) {
        if (Objects.equals(draft.primaryImageUrl, pending.localUri)) {
            draft.primaryImageUrl = uploadedUrl;
        }
        switch (pending.kind) {
            case DESCRIPTION_BLOCK -> {
                if (draft.descriptionBlocks != null) {
                    int index = draft.descriptionBlocks.indexOf(pending.descriptionBlock);
                    if (index >= 0) {
                        draft.descriptionBlocks.set(index, new DescriptionBlockDTO(
                                pending.descriptionBlock.type(), pending.descriptionBlock.text(), uploadedUrl, pending.descriptionBlock.caption()));
                    }
                }
            }
            case INSTRUCTION -> {
                if (pending.instruction != null) {
                    pending.instruction.imageUrl = uploadedUrl;
                }
            }
            case COVER -> { /* already handled above */ }
        }
    }

    private static boolean isLocalUri(String value) {
        return value != null && (value.startsWith("content://") || value.startsWith("file://"));
    }
}
