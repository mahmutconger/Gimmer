package com.mcclabs.gimmer.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mcclabs.gimmer.Language
import com.mcclabs.gimmer.R
import com.mcclabs.gimmer.data.Exercise
import com.mcclabs.gimmer.data.WorkoutCatalog
import com.mcclabs.gimmer.data.WorkoutDay
import com.mcclabs.gimmer.withLanguage

internal val LocalExerciseLanguage = staticCompositionLocalOf { Language.Turkish }

@Composable
internal fun exerciseContext(): Context {
    val context = LocalContext.current
    val language = LocalExerciseLanguage.current
    return remember(context, language) { context.withLanguage(language) }
}

internal fun Exercise.localizedName(language: Language): String {
    val catalog = WorkoutCatalog.exercisesById[id]
    return if (language == Language.English && displayName == catalog?.displayName) catalog.name else displayName
}

internal fun loggedExerciseName(
    name: String,
    id: String?,
    exercises: Collection<Exercise>,
    language: Language,
): String {
    val exercise = if (id != null) {
        exercises.firstOrNull { it.id == id } ?: WorkoutCatalog.exercisesById[id]
    } else {
        exercises.firstOrNull { it.name == name || it.displayName == name }
            ?: WorkoutCatalog.exercisesByName[name]
            ?: WorkoutCatalog.exercisesById.values.firstOrNull { it.displayName == name }
    }
    return exercise?.localizedName(language) ?: name
}

@Composable
internal fun exerciseGroup(name: String): String {
    val label = when (name) {
        "Göğüs" -> R.string.group_chest
        "Sırt" -> R.string.group_back
        "Omuz" -> R.string.group_shoulders
        "Ön Kol" -> R.string.group_biceps
        "Arka Kol" -> R.string.group_triceps
        "Bacak" -> R.string.group_legs
        "Karın" -> R.string.group_abs
        "Diğer" -> R.string.group_other
        else -> return name
    }
    return exerciseContext().getString(label)
}

@Composable
internal fun workoutDayName(name: String): String {
    val label = when (name) {
        "Pazartesi" -> R.string.monday
        "Salı" -> R.string.tuesday
        "Perşembe" -> R.string.thursday
        "Cuma" -> R.string.friday
        "Cumartesi" -> R.string.saturday
        else -> return name
    }
    return stringResource(label)
}

@Composable
internal fun WorkoutDay.localizedProgramName(): String {
    val label = when (programName) {
        "Üst Vücut" -> R.string.program_upper_body
        "Alt Vücut A" -> R.string.program_lower_body
        "İtiş" -> R.string.program_push
        "Çekiş" -> R.string.program_pull
        "Bacak B" -> R.string.program_legs
        else -> return programName
    }
    return stringResource(label)
}
