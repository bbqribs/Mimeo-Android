package com.mimeo.android.startup

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mimeo.android.MainActivity
import com.mimeo.android.StartupLoadingScreen
import com.mimeo.android.data.SettingsStore
import com.mimeo.android.model.ConnectionMode
import com.mimeo.android.ui.theme.MimeoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Isolated composable test for [StartupLoadingScreen].
 * Runs without launching the full Activity.
 */
@RunWith(AndroidJUnit4::class)
class StartupLoadingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsLoadingTextAndAppTitle() {
        composeTestRule.setContent {
            MimeoTheme {
                StartupLoadingScreen()
            }
        }
        composeTestRule.onNodeWithText("MIMEO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading your session...").assertIsDisplayed()
    }
}

/**
 * Activity-level startup smoke tests.
 *
 * These tests clear credentials before launching [MainActivity] so they exercise
 * the signed-out cold-launch path without requiring a live backend.
 *
 * Signed-in path (no sign-in flash) requires real credentials and a running
 * backend; that path is covered by manual verification only.
 */
@RunWith(AndroidJUnit4::class)
class StartupActivitySmokeTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun clearCredentials() = runBlocking {
        SettingsStore(context).saveSignedInSession(
            baseUrl = "",
            connectionMode = ConnectionMode.LOCAL,
            apiToken = "",
        )
    }

    @Test
    fun noCredentials_signInScreenAppearsAfterStartupRestore() {
        clearCredentials()
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.waitUntil(timeoutMillis = 6_000) {
                composeTestRule
                    .onAllNodesWithText("Sign in to Mimeo")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Sign in to Mimeo").assertIsDisplayed()
        }
    }

    @Test
    fun noCredentials_drawerMenuButtonNotVisible() {
        // When signed out, libraryShellVisible=false so the hamburger (☰) is never rendered.
        // This proves the drawer cannot be accidentally opened before authentication.
        clearCredentials()
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            composeTestRule.waitUntil(timeoutMillis = 6_000) {
                composeTestRule
                    .onAllNodesWithText("Sign in to Mimeo")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("☰").assertDoesNotExist()
        }
    }
}
