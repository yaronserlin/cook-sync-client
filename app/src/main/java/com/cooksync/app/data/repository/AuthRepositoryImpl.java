package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.remote.ApiService;
import com.cooksync.app.data.remote.RetrofitClient;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.util.SessionManager;
import com.dtos.request.auth.ChangePasswordRequestDTO;
import com.dtos.request.auth.EmailUpdateRequestDTO;
import com.dtos.request.auth.LoginRequestDTO;
import com.dtos.request.auth.ProfileUpdateRequestDTO;
import com.dtos.request.auth.RegisterRequestDTO;
import com.dtos.response.ApiResponse;
import com.dtos.response.auth.AuthResponse;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

/**
 * Concrete implementation of {@link AuthRepository} that delegates every call to the remote
 * REST API via Retrofit, executes the network work on a dedicated background thread pool,
 * and posts typed {@link ApiResult} values back to the provided {@link MutableLiveData}
 * targets on the main thread.
 *
 * <p>Session side-effects (persisting tokens, broadcasting login/logout state) are handled
 * here through {@link SessionManager} so neither the ViewModel nor the UI ever touch raw
 * token strings.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class AuthRepositoryImpl implements AuthRepository {

    /** Thread pool for network I/O — keeps the main thread unblocked at all times. */
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final ApiService apiService;

    /**
     * Constructs the repository using the shared authenticated Retrofit service.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public AuthRepositoryImpl() {
        this.apiService = RetrofitClient.getInstance();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts {@link ApiResult.Loading} immediately, then executes the login call
     * asynchronously. On HTTP 200 with {@code success=true} the session is started and
     * {@link ApiResult.Success} is posted; on any other outcome {@link ApiResult.Error}
     * is posted with a user-facing message.</p>
     */
    @Override
    public void login(LoginRequestDTO request, MutableLiveData<ApiResult<AuthResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<AuthResponse> result = executeAuthCall(apiService.login(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().startSession(((ApiResult.Success<AuthResponse>) result).getData());
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts {@link ApiResult.Loading} immediately, then executes the registration call
     * asynchronously. On success the session is started immediately (no separate login step
     * needed) and {@link ApiResult.Success} is posted.</p>
     */
    @Override
    public void register(RegisterRequestDTO request, MutableLiveData<ApiResult<AuthResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<AuthResponse> result = executeAuthCall(apiService.register(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().startSession(((ApiResult.Success<AuthResponse>) result).getData());
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls the server logout endpoint to invalidate the refresh token on the server,
     * then clears the local session regardless of the server response (so a network failure
     * does not leave the user stuck in a logged-in state).</p>
     */
    @Override
    public void logout(MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            try {
                apiService.logout().execute();
            } catch (IOException ignored) {
                // Best-effort: even if the server call fails we still clear locally.
            }
            SessionManager.getInstance().logout();
            resultTarget.postValue(new ApiResult.Success<>(null));
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateProfile(ProfileUpdateRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeVoidCall(apiService.updateProfile(request))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changePassword(ChangePasswordRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeVoidCall(apiService.changePassword(request))));
    }

    /**
     * {@inheritDoc}
     *
     * <p>On success the new session (carrying the renewed tokens for the new email identity)
     * is persisted via {@link SessionManager}.</p>
     */
    @Override
    public void updateEmail(EmailUpdateRequestDTO request, MutableLiveData<ApiResult<AuthResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<AuthResponse> result = executeAuthCall(apiService.updateEmail(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().startSession(((ApiResult.Success<AuthResponse>) result).getData());
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>On success the local session is also cleared so the user is immediately signed out.</p>
     */
    @Override
    public void deactivateAccount(MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<Void> result = executeVoidCall(apiService.deactivateAccount());
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().logout();
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Calls {@code GET /api/auth/validate-token} with the currently stored access token.
     * On success the cached session profile is refreshed; on any failure the local session
     * is cleared so the login form is shown instead of leaving stale credentials on device.</p>
     */
    @Override
    public void validateToken(MutableLiveData<ApiResult<AuthResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<AuthResponse> result = executeAuthCall(apiService.validateToken());
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().startSession(((ApiResult.Success<AuthResponse>) result).getData());
            } else {
                // Token invalid or expired: clear stale local session
                SessionManager.getInstance().forceLogout();
            }
            resultTarget.postValue(result);
        });
    }



    /**
     * Executes a Retrofit call that returns {@link ApiResponse}{@code <AuthResponse>} and
     * maps the outcome to the appropriate {@link ApiResult} subtype.
     *
     * Complexity:
     * Time: O(1) plus one synchronous network round-trip
     * Space: O(1)
     *
     * @param call the Retrofit call to execute
     * @return {@link ApiResult.Success} on HTTP 2xx with {@code success=true},
     *         {@link ApiResult.Error} on any failure
     */
    private ApiResult<AuthResponse> executeAuthCall(retrofit2.Call<ApiResponse<AuthResponse>> call) {
        try {
            Response<ApiResponse<AuthResponse>> response = call.execute();
            if (response.isSuccessful() && response.body() != null && response.body().success()) {
                return new ApiResult.Success<>(response.body().data());
            }
            String message = extractErrorMessage(response);
            return new ApiResult.Error<>(message, null);
        } catch (IOException e) {
            return new ApiResult.Error<>("Network error. Check your connection and try again.", e);
        }
    }

    /**
     * Executes a Retrofit call that returns {@link ApiResponse}{@code <Void>} and maps
     * the outcome to the appropriate {@link ApiResult} subtype.
     *
     * Complexity:
     * Time: O(1) plus one synchronous network round-trip
     * Space: O(1)
     *
     * @param call the Retrofit call to execute
     * @return {@link ApiResult.Success} on HTTP 2xx with {@code success=true},
     *         {@link ApiResult.Error} on any failure
     */
    private ApiResult<Void> executeVoidCall(retrofit2.Call<ApiResponse<Void>> call) {
        try {
            Response<ApiResponse<Void>> response = call.execute();
            if (response.isSuccessful() && response.body() != null && response.body().success()) {
                return new ApiResult.Success<>(null);
            }
            String message = extractErrorMessage(response);
            return new ApiResult.Error<>(message, null);
        } catch (IOException e) {
            return new ApiResult.Error<>("Network error. Check your connection and try again.", e);
        }
    }

    /**
     * Extracts a human-readable error message from a failed HTTP response, preferring the
     * {@code message} field in the JSON body if present.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param response the non-successful HTTP response
     * @return a user-facing error string
     */
    private String extractErrorMessage(Response<?> response) {
        if (response.body() instanceof ApiResponse<?> apiResponse) {
            String msg = apiResponse.message();
            if (msg != null && !msg.isBlank()) {
                return msg;
            }
        }
        return switch (response.code()) {
            case 400 -> "Invalid request. Please check your input.";
            case 401 -> "Invalid credentials. Please try again.";
            case 403 -> "You do not have permission to perform this action.";
            case 409 -> "An account with this email already exists.";
            case 500 -> "Server error. Please try again later.";
            default  -> "Unexpected error (" + response.code() + "). Please try again.";
        };
    }
}
