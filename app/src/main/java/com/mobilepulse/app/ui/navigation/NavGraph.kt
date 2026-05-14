package com.mobilepulse.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Terminal
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.mobilepulse.app.ui.screen.*

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard  : Screen("dashboard",        "Monitor",  Icons.Filled.Dashboard)
    object Apps       : Screen("apps",             "Apps",     Icons.Filled.Apps)
    object Automation : Screen("automation",       "Rules",    Icons.Filled.AutoFixHigh)
    object Lists      : Screen("lists",            "Lists",    Icons.AutoMirrored.Filled.List)
    object Log        : Screen("log",              "Log",      Icons.Filled.History)
    object Settings   : Screen("settings",         "Settings", Icons.Filled.Settings)
    object AppFilter  : Screen("apps/{filter}",    "Apps",     Icons.Filled.Apps)
    object Ram        : Screen("ram",              "RAM",      Icons.Filled.Memory)
    object Ai         : Screen("ai?query={query}", "AI",       Icons.Filled.AutoAwesome)
    object Terminal   : Screen("terminal",         "Terminal", Icons.Filled.Terminal)
}

val bottomNavItems = listOf(
    Screen.Dashboard, Screen.Apps, Screen.Automation,
    Screen.Lists, Screen.Log, Screen.Settings
)

private val enterTransition = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 16 }
private val exitTransition  = fadeOut(tween(160))
private val popEnter        = fadeIn(tween(220))
private val popExit         = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 16 }

@Composable
fun MobilePulseNavGraph() {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentDest   = navBackStack?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon     = { Icon(screen.icon, screen.label) },
                        label    = { Text(screen.label) },
                        selected = currentDest?.hierarchy
                            ?.any { it.route == screen.route } == true,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController        = navController,
            startDestination     = Screen.Dashboard.route,
            modifier             = Modifier.padding(padding),
            enterTransition      = { enterTransition },
            exitTransition       = { exitTransition },
            popEnterTransition   = { popEnter },
            popExitTransition    = { popExit }
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen(navController) }
            composable(Screen.Apps.route)      { AppsScreen() }
            composable(Screen.AppFilter.route) { backStackEntry ->
                val filter = backStackEntry.arguments?.getString("filter") ?: "NONE"
                AppsScreen(filter = filter)
            }
            composable(Screen.Automation.route) { AutomationScreen() }
            composable(Screen.Lists.route)      { WhitelistScreen() }
            composable(Screen.Log.route)        { LogScreen(navController) }
            composable(Screen.Settings.route)   { SettingsScreen(navController) }
            composable(Screen.Ram.route)        { RamScreen(navController) }
            composable(
                route = Screen.Ai.route,
                arguments = listOf(navArgument("query") {
                    type         = NavType.StringType
                    defaultValue = ""
                })
            ) { AiScreen(navController) }
            composable(Screen.Terminal.route) { TerminalScreen(navController) }
        }
    }
}
