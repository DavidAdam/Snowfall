package com.example.canvassnow

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.canvassnow.MainViewModel.Companion.SNOWFLAKE_SIZE_LARGE_DP
import com.example.canvassnow.MainViewModel.Companion.SNOWFLAKE_SIZE_MEDIUM_DP
import com.example.canvassnow.MainViewModel.Companion.SNOWFLAKE_SIZE_SMALL_DP
import com.example.canvassnow.ui.theme.CanvasSnowTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.absoluteValue
import kotlin.math.max

@AndroidEntryPoint
class MainActivity : ComponentActivity(), SensorEventListener {

    companion object {
        private const val SHAKE_TRIGGER = 15
    }

    private val viewModel by viewModels<MainViewModel>()

    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val accelerometer by lazy { sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) }
    private var gameRotationVectorReading = FloatArray(3)

    // Last 15 values of the sensor reading
    private val lastShakeStrengthValues = ArrayDeque(generateSequence { 0f }.take(15).toList())
    private val lastDrawDelayMs = ArrayDeque(generateSequence { 15L }.take(100).toList())
    private var lastDrawTime = System.currentTimeMillis()
    private var fpsCount = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainContent(viewModel, ::onDrawStarted, fpsCount)
        }
    }

    override fun onStart() {
        super.onStart()
        sensorManager.registerListener(
            this,
            accelerometer,
            50000,
        )
    }

    override fun onStop() {
        super.onStop()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }

    private fun onDrawStarted(time: Long) {
        lastDrawDelayMs.add(time - lastDrawTime)
        lastDrawDelayMs.removeFirst()
        lastDrawTime = time
        fpsCount.value = (1000L / max(lastDrawDelayMs.average(), 1.0)).toInt()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            gameRotationVectorReading = event.values
            lastShakeStrengthValues.add(gameRotationVectorReading[0].absoluteValue + gameRotationVectorReading[1].absoluteValue + gameRotationVectorReading[2].absoluteValue)
            lastShakeStrengthValues.removeFirst()
            if (lastShakeStrengthValues.average() > SHAKE_TRIGGER) {
                viewModel.increaseSnowfallIntensity()
            }
        }
    }
}

@Composable
fun MainContent(
    viewModel: MainViewModel,
    onDrawStarted: (Long) -> Unit,
    fpsCount: MutableIntState
) {
    CanvasSnowTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            SnowCanvas(
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
                onDrawStarted = onDrawStarted,
            )
            Text(
                modifier = Modifier.padding(top = 40.dp),
                text = "FPS: ${fpsCount.value}",
                color = Color.Red,
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
fun SnowCanvas(viewModel: MainViewModel, modifier: Modifier, onDrawStarted: (Long) -> Unit) {
    val snowflakeVectorLarge = ImageVector.vectorResource(id = R.drawable.snowflake_large)
    val snowflakeVectorMedium = ImageVector.vectorResource(id = R.drawable.snowflake_medium)
    val snowflakeVectorSmall = ImageVector.vectorResource(id = R.drawable.snowflake_small)

    val snowflakeSizeLargePx = remember { SNOWFLAKE_SIZE_LARGE_DP.dpToPx.toFloat() }
    val snowflakeSizeMediumPx = remember { SNOWFLAKE_SIZE_MEDIUM_DP.dpToPx.toFloat() }
    val snowflakeSizeSmallPx = remember { SNOWFLAKE_SIZE_SMALL_DP.dpToPx.toFloat() }

    val painterLarge = rememberVectorPainter(image = snowflakeVectorLarge)
    val painterMedium = rememberVectorPainter(image = snowflakeVectorMedium)
    val painterSmall = rememberVectorPainter(image = snowflakeVectorSmall)

    Canvas(
        modifier = modifier
            .clipToBounds()
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                viewModel.startSnowFall(coordinates.size.width, coordinates.size.height)
            }
    ) {
        onDrawStarted.invoke(System.currentTimeMillis())
        viewModel.snowflakesState.value.snowflakes.forEach { snowflake ->
            val snowflakeSizePx: Float
            val snowflakePainter: VectorPainter

            when (snowflake.type) {
                SnowFlakeType.SMALL -> {
                    snowflakeSizePx = snowflakeSizeSmallPx
                    snowflakePainter = painterSmall
                }

                SnowFlakeType.MEDIUM -> {
                    snowflakeSizePx = snowflakeSizeMediumPx
                    snowflakePainter = painterMedium
                }

                SnowFlakeType.LARGE -> {
                    snowflakeSizePx = snowflakeSizeLargePx
                    snowflakePainter = painterLarge
                }
            }

            rotate(
                snowflake.rotationAngle,
                Offset(
                    snowflake.x,
                    snowflake.y,
                )
            ) {
                translate(
                    snowflake.x - snowflakeSizePx / 2,
                    snowflake.y - snowflakeSizePx / 2,
                ) {
                    val scaleY = if (snowflake.type == SnowFlakeType.LARGE) {
                        ((snowflake.rotationAngle.toInt() % 360) - 180).toFloat() / 180f
                    } else {
                        1f
                    }
                    scale(1f, scaleY, Offset(snowflakeSizePx / 2, snowflakeSizePx / 2)) {
                        with(snowflakePainter) {
                            draw(Size(snowflakeSizePx, snowflakeSizePx), snowflake.alpha)
                        }
                    }
                }
            }
        }
    }
}
