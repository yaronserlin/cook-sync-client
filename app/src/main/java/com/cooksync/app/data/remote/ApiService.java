package com.cooksync.app.data.remote;

import com.dtos.request.auth.AvatarUpdateRequestDTO;
import com.dtos.request.auth.ChangePasswordRequestDTO;
import com.dtos.request.auth.EmailUpdateRequestDTO;
import com.dtos.request.auth.LoginRequestDTO;
import com.dtos.request.auth.ProfileUpdateRequestDTO;
import com.dtos.request.auth.RegisterRequestDTO;
import com.dtos.request.auth.TokenRefreshRequestDTO;
import com.dtos.response.ApiResponse;
import com.dtos.response.auth.AuthResponse;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;

/**
 * Retrofit contract for every REST endpoint this module (core networking + authentication)
 * depends on. Endpoint paths and payload shapes mirror {@code AuthController} on
 * cook-sync-server exactly, since both sides share the same DTOs from the
 * {@code cooksync-DTOs} artifact. Additional feature areas (recipes, reviews, search, ...)
 * will extend this interface in later modules.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public interface ApiService {

    /**
     * Registers a new user account.
     *
     * @param request registration payload
     * @return call yielding the newly created session
     */
    @POST("api/auth/register")
    Call<ApiResponse<AuthResponse>> register(@Body RegisterRequestDTO request);

    /**
     * Authenticates an existing user with email and password.
     *
     * @param request login credentials payload
     * @return call yielding the authenticated session
     */
    @POST("api/auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body LoginRequestDTO request);

    /**
     * Exchanges a refresh token for a new access/refresh token pair. Invoked exclusively
     * by {@link TokenAuthenticator} in response to a 401 on some other request.
     *
     * @param request refresh token payload
     * @return call yielding the renewed session
     */
    @POST("api/auth/refresh-token")
    Call<ApiResponse<AuthResponse>> refreshToken(@Body TokenRefreshRequestDTO request);

    /**
     * Validates the current access token and returns the associated user profile.
     *
     * @return call yielding the current session's profile
     */
    @GET("api/auth/validate-token")
    Call<ApiResponse<AuthResponse>> validateToken();

    /**
     * Invalidates the current refresh token session on the server.
     *
     * @return call yielding an empty acknowledgement
     */
    @POST("api/auth/logout")
    Call<ApiResponse<Void>> logout();

    /**
     * Updates the authenticated user's avatar URL.
     *
     * @param request avatar update payload
     * @return call yielding an empty acknowledgement
     */
    @PUT("api/auth/avatar")
    Call<ApiResponse<Void>> updateAvatar(@Body AvatarUpdateRequestDTO request);

    /**
     * Updates the authenticated user's first/last name.
     *
     * @param request profile update payload
     * @return call yielding an empty acknowledgement
     */
    @PUT("api/auth/profile")
    Call<ApiResponse<Void>> updateProfile(@Body ProfileUpdateRequestDTO request);

    /**
     * Changes the authenticated user's password.
     *
     * @param request password change payload
     * @return call yielding an empty acknowledgement
     */
    @PUT("api/auth/password")
    Call<ApiResponse<Void>> changePassword(@Body ChangePasswordRequestDTO request);

    /**
     * Changes the authenticated user's email address, re-issuing tokens for the new identity.
     *
     * @param request email update payload
     * @return call yielding the renewed session
     */
    @PUT("api/auth/email")
    Call<ApiResponse<AuthResponse>> updateEmail(@Body EmailUpdateRequestDTO request);

    /**
     * Deactivates the authenticated user's account.
     *
     * @return call yielding an empty acknowledgement
     */
    @PATCH("api/auth/deactivate")
    Call<ApiResponse<Void>> deactivateAccount();

    /**
     * Fetches a short-lived signed payload the client uses to upload media directly to
     * Cloudinary, bypassing the application server for the binary transfer itself.
     *
     * @return call yielding Cloudinary upload credentials
     */
    @GET("api/media/signature")
    Call<ApiResponse<CloudinarySignatureResponse>> getMediaSignature();
}
