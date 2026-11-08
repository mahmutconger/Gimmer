package com.mcclabs.gimmer

import com.mcclabs.gimmer.data.WorkoutLog
import com.mcclabs.gimmer.ui.DailyExerciseProgress
import com.mcclabs.gimmer.ui.PlateCount
import com.mcclabs.gimmer.ui.calculatePlateLoad
import com.mcclabs.gimmer.ui.dailyExerciseProgress
import com.mcclabs.gimmer.ui.remainingRestSeconds
import com.mcclabs.gimmer.ui.restSecondsFor
import com.mcclabs.gimmer.ui.stepWeight
import com.mcclabs.gimmer.ui.workingSetVolume
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SetTrackingTest {
    @Test fun plateLoadingUsesBothSidesAndNeverExceedsTheTarget() {
        val load = requireNotNull(calculatePlateLoad(83.1))
        assertEquals(82.5, load.totalWeight, 0.0)
        assertEquals(listOf(PlateCount(20.0, 1), PlateCount(10.0, 1), PlateCount(1.25, 1)), load.plates)
        assertTrue(requireNotNull(calculatePlateLoad(20.0)).plates.isEmpty())
        assertNull(calculatePlateLoad(19.99))
    }

    @Test fun stepperKeepsTurkishDecimalsAndRespectsWeightLimits() {
        assertEquals("52,5", stepWeight("50", 2.5))
        assertEquals("0", stepWeight("1,25", -2.5))
        assertEquals("10000", stepWeight("9999", 2.5))
    }

    @Test fun autoRestUsesTheMovementCategory() {
        assertEquals(180, restSecondsFor(true))
        assertEquals(90, restSecondsFor(false))
    }

    @Test fun warmupVolumeNeverInflatesWorkingStats() {
        val set = WorkoutLog(exerciseName = "Machine Chest Press", weight = 50.0,
            sets = 1, reps = 10, rir = 2, dateTimestamp = 100)
        assertEquals(500.0, workingSetVolume(listOf(set, set.copy(weight = 100.0, isWarmup = true))), 0.0)
        assertEquals(0.0, workingSetVolume(listOf(set.copy(isWarmup = true))), 0.0)
    }
    @Test fun countdownUsesElapsedDeadlineAndRoundsPartialSecondsUp() {
        assertEquals(120, remainingRestSeconds(121000, 1000))
        assertEquals(1, remainingRestSeconds(121000, 120999))
        assertEquals(0, remainingRestSeconds(121000, 121000))
        assertEquals(0, remainingRestSeconds(121000, 125000))
    }

    @Test fun dailyProgressUsesTheSameWorkingSetForWeightAndRepsInTheLocalDay() {
        val set = WorkoutLog(exerciseName = "Machine Chest Press", weight = 50.0, sets = 1, reps = 8,
            dateTimestamp = Instant.parse("2026-11-06T22:00:00Z").toEpochMilli())
        val logs = listOf(
            set.copy(weight = 45.0, reps = 12),
            set,
            set.copy(reps = 9),
            set.copy(weight = 70.0, isWarmup = true),
            set.copy(weight = 55.0, reps = 6,
                dateTimestamp = Instant.parse("2026-11-07T22:00:00Z").toEpochMilli()),
        )
        assertEquals(
            listOf(
                DailyExerciseProgress(LocalDate.parse("2026-11-07"), 50.0, 9),
                DailyExerciseProgress(LocalDate.parse("2026-11-08"), 55.0, 6),
            ),
            dailyExerciseProgress(logs, ZoneId.of("Europe/Istanbul")),
        )
    }
}
