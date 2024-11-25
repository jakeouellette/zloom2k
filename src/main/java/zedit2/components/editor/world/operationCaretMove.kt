package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.model.Board
import zedit2.model.spatial.Pos


internal fun WorldEditor.operationCaretMove(off: Pos, draw: Boolean) {
    val newCaretPos = (caretPos+off).clamp(0, dim.asPos - 1)
    var updatingPosition = caretPos
    if (newCaretPos != caretPos) {
        if (!draw) {
            updatingPosition = newCaretPos
        } else {
            val delta = off.toDelta()
            val dirty = HashSet<Board>()

            while (updatingPosition != newCaretPos) {
                updatingPosition += delta

                if (drawing) {
                    val board = putTileDeferred(updatingPosition, bufferTile, WorldEditor.Companion.PutTypes.PUT_DEFAULT)
                    if (board != null) dirty.add(board)
                }
            }
            for (board in dirty) {
                board.finaliseStats()
            }
        }
        this.caretPos = updatingPosition
        canvas.setCaret(updatingPosition)
        if (drawing) {
            afterModification()
        } else {
            afterUpdate()
        }
    }
}

