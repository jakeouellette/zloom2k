package zedit2.components.editor.world

import zedit2.components.ColourSelector
import zedit2.components.ColourSelector.Companion.createColourSelector
import zedit2.components.WorldEditor
import java.awt.event.ActionEvent


internal fun WorldEditor.operationColour() {
    val col = getTileColour(bufferTile!!)
    createColourSelector(this, col, frame, { e: ActionEvent ->
        val newCol = e.actionCommand.toInt()
        val tile = bufferTile!!
        paintTile(tile, newCol)
        bufferTile = tile
        afterUpdate()
    }, ColourSelector.COLOUR)
}