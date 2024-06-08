package zedit2.components.editor.world

import zedit2.components.WorldEditor


internal fun WorldEditor.operationExitCreate(exit: Int) {
    val exitRecip = intArrayOf(1, 0, 3, 2)
    val xOff = intArrayOf(0, 0, -boardW, boardW)
    val yOff = intArrayOf(-boardH, boardH, 0, 0)

    val oldBoardIdx = boardIdx
    val destBoard = currentBoard!!.getExit(exit)
    if (destBoard != 0) {
        changeBoard(destBoard)
    } else {
        val savedCursorX = cursorX
        val savedCursorY = cursorY
        cursorX += xOff[exit]
        cursorY += yOff[exit]
        if (cursorX < 0 || cursorY < 0 || cursorX >= width || cursorY >= height) {
            cursorX = savedCursorX
            cursorY = savedCursorY
        }
        val newBoardIdx = operationAddBoard()
        if (newBoardIdx != -1) {
            boards[oldBoardIdx].setExit(exit, newBoardIdx)
            boards[newBoardIdx].setExit(exitRecip[exit], oldBoardIdx)
            canvas.setCursor(cursorX, cursorY)
        } else {
            cursorX = savedCursorX
            cursorY = savedCursorY
        }
        afterUpdate()
    }
}