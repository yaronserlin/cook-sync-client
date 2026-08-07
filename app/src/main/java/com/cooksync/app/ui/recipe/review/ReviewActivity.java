package com.cooksync.app.ui.recipe.review;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.BaseActivity;
import com.cooksync.app.ui.common.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for submitting a new recipe review: a required 1-5 star rating, a title, and an
 * optional comment. The submit button stays disabled until a star is tapped, since a review
 * with no rating isn't a valid submission.
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 04/08/2026
 */
public class ReviewActivity extends BaseActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    private ReviewViewModel viewModel;
    private final List<ImageView> stars = new ArrayList<>();
    private TextView tvRatingLabel;
    private EditText etTitle;
    private EditText etComment;
    private com.google.android.material.button.MaterialButton btnSubmit;
    private View progress;

    private int selectedRating = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
        if (recipeId == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(ReviewViewModel.class);

        initViews();
        setupObservers();
    }

    private void initViews() {
        tvRatingLabel = findViewById(R.id.tv_rating_label);
        etTitle = findViewById(R.id.et_review_title);
        etComment = findViewById(R.id.et_review_comment);
        btnSubmit = findViewById(R.id.btn_submit_review);
        progress = findViewById(R.id.review_progress);

        stars.add(findViewById(R.id.star_1));
        stars.add(findViewById(R.id.star_2));
        stars.add(findViewById(R.id.star_3));
        stars.add(findViewById(R.id.star_4));
        stars.add(findViewById(R.id.star_5));

        int j = 0;
        while (j < stars.size()) {
            final int rating = j + 1;
            stars.get(j).setOnClickListener(v -> updateRating(rating));
            j++;
        }

        findViewById(R.id.btn_review_close).setOnClickListener(v -> finish());

        btnSubmit.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String comment = etComment.getText().toString().trim();
            viewModel.submitReview(getIntent().getStringExtra(EXTRA_RECIPE_ID), selectedRating, title, comment);
        });

        updateRating(0);
    }

    private void setupObservers() {
        viewModel.getSubmitResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                setLoading(true);
            } else if (result instanceof ApiResult.Success) {
                showSuccess(getString(R.string.review_submitted), null);
                finish();
            } else if (result instanceof ApiResult.Error<Void> error) {
                setLoading(false);
                showError(error.getMessage(), null);
            }
        });
    }

    /**
     * Sets the selected star rating (0 for none), restyling the star icons and toggling the
     * submit button's enabled state to match.
     *
     * @param rating the newly selected rating, 0-5
     */
    private void updateRating(int rating) {
        selectedRating = rating;
        for (int i = 0; i < stars.size(); i++) {
            stars.get(i).setImageResource(R.drawable.ic_star);
            stars.get(i).setColorFilter(getColor(i < rating ? R.color.color_accent : R.color.color_neutral_400));
        }

        if (rating == 0) {
            tvRatingLabel.setText(R.string.review_tap_to_rate);
            tvRatingLabel.setAlpha(0.6f);
            btnSubmit.setEnabled(false);
            btnSubmit.setAlpha(0.5f);
        } else {
            tvRatingLabel.setText(getString(R.string.review_rating_selected_format, rating));
            tvRatingLabel.setAlpha(1.0f);
            btnSubmit.setEnabled(true);
            btnSubmit.setAlpha(1.0f);
        }
    }

    /**
     * Toggles the in-flight submission state: shows/hides the progress indicator, swaps the
     * submit button out so it can't be double-tapped, and disables every input field.
     *
     * @param loading {@code true} while the submit call is in flight
     */
    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
        etTitle.setEnabled(!loading);
        etComment.setEnabled(!loading);
        for (ImageView star : stars) star.setEnabled(!loading);
    }
}
