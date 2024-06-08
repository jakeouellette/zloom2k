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
            val gridX = cursorX / boardW
            val gridY = cursorY / boardH
            if (grid[gridY][gridX] == -1) {
                addedToAtlas = true
                grid[gridY][gridX] = newBoardIdx
                atlases[newBoardIdx] = currentAtlas!!

                val dirs = arrayOf(intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(1, 0))
                val dirReverse = intArrayOf(1, 0, 3, 2)

                for (exit in 0..3) {
                    val bx = gridX + dirs[exit][0]
                    val by = gridY + dirs[exit][1]
                    if (bx >= 0 && by >= 0 && bx < gridW && by < gridH) {
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