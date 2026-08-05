package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.domain.ApiResult;
import com.dtos.request.auth.AvatarUpdateRequestDTO;
import com.dtos.request.auth.ChangePasswordRequestDTO;
import com.dtos.request.auth.EmailUpdateRequestDTO;
import com.dtos.request.auth.ForgotPasswordRequestDTO;
import com.dtos.request.auth.LoginRequestDTO;
import com.dtos.request.auth.ProfileUpdateRequestDTO;
import com.dtos.request.auth.RegisterRequestDTO;
import com.dtos.request.auth.ResetPasswordRequestDTO;
import com.dtos.response.auth.AuthResponse;

/**
 * Declares the contract for all authentication-related data operations available to
 * ViewModels. The interface deliberately accepts {@link MutableLiveData} targets rather
 * than returning them, so the ViewModel controls the observable lifecycle while the
 * repository simply posts results to it — this keeps the boundary clean and avoids leaking
 * framework objects into the repository implementation.
 *
 * <p>Every method posts an {@link ApiResult.Loading} immediately, then either
 * {@link ApiResult.Success} or {@link ApiResult.Error} once the operation resolves,
 * all from a background thread so the main UI thread is never blocked.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public interface AuthRepository {

    /**
     * Authenticates the user with email and password. Persists the resulting session via
     * {@link com.cooksync.app.util.SessionManager} on success.
     *
     * @param request     login credentials
     * @param resultTarget live data target the result will be posted to
     */
    void login(LoginRequestDTO request, MutableLiveData<ApiResult<AuthResponse>> resultTarget);

    /**
     * Registers a new user account and starts a session immediately on success.
     *
     * @param request     registration payload
     * @param resultTarget live data target the result will be posted to
     */
    void register(RegisterRequestDTO request, MutableLiveData<ApiResult<AuthResponse>> resultTarget);

    /**
     * Logs the current user out, invalidating the server-side refresh token and clearing
     * the local session.
     *
     * @param resultTarget live data target the result will be posted to
     */
    void logout(MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Updates the authenticated user's display name fields.
     *
     * @param request     profile update payload
     * @param resultTarget live data target the result will be posted to
     */
    void updateProfile(ProfileUpdateRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Updates the authenticated user's avatar URL, after it has already been uploaded to
     * Cloudinary by the caller.
     *
     * @param request     avatar update payload
     * @param resultTarget live data target the result will be posted to
     */
    void updateAvatar(AvatarUpdateRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Changes the authenticated user's password.
     *
     * @param request     password change payload
     * @param resultTarget live data target the result will be posted to
     */
    void changePassword(ChangePasswordRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Changes the authenticated user's email address, re-issuing a session under the new
     * identity.
     *
     * @param request     email update payload
     * @param resultTarget live data target the result will be posted to
     */
    void updateEmail(EmailUpdateRequestDTO request, MutableLiveData<ApiResult<AuthResponse>> resultTarget);

    /**
     * Deactivates the authenticated user's account.
     *
     * @param resultTarget live data target the result will be posted to
     */
    void deactivateAccount(MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Validates the stored access token against the server. Used on app startup to
     * silently re-authenticate the user when a previous session exists, avoiding the
     * need to show the login form again.
     *
     * @param resultTarget live data target the result will be posted to
     */
    void validateToken(MutableLiveData<ApiResult<AuthResponse>> resultTarget);

    /**
     * Requests a password-reset email for the given account, if one exists. Always succeeds
     * from the caller's perspective regardless of whether the email is registered.
     *
     * @param request     forgot-password payload
     * @param resultTarget live data target the result will be posted to
     */
    void forgotPassword(ForgotPasswordRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Completes a password reset using a token issued via {@link #forgotPassword}.
     *
     * @param request     reset-password payload
     * @param resultTarget live data target the result will be posted to
     */
    void resetPassword(ResetPasswordRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget);
}
