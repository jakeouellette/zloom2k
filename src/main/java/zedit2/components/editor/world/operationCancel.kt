package zedit2.components.editor.world

import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.SHOW_NOTHING
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos


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
        setBlockBuffer(Dim(0, 0), null, false)
        afterUpdate()
        return true
    }
    if (selectionBlockAnchorPos.isPositive) {
        setSelectionBlockStart(Pos.NEG_ONE)
        afterUpdate()
        return true
    }
    if (moveBlockDim.w != 0) {
        setMoveBlock(Pos.NEG_ONE, Dim.EMPTY)
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
