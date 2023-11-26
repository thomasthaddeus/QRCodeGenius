package com.example.programmingtools

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
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//import androidx.test.espresso.UiController
//import androidx.test.espresso.ViewAction
//import android.view.View

@RunWith(AndroidJUnit4::class)
@MediumTest
class MainActivityInstrumentedTest {

    @get:Rule
    var activityRule: ActivityTestRule<MainActivity> = ActivityTestRule(MainActivity::class.java)

    @Test
    fun testEmptyInput() {
        // Assuming buttonGenerate is the ID for your "Generate" button
        onView(withId(R.id.buttonGenerate)).perform(click())
        // Assuming imageViewQRCode is the ID for the ImageView displaying QR code
        onView(withId(R.id.imageViewQRCode))
            .check(matches(withEffectiveVisibility(Visibility.GONE))) // Check if ImageView is not visible
        // Alternatively, check for an error message if your app shows one
    }

    @Test
    fun testQRCodeGenerationAndDisplay() {
        // Assume you have an EditText with id editTextText and a Button with id buttonGenerate
        val testText = "Test QR Code"
        onView(withId(R.id.editTextText)).perform(typeText(testText))
        onView(withId(R.id.buttonGenerate)).perform(click())

        // Assuming you have an ImageView with id imageViewQRCode
        // Check if the ImageView is displayed after generating the QR code
        onView(withId(R.id.imageViewQRCode)).check(matches(isDisplayed()))
    }

    @Test
    fun testQRCodeContent() {
        val testText = "Test QR Code"
        onView(withId(R.id.editTextText)).perform(typeText(testText))
        onView(withId(R.id.buttonGenerate)).perform(click())
        onView(withId(R.id.imageViewQRCode)).check(matches(isDisplayed()))
        // Additional logic to decode and verify the QR code would go here
    }

    @Test
    fun testQRCodePersistenceOnRotation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val testText = "Test QR Code"
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
    // Additional tests can be written to check if the file is saved correctly, but this might require mocking the file system
}
