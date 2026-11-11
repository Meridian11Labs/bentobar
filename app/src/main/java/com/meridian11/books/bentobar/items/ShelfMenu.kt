package com.meridian11.books.bentobar.items

import android.content.ClipData
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.meridian11.books.bentobar.R
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.items.ShelfRules.Entry
import com.meridian11.books.bentobar.items.ShelfRules.Kind
import com.meridian11.books.bentobar.ui.MenuCard
import com.meridian11.books.bentobar.ui.MenuDivider
import com.meridian11.books.bentobar.ui.MenuEntry
import com.meridian11.books.bentobar.ui.MenuNote
import com.meridian11.books.bentobar.ui.SmallIconButton
import com.meridian11.books.bentobar.util.Sym

/**
 * The shelf's popup: a row per entry, newest first (a click copies it, a link has a button that opens it, a row can be
 * dragged out into another app and taken off), then "Clear" while it holds something.
 */
@Composable
internal fun ShelfMenu(item: ItemConfig, host: MenuHost) {
    val id = item.id
    val shelves by ShelfItem.shelves.collectAsState()
    val staged by ShelfItem.staged.collectAsState()
    LaunchedEffect(id) { ShelfItem.load(id) }
    val entries = staged ?: shelves[id]
    val label = stringResource(R.string.app_name)
    MenuCard(Sym.INBOX, stringResource(R.string.item_shelf_title), when {
        entries == null -> null
        entries.isEmpty() -> stringResource(R.string.shelf_empty)
        else -> pluralStringResource(R.plurals.shelf_count, entries.size, entries.size)
    }) {
        entries.orEmpty().forEach { entry -> key(entry.text) { ShelfRow(entry, label, host) { ShelfItem.remove(id, entry.text) } } }
        MenuNote(stringResource(if (entries.orEmpty().size >= ShelfRules.MOST) R.string.shelf_full else R.string.shelf_note))
        if (!entries.isNullOrEmpty()) {
            MenuDivider()
            MenuEntry(Sym.DELETE, stringResource(R.string.shelf_clear)) { ShelfItem.clear(id) }
        }
    }
}

/** One entry: a click copies it and closes the popup. */
@Composable
private fun ShelfRow(entry: Entry, label: String, host: MenuHost, onRemove: () -> Unit) {
    val link = entry.kind == Kind.LINK
    MenuEntry(
        icon = if (link) Sym.LINK else Sym.SHORT_TEXT,
        label = ShelfRules.line(entry),
        // Dragged out into another app's field as plain text, and nothing else: no file, no right to read one.
        modifier = Modifier.dragAndDropSource { _ -> DragAndDropTransferData(ClipData.newPlainText(label, entry.text), flags = View.DRAG_FLAG_GLOBAL) },
        trailing = {
            Row {
                if (link) SmallIconButton(Sym.OPEN_IN_NEW, stringResource(R.string.shelf_open)) { host.close(); ShelfItem.open(entry.text) }
                SmallIconButton(Sym.CLOSE, stringResource(R.string.shelf_remove), onClick = onRemove)
            }
        },
    ) {
        Env.copy(entry.text)
        host.close()
    }
}
