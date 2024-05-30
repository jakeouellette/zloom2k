package zedit2.components

import zedit2.event.KeyActionReceiver
import java.awt.*
import java.awt.event.*
import javax.swing.JDialog
import javax.swing.JPanel

class ColourSelector(
    ge: GlobalEditor,
    col: Int,
    outerDialog: JDialog,
    var listener: ActionListener,
    var canvas: DosCanvas,
    selectorMode: Int
) : JPanel(), KeyActionReceiver, MouseListener {
    var colourPalette: Image
    var zoomx: Int = 2
    var zoomy: Int = 2
    var borderx: Int = 8
    var bordery: Int = 8
    // TODO(jakeouellette): These shouldn't be necessary,
    // they override internal params.
    var oX: Int
    var oY: Int
    var oWidth: Int = 0
    var oHeight: Int = 0
    var dialog: JDialog
    var selectorMode: Int
    var wrap: Boolean = false

    init {
        this.addMouseListener(this)
        this.selectorMode = selectorMode
        if (selectorMode == CHAR) wrap = true
        dialog = outerDialog

        when (this.selectorMode) {
            COLOUR -> {
                oWidth = 16
                oHeight = 16
            }

            CHAR -> {
                oWidth = 32
                oHeight = 8
            }

            else -> throw RuntimeException("Invalid colour selector mode")
        }
        oX = col % oWidth
        oY = col / oWidth
        colourPalette = canvas.extractCharImageWH(0, 0, zoomx, zoomy, false, colourPattern, oWidth, oHeight)
        Util.addKeybind(ge, this, this, "Up")
        Util.addKeybind(ge, this, this, "Down")
        Util.addKeybind(ge, this, this, "Left")
        Util.addKeybind(ge, this, this, "Right")
        Util.addKeybind(ge, this, this, "Home")
        Util.addKeybind(ge, this, this, "End")

        Util.addKeybind(ge, this, this, "Enter")
        Util.addKeybind(ge, this, this, "Enter")
        Util.addKeybind(ge, this, this, "Escape")

        if (selectorMode == CHAR) {
            dialog.addKeyListener(object : KeyAdapter() {
                override fun keyTyped(e: KeyEvent) {
                    val c = e.keyChar.code
                    if (c >= 32 && c <= 127) {
                        oX = c % oWidth
                        oY = c / oWidth
                        upd()
                    }
                }
            })
        }

        upd()
    }

    override fun keyAction(actionName: String?, e: ActionEvent?) {
        when (actionName) {
            "Up" -> operationCursorMove(0, -1)
            "Down" -> operationCursorMove(0, 1)
            "Left" -> operationCursorMove(-1, 0)
            "Right" -> operationCursorMove(1, 0)
            "Home" -> operationCursorMove(-999, -999)
            "End" -> operationCursorMove(999, 999)
            "Enter", "Space" -> operationSubmit()
            "Escape" -> operationExit()
        }
    }

    private fun operationCursorMove(xOff: Int, yOff: Int) {
        val xMin = 0
        if (oX == 0 && xOff == -1 && wrap) {
            oX = oWidth - 1
            oY = (oY + oHeight - 1) % oHeight
        } else if (oX == oWidth - 1 && xOff == 1 && wrap) {
            oX = 0
            oY = (oY + 1) % oHeight
        } else {
            oX = Util.clamp(oX + xOff, xMin, oWidth - 1)
            oY = Util.clamp(oY + yOff, 0, oHeight - 1)
        }
        upd()
    }

    private fun operationSubmit() {
        listener.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, col.toString()))
        operationExit()
    }

    private fun operationExit() {
        dialog.dispose()
    }

    private fun upd() {
        dialog.title = TITLES[selectorMode] + ": " + col
        if (selectorMode != CHAR) {
            dialog.setIconImage(canvas.extractCharImage(254, col, 2, 2, false, "$"))
        } else {
            dialog.setIconImage(canvas.extractCharImage(col, 0x1b, 2, 2, false, "$"))
        }
        repaint()
    }

    private val col: Int
        get() = oY * oWidth + oX

    override fun getPreferredSize(): Dimension {
        return Dimension(
            DosCanvas.CHAR_W * zoomx * oWidth + borderx * 2,
            DosCanvas.CHAR_H * zoomy * oHeight + bordery * 2
        )
    }

    public override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.color = Color(0x7F7F7F)
        g.fillRect(0, 0, getWidth(), getHeight())
        g.drawImage(colourPalette, borderx, bordery, null)


        val cursorX = oX * DosCanvas.CHAR_W * zoomx + 8
        val cursorY = oY * DosCanvas.CHAR_H * zoomy + 8
        val cursorW = DosCanvas.CHAR_W * zoomx
        val cursorH = DosCanvas.CHAR_H * zoomy
        g.color = Color.BLUE
        g.fillRect(cursorX - 6, cursorY - 6, cursorW + 12, cursorH + 12)
        g.color = Color.WHITE
        g.fillRect(cursorX - 4, cursorY - 4, cursorW + 8, cursorH + 8)
        g.color = Color.BLUE
        g.fillRect(cursorX - 2, cursorY - 2, cursorW + 4, cursorH + 4)

        g.drawImage(
            colourPalette, cursorX, cursorY, cursorX + cursorW, cursorY + cursorH,
            cursorX - 8, cursorY - 8, cursorX + cursorW - 8, cursorY + cursorH - 8, null
        )
    }

    private val colourPattern: String
        get() {
            val sb = StringBuilder(256)
            for (col in 0..255) {
                val c = col
                if (selectorMode != CHAR) {
                    sb.append(((254 shl 8) or c).toChar())
                } else {
                    sb.append(((c shl 8) or 0x8F).toChar())
                }
            }
            return sb.toString()
        }

    override fun mouseClicked(e: MouseEvent) {
        if (mouseSelectColour(e)) operationSubmit()
    }

    private fun mouseSelectColour(e: MouseEvent): Boolean {
        var mouseX = e.x
        var mouseY = e.y
        if (mouseX < 8 || mouseY < 8) return false
        mouseX = (mouseX - 8) / zoomx / DosCanvas.CHAR_W
        mouseY = (mouseY - 8) / zoomy / DosCanvas.CHAR_H
        if (mouseX >= oWidth || mouseY >= oHeight) return false
        oX = mouseX
        oY = mouseY
        upd()
        return true
    }

    override fun mousePressed(e: MouseEvent) {
        mouseSelectColour(e)
    }

    override fun mouseReleased(e: MouseEvent) {}

    override fun mouseEntered(e: MouseEvent) {}

    override fun mouseExited(e: MouseEvent) {}

    companion object {
        const val COLOUR: Int = 0
        const val CHAR: Int = 2
        private val TITLES = arrayOf("Select a colour", "Select a text colour", "Select a character")

        @JvmStatic
        fun createColourSelector(
            editor: WorldEditor,
            col: Int,
            relativeTo: Component?,
            listener: ActionListener,
            selectorMode: Int
        ) {
            createColourSelector(editor, col, relativeTo, null, listener, selectorMode)
        }

        @JvmStatic
        fun createColourSelector(
            editor: WorldEditor,
            col: Int,
            relativeTo: Component?,
            owner: Any?,
            listener: ActionListener,
            selectorMode: Int
        ) {
            val colourSelectorDialog = if (owner != null) {
                if (owner is Dialog) JDialog(owner as Dialog?)
                else if (owner is Frame) JDialog(owner as Frame?)
                else if (owner is Window) JDialog(owner as Window?)
                else throw RuntimeException("invalid owner")
            } else {
                JDialog()
            }
            colourSelectorDialog.modalityType = Dialog.ModalityType.APPLICATION_MODAL
            colourSelectorDialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            colourSelectorDialog.isResizable = false
            val ge = editor.globalEditor
            val cs = ColourSelector(ge, col, colourSelectorDialog, listener, editor.canvas, selectorMode)
            colourSelectorDialog.contentPane.add(cs)
            colourSelectorDialog.pack()
            colourSelectorDialog.setLocationRelativeTo(relativeTo)
            colourSelectorDialog.isVisible = true
        }
    }
}
