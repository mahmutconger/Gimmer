package com.mcclabs.gimmer

import com.mcclabs.gimmer.data.RoutineCodec
import com.mcclabs.gimmer.data.WorkoutCatalog
import org.junit.Assert.*
import org.junit.Test

class RoutineCodecTest {
    @Test fun exportedDefaultsRoundTripWithTurkishLabelsAndStableKeys() {
        val program = WorkoutCatalog.days
        val json = RoutineCodec.encode(program)
        assertTrue(json.contains("Makinede göğüs itişi"))
        assertFalse(json.contains("Machine Chest Press"))
        assertEquals(program, RoutineCodec.decode(json))
        assertEquals(program, RoutineCodec.decode("{\"routine\":$json}"))
    }

    @Test fun pairsRemainAdjacentAndOlderProgramsKeepTheirExerciseOrder() {
        for (day in WorkoutCatalog.days) {
            assertTrue(day.pairs.isNotEmpty())
            for (pair in day.pairs) {
                val index = day.exercises.indexOfFirst { it.id == pair.firstId }
                assertEquals(pair.secondId, day.exercises[index + 1].id)
            }
        }
        val legacy = WorkoutCatalog.days.map { it.copy(pairs = emptyList()) }
        val json = RoutineCodec.toJson(legacy)
        json.getAsJsonArray("days").forEach { it.asJsonObject.remove("pairs") }
        assertEquals(legacy, RoutineCodec.decode(json))

        val invalid = RoutineCodec.toJson(WorkoutCatalog.days)
        val pair = invalid.getAsJsonArray("days")[0].asJsonObject.getAsJsonArray("pairs")[0].asJsonArray
        pair.set(1, pair[0])
        assertThrows(IllegalArgumentException::class.java) { RoutineCodec.decode(invalid) }
    }

    @Test fun invalidTargetsAndQuotedEquipmentFlagsAreRejected() {
        val json = RoutineCodec.encode(WorkoutCatalog.days)
        assertThrows(IllegalArgumentException::class.java) {
            RoutineCodec.decode(json.replaceFirst("\"6-10\"", "\"12-8\""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutineCodec.decode(json.replaceFirst("\"isCompound\": true", "\"isCompound\": \"true\""))
        }
    }

    @Test fun repeatedExerciseCanHaveDifferentTargetsButNotDifferentEquipment() {
        val base = WorkoutCatalog.days.first()
        val other = base.copy(id = "another", name = "Çarşamba",
            exercises = base.exercises.map { it.copy(targetSets = 4) })
        assertEquals(listOf(base, other), RoutineCodec.decode(RoutineCodec.encode(listOf(base, other))))
        val inconsistent = other.copy(exercises = other.exercises.map { it.copy(isBarbell = !it.isBarbell) })
        assertThrows(IllegalArgumentException::class.java) {
            RoutineCodec.decode(RoutineCodec.encode(listOf(base, inconsistent)))
        }
    }
}
