package com.meridian11.books.bentobar.items

import com.meridian11.books.bentobar.SourceScan
import com.meridian11.books.bentobar.SourceScan.place
import com.meridian11.books.bentobar.items.ClipboardActionsRules.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copied text is the user's own: it goes to the model on this device only at a press, and is never logged or printed.
 * And the model is the system's own: nothing in the app or its build names a model on a server.
 */
class ClipboardActionsPrivacyTest {
    private val text = "The door code is 4711 and the meeting room is the one on the left, past the kitchen."

    @Test fun noLogLineInTheFilesThatHoldCopiedText() {
        val names = setOf("items/ClipboardItem.kt", "items/ClipboardRules.kt", "items/ClipboardActions.kt", "items/ClipboardActionsRules.kt",
            "items/OnDeviceModel.kt")
        val files = SourceScan.sources.filter { it.place() in names }
        assertEquals(names, files.map { it.place() }.toSet())
        for (f in files) {
            val code = SourceScan.code(f)
            for (call in listOf("Log.", "println(", "printStackTrace", "System.out", "System.err")) assertFalse("${f.name} calls $call", call in code)
        }
    }

    @Test fun aPieceOfAnAnswerAndAHandoverPrintNoWords() {
        assertFalse(OnDeviceModel.Answer.Piece(text).toString().contains("door"))
        assertFalse(ClipboardActions.Handover(text, Action.PROOFREAD).toString().contains("door"))
        ClipboardActions.stage(OnDeviceModel.Ready.READY, text, Action.SUMMARISE, text)
        try {
            assertFalse(ClipboardActions.describe().contains("door"))
        } finally {
            ClipboardActions.unstage()
        }
    }

    @Test fun noModelOnAServerIsReachable() {
        // The road to a model on a server, on Android, is another library (Firebase AI Logic, whose hybrid mode can fall back
        // to the cloud): no source names it, and the build pulls in nothing of Firebase's. A change here is a decision.
        val cloud = listOf("com.google.firebase", "firebase.ai", "FirebaseAI", "vertexai", "generativelanguage", "aiplatform", "InferenceMode")
        for (f in SourceScan.sources) {
            val code = SourceScan.code(f)
            for (word in cloud) assertFalse("${f.place()} names $word", word in code)
        }
        val catalog = File("../gradle/libs.versions.toml")
        assertTrue(catalog.absolutePath, catalog.isFile)
        val build = File("build.gradle.kts").readText() + catalog.readText()
        for (word in listOf("firebase", "vertexai", "generativeai")) assertFalse("the build names $word", word in build.lowercase())
    }

    @Test fun noModelIsBuiltIn() {
        // The library that reaches the system's model brings a permission and a usage reporter along (ManifestTest would fail
        // on the first), so none is built in: no row offers an action. Wiring one in is a decision, made here too.
        assertEquals(null, ClipboardActions.model)
        assertEquals(null, ClipboardActions.current())
    }
}
