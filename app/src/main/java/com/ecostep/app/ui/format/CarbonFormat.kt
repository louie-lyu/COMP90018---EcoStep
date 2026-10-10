package com.ecostep.app.ui.format

import java.text.NumberFormat
import java.util.Locale

/** Display rounding only; calculations and Firestore totals retain their original grams. */
fun formatCarbonGrams(grams: Double, locale: Locale): String {
    require(grams.isFinite() && grams >= 0.0)
    val number = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return if (grams > 0.0 && grams < 0.05) {
        "<${number.format(0.1)} g"
    } else {
        "${number.format(grams)} g"
    }
}
