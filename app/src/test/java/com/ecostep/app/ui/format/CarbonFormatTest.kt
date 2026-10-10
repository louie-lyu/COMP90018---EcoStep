package com.ecostep.app.ui.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CarbonFormatTest {
    @Test fun `short walking and public transport journeys retain useful precision`() {
        assertEquals("70.7 g", formatCarbonGrams(368.0 / 1000 * 192, Locale.US))
        assertEquals("37.9 g", formatCarbonGrams(368.0 / 1000 * (192 - 89), Locale.US))
        assertEquals("0.0 g", formatCarbonGrams(0.0, Locale.US))
        assertEquals("<0.1 g", formatCarbonGrams(0.01, Locale.US))
    }

    @Test fun `format respects locale and rounds only after aggregation`() {
        assertEquals("1.234,6 g", formatCarbonGrams(1234.56, Locale.GERMANY))
        assertEquals("0.1 g", formatCarbonGrams(0.04 + 0.04, Locale.US))
    }
}
