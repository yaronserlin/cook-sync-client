package com.cooksync.app.ui.common;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
 * <p>Not used for recipe/search list-card thumbnails, since those already navigate to the
 * recipe detail screen on tap.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 06/08/2026
 */
public class FullscreenImageActivity extends BaseActivity {

    /** The image URL to display, loaded via Glide. Required. */
    public static final String EXTRA_IMAGE_URL = "extra_image_url";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        if (imageUrl == null || imageUrl.isBlank()) {
            finish();
            return;
        }

        enterImmersiveMode();

        PhotoView photoView = findViewById(R.id.fullscreen_image);
        ProgressBar progressBar = findViewById(R.id.fullscreen_progress);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        loadImage(imageUrl, photoView, progressBar);
    }

    /**
     * Hides the status and navigation bars for a full-bleed black canvas, matching common
     * photo-viewer UX. Bars can still be revealed with an edge swipe and are restored
     * automatically when this activity closes.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
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
     * Complexity:
     * Time: O(1) plus one asynchronous image load
     * Space: O(1)
     *
     * @param imageUrl the image URL to load
     * @param photoView the zoomable target view
     * @param progressBar the spinner shown while the image is loading
     */
    private void loadImage(String imageUrl, PhotoView photoView, ProgressBar progressBar) {
        progressBar.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(imageUrl)
                .fitCenter()
                .listener(new RequestListener<>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        showError(getString(R.string.error_load_image), null);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                        progressBar.setVisibility(View.GONE);
                        photoView.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
                .into(photoView);
    }
}
