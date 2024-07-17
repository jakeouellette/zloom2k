package zedit2.components

import zedit2.util.CP437.font
import zedit2.util.CP437.toUnicode
import zedit2.components.ColourSelector.Companion.createColourSelector
import zedit2.components.StatSelector.Companion.getStatIdx
import zedit2.components.editor.code.CodeEditor
import zedit2.components.editor.code.CodeEditorFactory
import zedit2.event.TileEditorCallback
import zedit2.model.Board
import zedit2.model.IconFactory
import zedit2.model.Stat
import zedit2.model.Tile
import zedit2.model.spatial.Pos
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.SZZTType
import zedit2.util.ZType
import zedit2.util.ZZTType
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.ListSelectionListener

class TileEditor(
    editor: WorldEditor,
    private val boardPosOffset: Pos,
    val currentBoard: Int,
    val isSuperZZT: Boolean,
    private val boards: List<Board>,
    private val imageRetriever: ImageRetriever,
    val frameForRelativePositioning: Component,
    board: Board,
    inputTile: Tile?,
    stats: List<Stat>?,
    callback: TileEditorCallback,
    xy: Pos,
    advanced: Boolean,
    selected: Int,
    editExempt: Boolean,
    private val onIndicateSet: (Array<Pos>?) -> Unit,
    val getKeystroke: (stroke: String) -> KeyStroke
) {
    private val editExempt: Boolean
    private val selected: Int
    private val editor: WorldEditor
    private lateinit var tile: Tile
    private val originalTile: Tile
    private val szzt: Boolean
    private lateinit var everyType: Array<String?>
    private val callback: TileEditorCallback
    private var tileEditorFrame: JDialog? = null
    private var statList: JList<String?>? = null
    private val tilePos: Pos
    private val board: Board
    private var currentStat: Stat? = null
    private var otherEditorPanel: JPanel? = null
    private var otherEditorPanelActive: JPanel? = null

    // Edit: ID, Colour
    // +-------+
    // | Stats | X, Y, StepX, StepY, Cycle, P1, P2, P3, Follower, Leader, Uid, Uco, CurrentInstruction, Code / Bind, Order
    // +-------+
    // Type: [        ] Col: [ ]
    // ------------------------------------
    //  Stat  | X      | P1     | Uid    |
    //  List  | Y      | P2     | Uco    |
    // -------| StepX  | P3     | IP     |
    //  New   | StepY  | Follower| Code   |
    //          Cycle    Leader    Order
    init {
        internalTagTracker++
        this.callback = callback
        this.editor = editor
        this.board = board
        this.selected = selected
        this.editExempt = editExempt
        tilePos = xy
        if (inputTile == null) {
            if (stats != null) {
                if (xy.inside(board.dim)) {
                    tile = board.getTile(xy, false)
                } else {
                    tile = Tile(-1, -1, stats)
                }
            } else {
                throw RuntimeException("Must pass in stats or a tile")
            }
        } else {
            tile = inputTile.clone()
        }
        originalTile = tile.clone()
        szzt = isSuperZZT
        if (advanced) {
            createAdvancedGUI()
        } else {
            editorSelect()
        }
    }

    private fun editorSelect() {
        // Certain things are not editable except with the advanced editor.
        // These will result in the dialog not popping up
        val stats = tile.stats
        // First of all, multiple stats means you get the advanced editor, always
        if (stats.size > 1) {
            createAdvancedGUI()
            return
        }
        // On the other hand, no stats means no editor, except for text, which gets a char picker
        if (stats.isEmpty()) {
            if (ZType.isText(szzt, tile.id)) {
                editorText()
                return
            }
        }

        val firstStat = stats.getOrNull(0)
        if (stats.size == 1 && firstStat != null) {
            currentStat = firstStat

            when (tile.id) {
                ZType.OBJECT -> {
                    editorObject()
                    return
                }

                ZType.SCROLL -> {
                    editorScroll()
                    return
                }

                else -> {}
            }
            // May be some other type
            if (editorOther()) {
                return
            }
        }

        // Nothing happened. Call the callback
        callback.callback(tile)
    }

    private fun constructOtherDialog(): Boolean {
        otherEditorPanel = JPanel(BorderLayout())
        otherEditorPanelActive = otherEditorPanel

        val tileId = tile.id
        val szzt = isSuperZZT
        when (tileId) {
            ZType.PASSAGE -> {
                editorAddBoardSelect(PARAM_P3)
                return true
            }

            ZType.DUPLICATOR -> {
                editorAddBar("Start time", 1, 6, PARAM_P1)
                editorAddDirection("Duplicate in direction")
                editorAddBar("Speed", 1, 9, PARAM_P2)
                return true
            }

            ZType.BOMB -> {
                editorAddBar("Countdown (1=unlit 2=cleanup)", 1, 9, PARAM_P1)
                return true
            }

            ZType.BLINKWALL -> {
                editorAddBar("Start time", 1, 9, PARAM_P1)
                editorAddDirection("Direction")
                editorAddBar("Period", 1, 9, PARAM_P2)
                return true
            }

            ZType.TRANSPORTER, ZType.PUSHER -> {
                editorAddDirection("Direction")
                return true
            }

            ZType.BEAR -> {
                editorAddBar("Sensitivity", 1, 9, PARAM_P1)
                return true
            }

            ZType.RUFFIAN -> {
                editorAddBar("Intelligence", 1, 9, PARAM_P1)
                editorAddBar("Resting time", 1, 9, PARAM_P2)
                return true
            }

            ZType.SLIME -> {
                editorAddBar("Speed", 1, 9, PARAM_P2)
                return true
            }

            ZType.TIGER, ZType.SPINNINGGUN -> {
                editorAddBar("Intelligence", 1, 9, PARAM_P1)
                editorAddBar("Firing rate", 1, 9, PARAM_P2, 127)
                editorAddRadio("Fires bullets", "Fires stars", PARAM_P2, 128)
                return true
            }

            ZType.LION -> {
                editorAddBar("Intelligence", 1, 9, PARAM_P1)
                return true
            }

            ZType.HEAD -> {
                editorAddBar("Intelligence", 1, 9, PARAM_P1)
                editorAddBar("Deviance", 1, 9, PARAM_P2)
                return true
            }

            else -> {}
        }
        if ((!szzt && tileId == ZZTType.BULLET) || (szzt && tileId == SZZTType.BULLET)) {
            editorAddDirection("Direction")
            editorAddRadio("Player bullet", "Enemy bullet", PARAM_P1, 1)
            return true
        }
        if ((!szzt && tileId == ZZTType.STAR) || (szzt && tileId == SZZTType.STAR)) {
            editorAddDirection("Direction")
            editorAddRadio("Player star", "Enemy star", PARAM_P1, 1)
            editorAddSpinner("Lifespan", 0, 255, PARAM_P2)
            return true
        }
        if (!szzt && tileId == ZZTType.SHARK) {
            editorAddBar("Intelligence", 1, 9, PARAM_P1)
            return true
        }
        if (szzt && tileId == SZZTType.ROTON) {
            editorAddBar("Intelligence", 1, 9, PARAM_P1)
            editorAddBar("Switch rate", 1, 9, PARAM_P2)
            return true
        }
        if (szzt && tileId == SZZTType.DRAGONPUP) {
            editorAddBar("Intelligence", 1, 9, PARAM_P1)
            editorAddBar("Switch rate", 1, 9, PARAM_P2)
            return true
        }
        if (szzt && tileId == SZZTType.PAIRER) {
            editorAddBar("Intelligence", 1, 9, PARAM_P1)
            return true
        }
        if (szzt && tileId == SZZTType.SPIDER) {
            editorAddBar("Intelligence", 1, 9, PARAM_P1)
            return true
        }

        return false
    }

    private fun getParam(param: Int): Int {
        return when (param) {
            PARAM_P1 -> currentStat!!.p1
            PARAM_P2 -> currentStat!!.p2
            PARAM_P3 -> currentStat!!.p3
            else -> throw UnsupportedOperationException()
        }
    }

    private fun setParam(param: Int, value: Int) {
        when (param) {
            PARAM_P1 -> currentStat!!.p1 = value
            PARAM_P2 -> currentStat!!.p2 = value
            PARAM_P3 -> currentStat!!.p3 = value
            else -> throw UnsupportedOperationException()
        }
    }

    private fun appendToActivePanel(component: JComponent) {
        otherEditorPanelActive!!.add(component, BorderLayout.NORTH)
        val newActivePanel = JPanel(BorderLayout())
        otherEditorPanelActive!!.add(newActivePanel, BorderLayout.CENTER)
        otherEditorPanelActive = newActivePanel
    }

    private fun editorAddSpinner(label: String, min: Int, max: Int, param: Int) {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel(label), BorderLayout.WEST)
        val initialValue = getParam(param)
        val spinner = JSpinner(SpinnerNumberModel(initialValue, min, max, 1))
        spinner.addChangeListener { e: ChangeEvent? -> setParam(param, spinner.value as Int) }
        panel.add(spinner, BorderLayout.EAST)

        appendToActivePanel(panel)
    }

    private fun editorAddRadio(option1: String, option2: String, param: Int, value: Int) {
        val initialState = (getParam(param) and value) == value
        val panel = JPanel(BorderLayout())
        val cb1 = JRadioButton(option1, !initialState)
        val cb2 = JRadioButton(option2, initialState)
        val listener = ChangeListener { e: ChangeEvent? ->
            val v = getParam(param) and (value.inv()) or (if (cb2.isSelected) value else 0)
            setParam(param, v)
        }
        cb1.addChangeListener(listener)
        cb2.addChangeListener(listener)
        val bg = ButtonGroup()
        bg.add(cb1)
        bg.add(cb2)

        panel.add(cb1, BorderLayout.NORTH)
        panel.add(cb2, BorderLayout.SOUTH)

        appendToActivePanel(panel)
    }

    private fun editorAddDirection(label: String) {
        val panel = JPanel(GridLayout(4, 1))
        panel.border = BorderFactory.createTitledBorder(label)
        val cbN = JRadioButton("North", (currentStat!!.stepX == 0) && (currentStat!!.stepY == -1))
        val cbS = JRadioButton("South", (currentStat!!.stepX == 0) && (currentStat!!.stepY == 1))
        val cbE = JRadioButton("East", (currentStat!!.stepX == 1) && (currentStat!!.stepY == 0))
        val cbW = JRadioButton("West", (currentStat!!.stepX == -1) && (currentStat!!.stepY == 0))
        val listener = ChangeListener { e: ChangeEvent? ->
            if (cbN.isSelected) {
                currentStat!!.stepX = 0
                currentStat!!.stepY = -1
            } else if (cbS.isSelected) {
                currentStat!!.stepX = 0
                currentStat!!.stepY = 1
            } else if (cbE.isSelected) {
                currentStat!!.stepX = 1
                currentStat!!.stepY = 0
            } else if (cbW.isSelected) {
                currentStat!!.stepX = -1
                currentStat!!.stepY = 0
            }
        }
        cbN.addChangeListener(listener)
        cbS.addChangeListener(listener)
        cbE.addChangeListener(listener)
        cbW.addChangeListener(listener)
        val bg = ButtonGroup()
        bg.add(cbN)
        bg.add(cbS)
        bg.add(cbE)
        bg.add(cbW)
        panel.add(cbN)
        panel.add(cbS)
        panel.add(cbE)
        panel.add(cbW)

        appendToActivePanel(panel)
    }

    private fun editorAddBar(label: String, min: Int, max: Int, param: Int, mask: Int = Int.MAX_VALUE) {
        val panel = JPanel(BorderLayout())
        panel.add(JLabel(label), BorderLayout.NORTH)
        val initialValue = getParam(param) and mask
        val slider = JSlider(JSlider.HORIZONTAL, min, max, Util.clamp(initialValue + 1, min, max))
        slider.majorTickSpacing = 1
        slider.paintLabels = true
        slider.addChangeListener { e: ChangeEvent? ->
            var v = slider.value - 1
            v = (getParam(param) and mask.inv()) or (v and mask)
            setParam(param, v)
        }
        panel.add(slider, BorderLayout.SOUTH)

        appendToActivePanel(panel)
    }

    private fun editorAddBoardSelect(param: Int) {
        val destination = getParam(param)
        val boards = boards
        val boardNames = arrayOfNulls<String>(boards.size)
        for (i in boardNames.indices) {
            boardNames[i] = toUnicode(boards[i].getName())
        }
        val boardSelect = JComboBox(boardNames)
        if (destination < boards.size) {
            boardSelect.setSelectedIndex(destination)
        } else {
            boardSelect.isEditable = true
            boardSelect.editor.item = String.format("(invalid reference: %d)", destination)
        }
        boardSelect.font = font
        boardSelect.addActionListener { e: ActionEvent? ->
            var newDestination = boardSelect.selectedIndex
            if (newDestination != -1) {
                setParam(param, newDestination)
            } else {
                try {
                    newDestination = (boardSelect.editor.item as String).toInt()
                    if (newDestination >= 0 && newDestination <= 255) {
                        setParam(param, newDestination)
                    }
                } catch (ignored: NumberFormatException) {
                }
            }
        }

        appendToActivePanel(boardSelect)
    }

    private fun relativeFrame(): Component? {
        val frame = tileEditorFrame
        if (frame != null) {
            Logger.i(TAG) { "tileEditorFrame Returning" }
            return frame
        }

//        Logger.i(TAG) { "frameForRelativePositioning Returning" }
        return null
    }

    private fun editorText() {
        createColourSelector(editor, tile.col, relativeFrame(), { e: ActionEvent ->
            tile.col = e.actionCommand.toInt()
            callback.callback(tile)
        }, ColourSelector.CHAR)
    }

    private fun editorObject() {
        // Objects get char selector (for P1) followed by code editor
        createColourSelector(editor, currentStat!!.p1, relativeFrame(), { e: ActionEvent ->
            currentStat!!.p1 = e.actionCommand.toInt()
            // If this is a buffer object, don't open the code editor
            if (!tilePos.isPositive && !editExempt) {
                callback.callback(tile)
                return@createColourSelector
            }
            codeEditor(currentStat) { e1: ActionEvent ->
                if (e1.actionCommand == "update") {
                    val source = e1.source as CodeEditor
                    currentStat!!.code = source.code
                }
                callback.callback(tile)
            }
        }, ColourSelector.CHAR)
    }

    private fun editorScroll() {
        // If this is a buffer object, don't open the editor
        if (!tilePos.isPositive && !editExempt) return
        codeEditor(currentStat) { e: ActionEvent ->
            val source = e.source as CodeEditor
            currentStat!!.code = source.code
            callback.callback(tile)
        }
    }

    private fun editorOther(): Boolean {
        if (constructOtherDialog()) {
            createGUI()
            setTitle()

            val buttonHolder = JPanel(FlowLayout())
            val buttons = JPanel(BorderLayout())
            otherEditorPanel!!.border = BorderFactory.createEmptyBorder(8, 8, 4, 8)
            buttons.border = BorderFactory.createEmptyBorder(4, 8, 8, 8)
            tileEditorFrame!!.contentPane.layout = BorderLayout()
            tileEditorFrame!!.contentPane.add(otherEditorPanel, BorderLayout.CENTER)
            tileEditorFrame!!.contentPane.add(buttonHolder, BorderLayout.SOUTH)
            buttonHolder.add(buttons)

            buttons.add(okButton(), BorderLayout.WEST)
            buttons.add(cancelButton(), BorderLayout.EAST)

            finaliseGUI()
            return true
        } else {
            return false
        }
    }

    private val isModified: Boolean
        get() = !tile.equals(originalTile)

    private fun createGUI() {
        val frame: JDialog = JDialog()
        tileEditorFrame = frame
        Util.addPromptedEscClose(frame, frame.rootPane) {
            if (isModified) {
                val confirm = JOptionPane.showOptionDialog(
                    relativeFrame(),
                    "Are you sure you want to discard changes to this stat?",
                    "Are you sure?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    null,
                    null
                )
                return@addPromptedEscClose confirm != JOptionPane.NO_OPTION
            }
            true
        }
        tileEditorFrame!!.modalityType = Dialog.ModalityType.APPLICATION_MODAL
        tileEditorFrame!!.isResizable = false
        tileEditorFrame!!.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    }

    private fun createAdvancedGUI() {
        everyType = getEveryType()
        createGUI()
        upd()
    }

    private fun setTitle() {
        tileEditorFrame!!.title = "Set " + ZType.getName(szzt, tile.id)
        tileEditorFrame!!.setIconImage(icon)
    }

    private fun setKeystrokes(statList: JList<String?>): KeyListener {
        val k_PgUp = getKeystroke("PgUp")
        val k_PgDn = getKeystroke("PgDn")

        return object : KeyListener {
            override fun keyPressed(e: KeyEvent) {
                Logger.i(TAG) { "TileEditor Key Pressed Handler"}
                if (Util.keyMatches(e, k_PgUp)) {
                    statList.setSelectedIndex(statList.selectedIndex - 1)
                    e.consume()
                } else if (Util.keyMatches(e, k_PgDn)) {
                    statList.selectedIndex = statList.selectedIndex + 1
                    e.consume()
                }
            }

            override fun keyTyped(e: KeyEvent) {}
            override fun keyReleased(e: KeyEvent) {}
        }
    }

    private fun upd() {
        var focusedElement = ""
        try {
            val focusedComponent = tileEditorFrame!!.mostRecentFocusOwner as JComponent
            if (focusedComponent != null) {
                focusedElement = focusedComponent.toolTipText
            }
        } catch (e: ClassCastException) {
        }
        if (statList != null) {
            val statIdx = statList!!.selectedIndex
            if (statIdx > -1 && statIdx < tile.stats.size) {
                ++internalTagTracker
                tile.stats[statIdx].internalTag = internalTagTracker
            }
        }

        setTitle()

        tileEditorFrame!!.contentPane.removeAll()
        tileEditorFrame!!.contentPane.layout = BorderLayout()

        if (tile.id != -1) {
            val tileControls = JPanel()
            tileControls.border = BorderFactory.createTitledBorder("Edit tile:")
            val tileTypeLabel = JLabel("Type:")
            val tileTypeChoice = JComboBox(everyType)
            tileTypeChoice.toolTipText = "Change this tile's type"
            tileTypeChoice.selectedIndex = tile.id
            tileTypeChoice.addActionListener { e: ActionEvent? ->
                tile.id = tileTypeChoice.selectedIndex
                upd()
            }
            val tileColLabel = JLabel("Colour:")
            val selectorMode = if (ZType.isText(szzt, tile.id)) ColourSelector.CHAR else ColourSelector.COLOUR
            val colSelectButton = createColButton(tile.col, selectorMode) { e: ActionEvent ->
                tile.col = e.actionCommand.toInt()
                upd()
            }
            colSelectButton.toolTipText = "Change this tile's colour"

            tileControls.add(tileTypeLabel)
            tileControls.add(tileTypeChoice)
            tileControls.add(tileColLabel)
            tileControls.add(colSelectButton)
            tileEditorFrame!!.contentPane.add(tileControls, BorderLayout.NORTH)
        }

        val statControls = JPanel(BorderLayout())
        statControls.border = BorderFactory.createTitledBorder("Edit stats:")

        statList = JList(getStats(tile.stats))
        statList!!.toolTipText = "Select a stat to edit the properties of"
        restoreSelectedIndex(statList!!)
        val statListListener = ListSelectionListener { upd() }
        statList!!.addListSelectionListener(statListListener)

        val statListScroll = JScrollPane(statList)
        statListScroll.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        statListScroll.preferredSize = Dimension(50, 0)
        val selectedStatBounds = statList!!.getCellBounds(statList!!.selectedIndex, statList!!.selectedIndex)
        if (selectedStatBounds != null) {
            selectedStatBounds.grow(0, selectedStatBounds.height)
            statList!!.scrollRectToVisible(selectedStatBounds)
        }

        statControls.add(statListScroll, BorderLayout.WEST)

        val statParamPanel = JPanel(GridLayout(5, 3))
        statParamPanel.border = BorderFactory.createEmptyBorder(4, 2, 4, 2)
        fillStatParamPanel(statParamPanel, tile, statList!!.selectedIndex)

        statControls.add(statParamPanel, BorderLayout.CENTER)

        val statListButtons = JPanel(GridLayout(1, 3))
        val addStatButton = JButton("Add stat")
        addStatButton.toolTipText = "Add a new stat"
        addStatButton.addActionListener { e: ActionEvent? -> addStat(statList!!, statListListener, null) }
        val dupStatButton = JButton("Clone stat")
        dupStatButton.toolTipText = "Add a new stat, duplicating the currently selected stat"
        dupStatButton.addActionListener { e: ActionEvent? ->
            addStat(
                statList!!, statListListener, tile.stats[statList!!.selectedIndex]
            )
        }
        val delStatButton = JButton("Delete stat")
        delStatButton.toolTipText = "Delete the currently selected stat"
        delStatButton.addActionListener { e: ActionEvent? ->
            delStat(
                statList!!,
                statListListener,
                statList!!.selectedIndex
            )
        }
        statListButtons.add(addStatButton)

        val bottomRow = JPanel(BorderLayout())
        bottomRow.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        bottomRow.add(statListButtons, BorderLayout.WEST)

        val tileSubmitButtons = JPanel(GridLayout(1, 2))
        tileSubmitButtons.add(okButton())
        tileSubmitButtons.add(cancelButton())
        bottomRow.add(tileSubmitButtons, BorderLayout.EAST)

        if (!tile.stats.isEmpty()) {
            statListButtons.add(dupStatButton)
            statListButtons.add(delStatButton)

            tileEditorFrame!!.contentPane.add(statControls, BorderLayout.CENTER)
        }

        tileEditorFrame!!.contentPane.add(bottomRow, BorderLayout.SOUTH)
        val keyListener = setKeystrokes(statList!!)
        restoreFocus(tileEditorFrame!!.contentPane, focusedElement, keyListener)
        finaliseGUI()
    }

    private fun cancelButton(): JButton {
        val button = JButton("Cancel")
        button.addActionListener { e: ActionEvent? -> tileEditorFrame!!.dispose() }
        return button
    }

    private fun okButton(): JButton {
        val button = JButton("OK")
        button.addActionListener { e: ActionEvent? ->
            callback.callback(tile)
            tileEditorFrame!!.dispose()
        }
        return button
    }

    private fun finaliseGUI() {
        tileEditorFrame!!.pack()
        tileEditorFrame!!.setLocationRelativeTo(frameForRelativePositioning)
        tileEditorFrame!!.isVisible = true
    }

    private fun addStat(statList: JList<String?>, statListListener: ListSelectionListener, copyFrom: Stat?) {
        val t: Tile = tile
        val newStat = copyFrom?.clone() ?: Stat(szzt)
        if (tilePos.isPositive) {
            newStat.pos = tilePos + 1
        } else {
            newStat.pos = Pos(-1, -1)
        }
        newStat.statId = -1
        t.addStat(newStat)
        statList.removeListSelectionListener(statListListener)
        statList.setListData(getStats(t.stats))
        statList.selectedIndex = t.stats.size - 1
        upd()
    }

    private fun delStat(statList: JList<String?>, statListListener: ListSelectionListener, statIdx: Int) {
        val t: Tile = tile
        if (t.stats[statIdx].statId == 0) {
            JOptionPane.showMessageDialog(relativeFrame(), "You can't delete stat 0.")
            return
        }
        t.delStat(statIdx)
        statList.removeListSelectionListener(statListListener)
        statList.setListData(getStats(t.stats))
        statList.selectedIndex = t.stats.size - 1
        upd()
    }

    private fun restoreSelectedIndex(statList: JList<String?>) {
        val statCount = tile.stats.size
        if (statCount == 0) return
        for (i in 0 until statCount) {
            if (tile.stats[i].internalTag == internalTagTracker) {
                statList.selectedIndex = i
                return
            }
        }
        if (selected != -1) {
            for (i in 0 until statCount) {
                if (tile.stats[i].statId == selected) {
                    statList.selectedIndex = i
                    return
                }
            }
        }
        statList.selectedIndex = 0
    }

    private fun restoreFocus(container: Container, focusedElement: String, listener: KeyListener) {
        Logger.i(TAG) { "Restore Focus" }
        val components = container.components
        for (component in components) {
            if (component is JComponent) {
                val jcomponent = component
                jcomponent.addKeyListener(listener)
                val tooltip = jcomponent.toolTipText
                if (tooltip != null && tooltip == focusedElement) {
                    Logger.i(TAG) { "Requesting Focus." }
                    jcomponent.requestFocusInWindow()
                    return
                }
            }
            if (component is Container) {
                restoreFocus(component, focusedElement, listener)
            }
        }
    }


    private fun createColButton(col: Int, selectorMode: Int, actionListener: ActionListener): JButton {
        val colSelectIcon: Image = if (selectorMode != ColourSelector.CHAR) {
            imageRetriever.extractCharImage(0, col, 1, 1, false, "_#_")
        } else {
            imageRetriever.extractCharImage(col, 0x8F, 1, 1, false, "_\$_")
        }
        val button = JButton(ImageIcon(colSelectIcon))
        button.addActionListener { e: ActionEvent? ->
            createColourSelector(
                editor,
                col,
                relativeFrame(),
                actionListener,
                selectorMode
            )
        }
        return button
    }

    private fun fillStatParamPanel(panel: JPanel, tile: Tile?, selectedIndex: Int) {
        panel.removeAll()
        if (selectedIndex == -1) return
        val stat = tile!!.stats[selectedIndex]
        // Type: [        ] Col: [ ]
        // ------------------------------------
        //  Stat  | X      | P1     | Uid    |
        //  List  | Y      | P2     | Uco    |
        // -------| StepX  | P3     | IP     |
        //  New   | StepY  | Follower| Code   |
        //          Cycle    Leader    Order
        addPosSpin(panel, stat)
        //addInt8Spin(panel, "X:", stat.getX(), e -> stat.setX((Integer) ((JSpinner)e.getSource()).getValue()),
        //        "Edit stat's X position. Warning: this will not change the tile's position. Edit this only if you know what you are doing.");
        addInt8Spin(
            panel, "P1:", stat.p1, { e: ChangeEvent ->
                stat.p1 =
                    ((e.source as JSpinner).value as Int)
            },
            "Edit this stat's 1st parameter (function based on tile type)"
        )
        addUidSelect(panel, "Under type:", stat.uid, { e: ActionEvent ->
            val src = e.source as JComboBox<String>
            stat.uid = src.selectedIndex
            upd()
        }, "Edit under type")
        //addInt8Spin(panel, "Y:", stat.getY(), e -> stat.setY((Integer) ((JSpinner)e.getSource()).getValue()),
        //        "Edit stat's Y position. Warning: this will not change the tile's position. Edit this only if you know what you are doing.");
        addInt16Spin(
            panel, "X-Step:", stat.stepX, { e: ChangeEvent ->
                stat.stepX =
                    ((e.source as JSpinner).value as Int)
            },
            "Edit this stat's X-Step (function based on tile type)"
        )
        addInt8Spin(
            panel, "P2:", stat.p2, { e: ChangeEvent ->
                stat.p2 =
                    ((e.source as JSpinner).value as Int)
            },
            "Edit this stat's 2nd parameter (function based on tile type)"
        )
        addUcoBtn(panel, "Under colour:", stat, { e: ActionEvent ->
            stat.uco = e.actionCommand.toInt()
            upd()
        }, "Edit the colour of the tile under this stat")
        addInt16Spin(
            panel, "Y-Step:", stat.stepY, { e: ChangeEvent ->
                stat.stepY =
                    ((e.source as JSpinner).value as Int)
            },
            "Edit this stat's Y-Step (function based on tile type)"
        )
        addInt8Spin(
            panel, "P3:", stat.p3, { e: ChangeEvent ->
                stat.p3 =
                    ((e.source as JSpinner).value as Int)
            },
            "Edit this stat's 3rd parameter (function based on tile type)"
        )
        addInt16Spin(
            panel, "Instr. Ptr:", stat.ip, { e: ChangeEvent ->
                stat.ip =
                    ((e.source as JSpinner).value as Int)
            },
            "Edit this stat's instruction pointer (-1 means ended)"
        )
        addInt16Spin(
            panel, "Cycle:", stat.cycle, { e: ChangeEvent ->
                stat.cycle =
                    ((e.source as JSpinner).value as Int)
            },
            "Edit this stat's cycle"
        )
        addInt16SpinStatSel(
            panel, "Follower:", stat.follower, { e: ChangeEvent ->
                stat.follower =
                    ((e.source as JSpinner).value as Int)
            },
            "follower"
        )
        addCodeSelect(panel, stat)
        addInt16Spin(
            panel, "Order:", stat.order, { e: ChangeEvent ->
                stat.order =
                    ((e.source as JSpinner).value as Int)
            },
            "Edit this stat's order (lower means its stat ID will be reduced)"
        )
        addInt16SpinStatSel(
            panel, "Leader:", stat.leader, { e: ChangeEvent ->
                stat.leader =
                    ((e.source as JSpinner).value as Int)
            },
            "leader"
        )
        addStatFlags(panel, stat)
    }

    private fun addCodeSelect(panel: JPanel, stat: Stat) {
        val selectPanel = JPanel(BorderLayout())
        selectPanel.border = BorderFactory.createEmptyBorder(0, 2, 0, 2)
        val codeButton: JButton
        val bindButton: JButton
        val listener = ActionListener { e: ActionEvent ->
            if (e.actionCommand == "update") {
                val source = e.source as CodeEditor
                stat.code = source.code
            }
            upd()
        }
        if (stat.codeLength >= 0) {
            if (!tilePos.isPositive) {
                codeButton = JButton("View code (" + stat.codeLength + ")")
                codeButton.toolTipText = "View the code attached to this buffer stat (read-only)"
            } else {
                codeButton = JButton("Edit code (" + stat.codeLength + ")")
                codeButton.toolTipText = "Edit the code attached to this stat"
            }
            codeButton.addActionListener { e: ActionEvent? -> codeEditor(stat, listener) }
            bindButton = JButton("Bind")
            bindButton.toolTipText = "Bind this stat's code to another"
            bindButton.addActionListener { e: ActionEvent? ->
                var confirm = JOptionPane.OK_OPTION
                if (stat.codeLength > 0) {
                    confirm = JOptionPane.showConfirmDialog(
                        relativeFrame(),
                        "This will delete this object's code. Are you sure?",
                        "Are you sure?",
                        JOptionPane.OK_CANCEL_OPTION
                    )
                }
                if (confirm == JOptionPane.OK_OPTION) {
                    StatSelector(boardPosOffset, currentBoard, imageRetriever, board, { e1: ActionEvent ->
                        (e1.source as StatSelector).close()
                        val value = getStatIdx(e1.actionCommand)
                        if (value != stat.statId) {
                            stat.codeLength = -value
                        }
                        upd()
                    }, arrayOf("Select"), null, null, frameForRelativePositioning, isSuperZZT, onIndicateSet)
                }
            }
        } else {
            codeButton = JButton("View code (#" + -stat.codeLength + ")")
            codeButton.toolTipText = "View the code attached to the bound stat (read-only)"
            codeButton.addActionListener { e: ActionEvent? -> codeEditor(stat, listener) }
            bindButton = JButton("Unbind")
            bindButton.toolTipText = "Break this object's bind"
            bindButton.addActionListener { e: ActionEvent? ->
                stat.codeLength = 0
                upd()
            }
        }
        selectPanel.add(codeButton, BorderLayout.CENTER)
        selectPanel.add(bindButton, BorderLayout.EAST)
        panel.add(selectPanel)
    }

    private fun codeEditor(stat: Stat?, listener: ActionListener) {
        CodeEditorFactory.create(tilePos, editExempt, relativeFrame(), editor, icon, board, stat, listener)
    }

    private val icon: Image
        get() = IconFactory.getIcon(szzt, tile, editor)

    private fun addUcoBtn(panel: JPanel, label: String, stat: Stat, actionListener: ActionListener, tooltip: String) {
        val initVal = stat.uco
        val selectPanel = JPanel(BorderLayout())
        selectPanel.border = BorderFactory.createEmptyBorder(0, 2, 0, 2)
        selectPanel.add(JLabel(label), BorderLayout.WEST)
        val selectorMode = if (ZType.isText(szzt, stat.uid)) ColourSelector.CHAR else ColourSelector.COLOUR
        val btn = createColButton(initVal, selectorMode, actionListener)
        btn.toolTipText = tooltip
        selectPanel.add(btn, BorderLayout.EAST)
        panel.add(selectPanel)
    }

    private fun addInt8Spin(
        panel: JPanel,
        label: String,
        initVal: Int,
        changeListener: ChangeListener,
        tooltip: String
    ) {
        addSpin(panel, label, initVal, changeListener, 0, 255, false, tooltip)
    }

    private fun addInt16Spin(
        panel: JPanel,
        label: String,
        initVal: Int,
        changeListener: ChangeListener,
        tooltip: String
    ) {
        addSpin(panel, label, initVal, changeListener, -32768, 32767, false, tooltip)
    }

    private fun addInt16SpinStatSel(
        panel: JPanel,
        label: String,
        initVal: Int,
        changeListener: ChangeListener,
        tooltip: String
    ) {
        addSpin(panel, label, initVal, changeListener, -32768, 32767, true, tooltip)
    }

    private fun addUidSelect(
        panel: JPanel,
        label: String,
        initVal: Int,
        actionListener: ActionListener,
        tooltip: String
    ) {
        val selectPanel = JPanel(BorderLayout())
        selectPanel.border = BorderFactory.createEmptyBorder(0, 2, 0, 2)
        selectPanel.add(JLabel(label), BorderLayout.WEST)
        val select = JComboBox(everyType)
        select.toolTipText = tooltip
        select.selectedIndex = initVal
        select.addActionListener(actionListener)
        selectPanel.add(select, BorderLayout.EAST)
        panel.add(selectPanel)
    }

    private fun addSpin(
        panel: JPanel,
        label: String,
        initVal: Int,
        changeListener: ChangeListener,
        min: Int,
        max: Int,
        statSel: Boolean,
        tooltip: String
    ) {
        val spinPanel = JPanel(BorderLayout())
        spinPanel.border = BorderFactory.createEmptyBorder(0, 2, 0, 2)
        spinPanel.add(JLabel(label), BorderLayout.WEST)
        val spinner = JSpinner(SpinnerNumberModel(initVal, min, max, 1))
        if (!statSel) {
            spinner.toolTipText = tooltip
        } else {
            spinner.toolTipText =
                String.format("Edit this stat's %s (used by centipedes. -1 means no %s)", tooltip, tooltip)
        }
        spinner.addChangeListener(changeListener)
        if (!statSel) {
            spinPanel.add(spinner, BorderLayout.EAST)
        } else {
            val spinPanelSel = JPanel(BorderLayout())
            val spinPanelSearch = JButton("\uD83D\uDD0D")
            spinPanelSearch.addActionListener { e: ActionEvent? ->
                StatSelector(boardPosOffset, currentBoard, imageRetriever, board, { e1: ActionEvent ->
                    (e1.source as StatSelector).close()
                    val value = getStatIdx(e1.actionCommand)
                    spinner.value = value
                }, arrayOf("Select"), null, null, frameForRelativePositioning, isSuperZZT, onIndicateSet)
            }
            spinPanelSearch.toolTipText = String.format("Find a %s", tooltip)
            spinPanelSel.add(spinPanelSearch, BorderLayout.WEST)
            spinPanelSel.add(spinner, BorderLayout.EAST)
            spinPanel.add(spinPanelSel, BorderLayout.EAST)
        }
        panel.add(spinPanel)
    }

    private fun addPosSpin(panel: JPanel, stat: Stat) {
        val spinPanelOuter = JPanel(BorderLayout())
        val toolTip =
            "<html>Edit stat's location. Warning: this will not move the tile itself, only the stat. <b>Edit this only if you know what you are doing.</b></html>"

        spinPanelOuter.add(JLabel("Location:"), BorderLayout.WEST)
        val xSpinner: JSpinner
        val ySpinner: JSpinner
        val bgColour = Color(0xFFCCCC)
        if (stat.x >= 0) {
            xSpinner = JSpinner(SpinnerNumberModel(stat.x, 0, 255, 1))
            ySpinner = JSpinner(SpinnerNumberModel(stat.y, 0, 255, 1))
        } else {
            val model: AbstractSpinnerModel = object : AbstractSpinnerModel() {
                override fun getValue(): Any {
                    return "N/A"
                }

                override fun setValue(value: Any) {}

                override fun getNextValue(): Any? {
                    return null
                }

                override fun getPreviousValue(): Any? {
                    return null
                }
            }
            xSpinner = JSpinner(model)
            ySpinner = JSpinner(model)
            xSpinner.isEnabled = false
            ySpinner.isEnabled = false
        }
        xSpinner.editor.getComponent(0).background = bgColour
        ySpinner.editor.getComponent(0).background = bgColour
        xSpinner.addChangeListener { e: ChangeEvent? -> stat.x = xSpinner.value as Int }
        ySpinner.addChangeListener { e: ChangeEvent? -> stat.y = ySpinner.value as Int }
        xSpinner.toolTipText = toolTip
        ySpinner.toolTipText = toolTip

        val spinPanelInner = JPanel(BorderLayout())
        spinPanelOuter.add(spinPanelInner, BorderLayout.EAST)
        spinPanelInner.add(xSpinner, BorderLayout.WEST)
        spinPanelInner.add(ySpinner, BorderLayout.EAST)
        panel.add(spinPanelOuter)
    }

    private fun addStatFlags(panel: JPanel, stat: Stat) {
        val flagPanel = JPanel(BorderLayout())
        val flagValues = booleanArrayOf(stat.isAutobind, stat.isSpecifyId, stat.isPlayer, stat.isFlag4)
        val flagNames = arrayOf("Autobind", "Set ID", "Player", "4")
        val tooltips = arrayOf(
            "Stat will automatically bind to other stats with the same code",
            "The Order field will be treated as a stat ID and this stat will be given that ID, if possible",
            "This is set to indicate that this is the real player, not a clone",
            "Flag #4"
        )
        val actions = arrayOf(
            ActionListener { e: ActionEvent -> stat.isAutobind = (e.source as JCheckBox).isSelected },
            ActionListener { e: ActionEvent -> stat.isSpecifyId = (e.source as JCheckBox).isSelected },
            ActionListener { e: ActionEvent -> stat.isPlayer = (e.source as JCheckBox).isSelected },
            ActionListener { e: ActionEvent -> stat.isFlag4 = (e.source as JCheckBox).isSelected })

        var currentPanel = flagPanel
        //var checkBoxFont = new Font(Font.SANS_SERIF, Font.PLAIN, 8);
        val numBoxesToDraw = 3
        for (i in 0 until numBoxesToDraw) {
            val cb = JCheckBox(flagNames[i], flagValues[i])
            if (i == 2) cb.isEnabled = false
            cb.toolTipText = tooltips[i]
            cb.addActionListener(actions[i])
            val newPanel = JPanel(BorderLayout())
            currentPanel.add(cb, BorderLayout.WEST)
            currentPanel.add(newPanel, BorderLayout.CENTER)
            currentPanel = newPanel
        }

        panel.add(flagPanel)
    }

    private fun getStats(stats: List<Stat>?): Array<String?> {
        val statList = arrayOfNulls<String>(stats!!.size)
        for (i in stats.indices) {
            val statId = stats[i].statId
            statList[i] = if (statId == -1) "(new)" else statId.toString()
        }
        return statList
    }

    private fun getEveryType(): Array<String?> {
        val allTypes = arrayOfNulls<String>(256)
        for (i in 0..255) {
            allTypes[i] = ZType.getName(szzt, i)
        }
        return allTypes
    }

    companion object {
        private var internalTagTracker = 0
        private const val PARAM_P1 = 1
        private const val PARAM_P2 = 2
        private const val PARAM_P3 = 3
    }
}
