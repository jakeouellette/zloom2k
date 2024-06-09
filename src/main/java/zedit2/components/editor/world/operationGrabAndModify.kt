package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import zedit2.model.Tile
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import kotlin.math.min


internal fun WorldEditor.operationGrabAndModify(grab: Boolean, advanced: Boolean) {
    if (grab) { // Enter also finishes a copy block operation
        if (blockStartX != -1) {
            operationBlockEnd()
            return
        }
        if (GlobalEditor.isBlockBuffer()) {
            blockPaste()
            return
        }
        if (moveBlockW != 0) {
            blockFinishMove()
            return
        }
    }
    val tile = getTileAt(cursorX, cursorY, false)
    val board = getBoardAt(cursorX, cursorY)
    val x = cursorX % boardW
    val y = cursorY % boardH

    if (tile != null && board != null) {
        createTileEditor(
            board = board,
            x = x,
            y = y,
            callback = { resultTile: Tile ->
                // Put this tile down, subject to the following:
                // - Any stat IDs on this tile that matches a stat ID on the destination tile go in in-place
                // - If there are stats on the destination tile that weren't replaced, delete them
                // - If there are stats on this tile that didn't go in, add them to the end
                setStats(board, cursorX / boardW * boardW, cursorY / boardH * boardH, x, y, resultTile.stats)
                addRedraw(cursorX, cursorY, cursorX, cursorY)
                board.setTileRaw(x, y, resultTile.id, resultTile.col)
                if (grab) bufferTile = getTileAt(cursorX, cursorY, true)
                afterModification()
            },
            advanced = advanced,
            tile = tile,
            exempt = false,
        )
    }
}