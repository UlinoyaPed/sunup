/*
 * Adapted from UnifiedPush android-distributor 0.7.6 (Apache-2.0).
 * https://codeberg.org/UnifiedPush/android-distributor
 * Sunup modifications: per-application push arrival controls.
 */
package org.unifiedpush.distributor.sunup.activities

import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.unifiedpush.android.distributor.ipc.InternalOpcode
import org.unifiedpush.android.distributor.ui.AppConfig
import org.unifiedpush.android.distributor.ui.NoLoginConfig
import org.unifiedpush.android.distributor.ui.R
import org.unifiedpush.android.distributor.ui.UserLoginConfig
import org.unifiedpush.android.distributor.ui.compose.AppBar
import org.unifiedpush.android.distributor.ui.screen.MainAppBarOrSelection
import org.unifiedpush.android.distributor.ui.screen.SettingsScreen
import org.unifiedpush.android.distributor.ui.vm.DistribMigrationViewModel
import org.unifiedpush.android.distributor.ui.vm.MainViewModel
import org.unifiedpush.android.distributor.ui.vm.SettingsViewModel
import org.unifiedpush.android.distributor.ui.vm.ThemeViewModel

/**
 * @param title : Title shown in AppBar. Uses [AppConfig.appName] if null
 */
enum class AppScreen(@StringRes val title: Int?) {
    Login(null),
    Main(null),
    Settings(R.string.settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(
    @StringRes
    title: Int,
    canNavigateBack: Boolean,
    modifier: Modifier = Modifier,
    navigateUp: () -> Unit
) = AppBar(
    title,
    canNavigateBack,
    navigateUp,
    modifier
)

private enum class Dir {
    Left,
    Right
}

private fun Dir.transform(it: Int): Int = when (this) {
    Dir.Left -> it
    Dir.Right -> -it
}

private fun slideInTo(dir: Dir): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = 200)
) { dir.transform(it) } + fadeIn(initialAlpha = 1f)

private fun slideOutFrom(dir: Dir): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = 200)
) { dir.transform(it) } + fadeOut(targetAlpha = 1f)

@Composable
fun App(
    appConfig: AppConfig,
    factory: ViewModelProvider.Factory,
    themeViewModel: ThemeViewModel = viewModel<ThemeViewModel>(factory = factory),
    navController: NavHostController = rememberNavController(),
    uiFlow: Flow<String> = flow {}
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    // shared with all views, no need to scope it
    val migrationViewModel = viewModel<DistribMigrationViewModel>(factory = factory)
    val mainViewModel = viewModel<MainViewModel>(factory = factory)
    LaunchedEffect(key1 = Unit) {
        // For the dyn colors, it may be a good idea to restore without ipc
        themeViewModel.restoreDynamicColors()
        mainViewModel.startPeriodicWorker()
        // Run once app migration, if needed
        mainViewModel.messenger?.sendIMessage(InternalOpcode.RUN_APP_MIGRATION, 0)
    }
    val startScreen = appConfig.loginConfig.let { cfg ->
        when (cfg) {
            is NoLoginConfig -> AppScreen.Main
            is UserLoginConfig<*> -> {
                val vm = cfg.loginViewModel
                LaunchedEffect(vm.connected) {
                    mainViewModel.refreshAccountName()
                }
                if (vm.connected == true) AppScreen.Main else AppScreen.Login
            }
        }
    }
    val currentScreen = AppScreen.valueOf(
        backStackEntry?.destination?.route ?: startScreen.name
    )

    Scaffold(
        topBar = {
            when (currentScreen) {
                AppScreen.Login -> {
                    Spacer(Modifier)
                } // Nothing
                AppScreen.Main -> {
                    MainAppBarOrSelection(
                        appConfig,
                        mainViewModel,
                        onGoToSettings = {
                            navController.navigate(AppScreen.Settings.name)
                        }
                    )
                }
                else -> null
            } ?: DefaultTopBar(
                currentScreen.title ?: appConfig.appName,
                canNavigateBack = navController.previousBackStackEntry != null
            ) { navController.navigateUp() }
        },
        // Exclude nav bar for the login view
        contentWindowInsets = WindowInsets.ime
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startScreen.name,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            appConfig.loginConfig.let { cfg ->
                if (cfg is UserLoginConfig<*>) {
                    composable(route = AppScreen.Login.name) {
                        cfg.StartScreen()
                    }
                }
            }
            composable(
                route = AppScreen.Main.name,
                exitTransition = {
                    when (targetState.destination.route) {
                        AppScreen.Settings.name -> slideOutFrom(Dir.Right)
                        else -> fadeOut()
                    }
                },
                popEnterTransition = {
                    when (initialState.destination.route) {
                        AppScreen.Settings.name -> slideInTo(Dir.Right)
                        else -> fadeIn()
                    }
                }
            ) {
                MainScreen(
                    mainViewModel,
                    migrationViewModel,
                    uiFlow
                )
            }
            composable(
                route = AppScreen.Settings.name,
                enterTransition = { slideInTo(Dir.Left) },
                popExitTransition = { slideOutFrom(Dir.Left) }
            ) {
                val vm = viewModel<SettingsViewModel>(factory = factory)
                SettingsScreen(
                    appConfig,
                    vm,
                    themeViewModel,
                    migrationViewModel,
                    uiFlow
                )
            }
        }
    }
}
