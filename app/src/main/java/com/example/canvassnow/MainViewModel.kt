package com.example.canvassnow

import android.content.res.Resources.getSystem
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.canvassnow.MainViewModel.Companion.SNOWFLAKE_OSCILLATION_DELTA_PX
import com.example.canvassnow.MainViewModel.Companion.SNOWFLAKE_OSCILLATION_SPEED
import com.example.canvassnow.MainViewModel.Companion.SNOWFLAKE_ROTATION_SPEED
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    companion object {
        const val SNOWFLAKE_SIZE_SMALL_DP = 12
        const val SNOWFLAKE_SIZE_MEDIUM_DP = 24
        const val SNOWFLAKE_SIZE_LARGE_DP = 36
        private const val SNOW_GENERATION_INTERVAL_MS = 1L
        private const val SNOWFLAKE_GENERATION_INTENSITY = 0.125f
        private const val SNOWFLAKE_GENERATION_INTENSITY_SHAKE = 0.4f
        private const val SNOWFLAKE_FALLING_SPEED_MODIFIER = 1f
        private const val SNOWFLAKE_FALLING_SPEED_MODIFIER_SHAKE = 1.5f
        private const val SHAKE_INTENSITY_DECREASE_TIME_MS = 7000L
        private const val SNOWFLAKE_STEP_DELAY_MS = 15L
        val SNOWFLAKE_SMALL_VELOCITY_PX_PER_SEC = 140.dpToPx
        val SNOWFLAKE_MEDIUM_VELOCITY_PX_PER_SEC = 110.dpToPx
        val SNOWFLAKE_LARGE_VELOCITY_PX_PER_SEC = 90.dpToPx
        const val SNOWFLAKE_ROTATION_SPEED = 100f
        const val SNOWFLAKE_OSCILLATION_SPEED = 0.08f
        val SNOWFLAKE_OSCILLATION_DELTA_PX = 20.dpToPx
        private const val MAXIMUM_SNOWFLAKE_COUNT = 500
    }

    val snowflakesState = mutableStateOf(SnowfallState())

    private var currentSnowfallIntensity = SNOWFLAKE_GENERATION_INTENSITY
    private var currentSnowfallVelocityModifier = SNOWFLAKE_FALLING_SPEED_MODIFIER

    private var job: Job? = null

    fun startSnowFall(snowLayerWidth: Int, snowLayerHeight: Int) {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.Default) {
            generateSnowFlakes(this, snowLayerWidth)
            stepSnowFlakes(this, snowLayerHeight)
        }
    }

    private fun generateSnowFlakes(coroutineScope: CoroutineScope, snowLayerWidth: Int) {
        coroutineScope.launch {
            while (true) {
                // State update: Generating a snowflake
                if (snowflakesState.value.snowflakes.size < MAXIMUM_SNOWFLAKE_COUNT && Random.nextFloat() < currentSnowfallIntensity) {
                    snowflakesState.value = SnowfallState(
                        snowflakesState.value.snowflakes.map {
                            it.copy()
                        } + generateSnowflake(snowLayerWidth)
                    )
                }
                // After shaking decrease the snowfall intensity back to normal
                if (currentSnowfallIntensity > SNOWFLAKE_GENERATION_INTENSITY) {
                    currentSnowfallIntensity = max(
                        (currentSnowfallIntensity - (SNOWFLAKE_GENERATION_INTENSITY_SHAKE - SNOWFLAKE_GENERATION_INTENSITY) / (SHAKE_INTENSITY_DECREASE_TIME_MS / SNOW_GENERATION_INTERVAL_MS.toFloat())),
                        SNOWFLAKE_GENERATION_INTENSITY
                    )
                }
                // After shaking decrease the speed modifier back to normal
                if (currentSnowfallVelocityModifier > SNOWFLAKE_FALLING_SPEED_MODIFIER) {
                    currentSnowfallVelocityModifier = max(
                        (currentSnowfallVelocityModifier - (SNOWFLAKE_FALLING_SPEED_MODIFIER_SHAKE - SNOWFLAKE_FALLING_SPEED_MODIFIER) / (SHAKE_INTENSITY_DECREASE_TIME_MS / SNOW_GENERATION_INTERVAL_MS.toFloat())),
                        SNOWFLAKE_FALLING_SPEED_MODIFIER
                    )
                }
                delay(SNOW_GENERATION_INTERVAL_MS)
            }
        }
    }

    private fun generateSnowflake(snowLayerWidth: Int): Snowflake {
        val type = SnowFlakeType.entries[Random.nextInt(3)]
        return Snowflake(
            oscillationAngle = Random.nextFloat().times(360f),
            baseX = Random.nextFloat().times(snowLayerWidth),
            y = -SNOWFLAKE_SIZE_LARGE_DP.dpToPx.toFloat(),
            rotationAngle = Random.nextFloat().times(360f),
            type = type,
            speedY = when (type) {
                SnowFlakeType.SMALL -> SNOWFLAKE_SMALL_VELOCITY_PX_PER_SEC
                SnowFlakeType.MEDIUM -> SNOWFLAKE_MEDIUM_VELOCITY_PX_PER_SEC
                SnowFlakeType.LARGE -> SNOWFLAKE_LARGE_VELOCITY_PX_PER_SEC
            } * currentSnowfallVelocityModifier
        )
    }

    private fun stepSnowFlakes(coroutineScope: CoroutineScope, snowLayerHeight: Int) {
        coroutineScope.launch {
            while (true) {
                // State update: copy original snowflake list, step, filter melted ones
                snowflakesState.value = SnowfallState(
                    snowflakesState.value.snowflakes.map {
                        it.copy().apply { step(SNOWFLAKE_STEP_DELAY_MS, snowLayerHeight) }
                    }.filter { snowflake -> !snowflake.isMelted }
                )
                delay(SNOWFLAKE_STEP_DELAY_MS)
            }
        }
    }

    fun increaseSnowfallIntensity() {
        currentSnowfallIntensity = SNOWFLAKE_GENERATION_INTENSITY_SHAKE
        currentSnowfallVelocityModifier = SNOWFLAKE_FALLING_SPEED_MODIFIER_SHAKE
    }
}

