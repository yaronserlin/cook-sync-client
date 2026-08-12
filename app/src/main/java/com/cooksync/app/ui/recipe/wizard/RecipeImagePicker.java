package com.cooksync.app.ui.recipe.wizard;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.data.model.recipe.RecipeDraft;
import com.cooksync.app.data.model.recipe.RecipeDraftMapper;
import com.cooksync.app.data.model.recipe.RecipeDraftValidator;
import com.cooksync.app.data.model.recipe.RecipeDraftMediaHelper;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin wrapper around a single system photo-picker {@link ActivityResultLauncher}, reused by
 * every image pick in the recipe creation wizard (cover photo, description photos, instruction
 * step photos). Must be constructed in a fragment's {@code onCreate} — before the fragment
 * reaches {@code STARTED} — per {@link Fragment#registerForActivityResult}'s contract.
 *
 * <p>The system picker's {@code content://} URI only grants this app read access for a short,
 * picker-defined window — not persistable via {@code takePersistableUriPermission} for modern
 * Photo Picker URIs. Since the wizard defers every Cloudinary upload until Publish (potentially
 * much later, after the user has filled in every other step), reading that original URI at
 * upload time reliably throws {@link SecurityException}. To avoid depending on that grant at
 * all, the picked file's bytes are copied into this app's private cache the moment it's picked,
 * and callers only ever see a {@code file://} URI this app owns outright.</p>
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 08/08/2026
 */
public final class RecipeImagePicker {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    /** Notified with the picked image's local URI, once it's been copied into this app's own cache. */
    public interface Listener {
        void onImagePicked(@NonNull Uri uri);
    }

    private final Fragment fragment;
    private final ActivityResultLauncher<String> launcher;
    private Listener listener;

    /**
     * Registers the underlying picker launcher against {@code fragment}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param fragment the hosting fragment, mid-{@code onCreate}
     */
    public RecipeImagePicker(@NonNull Fragment fragment) {
        this.fragment = fragment;
        launcher = fragment.registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && listener != null) {
                copyToPrivateCacheThenNotify(uri);
            }
        });
    }

    /**
     * Launches the system image picker, invoking {@code listener} once the picked image has been
     * copied into this app's private cache.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param listener invoked with the copied image's {@code file://} URI; not called if the
     *                  user cancels the pick, or if the copy fails
     */
    public void pick(@NonNull Listener listener) {
        this.listener = listener;
        launcher.launch("image/*");
    }

    private void copyToPrivateCacheThenNotify(Uri pickedUri) {
        Listener target = listener;
        Context appContext = fragment.requireContext().getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        EXECUTOR.execute(() -> {
            Uri localUri = copyToCacheFile(appContext, pickedUri);
            if (localUri != null) {
                mainHandler.post(() -> target.onImagePicked(localUri));
            }
        });
    }

    private static Uri copyToCacheFile(Context context, Uri sourceUri) {
        File outFile = new File(context.getCacheDir(), "wizard_pick_" + UUID.randomUUID() + ".jpg");
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(outFile)) {
            if (in == null) return null;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return Uri.fromFile(outFile);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Deletes every cache copy this picker has made ({@code wizard_pick_*.jpg}), once none of
     * them are needed anymore — the wizard's single in-flight draft either finished publishing
     * (bytes already sent to Cloudinary) or was discarded, so no local {@code file://} URI it
     * handed out is still referenced by anything.
     *
     * Complexity:
     * Time: O(n) where n is the number of files in the app's cache directory
     * Space: O(1)
     *
     * @param context any context; only {@link Context#getCacheDir()} is used
     */
    public static void clearCache(@NonNull Context context) {
        EXECUTOR.execute(() -> {
            File[] files = context.getCacheDir().listFiles((dir, name) -> name.startsWith("wizard_pick_"));
            if (files == null) return;
            for (File file : files) {
                file.delete();
            }
        });
    }
}
