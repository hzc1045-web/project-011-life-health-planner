package com.project011.lifehealthplanner.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A single foreground sample from the phone's hardware step counter.
 *
 * The counter is cumulative from the most recent device boot. It is not a
 * daily total and it cannot recover historical records from vivo/iQOO Health.
 */
data class OnDeviceStepSnapshot(
    val stepsSinceBoot: Long,
    val observedAtEpochMillis: Long,
    val source: String = SOURCE,
) {
    val dataScope: String = DATA_SCOPE
    val disclosure: String = DISCLOSURE

    companion object {
        const val SOURCE = "on_device_sensor:type_step_counter"
        const val DATA_SCOPE = "since_boot"
        const val DISCLOSURE =
            "仅为本机 TYPE_STEP_COUNTER 自最近一次开机以来的累计步数，不是 vivo/iQOO 健康历史数据。"
    }
}

class StepCounterUnavailableException : IllegalStateException(
    "本机没有可用的 TYPE_STEP_COUNTER 传感器",
)

class StepCounterRegistrationException : IllegalStateException(
    "无法注册本机计步传感器",
)

class StepCounterInvalidReadingException(value: Float) : IllegalStateException(
    "本机计步传感器返回了无效读数：$value",
)

class StepCounterTimeoutException(
    timeoutMillis: Long,
    cause: Throwable? = null,
) : IllegalStateException(
    "本机计步传感器在 ${timeoutMillis}ms 内没有返回读数",
    cause,
)

/** A small adapter boundary that keeps the suspend logic JVM-testable. */
internal interface StepCounterSource {
    val isAvailable: Boolean

    fun register(listener: StepCounterListener): Boolean

    fun unregister(listener: StepCounterListener)
}

internal fun interface StepCounterListener {
    fun onStepCount(value: Float)
}

private sealed interface StepCounterSample {
    data class Value(val steps: Long) : StepCounterSample

    data class Invalid(val error: StepCounterInvalidReadingException) : StepCounterSample
}

/**
 * Reads one sample while the caller is in the foreground. No background
 * service is started by this class.
 */
class OnDeviceStepCounter internal constructor(
    private val source: StepCounterSource,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        context: Context,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        source = AndroidStepCounterSource(context.applicationContext),
        timeoutMillis = timeoutMillis,
        clock = clock,
    )

    val isAvailable: Boolean
        get() = source.isAvailable

    /**
     * Suspends until the first valid sensor sample arrives. The listener is
     * always unregistered when the read succeeds, times out, or is cancelled.
     */
    suspend fun readOnce(): OnDeviceStepSnapshot {
        require(timeoutMillis > 0) { "timeoutMillis 必须大于 0" }
        val sample = try {
            withTimeout(timeoutMillis) {
                callbackFlow<StepCounterSample> {
                    if (!source.isAvailable) {
                        close(StepCounterUnavailableException())
                        return@callbackFlow
                    }

                    val listener = StepCounterListener { rawValue ->
                        val normalized = normalizeStepCounterValue(rawValue)
                        if (normalized == null) {
                            trySend(StepCounterSample.Invalid(StepCounterInvalidReadingException(rawValue)))
                        } else {
                            trySend(StepCounterSample.Value(normalized))
                        }
                    }

                    val registered = try {
                        source.register(listener)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        close(error)
                        return@callbackFlow
                    }
                    if (!registered) {
                        close(StepCounterRegistrationException())
                        return@callbackFlow
                    }

                    awaitClose { source.unregister(listener) }
                }.first()
            }
        } catch (error: TimeoutCancellationException) {
            throw StepCounterTimeoutException(timeoutMillis, error)
        }
        val steps = when (sample) {
            is StepCounterSample.Value -> sample.steps
            is StepCounterSample.Invalid -> throw sample.error
        }
        return OnDeviceStepSnapshot(
            stepsSinceBoot = steps,
            observedAtEpochMillis = clock(),
        )
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 2_000L
    }
}

/**
 * TYPE_STEP_COUNTER is specified as a cumulative integer count, although the
 * Android API exposes sensor values as Float. Reject fractional, non-finite,
 * negative, and out-of-range values instead of silently truncating them.
 */
internal fun normalizeStepCounterValue(value: Float): Long? {
    if (!value.isFinite() || value < 0f) return null
    val asDouble = value.toDouble()
    if (asDouble > Long.MAX_VALUE.toDouble()) return null
    val rounded = asDouble.roundToLong()
    return rounded.takeIf { abs(asDouble - it.toDouble()) <= FLOAT_INTEGER_TOLERANCE }
}

private class AndroidStepCounterSource(context: Context) : StepCounterSource {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val registrations = mutableMapOf<StepCounterListener, SensorEventListener>()

    override val isAvailable: Boolean
        get() = sensorManager != null && sensor != null

    override fun register(listener: StepCounterListener): Boolean {
        val manager = sensorManager ?: return false
        val stepSensor = sensor ?: return false
        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let(listener::onStepCount)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        synchronized(registrations) { registrations[listener] = sensorListener }
        val registered = manager.registerListener(
            sensorListener,
            stepSensor,
            SensorManager.SENSOR_DELAY_NORMAL,
        )
        if (!registered) synchronized(registrations) { registrations.remove(listener) }
        return registered
    }

    override fun unregister(listener: StepCounterListener) {
        val sensorListener = synchronized(registrations) { registrations.remove(listener) } ?: return
        sensorManager?.unregisterListener(sensorListener)
    }
}

private const val FLOAT_INTEGER_TOLERANCE = 0.001
