package dev.mauicoder.morsekata.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLogicTest {

    @Test
    fun shouldRedirectToTrain_whenCurrentIsDecoderAndExperimentalDisabled_returnsTrue() {
        val result = shouldRedirectToTrain(
            isCurrentDestinationDecoder = true,
            showExperimentalFeatures = false
        )
        assertTrue(result)
    }

    @Test
    fun shouldRedirectToTrain_whenCurrentIsDecoderAndExperimentalEnabled_returnsFalse() {
        val result = shouldRedirectToTrain(
            isCurrentDestinationDecoder = true,
            showExperimentalFeatures = true
        )
        assertFalse(result)
    }

    @Test
    fun shouldRedirectToTrain_whenCurrentIsNotDecoderAndExperimentalDisabled_returnsFalse() {
        val result = shouldRedirectToTrain(
            isCurrentDestinationDecoder = false,
            showExperimentalFeatures = false
        )
        assertFalse(result)
    }

    @Test
    fun shouldRedirectToTrain_whenCurrentIsNotDecoderAndExperimentalEnabled_returnsFalse() {
        val result = shouldRedirectToTrain(
            isCurrentDestinationDecoder = false,
            showExperimentalFeatures = true
        )
        assertFalse(result)
    }

    @Test
    fun isRouteMatching_whenSameRouteClass_returnsTrue() {
        assertTrue(isRouteMatching(TrainRoute::class.qualifiedName, TrainRoute))
        assertTrue(isRouteMatching(PlayTextRoute::class.qualifiedName, PlayTextRoute))
        assertTrue(isRouteMatching(DecoderRoute::class.qualifiedName, DecoderRoute))
        assertTrue(isRouteMatching(ConfigRoute::class.qualifiedName, ConfigRoute))
    }

    @Test
    fun isRouteMatching_whenDifferentRoute_returnsFalse() {
        assertFalse(isRouteMatching(TrainRoute::class.qualifiedName, PlayTextRoute))
        assertFalse(isRouteMatching(PlayTextRoute::class.qualifiedName, TrainRoute))
        assertFalse(isRouteMatching(DecoderRoute::class.qualifiedName, ConfigRoute))
    }

    @Test
    fun isRouteMatching_whenCurrentRouteIsNull_returnsFalse() {
        assertFalse(isRouteMatching(null, TrainRoute))
        assertFalse(isRouteMatching(null, PlayTextRoute))
    }

    @Test
    fun bottomNavOptions_setsSaveStateAndRestoreState() {
        val options = bottomNavOptions(startDestinationId = 42)
        assertTrue(options.shouldLaunchSingleTop())
        assertTrue(options.shouldRestoreState())
        org.junit.Assert.assertEquals(42, options.popUpToId)
        assertTrue(options.shouldPopUpToSaveState())
    }

    @Test
    fun isNavItemSelected_whenNullDestination_returnsFalse() {
        val item = BottomNavItem(TrainRoute, "Train", dev.mauicoder.morsekata.R.drawable.ic_train)
        assertFalse(null.isItemSelected(item))
    }

    @Test
    fun isNavItemSelected_whenMatchingDestinationRoute_returnsTrue() {
        val navigator = androidx.navigation.NoOpNavigator()
        val allRoutes = listOf(
            TrainRoute to dev.mauicoder.morsekata.R.drawable.ic_train,
            PlayTextRoute to dev.mauicoder.morsekata.R.drawable.ic_play,
            DecoderRoute to dev.mauicoder.morsekata.R.drawable.ic_mic,
            ConfigRoute to dev.mauicoder.morsekata.R.drawable.ic_settings
        )

        for ((route, iconRes) in allRoutes) {
            val destination = androidx.navigation.NavDestinationBuilder(
                navigator,
                route::class,
                emptyMap()
            ).build()
            val item = BottomNavItem(route, "Title", iconRes)
            assertTrue("Expected destination for $route to be selected", destination.isItemSelected(item))
        }
    }

    @Test
    fun isNavItemSelected_whenDifferentDestinationRoute_returnsFalse() {
        val destination = androidx.navigation.NavDestinationBuilder(
            androidx.navigation.NoOpNavigator(),
            PlayTextRoute::class,
            emptyMap()
        ).build()
        val item = BottomNavItem(TrainRoute, "Train", dev.mauicoder.morsekata.R.drawable.ic_train)
        assertFalse(destination.isItemSelected(item))
    }

    @Test
    fun isNavItemSelected_whenRouteInHierarchy_returnsTrue() {
        val navigator = androidx.navigation.NoOpNavigator()
        val destination = androidx.navigation.NavDestinationBuilder(
            navigator,
            TrainRoute::class,
            emptyMap()
        ).build()
        val provider = androidx.navigation.NavigatorProvider()
        val graph = androidx.navigation.NavGraphNavigator(provider).createDestination()
        graph.addDestination(destination)

        val item = BottomNavItem(TrainRoute, "Train", dev.mauicoder.morsekata.R.drawable.ic_train)
        assertTrue(destination.isItemSelected(item))
    }
}
