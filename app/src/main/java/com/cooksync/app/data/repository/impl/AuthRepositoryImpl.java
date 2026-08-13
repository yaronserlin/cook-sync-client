package com.cooksync.app.data.repository.impl;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.datasource.local.TokenStore;
import com.cooksync.app.data.datasource.remote.ApiService;
import com.cooksync.app.data.datasource.remote.RetrofitClient;
import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.BaseRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.util.SessionManager;
import com.dtos.request.auth.AvatarUpdateRequestDTO;
import com.dtos.request.auth.ChangePasswordRequestDTO;
import com.dtos.request.auth.DeleteAccountRequestDTO;
import com.dtos.request.auth.EmailUpdateRequestDTO;
import com.dtos.request.auth.ForgotPasswordRequestDTO;
import com.dtos.request.auth.LoginRequestDTO;
import com.dtos.request.auth.PrivacySettingsUpdateRequestDTO;
import com.dtos.request.auth.ProfileUpdateRequestDTO;
import com.dtos.request.auth.RegisterRequestDTO;
import com.dtos.request.auth.ResendRegistrationOtpRequestDTO;
import com.dtos.request.auth.ResetPasswordRequestDTO;
import com.dtos.request.auth.TokenRefreshRequestDTO;
import com.dtos.request.auth.VerifyRegistrationOtpRequestDTO;
import com.dtos.response.ApiResponse;
import com.dtos.response.auth.AuthResponse;
import com.dtos.response.auth.PendingRegistrationResponse;
import com.dtos.response.user.UserResponse;

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
     * asynchronously. No session is started here — the server only emails an OTP code at this
     * stage; the session starts once {@link #verifyRegistrationOtp} succeeds.</p>
     */
    @Override
    public void register(RegisterRequestDTO request, MutableLiveData<ApiResult<PendingRegistrationResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.register(request))));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts {@link ApiResult.Loading} immediately, then executes the OTP verification call
     * asynchronously. On success the session is started immediately, exactly as registration
     * itself used to do before the OTP step existed.</p>
     */
    @Override
    public void verifyRegistrationOtp(VerifyRegistrationOtpRequestDTO request, MutableLiveData<ApiResult<AuthResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<AuthResponse> result = executeCall(apiService.verifyRegistrationOtp(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().startSession(((ApiResult.Success<AuthResponse>) result).getData());
                SessionManager.getInstance().cacheEmail(request.email());
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resendRegistrationOtp(ResendRegistrationOtpRequestDTO request, MutableLiveData<ApiResult<PendingRegistrationResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.resendRegistrationOtp(request))));
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
            } catch (IOException e) {
                android.util.Log.w("AuthRepositoryImpl", "Server logout request failed", e);
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
     */
    @Override
    public void getCurrentUserProfile(MutableLiveData<ApiResult<UserResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getCurrentUser())));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void getUserProfile(String userId, MutableLiveData<ApiResult<UserResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.getUserProfile(userId))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePrivacySettings(PrivacySettingsUpdateRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> resultTarget.postValue(executeCall(apiService.updatePrivacySettings(request))));
    }

    /**
     * {@inheritDoc}
     *
     * <p>On success the local session is also cleared, matching {@link #deactivateAccount}: the
     * account is disabled server-side for the duration of the 30-day grace period, so the user
     * is signed out immediately and must log back in to cancel the deletion.</p>
     */
    @Override
    public void requestAccountDeletion(DeleteAccountRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            ApiResult<Void> result = executeCall(apiService.requestAccountDeletion(request));
            if (result instanceof ApiResult.Success) {
                SessionManager.getInstance().logout();
            }
            resultTarget.postValue(result);
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Executes a silent validation/refresh flow intended for app startup:</p>
     * <ol>
     *   <li>Calls {@code GET /api/auth/validate-token} with the current access token.</li>
     *   <li>If the access token is expired (HTTP 401), {@link com.cooksync.app.data.datasource.remote.TokenAuthenticator}
     *       transparently attempts a refresh. If that succeeds, the call is retried and we
     *       receive a {@link ApiResult.Success} here.</li>
     *   <li>If for any reason (e.g. network failure or authenticator failure) validation
     *       is still failing, this method manually attempts a refresh using the stored
     *       refresh token via the bare (unauthenticated) API service to guarantee a clean
     *       state.</li>
     *   <li>On any ultimate success, the cached profile is updated and the user identity
     *       is posted. On ultimate failure, the local session is cleared.</li>
     * </ol>
     */
    @Override
    public void validateToken(MutableLiveData<ApiResult<AuthResponse>> resultTarget) {
        resultTarget.postValue(new ApiResult.Loading<>());
        EXECUTOR.execute(() -> {
            // ── Phase 1: Try validation (transparently leverages TokenAuthenticator) ──
            ApiResult<AuthResponse> validateResult = executeCall(apiService.validateToken());

            if (validateResult instanceof ApiResult.Success) {
                SessionManager.getInstance().refreshCachedProfile(((ApiResult.Success<AuthResponse>) validateResult).getData());
                resultTarget.postValue(validateResult);
                return;
            }

            ApiResult<AuthResponse> terminalResult;

            // ── Phase 2: Explicitly try refresh if validation failed ────────────────
            String refreshToken = TokenStore.getRefreshToken();
            if (refreshToken != null && !refreshToken.isEmpty()) {
                // Use the BARE service to avoid recursive authenticator loops.
                ApiResult<AuthResponse> refreshResult = executeCall(
                        RetrofitClient.getBareService().refreshToken(new TokenRefreshRequestDTO(refreshToken))
                );

                if (refreshResult instanceof ApiResult.Success) {
                    AuthResponse renewed = ((ApiResult.Success<AuthResponse>) refreshResult).getData();
                    SessionManager.getInstance().startSession(renewed);
                    // The refresh response carries the full user profile, so this is a valid terminal state.
                    resultTarget.postValue(refreshResult);
                    return;
                }
                terminalResult = refreshResult;
            } else {
                terminalResult = validateResult;
            }

            // ── Phase 3: Total failure (expired refresh token or no session) ────────
            SessionManager.getInstance().forceLogout();
            resultTarget.postValue(terminalResult);
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
