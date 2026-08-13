package com.cooksync.app.data.repository;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.domain.ApiResult;
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
import com.dtos.request.auth.VerifyRegistrationOtpRequestDTO;
import com.dtos.response.auth.AuthResponse;
import com.dtos.response.auth.PendingRegistrationResponse;
import com.dtos.response.user.UserResponse;

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
     * Initiates registration for a new account. No session is started yet — a one-time
     * verification code is emailed to the given address, and the registration is only
     * completed by calling {@link #verifyRegistrationOtp}.
     *
     * @param request     registration payload
     * @param resultTarget live data target the result will be posted to
     */
    void register(RegisterRequestDTO request, MutableLiveData<ApiResult<PendingRegistrationResponse>> resultTarget);

    /**
     * Completes registration by submitting the OTP code emailed for a pending registration.
     * Starts a session immediately on success.
     *
     * @param request     OTP verification payload
     * @param resultTarget live data target the result will be posted to
     */
    void verifyRegistrationOtp(VerifyRegistrationOtpRequestDTO request, MutableLiveData<ApiResult<AuthResponse>> resultTarget);

    /**
     * Regenerates and re-emails a fresh OTP code for an existing pending registration.
     *
     * @param request     resend request payload
     * @param resultTarget live data target the result will be posted to
     */
    void resendRegistrationOtp(ResendRegistrationOtpRequestDTO request, MutableLiveData<ApiResult<PendingRegistrationResponse>> resultTarget);

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
     * Updates the authenticated user's public-profile privacy preferences.
     *
     * @param request     privacy settings update payload
     * @param resultTarget live data target the result will be posted to
     */
    void updatePrivacySettings(PrivacySettingsUpdateRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Starts the 30-day self-service account-deletion grace period for the authenticated user.
     *
     * @param request     delete-account payload carrying the current password for verification
     * @param resultTarget live data target the result will be posted to
     */
    void requestAccountDeletion(DeleteAccountRequestDTO request, MutableLiveData<ApiResult<Void>> resultTarget);

    /**
     * Validates the stored access token against the server. Used on app startup to
     * silently re-authenticate the user when a previous session exists, avoiding the
     * need to show the login form again.
     *
     * @param resultTarget live data target the result will be posted to
     */
    void validateToken(MutableLiveData<ApiResult<AuthResponse>> resultTarget);

    /**
     * Fetches the authenticated user's full profile, including fields not carried by
     * {@link AuthResponse} (city, bio, privacy preferences). Used to pre-fill the Account
     * Details screen.
     *
     * @param resultTarget live data target the result will be posted to
     */
    void getCurrentUserProfile(MutableLiveData<ApiResult<UserResponse>> resultTarget);

    /**
     * Fetches a specific user's public profile by ID.
     *
     * @param userId       target user ID
     * @param resultTarget live data target the result will be posted to
     */
    void getUserProfile(String userId, MutableLiveData<ApiResult<UserResponse>> resultTarget);

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
