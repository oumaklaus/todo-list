package com.example.taskflow

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {
    @Before
    fun clearSession() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("taskflow_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun launchShowsLoginFields() {
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withText("Welcome back")).check(matches(isDisplayed()))
            onView(withId(R.id.inputEmail)).check(matches(isDisplayed()))
            onView(withId(R.id.inputPassword)).check(matches(isDisplayed()))
            onView(withId(R.id.layoutDisplayName)).check(matches(not(isDisplayed())))
            onView(withId(R.id.buttonSubmit)).check(matches(withText("Log in")))
        }
    }

    @Test
    fun switchShowsAccountCreationFields() {
        ActivityScenario.launch(LoginActivity::class.java).use {
            onView(withId(R.id.buttonSwitchMode)).perform(click())

            onView(withText("Create your account")).check(matches(isDisplayed()))
            onView(withId(R.id.layoutDisplayName)).check(matches(isDisplayed()))
            onView(withId(R.id.buttonSubmit)).check(matches(withText("Create account")))
        }
    }
}
