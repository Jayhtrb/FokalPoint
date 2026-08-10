package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.screens.hiltViewModel
import com.example.ui.viewmodel.FokalViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

annotation class Inject


val android.content.Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

val FokalTypography = Typography
val FokalShapes = Shapes()

@Composable
fun FokalAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FokalTypography,
        shapes = FokalShapes,
        content = content
    )
}

@Composable
fun ThemeToggleButton(
    viewModel: FokalViewModel = hiltViewModel()
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    
    IconButton(
        onClick = { viewModel.toggleTheme() }
    ) {
        Icon(
            if (isDarkTheme) 
                Icons.Outlined.WbSunny 
            else 
                Icons.Outlined.Nightlight,
            contentDescription = if (isDarkTheme) "Switch to Light" else "Switch to Dark"
        )
    }
}

// Theme Preferences ViewModel
class ThemeViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {
    private val THEME_KEY = booleanPreferencesKey("dark_theme")
    
    val isDarkTheme = dataStore.data
        .map { preferences -> preferences[THEME_KEY] ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    suspend fun toggleTheme() {
        dataStore.edit { preferences ->
            val current = preferences[THEME_KEY] ?: false
            preferences[THEME_KEY] = !current
        }
    }
}
