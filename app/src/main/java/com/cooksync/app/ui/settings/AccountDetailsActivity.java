package com.cooksync.app.ui.settings;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.util.CloudinaryUploader;
import com.cooksync.app.util.SessionManager;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.user.UserResponse;
import com.google.android.material.button.MaterialButton;

/**
 * Dedicated screen for managing every self-service account setting in one place: name, city,
 * bio, email, password, avatar, public-profile privacy toggles, and account deletion. Reached
 * from the "Account details" row in {@link SettingsActivity}, matching the design's
 * {@code is.edit} screen.
 *
 * <p>Reuses {@link SettingsViewModel} exactly as {@link SettingsActivity} does — this screen is
 * a new View bound to the same ViewModel and repository calls, not a new business-logic layer.
 * Each edited section ("Save changes" tap) fires only the network calls whose underlying fields
 * actually changed, mirroring the granularity of the server's {@code AuthController} endpoints.</p>
 *
 * <p>A picked avatar (or "Use initials instead") is only a local preview until "Save changes" is
 * tapped — nothing is uploaded or persisted until then, matching every other field on this
 * screen. Leaving the screen (back arrow, system back, or Cancel) with any unsaved field —
 * including a pending avatar change — prompts a discard-confirmation dialog first.</p>
 *
 * @author Yaron Serlin
 * @version 1.1
 * @since 08/08/2026
 */
public class AccountDetailsActivity extends BaseActivity {

    private SettingsViewModel viewModel;

    private ImageView ivAvatar;
    private TextView tvAvatarInitials;
    private ProgressBar avatarProgress;
    private EditText etFirstName;
    private EditText etLastName;
    private EditText etEmail;
    private EditText etCity;
    private EditText etBio;
    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etRepeatNewPassword;
    private MaterialCheckBox cbShowRecipesPublicly;
    private MaterialCheckBox cbShowFavoritesPublicly;
    private View footer;

    private ActivityResultLauncher<String> pickAvatarLauncher;

    /** A newly picked photo not yet uploaded/saved; mutually exclusive with {@link #avatarCleared}. */
    private Uri pendingAvatarUri;
    /** Whether "Use initials instead" was tapped but not yet saved. */
    private boolean avatarCleared;

