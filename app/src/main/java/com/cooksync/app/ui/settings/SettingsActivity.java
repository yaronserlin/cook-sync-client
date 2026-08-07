package com.cooksync.app.ui.settings;

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
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.cooksync.app.R;
import com.cooksync.app.data.local.CookingPreferencesStore;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.common.BaseActivity;
import com.cooksync.app.ui.common.FullscreenImageActivity;
import com.cooksync.app.ui.common.Navigator;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.common.ViewModelFactory;
import com.cooksync.app.ui.home.HomeActivity;
import com.cooksync.app.ui.recipe.list.FavoriteRecipesActivity;
import com.cooksync.app.ui.recipe.list.MyRecipesActivity;
import com.cooksync.app.util.CloudinaryUploader;
import com.cooksync.app.util.SessionManager;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Objects;

/**
 * The "Settings" tab: a hub of navigational rows (Favorites, My recipes, Notifications, Cooking
 * preferences, Account details, and, for admins, Admin console), plus the avatar header, log out,
 * and account deactivation. Uses the same shared {@link com.dtos.response.auth.AuthResponse}-backed
 * session cache ({@link SessionManager}) that every other screen reads for the avatar chip's
 * initials, so a successful edit here is immediately reflected everywhere else on the next visit.
 *
 * <p>Avatar uploads go directly from this device to Cloudinary using a short-lived signature
 * fetched from the server (see {@link com.cooksync.app.data.repository.MediaRepository}), and
 * only the resulting URL is sent to CookSync's own server — the binary image never passes
 * through the application backend.</p>
 *
 * <p>The name/password/email edit dialogs below are not reachable from any row in this screen
 * (their dedicated "Account details" row currently points at {@link AccountDetailsActivity}, a
 * placeholder), but are kept in place for reuse once that screen's real implementation is
 * scoped.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class SettingsActivity extends BaseActivity {

    private SettingsViewModel viewModel;

    private ImageView ivAvatar;
    private TextView tvAvatarInitials;
    private ProgressBar avatarProgress;
    private TextView tvName;
    private TextView tvEmail;
    private TextView tvFavoritesSub;
    private TextView tvMyRecipesSub;

    private ActivityResultLauncher<String> pickAvatarLauncher;
    private Uri pendingAvatarUri;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(SettingsViewModel.class);

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
        setupRows();
        setupObservers();

        findViewById(R.id.btn_edit_avatar).setOnClickListener(v -> pickAvatarLauncher.launch("image/*"));
        findViewById(R.id.btn_logout).setOnClickListener(v -> confirmLogout());
        findViewById(R.id.btn_deactivate).setOnClickListener(v -> confirmDeactivate());

        viewModel.loadFavoritesCount();
        viewModel.loadMyRecipesCount();
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
        Intent intent = new Intent(this, FullscreenImageActivity.class);
        intent.putExtra(FullscreenImageActivity.EXTRA_IMAGE_URL, imageUrl);
        Navigator.start(this, intent);
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_settings);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_settings) return true;
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

    /**
     * Binds every settings row's icon, label, subtitle and click destination, and hides the
     * "Admin console" row entirely for non-admin users.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void setupRows() {
        tvFavoritesSub = bindRow(R.id.row_favorites, R.drawable.ic_heart_filled,
                getString(R.string.settings_row_favorites_label),
                getString(R.string.settings_row_favorites_sub_format, 0),
                v -> Navigator.start(this, FavoriteRecipesActivity.class));

        tvMyRecipesSub = bindRow(R.id.row_my_recipes, R.drawable.ic_chef_hat,
                getString(R.string.settings_row_my_recipes_label),
                getString(R.string.settings_row_my_recipes_sub_format, 0),
                v -> Navigator.start(this, MyRecipesActivity.class));

        bindRow(R.id.row_notifications, R.drawable.ic_bell,
                getString(R.string.settings_row_notifications_label), getString(R.string.settings_row_notifications_sub),
                v -> showComingSoon(R.string.settings_row_notifications_label));

        TextView tvCookingSub = bindRow(R.id.row_cooking_preferences, R.drawable.ic_smartphone,
                getString(R.string.settings_row_cooking_preferences_label), null,
                v -> Navigator.start(this, CookingPreferencesActivity.class));
        tvCookingSub.setText(CookingPreferencesStore.isScreenAwakeEnabled()
                ? R.string.settings_row_cooking_preferences_sub_on
                : R.string.settings_row_cooking_preferences_sub_off);

        bindRow(R.id.row_account_details, R.drawable.ic_user_cog,
                getString(R.string.settings_row_account_details_label), getString(R.string.settings_row_account_details_sub),
                v -> Navigator.start(this, AccountDetailsActivity.class));

        View adminRow = findViewById(R.id.row_admin_console);
        boolean isAdmin = SessionManager.getInstance().isAdmin();
        adminRow.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        if (isAdmin) {
            bindRow(R.id.row_admin_console, R.drawable.ic_shield,
                    getString(R.string.settings_row_admin_console_label), getString(R.string.settings_row_admin_console_sub),
                    v -> showComingSoon(R.string.settings_row_admin_console_label));
        }
    }

    /**
     * Binds one {@code item_settings_row} include's icon, label, subtitle and click listener.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param rowId the id of the {@code <include>} hosting the row
     * @param iconRes the row's icon drawable
     * @param label the row's bold label text
     * @param sub the row's subtitle text, or {@code null} to leave it for the caller to set
     * @param onClick the action to run when the row is tapped
     * @return the row's subtitle {@link TextView}, so callers needing a dynamic subtitle can
     *         update it later
     */
    private TextView bindRow(int rowId, @DrawableRes int iconRes, String label, String sub,
                              View.OnClickListener onClick) {
        View row = findViewById(rowId);
        ((ImageView) row.findViewById(R.id.iv_row_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.tv_row_label)).setText(label);
        TextView tvSub = row.findViewById(R.id.tv_row_sub);
        if (sub != null) {
            tvSub.setText(sub);
        }
        row.setOnClickListener(onClick);
        return tvSub;
    }

    private void showComingSoon(@StringRes int rowLabelRes) {
        OrganicToast.show(this, bottomNav, getString(R.string.settings_row_coming_soon_format, getString(rowLabelRes)));
    }

    private void setupObservers() {
        viewModel.getValidationError().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) showError(message, bottomNav);
        });

        viewModel.getProfileResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                renderCachedProfile();
                showSuccess(getString(R.string.settings_updated), bottomNav);
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
                showSuccess(getString(R.string.settings_avatar_updated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                setAvatarUploading(false);
                pendingAvatarUri = null;
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getPasswordResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                showSuccess(getString(R.string.settings_password_updated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getEmailResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                renderCachedProfile();
                showSuccess(getString(R.string.settings_email_updated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getDeactivateResult().observe(this, result -> {
            if (result instanceof ApiResult.Success) {
                showSuccess(getString(R.string.settings_account_deactivated), bottomNav);
            } else if (result instanceof ApiResult.Error<?> error) {
                showError(error.getMessage(), bottomNav);
            }
        });

        viewModel.getFavoritesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                tvFavoritesSub.setText(getString(R.string.settings_row_favorites_sub_format, success.getData().size()));
            }
        });

        viewModel.getMyRecipesResult().observe(this, result -> {
            if (result instanceof ApiResult.Success<List<RecipePreviewResponse>> success) {
                tvMyRecipesSub.setText(getString(R.string.settings_row_my_recipes_sub_format, success.getData().size()));
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
                .setTitle(R.string.settings_dialog_edit_name_title)
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
                .setTitle(R.string.settings_dialog_change_password_title)
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
                .setTitle(R.string.settings_dialog_change_email_title)
                .setView(view)
                .setPositiveButton(R.string.action_save, (dialog, which) ->
                        viewModel.updateEmail(etEmail.getText().toString(), etPassword.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmLogout() {
        OrganicConfirmDialog.show(this, getString(R.string.settings_dialog_logout_title),
                getString(R.string.settings_dialog_logout_message), getString(R.string.settings_action_logout),
                getString(R.string.action_cancel), false, viewModel::logout);
    }

    private void confirmDeactivate() {
        OrganicConfirmDialog.show(this, getString(R.string.settings_dialog_deactivate_title),
                getString(R.string.settings_dialog_deactivate_message), getString(R.string.settings_action_deactivate),
                getString(R.string.action_cancel), true, viewModel::deactivateAccount);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
