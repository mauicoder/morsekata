package dev.mauicoder.morsekata.ui.navigation

import androidx.annotation.DrawableRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
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

/**
 * Evaluates whether navigation should automatically redirect to [TrainRoute]
 * when experimental features are disabled while on [DecoderRoute].
 */
fun shouldRedirectToTrain(
    isCurrentDestinationDecoder: Boolean,
    showExperimentalFeatures: Boolean
): Boolean {
    return isCurrentDestinationDecoder && !showExperimentalFeatures
}

/**
 * Creates standard bottom navigation options configured for state-preserving tab switching:
 * pop up to the start destination (saving state), single-top launch, and state restoration.
 */
fun bottomNavOptions(startDestinationId: Int): NavOptions = navOptions {
    popUpTo(startDestinationId) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}

/**
 * Determines whether this [NavDestination] matches the route of the provided [BottomNavItem].
 */
fun NavDestination?.isItemSelected(item: BottomNavItem<*>): Boolean {
    if (this == null) return false
    return hierarchy.any { it.hasRoute(item.route::class) }
}

/**
 * Checks if a given destination route string matches a target route object.
 */
fun isRouteMatching(currentRoute: String?, targetRoute: Any): Boolean {
    if (currentRoute == null) return false
    val targetClassName = targetRoute::class.qualifiedName ?: return false
    return currentRoute == targetClassName ||
            currentRoute.startsWith("$targetClassName/") ||
            currentRoute.startsWith("$targetClassName?")
}



