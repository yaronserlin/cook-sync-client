package com.cooksync.app.ui.detail;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.SkeletonHelper;
import com.cooksync.app.ui.home.TagChipAdapter;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.review.ReviewResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
public class RecipeDetailActivity extends AppCompatActivity {

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
    private SkeletonHelper skeletonHelper;

    private NestedScrollView scrollView;
    private View skeletonView;
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

    private boolean isFavorite = false;
    private final List<ReviewResponse> allReviews = new ArrayList<>();
    private final java.util.Map<Integer, com.google.android.material.card.MaterialCardView> starChips = new java.util.HashMap<>();
    private Integer activeStarFilter = null;
    private int sortIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
        if (recipeId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(RecipeDetailViewModel.class);

        initViews();
        setupAdapters();
        setupObservers();

        showSkeleton(true);
        viewModel.loadRecipe(recipeId);
        viewModel.loadFavorites();
    }

    private void initViews() {
        scrollView = findViewById(R.id.detail_scroll);
        skeletonView = findViewById(R.id.detail_skeleton);
        contentGroup = scrollView;
        bottomBar = findViewById(R.id.detail_bottom_bar);
        skeletonHelper = new SkeletonHelper();
        skeletonHelper.attachAll((android.view.ViewGroup) skeletonView);

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

        starChips.put(5, findViewById(R.id.chip_star_5));
        starChips.put(4, findViewById(R.id.chip_star_4));
        starChips.put(3, findViewById(R.id.chip_star_3));
        starChips.put(2, findViewById(R.id.chip_star_2));
        starChips.put(1, findViewById(R.id.chip_star_1));

        findViewById(R.id.btn_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        btnFavorite.setOnClickListener(v -> {
            String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
            viewModel.toggleFavorite(recipeId, isFavorite);
            isFavorite = !isFavorite;
            updateFavoriteIcon();
        });

        ratingRow.setOnClickListener(v -> scrollView.smoothScrollTo(0, reviewsHeader.getTop() + ((View) reviewsHeader.getParent()).getTop()));

        findViewById(R.id.btn_start_cooking).setOnClickListener(v ->
                Toast.makeText(this, "Cooking Mode coming soon!", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btn_review).setOnClickListener(v ->
                Toast.makeText(this, "Review functionality coming soon!", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btn_offline_download).setOnClickListener(v ->
                Toast.makeText(this, "Offline download coming soon!", Toast.LENGTH_SHORT).show());

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
        rvInstructions.setAdapter(instructionAdapter);

        RecyclerView rvReviews = findViewById(R.id.rv_reviews);
        reviewAdapter = new ReviewAdapter();
        rvReviews.setAdapter(reviewAdapter);

        RecyclerView rvDescriptionBlocks = findViewById(R.id.rv_description_blocks);
        descriptionBlockAdapter = new DescriptionBlockAdapter();
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
                showSkeleton(false);
            } else if (result instanceof ApiResult.Error<RecipeResponse> error) {
                showSkeleton(false);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getFavoritesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
                isFavorite = false;
                for (RecipePreviewResponse fav : success.getData()) {
                    if (fav.id().equals(recipeId)) {
                        isFavorite = true;
                        break;
                    }
                }
                updateFavoriteIcon();
            }
        });
    }

    /**
     * Shows or hides the skeleton loading placeholder, toggling it against the real content
     * scroll view and the bottom action bar (which isn't actionable before data arrives).
     *
     * @param show {@code true} to show the skeleton and hide real content, {@code false} to reveal it
     */
    private void showSkeleton(boolean show) {
        if (show) {
            skeletonHelper.start();
            skeletonView.setVisibility(View.VISIBLE);
            contentGroup.setVisibility(View.INVISIBLE);
            bottomBar.setVisibility(View.INVISIBLE);
        } else {
            skeletonHelper.stop();
            skeletonView.setVisibility(View.GONE);
            contentGroup.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
        }
    }

