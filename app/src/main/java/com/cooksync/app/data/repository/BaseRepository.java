package com.cooksync.app.data.repository;

import com.cooksync.app.CookSyncApplication;
import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.dtos.response.ApiResponse;
import com.dtos.response.PagedResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

import androidx.lifecycle.MutableLiveData;

import retrofit2.Call;
import retrofit2.Response;

/**
 * Common execution machinery shared by every {@code *RepositoryImpl} class: a background
 * thread pool for network I/O, generic Retrofit call execution, and uniform error-message
 * extraction. Every repository extends this instead of re-implementing the same
 * try/execute/map-to-{@link ApiResult} boilerplate, so a fix or improvement here (e.g. richer
 * error parsing) applies to every feature area at once.
 *
 * <p>All user-facing error text is resolved from {@code strings.xml} via
 * {@link CookSyncApplication#getAppContext()} rather than hardcoded, keeping the app
 * localisable and consistent with the rest of the UI layer.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public abstract class BaseRepository {

    /** Shared thread pool for network I/O — keeps the main thread unblocked at all times. */
    protected static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    /** Matches {@code OrganicToast}'s auto-dismiss duration. Shared across ViewModels for undo timing. */
    public static final long UNDO_WINDOW_MS = 3200;

    /**
     * Page size used by {@link #fetchAllPages}. Every server-paginated endpoint the app
     * consumes as a complete, non-scrolling client-side collection (favorites, "my recipes",
     * per-recipe notes, the tag catalog) loops in chunks of this size rather than issuing one
     * unbounded request, while still assembling the full collection those screens' existing
     * client-side search/filter/count logic depends on.
     */
    protected static final int LOOP_FETCH_PAGE_SIZE = 50;

    /**
     * Executes a Retrofit call wrapping any payload type in {@link ApiResponse} and maps the
     * outcome to the appropriate {@link ApiResult} subtype. Works uniformly for {@code Void}
     * payloads (e.g. {@code ApiResponse<Void>}) as well as populated ones, since
     * {@code response.body().data()} is simply {@code null} in the former case.
     *
     * Complexity:
     * Time: O(1) plus one synchronous network round-trip
     * Space: O(1)
     *
     * @param <T> the type of the successful payload
     * @param call the Retrofit call to execute
     * @return {@link ApiResult.Success} on HTTP 2xx with {@code success=true},
     *         {@link ApiResult.Error} on any other outcome
     */
    protected <T> ApiResult<T> executeCall(Call<ApiResponse<T>> call) {
        try {
            Response<ApiResponse<T>> response = call.execute();
            if (response.isSuccessful() && response.body() != null && response.body().success()) {
                return new ApiResult.Success<>(response.body().data());
            }
            return new ApiResult.Error<>(extractErrorMessage(response), null);
        } catch (IOException e) {
            return new ApiResult.Error<>(CookSyncApplication.getAppContext().getString(R.string.error_network), e);
        }
    }

    /**
     * Executes a Retrofit call on a background thread and posts the result to the given LiveData.
     *
     * @param <T> the type of the successful payload
     * @param call the Retrofit call to execute
     * @param resultTarget the LiveData to post the result to
     */
    protected <T> void executeAsync(Call<ApiResponse<T>> call, MutableLiveData<ApiResult<T>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(call)));
    }

    /**
     * Repeatedly calls a paginated endpoint on a background thread and posts the concatenated
     * result to the given LiveData.
     *
     * @param <T> the type of items contained within each page
     * @param callFactory produces the Retrofit call for a given (page, size) pair
     * @param resultTarget the LiveData to post the result to
     */
    protected <T> void fetchAsync(BiFunction<Integer, Integer, Call<ApiResponse<PagedResponse<T>>>> callFactory,
                                   MutableLiveData<ApiResult<List<T>>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(fetchAllPages(callFactory)));
    }

    /**
     * Repeatedly calls a paginated endpoint, starting at page 0 and stopping once the server
     * reports the last page, concatenating every page's content into one list. Used for
     * endpoints whose result the client treats as a single complete, non-scrolling collection
     * (e.g. favorites, "my recipes", per-recipe notes, the tag catalog) so their existing
     * client-side search/filter/count logic keeps seeing the whole set, while each individual
     * HTTP request still stays bounded to {@link #LOOP_FETCH_PAGE_SIZE} rather than being
     * unbounded.
     *
     * Complexity:
     * Time: O(P) network round-trips, where P is the number of pages the collection spans
     * Space: O(N) where N is the total item count across all pages
     *
     * @param <T> the type of items contained within each page
     * @param callFactory produces the Retrofit call for a given (page, size) pair
     * @return {@link ApiResult.Success} wrapping the concatenated content of every page, or the
     *         first {@link ApiResult.Error} encountered
     */
    protected <T> ApiResult<List<T>> fetchAllPages(
            BiFunction<Integer, Integer, Call<ApiResponse<PagedResponse<T>>>> callFactory) {
        List<T> all = new ArrayList<>();
        int page = 0;
        while (true) {
            ApiResult<PagedResponse<T>> pageResult = executeCall(callFactory.apply(page, LOOP_FETCH_PAGE_SIZE));
            if (pageResult instanceof ApiResult.Error<PagedResponse<T>> error) {
                return new ApiResult.Error<>(error.getMessage(), error.getCause());
            }
            PagedResponse<T> paged = ((ApiResult.Success<PagedResponse<T>>) pageResult).getData();
            all.addAll(paged.content());
            if (paged.last()) {
                break;
            }
            page++;
        }
        return new ApiResult.Success<>(all);
    }

    /**
     * Extracts a human-readable error message from a failed HTTP response, preferring the
     * {@code message} field in the JSON body if present, falling back to a status-code-specific
     * default otherwise.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param response the non-successful HTTP response
     * @return a user-facing error string
     */
    protected String extractErrorMessage(Response<?> response) {
        if (response.body() instanceof ApiResponse<?> apiResponse) {
            String msg = apiResponse.message();
            if (msg != null && !msg.isBlank()) {
                return msg;
            }
        }
        return switch (response.code()) {
            case 400 -> CookSyncApplication.getAppContext().getString(R.string.error_invalid_request);
            case 401 -> CookSyncApplication.getAppContext().getString(R.string.error_invalid_credentials);
            case 403 -> CookSyncApplication.getAppContext().getString(R.string.error_no_permission);
            case 404 -> CookSyncApplication.getAppContext().getString(R.string.error_not_found);
            case 409 -> CookSyncApplication.getAppContext().getString(R.string.error_account_exists);
            case 500 -> CookSyncApplication.getAppContext().getString(R.string.error_server);
            default -> CookSyncApplication.getAppContext().getString(R.string.error_unexpected, response.code());
        };
    }
}