data class Snowflake(
    private var oscillationAngle: Float,
    private val baseX: Float,
    var y: Float,
    var rotationAngle: Float,
    val type: SnowFlakeType,
    private val speedY: Float,
    private var remainingMeltingTime: Float = 1000f,
    var isMelted: Boolean = false,
) {
    // Depends on starting x and sinus of osc angle
    val x: Float
        get() {
            return baseX + sin(oscillationAngle.degToRad) * SNOWFLAKE_OSCILLATION_DELTA_PX
        }

    // Melting representation by alpha 1-0 transition
    val alpha: Float
        get() {
            return remainingMeltingTime / 1000f
        }

    fun step(deltaTimeMs: Long, snowLayerHeight: Int) {
        // Step only if not melted
        if (!isMelted) {
            // If snowflake reaches bottom of the canvas, start melting it
            if (y > snowLayerHeight) {
                remainingMeltingTime -= deltaTimeMs
                if (remainingMeltingTime < 0) {
                    isMelted = true
                    remainingMeltingTime = 0f
                }
            } else {
                // Modify y by speed, rotation angle and osc angle
                oscillationAngle += deltaTimeMs * SNOWFLAKE_OSCILLATION_SPEED
                rotationAngle += SNOWFLAKE_ROTATION_SPEED * deltaTimeMs / 1000f
                y += speedY * deltaTimeMs / 1000f
            }
        }
    }
}

enum class SnowFlakeType {
    SMALL, MEDIUM, LARGE
}

data class SnowfallState(
    val snowflakes: List<Snowflake> = listOf()
) {
    override fun equals(other: Any?) = false
}

val Int.pxToDp: Int get() = (this / getSystem().displayMetrics.density).toInt()
val Int.dpToPx: Int get() = (this * getSystem().displayMetrics.density).toInt()
val Float.degToRad: Float get() = this * 0.017453292f