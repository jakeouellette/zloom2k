package zedit2.components

import zedit2.model.Board
import zedit2.util.CP437
import java.awt.Color
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent

class BoardSelector(
    private val editor: WorldEditor,
    private val boards: ArrayList<Board>,
    private val listener: ActionListener
) : JList<String?>() {

    init {
        val boardNames = mutableListOf<String>()
        if (boards.size > 0) {
            for (i in 0 until boards.size) {
                boardNames.add(CP437.toUnicode(boards[i].getName()))
                if (boardNames[i].length < 20) boardNames[i] = (boardNames[i] + "                    ").substring(0, 20)
            }
        }
        boardNames.add("(add board)")
        this.setListData(boardNames.toTypedArray())

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

        this.ensureIndexIsVisible(editor.boardIdx)

    }


    fun selectBoard() {
        val boardIdx = this.selectedIndex
        // TODO(jakeouellette): Make dialog close again after selecting item.
//        dialog.dispose()
        if (boardIdx == boards.size) {
            editor.operationAddBoard()
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
                this.setIconImage(editor.canvas.extractCharImage(240, 0x0F, 2, 2, false, "$"))
                this.pack()
                this.setLocationRelativeTo(editor.frameForRelativePositioning)
                this.isVisible = true
                this.contentPane.add(boardList)
            }
        }



}
