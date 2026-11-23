package dev.mauicoder.morsekata.ui.navigation

import dev.mauicoder.morsekata.R
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRoutesTest {

    @Test
    fun routes_areSerializable() {
        val trainJson = Json.encodeToString(TrainRoute)
        assertEquals(TrainRoute, Json.decodeFromString<TrainRoute>(trainJson))

        val playTextJson = Json.encodeToString(PlayTextRoute)
        assertEquals(PlayTextRoute, Json.decodeFromString<PlayTextRoute>(playTextJson))

        val decoderJson = Json.encodeToString(DecoderRoute)
        assertEquals(DecoderRoute, Json.decodeFromString<DecoderRoute>(decoderJson))

        val configJson = Json.encodeToString(ConfigRoute)
        assertEquals(ConfigRoute, Json.decodeFromString<ConfigRoute>(configJson))
    }

    @Test
    fun getBottomNavItems_whenExperimentalFeaturesDisabled_returnsThreeRoutesInOrder() {
        val items = getBottomNavItems(showExperimentalFeatures = false)

        assertEquals(3, items.size)
        assertEquals(listOf(TrainRoute, PlayTextRoute, ConfigRoute), items.map { it.route })
        assertTrue(items.none { it.isExperimental })

        assertEquals("Train", items[0].title)
        assertEquals(R.drawable.ic_train, items[0].iconRes)

        assertEquals("Play", items[1].title)
        assertEquals(R.drawable.ic_play, items[1].iconRes)

        assertEquals("Config", items[2].title)
        assertEquals(R.drawable.ic_settings, items[2].iconRes)
    }

    @Test
    fun getBottomNavItems_whenExperimentalFeaturesEnabled_includesDecoderRouteInOrder() {
        val items = getBottomNavItems(showExperimentalFeatures = true)

        assertEquals(4, items.size)
        assertEquals(listOf(TrainRoute, PlayTextRoute, DecoderRoute, ConfigRoute), items.map { it.route })

        val decoderItem = items.first { it.route == DecoderRoute }
        assertTrue(decoderItem.isExperimental)
        assertEquals("Decoder", decoderItem.title)
        assertEquals(R.drawable.ic_mic, decoderItem.iconRes)
    }

    @Test
    fun bottomNavItem_defaultExperimentalIsFalse() {
        val item = BottomNavItem(
            route = TrainRoute,
            title = "Train",
            iconRes = R.drawable.ic_train
        )
        assertFalse(item.isExperimental)
    }
}
