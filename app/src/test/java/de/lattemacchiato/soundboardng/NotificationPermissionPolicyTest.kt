package de.lattemacchiato.soundboardng

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun `explanation is only shown when required`() {
        val cases =
            listOf(
                TestCase(
                    apiLevel = 27,
                    permissionGranted = false,
                    explanationShown = false,
                    expected = false,
                ),
                TestCase(
                    apiLevel = 32,
                    permissionGranted = false,
                    explanationShown = false,
                    expected = false,
                ),
                TestCase(
                    apiLevel = 33,
                    permissionGranted = false,
                    explanationShown = false,
                    expected = true,
                ),
                TestCase(
                    apiLevel = 36,
                    permissionGranted = true,
                    explanationShown = false,
                    expected = false,
                ),
                TestCase(
                    apiLevel = 36,
                    permissionGranted = false,
                    explanationShown = true,
                    expected = false,
                ),
                TestCase(
                    apiLevel = 36,
                    permissionGranted = true,
                    explanationShown = true,
                    expected = false,
                ),
            )

        cases.forEach { case ->
            assertEquals(
                "Failed for API ${case.apiLevel}, granted=${case.permissionGranted}, shown=${case.explanationShown}",
                case.expected,
                shouldExplainNotificationPermission(
                    apiLevel = case.apiLevel,
                    permissionGranted = case.permissionGranted,
                    explanationShown = case.explanationShown,
                ),
            )
        }
    }

    private data class TestCase(
        val apiLevel: Int,
        val permissionGranted: Boolean,
        val explanationShown: Boolean,
        val expected: Boolean,
    )
}
