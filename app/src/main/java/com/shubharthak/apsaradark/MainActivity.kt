package com.shubharthak.apsaradark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.shubharthak.apsaradark.ui.navigation.AppNavigation
import com.shubharthak.apsaradark.ui.theme.ApsaraDarkTheme

class MainActivity : ComponentActivity() {

    /** True when the app was launched via ACTION_ASSIST (power-button long-press). */
    private val startInLiveMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if launched as the default assistant
        startInLiveMode.value = intent?.action == Intent.ACTION_ASSIST

        setContent {
            ApsaraDarkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startInLiveMode = startInLiveMode.value)
                }
            }
        }
    }

    /** Handle re-launch while activity is already running (singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_ASSIST) {
            startInLiveMode.value = true
        }
    }
}
