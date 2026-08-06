package com.cooksync.app.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Helper for deriving a small, content-aware-cropped Cloudinary thumbnail URL from a full-size
 * Cloudinary asset URL, so avatar-sized image requests (e.g. review author photos) don't
 * download a full-resolution image. Relies on Cloudinary's URL-based transformation syntax:
 * inserting a transformation segment right after {@code /upload/} in the delivery URL. Uses
 * {@code g_auto} rather than {@code g_face} gravity, since basic face-detection cropping is
 * plan-gated on some Cloudinary accounts while {@code g_auto} is universally available.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 05/08/2026
 */
public final class CloudinaryImageUtils {

    private static final String UPLOAD_SEGMENT = "/upload/";

    private CloudinaryImageUtils() {
    }

    /**
     * Builds a square, face-cropped thumbnail URL from a Cloudinary asset URL.
     *
     * Complexity:
     * Time: O(n) in the URL length
     * Space: O(n) in the URL length
     *
     * @param url the original Cloudinary asset URL, may be {@code null}/blank
     * @param sizePx the target width/height of the thumbnail, in pixels
     * @return the transformed thumbnail URL, or the original {@code url} if it isn't a
     *         recognizable Cloudinary upload URL, or {@code null} if {@code url} was blank
     */
    @Nullable
    public static String thumbnailUrl(@Nullable String url, int sizePx) {
        if (url == null || url.isBlank()) {
            return null;
        }
        int uploadIndex = url.indexOf(UPLOAD_SEGMENT);
        if (uploadIndex < 0) {
            return url;
        }
        int insertAt = uploadIndex + UPLOAD_SEGMENT.length();
        String transform = "w_" + sizePx + ",h_" + sizePx + ",c_fill,g_auto/";
        return url.substring(0, insertAt) + transform + url.substring(insertAt);
    }

    /**
     * Builds a two-letter initials string from a display name, matching the convention already
     * used across the app's avatar chips.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param displayName the name to derive initials from, may be {@code null}/blank
     * @return up to two uppercase initial characters, or {@code "?"} if unavailable
     */
    @NonNull
    public static String initialsOf(@Nullable String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "?";
        }
        String[] parts = displayName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }
        return initials.length() > 0 ? initials.toString() : "?";
    }
}
