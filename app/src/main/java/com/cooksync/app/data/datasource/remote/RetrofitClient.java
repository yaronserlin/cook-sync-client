package com.cooksync.app.data.datasource.remote;

import com.cooksync.app.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton factory for {@link ApiService} instances. Builds two distinct OkHttp clients:
 * <ol>
 *   <li><b>Authenticated client</b> — carries {@link AuthInterceptor} (stamps the
 *       {@code Authorization} header) and {@link TokenAuthenticator} (transparently
 *       refreshes expired access tokens on a 401). This is the client used by the vast
 *       majority of the application.</li>
 *   <li><b>Bare client</b> — no auth headers, no authenticator. Used exclusively inside
 *       {@link TokenAuthenticator} to call {@code POST /api/auth/refresh-token} without
 *       triggering a recursive refresh cycle.</li>
 * </ol>
 *
 * <p>HTTP logging is enabled only in debug builds ({@link BuildConfig#DEBUG}) so production
 * releases never leak token values into logcat.</p>
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public final class RetrofitClient {

    private static volatile ApiService apiService;
    private static volatile ApiService bareApiService;

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private RetrofitClient() {
    }

    /**
     * Returns the shared, authenticated {@link ApiService}. Lazily constructed once (double-
     * checked locking). The instance is shared across all callers in the process.
     *
     * Complexity:
     * Time: O(1) after first call
     * Space: O(1)
     *
     * @return the authenticated API service
     */
    public static ApiService getInstance() {
        if (apiService == null) {
            synchronized (RetrofitClient.class) {
                if (apiService == null) {
                    apiService = buildAuthenticatedService();
                }
            }
        }
        return apiService;
    }

    /**
     * Builds the authenticated Retrofit service, pairing {@link AuthInterceptor} with
     * {@link TokenAuthenticator}. The authenticator receives a bare service so it can
     * call the refresh endpoint without re-entering itself.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return a configured {@link ApiService} with JWT support
     */
    private static ApiService buildAuthenticatedService() {
        ApiService bare = buildBareService();

        OkHttpClient client = baseClientBuilder()
                .addInterceptor(new AuthInterceptor())
                .authenticator(new TokenAuthenticator(bare))
                .build();

        return buildRetrofit(client).create(ApiService.class);
    }

    /**
     * Returns (or lazily creates) the bare, unauthenticated {@link ApiService} used solely
     * inside {@link TokenAuthenticator} for token-refresh calls.
     *
     * Complexity:
     * Time: O(1) after first call
     * Space: O(1)
     *
     * @return a bare API service without auth headers or authenticator
     */
    public static ApiService getBareService() {
        if (bareApiService == null) {
            synchronized (RetrofitClient.class) {
                if (bareApiService == null) {
                    bareApiService = buildBareService();
                }
            }
        }
        return bareApiService;
    }

    /**
     * Builds a Retrofit instance backed by a plain OkHttp client with no auth logic.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return a bare {@link ApiService}
     */
    private static ApiService buildBareService() {
        return buildRetrofit(baseClientBuilder().build()).create(ApiService.class);
    }

    /**
     * Returns a pre-configured {@link OkHttpClient.Builder} shared by both client variants.
     * Attaches an HTTP logging interceptor in DEBUG builds only.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @return a partially configured builder
     */
    private static OkHttpClient.Builder baseClientBuilder() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);
            builder.addInterceptor(logging);
        }
        return builder;
    }

    /**
     * Builds a {@link Retrofit} instance pointed at {@link BuildConfig#BASE_URL}, using
     * Gson for JSON (de)serialization.
     *
     * Complexity:
     * Time: O(1)
     * Space: O(1)
     *
     * @param client the configured OkHttp client to attach
     * @return a fully built Retrofit instance
     */
    private static Retrofit buildRetrofit(OkHttpClient client) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(GSON))
                .build();
    }
}
