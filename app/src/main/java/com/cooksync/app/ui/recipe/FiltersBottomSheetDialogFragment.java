package com.cooksync.app.ui.recipe;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cooksync.app.R;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A bottom sheet dialog for choosing sorting and filtering options. The dietary-preference
 * chips are populated dynamically from {@link #setAvailableTags(List)} rather than hardcoded,
 * since the actual tag catalog is server-defined and can't be guessed reliably client-side.
 * Tags support multi-selection (e.g. "Vegan" and "Dessert" active together), matching the
 * design's Tags filter row.
 */
public class FiltersBottomSheetDialogFragment extends BottomSheetDialogFragment {

    public interface OnFiltersAppliedListener {
        void onFiltersApplied(String sortBy, String difficulty, List<String> tags,
                               Double minRating, Integer maxTotalTimeMinutes);
    }

    private OnFiltersAppliedListener listener;
    private List<String> availableTags = Collections.emptyList();
    private String initialSort = "Newest";
    private String initialDifficulty = null;
    private Set<String> initialTags = Collections.emptySet();
    private Double initialMinRating = null;
    private Integer initialMaxTotalTimeMinutes = null;

    public void setOnFiltersAppliedListener(OnFiltersAppliedListener listener) {
        this.listener = listener;
    }

    /**
     * Supplies the real tag names to render as diet/dietary-preference chips, replacing the
     * layout's static placeholder chips. Must be called before the dialog is shown.
     *
     * @param tagNames tag display names currently known to the app (e.g. from {@code GET /api/tags})
     */
    public void setAvailableTags(List<String> tagNames) {
        this.availableTags = tagNames == null ? Collections.emptyList() : tagNames;
    }

    /**
     * Supplies the currently active sort/difficulty/tags so the sheet opens pre-checked to
     * match what's already applied, rather than always resetting to defaults. Must be called
     * before the dialog is shown.
     *
     * @param sortBy the active sort choice
     * @param difficulty the active difficulty filter, or {@code null}
     * @param tags the currently selected tag names
     * @param minRating the active minimum-rating filter, or {@code null}
     * @param maxTotalTimeMinutes the active total-time filter in minutes, or {@code null}
     */
    public void setInitialState(String sortBy, String difficulty, Set<String> tags,
                                 Double minRating, Integer maxTotalTimeMinutes) {
        this.initialSort = sortBy == null ? "Newest" : sortBy;
        this.initialDifficulty = difficulty;
        this.initialTags = tags == null ? Collections.emptySet() : tags;
        this.initialMinRating = minRating;
        this.initialMaxTotalTimeMinutes = maxTotalTimeMinutes;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new BottomSheetDialog(requireContext(), R.style.Theme_CookSync_BottomSheetDialog);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_filters_bottom_sheet, container, false);
    }

    /**
     * Matches the design's {@code .sheetbody { max-height:82% }}: opens the sheet already
     * expanded to (up to) 82% of the screen height, rather than Material's default collapsed
     * peek state, which used to hide the "Apply Filters" button and everything below the peek
     * line unless the user knew to drag the sheet up first.
     */
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (!(dialog instanceof BottomSheetDialog)) {
            return;
        }
        FrameLayout bottomSheet = ((BottomSheetDialog) dialog)
                .findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setMaxHeight((int) (getResources().getDisplayMetrics().heightPixels * 0.82f));
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ChipGroup cgSort = view.findViewById(R.id.cg_sort);
        ChipGroup cgDifficulty = view.findViewById(R.id.cg_difficulty);
        ChipGroup cgDiet = view.findViewById(R.id.cg_diet);
        ChipGroup cgTime = view.findViewById(R.id.cg_time);
        ChipGroup cgRating = view.findViewById(R.id.cg_rating);

        if (!availableTags.isEmpty()) {
            cgDiet.removeAllViews();
            for (String tagName : availableTags) {
                Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_filter_chip, cgDiet, false);
                // item_filter_chip.xml hardcodes a single android:id, so every inflated
                // instance would otherwise share that same id — breaking any id-based lookup
                // (ChipGroup#getCheckedChipIds() + findViewById always resolves to the first
                // chip in the tree). Generate a unique id per instance to keep each chip
                // independently addressable.
                chip.setId(View.generateViewId());
                chip.setText(capitalize(tagName));
                // Keep the raw server tag name as the chip's tag so filtering compares against
                // the exact value the API returns, independent of how it's capitalized for display.
                chip.setTag(tagName);
                chip.setChecked(initialTags.contains(tagName));
                cgDiet.addView(chip);
            }
        }

        for (int i = 0; i < cgSort.getChildCount(); i++) {
            Chip chip = (Chip) cgSort.getChildAt(i);
            if (chip.getText().toString().equals(initialSort)) {
                cgSort.check(chip.getId());
                break;
            }
        }
        for (int i = 0; i < cgDifficulty.getChildCount(); i++) {
            Chip chip = (Chip) cgDifficulty.getChildAt(i);
            chip.setChecked(chip.getText().toString().equals(initialDifficulty));
        }

        if (initialMaxTotalTimeMinutes != null) {
            if (initialMaxTotalTimeMinutes <= 30) {
                cgTime.check(R.id.chip_time_under_30);
            } else if (initialMaxTotalTimeMinutes <= 60) {
                cgTime.check(R.id.chip_time_under_60);
            }
        }

        if (initialMinRating != null) {
            if (initialMinRating >= 4.5) {
                cgRating.check(R.id.chip_rating_45);
            } else if (initialMinRating >= 4.0) {
                cgRating.check(R.id.chip_rating_40);
            } else if (initialMinRating >= 3.5) {
                cgRating.check(R.id.chip_rating_35);
            }
        }

        view.findViewById(R.id.btn_apply).setOnClickListener(v -> {
            if (listener != null) {
                String sortBy = null;
                int sortId = cgSort.getCheckedChipId();
                if (sortId != View.NO_ID) {
                    sortBy = ((Chip) cgSort.findViewById(sortId)).getText().toString();
                }

                String difficulty = null;
                int diffId = cgDifficulty.getCheckedChipId();
                if (diffId != View.NO_ID) {
                    difficulty = ((Chip) cgDifficulty.findViewById(diffId)).getText().toString();
                }

                // Iterate children directly rather than getCheckedChipIds() + findViewById:
                // findViewById only ever returns the *first* view matching a given id, so if
                // multiple chips shared an id (as they did before each got a unique generated
                // id above) this would silently resolve every checked chip to the same one.
                List<String> tags = new ArrayList<>();
                for (int i = 0; i < cgDiet.getChildCount(); i++) {
                    Chip dietChip = (Chip) cgDiet.getChildAt(i);
                    if (dietChip.isChecked()) {
                        Object rawTag = dietChip.getTag();
                        tags.add(rawTag instanceof String ? (String) rawTag : dietChip.getText().toString());
                    }
                }

                Integer maxTotalTimeMinutes = null;
                int timeId = cgTime.getCheckedChipId();
                if (timeId == R.id.chip_time_under_30) {
                    maxTotalTimeMinutes = 30;
                } else if (timeId == R.id.chip_time_under_60) {
                    maxTotalTimeMinutes = 60;
                }

                Double minRating = null;
                int ratingId = cgRating.getCheckedChipId();
                if (ratingId == R.id.chip_rating_35) {
                    minRating = 3.5;
                } else if (ratingId == R.id.chip_rating_40) {
                    minRating = 4.0;
                } else if (ratingId == R.id.chip_rating_45) {
                    minRating = 4.5;
                }

                listener.onFiltersApplied(sortBy, difficulty, tags, minRating, maxTotalTimeMinutes);
            }
            dismiss();
        });

        view.findViewById(R.id.btn_reset).setOnClickListener(v -> {
            cgSort.check(R.id.chip_sort_newest);
            cgDifficulty.clearCheck();
            cgDiet.clearCheck();
            cgTime.check(R.id.chip_time_any);
            cgRating.check(R.id.chip_rating_any);

            if (listener != null) {
                listener.onFiltersApplied("Newest", null, Collections.emptyList(), null, null);
            }
            dismiss();
        });
    }

    /**
     * Title-cases a raw tag name for chip display (e.g. "gluten-free" → "Gluten-free").
     *
     * @param raw the server tag name
     * @return a display-friendly capitalized version
     */
    private String capitalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
