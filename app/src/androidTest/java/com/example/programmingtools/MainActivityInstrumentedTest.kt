package com.programmingtools.app

import android.content.pm.ActivityInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@MediumTest
class MainActivityInstrumentedTest {

    @get:Rule
    var activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testEmptyInput() {
        onView(withId(R.id.buttonGenerate)).perform(click())
        onView(withId(R.id.imageViewQRCode))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun testQRCodeGenerationAndDisplay() {
        val testText = "Test QR Code"
        onView(withId(R.id.editTextText)).perform(typeText(testText))
        onView(withId(R.id.buttonGenerate)).perform(click())

        onView(withId(R.id.imageViewQRCode)).check(matches(isDisplayed()))
    }

    @Test
    fun testSaveWithoutGenerationKeepsImageHidden() {
        onView(withId(R.id.buttonSave)).perform(click())
        onView(withId(R.id.imageViewQRCode))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun testViewSampleDisplaysQRCode() {
        onView(withId(R.id.buttonViewSample)).perform(click())
        onView(withId(R.id.imageViewQRCode)).check(matches(isDisplayed()))
    }

    @Test
    fun testQRCodePersistsOnRotation() {
        val testText = "Test QR Code"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.editTextText)).perform(typeText(testText))
            onView(withId(R.id.buttonGenerate)).perform(click())

            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            onView(withId(R.id.imageViewQRCode)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            onView(withId(R.id.imageViewQRCode)).check(matches(isDisplayed()))
        }
    }
}
