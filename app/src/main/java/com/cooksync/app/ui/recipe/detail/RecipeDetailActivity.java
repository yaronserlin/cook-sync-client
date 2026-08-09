package com.cooksync.app.ui.recipe.detail;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.BaseActivity;
import com.cooksync.app.ui.common.FullscreenImageActivity;
import com.cooksync.app.ui.common.Navigator;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.common.ReportReviewDialog;
import com.cooksync.app.ui.common.ViewModelFactory;
import com.cooksync.app.ui.home.TagChipAdapter;
import com.cooksync.app.ui.recipe.cooking.CookingModeActivity;
import com.cooksync.app.ui.recipe.review.ReviewActivity;
import com.cooksync.app.ui.recipe.wizard.AddRecipeWizardActivity;
import com.cooksync.app.util.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.dtos.response.instruction.InstructionResponse;
import com.dtos.response.note.NoteResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.review.ReviewResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Detailed view of a single recipe, showing ingredients, instructions, and reviews.
 * Follows the Organic design system's Recipe Detail screen: kicker (author + publish date),
 * title, description (with inline supplementary photos), tappable rating row, prep/cook/serves
 * cards, tags, ingredients, instructions, and a reviews section with a rating-breakdown summary
 * card, star-tap filtering, and a sort toggle.
 *
 * @author Yaron Serlin
 * @version 1.3
 * @since 04/08/2026
 */
