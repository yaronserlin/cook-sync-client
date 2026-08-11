package com.cooksync.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.data.repository.AuthRepository;
import com.cooksync.app.domain.ApiResult;
import com.cooksync.app.ui.base.BaseViewModel;
import com.dtos.response.user.UserResponse;

/**
 * ViewModel for {@link UserProfileDialogFragment}. Fetches another user's public profile
 * (avatar, name, city, bio) by ID for read-only display.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 11/08/2026
 */
public class UserProfileViewModel extends BaseViewModel {

    private final AuthRepository authRepository;

    private final MutableLiveData<ApiResult<UserResponse>> profileResult = new MutableLiveData<>();

    /**
     * Constructs the ViewModel with the given {@link AuthRepository}, injected by
     * {@link com.cooksync.app.ui.base.ViewModelFactory}.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param authRepository the repository used to fetch the target user's public profile
     */
    public UserProfileViewModel(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    /**
     * Loads the public profile of the given user.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param userId the ID of the user whose profile should be fetched
     */
    public void loadProfile(String userId) {
        authRepository.getUserProfile(userId, profileResult);
    }

    /** @return observable public-profile fetch result (Loading → Success/Error) */
    public LiveData<ApiResult<UserResponse>> getProfileResult() { return profileResult; }
}
