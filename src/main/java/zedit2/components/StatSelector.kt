package zedit2.components

import zedit2.model.Tile
import zedit2.util.ZType
import zedit2.util.CP437.toUnicode
import zedit2.model.Board
import java.awt.Dialog
import java.awt.event.*
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel

class StatSelector(
    editor: WorldEditor, board: Board, private val listener: ActionListener?, private val options: Array<String>,
    upKeybind: KeyStroke?, downKeybind: KeyStroke?
) {
    /*
case COL_IMAGE:
case COL_STATID:
case COL_TYPE:
case COL_COLOUR:
case COL_X:
case COL_Y:
case COL_NAME:
case COL_CYCLE:
case COL_STEPX:
case COL_STEPY:
case COL_P1:
case COL_P2:
case COL_P3:
case COL_FOLLOWER:
case COL_LEADER:
case COL_IP:
case COL_CODELEN:
case COL_UID:
case COL_UCO:
     */
    private val dialog = JDialog()
    private val table: JTable
    private val tableModel: AbstractTableModel

    init {
        Util.addEscClose(dialog, dialog.rootPane)
        dialog.modalityType = Dialog.ModalityType.APPLICATION_MODAL
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.title =
            "Stat list :: Board #" + editor.boardIdx + " :: " + toUnicode(board.getName()) + " :: Double-click to select"
        dialog.setIconImage(editor.canvas.extractCharImage(240, 0x1F, 2, 2, false, "$"))
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                editor.canvas.setIndicate(null, null)
            }
        })

        tableModel = object : AbstractTableModel() {
            override fun getRowCount(): Int {
                return board.statCount
            }

            override fun getColumnCount(): Int {
                return colNames.size
            }

            override fun getColumnName(columnIndex: Int): String {
                return colNames[columnIndex]
            }

            override fun getColumnClass(columnIndex: Int): Class<*>? {
                return when (columnIndex) {
                    COL_IMAGE, COL_COLOUR, COL_UCO -> Icon::class.java
                    COL_STATID, COL_X, COL_Y, COL_CYCLE, COL_STEPX, COL_STEPY, COL_P1, COL_P2, COL_P3, COL_FOLLOWER, COL_LEADER, COL_IP, COL_CODELEN, COL_ORDER -> java.lang.Integer::class.java
                    COL_TYPE, COL_NAME, COL_UID -> java.lang.String::class.java
                    else -> null
                }
            }

            override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
                val stat = board.getStat(rowIndex) ?: throw RuntimeException("expected board to have stat at row $rowIndex")
                val x = stat.x - 1
                val y = stat.y - 1
                val tile = if (x >= 0 && y >= 0 && x < board.width && y < board.height) {
                    board.getTile(x, y, false)
                } else {
                    Tile(-1, -1, board.getStatsAt(x, y))
                }
                val worldData = editor.worldData
                val szzt = worldData.isSuperZZT
                val canvas = editor.canvas

                when (columnIndex) {
                    COL_IMAGE -> {
                        val chr = ZType.getChar(worldData.isSuperZZT, tile)
                        val col = ZType.getColour(worldData.isSuperZZT, tile)
                        return ImageIcon(
                            canvas.extractCharImage(
                                chr,
                                col,
                                if (worldData.isSuperZZT) 2 else 1,
                                1,
                                false,
                                "$"
                            )
                        )
                    }

                    COL_STATID -> return stat.statId
                    COL_TYPE -> return ZType.getName(szzt, tile.id)
                    COL_COLOUR -> {
                        val col = tile.col
                        return ImageIcon(canvas.extractCharImage(0, col, 1, 1, false, "_#_"))
                    }

                    COL_X -> return x + 1
                    COL_Y -> return y + 1
                    COL_NAME -> {
                        run {
                            val codeLen = stat.codeLength
                            if (codeLen >= 0) {
                                return stat.name
                            } else {
                                val boundTo = -codeLen
                                if (boundTo < board.statCount) {
                                    return board.getStat(boundTo)!!.name
                                }
                            }
                        }
                        return stat.cycle
                    }

                    COL_CYCLE -> return stat.cycle
                    COL_STEPX -> return stat.stepX
                    COL_STEPY -> return stat.stepY
                    COL_P1 -> return stat.p1
                    COL_P2 -> return stat.p2
                    COL_P3 -> return stat.p3
                    COL_FOLLOWER -> return stat.follower
                    COL_LEADER -> return stat.leader
                    COL_IP -> return stat.ip
                    COL_CODELEN -> return stat.codeLength
                    COL_UID -> return ZType.getName(szzt, stat.uid)
                    COL_UCO -> {
                        val col = stat.uco
                        return ImageIcon(canvas.extractCharImage(0, col, 1, 1, false, "_#_"))
                    }

                    COL_ORDER -> return stat.order
                    else -> throw UnsupportedOperationException("Unexpected Column index type")
                }
            }
        }
        table = JTable(tableModel)
        table.autoCreateRowSorter = true

        // Set column preferred widths
        table.columnModel.getColumn(COL_IMAGE).preferredWidth = if (editor.worldData.isSuperZZT) 16 else 8
        table.columnModel.getColumn(COL_STATID).preferredWidth = 26
        table.columnModel.getColumn(COL_TYPE).preferredWidth = 64
        table.columnModel.getColumn(COL_COLOUR).preferredWidth = 25
        table.columnModel.getColumn(COL_X).preferredWidth = 20
        table.columnModel.getColumn(COL_Y).preferredWidth = 20
        table.columnModel.getColumn(COL_NAME).preferredWidth = 64
        table.columnModel.getColumn(COL_CYCLE).preferredWidth = 36
        table.columnModel.getColumn(COL_STEPX).preferredWidth = 42
        table.columnModel.getColumn(COL_STEPY).preferredWidth = 42
        table.columnModel.getColumn(COL_P1).preferredWidth = 27
        table.columnModel.getColumn(COL_P2).preferredWidth = 27
        table.columnModel.getColumn(COL_P3).preferredWidth = 27
        table.columnModel.getColumn(COL_FOLLOWER).preferredWidth = 50
        table.columnModel.getColumn(COL_LEADER).preferredWidth = 43
        table.columnModel.getColumn(COL_IP).preferredWidth = 55
        table.columnModel.getColumn(COL_CODELEN).preferredWidth = 55
        table.columnModel.getColumn(COL_UID).preferredWidth = 64
        table.columnModel.getColumn(COL_UCO).preferredWidth = 25
        table.columnModel.getColumn(COL_ORDER).preferredWidth = 38
        table.autoResizeMode = JTable.AUTO_RESIZE_OFF

        val statSelector = this
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    if ((e.modifiersEx and InputEvent.CTRL_DOWN_MASK) > 0) {
                        statSelector.selectRow(1)
                    } else {
                        statSelector.selectRow(0)
                    }
                    e.consume()
                }
                if (upKeybind != null && Util.keyMatches(e, upKeybind)) {
                    statSelector.selectRow(3)
                }
                if (downKeybind != null && Util.keyMatches(e, downKeybind)) {
                    statSelector.selectRow(4)
                }
            }
        })
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (e.clickCount == 2) {
                        statSelector.selectRow(0)
                    }
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    val row = table.rowAtPoint(e.point)
                    if (row == -1) return
                    table.clearSelection()
                    table.addRowSelectionInterval(row, row)
                    val popupMenu = JPopupMenu()
                    for (i in options.indices) {
                        val optionIdx = i
                        val option = options[i]
                        val menuItem = JMenuItem(option)
                        menuItem.addActionListener { e1: ActionEvent? -> statSelector.selectRow(optionIdx) }
                        popupMenu.add(menuItem)
                    }
                    popupMenu.show(
                        dialog, e.xOnScreen - dialog.locationOnScreen.x,
                        e.yOnScreen - dialog.locationOnScreen.y
                    )
                }
            }
        })
        table.selectionModel.addListSelectionListener { e: ListSelectionEvent? ->
            val canvas = editor.canvas
            val rows = table.selectedRows
            val indicateX = IntArray(rows.size)
            val indicateY = IntArray(rows.size)
            for (i in rows.indices) {
                val row = table.convertRowIndexToModel(rows[i])
                var x = board.getStat(row)!!.x - 1
                var y = board.getStat(row)!!.y - 1
                if (x >= 0 && y >= 0 && x < board.width && y < board.height) {
                    x += editor.boardXOffset
                    y += editor.boardYOffset
                    indicateX[i] = x
                    indicateY[i] = y
                } else {
                    indicateX[i] = -1
                    indicateY[i] = -1
                }
            }
            canvas.setIndicate(indicateX, indicateY)
        }

        val scroll = JScrollPane(table)
        dialog.contentPane.add(scroll)
        dialog.pack()
        dialog.setLocationRelativeTo(editor.frameForRelativePositioning)
        dialog.isVisible = true
    }

    private fun selectRow(option: Int) {
        var option = option
        option = Util.clamp(option, 0, options.size - 1)
        if (table.selectedRow == -1) return
        val selectedRow = table.convertRowIndexToModel(table.selectedRow)
        if (listener != null) {
            val command = String.format("%d|%d", option, selectedRow)
            val actionEvent = ActionEvent(this, ActionEvent.ACTION_PERFORMED, command)
            listener.actionPerformed(actionEvent)
        }
    }

    fun close() {
        dialog.dispose()
    }

    fun dataChanged() {
        val selectedRow = table.convertRowIndexToModel(table.selectedRow)
        tableModel.fireTableDataChanged()
        if (selectedRow < table.rowCount) {
            val viewRow = table.convertRowIndexToView(selectedRow)
            table.addRowSelectionInterval(viewRow, viewRow)
        }
    }

    fun focusStat(i: Int) {
        var i = i
        if (table.rowCount < 1) {
            return
        }
        if (i < 0) {
            i = 0
        }
        if (i >= table.rowCount) {
            i = table.rowCount - 1
        }
        table.clearSelection()
        table.addRowSelectionInterval(i, i)
    }

    companion object {
        private val colNames = arrayOf(
            "", "#", "Type", "Colour", "X", "Y", "Name", "Code Len", "Cycle", "X-Step", "Y-Step",
            "P1", "P2", "P3", "Follower", "Leader", "Instr. Ptr", "Under ID", "Under Col", "Order"
        )
        private const val COL_IMAGE = 0
        private const val COL_STATID = 1
        private const val COL_TYPE = 2
        private const val COL_COLOUR = 3
        private const val COL_X = 4
        private const val COL_Y = 5
        private const val COL_NAME = 6
        private const val COL_CODELEN = 7
        private const val COL_CYCLE = 8
        private const val COL_STEPX = 9
        private const val COL_STEPY = 10
        private const val COL_P1 = 11
        private const val COL_P2 = 12
        private const val COL_P3 = 13
        private const val COL_FOLLOWER = 14
        private const val COL_LEADER = 15
        private const val COL_IP = 16
        private const val COL_UID = 17
        private const val COL_UCO = 18
        private const val COL_ORDER = 19

        @JvmStatic
        fun getStatIdx(actionCommand: String): Int {
            return actionCommand.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].toInt()
        }

        @JvmStatic
        fun getOption(actionCommand: String): Int {
            return actionCommand.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].toInt()
        }
    }
}
