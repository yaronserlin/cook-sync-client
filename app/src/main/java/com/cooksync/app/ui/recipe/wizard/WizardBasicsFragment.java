package com.cooksync.app.ui.recipe.wizard;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.DurationPickerDialog;
import com.cooksync.app.ui.common.ViewModelFactory;
import com.dtos.response.recipe.DescriptionBlockDTO;
import com.dtos.response.tags.TagResponse;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Wizard step 1: cover photo, title, an ordered/reorderable description (text and inline
 * photos), prep/cook time, servings, difficulty, and tags. Shares {@link AddRecipeViewModel}
 * with the other three steps via an activity-scoped {@link ViewModelProvider}. None of this
 * step's fields are validated here — the wizard lets the user move freely between steps, and
 * every required-field check happens once, on the Review step.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 08/08/2026
 */
public class WizardBasicsFragment extends Fragment {

    private AddRecipeViewModel viewModel;
    private RecipeImagePicker imagePicker;
    private TagAutocompleteController tagController;
    private WizardDescriptionBlockAdapter descriptionAdapter;

    private EditText etTitle;
    private EditText etPrepTime;
    private EditText etCookTime;
    private EditText etServings;
    private MaterialCardView cardCoverPreview;
    private ImageView ivCoverPreview;
    private View btnPickCover;
    private TextView chipEasy;
    private TextView chipMedium;
    private TextView chipHard;
    private ChipGroup cgSelectedTags;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        imagePicker = new RecipeImagePicker(this);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wizard_basics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity(), new ViewModelFactory()).get(AddRecipeViewModel.class);

        if (viewModel.getDescriptionBlocks().isEmpty()) {
            viewModel.getDescriptionBlocks().add(new DescriptionBlockDTO("TEXT", "", null, null));
        }

        bindViews(view);
        renderFromDraft();
        setupListeners(view);
        observeViewModel();
        renderPopularTags(view);

        viewModel.loadTags();
    }

    /** Populates the static "Popular tags" suggestion row (design-provided examples). */
    private void renderPopularTags(View view) {
        ChipGroup cgPopularTags = view.findViewById(R.id.cg_popular_tags);
        for (String name : getResources().getStringArray(R.array.wizard_popular_tag_names)) {
            TextView pill = new TextView(requireContext());
            pill.setText(name);
            pill.setTextSize(12.5f);
            pill.setTextColor(getResources().getColor(R.color.color_accent, null));
            pill.setBackgroundResource(R.drawable.bg_tag_outline);
            int paddingH = Math.round(13 * getResources().getDisplayMetrics().density);
            int paddingV = Math.round(7 * getResources().getDisplayMetrics().density);
            pill.setPadding(paddingH, paddingV, paddingH, paddingV);
            pill.setOnClickListener(v -> tagController.selectPopularTag(name));
            cgPopularTags.addView(pill);
        }
    }

    private void bindViews(View view) {
        etTitle = view.findViewById(R.id.et_title);
        etPrepTime = view.findViewById(R.id.et_prep_time);
        etCookTime = view.findViewById(R.id.et_cook_time);
        etServings = view.findViewById(R.id.et_servings);
        cardCoverPreview = view.findViewById(R.id.card_cover_preview);
        ivCoverPreview = view.findViewById(R.id.iv_cover_preview);
        btnPickCover = view.findViewById(R.id.btn_pick_cover);
        // Dashed shape-drawable strokes don't reliably render under hardware acceleration.
        btnPickCover.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        chipEasy = view.findViewById(R.id.chip_difficulty_easy);
        chipMedium = view.findViewById(R.id.chip_difficulty_medium);
        chipHard = view.findViewById(R.id.chip_difficulty_hard);
        cgSelectedTags = view.findViewById(R.id.cg_selected_tags);

        RecyclerView rvDescriptionBlocks = view.findViewById(R.id.rv_description_blocks);
        descriptionAdapter = new WizardDescriptionBlockAdapter(viewModel.getDescriptionBlocks());
        descriptionAdapter.setListener(new WizardDescriptionBlockAdapter.Listener() {
            @Override
            public void onTextChanged(DescriptionBlockDTO block, String text) {
                viewModel.updateDescriptionBlockText(block, text);
            }

            @Override
            public void onCaptionChanged(DescriptionBlockDTO block, String caption) {
                viewModel.setDescriptionImageCaption(block, caption);
            }

            @Override
            public void onRemoveImage(DescriptionBlockDTO block) {
                viewModel.removeDescriptionImage(block);
                descriptionAdapter.notifyDataSetChanged();
            }

            @Override
            public void onSplitTextBlock(DescriptionBlockDTO block, String beforeText, String afterText) {
                rvDescriptionBlocks.post(() -> {
                    int newIndex = viewModel.splitDescriptionTextBlock(block, beforeText, afterText);
                    if (newIndex < 0) return;
                    descriptionAdapter.notifyItemChanged(newIndex - 1);
                    descriptionAdapter.notifyItemInserted(newIndex);
                    descriptionAdapter.focusTextBlockAt(newIndex);
                });
            }

            @Override
            public void onRemoveEmptyTextBlock(DescriptionBlockDTO block) {
                rvDescriptionBlocks.post(() -> {
                    int removedAt = viewModel.getDescriptionBlocks().indexOf(block);
                    int focusIndex = viewModel.removeDescriptionTextBlock(block);
                    if (focusIndex < 0 || removedAt < 0) return;
                    descriptionAdapter.notifyItemRemoved(removedAt);
                    descriptionAdapter.focusTextBlockAtEnd(focusIndex);
                });
            }
        });
        rvDescriptionBlocks.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvDescriptionBlocks.setAdapter(descriptionAdapter);
        ItemTouchHelper descriptionTouchHelper = new ItemTouchHelper(new DragReorderTouchHelperCallback((from, to) -> {
            Collections.swap(viewModel.getDescriptionBlocks(), from, to);
            descriptionAdapter.notifyItemMoved(from, to);
        }));
        descriptionTouchHelper.attachToRecyclerView(rvDescriptionBlocks);
        descriptionAdapter.setItemTouchHelper(descriptionTouchHelper);

        AutoCompleteTextView actvTagInput = view.findViewById(R.id.actv_tag_input);
        tagController = new TagAutocompleteController(actvTagInput, cgSelectedTags, new TagAutocompleteController.Listener() {
            @Override
            public void onTagSelected(TagResponse tag) {
                viewModel.addTag(tag);
                renderSelectedTags();
            }

            @Override
            public void onCreateTagRequested(String name) {
                // Not created server-side yet — only resolved into a real tag at Publish time.
                viewModel.addPendingTag(name);
                renderSelectedTags();
            }

            @Override
            public void onTagRemoved(TagResponse tag) {
                viewModel.removeTag(tag);
                renderSelectedTags();
            }

            @Override
            public void onPendingTagRemoved(String name) {
                viewModel.removePendingTag(name);
                renderSelectedTags();
            }
        });
    }

    private void renderFromDraft() {
        RecipeDraft draft = viewModel.getDraft();
        etTitle.setText(draft.title);
        renderDuration(etPrepTime, draft.prepTimeMinutes);
        renderDuration(etCookTime, draft.cookTimeMinutes);
        etServings.setText(draft.servings == null ? "" : String.valueOf(draft.servings));
        renderCoverPreview();
        styleDifficultyChips();
        renderSelectedTags();
    }

    private void renderDuration(EditText field, Integer totalMinutes) {
        if (totalMinutes == null) {
            field.setText("");
        } else if (totalMinutes >= 60) {
            field.setText(getString(R.string.wizard_duration_format, totalMinutes / 60, totalMinutes % 60));
        } else {
            field.setText(getString(R.string.wizard_duration_minutes_only_format, totalMinutes));
        }
    }

    private void setupListeners(View view) {
        etTitle.addTextChangedListener(onChanged(viewModel::setTitle));
        etServings.addTextChangedListener(onChanged(text -> viewModel.setServings(parseIntOrNull(text))));

        etPrepTime.setOnClickListener(v -> DurationPickerDialog.show(requireContext(), R.string.wizard_prep_time_dialog_title,
                viewModel.getDraft().prepTimeMinutes == null ? 0 : viewModel.getDraft().prepTimeMinutes, minutes -> {
                    viewModel.setPrepTimeMinutes(minutes);
                    renderDuration(etPrepTime, minutes);
                }));
        etCookTime.setOnClickListener(v -> DurationPickerDialog.show(requireContext(), R.string.wizard_cook_time_dialog_title,
                viewModel.getDraft().cookTimeMinutes == null ? 0 : viewModel.getDraft().cookTimeMinutes, minutes -> {
                    viewModel.setCookTimeMinutes(minutes);
                    renderDuration(etCookTime, minutes);
                }));

        chipEasy.setOnClickListener(v -> selectDifficulty("EASY"));
        chipMedium.setOnClickListener(v -> selectDifficulty("MEDIUM"));
        chipHard.setOnClickListener(v -> selectDifficulty("HARD"));

        View.OnClickListener pickCoverListener = v -> imagePicker.pick(uri -> {
            // Not uploaded yet — only the local URI is stored until Publish, matching the
            // wizard's "nothing leaves the device before Publish" rule. Glide can preview a
            // local file:// URI exactly like a remote one.
            viewModel.setCoverImageUrl(uri.toString());
            renderCoverPreview();
        });
        btnPickCover.setOnClickListener(pickCoverListener);
        // Tapping the preview itself re-picks, so an already-set cover can be replaced.
        cardCoverPreview.setOnClickListener(pickCoverListener);

        view.findViewById(R.id.btn_add_description_photo).setOnClickListener(v -> imagePicker.pick(uri -> {
            viewModel.addDescriptionImage(uri.toString());
            descriptionAdapter.notifyDataSetChanged();
            renderCoverPreview();
        }));

        view.findViewById(R.id.btn_add_tag).setOnClickListener(v -> tagController.submitCurrentText());
    }

    private void observeViewModel() {
        viewModel.getTagsResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof ApiResult.Success<List<TagResponse>> success) {
                tagController.setAvailableTags(success.getData());
            }
        });
    }

    private void renderCoverPreview() {
        String url = viewModel.getDraft().primaryImageUrl;
        boolean hasCover = url != null && !url.isEmpty();
        // Both views fill the same FrameLayout cell; the dashed placeholder is declared after
        // the preview card so it draws on top and must be hidden once there's a photo to show,
        // otherwise the preview renders underneath it and never becomes visible.
        cardCoverPreview.setVisibility(hasCover ? View.VISIBLE : View.GONE);
        btnPickCover.setVisibility(hasCover ? View.GONE : View.VISIBLE);
        if (hasCover) {
            Glide.with(this).load(url).into(ivCoverPreview);
        }
    }

    private void selectDifficulty(String difficulty) {
        viewModel.setDifficulty(difficulty);
        styleDifficultyChips();
    }

    private void styleDifficultyChips() {
        String selected = viewModel.getDraft().difficulty;
        styleChip(chipEasy, "EASY".equals(selected));
        styleChip(chipMedium, "MEDIUM".equals(selected));
        styleChip(chipHard, "HARD".equals(selected));
    }

    private void styleChip(TextView chip, boolean active) {
        chip.setBackgroundColor(active
                ? getResources().getColor(R.color.color_accent, null)
                : android.graphics.Color.TRANSPARENT);
        chip.setTextColor(active
                ? getResources().getColor(R.color.color_bg, null)
                : getResources().getColor(R.color.color_text, null));
    }

    private void renderSelectedTags() {
        tagController.renderSelectedTags(viewModel.getDraft().tags, viewModel.getPendingTagNames());
    }

    private static Integer parseIntOrNull(String text) {
        try {
            return text.isEmpty() ? null : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static TextWatcher onChanged(Consumer<String> onChanged) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onChanged.accept(s.toString());
            }
        };
    }
}