    private void bindRecipe(RecipeResponse recipe) {
        String authorName = recipe.createdBy() != null
                ? recipe.createdBy().firstName() + " " + recipe.createdBy().lastName()
                : "Anonymous";
        kicker.setText(getString(R.string.recipe_kicker_format, authorName, formatPublishedDate(recipe.createdAt())));

        title.setText(recipe.title());
        rating.setText(recipe.averageRating() == null ? "0.0" : String.format(Locale.US, "%.1f", recipe.averageRating()));
        reviewCount.setText(getString(R.string.review_count_format, recipe.reviewCount()));
        prepTime.setText(getString(R.string.time_format_short, recipe.prepTimeMinutes()));
        cookTime.setText(getString(R.string.time_format_short, recipe.cookTimeMinutes()));
        servings.setText(String.valueOf(recipe.servings()));
        ingredientsHeader.setText(getString(R.string.ingredients_header_format, recipe.servings()));

        Glide.with(this)
                .load(recipe.primaryImageUrl())
                .placeholder(R.color.color_neutral_300)
                .centerCrop()
                .into(heroImage);

        descriptionBlockAdapter.setBlocks(recipe.descriptionBlocks());

        ingredientAdapter.setIngredients(new ArrayList<>(recipe.ingredients()));
        instructionAdapter.setInstructions(recipe.instructions());
        tagAdapter.setTags(recipe.tags());

        allReviews.clear();
        if (recipe.reviews() != null) {
            allReviews.addAll(recipe.reviews());
        }
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
        for (ReviewResponse review : allReviews) {
            starCounts[clampStars(review.rating())]++;
        }

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
        TextView pctLabel = row.findViewById(R.id.pct_label);

        int count = starCounts[star];
        int percent = total == 0 ? 0 : Math.round((count * 100f) / total);
        starLabel.setText(String.valueOf(star));
        pctLabel.setText(String.valueOf(count));

        android.widget.LinearLayout.LayoutParams params =
                (android.widget.LinearLayout.LayoutParams) barFill.getLayoutParams();
        params.weight = percent;
        barFill.setLayoutParams(params);
    }

    /**
     * Wires the five star-value filter chips (5★..1★), each showing how many reviews carry
     * that rating. Tapping a chip toggles {@link #activeStarFilter} down to that star (or
     * clears it if already active), matching the design's "{{ rev.chips }}" row.
     */
    private void bindStarChips() {
        for (int star : STAR_VALUES) {
            com.google.android.material.card.MaterialCardView chip = starChips.get(star);
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

        if (activeStarFilter != null) {
            displayed.removeIf(r -> clampStars(r.rating()) != activeStarFilter);
        }

        Comparator<ReviewResponse> comparator = switch (SORT_OPTIONS[sortIndex]) {
            case "Highest rated" -> Comparator.comparing(
                    (ReviewResponse r) -> r.rating() == null ? BigDecimal.ZERO : r.rating(),
                    Comparator.reverseOrder());
            case "Lowest rated" -> Comparator.comparing(
                    (ReviewResponse r) -> r.rating() == null ? BigDecimal.ZERO : r.rating());
            default -> Comparator.comparing(
                    (ReviewResponse r) -> r.createdAt() == null ? "" : r.createdAt(),
                    Comparator.reverseOrder());
        };
        displayed.sort(comparator);

        reviewAdapter.setReviews(displayed);

        reviewsSummaryLabel.setText(activeStarFilter == null
                ? displayed.size() + (displayed.size() == 1 ? " review" : " reviews")
                : displayed.size() + " reviews · filtered by " + activeStarFilter + "★");

        reviewsEmptyState.setVisibility(displayed.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String starsForRating(Double averageRating) {
        int filled = averageRating == null ? 0 : (int) Math.round(averageRating);
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            stars.append(i < filled ? "★" : "☆");
        }
        return stars.toString();
    }

    private int clampStars(BigDecimal rating) {
        if (rating == null) {
            return 1;
        }
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
        if (isoTimestamp == null || isoTimestamp.isBlank()) {
            return "";
        }
        try {
            LocalDate date = LocalDate.parse(isoTimestamp.substring(0, 10));
            return date.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + date.getYear();
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return "";
        }
    }

    private void updateFavoriteIcon() {
        btnFavorite.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart_outline);
    }
}
