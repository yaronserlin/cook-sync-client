package com.cooksync.app.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.BaseActivity;
import com.cooksync.app.ui.common.FullscreenImageActivity;
import com.cooksync.app.ui.common.Navigator;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.ViewModelFactory;
import com.cooksync.app.ui.home.HomeActivity;
import com.cooksync.app.ui.recipe.list.FavoriteRecipesActivity;
import com.cooksync.app.ui.recipe.list.MyRecipesActivity;
import com.cooksync.app.util.CloudinaryUploader;
import com.cooksync.app.util.SessionManager;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

/**
 * The "Profile" tab: view and edit account details (name, avatar, password, email), log out,
 * or deactivate the account. Uses the same shared {@link com.dtos.response.auth.AuthResponse}-backed
 * session cache ({@link SessionManager}) that every other screen reads for the avatar chip's
 * initials, so a successful edit here is immediately reflected everywhere else on the next visit.
 *
 * <p>Avatar uploads go directly from this device to Cloudinary using a short-lived signature
 * fetched from the server (see {@link com.cooksync.app.data.repository.MediaRepository}), and
 * only the resulting URL is sent to CookSync's own server — the binary image never passes
 * through the application backend.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class ProfileActivity extends BaseActivity {

    private ProfileViewModel viewModel;

    private ImageView ivAvatar;
    private TextView tvAvatarInitials;
    private ProgressBar avatarProgress;
    private TextView tvName;
    private TextView tvEmail;

    private ActivityResultLauncher<String> pickAvatarLauncher;
    private Uri pendingAvatarUri;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(ProfileViewModel.class);

        pickAvatarLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                pendingAvatarUri = uri;
                setAvatarUploading(true);
                viewModel.requestUploadSignature();
            }
        });

        bindViews();
        renderCachedProfile();
        setupBottomNav();
        setupRowClicks();
        setupObservers();

        findViewById(R.id.btn_edit_avatar).setOnClickListener(v -> pickAvatarLauncher.launch("image/*"));
        findViewById(R.id.btn_logout).setOnClickListener(v -> confirmLogout());
        findViewById(R.id.btn_deactivate).setOnClickListener(v -> confirmDeactivate());
    }

    private void bindViews() {
        ivAvatar = findViewById(R.id.iv_avatar);
        tvAvatarInitials = findViewById(R.id.tv_avatar_initials);
        avatarProgress = findViewById(R.id.avatar_progress);
        tvName = findViewById(R.id.tv_name);
        tvEmail = findViewById(R.id.tv_email);
    }

    private void renderCachedProfile() {
        String first = SessionManager.getInstance().getFirstName();
        String last = SessionManager.getInstance().getLastName();
        tvName.setText(TextUtils.join(" ", new String[]{nullToEmpty(first), nullToEmpty(last)}).trim());

        String email = SessionManager.getInstance().getEmail();
        tvEmail.setText(Objects.requireNonNullElse(email, ""));
        tvEmail.setVisibility(email != null ? View.VISIBLE : View.GONE);

        renderAvatar(SessionManager.getInstance().getAvatarUrl());
    }

    private void renderAvatar(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isEmpty()) {
            ivAvatar.setImageDrawable(null);
            ivAvatar.setOnClickListener(null);
            tvAvatarInitials.setText(SessionManager.getInstance().getInitials());
            tvAvatarInitials.setVisibility(View.VISIBLE);
        } else {
            tvAvatarInitials.setVisibility(View.GONE);
            Glide.with(this).load(avatarUrl).transform(new CircleCrop()).into(ivAvatar);
            ivAvatar.setOnClickListener(v -> openFullscreenImage(avatarUrl));
        }
    }

    /**
     * Opens {@link FullscreenImageActivity} to view the current avatar photo full-screen.
     *
     * @param imageUrl the avatar's image URL
     */
    private void openFullscreenImage(String imageUrl) {
        Intent intent = new Intent();
        intent.putExtra(FullscreenImageActivity.EXTRA_IMAGE_URL, imageUrl);
        Navigator.start(this, FullscreenImageActivity.class, intent);
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) return true;
            Class<? extends AppCompatActivity> target;
            if (id == R.id.nav_home) target = HomeActivity.class;
            else if (id == R.id.nav_my_recipes) target = MyRecipesActivity.class;
            else if (id == R.id.nav_favorites) target = FavoriteRecipesActivity.class;
            else return false;

            Intent extras = new Intent();
            extras.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            Navigator.start(this, target, extras);
            Navigator.finish(this);
            return true;
        });
    }

    private void setupRowClicks() {
        findViewById(R.id.row_edit_name).<TextView>findViewById(R.id.tv_row_label).setText(R.string.profile_row_edit_name);
        findViewById(R.id.row_change_password).<TextView>findViewById(R.id.tv_row_label).setText(R.string.profile_row_change_password);
        findViewById(R.id.row_change_email).<TextView>findViewById(R.id.tv_row_label).setText(R.string.profile_row_change_email);

        findViewById(R.id.row_edit_name).setOnClickListener(v -> showEditNameDialog());
        findViewById(R.id.row_change_password).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.row_change_email).setOnClickListener(v -> showChangeEmailDialog());
    }

    private void setupObservers() {
        viewModel.getValidationError().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) showError(message, bottomNav);
        });

        viewModel.getProfileResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                renderCachedProfile();
                showSuccess(getString(R.string.profile_updated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getSignatureResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<CloudinarySignatureResponse> success && pendingAvatarUri != null) {
                CloudinaryUploader.upload(this, pendingAvatarUri, success.getData(), new CloudinaryUploader.Callback() {
                    @Override
                    public void onSuccess(@NonNull String secureUrl) {
                        viewModel.updateAvatar(secureUrl);
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        setAvatarUploading(false);
                        pendingAvatarUri = null;
                        showError(message, bottomNav);
                    }
                });
            } else if (result instanceof ApiResult.Error<?> error) {
                setAvatarUploading(false);
                pendingAvatarUri = null;
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getAvatarResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                setAvatarUploading(false);
                pendingAvatarUri = null;
                renderAvatar(SessionManager.getInstance().getAvatarUrl());
                showSuccess(getString(R.string.profile_avatar_updated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                setAvatarUploading(false);
                pendingAvatarUri = null;
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getPasswordResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                showSuccess(getString(R.string.profile_password_updated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getEmailResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                renderCachedProfile();
                showSuccess(getString(R.string.profile_email_updated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getDeactivateResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                showSuccess(getString(R.string.profile_account_deactivated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), bottomNav);
            }
        });
    }

    private void setAvatarUploading(boolean uploading) {
        avatarProgress.setVisibility(uploading ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_edit_avatar).setEnabled(!uploading);
    }

    private void showEditNameDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_name, null);
        EditText etFirst = view.findViewById(R.id.et_first_name);
        EditText etLast = view.findViewById(R.id.et_last_name);
        etFirst.setText(SessionManager.getInstance().getFirstName());
        etLast.setText(SessionManager.getInstance().getLastName());

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(R.string.profile_dialog_edit_name_title)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) ->
                        viewModel.updateProfile(etFirst.getText().toString(), etLast.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showChangePasswordDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        EditText etCurrent = view.findViewById(R.id.et_current_password);
        EditText etNew = view.findViewById(R.id.et_new_password);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(R.string.profile_dialog_change_password_title)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) ->
                        viewModel.changePassword(etCurrent.getText().toString(), etNew.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showChangeEmailDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_change_email, null);
        EditText etEmail = view.findViewById(R.id.et_new_email);
        EditText etPassword = view.findViewById(R.id.et_current_password);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_CookSync_Dialog)
                .setTitle(R.string.profile_dialog_change_email_title)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) ->
                        viewModel.updateEmail(etEmail.getText().toString(), etPassword.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmLogout() {
        OrganicConfirmDialog.show(this, getString(R.string.profile_dialog_logout_title),
                getString(R.string.profile_dialog_logout_message), getString(R.string.profile_action_logout),
                getString(R.string.action_cancel), false, viewModel::logout);
    }

    private void confirmDeactivate() {
        OrganicConfirmDialog.show(this, getString(R.string.profile_dialog_deactivate_title),
                getString(R.string.profile_dialog_deactivate_message), getString(R.string.profile_action_deactivate),
                getString(R.string.action_cancel), true, viewModel::deactivateAccount);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
