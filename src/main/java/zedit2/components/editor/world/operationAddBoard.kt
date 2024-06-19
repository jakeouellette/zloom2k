package zedit2.components.editor.world

import zedit2.components.WorldEditor
import javax.swing.JOptionPane

fun WorldEditor.operationAddBoard(): Int {
    val response = JOptionPane.showInputDialog(frame, "Name for new board:")
    if (response != null) {
        val newBoard = blankBoard(response)
        val newBoardIdx = boards.size
        boards.add(newBoard)

        var addedToAtlas = false

        if (currentAtlas != null) {
            // TODO(jakeouellette): Update this with more Pos / Dim idiom
            val gridPos = caretPos / boardDim
            if (grid[gridPos.y][gridPos.x] == -1) {
                addedToAtlas = true
                grid[gridPos.y][gridPos.x] = newBoardIdx
                atlases[newBoardIdx] = currentAtlas!!

                val dirs = arrayOf(intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(1, 0))
                val dirReverse = intArrayOf(1, 0, 3, 2)

                for (exit in 0..3) {
                    val bx = gridPos.x + dirs[exit][0]
                    val by = gridPos.y + dirs[exit][1]
                    if (bx >= 0 && by >= 0 && bx < gridDim.w && by < gridDim.h) {
                        val boardAtIdx = grid[by][bx]
                        if (boardAtIdx != -1) {
                            val revExit = dirReverse[exit]
                            val boardAt = boards[boardAtIdx]
                            if (boardAt.getExit(revExit) == 0) {
                                boardAt.setExit(revExit, newBoardIdx)
                                newBoard.setExit(exit, boardAtIdx)
                            }
                        }
                    }
                }
            }
        }
        if (!addedToAtlas) {
            changeBoard(boards.size - 1)
        } else {
            invalidateCache()
            afterModification()
        }
        onBoardsUpdated(boards)
        return newBoardIdx
    }
    return -1
}