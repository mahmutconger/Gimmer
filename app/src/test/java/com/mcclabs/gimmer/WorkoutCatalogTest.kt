package com.mcclabs.gimmer

import com.mcclabs.gimmer.data.WorkoutCatalog
import com.mcclabs.gimmer.ui.localizedName
import com.mcclabs.gimmer.ui.loggedExerciseName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCatalogTest {
    @Test
    fun catalogContainsFiveDaysAndAllExercises() {
        val days = WorkoutCatalog.days

        assertEquals(
            listOf("Pazartesi", "Salı", "Perşembe", "Cuma", "Cumartesi"),
            days.map { it.name },
        )
        assertEquals(listOf(8, 8, 9, 9, 9), days.map { it.exercises.size })
        assertEquals(43, days.sumOf { it.exercises.size })
    }

    @Test
    fun everyExerciseHasAUniqueIdAndValidTarget() {
        val exercises = WorkoutCatalog.days.flatMap { it.exercises }

        assertEquals(exercises.size, exercises.map { it.id }.distinct().size)
        assertTrue(exercises.all { it.name.isNotBlank() })
        assertTrue(exercises.all { it.targetSets > 0 && it.targetReps.isNotBlank() })
    }

    @Test
    fun catalogNamesFollowTheExerciseLanguageInExistingLogs() {
        val exercise = WorkoutCatalog.exercisesById.getValue("machine_chest_press")
        assertEquals("Makinede göğüs itişi", exercise.localizedName(Language.Turkish))
        assertEquals("Machine Chest Press", exercise.localizedName(Language.English))
        assertEquals(exercise.name, loggedExerciseName(exercise.displayName, null, emptyList(), Language.English))
        assertEquals(exercise.displayName, loggedExerciseName(exercise.name, null, emptyList(), Language.Turkish))
    }

    @Test
    fun customExerciseLabelsSurviveLanguageChanges() {
        val exercise = WorkoutCatalog.exercisesById.getValue("machine_chest_press")
            .copy(displayName = "My chest press")
        for (language in Language.entries) {
            assertEquals("My chest press", exercise.localizedName(language))
            assertEquals("My chest press", loggedExerciseName(exercise.name, exercise.id, listOf(exercise), language))
        }
    }
}
