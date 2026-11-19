package com.aquarescue.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aquarescue.app.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aquarescue.app.ui.screens.history.HistoryScreen
import com.aquarescue.app.ui.screens.home.HomeScreen
import com.aquarescue.app.ui.screens.map.MapScreen
import com.aquarescue.app.ui.screens.settings.SettingsScreen
import com.aquarescue.app.ui.theme.MarineSurface
import com.aquarescue.app.ui.theme.TextSecondary

enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    MAP("map", R.string.nav_map, Icons.Outlined.Map, Icons.Rounded.Map),
    HISTORY("history", R.string.nav_history, Icons.Outlined.History, Icons.Rounded.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@Composable
fun AquaRescueNavApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Отступы под системные панели экраны обрабатывают сами (edge-to-edge).
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = MarineSurface) {
                Destination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.navigateToTab(dest) },
                        icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label), maxLines = 1, softWrap = false) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = Destination.HOME.route) {
                composable(Destination.HOME.route) {
                    HomeScreen(
                        onOpenMap = { navController.navigateToTab(Destination.MAP) },
                        onOpenSettings = { navController.navigateToTab(Destination.SETTINGS) },
                    )
                }
                composable(Destination.MAP.route) { MapScreen() }
                composable(Destination.HISTORY.route) { HistoryScreen() }
                composable(Destination.SETTINGS.route) { SettingsScreen() }
            }
        }
    }
}

/** Переход между вкладками нижней панели без накопления копий экранов в стеке. */
fun NavHostController.navigateToTab(destination: Destination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
