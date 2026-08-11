package com.cooksync.app.ui.settings;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.base.BaseViewModel;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.base.ViewModelFactory;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
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
import com.cooksync.app.data.datasource.local.CookingPreferencesStore;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.admin.AdminConsoleActivity;
import com.cooksync.app.ui.base.BaseActivity;
import com.cooksync.app.ui.common.FullscreenImageActivity;
import com.cooksync.app.ui.base.Navigator;
import com.cooksync.app.ui.common.OrganicConfirmDialog;
import com.cooksync.app.ui.common.OrganicToast;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.home.HomeActivity;
import com.cooksync.app.ui.recipe.favorites.FavoriteRecipesActivity;
import com.cooksync.app.ui.recipe.myrecipes.MyRecipesActivity;
import com.cooksync.app.util.CloudinaryUploader;
import com.cooksync.app.util.SessionManager;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.recipe.RecipePreviewResponse;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;
import java.util.Objects;

/**
 * The "Settings" tab: a hub of navigational rows (Favorites, My recipes, Notifications, Cooking
 * preferences, Account details, and, for admins, Admin console), plus the avatar header and log
 * out. Uses the same shared {@link com.dtos.response.auth.AuthResponse}-backed session cache
 * ({@link SessionManager}) that every other screen reads for the avatar chip's initials, so a
 * successful edit here is immediately reflected everywhere else on the next visit.
 *
 * <p>Avatar uploads go directly from this device to Cloudinary using a short-lived signature
 * fetched from the server (see {@link com.cooksync.app.data.repository.MediaRepository}), and
 * only the resulting URL is sent to CookSync's own server — the binary image never passes
 * through the application backend.</p>
 *
 * <p>Name/city/bio/email/password/privacy editing and account deletion now live on the
 * dedicated {@link AccountDetailsActivity} screen, reached via the "Account details" row.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class SettingsActivity extends BaseActivity {

    /** Intent extra: a one-shot success message to show once this screen is resumed. */
    public static final String EXTRA_PENDING_TOAST = "extra_pending_toast";

    private SettingsViewModel viewModel;

    private ImageView ivAvatar;
    private TextView tvAvatarInitials;
    private ProgressBar avatarProgress;
    private TextView tvName;
    private TextView tvEmail;
    private TextView tvFavoritesSub;
    private TextView tvMyRecipesSub;
    private TextView tvCookingSub;

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(SettingsViewModel.class);

        bindViews();
        renderCachedProfile();
        setupBottomNav();
        setupRows();
        setupObservers();

        findViewById(R.id.btn_logout).setOnClickListener(v -> confirmLogout());
        findViewById(R.id.fab_add_recipe).setOnClickListener(v ->
                Navigator.start(this, com.cooksync.app.ui.recipe.wizard.AddRecipeWizardActivity.class));

        viewModel.loadFavoritesCount();
        viewModel.loadMyRecipesCount();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderCachedProfile();
        refreshCookingPreferencesSub();
        viewModel.loadFavoritesCount();
        viewModel.loadMyRecipesCount();
        showPendingToastIfAny();
    }

    /**
     * Shows and consumes a one-shot success message passed via {@link #EXTRA_PENDING_TOAST},
     * e.g. from {@link AccountDetailsActivity} after a successful save. {@link OrganicToast}
     * can't outlive the activity it's anchored to, so this is how a save on one screen shows its
     * confirmation on the screen the user actually lands on. Removed from the intent immediately
     * so it isn't re-shown on a later {@code onResume} (rotation, returning from another tab).
     */
    private void showPendingToastIfAny() {
        String message = getIntent().getStringExtra(EXTRA_PENDING_TOAST);
        if (message != null) {
            getIntent().removeExtra(EXTRA_PENDING_TOAST);
            showSuccess(message, bottomNav);
        }
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
        tvCookingSub = bindRow(R.id.row_cooking_preferences, R.drawable.ic_smartphone,
                getString(R.string.settings_row_cooking_preferences_label), null,
                v -> Navigator.start(this, CookingPreferencesActivity.class));
        refreshCookingPreferencesSub();

        bindRow(R.id.row_account_details, R.drawable.ic_user_cog,
                getString(R.string.settings_row_account_details_label), getString(R.string.settings_row_account_details_sub),
                v -> Navigator.start(this, AccountDetailsActivity.class));

        View adminRow = findViewById(R.id.row_admin_console);
        boolean isAdmin = SessionManager.getInstance().isAdmin();
        adminRow.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        if (isAdmin) {
            bindRow(R.id.row_admin_console, R.drawable.ic_shield,
                    getString(R.string.settings_row_admin_console_label), getString(R.string.settings_row_admin_console_sub),
                    v -> Navigator.start(this, AdminConsoleActivity.class));
        }

        LegalLinkSpanner.apply(findViewById(R.id.tv_legal_links), this, R.string.settings_footer_legal_links);
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

    /**
     * Re-reads {@link CookingPreferencesStore} and updates the "Cooking preferences" row's
     * subtitle to match, since the toggles it summarizes are edited on
     * {@link CookingPreferencesActivity} and only reflected here once the user navigates back.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     */
    private void refreshCookingPreferencesSub() {
        tvCookingSub.setText(CookingPreferencesStore.isScreenAwakeEnabled()
                ? R.string.settings_row_cooking_preferences_sub_on
                : R.string.settings_row_cooking_preferences_sub_off);
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

    private void confirmLogout() {
        OrganicConfirmDialog.show(this, getString(R.string.settings_dialog_logout_title),
                getString(R.string.settings_dialog_logout_message), getString(R.string.settings_action_logout),
                getString(R.string.action_cancel), false, viewModel::logout);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
