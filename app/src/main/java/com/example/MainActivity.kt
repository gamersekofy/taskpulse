package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.liveupdates.WorkSessionNotificationHelper
import com.example.ui.TaskPulseRootApp
import com.example.ui.theme.TaskPulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WorkSessionNotificationHelper.ensureChannel(this)
        setContent {
            TaskPulseTheme {
                TaskPulseRootApp()
            }
        }
    }
}
