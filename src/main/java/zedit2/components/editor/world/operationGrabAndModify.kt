package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import zedit2.model.Tile
import zedit2.util.Logger
import zedit2.util.Logger.TAG


internal fun WorldEditor.operationGrabAndModify(grab: Boolean, advanced: Boolean) {
    if (grab) { // Enter also finishes a copy block operation
        if (selectionBlockAnchorPos.isPositive) {
            Logger.i(TAG) { "Block Anchor is Positive."}
            operationBlockEnd()
            return
        }
        if (GlobalEditor.isBlockBuffer()) {
            Logger.i(TAG) { "Pasting block buffer."}
            blockPaste()
            return
        }
        if (moveBlockDim.w != 0) {
            Logger.i(TAG) { "Move block is nonzero."}
            blockFinishMove()
            return
        }
    }
    val tile = getTileAt(caretPos, false)
    val board = getBoardAt(caretPos)
    val xy = caretPos % boardDim

    if (tile != null && board != null) {
        createTileEditor(
            board = board,
            pos = xy,
            callback = { resultTile: Tile ->
                // Put this tile down, subject to the following:
                // - Any stat IDs on this tile that matches a stat ID on the destination tile go in in-place
                // - If there are stats on the destination tile that weren't replaced, delete them
                // - If there are stats on this tile that didn't go in, add them to the end
                setStats(board, caretPos / boardDim * boardDim, xy, resultTile.stats)
                addRedraw(caretPos, caretPos)
                board.setTileRaw(xy, resultTile.id, resultTile.col)
                if (grab) bufferTile = getTileAt(caretPos, true)
                afterModification()
            },
            advanced = advanced,
            tile = tile,
            exempt = false,
        )
    }
}