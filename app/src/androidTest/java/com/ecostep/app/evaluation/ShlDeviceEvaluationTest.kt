package com.ecostep.app.evaluation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.evaluation.ShlTransportEvaluation
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith

/** Studio packages the archive and exports the transport evaluation reports. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ShlDeviceEvaluationTest {
    @Test
    fun evaluateActivityHintTransportClassification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ShlTransportEvaluation(InstrumentationRegistry.getArguments()).run(
            prepareArchive = {
                File(instrumentation.targetContext.cacheDir, "shl-preview.zip").also { archive ->
                    instrumentation.context.assets.open("shl-preview.zip").use { input ->
                        archive.outputStream().use { input.copyTo(it) }
                    }
                }
            },
            writeReport = { name, report -> writeDeviceReport(name, report, preserveSourceMetadata = true) },
        )
    }
}
