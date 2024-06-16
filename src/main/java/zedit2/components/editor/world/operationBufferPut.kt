package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.PUT_DEFAULT


internal fun WorldEditor.operationBufferPut() {
    if (getTileAt(cursorPos, false) == bufferTile) {
        operationDelete()
        return
    }
    putTileAt(cursorPos, bufferTile, PUT_DEFAULT)
    afterModification()
}