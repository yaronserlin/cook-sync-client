package com.cooksync.app.ui.common;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.cooksync.app.R;
import com.cooksync.app.util.CloudinaryImageUtils;

/**
 * Custom, reusable view for profile avatars: renders a circular Cloudinary-hosted photo when
 * a URL is available, or a two-letter initials badge as a fallback (no URL, or the image
 * failed to load). Used across profile, review, and recipe-author displays so avatar
 * rendering logic lives in exactly one place.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class AvatarView extends FrameLayout {

    /** Target width/height requested from Cloudinary — small enough to keep downloads cheap. */
    private static final int THUMBNAIL_SIZE_PX = 96;

    private ImageView imageView;
    private TextView initialsView;

    public AvatarView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        inflate(context, R.layout.view_avatar, this);
        imageView = findViewById(R.id.avatar_image);
        initialsView = findViewById(R.id.avatar_initials);
    }

    /**
     * Renders either an avatar image (if a URL is provided) or a fallback initials badge.
     * The URL is requested through {@link CloudinaryImageUtils#thumbnailUrl} so only a
     * small, cropped thumbnail is downloaded rather than the full-resolution asset.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param url the profile photo URL, may be {@code null}/blank
     * @param name full name to generate fallback initials from
     */
    public void setAvatar(@Nullable String url, @Nullable String name) {
        if (url != null && !url.isBlank()) {
            initialsView.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE);
            Glide.with(getContext())
                    .load(CloudinaryImageUtils.thumbnailUrl(url, THUMBNAIL_SIZE_PX))
                    .transform(new CircleCrop())
                    .listener(new RequestListener<>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                            Log.w("AvatarView", "Failed to load avatar: " + model, e);
                            renderInitials(name);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);
        } else {
            renderInitials(name);
        }
    }

    /**
     * Hides the image and shows the initials badge derived from {@code name}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param name full name to generate initials from, may be {@code null}/blank
     */
    private void renderInitials(@Nullable String name) {
        imageView.setVisibility(View.GONE);
        initialsView.setVisibility(View.VISIBLE);
        initialsView.setText(CloudinaryImageUtils.initialsOf(name));
    }
}
