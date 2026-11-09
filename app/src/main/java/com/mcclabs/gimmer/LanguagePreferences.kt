package com.mcclabs.gimmer

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

internal enum class Language(val locale: Locale, val label: String) {
    Turkish(Locale.forLanguageTag("tr-TR"), "Türkçe"),
    English(Locale.forLanguageTag("en-US"), "English"),
}

internal class LanguagePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("languages", Context.MODE_PRIVATE)

    var appLanguage: Language
        get() = read("app")
        set(value) = write("app", value)

    var exerciseLanguage: Language
        get() = read("exercises")
        set(value) = write("exercises", value)

    private fun read(key: String): Language =
        Language.entries.firstOrNull { it.locale.toLanguageTag() == preferences.getString(key, null) }
            ?: Language.Turkish

    private fun write(key: String, value: Language) {
        preferences.edit { putString(key, value.locale.toLanguageTag()) }
    }
}

internal fun Context.withLanguage(language: Language): Context {
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(language.locale))
    return createConfigurationContext(configuration)
}
