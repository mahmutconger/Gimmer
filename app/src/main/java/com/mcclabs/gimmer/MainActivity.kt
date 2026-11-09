package com.mcclabs.gimmer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mcclabs.gimmer.ui.GimmerApp
import com.mcclabs.gimmer.ui.theme.GimmerTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLanguage(LanguagePreferences(newBase).appLanguage))
    }

    internal fun setAppLanguage(language: Language) {
        val preferences = LanguagePreferences(this)
        if (preferences.appLanguage == language) return
        preferences.appLanguage = language
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            GimmerTheme {
                GimmerApp()
            }
        }
    }
}
