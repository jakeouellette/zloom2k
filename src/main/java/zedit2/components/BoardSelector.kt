package zedit2.components

import zedit2.model.Board
import zedit2.util.CP437
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.Component
import java.awt.event.*
import javax.swing.*
import javax.swing.event.ListSelectionEvent


class BoardSelector(
    private val imageRetriever: ImageRetriever,
    currentBoard : Int,
    private val frameForPositioning: Component,
    private val onBoardAddRequested: () -> Unit,
    private val onBoardFocusRequested: () -> Unit,
    boards: ArrayList<Board>,
    private val listener: ActionListener,
    private val onBoardOrderSwapRequested: (Int, Int) -> Unit,
) : JList<String>() {

    init {
        Logger.i(TAG) { "Making new board selector..." }

//        this.model = listModel

        this.selectedIndex = currentBoard
        this.font = CP437.font
        this.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        this.border = null
        val boardSelector = this
        this.updateBoards(boards, currentBoard)
        this.transferHandler = ListTransferHandler(onBoardOrderSwapRequested)
        this.dragEnabled = true
        this.dropMode = DropMode.INSERT
//        val boardDragAndDrop = BoardDragAndDrop(this, onBoardOrderSwapRequested)
//
//        this.addMouseListener(boardDragAndDrop)
//        this.addMouseMotionListener(boardDragAndDrop)
        this.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                Logger.i(TAG) {"Key Processed"}
                // TODO(jakeouellette): Fix the fact that this doesn't use keymappings.
                if (e.keyCode == KeyEvent.VK_B) {
                    onBoardFocusRequested()
                }
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    this@BoardSelector.selectBoard()
                    e.consume()
                }
            }
        })
        this.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    this@BoardSelector.selectBoard()
                    e.consume()
                }
            }
        })
        this.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                Logger.i(this@BoardSelector.TAG) {"Focus gained, $e"}
            }

            override fun focusLost(e: FocusEvent?) {
                Logger.i(this@BoardSelector.TAG) {"Focus lost, $e"}
            }
        })
        this.isFocusable = true

        this.addListSelectionListener { e: ListSelectionEvent? ->
            val boardIdx = this.selectedIndex
            // -1 for "add board" item
            if (boardIdx < model.size -1 ) {
                listener.actionPerformed(ActionEvent(boardSelector, ActionEvent.ACTION_PERFORMED, boardIdx.toString()))
            }
        }


        this.ensureIndexIsVisible(currentBoard)

    }

    private fun getBoardsList(boards : List<Board>) : List<String> {
        val boardNames = mutableListOf<String>()
        if (boards.isNotEmpty()) {
            for (i in boards.indices) {
                boardNames.add(CP437.toUnicode(boards[i].getName()))
                if (boardNames[i].length < 20) boardNames[i] = (boardNames[i] + "                    ").substring(0, 20)
            }
        }
        boardNames.add("(add board)")
        return boardNames
    }

    fun updateBoards(boards: ArrayList<Board>, selectedBoard : Int) {
        Logger.i(TAG) { "Updating to ${boards.size} boards, and selecting $selectedBoard"}
        if (selectedBoard > boards.size-1) {
            throw IndexOutOfBoundsException("Board selection out of bounds.")
        }
        this.setListData(getBoardsList(boards).toTypedArray())

        this.selectedIndex = selectedBoard


    }

    fun selectBoard() {
        val boardIdx = this.selectedIndex
        Logger.i(TAG) { "Selected board $boardIdx of ${this.model.size - 1}"}
        // TODO(jakeouellette): Make dialog close again after selecting item.
//        dialog.dispose()
        if (boardIdx == this.model.size - 1) {
            onBoardAddRequested()
        } else {
            listener.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, boardIdx.toString()))
        }
    }

    private fun createPopout(boardList: JComponent) =
        object : JDialog() {
            init {
                Util.addEscClose(this, this.rootPane)
                this.modalityType = ModalityType.APPLICATION_MODAL
                this.defaultCloseOperation = DISPOSE_ON_CLOSE
                this.title = "Select a board"
                this.setIconImage(imageRetriever.extractCharImage(240, 0x0F, 2, 2, false, "$"))
                this.pack()
                this.setLocationRelativeTo(frameForPositioning)
                this.isVisible = true
                this.contentPane.add(boardList)
            }
        }


}
