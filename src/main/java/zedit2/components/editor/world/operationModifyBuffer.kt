package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.components.editor.BrushMenuPanel
import zedit2.model.Tile
import java.awt.Point


internal fun WorldEditor.operationModifyBuffer(advanced: Boolean) {
    openCurrentTileEditor(
        callback = { resultTile: Tile? ->
            bufferTile = resultTile
            afterUpdate()
        },
        exempt = false,
        advanced = advanced,
        tile = bufferTile!!,
    )
}