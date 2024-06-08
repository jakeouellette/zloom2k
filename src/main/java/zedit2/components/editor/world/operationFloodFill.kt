package zedit2.components.editor.world

import zedit2.components.FancyFill
import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.PUT_DEFAULT
import zedit2.components.WorldEditor.Companion.PUT_REPLACE_BOTH
import zedit2.model.Board
import zedit2.model.Stat
import zedit2.util.ZType
import java.awt.event.ActionListener
import java.util.*


internal fun WorldEditor.operationFloodfill(x: Int, y: Int, fancy: Boolean) {
    val originalTile = this.getTileAt(x, y, false) ?: return

    val filled = Array(height) { ByteArray(width) }
    val tileStats: List<Stat> = originalTile.stats
    val isStatted = tileStats != null && !tileStats.isEmpty()

    floodFill(x, y, originalTile.id, originalTile.col, isStatted, filled)

    if (!fancy) {
        val dirty = HashSet<Board>()
        for (fy in 0 until height) {
            for (fx in 0 until width) {
                if (filled[fy][fx].toInt() == 1) {
                    val board = putTileDeferred(fx, fy, bufferTile, PUT_DEFAULT)
                    if (board != null) dirty.add(board)
                }
            }
        }
        for (board in dirty) {
            board.finaliseStats()
        }
        afterModification()
    } else {
        fancyFill(filled)
    }
}

internal fun WorldEditor.floodFill(
    startX: Int,
    startY: Int,
    id: Int,
    col: Int,
    statted: Boolean,
    filled: Array<ByteArray>
) {
    val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
    val stack = ArrayDeque<Int>()
    stack.add(startX)
    stack.add(startY)
    filled[startY][startX] = 1

    while (!stack.isEmpty()) {
        val x = stack.pop()
        val y = stack.pop()

        for (dir in dirs) {
            val nx = x + dir[0]
            val ny = y + dir[1]
            if (nx >= 0 && ny >= 0 && nx < width && ny < height) {
                if (filled[ny][nx].toInt() == 0) {
                    val board = getBoardAt(nx, ny)
                    if (board != null) {
                        if (board.getTileId(nx % boardW, ny % boardH) == id) {
                            if (id == ZType.EMPTY || board.getTileCol(nx % boardW, ny % boardH) == col) {
                                if (!board.getStatsAt(nx % boardW, ny % boardH).isEmpty() == statted) {
                                    filled[ny][nx] = 1
                                    stack.add(nx)
                                    stack.add(ny)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun WorldEditor.fancyFill(filled: Array<ByteArray>) {
    val boardListing = HashSet<Int>()
    for (fy in 0 until height) for (fx in 0 until width) if (filled[fy][fx].toInt() == 1) boardListing.add(grid[fy / boardH][fx / boardW])
    val savedBoards = HashMap<Int, Board>()
    for (boardIdx in boardListing) savedBoards[boardIdx] = boards[boardIdx].clone()
    this.fancyFillDialog = true
    val listener = ActionListener { e ->
        val fill = e.source as FancyFill
        if (e.actionCommand == "updateFill") {
            val tileXs = fill.xs
            val tileYs = fill.ys
            val tiles = fill.tiles
            val boardsHit = HashSet<Board?>()
            for (i in tileXs.indices) {
                boardsHit.add(putTileDeferred(tileXs[i], tileYs[i], tiles[i], PUT_REPLACE_BOTH))
            }
            for (board in boardsHit) {
                board!!.finaliseStats()
            }
            afterModification()
        } else if (e.actionCommand == "undo") {
            fancyFillDialog = false
            for (boardIdx in boardListing) {
                savedBoards[boardIdx]!!.cloneInto(boards[boardIdx])
            }
            addRedraw(1, 1, width - 2, height - 2)
            afterModification()
        } else if (e.actionCommand == "done") {
            fancyFillDialog = false
            afterUpdate()
        }
    }
    FancyFill(this, listener, filled)
}