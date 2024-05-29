package zedit2

import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.io.IOException
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

public class BoardManager @JvmOverloads constructor(
    private val editor: WorldEditor,
    private var boards: List<Board>,
    private val modal: Boolean = true
) {
    private var dialog: JDialog? = null
    private var worldData: WorldData
    private lateinit var table: JTable
    private var tableModel: AbstractTableModel? = null
    private val szzt: Boolean
    private lateinit var boardSelectArray: Array<String?>
    private var upButton: JButton? = null
    private var downButton: JButton? = null
    private var delButton: JButton? = null

    init {
        // TODO(jakeouellette): Handle more gracefully this null check
        worldData = editor.worldData!!
        szzt = worldData.isSuperZZT

        updateBoardSelectArray()
        generateTable()
    }

    constructor(editor: WorldEditor, boards: List<Board>, deleteBoard: Int) : this(editor, boards, false) {
        if (boards.size > 1) {
            table.clearSelection()
            table.rowSorter.sortKeys = null
            table.addRowSelectionInterval(deleteBoard, deleteBoard)
            SwingUtilities.invokeLater { this.delSelected() }
        } else {
            JOptionPane.showMessageDialog(
                dialog,
                "Can't delete the only board.",
                "Board deletion error",
                JOptionPane.ERROR_MESSAGE
            )
        }
        dialog!!.dispose()
    }

    private fun generateTable() {
        val dialog = object : JDialog() {
            init {
                if (modal) this.modalityType = ModalityType.APPLICATION_MODAL
                Util.addEscClose(this, this.rootPane)
                this.defaultCloseOperation = DISPOSE_ON_CLOSE
                this.title = "Board list"

                this.setIconImage(editor.canvas.extractCharImage(240, 0x1F, 2, 2, false, "$"))
                this.addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent) {
                        editor.canvas.setIndicate(null, null)
                    }
                })
            }
        }
        this.dialog = dialog


        val colDark = if (szzt) -1 else 10
        val colCameraX = if (szzt) 10 else -1
        val colCameraY = if (szzt) 11 else -1
        val colRestart = if (szzt) 12 else 11
        val numCols = if (szzt) 13 else 12

        tableModel = object : AbstractTableModel() {
            override fun getRowCount(): Int {
                return boards.size
            }

            override fun getColumnCount(): Int {
                return numCols
            }

            override fun getColumnName(columnIndex: Int): String {
                when (columnIndex) {
                    COL_NUM -> return "#"
                    COL_NAME -> return "Board name"
                    COL_SHOTS -> return "Shots"
                    COL_TIMELIMIT -> return "Time limit"
                    COL_PLAYERX -> return "Player X"
                    COL_PLAYERY -> return "Player Y"
                    COL_EXITN -> return "North exit"
                    COL_EXITS -> return "South exit"
                    COL_EXITE -> return "East exit"
                    COL_EXITW -> return "West exit"
                    else -> {
                        if (columnIndex == colDark) return "Dark"
                        if (columnIndex == colCameraX) return "Camera X"
                        if (columnIndex == colCameraY) return "Camera Y"
                        if (columnIndex == colRestart) return "Restart if hurt"
                        return "?"
                    }
                }
            }

            override fun getColumnClass(columnIndex: Int): Class<*>? {
                when (columnIndex) {
                    COL_NUM, COL_SHOTS, COL_TIMELIMIT, COL_PLAYERX, COL_PLAYERY, COL_EXITN, COL_EXITS, COL_EXITE, COL_EXITW -> return Int::class.java
                    COL_NAME -> return String::class.java
                    else -> {
                        if (columnIndex == colDark) return Boolean::class.java
                        if (columnIndex == colCameraX) return Int::class.java
                        if (columnIndex == colCameraY) return Int::class.java
                        if (columnIndex == colRestart) return Boolean::class.java
                        return null
                    }
                }
            }

            override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
                return columnIndex != COL_NUM
            }

            override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
                val board = boards[rowIndex]
                when (columnIndex) {
                    COL_NUM -> return rowIndex
                    COL_NAME -> return CP437.toUnicode(board!!.getName())
                    COL_SHOTS -> return board!!.getShots()
                    COL_TIMELIMIT -> return board!!.getTimeLimit()
                    COL_PLAYERX -> return board!!.getPlayerX()
                    COL_PLAYERY -> return board!!.getPlayerY()
                    COL_EXITN, COL_EXITS, COL_EXITE, COL_EXITW -> return board!!.getExit(columnToExit(columnIndex))
                    else -> {
                        if (columnIndex == colDark) return board!!.isDark
                        if (columnIndex == colCameraX) return board!!.cameraX
                        if (columnIndex == colCameraY) return board!!.cameraY
                        if (columnIndex == colRestart) return board!!.isRestartOnZap()
                        // TODO(jakeouellette): this might not be okay. (Was previously returning a null)
                        throw RuntimeException("Unexpected column, did not map to any value.")
                    }
                }
            }

            override fun setValueAt(value: Any, rowIndex: Int, columnIndex: Int) {
                val board = boards[rowIndex]
                when (columnIndex) {
                    COL_NAME -> {
                        board!!.setName(CP437.toBytes(value as String))
                        return
                    }

                    COL_SHOTS -> {
                        board!!.setShots((value as Int))
                        return
                    }

                    COL_TIMELIMIT -> {
                        board!!.setTimeLimit((value as Int))
                        return
                    }

                    COL_PLAYERX -> {
                        board!!.setPlayerX((value as Int))
                        return
                    }

                    COL_PLAYERY -> {
                        board!!.setPlayerY((value as Int))
                        return
                    }

                    COL_EXITN, COL_EXITS, COL_EXITE, COL_EXITW -> {
                        val boardIdx = getBoardIdx(value)
                        if (boardIdx in 0..255) {
                            board!!.setExit(columnToExit(columnIndex), boardIdx)
                        }
                        return
                    }

                    else -> {
                        if (columnIndex == colDark) board!!.isDark = (value as Boolean)
                        if (columnIndex == colCameraX) board!!.cameraX = (value as Int)
                        if (columnIndex == colCameraY) board!!.cameraY = (value as Int)
                        if (columnIndex == colRestart) board!!.setRestartOnZap((value as Boolean))
                    }
                }
            }
        }
        table = object : JTable(tableModel) {
            override fun getCellRenderer(rowIndex: Int, columnIndex: Int): TableCellRenderer {
                val renderer =
                    TableCellRenderer { table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int ->
                        val label: JLabel
                        val boardIdx = value as Int
                        label = if (boardIdx >= boards.size) {
                            JLabel(String.format("%d. (invalid)", boardIdx))
                        } else {
                            JLabel(boardSelectArray[boardIdx])
                        }
                        if (isSelected) {
                            label.isOpaque = true
                            label.background = table.selectionBackground
                            label.foreground = table.selectionForeground
                        }
                        label
                    }

                return when (columnIndex) {
                    COL_EXITN, COL_EXITS, COL_EXITE, COL_EXITW -> renderer
                    else -> super.getCellRenderer(rowIndex, columnIndex)
                }
            }

            override fun getCellEditor(rowIndex: Int, columnIndex: Int): TableCellEditor {
                when (columnIndex) {
                    COL_EXITN, COL_EXITS, COL_EXITE, COL_EXITW -> {
                        val exitBox = JComboBox(boardSelectArray)

                        val board = boards[table.convertRowIndexToModel(rowIndex)]
                        val boardExit = board.getExit(columnToExit(columnIndex))
                        if (boardExit >= 0 && boardExit < boardSelectArray.size) {
                            exitBox.setSelectedIndex(boardExit)
                        } else {
                            exitBox.setEditable(true)
                        }
                        return DefaultCellEditor(exitBox)
                    }

                    else -> return super.getCellEditor(rowIndex, columnIndex)
                }
            }
        }
        table.setAutoCreateRowSorter(true)
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
        table.getSelectionModel().addListSelectionListener { e: ListSelectionEvent? -> updateButtons() }

        val scroll = JScrollPane(table)
        val buttonPanel = JPanel(GridLayout(0, 1))
        upButton = JButton("↑")
        downButton = JButton("↓")
        delButton = JButton("×")
        val exportButton = JButton("Export")
        updateButtons()
        upButton!!.addActionListener { e: ActionEvent? -> moveSelected(-1) }
        downButton!!.addActionListener { e: ActionEvent? -> moveSelected(1) }
        delButton!!.addActionListener { e: ActionEvent? -> delSelected() }
        exportButton.addActionListener { e: ActionEvent? -> exportSelected() }
        val note = " (note: This will change board exits and passages.)"
        upButton!!.toolTipText = "Move selected boards up$note"
        downButton!!.toolTipText = "Move selected boards down$note"
        delButton!!.toolTipText = "Delete selected boards$note"
        exportButton.toolTipText = "Export selected boards"

        buttonPanel.add(upButton)
        buttonPanel.add(downButton)
        buttonPanel.add(delButton)
        buttonPanel.add(exportButton)
        dialog!!.contentPane.layout = BorderLayout()
        dialog!!.add(scroll, BorderLayout.CENTER)
        dialog!!.add(buttonPanel, BorderLayout.EAST)
        dialog!!.pack()
        dialog!!.setLocationRelativeTo(editor.frameForRelativePositioning)
        dialog!!.isVisible = true
    }

    private fun exportSelected() {
        if (table.selectedRows.isEmpty()) return
        val fileChooser = JFileChooser()
        fileChooser.currentDirectory = editor.globalEditor.defaultDirectory
        fileChooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        fileChooser.isAcceptAllFileFilterUsed = false
        if (fileChooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
            val targetDir = fileChooser.selectedFile
            val boards = editor.boards
            for (viewRow in table.selectedRows) {
                val modelRow = table.convertRowIndexToModel(viewRow)
                val file = File(targetDir, "$modelRow.brd")
                try {
                    boards[modelRow].saveTo(file)
                } catch (e: IOException) {
                    JOptionPane.showMessageDialog(dialog, e, "Error exporting board", JOptionPane.ERROR_MESSAGE)
                }
            }
        }
    }

    private fun updateBoardSelectArray() {
        boardSelectArray = generateBoardSelectArray(boards, true)
    }

    private fun updateButtons() {
        upButton!!.isEnabled = true
        downButton!!.isEnabled = true
        delButton!!.isEnabled = true
        val rows = table.selectedRows

        // Can't do anything if no rows are selected, or if all the rows are selected
        if (rows.isEmpty() || rows.size == boards.size) {
            upButton!!.isEnabled = false
            downButton!!.isEnabled = false
            delButton!!.isEnabled = false
            return
        }

        // Can't move up if the top row is selected or down if the bottom row is selected
        for (viewRow in rows) {
            val modelRow = table.convertRowIndexToModel(viewRow)
            if (modelRow == 0) upButton!!.isEnabled = false
            if (modelRow == boards.size - 1) downButton!!.isEnabled = false
        }
    }

    private fun defaultMapping(): IntArray {
        val remapping = IntArray(boards.size)
        for (i in remapping.indices) {
            remapping[i] = i
        }
        return remapping
    }

    private fun delSelected() {
        table.rowSorter.sortKeys = null

        val remapping = defaultMapping()
        for (viewRow in table.selectedRows) {
            val modelRow = table.convertRowIndexToModel(viewRow)
            remapping[modelRow] = -1
        }

        // Collapse it down
        var writeHead = 0
        for (readHead in remapping.indices) {
            if (remapping[readHead] == -1) continue
            remapping[writeHead] = remapping[readHead]
            writeHead++
        }
        while (writeHead < remapping.size) {
            remapping[writeHead++] = -1
        }

        doRemap(remapping, "deletion")
    }

    private fun doRemap(remapping: IntArray, desc: String) {
        val newBoardList = ArrayList<Board>()
        val newWorldData = worldData.clone()
        val oldToNew = HashMap<Int, Int>()

        // Create new board list through cloning. Keep the old just in case
        for (i in remapping.indices) {
            if (remapping[i] != -1) {
                val clonedBoard = boards[remapping[i]].clone()
                if (i != remapping[i]) clonedBoard.setDirty()
                newBoardList.add(clonedBoard)
                oldToNew[remapping[i]] = i
            }
        }

        val w = CompatWarning(newWorldData.isSuperZZT)

        // Deleted first board?
        if (remapping[0] != 0) {
            w.warn(1, "This operation will change the title screen.")
        }

        // Update first board / current board
        if (oldToNew.containsKey(newWorldData.currentBoard)) {
            newWorldData.currentBoard = oldToNew[newWorldData.currentBoard]!!
        } else {
            w.warn(1, "Deleting world's first/current board. First/current board will be changed to title screen.")
            newWorldData.currentBoard = 0
        }

        for (boardIdx in newBoardList.indices) {
            val board = newBoardList[boardIdx]
            val oldBoardIdx = remapping[boardIdx]
            w.setPrefix(String.format("Board #%d \"%s\"", oldBoardIdx, CP437.toUnicode(board!!.getName())))

            // Check exits. If we remap an exit to 0 or a nonexistent board we must give a warning
            for (exit in 0..3) {
                val oldDestination = board.getExit(exit)
                if (oldDestination != 0 && oldDestination < boards.size) {
                    if (oldToNew.containsKey(oldDestination)) {
                        val newDestination = oldToNew[oldDestination]!!
                        if (newDestination == 0) {
                            w.warn(
                                1,
                                String.format(
                                    "'s %s exit will lead to the title screen. That exit will be disabled.",
                                    EXIT_NAMES[exit]
                                )
                            )
                        }
                        if (newDestination != oldDestination) {
                            board.setExit(exit, newDestination)
                            board.setDirty()
                        }
                    } else {
                        w.warn(
                            1,
                            String.format(
                                "'s %s exit leads to a board being deleted. That exit will be disabled.",
                                EXIT_NAMES[exit]
                            )
                        )
                        board.setExit(exit, 0)
                        board.setDirty()
                    }
                }
            }

            // Check passages. We can only check passages that currently exist, so hopefully there's no stat muckery
            for (statIdx in 0 until board.statCount) {
                val stat = board.getStat(statIdx)
                val x = stat!!.x - 1
                val y = stat.y - 1
                if (x >= 0 && y >= 0 && x < board.width && y < board.height) {
                    val tid = board.getTileId(x, y)

                    // Passages are the same in ZZT and SuperZZT
                    if (tid == ZType.PASSAGE) {
                        val oldDestination = stat.p3
                        if (oldDestination < boards.size) {
                            if (oldToNew.containsKey(oldDestination)) {
                                val newDestination = oldToNew[oldDestination]!!
                                if (newDestination != oldDestination) {
                                    stat.p3 = newDestination
                                    board.dirtyStats()
                                }
                            } else {
                                w.warn(
                                    1,
                                    String.format(
                                        " has a passage at %d,%d pointing to a board being deleted. It will now point to the title screen.",
                                        x + 1,
                                        y + 1
                                    )
                                )
                                stat.p3 = 0
                                board.dirtyStats()
                            }
                        }
                    }
                }
            }
        }

        if (w.warningLevel == 0) {
            for (i in remapping) {
                if (i == -1) {
                    // For any deletion, warn the user
                    w.setPrefix("")
                    w.warn(1, "Are you sure?")
                    break
                }
            }
        }

        if (w.warningLevel > 0) {
            val result = JOptionPane.showConfirmDialog(
                dialog, w.getMessages(1),
                "Board $desc warning", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE
            )
            if (result != JOptionPane.OK_OPTION) return
        }

        // Now remap all the selected boards
        val selectedRows = ArrayList<Int>()

        for (viewRow in table.selectedRows) {
            val modelRow = table.convertRowIndexToModel(viewRow)
            if (oldToNew.containsKey(modelRow)) {
                val remappedModelRow = oldToNew[modelRow]!!
                selectedRows.add(remappedModelRow)
            }
        }

        // Update data structures here and in WorldEditor
        boards = newBoardList
        worldData = newWorldData
        updateBoardSelectArray()
        editor.worldData = newWorldData
        editor.replaceBoardList(newBoardList)

        val oldCurrentBoardIdx = editor.boardIdx
        var newCurrentBoardIdx = 0
        if (oldToNew.containsKey(oldCurrentBoardIdx)) {
            newCurrentBoardIdx = oldToNew[oldCurrentBoardIdx]!!
        }
        //if (newCurrentBoardIdx != oldCurrentBoardIdx) {
        editor.changeBoard(newCurrentBoardIdx)

        //}
        tableModel!!.fireTableDataChanged()
        table.clearSelection()

        for (remappedModelRow in selectedRows) {
            val remappedViewRow = table.convertRowIndexToView(remappedModelRow)
            table.addRowSelectionInterval(remappedViewRow, remappedViewRow)
        }

        table.repaint()
    }

    private fun moveSelected(offset: Int) {
        table.rowSorter.sortKeys = null
        val remapping = defaultMapping()
        val selected = BooleanArray(remapping.size)
        for (viewRow in table.selectedRows) {
            val modelRow = table.convertRowIndexToModel(viewRow)
            selected[modelRow] = true
        }

        if (offset == 1) {
            // 0 1 2 3 4
            // 0[1 2]3 4
            // 0 3[1 2]4

            //   * *
            // 0 1 2 3 4
            // 0 1 2{3 4} - n
            // 0 1{2 3}4  - y
            // 0 1{3 2}4  - *
            // 0{1 3}2 4  - y
            // 0{3 1}2 4  - *
            //{0 3}1 2 4  - n

            for (idx in remapping.size - 2 downTo 0) {
                if (selected[idx]) {
                    val t = remapping[idx + 1]
                    remapping[idx + 1] = remapping[idx]
                    remapping[idx] = t
                }
            }
        } else if (offset == -1) {
            // 0 1 2 3 4
            // 0[1 2]3 4
            // 0 1[2 3]4
            for (idx in 1 until remapping.size) {
                if (selected[idx]) {
                    val t = remapping[idx - 1]
                    remapping[idx - 1] = remapping[idx]
                    remapping[idx] = t
                }
            }
        }

        doRemap(remapping, "reordering")
    }

    private fun columnToExit(column: Int): Int {
        return when (column) {
            COL_EXITN -> 0
            COL_EXITS -> 1
            COL_EXITE -> 3
            COL_EXITW -> 2
            else -> -1
        }
    }

    companion object {
        private const val COL_NUM = 0
        private const val COL_NAME = 1
        private const val COL_SHOTS = 2
        private const val COL_TIMELIMIT = 3
        private const val COL_PLAYERX = 4
        private const val COL_PLAYERY = 5
        private const val COL_EXITN = 6
        private const val COL_EXITS = 7
        private const val COL_EXITE = 8
        private const val COL_EXITW = 9

        @JvmField
        val EXIT_NAMES: Array<String> = arrayOf("north", "south", "west", "east")

        private fun getBoardIdx(boardNameOrId: Any): Int {
            var boardIdx = -1
            if (boardNameOrId is String) {

                if (boardNameOrId != "(no board)") {
                    var dot = boardNameOrId.indexOf('.')
                    if (dot == -1) dot = boardNameOrId.length
                    try {
                        boardIdx = boardNameOrId.substring(0, dot).toInt()
                    } catch (ignored: NumberFormatException) {
                    }
                } else {
                    boardIdx = 0
                }
            } else {
                boardIdx = boardNameOrId as Int
            }
            return boardIdx
        }

        fun generateBoardSelectArray(boards: List<Board>, numPrefix: Boolean): Array<String?> {
            val boardList = arrayOfNulls<String>(boards.size)
            boardList[0] = "(no board)"
            for (i in 1 until boardList.size) {
                val name = CP437.toUnicode(boards[i].getName())
                boardList[i] = if (numPrefix) String.format("%d. %s", i, name) else name
            }
            return boardList
        }
    }
}
