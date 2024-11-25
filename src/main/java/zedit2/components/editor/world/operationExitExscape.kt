package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.model.spatial.Pos


internal fun WorldEditor.operationExitCreate(exit: Int) {
    val exitRecip = intArrayOf(1, 0, 3, 2)
    val xOff = intArrayOf(0, 0, -boardDim.w, boardDim.w)
    val yOff = intArrayOf(-boardDim.h, boardDim.h, 0, 0)

    val oldBoardIdx = currentBoardIdx
    val destBoard = currentBoard!!.getExit(exit)
    if (destBoard != 0) {
        changeBoard(destBoard)
    } else {
        val savedCursorPos = caretPos
        caretPos += Pos(xOff[exit],yOff[exit])
        if (caretPos.outside(dim)) {
            caretPos = savedCursorPos
        }
        val newBoardIdx = operationAddBoard()
        if (newBoardIdx != -1) {
            boards[oldBoardIdx].setExit(exit, newBoardIdx)
            boards[newBoardIdx].setExit(exitRecip[exit], oldBoardIdx)
            canvas.setCaret(caretPos)
        } else {
            caretPos = savedCursorPos
        }
        afterUpdate()
    }
}