package com.cooksync.app.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Thin wrapper around the Cloudinary Android SDK for direct client-to-Cloudinary uploads
 * authorized by a short-lived server-issued {@link CloudinarySignatureResponse}. Centralizes
 * the one-time {@link MediaManager#init} call so every upload call site (profile avatar,
 * eventually recipe photos) doesn't need to worry about re-initialization.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public final class CloudinaryUploader {

    private static volatile boolean initialized = false;

    /** Callback for the outcome of an upload. */
    public interface Callback {
        /**
         * Invoked when the upload finishes successfully.
         *
         * @param secureUrl the HTTPS URL of the uploaded asset
         */
        void onSuccess(@NonNull String secureUrl);

        /**
         * Invoked when the upload fails.
         *
         * @param message a user-facing error description
         */
        void onError(@NonNull String message);
    }

    private CloudinaryUploader() {
    }

    /**
     * Uploads the file at {@code fileUri} to Cloudinary using a freshly issued signature,
     * initializing the SDK against the signature's cloud name on first use.
     *
     * Complexity:
     * Time: O(1) plus one asynchronous network upload
     * Space: O(1)
     *
     * @param context the calling screen's context
     * @param fileUri content/file URI of the image to upload (e.g. from a photo picker)
     * @param signature signed upload credentials issued by the server
     * @param callback invoked on the main thread with the outcome
     */
    public static void upload(@NonNull Context context, @NonNull Uri fileUri,
                               @NonNull CloudinarySignatureResponse signature, @NonNull Callback callback) {
        ensureInitialized(context, signature.cloudName());

        MediaManager.get().upload(fileUri)
                .option("api_key", signature.apiKey())
                .option("timestamp", signature.timestamp())
                .option("signature", signature.signature())
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        Object url = resultData.get("secure_url");
                        if (url instanceof String) {
                            callback.onSuccess((String) url);
                        } else {
                            callback.onError("Upload succeeded but no URL was returned.");
                        }
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        callback.onError(error.getDescription());
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        callback.onError(error.getDescription());
                    }
                })
                .dispatch();
    }

    /**
     * Initializes {@link MediaManager} against the given cloud name exactly once per process.
     * Safe to call repeatedly with the same cloud name.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param context application context used for SDK initialization
     * @param cloudName the Cloudinary cloud name to target
     */
    private static void ensureInitialized(Context context, String cloudName) {
        if (initialized) {
            return;
        }
        synchronized (CloudinaryUploader.class) {
            if (initialized) {
                return;
            }
            Map<String, String> config = new HashMap<>();
            config.put("cloud_name", cloudName);
            MediaManager.init(context.getApplicationContext(), config);
            initialized = true;
        }
    }
}
