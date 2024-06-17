package zedit2.components.editor.world

import zedit2.components.DosCanvas
import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import zedit2.model.Board
import zedit2.model.MouseState
import java.awt.Color

// TODO(jakeouellette): This Class is tightly coupled to the editor.
// Instead, make it possible to only use editor abstractions to modify it.
class UndoHandler(val editor: WorldEditor) {
    private val undoList = HashMap<Int, ArrayList<Board>>()
    private val undoPositions = HashMap<Int, Int>()
    private var undoDirty = false

    internal fun undo(redo: Boolean) {
        with(editor) {
            // Find most recent timestamp value in the undos
            var mostRecentTimestamp = if (redo) Long.MAX_VALUE else Long.MIN_VALUE

            val boardsToUndo = ArrayList<Int>()
            for (row in grid) {
                for (boardIdx in row) {
                    if (boardIdx != -1) {
                        val undoBoards = undoList[boardIdx] ?: continue
                        val undoPos = undoPositions[boardIdx]!!
                        val newUndoPos = undoPos + (if (redo) 1 else -1)
                        if (newUndoPos < 0) continue
                        if (newUndoPos >= undoBoards.size) continue
                        val undoTimestamp = undoBoards[newUndoPos].dirtyTimestamp
                        if ((!redo && undoTimestamp > mostRecentTimestamp) ||
                            (redo && undoTimestamp < mostRecentTimestamp)
                        ) {
                            mostRecentTimestamp = undoTimestamp
                            boardsToUndo.clear()
                        }
                        if (undoTimestamp == mostRecentTimestamp) {
                            boardsToUndo.add(boardIdx)
                        }
                    }
                }
            }

            if (boardsToUndo.isEmpty()) {
                val operationName = if (redo) "Redo" else "Undo"
                editingModePane.display(Color.RED, 1500, "Can't $operationName")
            } else {
                for (boardIdx in boardsToUndo) {
                    val undoPos = undoPositions[boardIdx]!! + (if (redo) 1 else -1)
                    undoPositions[boardIdx] = undoPos
                    val undoBoard = undoList[boardIdx]!![undoPos]
                    boards[boardIdx] = undoBoard.clone()
                }
                invalidateCache()
                afterModification()
            }
        }
    }

    private fun addUndo() {
        undoDirty = false
        with(editor) {
            for (row in grid) {
                for (boardIdx in row) {
                    if (boardIdx != -1) {
                        var undoBoards = undoList[boardIdx]
                        var undoPos = -1
                        var addTo = true
                        if (undoBoards == null) {
                            undoBoards = ArrayList()
                            undoList[boardIdx] = undoBoards
                        } else {
                            undoPos = undoPositions[boardIdx]!!

                            val undoBoard = undoBoards[undoPos]
                            if (boards[boardIdx].timestampEquals(undoBoard)) {
                                addTo = false
                            }
                        }

                        if (addTo) {
                            // Seems this board was modified.
                            // First, cut off everything after the current undo position
                            while (undoBoards.size > undoPos + 1) {
                                undoBoards.removeAt(undoPos + 1)
                            }
                            // Now add this board to the undo list
                            undoBoards.add(boards[boardIdx].clone())
                            // Too many?
                            val undoBufferSize = GlobalEditor.getInt("UNDO_BUFFER_SIZE", 100)
                            if (undoBoards.size > undoBufferSize) {
                                undoBoards.removeAt(0)
                            }
                            // Update the undo position
                            undoPositions[boardIdx] = undoBoards.size - 1
                            //System.out.println("New undo list length for board " + boardIdx + ": " + undoBoards.size());
                        }
                    }
                }
            }
        }
    }

    internal fun resetUndoList() {
        with(editor) {
            undoList.clear()
            undoPositions.clear()
            for (row in grid) {
                for (boardIdx in row) {
                    if (boardIdx != -1) {
                        val boardUndoList = ArrayList<Board>()
                        boardUndoList.add(boards[boardIdx].clone())
                        undoList[boardIdx] = boardUndoList
                        undoPositions[boardIdx] = 0
                    }
                }
            }
        }
    }

    fun afterModification() {
        this.undoDirty = true
    }

    fun afterUpdate() {
        if (undoDirty && editor.mouseState != MouseState.DRAW && !editor.fancyFillDialog) {
            addUndo()
        }
    }
}