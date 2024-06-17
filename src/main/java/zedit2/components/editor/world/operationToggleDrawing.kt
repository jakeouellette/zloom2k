package zedit2.components.editor.world

import zedit2.components.WorldEditor

internal fun WorldEditor.operationToggleDrawing() {
    if (operationCancel()) return
    drawing = true
    putTileAt(cursorPos, bufferTile, WorldEditor.Companion.PutTypes.PUT_DEFAULT)
    afterModification()
}