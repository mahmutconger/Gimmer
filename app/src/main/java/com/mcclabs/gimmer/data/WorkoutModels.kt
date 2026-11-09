package com.mcclabs.gimmer.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

data class Exercise(
    val id: String,
    val name: String,
    val targetSets: Int,
    val targetReps: String,
    val muscleGroup: String = ExerciseMetadata.muscleGroup(id),
    val isCompound: Boolean = ExerciseMetadata.isCompound(id),
    val isBarbell: Boolean = ExerciseMetadata.isBarbell(id),
    val displayName: String = ExerciseMetadata.turkishName(id) ?: name,
)

data class WorkoutDay(
    val id: String,
    val name: String,
    val programName: String,
    val exercises: List<Exercise>,
    val pairs: List<ExercisePair> = emptyList(),
)

data class ExercisePair(val firstId: String, val secondId: String)

internal fun WorkoutDay.withPairs(vararg pairIds: Pair<String, String>): WorkoutDay {
    val pairs = pairIds.map { (first, second) -> ExercisePair(first, second) }
    val byFirst = pairs.associateBy { it.firstId }
    val secondIds = pairs.map { it.secondId }.toSet()
    val ordered = exercises.filterNot { it.id in secondIds }.flatMap { exercise ->
        val pair = byFirst[exercise.id]
        if (pair == null) listOf(exercise)
        else listOf(exercise, exercises.first { it.id == pair.secondId })
    }
    return copy(exercises = ordered, pairs = pairs)
}

@Entity(
    tableName = "workout_logs",
    indices = [Index("exerciseName"), Index("dateTimestamp"), Index("exerciseId", "dateTimestamp")],
)
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseName: String,
    @androidx.room.ColumnInfo(defaultValue = "'Diğer'")
    val muscleGroup: String = "Diğer",
    @androidx.room.ColumnInfo(defaultValue = "''")
    val dayName: String = "",
    val note: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isWarmup: Boolean = false,
    val rir: Int? = null,
    val weight: Double,
    val sets: Int,
    val reps: Int,
    val dateTimestamp: Long,
    val exerciseId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isCompound: Boolean = false,
)

