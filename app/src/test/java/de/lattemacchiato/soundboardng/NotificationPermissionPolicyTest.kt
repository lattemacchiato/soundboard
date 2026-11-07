package de.lattemacchiato.soundboardng

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun `explanation is only shown when required`() {
        val cases =
            listOf(
                TestCase(27, false, false, false),
                TestCase(32, false, false, false),
                TestCase(33, false, false, true),
                TestCase(36, true, false, false),
                TestCase(36, false, true, false),
                TestCase(36, true, true, false),
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
