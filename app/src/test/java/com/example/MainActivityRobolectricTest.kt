package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.theme.MikeWriteTheme
import com.example.ui.theme.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testMainActivityLifecycle() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.create().start().resume().get()
        assertNotNull(activity)
        controller.pause().stop().destroy()
    }

    @Test
    fun testActiveScreenEnum() {
        assertEquals(2, ActiveScreen.entries.size)
        assertEquals(ActiveScreen.BUDDY, ActiveScreen.valueOf("BUDDY"))
        assertEquals(ActiveScreen.SETTINGS, ActiveScreen.valueOf("SETTINGS"))
    }

    @Test
    fun testThemeLightTypography() {
        composeTestRule.setContent {
            MikeWriteTheme {
                Text(
                    text = "Theme Test Light",
                    style = Typography.bodyLarge
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
