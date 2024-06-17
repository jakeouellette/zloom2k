package zedit2.components.editor.world

import zedit2.components.FancyFill
import zedit2.components.WorldEditor
import zedit2.model.Board
import zedit2.model.Stat
import zedit2.model.spatial.Pos
import zedit2.util.ZType
import java.awt.event.ActionListener
import java.util.*


internal fun WorldEditor.operationFloodfill(pos: Pos, fancy: Boolean) {
    val originalTile = this.getTileAt(pos, false) ?: return

    val filled = Array(dim.h) { ByteArray(dim.w) }
    val tileStats: List<Stat> = originalTile.stats
    val isStatted = tileStats != null && !tileStats.isEmpty()
    // TODO(jakeouellette): remove the mechanic of passing in a boolean object to floodfill
    floodFill(pos, originalTile.id, originalTile.col, isStatted, filled)

    if (!fancy) {
        val dirty = HashSet<Board>()
        for (fy in 0 until dim.h) {
            for (fx in 0 until dim.w) {
                if (filled[fy][fx].toInt() == 1) {
                    val board = putTileDeferred(Pos(fx, fy), bufferTile, WorldEditor.Companion.PutTypes.PUT_DEFAULT)
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
    startPos : Pos,
    id: Int,
    col: Int,
    statted: Boolean,
    filled: Array<ByteArray>
) {
    val dirs = arrayOf(Pos.LEFT, Pos.RIGHT, Pos.UP, Pos.DOWN)
    val stack = ArrayDeque<Pos>()
    stack.add(startPos)
    filled[startPos.x][startPos.y] = 1

    while (!stack.isEmpty()) {
        val pos = stack.pop()

        for (dir in dirs) {
            val nPos = pos + dir
            if (nPos.inside(dim)) {
                if (filled[nPos.x][nPos.y].toInt() == 0) {
                    val board = getBoardAt(nPos)
                    if (board != null) {
                        val modPos = nPos % boardDim
                        if (board.getTileId(modPos) == id) {
                            if (id == ZType.EMPTY || board.getTileCol(modPos) == col) {
                                if (board.getStatsAt(modPos).isNotEmpty() == statted) {
                                    filled[nPos.x][nPos.y] = 1
                                    stack.add(nPos)
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
    for (fy in 0 until dim.h) for (fx in 0 until dim.w) if (filled[fy][fx].toInt() == 1) boardListing.add(grid[fy / boardDim.h][fx / boardDim.w])
    val savedBoards = HashMap<Int, Board>()
    for (boardIdx in boardListing) savedBoards[boardIdx] = boards[boardIdx].clone()
    this.fancyFillDialog = true
    val listener = ActionListener { e ->
        val fill = e.source as FancyFill
        if (e.actionCommand == "updateFill") {
            // TODO(jakeouellette): Update to use Pos
            val tileXs = fill.xs
            val tileYs = fill.ys
            val tiles = fill.tiles
            val boardsHit = HashSet<Board?>()
            for (i in tileXs.indices) {
                boardsHit.add(putTileDeferred(Pos(tileXs[i], tileYs[i]), tiles[i],
                    WorldEditor.Companion.PutTypes.PUT_REPLACE_BOTH
                ))
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
            addRedraw(Pos(1, 1), dim.asPos -2)
            afterModification()
        } else if (e.actionCommand == "done") {
            fancyFillDialog = false
            afterUpdate()
        }
    }
    FancyFill(this, listener, filled)
}