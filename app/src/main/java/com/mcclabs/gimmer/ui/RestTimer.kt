package com.mcclabs.gimmer.ui

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import com.mcclabs.gimmer.data.Exercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal fun remainingRestSeconds(deadline: Long, now: Long): Int =
    ((deadline - now).coerceAtLeast(0) + 999).div(1000).coerceAtMost(180).toInt()

internal fun restSecondsFor(isCompound: Boolean): Int = if (isCompound) 180 else 90

class RestTimer(
    private val savedState: SavedStateHandle,
    scope: CoroutineScope,
    private val onFinished: () -> Unit,
) {
    private val deadline = savedState.getStateFlow("restDeadline", 0L)
    private val _remaining = MutableStateFlow(0)
    private val _finished = MutableStateFlow(false)
    val remaining = _remaining.asStateFlow()
    val finished = _finished.asStateFlow()
    val active = remaining.map { it > 0 }.distinctUntilChanged()
    val preset = savedState.getStateFlow("restPreset", 120)
    val duration = savedState.getStateFlow("restDuration", preset.value)
    val exerciseId = savedState.getStateFlow<String?>("restExerciseId", null)
    val exerciseName = savedState.getStateFlow<String?>("restExerciseName", null)

    init {
        scope.launch {
            deadline.collectLatest { target ->
                if (target <= 0L) return@collectLatest
                // A reboot can reset elapsedRealtime.
                if (target > SystemClock.elapsedRealtime() + 180_000) {
                    cancel()
                    return@collectLatest
                }
                while (target == deadline.value) {
                    val now = SystemClock.elapsedRealtime()
                    val seconds = remainingRestSeconds(target, now)
                    _remaining.value = seconds
                    if (seconds == 0) {
                        savedState["restDeadline"] = 0L
                        _finished.value = true
                        onFinished()
                        break
                    }
                    delay(target - now - (seconds - 1) * 1000L)
                }
            }
        }
    }

    fun selectPreset(seconds: Int) {
        require(seconds in presets)
        if (seconds == preset.value && (_remaining.value == 0 || seconds == duration.value)) return
        savedState["restPreset"] = seconds
        if (_remaining.value > 0) start()
        else _finished.value = false
    }

    fun start() = start(preset.value, null)

    fun startFor(exercise: Exercise) = start(restSecondsFor(exercise.isCompound), exercise.displayName, exercise.id)

    fun restart() = start(duration.value, exerciseName.value, exerciseId.value)

    private fun start(seconds: Int, name: String?, id: String? = null) {
        _finished.value = false
        savedState["restDuration"] = seconds
        savedState["restExerciseId"] = id
        savedState["restExerciseName"] = name
        savedState["restDeadline"] = SystemClock.elapsedRealtime() + seconds * 1000L
        _remaining.value = seconds
    }

    fun cancel() {
        savedState["restDeadline"] = 0L
        _remaining.value = 0
        _finished.value = false
        savedState["restExerciseId"] = null
        savedState["restExerciseName"] = null
    }

    companion object {
        val presets = listOf(90, 120, 180)
    }
}
