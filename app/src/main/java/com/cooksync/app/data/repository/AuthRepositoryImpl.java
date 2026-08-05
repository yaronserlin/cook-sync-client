package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.remote.ApiService;
import com.cooksync.app.data.remote.RetrofitClient;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.util.SessionManager;
import com.dtos.request.auth.AvatarUpdateRequestDTO;
import com.dtos.request.auth.ChangePasswordRequestDTO;
import com.dtos.request.auth.EmailUpdateRequestDTO;
import com.dtos.request.auth.ForgotPasswordRequestDTO;
import com.dtos.request.auth.LoginRequestDTO;
import com.dtos.request.auth.ProfileUpdateRequestDTO;
import com.dtos.request.auth.RegisterRequestDTO;
import com.dtos.request.auth.ResetPasswordRequestDTO;
import com.dtos.response.ApiResponse;
import com.dtos.response.auth.AuthResponse;

import java.io.IOException;

/**
 * Concrete implementation of {@link AuthRepository} that delegates every call to the remote
 * REST API via Retrofit, executes the network work on a dedicated background thread pool
 * (inherited from {@link BaseRepository}), and posts typed {@link ApiResult} values back to
 * the provided {@link MutableLiveData} targets on the main thread.
 *
 * <p>Session side-effects (persisting tokens, broadcasting login/logout state) are handled
 * here through {@link SessionManager} so neither the ViewModel nor the UI ever touch raw
 * token strings.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class AuthRepositoryImpl extends BaseRepository implements AuthRepository {

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
            ApiResult<AuthResponse> result = executeCall(apiService.login(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().startSession(((ApiResult.Success<AuthResponse>) result).getData());
                SessionManager.getInstance().cacheEmail(request.email());
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
            ApiResult<AuthResponse> result = executeCall(apiService.register(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().startSession(((ApiResult.Success<AuthResponse>) result).getData());
                SessionManager.getInstance().cacheEmail(request.email());
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
     *
     * <p>On success the new first/last name are also cached locally, since the server
     * response carries no body to read them back from.</p>
     */
    @Override
    public void updateProfile(ProfileUpdateRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<Void> result = executeCall(apiService.updateProfile(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().updateCachedProfile(request.firstName(), request.lastName());
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>On success the new avatar URL is also cached locally, since the server response
     * carries no body to read it back from.</p>
     */
    @Override
    public void updateAvatar(AvatarUpdateRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<Void> result = executeCall(apiService.updateAvatar(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().updateCachedAvatar(request.avatarUrl());
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changePassword(ChangePasswordRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.changePassword(request))));
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
            ApiResult<AuthResponse> result = executeCall(apiService.updateEmail(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().startSession(((ApiResult.Success<AuthResponse>) result).getData());
                SessionManager.getInstance().cacheEmail(request.newEmail());
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
            ApiResult<Void> result = executeCall(apiService.deactivateAccount());
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
     * On success the cached profile fields are refreshed; on any failure the local session
     * is cleared so the login form is shown instead of leaving stale credentials on device.
     * The response's token fields are {@code null} by design (this endpoint checks a
     * session, it doesn't issue one), so the stored access/refresh tokens are left untouched
     * here — they were already updated in place by {@link com.cooksync.app.data.remote.TokenAuthenticator}
     * if a transparent refresh happened during this call.</p>
     */
    @Override
    public void validateToken(MutableLiveData<ApiResult<AuthResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<AuthResponse> result = executeCall(apiService.validateToken());
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().refreshCachedProfile(((ApiResult.Success<AuthResponse>) result).getData());
            } else {
                // Token invalid or expired: clear stale local session
                SessionManager.getInstance().forceLogout();
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forgotPassword(ForgotPasswordRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.forgotPassword(request))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetPassword(ResetPasswordRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.resetPassword(request))));
    }

}
