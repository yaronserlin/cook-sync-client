package com.cooksync.app.ui.recipe.wizard;

import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;

import com.cooksync.app.R;
import com.dtos.response.tags.TagResponse;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Drives the recipe wizard's tag picker: an {@link AutoCompleteTextView} that suggests from the
 * already-loaded tag catalog as the user types, and a {@link ChipGroup} of the currently
 * selected tags (reusing the app's existing {@code item_removable_chip} layout, the same one
 * {@link com.cooksync.app.ui.common.NoResultsStateHelper} uses for removable filter chips).
 * Submitting text that doesn't match an existing tag name is treated as a request to create a
 * new one.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 08/08/2026
 */
public final class TagAutocompleteController {

    /** Notified when the user picks or requests a tag, or removes one already selected. */
    public interface Listener {
        void onTagSelected(TagResponse tag);
        void onCreateTagRequested(String name);
        void onTagRemoved(TagResponse tag);
        void onPendingTagRemoved(String name);
    }

    private final AutoCompleteTextView input;
    private final ChipGroup chipGroup;
    private final Listener listener;
    private List<TagResponse> availableTags = new ArrayList<>();

    /**
     * Wires the controller to its input field and chip group.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param input the tag-name text field
     * @param chipGroup the group selected-tag chips are rendered into
     * @param listener notified of selection/creation/removal
     */
    public TagAutocompleteController(@NonNull AutoCompleteTextView input, @NonNull ChipGroup chipGroup,
                                      @NonNull Listener listener) {
        this.input = input;
        this.chipGroup = chipGroup;
        this.listener = listener;
        // Matches the design's card-style suggestion popup instead of the system default plain
        // white dropdown.
        input.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
        input.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            handleSubmittedName(selectedName);
        });
    }

    /**
     * Refreshes the autocomplete suggestions to match the full tag catalog.
     *
     * Complexity:
     * Time: O(n) where n is the number of tags
     * Space: O(n)
     *
     * @param tags every tag currently known to the system
     */
    public void setAvailableTags(@NonNull List<TagResponse> tags) {
        this.availableTags = tags;
        List<String> names = new ArrayList<>();
        for (TagResponse tag : tags) {
            names.add(tag.name());
        }
        input.setAdapter(new ArrayAdapter<>(input.getContext(), R.layout.item_tag_suggestion, R.id.tv_tag_suggestion_name, names));
    }

    /**
     * Submits whatever text is currently typed: selects the matching tag if one exists
     * (case-insensitive), otherwise requests creation of a new tag with that name. No-op if the
     * field is blank. Clears the field either way.
     *
     * Complexity:
     * Time: O(n) where n is the number of available tags
     * Space: O(1)
     */
    public void submitCurrentText() {
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        input.setText("");
        if (!text.isEmpty()) {
            handleSubmittedName(text);
        }
    }

    /**
     * Selects or creates a tag by name directly, without involving the input field — used by
     * the "Popular tags" suggestion pills.
     *
     * @param name the tag name to select or create
     */
    public void selectPopularTag(@NonNull String name) {
        handleSubmittedName(name);
    }

    private void handleSubmittedName(String name) {
        Optional<TagResponse> match = availableTags.stream()
                .filter(t -> t.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
                .findFirst();
        input.setText("");
        if (match.isPresent()) {
            listener.onTagSelected(match.get());
        } else {
            listener.onCreateTagRequested(name);
        }
    }

    /**
     * Rebuilds the selected-tags chip row from scratch — both already-resolved tags and
     * pending ones (typed but not yet created server-side; see
     * {@link AddRecipeViewModel#addPendingTag}) render identically as removable chips, since
     * the pending/resolved distinction is purely an internal publish-time concern.
     *
     * Complexity:
     * Time: O(n + p) where n is the number of selected tags and p the number of pending ones
     * Space: O(n + p)
     *
     * @param selectedTags the already-resolved tags currently attached to the draft
     * @param pendingTagNames names of tags selected but not yet created server-side
     */
    public void renderSelectedTags(@NonNull List<TagResponse> selectedTags, @NonNull List<String> pendingTagNames) {
        chipGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(chipGroup.getContext());
        for (TagResponse tag : selectedTags) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_removable_chip, chipGroup, false);
            chip.setText(tag.name());
            chip.setOnCloseIconClickListener(v -> listener.onTagRemoved(tag));
            chipGroup.addView(chip);
        }
        for (String name : pendingTagNames) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_removable_chip, chipGroup, false);
            chip.setText(name);
            chip.setOnCloseIconClickListener(v -> listener.onPendingTagRemoved(name));
            chipGroup.addView(chip);
        }
    }
}
