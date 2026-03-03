package com.zanoni.lardr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.zanoni.lardr.data.local.PreferencesManager
import com.zanoni.lardr.ui.navigation.NavGraph
import com.zanoni.lardr.ui.theme.LardrTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by preferencesManager.themeMode.collectAsStateWithLifecycle(
                initialValue = com.zanoni.lardr.ui.theme.ThemeMode.SYSTEM
            )

            LardrTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}