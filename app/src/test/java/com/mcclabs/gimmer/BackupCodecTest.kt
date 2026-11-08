package com.mcclabs.gimmer

import com.mcclabs.gimmer.data.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val log = WorkoutLog(id = 42, exerciseName = "Machine Chest Press",
        muscleGroup = "Göğüs", dayName = "Pazartesi", weight = 52.5, sets = 3, reps = 10,
        note = "Kürek kemiklerini sabitle.\nKontrollü iniş.", dateTimestamp = 1794000000000)

    @Test fun backupRoundTripPreservesSessionAndTurkishNotes() {
        val encoded = BackupCodec.encode(listOf(log))
        assertEquals(listOf(log.copy(id = 0)), BackupCodec.decode(encoded))
        assertTrue(encoded.contains("dateTimestamp"))
        assertTrue(encoded.contains("weightUnit"))
    }

    @Test fun malformedBackupIsRejectedBeforeImport() {
        listOf(
            BackupCodec.encode(listOf(log)).replace("52.5", "-1"),
            BackupCodec.encode(listOf(log)).replace("\"sets\": 3", "\"sets\": 3.5"),
            BackupCodec.encode(listOf(log)).replace("\"schemaVersion\": 3", "\"schemaVersion\": 99"),
            "{}",
        ).forEach { json -> assertTrue(runCatching { BackupCodec.decode(json) }.isFailure) }
    }

    @Test fun allCatalogExercisesHaveMuscleMapping() {
        assertTrue(WorkoutCatalog.days.flatMap { it.exercises }.none { it.muscleGroup == "Diğer" })
    }

    @Test fun migratedUnknownExerciseCanBeBackedUpWithoutLosingItsHistory() {
        val unknown = log.copy(exerciseName = "Eski Hareket", muscleGroup = "Diğer", dayName = "")
        assertEquals(listOf(unknown.copy(id = 0)), BackupCodec.decode(BackupCodec.encode(listOf(unknown))))
    }

    @Test fun versionThreeIncludesBodyweightsAndSetMetadata() {
        val set = log.copy(sets = 1, isWarmup = true, rir = 4)
        val weights = listOf(BodyweightLog("2026-11-06", 65.0), BodyweightLog("2026-11-07", 65.2))
        val result = BackupCodec.decodeSnapshot(BackupCodec.encode(listOf(set), weights))
        assertEquals(listOf(set.copy(id = 0)), result.workouts)
        assertEquals(weights, result.bodyweights)
        assertTrue(runCatching {
            BackupCodec.decodeSnapshot(BackupCodec.encode(listOf(set.copy(rir = 5)), weights))
        }.isFailure)
    }

    @Test fun legacyJsonDefaultsToWorkingSetsWithUnknownRir() {
        val legacy = BackupCodec.encode(listOf(log)).replace("\"schemaVersion\": 3", "\"schemaVersion\": 2")
        val result = BackupCodec.decodeSnapshot(legacy)
        assertFalse(result.workouts.single().isWarmup)
        assertNull(result.workouts.single().rir)
        assertTrue(result.bodyweights.isEmpty())
    }
}
