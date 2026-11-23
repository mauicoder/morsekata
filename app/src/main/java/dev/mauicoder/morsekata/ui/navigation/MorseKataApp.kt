package dev.mauicoder.morsekata.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mauicoder.morsekata.ui.decoder.MorseDecoderScreen
import dev.mauicoder.morsekata.ui.play.PlayTextScreen
import dev.mauicoder.morsekata.ui.settings.SettingsScreen
import dev.mauicoder.morsekata.ui.trainer.QsoTrainerScreen
import dev.mauicoder.morsekata.viewmodel.UserPreferencesViewModel

/**
 * Top-level application shell hosting Material 3 [Scaffold], [MorseKataBottomBar],
 * and type-safe Compose [NavHost].
 */
@Composable
fun MorseKataApp(
    userPreferencesViewModel: UserPreferencesViewModel = viewModel(),
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val preferences by userPreferencesViewModel.userPreferences.collectAsStateWithLifecycle()
    MorseKataApp(
        showExperimentalFeatures = preferences.showExperimentalFeatures,
        navController = navController,
        modifier = modifier
    )
}

/**
 * Overload of [MorseKataApp] accepting explicit [showExperimentalFeatures] flag.
 */
@Composable
fun MorseKataApp(
    showExperimentalFeatures: Boolean,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(showExperimentalFeatures, currentDestination) {
        val isDecoderActive = currentDestination?.hierarchy?.any { it.hasRoute(DecoderRoute::class) } == true
        if (shouldRedirectToTrain(isDecoderActive, showExperimentalFeatures)) {
            navController.navigate(TrainRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            MorseKataBottomBar(
                navController = navController,
                showExperimentalFeatures = showExperimentalFeatures
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TrainRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<TrainRoute> {
                QsoTrainerScreen()
            }
            composable<PlayTextRoute> {
                PlayTextScreen()
            }
            composable<DecoderRoute> {
                MorseDecoderScreen()
            }
            composable<ConfigRoute> {
                SettingsScreen()
            }
        }
    }
}
