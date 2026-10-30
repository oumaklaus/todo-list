package com.example.taskflow

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun launchShowsTaskFlowAndAddButton() {
        onView(withText("TaskFlow")).check(matches(isDisplayed()))
        onView(withId(R.id.fabAdd)).check(matches(isDisplayed()))
    }

    @Test
    fun addButtonOpensNewTaskDialog() {
        onView(withId(R.id.fabAdd)).perform(click())

        onView(withText("New task")).check(matches(isDisplayed()))
        onView(withId(R.id.inputTitle)).check(matches(isDisplayed()))
    }

    @Test
    fun priorityFieldShowsPriorityOptions() {
        onView(withId(R.id.fabAdd)).perform(click())

        onView(withId(R.id.inputPriority)).perform(click())

        onView(withText("High"))
            .inRoot(isPlatformPopup())
            .check(matches(isDisplayed()))
    }
}
