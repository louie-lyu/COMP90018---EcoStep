package com.ecostep.app.evaluation

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated

/** Keep results observable and check correctness outside the measured block. */
internal fun <T> BenchmarkRule.measureChecked(operation: () -> T, verify: (T) -> Unit) {
    var result = operation()
    verify(result)
    measureRepeated { result = operation() }
    verify(result)
}

internal suspend fun <T> BenchmarkRule.measureSuspendingChecked(operation: suspend () -> T, verify: (T) -> Unit) {
    var result = operation()
    verify(result)
    val state = getState()
    while (state.keepRunning()) { result = operation() }
    verify(result)
}
