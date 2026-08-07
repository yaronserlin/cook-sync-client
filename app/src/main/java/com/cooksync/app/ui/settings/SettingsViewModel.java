package com.cooksync.app.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.MediaRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.common.BaseViewModel;
import com.cooksync.app.util.InputValidator;
import com.dtos.request.auth.AvatarUpdateRequestDTO;
import com.dtos.request.auth.ChangePasswordRequestDTO;
import com.dtos.request.auth.EmailUpdateRequestDTO;
import com.dtos.request.auth.ProfileUpdateRequestDTO;
import com.dtos.response.auth.AuthResponse;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.recipe.RecipePreviewResponse;

import java.util.List;

/**
 * ViewModel for {@link SettingsActivity}. Validates every field client-side (mirroring the
 * server's Jakarta constraints, exactly like {@link com.cooksync.app.ui.auth.LoginViewModel}
 * and {@link com.cooksync.app.ui.auth.RegisterViewModel}) before delegating to
 * {@link AuthRepository}, fetches Cloudinary upload signatures via {@link MediaRepository} for
 * avatar changes, and fetches the Favorites/My recipes counts shown as row subtitles via
 * {@link RecipeRepository}.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class SettingsViewModel extends BaseViewModel {

    private final AuthRepository authRepository;
    private final MediaRepository mediaRepository;
    private final RecipeRepository recipeRepository;

    private final MutableLiveData<ApiResult<Void>> profileResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> avatarResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> passwordResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<AuthResponse>> emailResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> deactivateResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> logoutResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<CloudinarySignatureResponse>> signatureResult = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> validationError = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> favoritesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> myRecipesResult = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.common.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param authRepository the repository used for profile/password/email/account calls
     * @param mediaRepository the repository used for Cloudinary upload-signature requests
     * @param recipeRepository the repository used to fetch the Favorites/My recipes counts
     */
    public SettingsViewModel(AuthRepository authRepository, MediaRepository mediaRepository,
                              RecipeRepository recipeRepository) {
        this.authRepository = authRepository;
        this.mediaRepository = mediaRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * Validates and submits an updated first/last name.
     *
     * Complexity:
     * Time: O(n) where n is the combined length of both names
     * Space: O(1)
     *
     * @param rawFirstName raw text from the first-name field
     * @param rawLastName  raw text from the last-name field
     */
    public void updateProfile(String rawFirstName, String rawLastName) {
        InputValidator.ValidationResult firstRes = InputValidator.validateName(rawFirstName, "First name");
        if (!firstRes.isValid) {
            validationError.setValue(new Event<>(firstRes.errorMessage));
            return;
        }
        InputValidator.ValidationResult lastRes = InputValidator.validateName(rawLastName, "Last name");
        if (!lastRes.isValid) {
            validationError.setValue(new Event<>(lastRes.errorMessage));
            return;
        }
        authRepository.updateProfile(new ProfileUpdateRequestDTO(rawFirstName.trim(), rawLastName.trim()), profileResult);
    }

    /**
     * Requests a fresh Cloudinary upload signature, used just before uploading a newly
     * picked avatar image.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void requestUploadSignature() {
        mediaRepository.getUploadSignature(signatureResult);
    }

    /**
     * Persists a newly uploaded avatar's URL against the user's account. Called after the
     * image itself has already been uploaded to Cloudinary directly by the view layer.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param avatarUrl the secure URL Cloudinary returned for the uploaded image
     */
    public void updateAvatar(String avatarUrl) {
        authRepository.updateAvatar(new AvatarUpdateRequestDTO(avatarUrl), avatarResult);
    }

    /**
     * Validates and submits a password change.
     *
     * Complexity:
     * Time: O(n) where n is the combined length of both passwords
     * Space: O(1)
     *
     * @param rawCurrentPassword raw text from the current-password field
     * @param rawNewPassword     raw text from the new-password field
     */
    public void changePassword(String rawCurrentPassword, String rawNewPassword) {
        InputValidator.ValidationResult currentRes = InputValidator.validateLoginPassword(rawCurrentPassword);
        if (!currentRes.isValid) {
            validationError.setValue(new Event<>(currentRes.errorMessage));
            return;
        }
        InputValidator.ValidationResult newRes = InputValidator.validateNewPassword(rawNewPassword);
        if (!newRes.isValid) {
            validationError.setValue(new Event<>(newRes.errorMessage));
            return;
        }
        authRepository.changePassword(new ChangePasswordRequestDTO(rawCurrentPassword, rawNewPassword), passwordResult);
    }

    /**
     * Validates and submits an email change, re-authenticated with the current password.
     *
     * Complexity:
     * Time: O(n) where n is the combined length of the email and password
     * Space: O(1)
     *
     * @param rawNewEmail        raw text from the new-email field
     * @param rawCurrentPassword raw text from the current-password field
     */
    public void updateEmail(String rawNewEmail, String rawCurrentPassword) {
        InputValidator.ValidationResult emailRes = InputValidator.validateEmail(rawNewEmail);
        if (!emailRes.isValid) {
            validationError.setValue(new Event<>(emailRes.errorMessage));
            return;
        }
        InputValidator.ValidationResult passwordRes = InputValidator.validateLoginPassword(rawCurrentPassword);
        if (!passwordRes.isValid) {
            validationError.setValue(new Event<>(passwordRes.errorMessage));
            return;
        }
        authRepository.updateEmail(new EmailUpdateRequestDTO(rawNewEmail.trim(), rawCurrentPassword), emailResult);
    }

    /**
     * Deactivates the current account.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void deactivateAccount() {
        authRepository.deactivateAccount(deactivateResult);
    }

    /**
     * Logs the current user out.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void logout() {
        authRepository.logout(logoutResult);
    }

    /**
     * Fetches the current user's favorite recipes, used to derive the count shown as the
     * "Favorites" row's subtitle.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void loadFavoritesCount() {
        recipeRepository.getFavorites(favoritesResult);
    }

    /**
     * Fetches the current user's own recipes, used to derive the count shown as the
     * "My recipes" row's subtitle.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void loadMyRecipesCount() {
        recipeRepository.getMyRecipes(myRecipesResult);
    }

    /** @return observable result of a name update */
    public LiveData<ApiResult<Void>> getProfileResult() { return profileResult; }
    /** @return observable result of an avatar URL update */
    public LiveData<ApiResult<Void>> getAvatarResult() { return avatarResult; }
    /** @return observable result of a password change */
    public LiveData<ApiResult<Void>> getPasswordResult() { return passwordResult; }
    /** @return observable result of an email change */
    public LiveData<ApiResult<AuthResponse>> getEmailResult() { return emailResult; }
    /** @return observable result of an account deactivation */
    public LiveData<ApiResult<Void>> getDeactivateResult() { return deactivateResult; }
    /** @return observable result of a logout */
    public LiveData<ApiResult<Void>> getLogoutResult() { return logoutResult; }
    /** @return observable result of a Cloudinary upload-signature request */
    public LiveData<ApiResult<CloudinarySignatureResponse>> getSignatureResult() { return signatureResult; }
    /** @return one-shot client-side validation errors, to surface as a Toast */
    public LiveData<Event<String>> getValidationError() { return validationError; }
    /** @return observable result of the Favorites list fetch, used to derive its row's count */
    public LiveData<ApiResult<List<RecipePreviewResponse>>> getFavoritesResult() { return favoritesResult; }
    /** @return observable result of the My recipes list fetch, used to derive its row's count */
    public LiveData<ApiResult<List<RecipePreviewResponse>>> getMyRecipesResult() { return myRecipesResult; }
}
