package com.astralstream.player.features

import android.content.Context
import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor(
    private val context: Context
) {
    private var countDownTimer: CountDownTimer? = null
    
    private val _timerState = MutableStateFlow(SleepTimerState())
    val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()
    
    // Predefined timer options in minutes
    val timerOptions = listOf(
        TimerOption("Off", 0),
        TimerOption("15 minutes", 15),
        TimerOption("30 minutes", 30),
        TimerOption("45 minutes", 45),
        TimerOption("1 hour", 60),
        TimerOption("1.5 hours", 90),
        TimerOption("2 hours", 120),
        TimerOption("End of video", -1) // Special case
    )
    
    fun startTimer(minutes: Int, onTimerEnd: () -> Unit) {
        cancelTimer()
        
        if (minutes <= 0) {
            _timerState.value = SleepTimerState()
            return
        }
        
        val totalMillis = minutes * 60 * 1000L
        
        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timerState.value = SleepTimerState(
                    isActive = true,
                    remainingTimeMillis = millisUntilFinished,
                    totalTimeMillis = totalMillis,
                    selectedMinutes = minutes
                )
            }
            
            override fun onFinish() {
                _timerState.value = SleepTimerState()
                onTimerEnd()
            }
        }.start()
        
        _timerState.value = SleepTimerState(
            isActive = true,
            remainingTimeMillis = totalMillis,
            totalTimeMillis = totalMillis,
            selectedMinutes = minutes
        )
    }
    
    fun startEndOfVideoTimer(videoDurationMillis: Long, currentPositionMillis: Long, onTimerEnd: () -> Unit) {
        cancelTimer()
        
        val remainingMillis = videoDurationMillis - currentPositionMillis
        if (remainingMillis <= 0) {
            onTimerEnd()
            return
        }
        
        countDownTimer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timerState.value = SleepTimerState(
                    isActive = true,
                    remainingTimeMillis = millisUntilFinished,
                    totalTimeMillis = remainingMillis,
                    selectedMinutes = -1,
                    isEndOfVideo = true
                )
            }
            
            override fun onFinish() {
                _timerState.value = SleepTimerState()
                onTimerEnd()
            }
        }.start()
        
        _timerState.value = SleepTimerState(
            isActive = true,
            remainingTimeMillis = remainingMillis,
            totalTimeMillis = remainingMillis,
            selectedMinutes = -1,
            isEndOfVideo = true
        )
    }
    
    fun extendTimer(additionalMinutes: Int) {
        val currentState = _timerState.value
        if (currentState.isActive && !currentState.isEndOfVideo) {
            val newTotalMinutes = currentState.selectedMinutes + additionalMinutes
            startTimer(newTotalMinutes) {
                // Use the same callback as before
            }
        }
    }
    
    fun cancelTimer() {
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = SleepTimerState()
    }
    
    fun getFormattedRemainingTime(): String {
        val millis = _timerState.value.remainingTimeMillis
        val minutes = (millis / 1000 / 60).toInt()
        val seconds = ((millis / 1000) % 60).toInt()
        
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val mins = minutes % 60
                String.format("%d:%02d:%02d", hours, mins, seconds)
            }
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }
    
    fun release() {
        cancelTimer()
    }
}

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingTimeMillis: Long = 0L,
    val totalTimeMillis: Long = 0L,
    val selectedMinutes: Int = 0,
    val isEndOfVideo: Boolean = false
) {
    val progress: Float
        get() = if (totalTimeMillis > 0) {
            (totalTimeMillis - remainingTimeMillis).toFloat() / totalTimeMillis
        } else 0f
}

data class TimerOption(
    val label: String,
    val minutes: Int
)