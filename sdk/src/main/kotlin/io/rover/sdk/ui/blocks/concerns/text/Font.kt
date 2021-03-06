package io.rover.sdk.ui.blocks.concerns.text

import android.graphics.Typeface

/**
 * A specific font in the font-family and style tuple appropriate for Android.
 */
internal data class Font(
    /**
     * A font family name.
     */
    val fontFamily: String,

    /**
     * An Android style value of either [Typeface.NORMAL], [Typeface.BOLD], [Typeface.ITALIC], or
     * [Typeface.BOLD_ITALIC].
     */
    val fontStyle: Int
)