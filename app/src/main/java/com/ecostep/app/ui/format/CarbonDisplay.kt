package com.ecostep.app.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun carbonGramsText(grams: Double): String =
    formatCarbonGrams(grams, LocalConfiguration.current.locales[0])

/** Compatibility with existing UI models; database and calculation units remain grams. */
@Composable
fun carbonKilogramsAsGramsText(kilograms: Double): String = carbonGramsText(kilograms * 1000.0)
