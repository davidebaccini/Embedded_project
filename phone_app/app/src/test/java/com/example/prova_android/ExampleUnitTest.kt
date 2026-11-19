package com.example.prova_android

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun parseBleMessage_updatesBpmAndSteps() {
        val initial = FitnessData(bpm = 60, steps = 100)

        val afterBpm = parseBleMessage(initial, "BPM: 82\r\n")
        assertEquals(82, afterBpm.bpm)
        assertEquals(100, afterBpm.steps)

        val afterSteps = parseBleMessage(afterBpm, "STEPS:3450")
        assertEquals(82, afterSteps.bpm)
        assertEquals(3450, afterSteps.steps)
    }

    @Test
    fun parseBleMessage_handlesCombinedAndInvalidPayloads() {
        val initial = FitnessData(bpm = 70, steps = 500)

        val combined = parseBleMessage(initial, "bpm:95,steps:1200")
        assertEquals(95, combined.bpm)
        assertEquals(1200, combined.steps)

        val invalid = parseBleMessage(combined, "BPM:abc;UNKNOWN:42")
        assertEquals(95, invalid.bpm)
        assertEquals(1200, invalid.steps)
    }
}
