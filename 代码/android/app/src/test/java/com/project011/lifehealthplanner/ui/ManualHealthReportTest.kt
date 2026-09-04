package com.project011.lifehealthplanner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualHealthReportTest {
    @Test
    fun acceptsPartiallyFilledReportAndAssignsUnits() {
        val result = parseManualHealthDailyReport(
            mapOf(
                "weight" to "72.4",
                "sleep" to "",
                "steps" to "8342",
            ),
        )

        assertEquals(2, result.size)
        assertEquals("kg", result.first { it.kind == "weight" }.unit)
        assertEquals("步", result.first { it.kind == "steps" }.unit)
    }

    @Test
    fun rejectsEmptyReport() {
        val error = runCatching {
            parseManualHealthDailyReport(mapOf("weight" to " ", "steps" to ""))
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("请至少填写一项手环数据", error?.message)
    }

    @Test
    fun rejectsNonNumericValueInsteadOfDroppingIt() {
        val error = runCatching {
            parseManualHealthDailyReport(mapOf("weight" to "72", "sleep" to "unknown"))
        }.exceptionOrNull()

        assertEquals("手环数据“睡眠”必须是有效数值", error?.message)
    }

    @Test
    fun rejectsNegativeAndNonFiniteValues() {
        val negative = runCatching {
            parseManualHealthDailyReport(mapOf("steps" to "-1"))
        }.exceptionOrNull()
        val nonFinite = runCatching {
            parseManualHealthDailyReport(mapOf("steps" to "1e999"))
        }.exceptionOrNull()

        assertEquals("手环数据必须是非负数值", negative?.message)
        assertEquals("手环数据必须是非负数值", nonFinite?.message)
    }
}
