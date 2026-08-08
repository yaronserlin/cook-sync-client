package com.cooksync.app.data.remote;

import com.dtos.request.auth.AvatarUpdateRequestDTO;
import com.dtos.request.auth.ChangePasswordRequestDTO;
import com.dtos.request.auth.EmailUpdateRequestDTO;
import com.dtos.request.auth.ForgotPasswordRequestDTO;
import com.dtos.request.auth.LoginRequestDTO;
import com.dtos.request.auth.ProfileUpdateRequestDTO;
import com.dtos.request.auth.RegisterRequestDTO;
import com.dtos.request.auth.ResetPasswordRequestDTO;
import com.dtos.request.auth.TokenRefreshRequestDTO;
import com.dtos.request.tags.TagMergeRequestDTO;
import com.dtos.response.ApiResponse;
import com.dtos.response.PagedResponse;
import com.dtos.response.admin.AdminStatsResponse;
import com.dtos.response.admin.DuplicateTagGroupResponse;
import com.dtos.response.admin.ReportedReviewResponse;
import com.dtos.response.auth.AuthResponse;
import com.dtos.response.cloudinary.CloudinarySignatureResponse;
import com.dtos.response.user.UserResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

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
     * Requests a password-reset email for the given account, if it exists.
     *
     * @param request forgot-password payload
     * @return call yielding an empty acknowledgement
     */
    @POST("api/auth/forgot-password")
    Call<ApiResponse<Void>> forgotPassword(@Body ForgotPasswordRequestDTO request);

    /**
     * Completes a password reset using a token issued via {@link #forgotPassword}.
     *
     * @param request reset-password payload
     * @return call yielding an empty acknowledgement
     */
    @POST("api/auth/reset-password")
    Call<ApiResponse<Void>> resetPassword(@Body ResetPasswordRequestDTO request);

    /**
     * Fetches a short-lived signed payload the client uses to upload media directly to
     * Cloudinary, bypassing the application server for the binary transfer itself.
     *
     * @return call yielding Cloudinary upload credentials
     */
    @GET("api/cloudinary/signature")
    Call<ApiResponse<CloudinarySignatureResponse>> getMediaSignature();

    // ── Recipe Feed & Discovery ────────────────────────────────────

    /**
     * Fetches a paginated list of public recipe previews for the home feed.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of recipe previews
     */
    @GET("api/recipes/public/paged")
    Call<ApiResponse<com.dtos.response.PagedResponse<com.dtos.response.recipe.RecipePreviewResponse>>> getPublicFeed(
            @retrofit2.http.Query("page") int page,
            @retrofit2.http.Query("size") int size
    );

    /**
     * Searches for public recipes matching a text query, author, or ingredient.
     *
     * @param query search text
     * @param author optional author name filter
     * @param ingredient optional ingredient name filter
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of matching recipe previews
     */
    @GET("api/recipes/public/search")
    Call<ApiResponse<PagedResponse<com.dtos.response.recipe.RecipePreviewResponse>>> searchRecipes(
            @retrofit2.http.Query("q") String query,
            @retrofit2.http.Query("author") String author,
            @retrofit2.http.Query("ingredient") String ingredient,
            @retrofit2.http.Query("page") int page,
            @retrofit2.http.Query("size") int size
    );

    /**
     * Fetches public recipes associated with a specific tag.
     *
     * @param tagName the name of the tag to filter by
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of recipe previews
     */
    @GET("api/recipes/public/tag/{tagName}")
    Call<ApiResponse<PagedResponse<com.dtos.response.recipe.RecipePreviewResponse>>> getRecipesByTag(
            @retrofit2.http.Path("tagName") String tagName,
            @retrofit2.http.Query("page") int page,
            @retrofit2.http.Query("size") int size
    );

    /**
     * Fetches the complete details for a specific recipe.
     *
     * @param id the unique identifier of the recipe
     * @return call yielding the full recipe detail
     */
    @GET("api/recipes/public/{id}")
    Call<ApiResponse<com.dtos.response.recipe.RecipeResponse>> getRecipeDetail(
            @retrofit2.http.Path("id") String id
    );

    /**
     * Fetches every recipe (published or private) authored by the currently authenticated
     * user, for the "My Recipes" screen.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of the user's own recipes
     */
    @GET("api/recipes/mine")
    Call<ApiResponse<PagedResponse<com.dtos.response.recipe.RecipePreviewResponse>>> getMyRecipes(
            @retrofit2.http.Query("page") int page,
            @retrofit2.http.Query("size") int size
    );

    /**
     * Deletes one of the authenticated user's own recipes.
     *
     * @param id the ID of the recipe to delete
     * @return call yielding an empty acknowledgement
     */
    @retrofit2.http.DELETE("api/recipes/{id}")
    Call<ApiResponse<Void>> deleteRecipe(@retrofit2.http.Path("id") String id);

    /**
     * Changes only a recipe's visibility (Public/Private) without resubmitting the rest of
     * its fields.
     *
     * @param id the ID of the recipe to update
     * @param request the new visibility
     * @return call yielding the updated recipe
     */
    @PATCH("api/recipes/{id}/visibility")
    Call<ApiResponse<com.dtos.response.recipe.RecipeResponse>> updateRecipeVisibility(
            @retrofit2.http.Path("id") String id,
            @Body com.dtos.request.recipe.RecipeVisibilityUpdateRequestDTO request
    );

    // ── Tags ───────────────────────────────────────────────────────

    /**
     * Fetches a page of available tags for the horizontal filter bar.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of tags
     */
    @GET("api/tags")
    Call<ApiResponse<PagedResponse<com.dtos.response.tags.TagResponse>>> getAllTags(
            @retrofit2.http.Query("page") int page,
            @retrofit2.http.Query("size") int size
    );

    // ── Favorites ──────────────────────────────────────────────────

    /**
     * Fetches a page of recipes favorited by the currently authenticated user.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of the user's favorites
     */
    @GET("api/favorites")
    Call<ApiResponse<PagedResponse<com.dtos.response.recipe.RecipePreviewResponse>>> getFavorites(
            @retrofit2.http.Query("page") int page,
            @retrofit2.http.Query("size") int size
    );

    /**
     * Adds a recipe to the user's favorites list.
     *
     * @param recipeId the ID of the recipe to favorite
     * @return call yielding an empty response
     */
    @POST("api/favorites/{recipeId}")
    Call<ApiResponse<Void>> addFavorite(@retrofit2.http.Path("recipeId") String recipeId);

    /**
     * Removes a recipe from the user's favorites list.
     *
     * @param recipeId the ID of the recipe to unfavorite
     * @return call yielding an empty response
     */
    @retrofit2.http.DELETE("api/favorites/{recipeId}")
    Call<ApiResponse<Void>> removeFavorite(@retrofit2.http.Path("recipeId") String recipeId);

    // ── Personal Notes ─────────────────────────────────────────────

    /**
     * Fetches the private personal note attached by the user to a specific recipe.
     *
     * @param recipeId the ID of the recipe
     * @return call yielding the personal note, if any
     */
    @GET("api/notes/recipe/{recipeId}")
    Call<ApiResponse<com.dtos.response.note.NoteResponse>> getPersonalNote(
            @retrofit2.http.Path("recipeId") String recipeId
    );

    /**
     * Fetches every private note the user has attached to a recipe, both the general
     * recipe-wide note and any notes attached to individual instruction steps
     * (distinguished by {@link com.dtos.response.note.NoteResponse#instructionId()} being
     * non-null). Used by Cooking Mode to show the right note alongside each step.
     *
     * @param recipeId the ID of the recipe
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of every note (general + per-step) for the recipe
     */
    @GET("api/notes/recipe/{recipeId}/all")
    Call<ApiResponse<PagedResponse<com.dtos.response.note.NoteResponse>>> getAllPersonalNotes(
            @retrofit2.http.Path("recipeId") String recipeId,
            @retrofit2.http.Query("page") int page,
            @retrofit2.http.Query("size") int size
    );

    /**
     * Creates or updates a personal note on a recipe (when {@code instructionId} is null) or
     * on a specific instruction step (when it's set).
     *
     * @param request the note payload
     * @return call yielding an empty acknowledgement
     */
    @POST("api/notes")
    Call<ApiResponse<Void>> saveNote(@Body com.dtos.request.note.NoteRequestDTO request);

    /**
     * Deletes a personal note.
     *
     * @param noteId the ID of the note to delete
     * @return call yielding an empty acknowledgement
     */
    @retrofit2.http.DELETE("api/notes/{noteId}")
    Call<ApiResponse<Void>> deleteNote(@retrofit2.http.Path("noteId") String noteId);

    // ── Reviews ───────────────────────────────────────────────────

    /**
     * Submits a new rating/review for a recipe.
     *
     * @param recipeId the ID of the recipe being reviewed
     * @param request the review payload (rating, title, optional comment)
     * @return call yielding an empty acknowledgement
     */
    @POST("api/recipes/{recipeId}/reviews")
    Call<ApiResponse<Void>> submitReview(
            @retrofit2.http.Path("recipeId") String recipeId,
            @Body com.dtos.request.review.ReviewRequestDTO request
    );

    /**
     * Deletes a review the current user authored.
     *
     * @param reviewId the ID of the review to delete
     * @return call yielding an empty acknowledgement
     */
    @retrofit2.http.DELETE("api/reviews/{reviewId}")
    Call<ApiResponse<Void>> deleteReview(@retrofit2.http.Path("reviewId") String reviewId);

    /**
     * Flags a review for moderator review.
     *
     * @param reviewId the ID of the review being reported
     * @param request the report payload (reason + optional comment)
     * @return call yielding an empty acknowledgement
     */
    @POST("api/reviews/{reviewId}/report")
    Call<ApiResponse<Void>> reportReview(
            @retrofit2.http.Path("reviewId") String reviewId,
            @Body com.dtos.request.review.ReportReviewRequestDTO request
    );

    // ── Admin ─────────────────────────────────────────────────────

    /**
     * Fetches system-wide moderation/content statistics for the Admin Console header.
     *
     * @return call yielding the admin dashboard stats
     */
    @GET("api/admin/stats")
    Call<ApiResponse<AdminStatsResponse>> getAdminStats();

    /**
     * Fetches a paginated, searchable, sortable list of every registered user, for the Admin
     * Console's Users tab.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @param q optional search text matched against name/email, or {@code null}
     * @param enabled optional filter by account status, or {@code null} for both
     * @param sortBy one of "firstName", "lastName", "email", "createdAt"
     * @param direction "asc" or "desc"
     * @return call yielding a paged collection of user summaries
     */
    @GET("api/admin/users")
    Call<ApiResponse<PagedResponse<UserResponse>>> getAdminUsers(
            @Query("page") int page,
            @Query("size") int size,
            @Query("q") String q,
            @Query("enabled") Boolean enabled,
            @Query("sortBy") String sortBy,
            @Query("direction") String direction
    );

    /**
     * Fetches a paginated page of reviews currently flagged for moderation, for the Admin
     * Console's Reports tab.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of reported reviews
     */
    @GET("api/admin/reviews/reported")
    Call<ApiResponse<PagedResponse<ReportedReviewResponse>>> getReportedReviews(
            @Query("page") int page,
            @Query("size") int size
    );

    /**
     * Dismisses a review's report(s) without deleting the review itself (the "Keep" action).
     *
     * @param reviewId the ID of the reported review
     * @return call yielding an empty acknowledgement
     */
    @POST("api/admin/reviews/{id}/dismiss")
    Call<ApiResponse<Void>> dismissReport(@Path("id") String reviewId);

    /**
     * Re-enables a previously disabled user account.
     *
     * @param userId the ID of the user to enable
     * @return call yielding an empty acknowledgement
     */
    @PATCH("api/admin/users/{id}/enable")
    Call<ApiResponse<Void>> enableUser(@Path("id") String userId);

    /**
     * Disables a user account, blocking sign-in (the "ban user" action).
     *
     * @param userId the ID of the user to disable
     * @return call yielding an empty acknowledgement
     */
    @PATCH("api/admin/users/{id}/disable")
    Call<ApiResponse<Void>> disableUser(@Path("id") String userId);

    /**
     * Fetches a paginated page of tags that appear to be duplicates of one another, for the
     * Admin Console's Tags tab.
     *
     * @param page 0-based page index
     * @param size number of items per page
     * @return call yielding a paged collection of duplicate tag groups
     */
    @GET("api/admin/tags/duplicates")
    Call<ApiResponse<PagedResponse<DuplicateTagGroupResponse>>> getDuplicateTagGroups(
            @Query("page") int page,
            @Query("size") int size
    );

    /**
     * Merges a duplicate tag into a canonical target tag, repointing every recipe that used
     * the source tag and deleting the source tag row.
     *
     * @param request the source/target tag id pair
     * @return call yielding an empty acknowledgement
     */
    @POST("api/admin/tags/merge")
    Call<ApiResponse<Void>> mergeTags(@Body TagMergeRequestDTO request);
}
