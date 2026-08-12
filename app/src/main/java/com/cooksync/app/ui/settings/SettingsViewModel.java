package com.cooksync.app.ui.settings;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.data.repository.MediaRepository;
import com.cooksync.app.data.repository.RecipeRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.domain.Event;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.util.InputValidator;
import com.dtos.request.auth.AvatarUpdateRequestDTO;
import com.dtos.request.auth.ChangePasswordRequestDTO;
import com.dtos.request.auth.DeleteAccountRequestDTO;
import com.dtos.request.auth.EmailUpdateRequestDTO;
import com.dtos.request.auth.PrivacySettingsUpdateRequestDTO;
import com.dtos.request.auth.ProfileUpdateRequestDTO;
import com.dtos.response.auth.AuthResponse;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.dtos.response.user.UserResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
    private final MutableLiveData<ApiResult<Void>> privacyResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> deleteAccountResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<Void>> logoutResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<CloudinarySignatureResponse>> signatureResult = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> validationError = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> favoritesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<List<RecipePreviewResponse>>> myRecipesResult = new MutableLiveData<>();
    private final MutableLiveData<ApiResult<UserResponse>> accountDetailsResult = new MutableLiveData<>();
    private final MutableLiveData<Event<ApiResult<Void>>> saveChangesResult = new MutableLiveData<>();
    private String pendingFolder;
    private String pendingPublicId;

    /**
     * Constructs the ViewModel with the given repositories, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
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
     * Validates and submits an updated first/last name, city, and bio. City and bio are
     * optional free text, so they are trimmed and passed through as-is (empty becomes null).
     *
     * Complexity:
     * Time: O(n) where n is the combined length of all four fields
     * Space: O(1)
     *
     * @param rawFirstName raw text from the first-name field
     * @param rawLastName  raw text from the last-name field
     * @param rawCity      raw text from the city field, may be blank
     * @param rawBio       raw text from the bio field, may be blank
     */
    public void updateProfile(String rawFirstName, String rawLastName, String rawCity, String rawBio) {
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
        String city = rawCity == null || rawCity.trim().isEmpty() ? null : rawCity.trim();
        String bio = rawBio == null || rawBio.trim().isEmpty() ? null : rawBio.trim();
        authRepository.updateProfile(new ProfileUpdateRequestDTO(rawFirstName.trim(), rawLastName.trim(), city, bio), profileResult);
    }

    /**
     * Fetches the current user's full profile, including city, bio, and privacy preferences,
     * to pre-fill the Account Details screen.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    public void loadAccountDetails() {
        authRepository.getCurrentUserProfile(accountDetailsResult);
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
        String userId = com.cooksync.app.util.SessionManager.getInstance().getUserId();
        String first = com.cooksync.app.util.SessionManager.getInstance().getFirstName() == null ? "" : com.cooksync.app.util.SessionManager.getInstance().getFirstName().trim();
        String last = com.cooksync.app.util.SessionManager.getInstance().getLastName() == null ? "" : com.cooksync.app.util.SessionManager.getInstance().getLastName().trim();
        pendingFolder = "cooksync/" + userId + "/avatar";
        pendingPublicId = first + "_" + last + "_" + userId + "_" + System.currentTimeMillis();
        mediaRepository.getUploadSignature(pendingFolder, pendingPublicId, signatureResult);
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
     * Submits the profile (name/city/bio), privacy settings, and — if a new password was
     * entered — a password change, as a single batch. Every request that applies is fired in
     * parallel; the batch reports exactly one combined outcome via {@link #getSaveChangesResult()}
     * once all of them have settled, so the caller can navigate away on success without racing
     * any one of them. A client-side validation failure (invalid name, weak password, mismatched
     * repeat) fails fast and fires no network call at all.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param rawFirstName          raw text from the first-name field
     * @param rawLastName           raw text from the last-name field
     * @param rawCity               raw text from the city field, may be blank
     * @param rawBio                raw text from the bio field, may be blank
     * @param showRecipesPublicly   whether published recipes appear on the public profile
     * @param showFavoritesPublicly whether favorited recipes are visible to other users
     * @param rawCurrentPassword    raw text from the current-password field, required only if
     *                              {@code rawNewPassword} is non-blank
     * @param rawNewPassword        raw text from the new-password field, or blank to leave the
     *                              password unchanged
     * @param rawRepeatPassword     raw text from the repeat-new-password field
     */
    public void saveAccountChanges(String rawFirstName, String rawLastName, String rawCity, String rawBio,
                                    boolean showRecipesPublicly, boolean showFavoritesPublicly,
                                    String rawCurrentPassword, String rawNewPassword, String rawRepeatPassword) {
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

        boolean changingPassword = rawNewPassword != null && !rawNewPassword.isEmpty();
        if (changingPassword) {
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
            InputValidator.ValidationResult matchRes = InputValidator.validatePasswordsMatch(rawNewPassword, rawRepeatPassword);
            if (!matchRes.isValid) {
                validationError.setValue(new Event<>(matchRes.errorMessage));
                return;
            }
        }

        int totalCalls = changingPassword ? 3 : 2;
        AtomicInteger remaining = new AtomicInteger(totalCalls);
        AtomicReference<String> firstError = new AtomicReference<>();
        Consumer<ApiResult<Void>> onEachSettled = result -> {
            if (result instanceof ApiResult.Error<Void> error) {
                firstError.compareAndSet(null, error.getMessage());
            }
            if (remaining.decrementAndGet() == 0) {
                String error = firstError.get();
                saveChangesResult.postValue(new Event<>(error != null
                        ? new ApiResult.Error<>(error, null)
                        : new ApiResult.Success<>(null)));
            }
        };

        String city = rawCity == null || rawCity.trim().isEmpty() ? null : rawCity.trim();
        String bio = rawBio == null || rawBio.trim().isEmpty() ? null : rawBio.trim();
        MutableLiveData<ApiResult<Void>> profileTarget = new MutableLiveData<>();
        observeOnce(profileTarget, onEachSettled);
        authRepository.updateProfile(new ProfileUpdateRequestDTO(rawFirstName.trim(), rawLastName.trim(), city, bio), profileTarget);

        MutableLiveData<ApiResult<Void>> privacyTarget = new MutableLiveData<>();
        observeOnce(privacyTarget, onEachSettled);
        authRepository.updatePrivacySettings(new PrivacySettingsUpdateRequestDTO(showRecipesPublicly, showFavoritesPublicly), privacyTarget);

        if (changingPassword) {
            MutableLiveData<ApiResult<Void>> passwordTarget = new MutableLiveData<>();
            observeOnce(passwordTarget, onEachSettled);
            authRepository.changePassword(new ChangePasswordRequestDTO(rawCurrentPassword, rawNewPassword), passwordTarget);
        }
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
     * Submits updated public-profile privacy preferences.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param showRecipesPublicly   whether published recipes appear on the public profile
     * @param showFavoritesPublicly whether favorited recipes are visible to other users
     */
    public void updatePrivacySettings(boolean showRecipesPublicly, boolean showFavoritesPublicly) {
        authRepository.updatePrivacySettings(
                new PrivacySettingsUpdateRequestDTO(showRecipesPublicly, showFavoritesPublicly), privacyResult);
    }

    /**
     * Validates and submits an account-deletion request, starting the 30-day grace period.
     *
     * Complexity:
     * Time: O(n) where n is the password length
     * Space: O(1)
     *
     * @param rawCurrentPassword raw text from the current-password confirmation field
     */
    public void deleteAccount(String rawCurrentPassword) {
        InputValidator.ValidationResult passwordRes = InputValidator.validateLoginPassword(rawCurrentPassword);
        if (!passwordRes.isValid) {
            validationError.setValue(new Event<>(passwordRes.errorMessage));
            return;
        }
        authRepository.requestAccountDeletion(new DeleteAccountRequestDTO(rawCurrentPassword), deleteAccountResult);
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
    /** @return observable result of a privacy settings update */
    public LiveData<ApiResult<Void>> getPrivacyResult() { return privacyResult; }
    /** @return observable result of an account-deletion request */
    public LiveData<ApiResult<Void>> getDeleteAccountResult() { return deleteAccountResult; }
    /** @return one-shot combined outcome of {@link #saveAccountChanges}, Success only once every fired call has succeeded */
    public LiveData<Event<ApiResult<Void>>> getSaveChangesResult() { return saveChangesResult; }
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
    /** @return observable result of the current user's full profile fetch */
    public LiveData<ApiResult<UserResponse>> getAccountDetailsResult() { return accountDetailsResult; }
    public String getPendingFolder() { return pendingFolder; }
    public String getPendingPublicId() { return pendingPublicId; }

    /**
     * Compares the account details form's current field values against their last-known-saved
     * baseline, deciding whether leaving the screen should prompt a "discard changes?"
     * confirmation. The Activity supplies the current values (it owns the widgets); this method
     * owns only the "what counts as changed" rule.
     *
     * Complexity:
     * Time: O(n) where n is the combined length of the text fields
     * Space: O(1)
     *
     * @param firstName current value of the first-name field
     * @param lastName current value of the last-name field
     * @param city current value of the city field
     * @param bio current value of the bio field
     * @param email current value of the email field
     * @param currentPassword current value of the current-password field
     * @param newPassword current value of the new-password field
     * @param repeatNewPassword current value of the repeat-new-password field
     * @param showRecipesPublicly current state of the "show recipes publicly" checkbox
     * @param showFavoritesPublicly current state of the "show favorites publicly" checkbox
     * @param pendingAvatarChange whether a new photo was picked or the avatar was cleared, but
     *                            not yet saved
     * @param baselineFirstName first name as last loaded/saved
     * @param baselineLastName last name as last loaded/saved
     * @param baselineCity city as last loaded/saved
     * @param baselineBio bio as last loaded/saved
     * @param baselineEmail email as last loaded/saved
     * @param baselineShowRecipesPublicly "show recipes publicly" as last loaded/saved
     * @param baselineShowFavoritesPublicly "show favorites publicly" as last loaded/saved
     * @return {@code true} if any field or the avatar differs from what's actually saved
     */
    public boolean hasUnsavedAccountChanges(
            String firstName, String lastName, String city, String bio, String email,
            String currentPassword, String newPassword, String repeatNewPassword,
            boolean showRecipesPublicly, boolean showFavoritesPublicly, boolean pendingAvatarChange,
            String baselineFirstName, String baselineLastName, String baselineCity, String baselineBio,
            String baselineEmail, boolean baselineShowRecipesPublicly, boolean baselineShowFavoritesPublicly) {
        if (pendingAvatarChange) return true;
        if (!firstName.trim().equals(baselineFirstName)) return true;
        if (!lastName.trim().equals(baselineLastName)) return true;
        if (!city.trim().equals(baselineCity)) return true;
        if (!bio.trim().equals(baselineBio)) return true;
        if (!email.trim().equalsIgnoreCase(baselineEmail)) return true;
        if (!currentPassword.isEmpty()) return true;
        if (!newPassword.isEmpty()) return true;
        if (!repeatNewPassword.isEmpty()) return true;
        if (showRecipesPublicly != baselineShowRecipesPublicly) return true;
        if (showFavoritesPublicly != baselineShowFavoritesPublicly) return true;
        return false;
    }
}
