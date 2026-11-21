package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.SourceScan
import com.meridian11.books.bentobar.SourceScan.place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where Android can be asked for only some kinds of usage events (API 35), Screen time asks for those its reading takes:
 * a kind the reading handles and the question leaves out would be missing from the day, with no error.
 */
class ScreenTimeKindsTest {
    private val code = SourceScan.code(SourceScan.sources.single { it.place() == "items/ScreenTimeLoad.kt" })
    private fun kinds(text: String) = Regex("""UsageEvents\.Event\.([A-Z_]+)""").findAll(text).map { it.groupValues[1] }.toSet()

    @Test fun everyKindTheReadingHandlesIsAskedFor() {
        val handled = kinds(code.substringAfter("when (e.eventType)").substringBefore("catch"))
        val asked = kinds(code.substringAfter("val BAR_KINDS").substringBefore("val day"))
        assertTrue(handled.isNotEmpty())
        assertEquals(handled, asked)
    }
}
