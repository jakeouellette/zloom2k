package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationToggleText(forceEnable: Boolean = false) {
    if (forceEnable == false) {
        if (operationCancel()) return
    }
    canvas.disableKeymappings()
    canvas.requestFocusInWindow()
    textEntry = true
    textEntryX = caretPos.x
    afterUpdate()
}