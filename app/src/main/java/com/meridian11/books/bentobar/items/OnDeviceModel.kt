package com.meridian11.books.bentobar.items

import kotlinx.coroutines.flow.Flow

/**
 * The language model the system itself provides on this device, as Clipboard actions ask it. An implementation reaches
 * that model and nothing else: no model on a server, no fallback to one, no connection of its own. Every call is safe
 * from the main thread (the work is the system's, or on the implementation's own thread) and never throws: what goes
 * wrong is a [Ready]. A text reaches it only from a press of an action, and it keeps nothing it was given.
 */
interface OnDeviceModel {
    enum class Ready {
        /** Not asked yet. */
        UNKNOWN,
        /** No model on this device, and none to get. */
        NONE,
        /** The system can fetch the model; nothing is fetched until the user presses "Get the model" ([download]). */
        DOWNLOADABLE,
        DOWNLOADING,
        READY,
        /** The model answers only the app in front: BentoBar's window, not its popup in the bar. */
        NOT_IN_FRONT,
        /** It says no for now: too many questions, or its share of the battery is used up. */
        BUSY,
        /** Anything else went wrong. */
        FAILED,
    }

    /** Where the model stands. */
    suspend fun ready(): Ready

    /** Asks the system to fetch the model, only ever from the user's press: where it stands as it goes, then it ends. */
    fun download(): Flow<Ready>

    /** The answer to [prompt], piece by piece as the model writes it; it ends, with [Answer.Stopped] when it could not go on. */
    fun ask(prompt: String): Flow<Answer>

    sealed interface Answer {
        /** The next piece of the answer, to follow those before it. Prints its length, never its words. */
        class Piece(val text: String) : Answer {
            override fun toString() = "Piece(${text.length} chars)"
        }

        data class Stopped(val why: Ready) : Answer
    }
}
