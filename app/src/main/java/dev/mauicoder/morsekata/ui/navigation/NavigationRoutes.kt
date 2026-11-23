package dev.mauicoder.morsekata.ui.navigation

import androidx.annotation.DrawableRes
import dev.mauicoder.morsekata.R
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for primary Morse Kata screens.
 */
@Serializable
data object TrainRoute

@Serializable
data object PlayTextRoute

@Serializable
data object DecoderRoute

@Serializable
data object ConfigRoute

/**
 * Encapsulates bottom navigation destination item metadata.
 */
data class BottomNavItem<T : Any>(
    val route: T,
    val title: String,
    @get:DrawableRes val iconRes: Int,
    val isExperimental: Boolean = false
)

/**
 * Returns the list of bottom navigation bar items in display order,
 * conditionally including experimental destinations based on [showExperimentalFeatures].
 */
fun getBottomNavItems(showExperimentalFeatures: Boolean): List<BottomNavItem<*>> {
    val items = mutableListOf(
        BottomNavItem(
            route = TrainRoute,
            title = "Train",
            iconRes = R.drawable.ic_train,
            isExperimental = false
        ),
        BottomNavItem(
            route = PlayTextRoute,
            title = "Play",
            iconRes = R.drawable.ic_play,
            isExperimental = false
        )
    )

    if (showExperimentalFeatures) {
        items.add(
            BottomNavItem(
                route = DecoderRoute,
                title = "Decoder",
                iconRes = R.drawable.ic_mic,
                isExperimental = true
            )
        )
    }

    items.add(
        BottomNavItem(
            route = ConfigRoute,
            title = "Config",
            iconRes = R.drawable.ic_settings,
            isExperimental = false
        )
    )

    return items
}
