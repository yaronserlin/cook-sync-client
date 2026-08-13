package com.cooksync.app.data.datasource.remote;

import com.cooksync.app.data.datasource.local.TokenStore;

import java.io.IOException;

import androidx.annotation.NonNull;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * OkHttp {@link Interceptor} that stamps every outgoing request with the current JWT access
 * token, except for the handful of endpoints that must remain reachable without one
 * (login, register, the registration OTP verify/resend endpoints — no session exists yet at
 * that stage — and the refresh-token exchange itself, since attaching a possibly-expired token
 * to that call would be pointless and could mask the real 401 reason).
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public class AuthInterceptor implements Interceptor {

    private static final String[] PUBLIC_PATH_SUFFIXES = {
            "api/auth/login",
            "api/auth/register",
            "api/auth/refresh-token",
            "api/auth/verify-registration-otp",
            "api/auth/resend-registration-otp"
    };

    /**
     * Attaches the {@code Authorization: Bearer <token>} header to authenticated requests.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param chain the interceptor chain providing the outgoing request
     * @return the response produced by proceeding down the chain
     * @throws IOException if the underlying call fails
     */
    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();

        if (isPublicEndpoint(original)) {
            return chain.proceed(original);
        }

        String accessToken = TokenStore.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            return chain.proceed(original);
        }

        Request authenticated = original.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .build();
        return chain.proceed(authenticated);
    }

    /**
     * Determines whether a request targets an endpoint that must not carry an access token.
     *
     * Complexity:
     * Time: O(k) where k is the number of public endpoint suffixes (constant)
     * Space: O(1)
     *
     * @param request the outgoing request
     * @return {@code true} if the request path matches a public endpoint
     */
    private boolean isPublicEndpoint(Request request) {
        String path = request.url().encodedPath();
        for (String suffix : PUBLIC_PATH_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
