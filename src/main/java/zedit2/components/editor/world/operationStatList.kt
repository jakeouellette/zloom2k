package zedit2.components.editor.world

import zedit2.components.StatSelector
import zedit2.components.StatSelector.Companion.getOption
import zedit2.components.StatSelector.Companion.getStatIdx
import zedit2.components.Util.getKeyStroke
import zedit2.components.Util.keyStrokeString
import zedit2.components.WorldEditor
import zedit2.model.Tile
import java.awt.event.ActionEvent
import javax.swing.KeyStroke

internal fun WorldEditor.operationStatList() {
    val board = getBoardAt(caretPos)
    val boardXY = caretPos / boardDim * boardDim
    if (board == null) return
    val contextOptions = arrayOf(
        "Modify",
        "Modify (advanced)",
        "Move to 1",
        "Move up",
        "Move down",
        "Move to end"
    )
    var upStroke: KeyStroke? = getKeyStroke(globalEditor, "COMMA")

    val upString = keyStrokeString(upStroke)
    if (!upString.isNullOrEmpty()) {
        contextOptions[3] = contextOptions[3] + " (" + upString + ")"
    } else {
        upStroke = null
    }

    var downStroke: KeyStroke? = getKeyStroke(globalEditor, "PERIOD")

    val downString = keyStrokeString(downStroke)
    if (!downString.isNullOrEmpty()) {
        contextOptions[4] = contextOptions[4] + " (" + downString + ")"
    } else {
        downStroke = null
    }

    StatSelector(
        this.boardPosOffset,
        this.currentBoardIdx,
        this.canvas,
        board,
        { e: ActionEvent ->
            val value = getStatIdx(e.actionCommand)
            val option = getOption(e.actionCommand)
            val stat = board.getStat(value)
            when (option) {
                0, 1 -> {
                    val pos = stat!!.pos - 1

                    createTileEditor(
                        board = board,
                        pos = pos,
                        callback = { resultTile: Tile ->
                            setStats(board, boardXY, pos, resultTile.stats)
                            if (resultTile.id != -1) {
                                val newPos = pos + boardXY
                                addRedraw(newPos, newPos)
                                board.setTileRaw(pos, resultTile.id, resultTile.col)
                            }
                            (e.source as StatSelector).dataChanged()
                            afterModification()
                        },
                        advanced = option == 1,
                        exempt = false,
                        selected = value,
                        tile = null,
                        stats = board.getStatsAt(pos)
                    )
                }

                2, 3, 4, 5 -> {
                    val destination = when (option) {
                        2 -> 1
                        3 -> value - 1
                        4 -> value + 1
                        else -> board.statCount - 1
                    }
                    if (moveStatTo(board, value, destination)) {
                        (e.source as StatSelector).dataChanged()
                        (e.source as StatSelector).focusStat(destination)
                        afterModification()
                    }
                }
            }
        },
        contextOptions,
        upStroke,
        downStroke,
        this.frameForRelativePositioning,
        this.worldData.isSuperZZT,
        { xys -> this.canvas.setIndicate(xys) })
}