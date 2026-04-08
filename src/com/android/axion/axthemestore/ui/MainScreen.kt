/*
 * Copyright (C) 2025 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.axion.axthemestore.ui

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.android.axion.axthemestore.viewmodel.ThemeStoreViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainScreen(viewModel: ThemeStoreViewModel) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkInstallStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    DisposableEffect(context) {
        val packageReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                viewModel.loadThemes(forceRefresh = true)
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        
        context.registerReceiver(packageReceiver, filter)
        onDispose {
            context.unregisterReceiver(packageReceiver)
        }
    }
    
    val motionScheme = MaterialTheme.motionScheme
    NavHost(
        navController = navController,
        startDestination = "themes",
        enterTransition = { fadeIn(motionScheme.defaultEffectsSpec()) },
        exitTransition = { fadeOut(motionScheme.defaultEffectsSpec()) },
        popEnterTransition = { fadeIn(motionScheme.defaultEffectsSpec()) },
        popExitTransition = { fadeOut(motionScheme.defaultEffectsSpec()) }
    ) {
        composable("themes") {
            ThemeStoreScreen(
                viewModel = viewModel,
                onThemeClick = { theme ->
                    navController.navigate("detail/${theme.id}")
                },
                onNavigateToCategory = { categoryId ->
                    navController.navigate("category/$categoryId")
                },
                onNavigateToInstalledComponents = {
                    navController.navigate("installed_components")
                }
            )
        }

        composable("installed_components") {
            InstalledComponentsScreen(
                viewModel = viewModel,
                storeSection = viewModel.storeSection,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = "category/{categoryId}",
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId")
            categoryId?.let {
                CategoryThemesScreen(
                    categoryId = it,
                    viewModel = viewModel,
                    onThemeClick = { theme ->
                        navController.navigate("detail/${theme.id}")
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = "detail/{themeId}",
            arguments = listOf(navArgument("themeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val themeId = backStackEntry.arguments?.getString("themeId")
            val uiState = viewModel.uiState.value
            val theme = uiState.themes.find { it.id == themeId }

            theme?.let {
                ThemeDetailScreen(
                    theme = it,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