    // Baseline values loaded from the server, used both to detect what changed on Save and to
    // detect unsaved edits on exit. Updated after each section's own successful save.
    private String loadedFirstName = "";
    private String loadedLastName = "";
    private String loadedEmail = "";
    private String loadedCity = "";
    private String loadedBio = "";
    private boolean loadedShowRecipesPublicly = true;
    private boolean loadedShowFavoritesPublicly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_details);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(SettingsViewModel.class);

        pickAvatarLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                pendingAvatarUri = uri;
                avatarCleared = false;
                tvAvatarInitials.setVisibility(View.GONE);
                Glide.with(this).load(uri).transform(new CircleCrop()).into(ivAvatar);
                OrganicToast.show(this, footer, getString(R.string.account_details_avatar_pending));
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                attemptExit();
            }
        });

        bindViews();
        renderCachedProfile();
        setupObservers();

        findViewById(R.id.btn_back).setOnClickListener(v -> attemptExit());
        findViewById(R.id.btn_edit_avatar).setOnClickListener(v -> pickAvatarLauncher.launch("image/*"));
        findViewById(R.id.btn_upload_photo).setOnClickListener(v -> pickAvatarLauncher.launch("image/*"));
        findViewById(R.id.btn_use_initials).setOnClickListener(v -> {
            pendingAvatarUri = null;
            avatarCleared = true;
            renderAvatar(null);
            OrganicToast.show(this, footer, getString(R.string.account_details_avatar_cleared_pending));
        });
        findViewById(R.id.btn_cancel).setOnClickListener(v -> attemptExit());
        findViewById(R.id.btn_save).setOnClickListener(v -> onSaveClicked());
        findViewById(R.id.btn_delete_account).setOnClickListener(v -> confirmDeleteAccount());

        viewModel.loadAccountDetails();
    }

    private void bindViews() {
        ivAvatar = findViewById(R.id.iv_avatar);
        tvAvatarInitials = findViewById(R.id.tv_avatar_initials);
        avatarProgress = findViewById(R.id.avatar_progress);
        etFirstName = findViewById(R.id.et_first_name);
        etLastName = findViewById(R.id.et_last_name);
        etEmail = findViewById(R.id.et_email);
        etCity = findViewById(R.id.et_city);
        etBio = findViewById(R.id.et_bio);
        etCurrentPassword = findViewById(R.id.et_current_password);
        etNewPassword = findViewById(R.id.et_new_password);
        etRepeatNewPassword = findViewById(R.id.et_repeat_new_password);
        cbShowRecipesPublicly = findViewById(R.id.cb_show_recipes_publicly);
        cbShowFavoritesPublicly = findViewById(R.id.cb_show_favorites_publicly);
        footer = findViewById(R.id.footer);
    }

    /**
     * Pre-fills the form with whatever is already cached locally, so the screen isn't blank
     * while {@link SettingsViewModel#loadAccountDetails()}'s network call is in flight. City,
     * bio, and privacy preferences aren't part of the local cache and are filled in once that
     * call resolves.
     */
    private void renderCachedProfile() {
        loadedFirstName = nullToEmpty(SessionManager.getInstance().getFirstName());
        loadedLastName = nullToEmpty(SessionManager.getInstance().getLastName());
        loadedEmail = nullToEmpty(SessionManager.getInstance().getEmail());
        etFirstName.setText(loadedFirstName);
        etLastName.setText(loadedLastName);
        etEmail.setText(loadedEmail);
        renderAvatar(SessionManager.getInstance().getAvatarUrl());
    }

    private void renderAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            ivAvatar.setImageDrawable(null);
            tvAvatarInitials.setText(SessionManager.getInstance().getInitials());
            tvAvatarInitials.setVisibility(View.VISIBLE);
        } else {
            tvAvatarInitials.setVisibility(View.GONE);
            Glide.with(this).load(avatarUrl).transform(new CircleCrop()).into(ivAvatar);
        }
    }

    private void setAvatarUploading(boolean uploading) {
        avatarProgress.setVisibility(uploading ? View.VISIBLE : View.GONE);
        ((MaterialButton) findViewById(R.id.btn_upload_photo)).setEnabled(!uploading);
        findViewById(R.id.btn_edit_avatar).setEnabled(!uploading);
    }

    private void setupObservers() {
        viewModel.getValidationError().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) showError(message, footer);
        });

        viewModel.getAccountDetailsResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<UserResponse> success) {
                UserResponse data = success.getData();
                loadedFirstName = nullToEmpty(data.firstName());
                loadedLastName = nullToEmpty(data.lastName());
                loadedEmail = nullToEmpty(data.email());
                loadedCity = nullToEmpty(data.city());
                loadedBio = nullToEmpty(data.bio());
                loadedShowRecipesPublicly = data.showRecipesPublicly();
                loadedShowFavoritesPublicly = data.showFavoritesPublicly();
                etFirstName.setText(loadedFirstName);
                etLastName.setText(loadedLastName);
                etEmail.setText(loadedEmail);
                etCity.setText(loadedCity);
                etBio.setText(loadedBio);
                cbShowRecipesPublicly.setChecked(loadedShowRecipesPublicly);
                cbShowFavoritesPublicly.setChecked(loadedShowFavoritesPublicly);
                if (pendingAvatarUri == null && !avatarCleared) {
                    renderAvatar(data.avatarUrl());
                }
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), footer);
            }
        });

        viewModel.getSignatureResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<CloudinarySignatureResponse> success && pendingAvatarUri != null) {
                CloudinaryUploader.upload(this, pendingAvatarUri, viewModel.getPendingFolder(), viewModel.getPendingPublicId(), success.getData(), new CloudinaryUploader.Callback() {
                    @Override
                    public void onSuccess(@NonNull String secureUrl) {
                        viewModel.updateAvatar(secureUrl);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        setAvatarUploading(false);
                        showError(message, footer);
                    }
                });
            } else if (result instanceof ApiResult.Error<?> error) {
                setAvatarUploading(false);
                showError(error.getMessage(), footer);
            }
        });

        viewModel.getAvatarResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                setAvatarUploading(false);
                pendingAvatarUri = null;
                avatarCleared = false;
                renderAvatar(SessionManager.getInstance().getAvatarUrl());
                saveRemainingProfileChanges();
            } else if (result instanceof ApiResult.Error<?> error) {
                setAvatarUploading(false);
                renderAvatar(SessionManager.getInstance().getAvatarUrl());
                showError(error.getMessage(), footer);
            }
        });

        viewModel.getEmailResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                loadedEmail = etEmail.getText().toString().trim();
                showSuccess(getString(R.string.settings_email_updated), footer);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), footer);
            }
        });

        viewModel.getSaveChangesResult().observe(this, event -> {
            ApiResult<Void> result = event.getContentIfNotHandled();
            if (result == null) return;
            if (result instanceof ApiResult.Success) {
                loadedFirstName = etFirstName.getText().toString().trim();
                loadedLastName = etLastName.getText().toString().trim();
                loadedCity = etCity.getText().toString().trim();
                loadedBio = etBio.getText().toString().trim();
                loadedShowRecipesPublicly = cbShowRecipesPublicly.isChecked();
                loadedShowFavoritesPublicly = cbShowFavoritesPublicly.isChecked();
                etCurrentPassword.setText("");
                etNewPassword.setText("");
                etRepeatNewPassword.setText("");

                Intent extras = new Intent();
                extras.putExtra(SettingsActivity.EXTRA_PENDING_TOAST, getString(R.string.settings_updated));
                Navigator.start(AccountDetailsActivity.this, SettingsActivity.class, extras);
                finish();
            } else if (result instanceof ApiResult.Error<Void> error) {
                showError(error.getMessage(), footer);
            }
        });

        viewModel.getDeleteAccountResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                showSuccess(getString(R.string.account_details_deletion_requested), footer);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), footer);
            }
        });
    }

    /**
     * Kicks off saving the form. A pending avatar change (new photo or "use initials") is
     * resolved first — {@link #saveRemainingProfileChanges()} only runs once that single call
     * has actually settled, from {@link SettingsViewModel#getAvatarResult()}'s success handler,
     * so it never fires twice for the same tap and never races the avatar write into
     * {@link SessionManager}. With no pending avatar change, the rest of the form is submitted
     * right away.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void onSaveClicked() {
        if (pendingAvatarUri != null) {
            setAvatarUploading(true);
            viewModel.requestUploadSignature();
        } else if (avatarCleared) {
            setAvatarUploading(true);
            viewModel.updateAvatar(null);
        } else {
            saveRemainingProfileChanges();
        }
    }

    /**
     * If the email field changed, gates the whole save behind a password-confirmation dialog —
     * {@link #submitAccountChanges()} only runs from that dialog's confirm callback, once it has
     * already dismissed itself, so it never fires while the dialog is still on screen and can't
     * be torn down mid-confirmation by a navigate-away triggered from the rest of the batch.
     * Cancelling the dialog abandons the whole save attempt. With no email change, the batch is
     * submitted right away.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void saveRemainingProfileChanges() {
        String newEmail = etEmail.getText().toString().trim();
        if (!newEmail.equalsIgnoreCase(loadedEmail)) {
            showEmailChangeDialog(newEmail);
        } else {
            submitAccountChanges();
        }
    }

    /**
     * Prompts for the current password before submitting {@code newEmail}, since changing email
     * requires re-authentication and the "current password" field on this screen is meant for
     * the password-change section, not implicitly reused for email too. On confirm, submits the
     * email change and then the rest of the form's changes, in that order.
     *
     * @param newEmail the new email address to submit once the password is confirmed
     */
    private void showEmailChangeDialog(String newEmail) {
        OrganicConfirmDialog.showWithPasswordConfirm(this,
                getString(R.string.account_details_dialog_change_email_title),
                getString(R.string.account_details_dialog_change_email_message, newEmail),
                getString(R.string.account_details_action_change_email),
                getString(R.string.action_cancel),
                password -> {
                    viewModel.updateEmail(newEmail, password);
                    submitAccountChanges();
                });
    }

    /**
     * Submits the name/city/bio/privacy/password batch (see
     * {@link SettingsViewModel#saveAccountChanges}); on success this navigates to
     * {@link SettingsActivity}, so it must only run once any required email confirmation has
     * already been resolved.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void submitAccountChanges() {
        viewModel.saveAccountChanges(
                etFirstName.getText().toString(),
                etLastName.getText().toString(),
                etCity.getText().toString(),
                etBio.getText().toString(),
                cbShowRecipesPublicly.isChecked(),
                cbShowFavoritesPublicly.isChecked(),
                etCurrentPassword.getText().toString(),
                etNewPassword.getText().toString(),
                etRepeatNewPassword.getText().toString());
    }

    private void confirmDeleteAccount() {
        OrganicConfirmDialog.showWithPasswordConfirm(this,
                getString(R.string.account_details_dialog_delete_title),
                getString(R.string.account_details_dialog_delete_message),
                getString(R.string.account_details_action_delete),
                getString(R.string.action_cancel),
                viewModel::deleteAccount);
    }

    /**
     * Leaves the screen immediately if nothing is unsaved, otherwise asks the user to confirm
     * discarding their edits first.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void attemptExit() {
        if (!hasUnsavedChanges()) {
            finish();
            return;
        }
        OrganicConfirmDialog.show(this,
                getString(R.string.account_details_discard_title),
                getString(R.string.account_details_discard_message),
                getString(R.string.account_details_discard_confirm),
                getString(R.string.account_details_discard_keep_editing),
                true,
                this::finish);
    }

    /**
     * Compares every editable field (plus any pending, not-yet-saved avatar change) against its
     * last-known-saved baseline.
     *
     * Complexity:
     * Time: O(n) where n is the combined length of the editable text fields
     * Space: O(1)
     *
     * @return true if any field or the avatar differs from what's actually saved
     */
    private boolean hasUnsavedChanges() {
        return viewModel.hasUnsavedAccountChanges(
                etFirstName.getText().toString(), etLastName.getText().toString(),
                etCity.getText().toString(), etBio.getText().toString(), etEmail.getText().toString(),
                etCurrentPassword.getText().toString(), etNewPassword.getText().toString(),
                etRepeatNewPassword.getText().toString(),
                cbShowRecipesPublicly.isChecked(), cbShowFavoritesPublicly.isChecked(),
                pendingAvatarUri != null || avatarCleared,
                loadedFirstName, loadedLastName, loadedCity, loadedBio, loadedEmail,
                loadedShowRecipesPublicly, loadedShowFavoritesPublicly);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