object WorkoutCatalog {
    val days = listOf(
        WorkoutDay(
            id = "monday",
            name = "Pazartesi",
            programName = "Üst Vücut",
            exercises = listOf(
                Exercise("machine_chest_press", "Machine Chest Press", 3, "6-10"),
                Exercise("lat_pulldown", "Lat Pulldown", 3, "8-12"),
                Exercise("incline_db_press", "Incline DB Press", 3, "8-12"),
                Exercise("seated_cable_row", "Seated Cable Row", 3, "8-12"),
                Exercise("db_lateral_raise", "DB Lateral Raise", 3, "12-15"),
                Exercise("incline_db_curl", "Incline DB Curl", 3, "10-15"),
                Exercise("overhead_cable_triceps_ext", "Overhead Cable Triceps Ext", 3, "10-15"),
                Exercise("hammer_curl", "Hammer Curl", 3, "10-15"),
            ),
        ).withPairs(
            "machine_chest_press" to "lat_pulldown",
            "incline_db_press" to "seated_cable_row",
            "db_lateral_raise" to "incline_db_curl",
            "overhead_cable_triceps_ext" to "hammer_curl",
        ),
        WorkoutDay(
            id = "tuesday",
            name = "Salı",
            programName = "Alt Vücut A",
            exercises = listOf(
                Exercise("hack_squat", "Hack Squat", 3, "6-10"),
                Exercise("romanian_deadlift", "Romanian Deadlift", 3, "6-10"),
                Exercise("leg_extension", "Leg Extension", 3, "10-15"),
                Exercise("seated_leg_curl", "Seated Leg Curl", 3, "10-15"),
                Exercise("hip_adduction", "Hip Adduction", 3, "12-15"),
                Exercise("standing_calf_raise", "Standing Calf Raise", 4, "8-12"),
                Exercise("reverse_crunch", "Reverse Crunch", 3, "12-20"),
                Exercise("cable_crunch", "Cable Crunch", 3, "10-15"),
            ),
        ).withPairs(
            "leg_extension" to "seated_leg_curl",
            "standing_calf_raise" to "reverse_crunch",
        ),
        WorkoutDay(
            id = "thursday",
            name = "Perşembe",
            programName = "İtiş",
            exercises = listOf(
                Exercise("incline_smith_press", "Incline Smith Press", 3, "6-10"),
                Exercise("assisted_dip", "Assisted Dip", 3, "8-12"),
                Exercise("high_to_low_cable_fly", "High-to-Low Cable Fly", 3, "10-15"),
                Exercise("low_to_high_cable_fly", "Low-to-High Cable Fly", 3, "10-15"),
                Exercise("seated_db_shoulder_press", "Seated DB Shoulder Press", 3, "8-12"),
                Exercise("cable_lateral_raise", "Cable Lateral Raise", 3, "12-15"),
                Exercise("rope_triceps_pushdown", "Rope Triceps Pushdown", 3, "10-15"),
                Exercise("overhead_db_extension", "Overhead DB Extension", 3, "10-15"),
                Exercise("pallof_press", "Pallof Press", 3, "10-15"),
            ),
        ).withPairs(
            "high_to_low_cable_fly" to "rope_triceps_pushdown",
            "low_to_high_cable_fly" to "pallof_press",
        ),
        WorkoutDay(
            id = "friday",
            name = "Cuma",
            programName = "Çekiş",
            exercises = listOf(
                Exercise("assisted_pull_up", "Assisted Pull-up", 3, "6-10"),
                Exercise("chest_supported_row", "Chest-Supported Row", 3, "8-12"),
                Exercise("straight_arm_pulldown", "Straight-Arm Pulldown", 3, "10-15"),
                Exercise("face_pull", "Face Pull", 3, "12-15"),
                Exercise("reverse_pec_deck", "Reverse Pec Deck", 3, "12-15"),
                Exercise("db_shrug", "DB Shrug", 3, "10-15"),
                Exercise("ez_preacher_curl", "EZ Preacher Curl", 3, "8-12"),
                Exercise("reverse_grip_ez_curl", "Reverse-Grip EZ Curl", 3, "10-15"),
                Exercise("wrist_curl_reverse", "Wrist Curl + Reverse", 3, "12-20"),
            ),
        ).withPairs(
            "reverse_pec_deck" to "ez_preacher_curl",
        ),
        WorkoutDay(
            id = "saturday",
            name = "Cumartesi",
            programName = "Bacak B",
            exercises = listOf(
                Exercise("leg_press", "Leg Press", 3, "8-12"),
                Exercise("hip_thrust", "Hip Thrust", 3, "8-12"),
                Exercise("bulgarian_split_squat", "Bulgarian Split Squat", 3, "8-12"),
                Exercise("lying_leg_curl", "Lying Leg Curl", 3, "10-15"),
                Exercise("hip_abduction", "Hip Abduction", 3, "12-15"),
                Exercise("seated_calf_raise", "Seated Calf Raise", 4, "10-15"),
                Exercise("hanging_knee_raise", "Hanging Knee Raise", 3, "10-15"),
                Exercise("side_plank", "Side Plank", 3, "30-45 sn"),
                Exercise("ab_wheel", "Ab Wheel", 3, "8-12"),
            ),
        ).withPairs(
            "lying_leg_curl" to "hanging_knee_raise",
            "hip_abduction" to "ab_wheel",
            "seated_calf_raise" to "side_plank",
        ),
    )

    val exercisesById = days.flatMap { it.exercises }.associateBy { it.id }
    val exercisesByName = exercisesById.values.associateBy { it.name }

    fun displayName(name: String): String = exercisesByName[name]?.displayName ?: name
}
