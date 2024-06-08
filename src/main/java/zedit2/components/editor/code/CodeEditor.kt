package zedit2.components.editor.code

import zedit2.*
import zedit2.components.*
import zedit2.components.Menu
import zedit2.model.MusicLine
import zedit2.model.Stat
import zedit2.model.Undo
import zedit2.util.*
import zedit2.util.Audio.Companion.playSequence
import zedit2.util.Logger.TAG
import java.awt.*
import java.awt.event.*
import java.util.*
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultEditorKit
import javax.swing.text.JTextComponent
import kotlin.math.max

class CodeEditor(
    private val icon: Image,
    private val stat: Stat,
    private val worldEditor: WorldEditor,
    private val listener: ActionListener?,
    private val readOnly: Boolean,
    private val title: String
) : KeyListener, MouseMotionListener {
    private var form: JDialog
    private var editor: JTextComponent
    private var cd: CodeDocument
    private var lastText: String

    private var k_AltT: KeyStroke
    private var k_Escape: KeyStroke
    private var k_CtrlD: KeyStroke
    private var k_CtrlF: KeyStroke
    private var k_CtrlH: KeyStroke
    private var k_CtrlZ: KeyStroke
    private var k_CtrlY: KeyStroke
    private var k_F3: KeyStroke
    private var k_transposeDown: KeyStroke
    private var k_transposeUp: KeyStroke
    private var findDialog: JDialog? = null
    private var selFromRep = false
    private var recordUndo = false

    private var currentUndo = ArrayList<Undo>()
    private var undoList = ArrayList<ArrayList<Undo>>()
    private var undoPos = 0

    private var musicMode = false

    override fun mouseDragged(e: MouseEvent) {}

    override fun mouseMoved(e: MouseEvent) {
        val pt = e.point
        val pos = editor.viewToModel2D(pt)
        val warning = cd.getWarning(pos)
        editor.toolTipText = warning
    }

    private object Playing {
        var lineStarts: List<Int> = listOf()
        var audio: Audio? = null
    }

    init {
        k_AltT = Util.getKeyStroke(worldEditor.globalEditor, "Alt-T")
        k_CtrlD = Util.getKeyStroke(worldEditor.globalEditor, "Ctrl-D")
        k_CtrlF = Util.getKeyStroke(worldEditor.globalEditor, "Ctrl-F")
        k_CtrlH = Util.getKeyStroke(worldEditor.globalEditor, "Ctrl-H")
        k_CtrlY = Util.getKeyStroke(worldEditor.globalEditor, "Ctrl-Y")
        k_CtrlZ = Util.getKeyStroke(worldEditor.globalEditor, "Ctrl-Z")
        k_transposeDown = Util.getKeyStroke(worldEditor.globalEditor, "Ctrl--")
        k_transposeUp = Util.getKeyStroke(worldEditor.globalEditor, "Ctrl-=")
        k_Escape = Util.getKeyStroke(worldEditor.globalEditor, "Escape")
        k_F3 = Util.getKeyStroke(worldEditor.globalEditor, "F3")

        form = object : JDialog() {
            init {
                this.modalityType = ModalityType.APPLICATION_MODAL
                this.defaultCloseOperation = DISPOSE_ON_CLOSE
                this.setIconImage(icon)
                val codeEditor = this@CodeEditor
                this.addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent) {
                        if (listener != null) {
                            stopAudio()
                            val act =
                                ActionEvent(
                                    codeEditor,
                                    ActionEvent.ACTION_PERFORMED,
                                    if (readOnly) "readOnly" else "update"
                                )
                            listener.actionPerformed(act)
                        }
                        val currentDialog = findDialog
                        if (currentDialog != null) {
                            findDialog = null
                            currentDialog.dispose()
                        }
                    }
                })
            }
        }

        cd = CodeDocument(worldEditor)
        cd.putProperty(DefaultEditorKit.EndOfLineStringProperty, "\n")
        editor = object : JTextPane(cd) {
            // https://stackoverflow.com/a/23149584
            // Override getScrollableTracksViewportWidth
            // to preserve the full width of the text
            override fun getScrollableTracksViewportWidth(): Boolean {
                val parent: Component? = parent
                val ui = getUI()

                return parent == null || (ui.getPreferredSize(this).getWidth() <= parent.size.getWidth())
            }

            // http://www.jguru.com/faq/view.jsp?EID=253404
            override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
                val size = this.preferredSize
                super.setBounds(x, y, max(size.width.toDouble(), width.toDouble()).toInt(), height)
            }

            init {
                this.setSelectionColor(propCol("EDITOR_SELECTION_COL", "FF0000"))
                this.setSelectedTextColor(propCol("EDITOR_SELECTED_TEXT_COL", "000000"))
                this.setCaretColor(propCol("EDITOR_CARET_COL", "00FF00"))
                this.addMouseMotionListener(this@CodeEditor)
                this.addKeyListener(this@CodeEditor)
                val initialText = CP437.toUnicode(stat.code, false)
                this.text = initialText
                lastText = initialText


                cd.reHighlight()
                this.setCaretPosition(0)
                recordUndo = true
                this.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent) {
                        if (recordUndo) addUndo(true, e.length, e.offset)
                        upd(true, e.length, e.offset)
                    }

                    override fun removeUpdate(e: DocumentEvent) {
                        if (recordUndo) addUndo(false, e.length, e.offset)
                        upd(false, e.length, e.offset)
                    }

                    override fun changedUpdate(e: DocumentEvent) {
                    }

                    private fun upd(insert: Boolean, len: Int, offset: Int) {
                        SwingUtilities.invokeLater {
                            if (recordUndo) finishUndo()
                            this@CodeEditor.fullUpdate(insert, len, offset)
                        }
                    }
                })
                this.addCaretListener { upd() }
                if (readOnly) {
                    this.isEditable = false
                    this.caret.isVisible = true
                    this.caret.isSelectionVisible = true
                    this.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))
                }
                this.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4))

            }
        }


        val scroll = JScrollPane(editor)
        val w = GlobalEditor.getInt("CODEEDITOR_WIDTH")
        val h = GlobalEditor.getInt("CODEEDITOR_HEIGHT")

        createMenu()
        upd()
        scroll.preferredSize = Dimension(w, h)
        form.contentPane.add(scroll)
        form.pack()

        form.setLocationRelativeTo(worldEditor.frameForRelativePositioning)
        form.isVisible = true
    }

    private fun addUndo(insert: Boolean, length: Int, offset: Int) {
        val text = editor.text
        val caret = editor.caretPosition
        if (insert) {
            var added = false
            val addedPortion = text.substring(offset, offset + length)
            if (!currentUndo.isEmpty()) {
                val currentUndoLast = currentUndo[currentUndo.size - 1]
                if (currentUndoLast.insert && currentUndoLast.pos + currentUndoLast.text.length == offset) {
                    currentUndoLast.text += addedPortion
                    added = true
                }
            }
            //System.out.printf("Added [%s] at %d\n", addedPortion, offset);
            if (!added) currentUndo.add(Undo(true, offset, addedPortion, caret))
        } else {
            val deletedPortion = lastText.substring(offset, offset + length)
            var added = false
            if (!currentUndo.isEmpty()) {
                val currentUndoLast = currentUndo[currentUndo.size - 1]
                if (!currentUndoLast.insert && offset + length == currentUndoLast.pos) {
                    currentUndoLast.text = deletedPortion + currentUndoLast.text
                    currentUndoLast.pos = offset
                    added = true
                }
            }
            //System.out.printf("Removed [%s] at %d\n", deletedPortion, offset);
            if (!added) currentUndo.add(Undo(false, offset, deletedPortion, caret))
        }
        lastText = text
        //System.out.printf("addUndo(%s, %d, %d) - len=%d hash=%d\n", insert, length, offset, editor.getText().length(), editor.getText().hashCode());
    }

    private fun finishUndo() {
        if (!currentUndo.isEmpty()) {
            var added = false
            // Can we glue this change onto the last?
            if (undoPos > 0 && currentUndo.size == 1) {
                val undos = undoList[undoPos - 1]
                val undo = undos[undos.size - 1]
                val current = currentUndo[0]
                if (undo.insert && current.insert && undo.text.length < 50) {
                    if (undo.pos + undo.text.length == current.pos) {
                        undo.text += current.text
                        added = true
                    }
                } else if (!undo.insert && !current.insert && undo.text.length < 50) {
                    if (current.pos + current.text.length == undo.pos) {
                        undo.text = current.text + undo.text
                        undo.pos = current.pos
                        added = true
                    }
                }
            }
            if (undoPos < undoList.size) {
                undoList = ArrayList(undoList.subList(0, undoPos))
            }
            if (undoPos != undoList.size) {
                throw RuntimeException("undoPos != undoList.size()")
            }
            if (added) {
                currentUndo.clear()
                return
            }
            undoList.add(currentUndo)
            currentUndo = ArrayList()
            undoPos++
        }
        //System.out.printf("finishUndo (hash: %d)\n", text.hashCode());
    }

    private fun propCol(property: String, def: String): Color {
        return Color(GlobalEditor.getIntRadix(property, def, 16))
    }

    private fun fullUpdate(insert: Boolean, len: Int, offset: Int) {
        //System.out.printf("fullUpdate(%s, %d, %d)\n", insert ? "insert" : "delete", len, offset);
        cd.reHighlight(insert, len, offset)
        upd()
    }

    private fun upd() {
        val name = if (musicMode) {
            "Music editor"
        } else {
            "Code editor"
        }
        form.title = String.format("%s :: %s :: %d/%d", name, title, editor.caretPosition, editor.text.length)
        if (musicMode) {
            editor.background = propCol("EDITOR_MUSIC_BG", "000000")
        } else if (readOnly) {
            editor.background = propCol("EDITOR_BIND_BG", "000000")
        } else {
            editor.background = propCol("EDITOR_BG", "000000")
        }
    }

    val code: ByteArray
        get() {
            var text = editor.text
            if (GlobalEditor.getBoolean("AUTO_INSERT_NEWLINE")) {
                if (!text.isEmpty() && text[text.length - 1] != '\n') {
                    text += '\n'
                }
            }
            return CP437.toBytes(text, false)
        }

    override fun keyTyped(e: KeyEvent) {
        if (musicMode) {
            musicKey(e)
        }
    }

    private fun musicKey(e: KeyEvent) {
        e.consume()
    }

    override fun keyPressed(e: KeyEvent) {
        if (Util.keyMatches(e, k_AltT)) {
            testAudio()
        } else if (Util.keyMatches(e, k_Escape)) {
            if (Playing.audio != null) {
                stopAudio()
            } else {
                close()
            }
        } else if (Util.keyMatches(e, k_CtrlF)) find(false)
        else if (Util.keyMatches(e, k_CtrlH)) find(true)
        else if (Util.keyMatches(e, k_CtrlZ)) undo()
        else if (Util.keyMatches(e, k_CtrlY)) redo()
        else if (Util.keyMatches(e, k_CtrlD)) duplicateLine()
        else if (Util.keyMatches(e, k_transposeDown)) transposeDown()
        else if (Util.keyMatches(e, k_transposeUp)) transposeUp()
        else if (Util.keyMatches(e, k_F3)) Util.charInsert(worldEditor, editor, form, form)
        else if (musicMode && e.keyCode == KeyEvent.VK_ENTER) {
            e.consume() // We will handle this in the keyTyped event handler
        } else return
        e.consume()
    }

    private fun redo() {
        if (undoPos >= undoList.size) return  // Nothing to redo

        val undoItem = undoList[undoPos]
        var caret = -1
        for (undoStep in undoItem) {
            try {
                doOp(undoStep.insert, undoStep.pos, undoStep.text)
            } catch (e: BadLocationException) {
                undoError()
                return
            }
            caret = undoStep.caret
        }
        lastText = editor.text

        if (caret != -1 && caret < lastText.length) editor.caretPosition = caret
        undoPos++
    }

    private fun undoError() {
        undoPos = 0
        undoList.clear()
        lastText = editor.text
        JOptionPane.showMessageDialog(form, "Error performing undo/redo", "Undo/redo error", JOptionPane.ERROR_MESSAGE)
    }

    private fun undo() {
        if (undoPos == 0) return  // Nothing to undo

        undoPos--
        val undoItem = undoList[undoPos]
        var caret = -1
        for (i in undoItem.indices.reversed()) {
            val undoStep = undoItem[i]
            try {
                doOp(!undoStep.insert, undoStep.pos, undoStep.text)
            } catch (e: BadLocationException) {
                undoError()
                return
            }
            caret = undoStep.caret
        }
        lastText = editor.text
        if (caret != -1 && caret < lastText.length) editor.caretPosition = caret
    }

    @Throws(BadLocationException::class)
    private fun doOp(insert: Boolean, pos: Int, text: String) {
        recordUndo = false
        if (insert) {
            editor.document.insertString(pos, text, null)
        } else {
            editor.document.remove(pos, text.length)
        }
        recordUndo = true
    }

    private fun close() {
        if (musicMode) {
            toggleMusicMode()
            return
        }
        stopAudio()
        form.dispose()
    }

    private fun testAudio() {
        if (Playing.audio != null) {
            stopAudio()
            return
        }
        val saved_selectStart = editor.selectionStart
        val saved_selectEnd = editor.selectionEnd
        val selectionLength = saved_selectEnd - saved_selectStart

        val breakOnNonMusic: Boolean
        val playMin: Int
        val playMax: Int
        if (selectionLength == 0) {
            breakOnNonMusic = true
            playMin = 0
            playMax = Int.MAX_VALUE
        } else {
            breakOnNonMusic = false
            playMin = saved_selectStart
            playMax = saved_selectEnd
        }

        val cursor = beginningOfLine(saved_selectStart)
        val `as` = getMusicLines(cursor, playMin, playMax, breakOnNonMusic)
        val lineStarts = mutableListOf<Int>()
        for (musicLine in `as`) {
            lineStarts.add(musicLine.linestart)
        }
        Playing.lineStarts = lineStarts

        if (`as`.isEmpty()) return

        playSequence(`as`, object : AudioCallback {
            override fun upTo(line: Int, pos: Int, len: Int) {
                if (line == -1) {
                    Playing.audio = null
                    //System.err.printf("editor.setCaretPosition(%d)\n", editor.getCaretPosition());
                    editor.caretPosition = saved_selectStart
                    editor.moveCaretPosition(saved_selectEnd)
                    return
                }
                val ls = Playing.lineStarts
                //System.out.printf("upTo(%d, %d, %d)\n", line, pos, len);
                val start = ls[line] + pos
                //System.err.printf("editor.select(%d, %d)\n", start, start + len);
                editor.caretPosition = start
                editor.moveCaretPosition(start + len)
            }
        }).also { Playing.audio = it }
    }

    private fun getMusicLines(cursor: Int, playMin: Int, playMax: Int, breakOnNonMusic: Boolean): ArrayList<MusicLine> {
        var cursor = cursor
        val `as` = ArrayList<MusicLine>()
        while (cursor != -1) {
            val s = getLine(cursor)
            val beginningOfLine = cursor
            val endOfLine = beginningOfLine + s.length
            val musical = isMusicalLine(s)
            if (musical == 1) {
                var mstart = 0
                var mend = s.length
                if (playMin > beginningOfLine && playMin <= endOfLine) {
                    mstart = playMin - beginningOfLine
                }
                if (playMax > beginningOfLine && playMax <= endOfLine) {
                    mend = playMax - beginningOfLine
                }

                `as`.add(MusicLine(s, mstart, mend, cursor))
            } else {
                if (breakOnNonMusic && musical == -1) break
            }

            cursor = nextLine(cursor)
            if (cursor >= playMax) break
        }

        return `as`
    }

    private fun stopAudio() {
        val audio = Playing.audio ?: return
        audio.stop()
        Playing.audio = null
    }

    private fun isMusicalLine(s: String): Int {
        var s = s
        s = s.replace("?i", "")
        s = s.replace("/i", "")
        s = s.uppercase(Locale.getDefault()).trim { it <= ' ' }
        if (s.isEmpty()) return 0
        if (s.startsWith(":")) return 0
        if (s.startsWith("#PLAY")) return 1
        return -1
    }

    private fun nextLine(pos: Int): Int {
        var pos = pos
        val s = editor.text
        while (true) {
            if (pos == s.length) return -1
            if (s[pos] == '\n') break
            pos++
        }
        if (pos + 1 >= s.length) return -1
        return pos + 1
    }

    private fun beginningOfLine(pos: Int): Int {
        var pos = pos
        val s = editor.text
        while (true) {
            if (pos == 0) break
            if (s[pos - 1] == '\n') break
            pos--
        }
        return pos
    }

    private fun getLine(pos: Int): String {
        val s = editor.text
        var endPos = pos
        while (true) {
            if (endPos == s.length) break
            if (s[endPos] == '\n') break
            endPos++
        }
        return s.substring(pos, endPos)
    }

    override fun keyReleased(e: KeyEvent) {
    }

    private fun addButtonTo(rightPane: JPanel, text: String): JButton {
        val buttonPane = JPanel(BorderLayout())
        val button = JButton(text)
        buttonPane.add(button, BorderLayout.NORTH)
        buttonPane.border = BorderFactory.createEmptyBorder(2, 0, 2, 8)
        rightPane.add(buttonPane)
        return button
    }

    private fun addTextfieldTo(centrePane: JPanel, s: String): JTextField {
        val textPane = JPanel(BorderLayout())
        val textField = JTextField(32)
        val lbl = JLabel(s)
        lbl.preferredSize = Dimension(60, 0)
        lbl.border = BorderFactory.createEmptyBorder(0, 0, 0, 8)

        textPane.add(lbl, BorderLayout.WEST)
        textPane.add(textField, BorderLayout.CENTER)

        val fullpanel = JPanel(BorderLayout())
        fullpanel.add(textPane, BorderLayout.NORTH)
        fullpanel.border = BorderFactory.createEmptyBorder(2, 8, 2, 0)
        centrePane.add(fullpanel)

        return textField
    }

    private fun addCheckboxTo(centrePane: JPanel, s: String, cfg: String) {
        val ge = worldEditor.globalEditor

        val chk = JCheckBox(s, GlobalEditor.getBoolean(cfg, false))
        chk.addItemListener { e: ItemEvent? -> GlobalEditor.setBoolean(cfg, chk.isSelected) }
        chk.border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        val fullpanel = JPanel(BorderLayout())
        fullpanel.add(chk, BorderLayout.NORTH)
        fullpanel.border = BorderFactory.createEmptyBorder(2, 8, 2, 0)
        centrePane.add(fullpanel)
    }


    private fun find(replace: Boolean) {
        if (findDialog != null) {
            Logger.i(TAG) { "Closing" }
            findDialog!!.dispose()
        }
        selFromRep = false
        findDialog = object : JDialog(form) {
            init {
                Util.addEscClose(this, this.rootPane)
                this.isResizable = false
                this.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                this.addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent) {
                        findDialog = null
                    }
                })
                this.title = if (replace) "Replace Text" else "Find Text"
                this.contentPane.layout = BorderLayout()
                this.setIconImage(worldEditor.canvas.extractCharImage('?'.code, 0x70, 1, 1, false, "$"))
                val findNextBtn: JButton
                val cancelBtn: JButton
                val replaceBtn: JButton
                val replaceAllBtn: JButton

                val centrePane = JPanel(GridLayout(0, 1))
                centrePane.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
                val rightPane = JPanel(GridLayout(0, 1))
                rightPane.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
                this.contentPane.add(centrePane, BorderLayout.CENTER)
                this.contentPane.add(rightPane, BorderLayout.EAST)

                findNextBtn = addButtonTo(rightPane, "Find next")
                this.rootPane.defaultButton = findNextBtn
                val findTextField = addTextfieldTo(centrePane, "Find what:")
                findNextBtn.addActionListener { e: ActionEvent? -> findNext(findTextField.text) }
                if (replace) {
                    replaceBtn = addButtonTo(rightPane, "Replace")
                    replaceAllBtn = addButtonTo(rightPane, "Replace All")
                    val replaceTextField = addTextfieldTo(centrePane, "Replace:")
                    replaceBtn.addActionListener { e: ActionEvent? ->
                        replace(
                            findTextField.text,
                            replaceTextField.text,
                            false,
                            selFromRep
                        )
                    }
                    replaceAllBtn.addActionListener { e: ActionEvent? ->
                        replace(
                            findTextField.text,
                            replaceTextField.text,
                            true,
                            selFromRep
                        )
                    }
                }
                cancelBtn = addButtonTo(rightPane, "Cancel")
                if (!replace) {
                    rightPane.add(JPanel())
                }
                addCheckboxTo(centrePane, "Match case", "FIND_MATCH_CASE")
                addCheckboxTo(centrePane, "Regexp", "FIND_REGEX")

                cancelBtn.addActionListener { e: ActionEvent? -> findDialog!!.dispose() }

                this.pack()
                this.setLocationRelativeTo(form)
                this.isVisible = true
                Logger.i(TAG) {"Requesting Focus."}
                findTextField.requestFocusInWindow()
            }
        }

    }

    private fun replace(text: String, replaceWith: String, all: Boolean, selectionFromReplace: Boolean) {
        if (text.isEmpty()) return
        var document = editor.text
        var caret = editor.selectionStart
        if (selectionFromReplace) {
            caret = (caret + 1) % document.length
        }
        val startCaret = caret
        var wrapped = false
        var replaces = 0
        var lastStart = -1
        var lastEnd = -1
        while (true) {
            val res = generalSearch(document, caret, text)
            if (res == null) {
                if (replaces == 0) {
                    JOptionPane.showMessageDialog(
                        findDialog,
                        "The specified text was not found.",
                        "Replace",
                        JOptionPane.WARNING_MESSAGE
                    )
                    return
                }
                break
            } else {
                val start = res[0]
                val end = res[1]
                if (start < caret && !wrapped) {
                    wrapped = true
                } else if (start >= startCaret && wrapped) {
                    break
                }

                lastStart = start
                lastEnd = start + replaceWith.length
                caret = (start + 1) % document.length

                document = document.substring(0, start) + replaceWith + document.substring(end)
                replaces++
            }
            if (!all) break
        }

        editor.text = document
        editor.caretPosition = lastStart
        editor.moveCaretPosition(lastEnd)
        selFromRep = true

        if (all) {
            JOptionPane.showMessageDialog(
                findDialog,
                String.format(
                    "%d occurrence%s of the specified text have been replaced.",
                    replaces,
                    if (replaces == 1) "" else "s"
                ),
                "Replace", JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    private fun findNext(text: String) {
        val document = editor.text
        val caret = editor.caretPosition
        val res = generalSearch(document, caret, text)
        if (res == null) {
            JOptionPane.showMessageDialog(
                findDialog,
                "The specified text was not found.",
                "Find next",
                JOptionPane.WARNING_MESSAGE
            )
        } else {
            editor.caretPosition = res[0]
            editor.moveCaretPosition(res[1])
            selFromRep = false
        }
    }

    private fun generalSearch(document: String, caret: Int, text: String): ArrayList<Int>? {
        var document = document
        var text = text
        val regex = GlobalEditor.getBoolean("FIND_REGEX", false)
        val caseSensitive = GlobalEditor.getBoolean("FIND_MATCH_CASE", false)

        if (!regex) {
            if (!caseSensitive) {
                document = document.lowercase(Locale.getDefault())
                text = text.lowercase(Locale.getDefault())
            }
            var r = document.indexOf(text, caret)
            if (r != -1) return Util.pair(r, r + text.length)
            if (caret > 0) {
                r = document.indexOf(text)
                if (r != -1) return Util.pair(r, r + text.length)
            }
            return null
        } else {
            var flags = Pattern.MULTILINE or Pattern.UNIX_LINES
            if (!caseSensitive) {
                flags = flags or Pattern.CASE_INSENSITIVE
            }
            val pattern = Pattern.compile(text, flags)
            val matcher = pattern.matcher(document)
            if (matcher.find(caret)) {
                return Util.pair(matcher.start(), matcher.end())
            }
            if (matcher.find(0)) {
                return Util.pair(matcher.start(), matcher.end())
            }
            return null
        }
    }

    private fun createMenu() {
        val menus = ArrayList<Menu>()
        val m = Menu("Menu")
        m.add("Test music", "Alt-T") { e: ActionEvent? -> testAudio() }
        m.add("Transpose music down", "Ctrl--") { e: ActionEvent? -> transposeDown() }
        m.add("Transpose music up", "Ctrl-=") { e: ActionEvent? -> transposeUp() }
        m.add()
        m.add("Duplicate line", "Ctrl-D") { e: ActionEvent? -> duplicateLine() }
        m.add("Insert character", "F3") { e: ActionEvent? -> Util.charInsert(worldEditor, editor, form, form) }
        m.add()
        m.add("Find", "Ctrl-F") { e: ActionEvent? -> find(false) }
        m.add("Replace", "Ctrl-H") { e: ActionEvent? -> find(true) }
        m.add()
        m.add("Undo", "Ctrl-Z") { e: ActionEvent? -> undo() }
        m.add("Redo", "Ctrl-Y") { e: ActionEvent? -> redo() }
        m.add()
        //m.add("Toggle Music Mode", "Alt-M", e -> toggleMusicMode());
        //m.add();
        m.add("Close", "Escape") { e: ActionEvent? -> close() }
        menus.add(m)

        val menuBar = JMenuBar()
        for (menu in menus) {
            val jmenu = JMenu(menu.title)
            for (menuEntry in menu) {
                menuEntry.addToJMenu(worldEditor.globalEditor, jmenu)
            }
            menuBar.add(jmenu)
        }
        form.jMenuBar = menuBar
    }

    private fun transposeUp() {
        try {
            transpose(1)
        } catch (ignored: BadLocationException) {
        }
    }

    private fun transposeDown() {
        try {
            transpose(-1)
        } catch (ignored: BadLocationException) {
        }
    }

    @Throws(BadLocationException::class)
    private fun transpose(by: Int) {
        val saved_selectStart = editor.selectionStart
        var saved_selectEnd = editor.selectionEnd

        val cursor = beginningOfLine(saved_selectStart)
        val `as`: ArrayList<MusicLine>

        if (saved_selectStart == saved_selectEnd) {
            val startOfLine = beginningOfLine(saved_selectStart)
            val endOfLine = saved_selectStart + getLine(saved_selectStart).length
            `as` = getMusicLines(cursor, startOfLine, endOfLine, false)
        } else {
            `as` = getMusicLines(cursor, saved_selectStart, saved_selectEnd, false)
        }

        if (`as`.isEmpty()) return
        var codeOffset = 0
        val musicNoteLines = ArrayList<ArrayList<MusicNote>>()
        var transposeFailed = false
        for (musicLine in `as`) {
            val musicNotes = checkNotNull(MusicNote.fromPlay(musicLine.seq))
            if (musicNotes.isEmpty()) continue

            // Need to transpose notes between musicLine.start and musicLine.end
            for (musicNote in musicNotes) {
                if (musicNote.indicate_pos >= musicLine.start &&
                    musicNote.indicate_pos < musicLine.end
                ) {
                    if (!musicNote.transpose(by)) {
                        transposeFailed = true
                    }
                }
            }
            // after transposing all the notes, we need to fix up the octaves
            var i = 0
            while (i < musicNotes.size) {
                i = MusicNote.fixOctavesFor(i, musicNotes)
                i++
            }
            musicNoteLines.add(musicNotes)
        }
        if (transposeFailed) return

        for (line in `as`.indices) {
            val musicLine = `as`[line]
            val musicNotes = musicNoteLines[line]
            // Now, we need to replace the original #play line with this new one
            val preamble = musicLine.seq.substring(0, musicNotes[0].indicate_pos)
            val newSeq = StringBuilder(preamble)
            for (musicNote in musicNotes) {
                newSeq.append(musicNote.original)
            }

            editor.document.remove(musicLine.linestart + codeOffset, musicLine.seq.length)
            editor.document.insertString(musicLine.linestart + codeOffset, newSeq.toString(), null)
            val offsetChange = newSeq.toString().length - musicLine.seq.length
            codeOffset += offsetChange
            if (saved_selectStart != saved_selectEnd) {
                saved_selectEnd += offsetChange
            }
        }

        editor.caretPosition = saved_selectStart
        editor.moveCaretPosition(saved_selectEnd)
    }

    private fun toggleMusicMode() {
        musicMode = !musicMode
        upd()
    }

    private fun duplicateLine() {
        val caret = editor.caretPosition
        val begin = beginningOfLine(caret)
        var end = nextLine(caret)
        val text = editor.text
        if (end == -1) end = text.length
        try {
            var dupedLine = text.substring(begin, end)
            if (dupedLine.isEmpty()) return
            if (dupedLine[dupedLine.length - 1] != '\n') {
                dupedLine = dupedLine + "\n"
            }
            editor.document.insertString(begin, dupedLine, null)
        } catch (e: BadLocationException) {
            JOptionPane.showMessageDialog(form, "Error duplicating line", "Duplicate line", JOptionPane.ERROR_MESSAGE)
        }
    }
}
