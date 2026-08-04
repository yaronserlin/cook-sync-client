package com.cooksync.app.ui.recipe;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.cooksync.app.R;

/**
 * Placeholder landing screen for the review/rating flow. {@link CookingModeActivity}'s
 * "Done — rate it" button navigates here once a cook finishes the last step; the full
 * rating/review-submission UI is a separate, not-yet-built module (tracked as its own
 * follow-up), so this screen only confirms cooking finished and lets the user return to the
 * recipe rather than hitting an unregistered activity.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class ReviewActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        findViewById(R.id.btn_review_close).setOnClickListener(v -> finish());
    }
}
