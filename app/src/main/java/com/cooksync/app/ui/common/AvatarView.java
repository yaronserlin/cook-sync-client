package com.cooksync.app.ui.common;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.cooksync.app.R;
import com.cooksync.app.util.CloudinaryImageUtils;

/**
 * Reusable circular avatar component: shows a remote profile photo when one is available,
 * falling back to an accent-colored circle with the user's initials otherwise. Meant to be
 * dropped into any layout that needs to display a user's avatar (reviews, comments, member
 * lists, profile chips, ...) instead of each screen re-implementing the same
 * Glide-load/fallback logic.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public class AvatarView extends FrameLayout {

    /** Thumbnail edge length requested from Cloudinary, in pixels. Generous relative to the
     *  view's typical 32-40dp on-screen size to stay sharp on high-density screens. */
    private static final int THUMBNAIL_SIZE_PX = 96;

    private ImageView image;
    private TextView initials;

    public AvatarView(Context context) {
        super(context);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        View.inflate(getContext(), R.layout.view_avatar, this);
        image = findViewById(R.id.avatar_image);
        initials = findViewById(R.id.avatar_initials);
    }

    /**
     * Binds the avatar to a photo URL and a display name. Loads a Cloudinary face-cropped
     * thumbnail into the image when {@code avatarUrl} is present; otherwise shows the
     * initials-circle fallback derived from {@code displayName}.
     *
     * Complexity:
     * Time: O(1) plus an asynchronous image load
     * Space: O(1)
     *
     * @param avatarUrl the user's avatar photo URL, or {@code null}/blank if unset
     * @param displayName the user's display name, used to derive fallback initials
     */
    public void setAvatar(@Nullable String avatarUrl, @Nullable String displayName) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            image.setVisibility(GONE);
            initials.setVisibility(VISIBLE);
            initials.setText(CloudinaryImageUtils.initialsOf(displayName));
            return;
        }
        initials.setVisibility(GONE);
        image.setVisibility(VISIBLE);
        Glide.with(image.getContext())
                .load(CloudinaryImageUtils.thumbnailUrl(avatarUrl, THUMBNAIL_SIZE_PX))
                .placeholder(R.drawable.bg_accent_2_circle)
                .circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                 Target<Drawable> target, boolean isFirstResource) {
                        // Falls back to the initials circle instead of leaving a blank image
                        // view when the photo can't be loaded (bad URL, network error, ...).
                        Log.w("AvatarView", "Failed to load avatar: " + model, e);
                        image.setVisibility(GONE);
                        initials.setVisibility(VISIBLE);
                        initials.setText(CloudinaryImageUtils.initialsOf(displayName));
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                                    DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(image);
    }
}
