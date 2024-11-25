package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.util.Logger
import zedit2.util.Logger.TAG

internal fun WorldEditor.operationFocusOnBoardSelector() {
    Logger.i(TAG) {
        "Focusing on board selector ${this.boardSelectorComponent}:${this.boardSelectorComponent.isFocusOwner}"
    }
    Logger.i(TAG) {
        "Current focus element: ${this.lastFocusedElement}:${this.lastFocusedElement.isFocusOwner}"
    }
    if (this.boardSelectorComponent.isFocusOwner) {
        this.lastFocusedElement = this.canvas.requestFocusInWindow()
    } else {
        val willFocus = this.boardSelectorComponent.requestFocusInWindow()
        this.lastFocusedElement = this.boardSelectorComponent
        Logger.i(TAG) { "will focus? $willFocus"}
    }
}