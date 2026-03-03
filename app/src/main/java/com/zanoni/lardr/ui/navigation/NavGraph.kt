package com.zanoni.lardr.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.zanoni.lardr.ui.screens.auth.LoginScreen
import com.zanoni.lardr.ui.screens.friends.FriendsScreen
import com.zanoni.lardr.ui.screens.home.HomeScreen
import com.zanoni.lardr.ui.screens.settings.SettingsScreen
import com.zanoni.lardr.ui.screens.splash.SplashScreen
import com.zanoni.lardr.ui.screens.store.StoreScreen

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Home : Screen("home")
    object Friends : Screen("friends")
    object Settings : Screen("settings")
    object Store : Screen("store/{storeId}") {
        fun createRoute(storeId: String) = "store/$storeId"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToStore = { storeId ->
                    navController.navigate(Screen.Store.createRoute(storeId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                currentRoute = currentRoute,
                onNavigate = { route ->
                    when (route) {
                        "home" -> {
                            if (currentRoute != Screen.Home.route) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            }
                        }
                        "friends" -> {
                            navController.navigate(Screen.Friends.route)
                        }
                        "settings" -> {
                            navController.navigate(Screen.Settings.route)
                        }
                    }
                }
            )
        }

        composable(Screen.Friends.route) {
            FriendsScreen(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    when (route) {
                        "home" -> {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        }
                        "friends" -> {
                            if (currentRoute != Screen.Friends.route) {
                                navController.navigate(Screen.Friends.route) {
                                    popUpTo(Screen.Friends.route) { inclusive = true }
                                }
                            }
                        }
                        "settings" -> {
                            navController.navigate(Screen.Settings.route)
                        }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    when (route) {
                        "home" -> {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        }
                        "friends" -> {
                            navController.navigate(Screen.Friends.route)
                        }
                        "settings" -> {
                            if (currentRoute != Screen.Settings.route) {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(Screen.Settings.route) { inclusive = true }
                                }
                            }
                        }
                    }
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Store.route,
            arguments = listOf(navArgument("storeId") { type = NavType.StringType })
        ) {
            StoreScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                currentRoute = currentRoute,
                onNavigate = { route ->
                    when (route) {
                        "home" -> {
                            navController.popBackStack(Screen.Home.route, false)
                        }
                        "friends" -> {
                            navController.navigate(Screen.Friends.route)
                        }
                        "settings" -> {
                            navController.navigate(Screen.Settings.route)
                        }
                    }
                }
            )
        }
    }
}