package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationEscape() {
    if (operationCancel()) return
    tryClose()
}
