package io.rover.sdk.ui.toolbar

/**
 * Tool bar configuration.  Colour overrides, text, text colour, and status bar settings.
 */
internal data class ToolbarConfiguration(
    val useExistingStyle: Boolean,

    val appBarText: String,

    val color: Int,
    val textColor: Int,
    var buttonColor: Int,

    val upButton: Boolean,
    val closeButton: Boolean,

    /**
     * If null, then the default material design behaviour should be used.
     */
    val statusBarColor: Int
)
