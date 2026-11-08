package com.mcclabs.gimmer.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.mcclabs.gimmer.nutrition.LoggedMeal
import com.mcclabs.gimmer.nutrition.decodeNutrition
import com.mcclabs.gimmer.nutrition.exportNutrition
import java.time.Instant
import java.time.ZoneId

fun requireValidLog(log: WorkoutLog) {
    require(log.exerciseName.isNotBlank() && log.exerciseName.length <= 200)
    require(log.muscleGroup.isNotBlank() && log.muscleGroup.length <= 100 && log.dayName.length <= 100)
    require(log.weight.isFinite() && log.weight in 0.0..10000.0)
    require(log.sets in 1..1000 && log.reps in 1..10000)
    require(log.rir == null || log.rir in 0..4)
    require(log.dateTimestamp > 0 && (log.note?.length ?: 0) <= 2000)
    require(log.exerciseId == null || log.exerciseId.length in 1..80)
}

data class BackupSnapshot(
    val workouts: List<WorkoutLog>,
    val bodyweights: List<BodyweightLog>,
    val meals: List<LoggedMeal> = emptyList(),
    val dailyConditions: List<DailyCondition> = emptyList(),
    val routine: List<WorkoutDay>? = null,
)

object BackupCodec {
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()
    fun encode(
        logs: List<WorkoutLog>,
        bodyweights: List<BodyweightLog> = emptyList(),
        meals: List<LoggedMeal> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
        dailyConditions: List<DailyCondition> = emptyList(),
        routine: List<WorkoutDay>? = null,
    ): String {
        val sessions = logs.sortedWith(compareBy({ it.dateTimestamp }, { it.id })).map {
            gson.toJsonTree(it).asJsonObject.apply {
                addProperty("date", Instant.ofEpochMilli(it.dateTimestamp).toString())
            }
        }
        return gson.toJson(mapOf("schemaVersion" to 3, "weightUnit" to "kg",
            "sessions" to sessions, "bodyweights" to bodyweights.sortedBy { it.date },
            "nutrition" to exportNutrition(meals, zone), "dailyConditions" to dailyConditions.sortedBy { it.date },
            "routine" to routine?.let(RoutineCodec::toJson)))
    }

    // V2 JSON backups remain importable; unknown legacy RIR is preserved as null.
    fun decodeSnapshot(json: String): BackupSnapshot {
        require(json.toByteArray().size <= 10 * 1024 * 1024)
        val root = JsonParser.parseString(json).asJsonObject
        val version = root.get("schemaVersion").asBigDecimal.intValueExact()
        require(version in 2..3 && root.get("weightUnit").asString == "kg")
        val sessions = root.getAsJsonArray("sessions")
        require(sessions.size() <= 50000)
        val workouts = sessions.map { element ->
            val row = element.asJsonObject
            val exerciseName = row.get("exerciseName").asString
            val catalogExercise = WorkoutCatalog.exercisesByName[exerciseName]
            val warmup = if (version == 3) row.get("isWarmup").also {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean)
            }.asBoolean else false
            WorkoutLog(
                exerciseName = exerciseName,
                muscleGroup = row.get("muscleGroup").asString,
                dayName = row.get("dayName").asString,
                weight = row.get("weight").asDouble,
                sets = row.get("sets").asBigDecimal.intValueExact(),
                reps = row.get("reps").asBigDecimal.intValueExact(),
                note = row.get("note")?.takeUnless { it.isJsonNull }?.asString,
                isWarmup = warmup,
                rir = if (version == 3) row.get("rir")?.takeUnless { it.isJsonNull }
                    ?.asBigDecimal?.intValueExact() else null,
                dateTimestamp = row.get("dateTimestamp").asBigDecimal.longValueExact(),
                exerciseId = row.get("exerciseId")?.let { if (it.isJsonNull) null else it.asString }
                    ?: catalogExercise?.id.takeIf { !row.has("exerciseId") },
                isCompound = row.get("isCompound")?.asBoolean ?: catalogExercise?.isCompound ?: false,
            ).also(::requireValidLog)
        }
        val bodyweights = if (version == 3) root.getAsJsonArray("bodyweights") else JsonArray()
        require(bodyweights.size() <= 50000)
        val weights = bodyweights.map {
            BodyweightLog(it.asJsonObject.get("date").asString,
                it.asJsonObject.get("weight").asDouble).also(::requireValidBodyweight)
        }
        require(weights.map { it.date }.distinct().size == weights.size)
        val meals = root.getAsJsonArray("nutrition")?.let(::decodeNutrition).orEmpty()
        val conditions = (root.getAsJsonArray("dailyConditions") ?: JsonArray()).also {
            require(it.size() <= 50000)
        }.map { element ->
            val row = element.asJsonObject
            DailyCondition(row.get("date").asString, row.get("sleepMinutes").asBigDecimal.intValueExact())
                .also { it.requireValid() }
        }
        require(conditions.map { it.date }.distinct().size == conditions.size)
        val routine = root.get("routine")?.takeUnless { it.isJsonNull }?.asJsonObject?.let(RoutineCodec::decode)
        return BackupSnapshot(workouts, weights, meals, conditions, routine)
    }

    fun decode(json: String): List<WorkoutLog> = decodeSnapshot(json).workouts
}
