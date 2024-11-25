package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.model.Board
import zedit2.model.spatial.Pos
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.ZType
import kotlin.math.max
import kotlin.math.min

fun WorldEditor.operationSwapBoard(boardIdx1 : Int, boardIdx2a : Int) {
    Logger.i(TAG) {"Drag and drop processed: $boardIdx1 -> $boardIdx2a"}
    val boardIdx2 = if (boardIdx2a > boardIdx1) {
        boardIdx2a-1
    } else {
        boardIdx2a
    }
    val low = min(boardIdx2, boardIdx1)
    val high = max(boardIdx2, boardIdx1)
//    if (high-low)
    var sublist : List<Board> = boards.subList(low, high+1)
    if (boardIdx1 > boardIdx2) {
        sublist = listOf(sublist.last()) + sublist.subList(0,sublist.size-1)
    } else {
        sublist = sublist.subList(1,sublist.size) + listOf(sublist.first())
    }



    for (board in boards) {
        for (exit in 0..3) {
            val dest = board.getExit(exit)

            val newDest = getDest(
                dest = dest,
                low = low,
                high = high,
                boardIdx1 = boardIdx1,
                boardIdx2 = boardIdx2
            )
            Logger.i(TAG) { "Updated edge. $exit to $newDest" }
            if (newDest != null) {
                board.setExit(exit, newDest)
            }

        }
        for (y in 0 until board.dim.h) {
            for (x in 0 until board.dim.w) {
                val xyPos = Pos(x, y)
                val tile = board.getTile(xyPos, false)
                val tileId = tile.id
                when (tileId) {
                    ZType.PASSAGE -> {
                        Logger.i(TAG) { "Passage Found, checking stats $x, $y"}
                        for (stat in tile.stats) {
                            Logger.i(TAG) { " Stat: $stat, passage: ${stat.p3} boards: $boardIdx1, $boardIdx2"}
                            val dest = stat.p3

                            val newDest = getDest(
                                dest = dest,
                                low = low,
                                high = high,
                                boardIdx1 = boardIdx1,
                                boardIdx2 = boardIdx2
                            )
                            if (newDest != null) {
                                Logger.i(TAG) { "Updated passage. $x $y to $newDest"}
                                stat.p3 = newDest
                            }
                        }
                    }
                }
                // Shouldn't be needed.
                board.setTile(xyPos, tile)

            }
        }
    }
    for ((i, b) in sublist.withIndex()) {
        b.setDirty()
        boards[i+low] = b
    }
    currentBoardIdx = boardIdx2
    this.currentBoard = boards[currentBoardIdx]
    // Reset the current board because it is saved by index and reference
//    onBoardUpdated(this.worldData, boards, this.boardIdx)
    atlases.clear()
    currentAtlas = null

    // TODO(jakeouellette): Update board selector
    onBoardsUpdated(boards)
    atlas()
    changeBoard(this.currentBoardIdx)

    invalidateCache()
    afterModification()

    return
}

fun getDest(dest: Int, low : Int, high :Int, boardIdx1: Int, boardIdx2 : Int): Int? {
    if(dest in low..high) {
        // if the source board is the high board,
        if (boardIdx1 > boardIdx2) {
            // and the dest board for this link is the high board
            if (dest == high) {
                // set it to boardIdx1
                return low
            } else {
                // else, all the indicies move up one
                return dest+1
            }
        } else {
            // when the source board is the low board,
            if (dest == low) {
                // if the target is the low board, use the high board, that is moving to that location.
                return high
            } else {
                // else, move everything up 1.
                return dest-1
            }
        }
    }
    return null
}
