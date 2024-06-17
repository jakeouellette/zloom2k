package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationBufferPut() {
    if (getTileAt(cursorPos, false) == bufferTile) {
        operationDelete()
        return
    }
    putTileAt(cursorPos, bufferTile, WorldEditor.Companion.PutTypes.PUT_DEFAULT)
    afterModification()
}