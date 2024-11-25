package zedit2.components

import zedit2.event.OnBoardUpdatedCallback
import zedit2.model.Board
import zedit2.model.CompatWarning
import zedit2.model.WorldData
import zedit2.model.spatial.Pos
import zedit2.util.CP437
import zedit2.util.ZType
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Frame
import java.awt.GridLayout
import java.awt.event.ActionListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import java.io.File
import java.io.IOException
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class BoardManager @JvmOverloads constructor(
    private val imageRetriever: ImageRetriever,
    private val onWindowClosed: WindowListener,
    private val frameForRelativePositioning: Component,
    private val onBoardUpdatedCallback: OnBoardUpdatedCallback,
    private var worldData :WorldData,
    private var boards: List<Board>,
    private var currentIndex : Int,
    deleteBoard : Int? = null,
    private val modal: Boolean = true
) {
    private var dialog: JDialog? = null
    private lateinit var table: JTable
    private lateinit var tableModel: AbstractTableModel
    private val szzt: Boolean
    private lateinit var boardSelectArray: Array<String?>
    private lateinit var upButton: JButton
    private lateinit var downButton: JButton
    private lateinit var delButton: JButton

    init {
        // TODO(jakeouellette): Handle more gracefully this null check
        szzt = worldData.isSuperZZT

        updateBoardSelectArray()
        generateTable()
            if(deleteBoard != null) {
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
                dialog?.dispose()
            }
    }

    private fun generateTable() {
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
                    COL_NUM, COL_SHOTS, COL_TIMELIMIT, COL_PLAYERX, COL_PLAYERY, COL_EXITN, COL_EXITS, COL_EXITE, COL_EXITW -> return java.lang.Integer::class.java
                    COL_NAME -> return java.lang.String::class.java
                    else -> {
                        if (columnIndex == colDark) return java.lang.Boolean::class.java
                        if (columnIndex == colCameraX) return java.lang.Integer::class.java
                        if (columnIndex == colCameraY) return java.lang.Integer::class.java
                        if (columnIndex == colRestart) return java.lang.Boolean::class.java
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
                    COL_NAME -> return CP437.toUnicode(board.getName())
                    COL_SHOTS -> return board.getShots()
                    COL_TIMELIMIT -> return board.getTimeLimit()
                    COL_PLAYERX -> return board.getPlayerX()
                    COL_PLAYERY -> return board.getPlayerY()
                    COL_EXITN, COL_EXITS, COL_EXITE, COL_EXITW -> return board.getExit(columnToExit(columnIndex))
                    else -> {
                        if (columnIndex == colDark) return board.isDark
                        if (columnIndex == colCameraX) return board.cameraPos.x
                        if (columnIndex == colCameraY) return board.cameraPos.y
                        if (columnIndex == colRestart) return board.isRestartOnZap()
                        // TODO(jakeouellette): this might not be okay. (Was previously returning a null)
                        throw RuntimeException("Unexpected column, did not map to any value.")
                    }
                }
            }

            override fun setValueAt(value: Any, rowIndex: Int, columnIndex: Int) {
                val board = boards[rowIndex]
                when (columnIndex) {
                    COL_NAME -> {
                        board.setName(CP437.toBytes(value as String))
                        return
                    }

                    COL_SHOTS -> {
                        board.setShots((value as Int))
                        return
                    }

                    COL_TIMELIMIT -> {
                        board.setTimeLimit((value as Int))
                        return
                    }

                    COL_PLAYERX -> {
                        board.setPlayerX((value as Int))
                        return
                    }

                    COL_PLAYERY -> {
                        board.setPlayerY((value as Int))
                        return
                    }

                    COL_EXITN, COL_EXITS, COL_EXITE, COL_EXITW -> {
                        val boardIdx = getBoardIdx(value)
                        if (boardIdx in 0..255) {
                            board.setExit(columnToExit(columnIndex), boardIdx)
                        }
                        return
                    }

                    else -> {
                        if (columnIndex == colDark) board.isDark = (value as Boolean)
                        if (columnIndex == colCameraX) board.cameraPos = Pos((value as Int), board.cameraPos.y)
                        if (columnIndex == colCameraY) board.cameraPos = Pos(board.cameraPos.x, (value as Int))
                        if (columnIndex == colRestart) board.setRestartOnZap((value as Boolean))
                    }
                }
            }
        }
        table = object : JTable(tableModel) {
            init {
                this.setAutoCreateRowSorter(true)
                this.setAutoResizeMode(AUTO_RESIZE_OFF)
                this.getSelectionModel().addListSelectionListener { e: ListSelectionEvent? -> updateButtons() }
            }

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

        val scroll = JScrollPane(table)

        val note = " (note: This will change board exits and passages.)"

        upButton = panelButton(name = "↑", toolTipText = "Move selected boards up$note") {
            moveSelected(-1)
        }
        downButton = panelButton(name = "↓", toolTipText = "Move selected boards down$note") {
            moveSelected(1)
        }
        delButton = panelButton(name = "×", toolTipText = "Delete selected boards$note") {
            delSelected()
        }
        val exportButton = panelButton(name = "Export", toolTipText = "Export selected boards") {
            exportSelected()
        }
        updateButtons()

        val buttonPanel = object : JPanel(GridLayout(0, 1)) {
            init {
                this.add(upButton)
                this.add(downButton)
                this.add(delButton)
                this.add(exportButton)
            }
        }
        val dialog = object : JDialog() {
            init {
                if (modal) this.modalityType = ModalityType.APPLICATION_MODAL
                Util.addEscClose(this, this.rootPane)
                this.defaultCloseOperation = DISPOSE_ON_CLOSE
                this.title = "Board list"

                this.setIconImage(imageRetriever.extractCharImage(240, 0x1F, 2, 2, false, "$"))
                this.addWindowListener(onWindowClosed)
                this.contentPane.layout = BorderLayout()
                this.add(scroll, BorderLayout.CENTER)
                this.add(buttonPanel, BorderLayout.EAST)
                this.pack()
                this.setLocationRelativeTo(frameForRelativePositioning)
                this.isVisible = true
            }
        }
        this.dialog = dialog
    }

    fun panelButton(name: String, toolTipText: String, action: ActionListener) = object : JButton(name) {
        init {
            this.addActionListener(action)
            this.toolTipText = toolTipText
        }
    }

    private fun exportSelected() {
        if (table.selectedRows.isEmpty()) return
        object : JFileChooser() {
            init {
                this.currentDirectory = GlobalEditor.defaultDirectory
                this.fileSelectionMode = DIRECTORIES_ONLY
                this.isAcceptAllFileFilterUsed = false
                if (this.showOpenDialog(dialog) == APPROVE_OPTION) {
                    val targetDir = this.selectedFile
                    // FIXME(jakeouellette): I made this not be the local copy,
                    // We should make this whole function be passed in instead
                    val boards = boards
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
        }
    }

    private fun updateBoardSelectArray() {
        boardSelectArray = generateBoardSelectArray(boards, true)
    }

    private fun updateButtons() {
        upButton.isEnabled = true
        downButton.isEnabled = true
        delButton.isEnabled = true
        val rows = table.selectedRows

        // Can't do anything if no rows are selected, or if all the rows are selected
        if (rows.isEmpty() || rows.size == boards.size) {
            upButton.isEnabled = false
            downButton.isEnabled = false
            delButton.isEnabled = false
            return
        }

        // Can't move up if the top row is selected or down if the bottom row is selected
        for (viewRow in rows) {
            val modelRow = table.convertRowIndexToModel(viewRow)
            if (modelRow == 0) upButton.isEnabled = false
            if (modelRow == boards.size - 1) downButton.isEnabled = false
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
            w.setPrefix(String.format("Board #%d \"%s\"", oldBoardIdx, CP437.toUnicode(board.getName())))

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
                val pos = stat!!.pos - 1
                if (pos.inside(board.dim)) {
                    val tid = board.getTileId(pos)

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
                                        " has a passage at %s pointing to a board being deleted. It will now point to the title screen.",
                                        pos + 1
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

        val oldCurrentBoardIdx = currentIndex
        var newCurrentBoardIdx = 0
        if (oldToNew.containsKey(oldCurrentBoardIdx)) {
            newCurrentBoardIdx = oldToNew[oldCurrentBoardIdx]!!
        }

        // Update data structures here and in WorldEditor
        boards = newBoardList
        worldData = newWorldData
        updateBoardSelectArray()
        currentIndex = newCurrentBoardIdx
        //if (newCurrentBoardIdx != oldCurrentBoardIdx) {
        onBoardUpdatedCallback.onBoardUpdated(newWorldData, newBoardList, newCurrentBoardIdx)

        //}
        tableModel.fireTableDataChanged()
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
