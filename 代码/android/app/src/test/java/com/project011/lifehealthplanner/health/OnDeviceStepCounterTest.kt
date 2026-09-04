package com.project011.lifehealthplanner.health

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnDeviceStepCounterTest {
    @Test
    fun normalizesOnlyFiniteNonNegativeIntegerCounts() {
        assertEquals(0L, normalizeStepCounterValue(0f))
        assertEquals(123L, normalizeStepCounterValue(123f))
        assertEquals(null, normalizeStepCounterValue(-1f))
        assertEquals(null, normalizeStepCounterValue(Float.NaN))
        assertEquals(null, normalizeStepCounterValue(Float.POSITIVE_INFINITY))
        assertEquals(null, normalizeStepCounterValue(12.5f))
        assertEquals(null, normalizeStepCounterValue(Float.MAX_VALUE))
    }

    @Test
    fun unavailableSensorDoesNotRegister() = runTest {
        val source = FakeStepCounterSource(available = false)
        val counter = OnDeviceStepCounter(source, timeoutMillis = 100)

        val error = expectFailure<StepCounterUnavailableException> { counter.readOnce() }

        assertTrue(error.message.orEmpty().contains("TYPE_STEP_COUNTER"))
        assertEquals(0, source.registerCount)
        assertEquals(0, source.unregisterCount)
    }

    @Test
    fun firstValidSampleIsReturnedAndUnregistered() = runTest {
        val source = FakeStepCounterSource()
        val counter = OnDeviceStepCounter(source, timeoutMillis = 100) { 1_725_000_000_000L }
        val read = async { counter.readOnce() }
        runCurrent()

        source.emit(321f)
        val snapshot = read.await()

        assertEquals(321L, snapshot.stepsSinceBoot)
        assertEquals(1_725_000_000_000L, snapshot.observedAtEpochMillis)
        assertEquals(OnDeviceStepSnapshot.SOURCE, snapshot.source)
        assertEquals("since_boot", snapshot.dataScope)
        assertTrue(snapshot.disclosure.contains("vivo/iQOO"))
        assertEquals(1, source.registerCount)
        assertEquals(1, source.unregisterCount)
    }

    @Test
    fun invalidSampleFailsAndUnregisters() = runTest {
        val source = FakeStepCounterSource()
        val counter = OnDeviceStepCounter(source, timeoutMillis = 100)
        val error = supervisorScope {
            val read = async { counter.readOnce() }
            runCurrent()
            source.emit(Float.NaN)
            expectFailureFrom<StepCounterInvalidReadingException>(read)
        }

        assertTrue(error.message.orEmpty().contains("无效"))
        assertEquals(1, source.unregisterCount)
    }

    @Test
    fun registrationFailureDoesNotLeaveListenerRegistered() = runTest {
        val source = FakeStepCounterSource(registerResult = false)
        val counter = OnDeviceStepCounter(source, timeoutMillis = 100)

        val error = expectFailure<StepCounterRegistrationException> { counter.readOnce() }

        assertTrue(error.message.orEmpty().contains("注册"))
        assertEquals(1, source.registerCount)
        assertEquals(0, source.unregisterCount)
    }

    @Test
    fun timeoutUnregistersTheForegroundListener() = runTest {
        val source = FakeStepCounterSource()
        val counter = OnDeviceStepCounter(source, timeoutMillis = 25)
        val error = supervisorScope {
            val read = async { counter.readOnce() }
            runCurrent()
            advanceTimeBy(25)
            runCurrent()
            expectFailureFrom<StepCounterTimeoutException>(read)
        }

        assertTrue(error.message.orEmpty().contains("25ms"))
        assertEquals(1, source.unregisterCount)
    }

    @Test
    fun cancellationUnregistersTheForegroundListenerAndPropagatesCancellation() = runTest {
        val source = FakeStepCounterSource()
        val counter = OnDeviceStepCounter(source, timeoutMillis = 10_000)
        val read = async { counter.readOnce() }
        runCurrent()

        read.cancelAndJoin()

        assertTrue(read.isCancelled)
        assertEquals(1, source.unregisterCount)
    }

    private class FakeStepCounterSource(
        private val available: Boolean = true,
        private val registerResult: Boolean = true,
    ) : StepCounterSource {
        override val isAvailable: Boolean
            get() = available
        private var listener: StepCounterListener? = null
        var registerCount: Int = 0
            private set
        var unregisterCount: Int = 0
            private set

        override fun register(listener: StepCounterListener): Boolean {
            registerCount += 1
            if (registerResult) this.listener = listener
            return registerResult
        }

        override fun unregister(listener: StepCounterListener) {
            if (this.listener === listener) this.listener = null
            unregisterCount += 1
        }

        fun emit(value: Float) {
            listener?.onStepCount(value)
        }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: suspend () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        error("expected ${T::class.java.simpleName}")
    }

    private suspend inline fun <reified T : Throwable> expectFailureFrom(deferred: Deferred<*>): T {
        try {
            deferred.await()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        error("expected ${T::class.java.simpleName}")
    }
}
