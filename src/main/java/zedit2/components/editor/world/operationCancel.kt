package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.SHOW_NOTHING


internal fun WorldEditor.operationCancel(): Boolean {
    if (drawing) {
        drawing = false
        afterUpdate()
        return true
    }
    if (textEntry) {
        textEntry = false
        afterUpdate()
        return true
    }
    if (GlobalEditor.isBlockBuffer()) {
        setBlockBuffer(0, 0, null, false)
        afterUpdate()
        return true
    }
    if (blockStartX != -1) {
        setBlockStart(-1, -1)
        afterUpdate()
        return true
    }
    if (moveBlockW != 0) {
        setMoveBlock(0, 0)
        afterUpdate()
        return true
    }

    if (currentlyShowing != SHOW_NOTHING) {
        currentlyShowing = SHOW_NOTHING
        afterChangeShowing()
        return true
    }
    return false
}
