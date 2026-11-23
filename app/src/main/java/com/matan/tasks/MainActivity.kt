package com.matan.tasks

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
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

    private var voiceCallback: ((String) -> Unit)? = null

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) voiceCallback?.invoke(text)
        }
        voiceCallback = null
    }

    private fun startVoice(cb: (String) -> Unit) {
        voiceCallback = cb
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "דבר, אני מקשיב…")
        }
        try {
            voiceLauncher.launch(i)
        } catch (e: ActivityNotFoundException) {
            voiceCallback = null
            vm.message = "זיהוי דיבור לא זמין במכשיר"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App(
                        vm = vm,
                        requestCalendar = {
                            permLauncher.launch(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                            )
                        },
                        startVoice = { cb -> startVoice(cb) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}

val Indigo = Color(0xFF5B4CF0)

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
            background = Color(0xFFF4F3FA),
            surface = Color.White,
            surfaceVariant = Color(0xFFEEEEF6),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
