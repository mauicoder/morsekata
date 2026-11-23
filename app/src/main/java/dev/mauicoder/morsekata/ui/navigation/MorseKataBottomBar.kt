package dev.mauicoder.morsekata.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Material 3 bottom navigation bar for Morse Kata.
 *
 * Renders navigation destinations based on [getBottomNavItems] and highlights the active item.
 */
@Composable
fun MorseKataBottomBar(
    currentDestination: NavDestination?,
    showExperimentalFeatures: Boolean,
    onNavigateToItem: (BottomNavItem<*>) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember(showExperimentalFeatures) {
        getBottomNavItems(showExperimentalFeatures)
    }

    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            val selected = currentDestination.isItemSelected(item)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToItem(item) },
                icon = {
                    Icon(
                        painter = painterResource(id = item.iconRes),
                        contentDescription = item.title
                    )
                },
                label = {
                    Text(text = item.title)
                }
            )
        }
    }
}

/**
 * Overload connecting [MorseKataBottomBar] directly to a [NavController].
 */
@Composable
fun MorseKataBottomBar(
    navController: NavController,
    showExperimentalFeatures: Boolean,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    MorseKataBottomBar(
        currentDestination = currentDestination,
        showExperimentalFeatures = showExperimentalFeatures,
        onNavigateToItem = { item ->
            navController.navigate(
                item.route,
                bottomNavOptions(navController.graph.findStartDestination().id)
            )
        },
        modifier = modifier
    )
}
