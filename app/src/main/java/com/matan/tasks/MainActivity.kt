package com.matan.tasks

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {
    private val vm: TaskViewModel by viewModels()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App(vm = vm, requestCalendar = {
                        permLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        )
                    })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}

val Indigo = Color(0xFF4F46E5)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFA5B4FC),
            onPrimary = Color(0xFF1E1B4B),
            primaryContainer = Color(0xFF3730A3),
            onPrimaryContainer = Color(0xFFE0E7FF),
            secondaryContainer = Color(0xFF312E81),
            onSecondaryContainer = Color(0xFFE0E7FF),
            background = Color(0xFF111118),
            surface = Color(0xFF16161F),
            surfaceVariant = Color(0xFF23232F),
        )
    } else {
        lightColorScheme(
            primary = Indigo,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E7FF),
            onPrimaryContainer = Color(0xFF1E1B4B),
            secondaryContainer = Color(0xFFE0E7FF),
            onSecondaryContainer = Color(0xFF1E1B4B),
            background = Color(0xFFF6F6FB),
            surface = Color.White,
            surfaceVariant = Color(0xFFEEEEF6),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
