package com.cooksync.app.testutil;

import androidx.lifecycle.MutableLiveData;

import com.cooksync.app.domain.ApiResult;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Mockito {@link Answer} factories for repository methods shaped like every method in this
 * app's {@code *Repository} interfaces: a handful of request parameters followed by a
 * {@code MutableLiveData<ApiResult<T>>} result target as the last argument, which the real
 * implementation posts to once the network call settles. Posts synchronously so it plays
 * correctly with {@code InstantTaskExecutorRule} in ViewModel unit tests.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
public final class ApiResultAnswers {

    private ApiResultAnswers() {
    }

    /**
     * Builds an {@link Answer} that posts a successful result to the mocked call's last
     * argument.
     *
     * @param <T> the payload type
     * @param data the payload to post
     * @return the answer, for use in {@code doAnswer(...).when(repo).someCall(...)}
     */
    public static <T> Answer<Void> success(T data) {
        return invocation -> {
            ApiResultAnswers.<T>lastArg(invocation).postValue(new ApiResult.Success<>(data));
            return null;
        };
    }

    /**
     * Builds an {@link Answer} that posts a failed result to the mocked call's last argument.
     *
     * @param <T> the payload type that would have been returned on success
     * @param message the user-facing error message to post
     * @return the answer, for use in {@code doAnswer(...).when(repo).someCall(...)}
     */
    public static <T> Answer<Void> error(String message) {
        return invocation -> {
            ApiResultAnswers.<T>lastArg(invocation).postValue(new ApiResult.Error<>(message, null));
            return null;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> MutableLiveData<ApiResult<T>> lastArg(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        return (MutableLiveData<ApiResult<T>>) args[args.length - 1];
    }
}
