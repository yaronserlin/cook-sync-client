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
     * @return call yielding a list of matching recipe previews
     */
    @GET("api/recipes/public/search")
    Call<ApiResponse<java.util.List<com.dtos.response.recipe.RecipePreviewResponse>>> searchRecipes(
            @retrofit2.http.Query("q") String query,
            @retrofit2.http.Query("author") String author,
            @retrofit2.http.Query("ingredient") String ingredient
    );

    /**
     * Fetches public recipes associated with a specific tag.
     *
     * @param tagName the name of the tag to filter by
     * @return call yielding a list of recipe previews
     */
    @GET("api/recipes/public/tag/{tagName}")
    Call<ApiResponse<java.util.List<com.dtos.response.recipe.RecipePreviewResponse>>> getRecipesByTag(
            @retrofit2.http.Path("tagName") String tagName
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
     * @return call yielding the user's own recipes
     */
    @GET("api/recipes/mine")
    Call<ApiResponse<java.util.List<com.dtos.response.recipe.RecipePreviewResponse>>> getMyRecipes();

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
     * Fetches all available tags for the horizontal filter bar.
     *
     * @return call yielding the list of all tags
     */
    @GET("api/tags")
    Call<ApiResponse<java.util.List<com.dtos.response.tags.TagResponse>>> getAllTags();

    // ── Favorites ──────────────────────────────────────────────────

    /**
     * Fetches the list of recipes favorited by the currently authenticated user.
     *
     * @return call yielding the list of user's favorites
     */
    @GET("api/favorites")
    Call<ApiResponse<java.util.List<com.dtos.response.recipe.RecipePreviewResponse>>> getFavorites();

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
     * @return call yielding every note (general + per-step) for the recipe
     */
    @GET("api/notes/recipe/{recipeId}/all")
    Call<ApiResponse<java.util.List<com.dtos.response.note.NoteResponse>>> getAllPersonalNotes(
            @retrofit2.http.Path("recipeId") String recipeId
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
}
