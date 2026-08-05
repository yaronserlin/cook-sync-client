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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.home.HomeActivity;
import com.cooksync.app.ui.recipe.FavoriteRecipesActivity;
import com.cooksync.app.ui.recipe.MyRecipesActivity;
import com.cooksync.app.util.CloudinaryUploader;
import com.cooksync.app.util.SessionManager;
import com.dtos.response.auth.AuthResponse;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * The "Profile" tab: view and edit account details (name, avatar, password, email), log out,
 * or deactivate the account. Uses the same shared {@link AuthResponse}-backed session cache
 * ({@link SessionManager}) that every other screen reads for the avatar chip's initials, so
 * a successful edit here is immediately reflected everywhere else on the next visit.
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
public class ProfileActivity extends AppCompatActivity {

    private ProfileViewModel viewModel;

    private ImageView ivAvatar;
    private TextView tvAvatarInitials;
    private ProgressBar avatarProgress;
    private TextView tvName;
    private TextView tvEmail;

    private ActivityResultLauncher<String> pickAvatarLauncher;
    private Uri pendingAvatarUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

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
        tvEmail.setText(email != null ? email : "");
        tvEmail.setVisibility(email != null ? View.VISIBLE : View.GONE);

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

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_profile);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                return true;
            }
            Class<? extends AppCompatActivity> target = null;
            if (id == R.id.nav_home) {
                target = HomeActivity.class;
            } else if (id == R.id.nav_my_recipes) {
                target = MyRecipesActivity.class;
            } else if (id == R.id.nav_favorites) {
                target = FavoriteRecipesActivity.class;
            }
            if (target == null) {
                return false;
            }
            Intent intent = new Intent(this, target);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
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
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getProfileResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                renderCachedProfile();
                Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show();
            } else if (result instanceof ApiResult.Error<?> error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
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
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            } else if (result instanceof ApiResult.Error<?> error) {
                setAvatarUploading(false);
                pendingAvatarUri = null;
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getAvatarResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                setAvatarUploading(false);
                pendingAvatarUri = null;
                renderAvatar(SessionManager.getInstance().getAvatarUrl());
                Toast.makeText(this, R.string.profile_avatar_updated, Toast.LENGTH_SHORT).show();
            } else if (result instanceof ApiResult.Error<?> error) {
                setAvatarUploading(false);
                pendingAvatarUri = null;
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getPasswordResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                Toast.makeText(this, R.string.profile_password_updated, Toast.LENGTH_SHORT).show();
            } else if (result instanceof ApiResult.Error<?> error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getEmailResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                renderCachedProfile();
                Toast.makeText(this, R.string.profile_email_updated, Toast.LENGTH_SHORT).show();
            } else if (result instanceof ApiResult.Error<?> error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getDeactivateResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                Toast.makeText(this, R.string.profile_account_deactivated, Toast.LENGTH_LONG).show();
            } else if (result instanceof ApiResult.Error<?> error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
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

        new MaterialAlertDialogBuilder(this)
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

        new MaterialAlertDialogBuilder(this)
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

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_dialog_change_email_title)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) ->
                        viewModel.updateEmail(etEmail.getText().toString(), etPassword.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_dialog_logout_title)
                .setPositiveButton(R.string.profile_action_logout, (dialog, which) -> viewModel.logout())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDeactivate() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_dialog_deactivate_title)
                .setMessage(R.string.profile_dialog_deactivate_message)
                .setPositiveButton(R.string.profile_action_deactivate, (dialog, which) -> viewModel.deactivateAccount())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
