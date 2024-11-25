package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.util.Logger
import zedit2.util.Logger.TAG

internal fun WorldEditor.operationToggleDrawing() {
    Logger.i(TAG) {
        "Drawing mode toggled."
    }
    if (operationCancel()) return
    drawing = true
    putTileAt(caretPos, bufferTile, WorldEditor.Companion.PutTypes.PUT_DEFAULT)
    afterModification()
}