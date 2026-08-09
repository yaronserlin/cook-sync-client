package com.cooksync.app.data.publish;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.CookSyncApplication;
import com.cooksync.app.data.local.RecipeDraftStore;
import com.cooksync.app.data.repository.MediaRepository;
import com.cooksync.app.data.repository.MediaRepositoryImpl;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.data.repository.RecipeRepositoryImpl;
import com.cooksync.app.data.repository.TagRepository;
import com.cooksync.app.data.repository.TagRepositoryImpl;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.recipe.wizard.AddRecipeViewModel;
import com.cooksync.app.ui.recipe.wizard.RecipeDraft;
import com.cooksync.app.ui.recipe.wizard.RecipeDraftMapper;
import com.cooksync.app.util.CloudinaryUploader;
import com.dtos.request.recipe.RecipeCreateRequestDTO;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.recipe.RecipeResponse;
import com.dtos.response.tags.TagResponse;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-wide singleton managing background recipe media uploads and publishing.
 * Allows the user to tap "Publish", immediately navigate to the "My Recipes" screen,
 * and view live background progress while Cloudinary images and the backend payload are posted.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 09/08/2026
 */
public class RecipePublishManager {

    private static RecipePublishManager instance;

    /** Current state of a background publishing job. */
    public static class PublishState {
        public enum Status { IDLE, UPLOADING, PUBLISHING, SUCCESS, ERROR }

        public final Status status;
        public final int progress;
        public final String message;
        public final RecipeResponse recipe;
        public final String error;

        public PublishState(Status status, int progress, String message, RecipeResponse recipe, String error) {
            this.status = status;
            this.progress = progress;
            this.message = message;
            this.recipe = recipe;
            this.error = error;
        }

        public static PublishState idle() {
            return new PublishState(Status.IDLE, 0, null, null, null);
        }

        public static PublishState uploading(int progress, String message) {
            return new PublishState(Status.UPLOADING, progress, message, null, null);
        }

        public static PublishState publishing(String message) {
            return new PublishState(Status.PUBLISHING, 90, message, null, null);
        }

        public static PublishState success(RecipeResponse recipe) {
            return new PublishState(Status.SUCCESS, 100, "Published successfully", recipe, null);
        }

