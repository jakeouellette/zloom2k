package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.model.Tile


internal fun WorldEditor.operationModifyBuffer(advanced: Boolean) {
    openCurrentTileEditor(
        callback = { resultTile: Tile? ->
            bufferTile = resultTile
            afterUpdate()
        },
        exempt = false,
        advanced = advanced,
        tile = bufferTile!!
    )
}