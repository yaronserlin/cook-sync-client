package com.cooksync.app.ui.auth;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.cooksync.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Instrumented smoke test proving the Espresso test harness is wired up end to end.
 * On a fresh install (no stored session), {@link LoginActivity} skips the auto-login
 * skeleton and shows the sign-in form synchronously, so this assertion is not
 * subject to network timing flakiness.
 *
 * @author Yaron Serlin
 * @version 1.0
 * @since 12/08/2026
 */
@RunWith(AndroidJUnit4.class)
public class LoginActivitySmokeTest {

    @Test
    public void loginForm_isDisplayed_onLaunch() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.et_email)).check(matches(isDisplayed()));
            onView(withId(R.id.et_password)).check(matches(isDisplayed()));
            onView(withId(R.id.btn_sign_in)).check(matches(isDisplayed()));
        }
    }
}
