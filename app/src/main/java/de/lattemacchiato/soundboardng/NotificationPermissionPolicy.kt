package de.lattemacchiato.soundboardng

/**
 * Determines whether the notification permission explanation should be shown.
 */
internal fun shouldExplainNotificationPermission(
    apiLevel: Int,
    permissionGranted: Boolean,
    explanationShown: Boolean,
): Boolean =
    apiLevel >= 33 &&
        !permissionGranted &&
        !explanationShown
