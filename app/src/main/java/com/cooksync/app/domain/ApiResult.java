package com.cooksync.app.domain;

/**
 * Generic, closed hierarchy representing the outcome of any repository operation, whether
 * it is backed by a network call, local cache read, or a combination of both. Every
 * repository method in the app (Auth, Recipes, Reviews, ...) returns {@code ApiResult<T>}
 * regardless of the payload type {@code T}, so ViewModels can handle success/error/loading
 * uniformly via a single {@code switch}-like {@code instanceof} check instead of bespoke
 * callback interfaces per feature.
 *
 * @param <T> the type of the successful payload
 * @author Yaron Serlin
 * @version 1.0
 * @since 04/08/2026
 */
public abstract class ApiResult<T> {

    private ApiResult() {
    }

    /**
     * Successful outcome carrying the resulting payload.
     *
     * @param <T> the type of the payload
     */
    public static final class Success<T> extends ApiResult<T> {
        private final T data;

        /**
         * Constructs a successful result.
         *
         * Complexity:
         * Time: O(1)
         * Space: O(1)
         *
         * @param data the payload returned by the operation
         */
        public Success(T data) {
            this.data = data;
        }

        /**
         * Returns the successful payload.
         *
         * Complexity:
         * Time: O(1)
         * Space: O(1)
         *
         * @return the payload
         */
        public T getData() {
            return data;
        }
    }

    /**
     * Failed outcome carrying a user-facing message and the originating cause, if any.
     *
     * @param <T> the payload type that would have been returned on success
     */
    public static final class Error<T> extends ApiResult<T> {
        private final String message;
        private final Throwable cause;

        /**
         * Constructs a failed result.
         *
         * Complexity:
         * Time: O(1)
         * Space: O(1)
         *
         * @param message user-facing description of what went wrong
         * @param cause the underlying exception, or {@code null} if not applicable
         */
        public Error(String message, Throwable cause) {
            this.message = message;
            this.cause = cause;
        }

        /**
         * Returns the user-facing error message.
         *
         * Complexity:
         * Time: O(1)
         * Space: O(1)
         *
         * @return the error message
         */
        public String getMessage() {
            return message;
        }

        /**
         * Returns the underlying exception that caused this failure.
         *
         * Complexity:
         * Time: O(1)
         * Space: O(1)
         *
         * @return the cause, or {@code null} if not applicable
         */
        public Throwable getCause() {
            return cause;
        }
    }

    /**
     * In-flight outcome, emitted before the underlying operation has completed so the UI
     * can show a loading state.
     *
     * @param <T> the payload type that will be returned on success
     */
    public static final class Loading<T> extends ApiResult<T> {
    }
}
