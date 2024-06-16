package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.model.spatial.Pos


internal fun WorldEditor.operationExitCreate(exit: Int) {
    val exitRecip = intArrayOf(1, 0, 3, 2)
    val xOff = intArrayOf(0, 0, -boardDim.w, boardDim.w)
    val yOff = intArrayOf(-boardDim.h, boardDim.h, 0, 0)

    val oldBoardIdx = boardIdx
    val destBoard = currentBoard!!.getExit(exit)
    if (destBoard != 0) {
        changeBoard(destBoard)
    } else {
        val savedCursorPos = cursorPos
        cursorPos += Pos(xOff[exit],yOff[exit])
        if (cursorPos.outside(dim)) {
            cursorPos = savedCursorPos
        }
        val newBoardIdx = operationAddBoard()
        if (newBoardIdx != -1) {
            boards[oldBoardIdx].setExit(exit, newBoardIdx)
            boards[newBoardIdx].setExit(exitRecip[exit], oldBoardIdx)
            canvas.setCursor(cursorPos)
        } else {
            cursorPos = savedCursorPos
        }
        afterUpdate()
    }
}