package com.meridian11.books.bentobar.items

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A Focus session's end at its time: the alarm BentoBar sets for it, and nothing else (the receiver is not exported).
 * Until the Focus item is built it does nothing.
 */
class FocusEnd : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}
}
