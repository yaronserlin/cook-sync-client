package com.cooksync.app.ui.auth;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.cooksync.app.R;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.ViewModelFactory;
import com.cooksync.app.ui.common.AvatarView;
import com.dtos.response.user.UserResponse;

/**
 * Centered Dialog Fragment displaying another user's public profile (avatar, name, city, bio).
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 10/08/2026
 */
public class UserProfileDialogFragment extends DialogFragment {

    private static final String ARG_USER_ID = "user_id";
    private static final String ARG_USER_NAME = "user_name";

    private AvatarView avatarView;
    private TextView tvFullName;
    private TextView tvCity;
    private TextView tvBio;
    private ProgressBar progressBar;

    private UserProfileViewModel viewModel;

    /**
     * Creates and shows the dialog for the given user.
     *
     * @param fragmentManager fragment manager to show the dialog with
     * @param userId          ID of the user whose profile should be displayed
     * @param userName        display name to show immediately while the full profile loads, or
     *                        {@code null} if unknown
     */
    public static void show(@NonNull FragmentManager fragmentManager, String userId, @Nullable String userName) {
        UserProfileDialogFragment fragment = new UserProfileDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_USER_ID, userId);
        if (userName != null) {
            args.putString(ARG_USER_NAME, userName);
        }
        fragment.setArguments(args);
        fragment.show(fragmentManager, "user_profile_dialog");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_user_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        avatarView = view.findViewById(R.id.avatar_view);
        tvFullName = view.findViewById(R.id.tv_full_name);
        tvCity = view.findViewById(R.id.tv_city);
        tvBio = view.findViewById(R.id.tv_bio);
        progressBar = view.findViewById(R.id.progress_bar);

        Bundle args = getArguments();
        String userId = args != null ? args.getString(ARG_USER_ID) : null;
        String initialName = args != null ? args.getString(ARG_USER_NAME) : null;

        if (initialName != null && !initialName.isBlank()) {
            tvFullName.setText(initialName);
            avatarView.setAvatar(null, initialName);
        }

        viewModel = new ViewModelProvider(this, new ViewModelFactory()).get(UserProfileViewModel.class);

        if (userId != null && !userId.isBlank()) {
            observeProfile();
            viewModel.loadProfile(userId);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void observeProfile() {
        viewModel.getProfileResult().observe(getViewLifecycleOwner(), result -> {
            if (result instanceof ApiResult.Loading) {
                progressBar.setVisibility(View.VISIBLE);
            } else if (result instanceof ApiResult.Success<UserResponse> success) {
                progressBar.setVisibility(View.GONE);
                renderUser(success.getData());
            } else if (result instanceof ApiResult.Error<?> error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void renderUser(UserResponse user) {
        String fullName = (user.firstName() + " " + user.lastName()).trim();
        if (fullName.isEmpty()) fullName = getString(R.string.anonymous);

        tvFullName.setText(fullName);
        avatarView.setAvatar(user.avatarUrl(), fullName);

        if (user.city() != null && !user.city().isBlank()) {
            tvCity.setText(user.city());
            tvCity.setVisibility(View.VISIBLE);
        } else {
            tvCity.setVisibility(View.GONE);
        }

        if (user.bio() != null && !user.bio().isBlank()) {
            tvBio.setText(user.bio());
        } else {
            tvBio.setText(R.string.user_profile_no_bio);
        }
    }
}
