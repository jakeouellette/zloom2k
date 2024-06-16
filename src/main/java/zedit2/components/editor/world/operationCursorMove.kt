package zedit2.components.editor.world

import zedit2.components.Util.clamp
import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.PUT_DEFAULT
import zedit2.model.Board
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import kotlin.math.abs


internal fun WorldEditor.operationCursorMove(off: Pos, draw: Boolean) {
    val newCursorPos = (cursorPos+off).clamp(0, dim.asPos - 1)

    if (newCursorPos != cursorPos) {
        if (!draw) {
            cursorPos = newCursorPos
        } else {
            val delta = off.toDelta()
            val dirty = HashSet<Board>()
            while (cursorPos != newCursorPos) {
                cursorPos += delta

                if (drawing) {
                    val board = putTileDeferred(cursorPos, bufferTile, PUT_DEFAULT)
                    if (board != null) dirty.add(board)
                }
            }
            for (board in dirty) {
                board.finaliseStats()
            }
        }
        canvas.setCursor(cursorPos)

        if (drawing) {
            afterModification()
        } else {
            afterUpdate()
        }
    }
}

