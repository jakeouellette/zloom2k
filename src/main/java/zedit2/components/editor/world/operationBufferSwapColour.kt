package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationBufferSwapColour() {
    val tile = bufferTile!!.clone()
    val oldCol = tile.col
    val newCol = ((oldCol and 0x0F) shl 4) or ((oldCol and 0xF0) shr 4)
    tile.col = newCol
    bufferTile = tile
    afterUpdate()
}