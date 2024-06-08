package zedit2.components.editor.world

import zedit2.components.Util.clamp
import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.PUT_DEFAULT
import zedit2.model.Board
import kotlin.math.abs


internal fun WorldEditor.operationCursorMove(offX: Int, offY: Int, draw: Boolean) {
    val newCursorX = clamp(cursorX + offX, 0, width - 1)
    val newCursorY = clamp(cursorY + offY, 0, height - 1)

    if (newCursorX != cursorX || newCursorY != cursorY) {
        if (!draw) {
            cursorX = newCursorX
            cursorY = newCursorY
        } else {
            val deltaX = if (offX == 0) 0 else (offX / abs(offX.toDouble())).toInt()
            val deltaY = if (offY == 0) 0 else (offY / abs(offY.toDouble())).toInt()
            val dirty = HashSet<Board>()
            while (cursorX != newCursorX || cursorY != newCursorY) {
                cursorX += deltaX
                cursorY += deltaY

                if (drawing) {
                    val board = putTileDeferred(cursorX, cursorY, bufferTile, PUT_DEFAULT)
                    if (board != null) dirty.add(board)
                }
            }
            for (board in dirty) {
                board.finaliseStats()
            }
        }
        canvas.setCursor(cursorX, cursorY)

        if (drawing) {
            afterModification()
        } else {
            afterUpdate()
        }
    }
}

