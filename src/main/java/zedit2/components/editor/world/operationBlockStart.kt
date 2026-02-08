package zedit2.components.editor.world

import zedit2.components.WorldEditor

internal fun WorldEditor.operationBlockStart(fromKeyboard : Boolean = true) {
    this.blockSelectionStartedByKeyboard = fromKeyboard
    operationCancel()
    setSelectionBlockStart(caretPos)

    afterUpdate()
}