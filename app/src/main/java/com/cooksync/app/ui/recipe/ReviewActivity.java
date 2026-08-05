package com.cooksync.app.ui.recipe;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;

import java.util.Arrays;
import java.util.List;

/**
 * "Rate & review" submission screen. Reached either from {@link CookingModeActivity}'s
 * "Done" button once a cook finishes the last step, or from the recipe detail screen's
 * "Write a review" button. Lets the user pick a 1–5 star rating, a short title, and an
 * optional comment, then submits it via {@link ReviewViewModel}.
 *
 * @author Yaron Serlin
 * @version 2.0
 * @since 04/08/2026
 */
public class ReviewActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    private ReviewViewModel viewModel;
    private String recipeId;

    private List<ImageView> stars;
    private TextView tvRatingLabel;
    private EditText etTitle;
    private EditText etComment;
    private View btnSubmit;
    private View btnSkip;
    private ProgressBar progress;

    private int selectedRating = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
        viewModel = new ViewModelProvider(this).get(ReviewViewModel.class);

        bindViews();
        setupStarInput();
        setupObservers();

        btnSubmit.setOnClickListener(v -> viewModel.submitReview(
                recipeId, selectedRating, etTitle.getText().toString(), etComment.getText().toString()));
        btnSkip.setOnClickListener(v -> finish());
    }

    private void bindViews() {
        ImageView star1 = findViewById(R.id.star_1);
        ImageView star2 = findViewById(R.id.star_2);
        ImageView star3 = findViewById(R.id.star_3);
        ImageView star4 = findViewById(R.id.star_4);
        ImageView star5 = findViewById(R.id.star_5);
        stars = Arrays.asList(star1, star2, star3, star4, star5);

        tvRatingLabel = findViewById(R.id.tv_rating_label);
        etTitle = findViewById(R.id.et_review_title);
        etComment = findViewById(R.id.et_review_comment);
        btnSubmit = findViewById(R.id.btn_submit_review);
        btnSkip = findViewById(R.id.btn_review_close);
        progress = findViewById(R.id.review_progress);
    }

    private void setupStarInput() {
        for (int i = 0; i < stars.size(); i++) {
            int starValue = i + 1;
            stars.get(i).setOnClickListener(v -> selectRating(starValue));
        }
        renderStars();
    }

    private void selectRating(int rating) {
        selectedRating = rating;
        renderStars();
        tvRatingLabel.setText(getString(R.string.review_rating_selected_format, rating));
    }

    private void renderStars() {
        int selectedColor = getColor(R.color.color_accent);
        int unselectedColor = getColor(R.color.color_neutral_400);
        for (int i = 0; i < stars.size(); i++) {
            boolean filled = i < selectedRating;
            ImageViewCompat.setImageTintList(stars.get(i),
                    ColorStateList.valueOf(filled ? selectedColor : unselectedColor));
        }
    }

    private void setupObservers() {
        viewModel.getValidationError().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getSubmitResult().observe(this, result -> {
            if (result instanceof ApiResult.Loading) {
                setSubmitting(true);
            } else if (result instanceof ApiResult.Success) {
                setSubmitting(false);
                Toast.makeText(this, R.string.review_submitted, Toast.LENGTH_SHORT).show();
                finish();
            } else if (result instanceof ApiResult.Error<?> error) {
                setSubmitting(false);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setSubmitting(boolean submitting) {
        progress.setVisibility(submitting ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!submitting);
        btnSkip.setEnabled(!submitting);
    }
}
