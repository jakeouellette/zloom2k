package zedit2.components.editor.world

import zedit2.components.WorldEditor

internal fun WorldEditor.operationExitJump(exit: Int) {
    val destBoard = currentBoard!!.getExit(exit)
    if (destBoard != 0) changeBoard(destBoard)
}
