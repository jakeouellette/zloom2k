package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationToggleText() {
    if (operationCancel()) return
    textEntry = true
    textEntryX = cursorX
    afterUpdate()
}