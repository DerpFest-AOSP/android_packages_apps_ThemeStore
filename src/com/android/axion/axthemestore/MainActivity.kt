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

package com.android.axion.axthemestore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.android.axion.axthemestore.R
import com.android.axion.axthemestore.data.model.StoreSection
import com.android.axion.axthemestore.ui.MainScreen
import com.android.axion.axthemestore.ui.theme.AxThemeStoreTheme
import com.android.axion.axthemestore.viewmodel.ThemeStoreViewModel
import com.android.axion.axthemestore.viewmodel.ThemeStoreViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: ThemeStoreViewModel by viewModels {
        ThemeStoreViewModelFactory(application, StoreSection.fromIntent(intent))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setTitleForSection(StoreSection.fromIntent(intent))

        setContent {
            AxThemeStoreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun setTitleForSection(section: StoreSection) {
        val titleRes = when (section) {
            StoreSection.NetworkIcons -> R.string.store_section_network_icons
            StoreSection.BatteryStyles -> R.string.store_section_battery_styles
            StoreSection.BackGesture -> R.string.store_section_back_gesture
            StoreSection.ChargingAnimation -> R.string.store_section_charging_animation
            StoreSection.StatusBarCustomization -> R.string.store_section_status_bar_customization
            StoreSection.All -> R.string.store_title_all
        }
        setTitle(titleRes)
    }
}
