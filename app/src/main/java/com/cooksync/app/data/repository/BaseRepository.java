package com.cooksync.app.data.repository;

import com.cooksync.app.CookSyncApplication;
import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.dtos.response.ApiResponse;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
