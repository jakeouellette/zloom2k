package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationBufferPut() {
    if (getTileAt(caretPos, false) == bufferTile) {
        operationDelete()
        return
    }
    putTileAt(caretPos, bufferTile, WorldEditor.Companion.PutTypes.PUT_DEFAULT)
    afterModification()
}