package com.cooksync.app.ui.common;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.cooksync.app.R;
import com.github.chrisbanes.photoview.PhotoView;

/**
 * Reusable full-screen, pinch-to-zoom viewer for a single standalone content photo: the recipe
 * hero image, an inline description photo, an instruction-step photo, or a user avatar. Shared
 * across every screen that shows one of those photos, rather than each screen rolling its own
 * viewer, per the app's DRY/reuse convention.
 *
 * <p>Not used for recipe/search list-card thumbnails, since those already navigate to
 * {@link com.cooksync.app.ui.detail.RecipeDetailActivity} on tap.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 06/08/2026
 */
public class FullscreenImageActivity extends AppCompatActivity {

    /** The image URL to display, loaded via Glide. Required. */
    public static final String EXTRA_IMAGE_URL = "extra_image_url";

    private PhotoView photoView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        if (imageUrl == null || imageUrl.isBlank()) {
            finish();
            return;
        }

        enterImmersiveMode();

        photoView = findViewById(R.id.fullscreen_image);
        progressBar = findViewById(R.id.fullscreen_progress);
        ImageButton btnClose = findViewById(R.id.btn_close);

        btnClose.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        loadImage(imageUrl);
    }

    /**
     * Hides the status and navigation bars for a full-bleed black canvas, matching common
     * photo-viewer UX. Bars can still be revealed with an edge swipe and are restored
     * automatically when this activity closes.
     */
    private void enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    /**
     * Loads the photo into the zoomable {@link PhotoView}, keeping it hidden behind the
     * progress spinner until the load actually succeeds, matching the "hide until ready"
     * pattern used for other images in the app.
     *
     * @param imageUrl the image URL to load
     */
    private void loadImage(String imageUrl) {
        Glide.with(this)
                .load(imageUrl)
                .fitCenter()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                 Target<Drawable> target, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        OrganicToast.show(FullscreenImageActivity.this, null, "Couldn't load image");
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                                    DataSource dataSource, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        photoView.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
                .into(photoView);
    }
}
