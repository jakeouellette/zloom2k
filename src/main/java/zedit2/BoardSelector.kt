package zedit2

import java.awt.Color
import java.awt.Dialog
import java.awt.event.*
import javax.swing.JDialog
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent

class BoardSelector(
    private val editor: WorldEditor,
    private val boards: ArrayList<Board>,
    private val listener: ActionListener
) {
    private var dialog : JDialog = object : JDialog() {
        init {
            Util.addEscClose(this, this.rootPane)
            this.modalityType = ModalityType.APPLICATION_MODAL
            this.defaultCloseOperation = DISPOSE_ON_CLOSE
            this.title = "Select a board"
            this.setIconImage(editor.canvas.extractCharImage(240, 0x0F, 2, 2, false, "$"))
        }
    }
    private lateinit var boardList: JList<String?>

    init {
        val boardNames = mutableListOf<String>()
        for (i in 0..boards.size) {
            boardNames.add(CP437.toUnicode(boards[i].getName()))
            if (boardNames[i].length < 20) boardNames[i] = (boardNames[i] + "                    ").substring(0, 20)
        }
        boardNames[boards.size] = "(add board)"

        boardList = object : JList<String?>(boardNames.toTypedArray()) {
            init {
                this.selectedIndex = editor.boardIdx
                this.background = Color(0x0000AA)
                this.foreground = Color(0xFFFFFF)
                this.font = CP437.font
                this.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

                val boardSelector = this
                this.addKeyListener(object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
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

                this.addListSelectionListener { e: ListSelectionEvent? ->
                    val boardIdx = this.selectedIndex
                    if (boardIdx < boards.size) {
                        listener.actionPerformed(ActionEvent(boardSelector, ActionEvent.ACTION_PERFORMED, boardIdx.toString()))
                    }
                }
                object : JScrollPane(boardList) {
                    init {
                        dialog.contentPane.add(this)
                    }
                }

                dialog.pack()
                boardList.ensureIndexIsVisible(editor.boardIdx)
                dialog.setLocationRelativeTo(editor.frameForRelativePositioning)
                dialog.isVisible = true
            }
        }
    }

    protected fun selectBoard() {
        val boardIdx = boardList.selectedIndex
        dialog.dispose()
        if (boardIdx == boards.size) {
            editor.operationAddBoard()
        } else {
            listener.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, boardIdx.toString()))
        }
    }
}