public class RecipeDetailActivity extends BaseActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    /** Cyclic order the "Sort" button walks through on each tap. */
    private static final String[] SORT_OPTIONS = {"Newest", "Highest rated", "Lowest rated"};

    /** Star values the review chip row offers, in the design's display order (5 down to 1). */
    private static final int[] STAR_VALUES = {5, 4, 3, 2, 1};

    private RecipeDetailViewModel viewModel;
    private IngredientAdapter ingredientAdapter;
    private InstructionAdapter instructionAdapter;
    private ReviewAdapter reviewAdapter;
    private DescriptionBlockAdapter descriptionBlockAdapter;
    private TagChipAdapter tagAdapter;

    private NestedScrollView scrollView;
    private View contentGroup;
    private View bottomBar;
    private ImageView heroImage;
    private TextView title;
    private TextView kicker;
    private TextView rating;
    private TextView reviewCount;
    private TextView prepTime;
    private TextView cookTime;
    private TextView servings;
    private TextView ingredientsHeader;
    private TextView summaryRating;
    private TextView summaryStars;
    private TextView reviewsSummaryLabel;
    private View reviewsEmptyState;
    private com.google.android.material.button.MaterialButton btnSortReviews;
    private View reviewsHeader;
    private View ratingRow;
    private ImageButton btnFavorite;
    private View groupNoteView;
    private TextView tvNote;
    private View groupNoteEdit;
    private EditText etNote;
    private ImageButton btnNoteDelete;

    /** Guards against a duplicate commit when both the save/delete icon tap and the resulting
     *  focus-loss on {@link #etNote} fire for the same user gesture. Reset each time the note
     *  editor opens. */
    private boolean noteEditCommitted = false;

    private MaterialButton btnEditRecipe;
    private boolean isFavorite = false;
    private final List<ReviewResponse> allReviews = new ArrayList<>();
    /** The review last optimistically removed by {@link #confirmDeleteReview}, restored if the
     *  deferred delete fails server-side; {@code null} whenever no delete is in flight. */
    private ReviewResponse pendingDeletedReview;
    private final List<NoteResponse> currentNotes = new ArrayList<>();
    private final Map<Integer, com.google.android.material.card.MaterialCardView> starChips = new HashMap<>();
    private Integer activeStarFilter = null;
    private int sortIndex = 0;
    private boolean isInitialLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
        if (recipeId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(RecipeDetailViewModel.class);

        initViews();
        setupAdapters();
        setupObservers();

        showSkeleton(true, null);
        viewModel.loadRecipe(recipeId);
        viewModel.loadFavorites();
        viewModel.loadNotes(recipeId);
    }

    /**
     * Reloads the recipe on every return to this screen after the first creation, so a
     * review just submitted from {@link ReviewActivity} (reached either from here or from the
     * end of {@link CookingModeActivity}) shows up immediately without a manual refresh.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (isInitialLoad) {
            isInitialLoad = false;
            return;
        }
        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
        if (recipeId != null) {
            viewModel.loadRecipe(recipeId);
        }
    }

    private void initViews() {
        scrollView = findViewById(R.id.detail_scroll);
        contentGroup = scrollView;
        bottomBar = findViewById(R.id.detail_bottom_bar);
        setupSkeleton(R.id.detail_skeleton);

        heroImage = findViewById(R.id.detail_image);
        title = findViewById(R.id.detail_title);
        kicker = findViewById(R.id.detail_kicker);
        rating = findViewById(R.id.detail_rating);
        reviewCount = findViewById(R.id.detail_review_count);
        prepTime = findViewById(R.id.detail_prep_time);
        cookTime = findViewById(R.id.detail_cook_time);
        servings = findViewById(R.id.detail_servings);
        ingredientsHeader = findViewById(R.id.detail_ingredients_header);
        summaryRating = findViewById(R.id.detail_summary_rating);
        summaryStars = findViewById(R.id.detail_summary_stars);
        reviewsSummaryLabel = findViewById(R.id.detail_reviews_summary_label);
        reviewsEmptyState = findViewById(R.id.reviews_empty_state);
        btnSortReviews = findViewById(R.id.btn_sort_reviews);
        reviewsHeader = findViewById(R.id.detail_reviews_header);
        ratingRow = findViewById(R.id.detail_rating_row);
        btnFavorite = findViewById(R.id.btn_favorite);
        btnEditRecipe = findViewById(R.id.btn_edit_recipe);
        groupNoteView = findViewById(R.id.group_detail_note_view);
        tvNote = findViewById(R.id.tv_detail_note);
        groupNoteEdit = findViewById(R.id.group_detail_note_edit);
        etNote = findViewById(R.id.et_detail_note);
        ImageButton btnNoteSave = findViewById(R.id.btn_detail_note_save);
        btnNoteDelete = findViewById(R.id.btn_detail_note_delete);
        groupNoteView.setOnClickListener(v -> openRecipeNoteEditor());
        btnNoteSave.setOnClickListener(v -> commitRecipeNoteInline());
        btnNoteDelete.setOnClickListener(v -> deleteRecipeNoteInline());
        etNote.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitRecipeNoteInline();
        });

        starChips.put(5, findViewById(R.id.chip_star_5));
        starChips.put(4, findViewById(R.id.chip_star_4));
        starChips.put(3, findViewById(R.id.chip_star_3));
        starChips.put(2, findViewById(R.id.chip_star_2));
        starChips.put(1, findViewById(R.id.chip_star_1));

        findViewById(R.id.btn_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        btnFavorite.setOnClickListener(v -> {
            String recipeId1 = getIntent().getStringExtra(EXTRA_RECIPE_ID);
            boolean wasFavorite = isFavorite;
            viewModel.toggleFavorite(recipeId1, wasFavorite);
            isFavorite = !isFavorite;
            updateFavoriteIcon();
            if (!wasFavorite) {
                showSuccess(getString(R.string.favorites_added), null);
            } else {
                OrganicToast.showWithAction(this, null, R.drawable.ic_heart_outline,
                        getString(R.string.favorites_removed), getString(R.string.action_undo), () -> {
                    if (viewModel.undoRemoveFavorite(recipeId1)) {
                        isFavorite = true;
                        updateFavoriteIcon();
                    }
                });
            }
        });

        ratingRow.setOnClickListener(v -> scrollView.smoothScrollTo(0, reviewsHeader.getTop() + ((View) reviewsHeader.getParent()).getTop()));

        findViewById(R.id.btn_start_cooking).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_RECIPE_ID, getIntent().getStringExtra(EXTRA_RECIPE_ID));
            Navigator.start(this, CookingModeActivity.class, intent);
        });

        findViewById(R.id.btn_review).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putExtra(ReviewActivity.EXTRA_RECIPE_ID, getIntent().getStringExtra(EXTRA_RECIPE_ID));
            Navigator.start(this, ReviewActivity.class, intent);
        });

        btnSortReviews.setOnClickListener(v -> {
            sortIndex = (sortIndex + 1) % SORT_OPTIONS.length;
            btnSortReviews.setText(SORT_OPTIONS[sortIndex]);
            refreshReviewsDisplay();
        });

        findViewById(R.id.btn_clear_review_filter).setOnClickListener(v -> {
            activeStarFilter = null;
            refreshReviewsDisplay();
        });
    }

    private void setupAdapters() {
        RecyclerView rvIngredients = findViewById(R.id.rv_ingredients);
        ingredientAdapter = new IngredientAdapter();
        rvIngredients.setAdapter(ingredientAdapter);

        RecyclerView rvInstructions = findViewById(R.id.rv_instructions);
        instructionAdapter = new InstructionAdapter();
        instructionAdapter.setOnNoteChangeListener(new InstructionAdapter.OnNoteChangeListener() {
            @Override
            public void onSaveNote(InstructionResponse step, String noteText) {
                viewModel.saveNote(getIntent().getStringExtra(EXTRA_RECIPE_ID), step.id(), noteText);
            }

            @Override
            public void onDeleteNote(InstructionResponse step) {
                NoteResponse existing = findStepNote(step.id());
                if (existing != null) viewModel.deleteNote(existing.id());
            }
        });
        instructionAdapter.setOnImageClickListener(this::openFullscreenImage);
        rvInstructions.setAdapter(instructionAdapter);

        RecyclerView rvReviews = findViewById(R.id.rv_reviews);
        reviewAdapter = new ReviewAdapter();
        reviewAdapter.setCurrentUserId(SessionManager.getInstance().getUserId());
        reviewAdapter.setOnReviewActionListener(new ReviewAdapter.OnReviewActionListener() {
            @Override
            public void onDeleteReview(ReviewResponse review) {
                confirmDeleteReview(review);
            }

            @Override
            public void onReportReview(ReviewResponse review) {
                ReportReviewDialog.show(RecipeDetailActivity.this, (reason, comment) -> {
                    viewModel.reportReview(review.id(), reason, comment);
                    OrganicToast.showWithAction(RecipeDetailActivity.this, null, 0,
                            getString(R.string.review_reported), getString(R.string.action_undo),
                            () -> viewModel.undoReportReview(review.id()));
                });
            }
        });
        reviewAdapter.setOnAvatarClickListener(this::openFullscreenImage);
        rvReviews.setAdapter(reviewAdapter);

        RecyclerView rvDescriptionBlocks = findViewById(R.id.rv_description_blocks);
        descriptionBlockAdapter = new DescriptionBlockAdapter();
        descriptionBlockAdapter.setOnImageClickListener(this::openFullscreenImage);
        rvDescriptionBlocks.setLayoutManager(new LinearLayoutManager(this));
        rvDescriptionBlocks.setAdapter(descriptionBlockAdapter);

        RecyclerView rvTags = findViewById(R.id.rv_tags);
        // No "All" option here — these chips are a read-only display of the recipe's own
        // tags, not a filter control like the equivalent row on the Home screen.
        tagAdapter = new TagChipAdapter(false);
        rvTags.setAdapter(tagAdapter);
    }

    private void setupObservers() {
        viewModel.getRecipeResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<RecipeResponse> success) {
                bindRecipe(success.getData());
                showSkeleton(false, null);
            } else if (result instanceof ApiResult.Error<RecipeResponse> error) {
                showSkeleton(false, null);
                showError(error.getMessage(), null);
            }
        });

        viewModel.getFavoritesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
                isFavorite = false;
                for (RecipePreviewResponse fav : success.getData()) {
                    if (Objects.equals(fav.id(), recipeId)) {
                        isFavorite = true;
                        break;
                    }
                }
                updateFavoriteIcon();
            }
        });

        viewModel.getNotesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<NoteResponse>> success) {
                currentNotes.clear();
                currentNotes.addAll(success.getData());
                renderRecipeNote();
                renderStepNotes();
            }
        });

        viewModel.getNoteSaveResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<Void>) {
                viewModel.loadNotes(getIntent().getStringExtra(EXTRA_RECIPE_ID));
            } else if (result instanceof ApiResult.Error<Void> error) {
                showError(error.getMessage(), null);
            }
        });

        // Only a deferred delete/report that reached the server and failed shows up here — a
        // success needs no signal since the review list already reflects it optimistically.
        viewModel.getReviewActionResult().observe(this, result -> {
            if (result instanceof ApiResult.Error<Void> error) {
                if (pendingDeletedReview != null) {
                    allReviews.add(pendingDeletedReview);
                    pendingDeletedReview = null;
                    refreshReviewsDisplay();
                }
                showError(error.getMessage(), null);
            }
        });
    }

    /**
     * Opens {@link FullscreenImageActivity} for a tapped photo (hero image, description block,
     * instruction step, or review avatar). A no-op if the photo has no URL, e.g. an avatar
     * still showing its initials fallback.
     *
     * @param imageUrl the photo's URL, or {@code null}/blank if there is none to show
     */
    private void openFullscreenImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        Intent intent = new Intent();
        intent.putExtra(FullscreenImageActivity.EXTRA_IMAGE_URL, imageUrl);
        Navigator.start(this, FullscreenImageActivity.class, intent);
    }

    /**
     * Shows a confirm dialog before deleting a review the current user authored, matching the
     * style of {@link com.cooksync.app.ui.recipe.list.MyRecipesActivity}'s recipe delete confirm.
     *
     * @param review the review to delete
     */
    private void confirmDeleteReview(ReviewResponse review) {
        OrganicConfirmDialog.show(this, getString(R.string.dialog_delete_review_title),
                getString(R.string.dialog_delete_review_message),
                getString(R.string.action_delete), getString(R.string.action_cancel), true, () -> {
                    pendingDeletedReview = review;
                    allReviews.remove(review);
                    refreshReviewsDisplay();
                    viewModel.deleteReview(review.id());
                    OrganicToast.showWithAction(this, null, R.drawable.ic_delete,
                            getString(R.string.review_deleted), getString(R.string.action_undo), () -> {
                        if (viewModel.undoDeleteReview(review.id())) {
                            allReviews.add(review);
                            pendingDeletedReview = null;
                            refreshReviewsDisplay();
                        }
                    });
                });
    }

    private NoteResponse findRecipeNote() {
        for (NoteResponse note : currentNotes) if (note.instructionId() == null) return note;
        return null;
    }

    private NoteResponse findStepNote(String instructionId) {
        for (NoteResponse note : currentNotes) if (Objects.equals(instructionId, note.instructionId())) return note;
        return null;
    }

    private void renderRecipeNote() {
        NoteResponse note = findRecipeNote();
        boolean hasNote = note != null;
        tvNote.setText(hasNote ? note.note() : getString(R.string.recipe_add_note_hint));
        tvNote.setAlpha(hasNote ? 1f : 0.7f);
    }

    /**
     * Switches the recipe-wide note card from its display state into inline edit mode,
     * pre-filling the {@link EditText} with the existing note text, if any.
     */
    private void openRecipeNoteEditor() {
        NoteResponse existing = findRecipeNote();
        String text = existing != null ? existing.note() : "";
        etNote.setText(text);
        etNote.setSelection(text.length());
        groupNoteView.setVisibility(View.GONE);
        groupNoteEdit.setVisibility(View.VISIBLE);
        btnNoteDelete.setVisibility(existing != null ? View.VISIBLE : View.GONE);
        noteEditCommitted = false;
    }

    /**
     * Closes the recipe-wide note card's inline editor without saving, restoring the display
     * state.
     */
    private void closeRecipeNoteEditor() {
        groupNoteEdit.setVisibility(View.GONE);
        groupNoteView.setVisibility(View.VISIBLE);
    }

    /**
     * Commits the recipe-wide note editor's current text. Called both from the explicit save
     * icon and from the {@link EditText} losing focus (i.e. the user taps outside it) — the
     * {@link #noteEditCommitted} guard makes whichever fires second a no-op, since tapping the
     * save/delete icon itself blurs the field first. Only saves if the text is non-blank and
     * actually changed.
     */
    private void commitRecipeNoteInline() {
        if (noteEditCommitted) return;
        noteEditCommitted = true;
        String text = etNote.getText() == null ? "" : etNote.getText().toString().trim();
        NoteResponse existing = findRecipeNote();
        String currentText = existing != null ? existing.note() : "";
        if (!text.isEmpty() && !Objects.equals(text, currentText)) {
            String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
            viewModel.saveNote(recipeId, null, text);
        }
        closeRecipeNoteEditor();
    }

    private void deleteRecipeNoteInline() {
        if (noteEditCommitted) return;
        noteEditCommitted = true;
        NoteResponse existing = findRecipeNote();
        if (existing != null) viewModel.deleteNote(existing.id());
        closeRecipeNoteEditor();
    }

    private void renderStepNotes() {
        Map<String, String> stepNotes = new HashMap<>();
        for (NoteResponse note : currentNotes) {
            if (note.instructionId() != null) stepNotes.put(note.instructionId(), note.note());
        }
        instructionAdapter.setNotes(stepNotes);
    }

    /**
     * Shows or hides the skeleton loading placeholder, toggling it against the real content
     * scroll view and the bottom action bar (which isn't actionable before data arrives).
     *
     * @param show {@code true} to show the skeleton and hide real content, {@code false} to reveal it
     * @param ignored unused; {@link BaseActivity#showSkeleton} accepts a content view to toggle,
     *                but this screen swaps {@link #contentGroup} and {@link #bottomBar} directly
     */
    @Override
    protected void showSkeleton(boolean show, View ignored) {
        if (show) {
            if (skeletonHelper != null) skeletonHelper.start();
            if (skeletonView != null) skeletonView.setVisibility(View.VISIBLE);
            contentGroup.setVisibility(View.INVISIBLE);
            bottomBar.setVisibility(View.INVISIBLE);
        } else {
            if (skeletonHelper != null) skeletonHelper.stop();
            if (skeletonView != null) skeletonView.setVisibility(View.GONE);
            contentGroup.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
        }
    }

    private void bindRecipe(RecipeResponse recipe) {
        String authorName = recipe.createdBy() != null
                ? recipe.createdBy().firstName() + " " + recipe.createdBy().lastName()
                : getString(R.string.anonymous);
        kicker.setText(getString(R.string.recipe_kicker_format, authorName, formatPublishedDate(recipe.createdAt())));

        title.setText(recipe.title());
        rating.setText(recipe.averageRating() == null ? "0.0" : String.format(Locale.US, "%.1f", recipe.averageRating()));
        reviewCount.setText(getString(R.string.review_count_format, recipe.reviewCount()));
        prepTime.setText(getString(R.string.time_format_short, recipe.prepTimeMinutes()));
        cookTime.setText(getString(R.string.time_format_short, recipe.cookTimeMinutes()));
        servings.setText(String.valueOf(recipe.servings()));
        ingredientsHeader.setText(getString(R.string.ingredients_header_format, recipe.servings()));

        String currentUserId = SessionManager.getInstance().getUserId();
        boolean isMine = recipe.createdBy() != null && Objects.equals(recipe.createdBy().id(), currentUserId);
        if (btnEditRecipe != null) {
            btnEditRecipe.setVisibility(isMine ? View.VISIBLE : View.GONE);
            btnEditRecipe.setOnClickListener(v -> AddRecipeWizardActivity.startEdit(this, recipe));
        }

        Glide.with(this).load(recipe.primaryImageUrl()).placeholder(R.color.color_neutral_300).centerCrop().into(heroImage);
        heroImage.setOnClickListener(v -> openFullscreenImage(recipe.primaryImageUrl()));

        descriptionBlockAdapter.setBlocks(recipe.descriptionBlocks());
        ingredientAdapter.setIngredients(new ArrayList<>(recipe.ingredients()));
        List<InstructionResponse> sortedInstructions = new ArrayList<>(recipe.instructions());
        sortedInstructions.sort(Comparator.comparingInt(InstructionResponse::stepNumber));
        instructionAdapter.setInstructions(sortedInstructions);
        tagAdapter.setTags(recipe.tags());

        allReviews.clear();
        if (recipe.reviews() != null) allReviews.addAll(recipe.reviews());
        activeStarFilter = null;
        sortIndex = 0;
        btnSortReviews.setText(SORT_OPTIONS[sortIndex]);

        bindRatingSummary(recipe);
        refreshReviewsDisplay();
    }

    /** Per-star review counts, index 1..5, recomputed each time {@link #allReviews} changes. */
    private final int[] starCounts = new int[6];

    /**
     * Populates the average-rating headline and the five per-star breakdown bars from the
     * recipe's review list, matching the design's reviews-summary card. The bars are a
     * decorative percentage breakdown, matching the design (no tap behavior); filtering by
     * star is done separately via {@link #bindStarChips()}.
     *
     * @param recipe the recipe whose reviews back the summary
     */
    private void bindRatingSummary(RecipeResponse recipe) {
        summaryRating.setText(recipe.averageRating() == null ? "0.0" : String.format(Locale.US, "%.1f", recipe.averageRating()));
        summaryStars.setText(starsForRating(recipe.averageRating()));

        java.util.Arrays.fill(starCounts, 0);
        for (ReviewResponse review : allReviews) starCounts[clampStars(review.rating())]++;

        int total = allReviews.size();
        bindBarRow(R.id.bar_row_5, 5, total);
        bindBarRow(R.id.bar_row_4, 4, total);
        bindBarRow(R.id.bar_row_3, 3, total);
        bindBarRow(R.id.bar_row_2, 2, total);
        bindBarRow(R.id.bar_row_1, 1, total);

        bindStarChips();
    }

    private void bindBarRow(int rowId, int star, int total) {
        View row = findViewById(rowId);
        TextView starLabel = row.findViewById(R.id.star_label);
        View barFill = row.findViewById(R.id.bar_fill);
        View barSpacer = row.findViewById(R.id.bar_spacer);
        TextView pctLabel = row.findViewById(R.id.pct_label);

        int count = starCounts[star];
        int percent = total == 0 ? 0 : Math.round((count * 100f) / total);
        starLabel.setText(String.valueOf(star));
        pctLabel.setText(String.valueOf(count));

        android.widget.LinearLayout.LayoutParams fillParams = (android.widget.LinearLayout.LayoutParams) barFill.getLayoutParams();
        fillParams.weight = percent;
        barFill.setLayoutParams(fillParams);

        android.widget.LinearLayout.LayoutParams spacerParams = (android.widget.LinearLayout.LayoutParams) barSpacer.getLayoutParams();
        spacerParams.weight = 100 - percent;
        barSpacer.setLayoutParams(spacerParams);
    }

    /**
     * Wires the five star-value filter chips (5★..1★), each showing how many reviews carry
     * that rating. Tapping a chip toggles {@link #activeStarFilter} down to that star (or
     * clears it if already active), matching the design's "{{ rev.chips }}" row.
     */
    private void bindStarChips() {
        for (int star : STAR_VALUES) {
            com.google.android.material.card.MaterialCardView chip = starChips.get(star);
            if (chip == null) continue;
            TextView label = chip.findViewById(R.id.star_chip_label);
            TextView count = chip.findViewById(R.id.star_chip_count);
            label.setText(getString(R.string.star_chip_label_format, star));
            count.setText(String.valueOf(starCounts[star]));
            chip.setOnClickListener(v -> {
                activeStarFilter = (activeStarFilter != null && activeStarFilter == star) ? null : star;
                refreshReviewsDisplay();
            });
        }
        updateStarChipHighlight();
    }

    private void updateStarChipHighlight() {
        for (int star : STAR_VALUES) {
            com.google.android.material.card.MaterialCardView chip = starChips.get(star);
            if (chip == null) continue;
            TextView label = chip.findViewById(R.id.star_chip_label);
            TextView count = chip.findViewById(R.id.star_chip_count);
            boolean active = activeStarFilter != null && activeStarFilter == star;
            chip.setCardBackgroundColor(getColor(active ? R.color.color_accent : R.color.color_neutral_300));
            int textColor = getColor(active ? R.color.color_bg : R.color.color_text);
            label.setTextColor(textColor);
            count.setTextColor(textColor);
        }
    }

    /**
     * Recomputes the displayed review list from {@link #allReviews}: applies
     * {@link #activeStarFilter} (if any) then sorts per the current {@link #SORT_OPTIONS}
     * selection, and updates the "N reviews · ..." summary label to match. Shows the empty
     * state card in place of the list when the active filter matches nothing.
     */
    private void refreshReviewsDisplay() {
        updateStarChipHighlight();
        List<ReviewResponse> displayed = new ArrayList<>(allReviews);
        if (activeStarFilter != null) displayed.removeIf(r -> clampStars(r.rating()) != activeStarFilter);

        Comparator<ReviewResponse> comparator;
        String sortOption = SORT_OPTIONS[sortIndex];
        if (Objects.equals(sortOption, "Highest rated")) {
            comparator = Comparator.comparing((ReviewResponse r) -> r.rating() == null ? BigDecimal.ZERO : r.rating(), Comparator.reverseOrder());
        } else if (Objects.equals(sortOption, "Lowest rated")) {
            comparator = Comparator.comparing((ReviewResponse r) -> r.rating() == null ? BigDecimal.ZERO : r.rating());
        } else {
            comparator = Comparator.comparing((ReviewResponse r) -> r.createdAt() == null ? "" : r.createdAt(), Comparator.reverseOrder());
        }
        displayed.sort(comparator);
        reviewAdapter.setReviews(displayed);

        reviewsSummaryLabel.setText(activeStarFilter == null
                ? getString(R.string.reviews_count_summary, displayed.size())
                : getString(R.string.reviews_count_filtered_summary, displayed.size(), activeStarFilter));

        reviewsEmptyState.setVisibility(displayed.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String starsForRating(Double averageRating) {
        int filled = averageRating == null ? 0 : (int) Math.round(averageRating);
        StringBuilder stars = new StringBuilder();
        int j = 0;
        while (j < 5) {
            stars.append(j < filled ? "★" : "☆");
            j++;
        }
        return stars.toString();
    }

    private int clampStars(BigDecimal rating) {
        if (rating == null) return 1;
        int rounded = Math.round(rating.floatValue());
        return Math.max(1, Math.min(5, rounded));
    }

    /**
     * Formats an ISO-8601 timestamp into a "Month yyyy" label for the recipe kicker
     * (e.g. "April 2026"). Falls back to an empty string if the timestamp can't be parsed.
     *
     * @param isoTimestamp the recipe's {@code createdAt} value
     * @return a human-readable "Month yyyy" string, or "" if unparseable
     */
    private String formatPublishedDate(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isBlank()) return "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                LocalDate date = LocalDate.parse(isoTimestamp.substring(0, 10));
                return date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + date.getYear();
            } catch (DateTimeParseException | IndexOutOfBoundsException e) {
                return "";
            }
        }
        return isoTimestamp.substring(0, 10); // Fallback for older APIs
    }

    private void updateFavoriteIcon() {
        btnFavorite.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }
}