        public static PublishState error(String error) {
            return new PublishState(Status.ERROR, 0, null, null, error);
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final MediaRepository mediaRepository = new MediaRepositoryImpl();
    private final TagRepository tagRepository = new TagRepositoryImpl();
    private final RecipeRepository recipeRepository = new RecipeRepositoryImpl();

    private final MutableLiveData<PublishState> publishState = new MutableLiveData<>(PublishState.idle());
    private final MutableLiveData<Event<RecipeResponse>> recipePublishedEvent = new MutableLiveData<>();

    private RecipePublishManager() {
        // Singleton
    }

    /** @return process-wide singleton instance */
    public static synchronized RecipePublishManager getInstance() {
        if (instance == null) {
            instance = new RecipePublishManager();
        }
        return instance;
    }

    /** @return LiveData exposing the current publishing state and progress */
    public LiveData<PublishState> getPublishState() {
        return publishState;
    }

    /** @return LiveData event emitted whenever a recipe is published successfully */
    public LiveData<Event<RecipeResponse>> getRecipePublishedEvent() {
        return recipePublishedEvent;
    }

    /**
     * Resets the publish state back to IDLE (e.g. after hiding the progress card).
     */
    @MainThread
    public void resetState() {
        publishState.setValue(PublishState.idle());
    }

    /**
     * Starts background publishing for the provided draft, updating progress on {@link #getPublishState()}.
     *
     * Complexity:
     * Time: O(n) where n is pending images + tags + network upload time
     * Space: O(1)
     *
     * @param draft the recipe draft to upload and publish
     */
    public void startPublish(RecipeDraft draft) {
        if (publishState.getValue() != null &&
                (publishState.getValue().status == PublishState.Status.UPLOADING ||
                 publishState.getValue().status == PublishState.Status.PUBLISHING)) {
            return;
        }

        publishState.postValue(PublishState.uploading(5, "Preparing media..."));

        executor.execute(() -> {
            try {
                // 1. Upload pending media to Cloudinary
                List<com.cooksync.app.ui.recipe.wizard.RecipeDraftMediaHelper.PendingImageUpload> pending =
                        com.cooksync.app.ui.recipe.wizard.RecipeDraftMediaHelper.collectPendingImageUploads(draft);
                int totalImages = pending.size();

                for (int i = 0; i < totalImages; i++) {
                    com.cooksync.app.ui.recipe.wizard.RecipeDraftMediaHelper.PendingImageUpload item = pending.get(i);
                    int itemNum = i + 1;
                    int percent = (int) (((double) i / totalImages) * 70) + 5;
                    publishState.postValue(PublishState.uploading(percent,
                            "Uploading image " + itemNum + " of " + totalImages + "..."));

                    // Fetch signature synchronously
                    CloudinarySignatureResponse sig = fetchSignatureSync();
                    if (sig == null) {
                        publishState.postValue(PublishState.error("Failed to acquire upload signature"));
                        return;
                    }

                    // Perform Cloudinary upload
                    String uploadedUrl = uploadImageSync(item.getLocalUri(), sig);
                    if (uploadedUrl == null) {
                        publishState.postValue(PublishState.error("Failed to upload image " + itemNum));
                        return;
                    }

                    com.cooksync.app.ui.recipe.wizard.RecipeDraftMediaHelper.resolvePendingImageUpload(draft, item, uploadedUrl);
                }

                // 2. Create custom tags if any
                publishState.postValue(PublishState.publishing("Processing recipe tags..."));
                for (String tag : draft.pendingNewTagNames) {
                    TagResponse createdTag = createTagSync(tag);
                    if (createdTag != null) {
                        draft.tags.add(createdTag);
                    }
                }

                // 3. Post recipe creation DTO to server
                publishState.postValue(PublishState.publishing("Publishing recipe..."));
                RecipeCreateRequestDTO dto = com.cooksync.app.ui.recipe.wizard.RecipeDraftMapper.toDto(draft);
                RecipeResponse response = createRecipeSync(dto);

                if (response != null) {
                    RecipeDraftStore.clear();
                    publishState.postValue(PublishState.success(response));
                    recipePublishedEvent.postValue(new Event<>(response));
                } else {
                    publishState.postValue(PublishState.error("Failed to publish recipe to server"));
                }

            } catch (Exception e) {
                publishState.postValue(PublishState.error("Error publishing recipe: " + e.getMessage()));
            }
        });
    }

    private String uploadImageSync(String localUri, CloudinarySignatureResponse signature) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        mainHandler.post(() -> CloudinaryUploader.upload(
                CookSyncApplication.getAppContext(),
                android.net.Uri.parse(localUri),
                signature,
                new CloudinaryUploader.Callback() {
                    @Override
                    public void onSuccess(String secureUrl) {
                        result.set(secureUrl);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        latch.countDown();
                    }
                }
        ));
        latch.await();
        return result.get();
    }

    private CloudinarySignatureResponse fetchSignatureSync() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CloudinarySignatureResponse> result = new AtomicReference<>();
        MutableLiveData<ApiResult<CloudinarySignatureResponse>> target = new MutableLiveData<>();

        mainHandler.post(() -> target.observeForever(res -> {
            if (res instanceof ApiResult.Success<CloudinarySignatureResponse> s) {
                result.set(s.getData());
                latch.countDown();
            } else if (res instanceof ApiResult.Error) {
                latch.countDown();
            }
        }));

        mediaRepository.getUploadSignature(target);
        latch.await();
        return result.get();
    }

    private TagResponse createTagSync(String tag) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TagResponse> result = new AtomicReference<>();
        MutableLiveData<ApiResult<TagResponse>> target = new MutableLiveData<>();

        mainHandler.post(() -> target.observeForever(res -> {
            if (res instanceof ApiResult.Success<TagResponse> s) {
                result.set(s.getData());
                latch.countDown();
            } else if (res instanceof ApiResult.Error) {
                latch.countDown();
            }
        }));

        tagRepository.createTag(tag, target);
        latch.await();
        return result.get();
    }

    private RecipeResponse createRecipeSync(RecipeCreateRequestDTO dto) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RecipeResponse> result = new AtomicReference<>();
        MutableLiveData<ApiResult<RecipeResponse>> target = new MutableLiveData<>();

        mainHandler.post(() -> target.observeForever(res -> {
            if (res instanceof ApiResult.Success<RecipeResponse> s) {
                result.set(s.getData());
                latch.countDown();
            } else if (res instanceof ApiResult.Error) {
                latch.countDown();
            }
        }));

        recipeRepository.createRecipe(dto, target);
        latch.await();
        return result.get();
    }
}
