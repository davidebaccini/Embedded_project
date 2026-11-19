package com.example.prova_android

data class FitnessData(
    val bpm: Int = 0,
    val steps: Int = 0,
    val stepGoal: Int = 10000
)

enum class DisplayMode { ALL, BPM_ONLY, STEPS_ONLY }

fun parseBleMessage(current: FitnessData, message: String): FitnessData {
    var updated = current
    val tokens = message.split('\n', '\r', ',', ';')
    for (rawToken in tokens) {
        val token = rawToken.trim()
        if (token.isEmpty()) continue
        updated = when {
            token.startsWith("BPM:", ignoreCase = true) -> {
                val value = token.substringAfter(':').trim().toIntOrNull() ?: updated.bpm
                updated.copy(bpm = value)
            }
            token.startsWith("STEPS:", ignoreCase = true) -> {
                val value = token.substringAfter(':').trim().toIntOrNull() ?: updated.steps
                updated.copy(steps = value)
            }
            else -> updated
        }
    }
    return updated
}
