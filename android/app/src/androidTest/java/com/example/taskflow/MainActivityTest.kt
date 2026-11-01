package com.example.taskflow

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Before
    fun seedSession() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("taskflow_session", Context.MODE_PRIVATE)
            .edit()
            .putString("token", "instrumentation-token")
            .putString("user_id", "user-1")
            .putString("display_name", "Test User")
            .putString("email", "test@example.com")
            .commit()
    }

    @Test
    fun launchShowsTaskFlowAndAddButton() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("TaskFlow")).check(matches(isDisplayed()))
            onView(withId(R.id.fabAdd)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun addButtonOpensNewTaskDialog() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fabAdd)).perform(click())

            onView(withText("New task")).check(matches(isDisplayed()))
            onView(withId(R.id.inputTitle)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun priorityFieldShowsPriorityOptions() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fabAdd)).perform(click())

            onView(withId(R.id.inputPriority)).perform(click())

            onView(withText("High"))
                .inRoot(isPlatformPopup())
                .check(matches(isDisplayed()))
        }
    }
}
