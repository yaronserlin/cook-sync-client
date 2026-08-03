package com.cooksync.app.data.remote;

import com.cooksync.app.data.local.TokenStore;
import com.cooksync.app.util.SessionManager;
import com.dtos.request.auth.TokenRefreshRequestDTO;
import com.dtos.response.ApiResponse;
import com.dtos.response.auth.AuthResponse;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;

/**
 * OkHttp {@link Authenticator} implementing transparent JWT refresh: when any request comes
 * back {@code 401 Unauthorized}, this class exchanges the stored refresh token for a new
 * access/refresh token pair and OkHttp automatically retries the original request with the
 * new credentials — callers never see the 401 at all.
 *
 * <p>Refresh attempts are serialized via a monitor lock so that if several requests fail
 * with 401 concurrently, only one of them actually calls {@code /api/auth/refresh-token};
 * the others simply pick up the token that call already installed.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class TokenAuthenticator implements Authenticator {

    private static final int MAX_RETRY_ATTEMPTS = 1;

    private final ApiService refreshApiService;

    /**
     * Constructs the authenticator with a dedicated {@link ApiService} used solely for the
     * refresh-token call. This service must be built WITHOUT {@link AuthInterceptor} or this
     * same authenticator attached, to avoid recursively triggering another refresh.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param refreshApiService a bare API service pointed at the same base URL
     */
    public TokenAuthenticator(ApiService refreshApiService) {
        this.refreshApiService = refreshApiService;
    }

    /**
     * Refreshes the access token and retries the failed request, or gives up and forces a
     * logout if the refresh token itself is invalid.
     *
     * Complexity:
     * Time: O(1) plus one synchronous network round-trip on the first 401 in a burst
     * Space: O(1)
     *
     * @param route the failed request's route, unused
     * @param response the 401 response that triggered this authenticator
     * @return a rebuilt request carrying the new access token, or {@code null} to give up
     * @throws IOException if reading the response fails
     */
    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        if (responseCount(response) > MAX_RETRY_ATTEMPTS) {
            return null;
        }

        String tokenUsedByFailedRequest = response.request().header("Authorization");

        synchronized (this) {
            String currentToken = TokenStore.getAccessToken();
            String currentBearer = currentToken == null ? null : "Bearer " + currentToken;

            // Another thread already refreshed while we were waiting for the lock.
            if (currentBearer != null && !currentBearer.equals(tokenUsedByFailedRequest)) {
                return rebuildWithToken(response.request(), currentToken);
            }

            String refreshToken = TokenStore.getRefreshToken();
            if (refreshToken == null || refreshToken.isEmpty()) {
                SessionManager.getInstance().forceLogout();
                return null;
            }

            Call<ApiResponse<AuthResponse>> call =
                    refreshApiService.refreshToken(new TokenRefreshRequestDTO(refreshToken));

            retrofit2.Response<ApiResponse<AuthResponse>> httpResponse;
            try {
                httpResponse = call.execute();
            } catch (IOException e) {
                // Network failure during refresh: do not wipe the session, just give up on
                // this retry — the user stays logged out of this one request only.
                return null;
            }

            if (!httpResponse.isSuccessful() || httpResponse.body() == null || !httpResponse.body().success()) {
                SessionManager.getInstance().forceLogout();
                return null;
            }

            AuthResponse renewed = httpResponse.body().data();
            SessionManager.getInstance().startSession(renewed);
            return rebuildWithToken(response.request(), renewed.token());
        }
    }

    /**
     * Rebuilds a request with a fresh {@code Authorization} header.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param original the request that originally failed
     * @param accessToken the new access token to attach
     * @return the rebuilt request
     */
    private Request rebuildWithToken(Request original, String accessToken) {
        return original.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .build();
    }

    /**
     * Counts how many times this request chain has already been retried, walking the
     * {@code priorResponse} links, to guarantee this authenticator cannot loop forever.
     *
     * Complexity:
     * Time: O(n) where n is the number of prior responses (bounded by {@link #MAX_RETRY_ATTEMPTS})
     * Space: O(1)
     *
     * @param response the current response
     * @return the number of prior chained responses
     */
    private int responseCount(Response response) {
        int count = 1;
        Response prior = response.priorResponse();
        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }
        return count;
    }
}
