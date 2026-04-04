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

package com.android.axion.axthemestore.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.axion.axthemestore.data.model.Theme
import com.android.axion.axthemestore.data.model.ThemeCategory
import com.android.axion.axthemestore.data.model.ThemeSelectionState
import com.android.axion.axthemestore.data.repository.ThemeRepository
import com.android.axion.axthemestore.engine.ThemeEngineProxy
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.*

class ThemeStoreViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "ThemeStoreViewModel"
        private const val PREFS_NAME = "theme_store_prefs"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val MAX_SEARCH_HISTORY = 10
    }
    
    private val repository = ThemeRepository(application)
    private val themeEngineProxy = ThemeEngineProxy(application)
    private val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _uiState = MutableStateFlow(ThemeStoreUiState())
    val uiState: StateFlow<ThemeStoreUiState> = _uiState.asStateFlow()
    
    private val _themeStates = MutableStateFlow<Map<String, ThemeSelectionState>>(emptyMap())
    val themeStates: StateFlow<Map<String, ThemeSelectionState>> = _themeStates.asStateFlow()
    
    private val _enabledComponents = MutableStateFlow<Set<String>>(emptySet())
    val enabledComponents: StateFlow<Set<String>> = _enabledComponents.asStateFlow()
    
    private val _categoryThemes = MutableStateFlow<Map<String, String>>(emptyMap())
    val categoryThemesState: StateFlow<Map<String, String>> = _categoryThemes.asStateFlow()
    
    private val _pendingComponentChanges = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val pendingComponentChanges: StateFlow<Map<String, Set<String>>> = _pendingComponentChanges.asStateFlow()
    
    private val _initialComponentStates = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val initialComponentStates: StateFlow<Map<String, Set<String>>> = _initialComponentStates.asStateFlow()
    
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()
    
    init {
        loadThemes()
        loadUiStyle()
        refreshComponentStates()
        loadSearchHistory()
    }
    
    private fun refreshComponentStates() {
        _enabledComponents.value = themeEngineProxy.getIconThemeTargets().toSet()
        _categoryThemes.value = themeEngineProxy.getCategoryThemes()
    }

    fun loadUiStyle() {
        viewModelScope.launch {
            val style = themeEngineProxy.getUiStyle()
            _uiState.update { it.copy(currentUiStyle = style) }
        }
    }

    fun setUiStyle(styleId: String) {
        viewModelScope.launch {
            if (themeEngineProxy.setUiStyle(styleId)) {
                _uiState.update { it.copy(currentUiStyle = styleId) }
                updateSelectionStates(_uiState.value.themes)
            }
        }
    }
    
    fun loadThemes(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            repository.fetchThemes(forceRefresh).fold(
                onSuccess = { response ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            themes = response.themes,
                            categories = response.categories,
                            error = null
                        )
                    }
                    updateSelectionStates(response.themes)
                    
                    refreshComponentStates()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load themes", error)
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = error.message ?: "Failed to load themes"
                        ) 
                    }
                }
            )

        }
    }

    fun checkInstallStates() {
        refreshComponentStates()
        val themes = _uiState.value.themes
        if (themes.isNotEmpty()) {
            updateSelectionStates(themes)
        }
    }
    
    private fun updateSelectionStates(themes: List<Theme>) {
        val currentStyle = themeEngineProxy.getUiStyle()
        val enabledThemes = themeEngineProxy.getEnabledThemes()
        
        val states = themes.associate { theme ->
            val state = when {
                theme.isUiStyle -> {
                    if (theme.uiStyleId == currentStyle) {
                        ThemeSelectionState.Active
                    } else {
                        ThemeSelectionState.Inactive
                    }
                }
                theme.isUnified && theme.overlays.isNotEmpty() -> {
                    val packageName = theme.overlays.first().packageName
                    val onDevice = repository.isThemeInstalled(packageName)
                    if (!onDevice) {
                        ThemeSelectionState.Missing
                    } else {
                        val categoryThemes = themeEngineProxy.getCategoryThemes()
                        val targets = theme.overlays.first().targets
                        val isAnyComponentActive = targets.any { target ->
                            categoryThemes[target] == packageName
                        }
                        if (isAnyComponentActive) {
                            ThemeSelectionState.Active
                        } else {
                            ThemeSelectionState.Inactive
                        }
                    }
                }
                else -> {
                    val allOnDevice = theme.overlays.all { repository.isThemeInstalled(it.packageName) }
                    if (theme.overlays.isEmpty() || !allOnDevice) {
                        ThemeSelectionState.Missing
                    } else {
                        val categoryThemes = themeEngineProxy.getCategoryThemes()
                        val isAnyActive = theme.overlays.any { overlay ->
                            enabledThemes[overlay.componentId] == overlay.packageName
                                || categoryThemes[overlay.componentId] == overlay.packageName
                        }
                        if (isAnyActive) {
                            ThemeSelectionState.Active
                        } else {
                            ThemeSelectionState.Inactive
                        }
                    }
                }
            }
            theme.id to state
        }
        _themeStates.value = states
        
        val activeCount = states.values.count { it is ThemeSelectionState.Active }
        Log.d(TAG, "Selection states updated: $activeCount active of ${themes.size} themes")
    }
    
    fun filterByCategory(categoryId: String?) {
        _uiState.update { it.copy(selectedCategory = categoryId) }
    }
    
    fun searchThemes(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        if (query.trim().length >= 2) {
            saveSearchQuery(query.trim())
        }
    }
    
    fun getFilteredThemes(ignoreSearchQuery: Boolean = false): List<Theme> {
        val state = _uiState.value
        var themes = state.themes
        
        state.selectedCategory?.let { category ->
            themes = themes.filter { it.category == category }
        }
        
        if (!ignoreSearchQuery && state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            themes = themes.filter { theme ->
                theme.name.lowercase().contains(query) ||
                theme.description.lowercase().contains(query) ||
                theme.author.lowercase().contains(query)
            }
        }
        
        return themes
    }
    
    fun disableTheme(theme: Theme) {
        viewModelScope.launch {
            if (theme.isUnified) {
                val packageName = theme.overlays.first().packageName
                themeEngineProxy.clearIconTheme()
                themeEngineProxy.clearCategoryThemesForPackage(packageName)
                themeEngineProxy.notifyThemeChangedAfterOverlayChange()
            } else {
                for (overlay in theme.overlays) {
                    themeEngineProxy.clearCategoryTheme(overlay.componentId)
                }
                themeEngineProxy.notifyThemeChangedAfterOverlayChange()
            }

            refreshComponentStates()
            updateSelectionStates(_uiState.value.themes)
            Log.d(TAG, "Disabled theme: ${theme.name}")
        }
    }

    fun applyTheme(theme: Theme) {
        if (theme.isUiStyle) {
            val styleId = theme.uiStyleId
            if (styleId != null) {
                viewModelScope.launch {
                    if (themeEngineProxy.setUiStyle(styleId)) {
                        _uiState.update { it.copy(currentUiStyle = styleId) }
                        updateSelectionStates(_uiState.value.themes)
                        Log.d(TAG, "Activated UI style: $styleId")
                    } else {
                        _themeStates.update { 
                            it + (theme.id to ThemeSelectionState.Error("Failed to activate style")) 
                        }
                    }
                }
            }
            return
        }
        
        if (theme.overlays.isEmpty()) {
            _themeStates.update { 
                it + (theme.id to ThemeSelectionState.Error("No overlays available")) 
            }
            return
        }
        
        viewModelScope.launch {
            if (theme.isUnified) {
                val overlay = theme.overlays.first()

                val targetsToApply = _pendingComponentChanges.value[theme.id] 
                    ?: emptySet()

                if (targetsToApply.isNotEmpty()) {
                    if (themeEngineProxy.applyThemeComponents(overlay.packageName, targetsToApply.toList())) {
                        _pendingComponentChanges.update { it - theme.id }
                        
                        refreshComponentStates()
                        updateSelectionStates(_uiState.value.themes)
                        Log.d(TAG, "Applied unified theme components: ${theme.name}")
                    } else {
                        _themeStates.update { 
                            it + (theme.id to ThemeSelectionState.Error("Failed to apply theme")) 
                        }
                    }
                } else {
                     Log.w(TAG, "Attempted to apply theme ${theme.name} with no targets selected")
                }
            } else {
                var success = true
                for (overlay in theme.overlays) {
                    val category = overlay.componentId
                    if (!themeEngineProxy.setCategoryTheme(category, overlay.packageName)) {
                        success = false
                    }
                }
                if (success) {
                    themeEngineProxy.notifyThemeChangedAfterOverlayChange()
                    refreshComponentStates()
                    updateSelectionStates(_uiState.value.themes)
                    Log.d(TAG, "Applied overlay theme: ${theme.name}")
                } else {
                    _themeStates.update {
                        it + (theme.id to ThemeSelectionState.Error("Failed to apply theme"))
                    }
                }
            }
        }
    }
        
    fun toggleComponent(theme: Theme, componentId: String, enabled: Boolean) {
        if (!theme.isUnified) return
        
        val themeId = theme.id
        
        val isThemeActive = _themeStates.value[themeId] is ThemeSelectionState.Active
        
        val initialState = _initialComponentStates.value[themeId] ?: if (isThemeActive) {
            val themePackage = theme.overlays.firstOrNull()?.packageName
            val currentCategoryThemes = _categoryThemes.value
            
            _enabledComponents.value.filter { componentId ->
                currentCategoryThemes[componentId] == themePackage
            }.intersect(theme.overlays.firstOrNull()?.targets?.toSet() ?: emptySet())
        } else {
            emptySet()
        }.also { initial ->
            _initialComponentStates.update { it + (themeId to initial) }
        }
        
        val currentPending = _pendingComponentChanges.value[themeId] ?: initialState
        
        val newPending = if (enabled) {
            currentPending + componentId
        } else {
            currentPending - componentId
        }
        
        if (newPending == initialState) {
            _pendingComponentChanges.update { it - themeId }
            _initialComponentStates.update { it - themeId }
        } else {
            _pendingComponentChanges.update { it + (themeId to newPending) }
        }
        
        Log.d(TAG, "Pending component toggle: $componentId -> $enabled for ${theme.name}, has actual changes: ${newPending != initialState}")
    }
        
    fun hasPendingChanges(themeId: String): Boolean {
        return _pendingComponentChanges.value.containsKey(themeId)
    }
        
    fun getPendingComponents(themeId: String): Set<String> {
        return _pendingComponentChanges.value[themeId] ?: _enabledComponents.value
    }

    fun applyPendingChanges(theme: Theme) {
        if (!theme.isUnified) return
        
        val themeId = theme.id
        val pending = _pendingComponentChanges.value[themeId] ?: return
        
        viewModelScope.launch {
                val overlay = theme.overlays.first()
                val success = themeEngineProxy.applyThemeComponents(overlay.packageName, pending.toList())
                
                if (success) {
                    _pendingComponentChanges.update { it - themeId }
                    _initialComponentStates.update { it - themeId }
                    refreshComponentStates()
                    updateSelectionStates(_uiState.value.themes)
                    Log.d(TAG, "Applied pending changes for ${theme.name}: $pending")
                } else {
                    Log.e(TAG, "Failed to apply pending changes for ${theme.name}")
                }
        }
    }
        
    fun clearPendingChanges(themeId: String) {
        _pendingComponentChanges.update { it - themeId }
        _initialComponentStates.update { it - themeId }
    }
        
    fun isComponentEnabled(componentId: String): Boolean {
        return _enabledComponents.value.contains(componentId)
    }
        
    fun getEnabledComponents(): List<String> {
        return themeEngineProxy.getIconThemeTargets()
    }
        
    fun applyThemeComponents(theme: Theme, selectedCategories: List<String>) {
        if (!theme.isUnified || theme.overlays.isEmpty()) return
        
        viewModelScope.launch {
            val packageName = theme.overlays.first().packageName
            
            val success = themeEngineProxy.applyThemeComponents(packageName, selectedCategories)
            if (success) {
                Log.d(TAG, "Applied ${selectedCategories.size} categories from ${theme.name}")
                refreshComponentStates()
                updateSelectionStates(_uiState.value.themes)
            } else {
                Log.e(TAG, "Failed to apply theme components from ${theme.name}")
            }
        }
    }
        
    fun getCategoryThemes(): Map<String, String> {
        return _categoryThemes.value
    }
        
    fun isCategoryFromTheme(category: String, packageName: String): Boolean {
        return themeEngineProxy.getCategoryTheme(category) == packageName
    }

    fun getCategoryThemePackage(category: String): String? {
        return themeEngineProxy.getCategoryTheme(category)
    }
    
    fun clearCategoryTheme(category: String) {
        viewModelScope.launch {
            themeEngineProxy.clearCategoryTheme(category)
            themeEngineProxy.notifyThemeChangedAfterOverlayChange()
            refreshComponentStates()
            updateSelectionStates(_uiState.value.themes)
        }
    }
    
    fun clearError(themeId: String) {
        val currentState = _themeStates.value[themeId]
        if (currentState is ThemeSelectionState.Error) {
            updateSelectionStates(_uiState.value.themes)
        }
    }

    private fun loadSearchHistory() {
        val historyJson = sharedPrefs.getString(KEY_SEARCH_HISTORY, null)
        if (historyJson != null) {
            try {
                val jsonArray = org.json.JSONArray(historyJson)
                val history = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    history.add(jsonArray.getString(i))
                }
                _searchHistory.value = history
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load search history", e)
                _searchHistory.value = emptyList()
            }
        }
    }
    
    private fun saveSearchQuery(query: String) {
        val currentHistory = _searchHistory.value.toMutableList()
        
        currentHistory.remove(query)
        
        currentHistory.add(0, query)
        
        if (currentHistory.size > MAX_SEARCH_HISTORY) {
            currentHistory.removeAt(currentHistory.size - 1)
        }
        
        _searchHistory.value = currentHistory
        
        try {
            val jsonArray = org.json.JSONArray(currentHistory)
            sharedPrefs.edit()
                .putString(KEY_SEARCH_HISTORY, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save search history", e)
        }
    }
    
    fun removeSearchHistoryItem(query: String) {
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.remove(query)
        _searchHistory.value = currentHistory
        
        try {
            val jsonArray = org.json.JSONArray(currentHistory)
            sharedPrefs.edit()
                .putString(KEY_SEARCH_HISTORY, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update search history", e)
        }
    }
    
    fun getThemeEngineProxy(): ThemeEngineProxy = themeEngineProxy

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        sharedPrefs.edit()
            .remove(KEY_SEARCH_HISTORY)
            .apply()
    }
}

data class ThemeStoreUiState(
    val isLoading: Boolean = false,
    val themes: List<Theme> = emptyList(),
    val categories: List<ThemeCategory> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val error: String? = null,
    val currentUiStyle: String = ThemeEngineProxy.Companion.UiStyle.AXION
)
