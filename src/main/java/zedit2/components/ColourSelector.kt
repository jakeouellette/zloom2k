package zedit2.components

import zedit2.event.KeyActionReceiver
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import zedit2.util.Logger
import zedit2.util.Logger.TAG
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
    var borderPos : Pos = Pos(8,8)
    // TODO(jakeouellette): These shouldn't be necessary,
    // they override internal params.
    var oPos : Pos
    var oDim : Dim
    var dialog: JDialog
    var selectorMode: Int
    var wrap: Boolean = false

    init {
        this.addMouseListener(this)
        this.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                Logger.i(this@ColourSelector.TAG) {"Focus Gained"}
            }

            override fun focusLost(e: FocusEvent?) {
                Logger.i(this@ColourSelector.TAG) {"Focus Lost"}
            }

        })
        this.selectorMode = selectorMode
        if (selectorMode == CHAR) wrap = true
        dialog = outerDialog

        when (this.selectorMode) {
            COLOUR -> {
                oDim = Dim(16, 16)
            }

            CHAR -> {
                oDim = Dim(32, 8)
            }

            else -> throw RuntimeException("Invalid colour selector mode")
        }
        oPos = oDim.fromArray(col)
        colourPalette = canvas.extractCharImageWH(0, 0, zoomx, zoomy, false, colourPattern, oDim)
        listOf("Up","Down","Left","Right","Home","End","Enter","Enter","Escape").forEach {
            Util.addKeybind(ge, this@ColourSelector, this@ColourSelector, it)
        }

        if (selectorMode == CHAR) {
            dialog.addKeyListener(object : KeyAdapter() {
                override fun keyTyped(e: KeyEvent) {
                    Logger.i(this@ColourSelector.TAG) { "Captured Key Event. $e"}
                    val c = e.keyChar.code
                    if (c >= 32 && c <= 127) {
                        oPos = oDim.fromArray(c)
                        upd()
                    }
                }
            })
        }

        upd()
    }

    override fun keyAction(actionName: String?, e: ActionEvent?) {
        Logger.i(TAG) { "Key Action Pressed $actionName, $e"}
        when (actionName) {
            "Up" -> operationCaretMove(Pos.UP)
            "Down" -> operationCaretMove(Pos.DOWN)
            "Left" -> operationCaretMove(Pos.LEFT)
            "Right" -> operationCaretMove(Pos.RIGHT)
            "Home" -> operationCaretMove(Pos(-999,-999))
            "End" -> operationCaretMove(Pos(999,999))
            "Enter", "Space" -> operationSubmit()
            "Escape" -> operationExit()
        }
    }

    private fun operationCaretMove(pos : Pos) {
        if (oPos.x == 0 && pos.x == -1 && wrap) {
            oPos = Pos(oDim.w - 1, (oPos.y + oDim.h - 1) % oDim.h)
        } else if (oPos.x == oDim.w - 1 && pos.x == 1 && wrap) {
            oPos = Pos(0, (oPos.y + 1) % oDim.h)
        } else {
            oPos = (oPos + pos).clamp(0, oDim.asPos - 1)
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
        get() = oPos.arrayIdx(oDim.w)

    override fun getPreferredSize(): Dimension {
        return Dimension(
            DosCanvas.CHAR_W * zoomx * oDim.w + borderPos.x * 2,
            DosCanvas.CHAR_H * zoomy * oDim.h + borderPos.y * 2
        )
    }

    public override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.color = Color(0x7F7F7F)
        g.fillRect(0, 0, width, height)
        g.drawImage(colourPalette, borderPos.x, borderPos.y, null)


        val cursorX = oPos.x * DosCanvas.CHAR_W * zoomx + 8
        val cursorY = oPos.y * DosCanvas.CHAR_H * zoomy + 8
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
        if (mouseX >= oDim.w || mouseY >= oDim.h) return false
        oPos = Pos(mouseX, mouseY)
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
            createColourSelector(editor, col, relativeTo, relativeTo, listener, selectorMode)
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
            val colourSelectorDialog =  if (owner != null) {
                if (owner is Dialog) PopoverDialog(owner as Dialog?)
                else if (owner is Frame) PopoverDialog(owner as Frame?)
                else if (owner is Window) PopoverDialog(owner as Window?)
                else throw RuntimeException("invalid owner")
            } else {
                PopoverDialog()
            }
            colourSelectorDialog.modalityType = Dialog.ModalityType.MODELESS
            colourSelectorDialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            colourSelectorDialog.isResizable = false
            colourSelectorDialog.isUndecorated = true
            colourSelectorDialog.isFocusable = true
            val ge = editor.globalEditor
            val cs = ColourSelector(ge, col, colourSelectorDialog, listener, editor.canvas, selectorMode)
            Logger.i(TAG) { "Requesting focus for color selector" }
            // FIXME(jakeouellette): The color selector is busted.
            cs.isFocusable = true

            colourSelectorDialog.contentPane.add(cs)
            colourSelectorDialog.pack()
            colourSelectorDialog.setLocationRelativeTo(relativeTo)
            colourSelectorDialog.isVisible = true
            cs.requestFocusInWindow()
        }
    }
}
