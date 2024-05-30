package zedit2.components

import zedit2.components.BoardManager.Companion.generateBoardSelectArray
import zedit2.util.CP437.font
import zedit2.util.CP437.toBytes
import zedit2.util.CP437.toUnicode
import zedit2.model.ElementListModel.Companion.renderer
import zedit2.model.ElementListModel.Companion.transferHandler
import zedit2.model.Board
import zedit2.model.ElementListModel
import zedit2.model.WorldData
import zedit2.util.LimitDocFilter
import java.awt.*
import java.awt.Dialog.ModalityType
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.text.AbstractDocument

class Settings(private val editor: WorldEditor) {
    private val dialog = JDialog()

    init {
        Util.addEscClose(dialog, dialog.rootPane)
        dialog.isResizable = false
        dialog.setIconImage(null)
        dialog.title = "ZLoom2 Settings"
        dialog.modalityType = ModalityType.APPLICATION_MODAL
        //dialog.setResizable(false);
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE

        val tabbedPane = JTabbedPane()

        tabbedPane.add("Keystroke configuration", keystrokeConfig())
        tabbedPane.add("World testing configuration", testConfig())
        tabbedPane.add("Code editor configuration", codeEditConfig())
        tabbedPane.add("Element menu configuration", elementConfig())

        dialog.add(tabbedPane)
        dialog.pack()
        dialog.setLocationRelativeTo(editor.frameForRelativePositioning)
        dialog.isVisible = true
    }

    private fun elementConfig(): Component {
        val okButton = JButton("OK")
        val cancelButton = JButton("Cancel")
        val applyButton = JButton("Apply")

        val editPanel = JPanel(GridLayout(2, 4, 8, 8))
        val ge = editor.globalEditor

        val trHandler = transferHandler

        val titleFieldFont = Font(Font.SANS_SERIF, Font.BOLD, 11)
        val cellRenderer = renderer
        applyButton.isEnabled = false
        val listDataListener: ListDataListener = object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) {
                upd()
            }

            override fun intervalRemoved(e: ListDataEvent) {
                upd()
            }

            override fun contentsChanged(e: ListDataEvent) {
                upd()
            }

            private fun upd() {
                applyButton.isEnabled = true
            }
        }

        val menuTitleFields = HashMap<Int, JTextField>()
        val menuModels = HashMap<Int, ElementListModel>()

        for (f in 3..10) {
            val elementMenuPanel = JPanel(BorderLayout())
            editPanel.add(elementMenuPanel)
            val elementMenuTitle = JTextField()
            elementMenuTitle.font = titleFieldFont
            val elText = GlobalEditor.getString(String.format("F%d_MENU", f), "")
            menuTitleFields[f] = elementMenuTitle
            elementMenuTitle.text = elText
            val elementMenuTitlePane = JPanel(BorderLayout())

            val flabel = JLabel(String.format("F%d", f))
            flabel.font = font
            flabel.border = BorderFactory.createEmptyBorder(0, 0, 0, 4)

            elementMenuTitlePane.add(flabel, BorderLayout.WEST)
            elementMenuTitlePane.add(elementMenuTitle, BorderLayout.CENTER)

            elementMenuPanel.add(elementMenuTitlePane, BorderLayout.NORTH)


            val itemVec = Vector<String>()
            var i = 0
            while (true) {
                val key = String.format("F%d_MENU_%d", f, i)
                val `val` = GlobalEditor.getString(key, "")
                if (`val`.isEmpty()) break
                itemVec.add(`val`)
                i++
            }
            val menuModel = ElementListModel(ge, f)
            menuModels[f] = menuModel
            val list = JList(menuModel)
            list.cellRenderer = cellRenderer

            list.dropMode = DropMode.ON
            list.dragEnabled = true
            list.transferHandler = trHandler
            list.selectionMode = ListSelectionModel.SINGLE_INTERVAL_SELECTION
            list.model.addListDataListener(listDataListener)
            val scrollList = JScrollPane(list)
            scrollList.preferredSize = Dimension(0, 0)
            scrollList.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
            scrollList.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            //list.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
            elementMenuPanel.add(scrollList, BorderLayout.CENTER)
        }

        cancelButton.addActionListener { e: ActionEvent? -> dialog.dispose() }
        applyButton.addActionListener { e: ActionEvent? ->
            for (f in 3..10) {
                val elementMenuTitle = menuTitleFields[f]
                val menuModel = menuModels[f]
                if (elementMenuTitle!!.text.isEmpty()) {
                    if (menuModel!!.size != 0) {
                        elementMenuTitle.text = "F$f"
                    }
                }
                // Unset this entire menu
                run {
                    var i = 0
                    while (true) {
                        val key = String.format("F%d_MENU_%d", f, i)
                        val s = GlobalEditor.getString(key, "")
                        GlobalEditor.removeKey(key)
                        if (s.isEmpty()) {
                            break
                        }
                        i++
                    }
                }

                val title = elementMenuTitle.text
                GlobalEditor.setString(String.format("F%d_MENU", f), title)
                for (i in 0 until menuModel!!.size) {
                    val element = menuModel.getElementAt(i)
                    GlobalEditor.setString(String.format("F%d_MENU_%d", f, i), element)
                }
                GlobalEditor.setString(String.format("F%d_MENU_%d", f, menuModel.size), "")
            }
            editor.createMenu()
            editor.updateMenu()
            applyButton.isEnabled = false
        }
        okButton.addActionListener { e: ActionEvent? ->
            applyButton.doClick()
            dialog.dispose()
        }

        val presetButtons = getjButtons(menuTitleFields, menuModels)

        return mainPanel(editPanel, okButton, cancelButton, applyButton, presetButtons)
    }

    private fun getjButtons(
        menuTitleFields: HashMap<Int, JTextField>,
        menuModels: HashMap<Int, ElementListModel>
    ): ArrayList<JButton> {
        val zztPreset = JButton("(Super)ZZT Preset")
        val keveditPreset = JButton("KevEdit Preset")
        val zeditPreset = JButton("ZEdit Preset")
        zztPreset.addActionListener { e: ActionEvent? -> loadPreset(menuTitleFields, menuModels, preset_ZZT) }
        keveditPreset.addActionListener { e: ActionEvent? -> loadPreset(menuTitleFields, menuModels, preset_KevEdit) }
        zeditPreset.addActionListener { e: ActionEvent? -> loadPreset(menuTitleFields, menuModels, preset_ZEdit) }
        val presetButtons = ArrayList<JButton>()
        presetButtons.add(zztPreset)
        presetButtons.add(keveditPreset)
        presetButtons.add(zeditPreset)
        return presetButtons
    }

    private fun loadPreset(
        menuTitleFields: HashMap<Int, JTextField>,
        menuModels: HashMap<Int, ElementListModel>,
        preset: Array<Array<String>>
    ) {
        for (f in 3..10) {
            val presetArray = preset[f - 3]
            val elementMenuTitle = menuTitleFields[f]
            val menuModel = menuModels[f]

            elementMenuTitle!!.text = presetArray[0]
            menuModel!!.clear()
            val items = ArrayList(Arrays.asList(*presetArray).subList(1, presetArray.size))
            menuModel.insert(0, items, false)
        }
    }

    private fun codeEditConfig(): Component {
        val cfgMap = HashMap<String, Any>()
        val okButton = JButton("OK")
        val cancelButton = JButton("Cancel")
        val applyButton = JButton("Apply")

        okButton.addActionListener { e: ActionEvent? ->
            writeToConfig(cfgMap)
            dialog.dispose()
        }
        cancelButton.addActionListener { e: ActionEvent? -> dialog.dispose() }
        applyButton.addActionListener { e: ActionEvent? ->
            writeToConfig(cfgMap)
            applyButton.isEnabled = false
        }

        val editPanel = multiFrame(null, null)
        var desc = "Automatically insert newline at the end of object code (if missing):"
        var tt =
            "Editors like ZZT itself and KevEdit force a newline at the end of object code. Some programs malfunction without it, but if you know what you are doing and are desperate for space..."
        val autoInsertNewline = chkConfig("AUTO_INSERT_NEWLINE", cfgMap, desc, tt, applyButton)
        autoInsertNewline.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        var ctd = multiFrame(editPanel, autoInsertNewline)

        val grid = JPanel(GridLayout(1, 2))
        ctd = multiFrame(ctd, grid)
        val gridLeft = multiFrame(null, null)
        val gridRight = multiFrame(null, null)
        grid.add(gridLeft)
        grid.add(gridRight)
        gridLeft.border = BorderFactory.createEmptyBorder(0, 0, 0, 16)
        gridRight.border = BorderFactory.createEmptyBorder(0, 16, 0, 0)
        var l = gridLeft
        var r = gridRight

        desc = "Code editor default width:"
        tt = "Code editor default horizontal resolution (in pixels)"
        l = multiFrame(l, spinConfig("CODEEDITOR_WIDTH", cfgMap, desc, tt, applyButton))
        desc = "Code editor default height:"
        tt = "Code editor default vertical resolution (in pixels)"
        r = multiFrame(r, spinConfig("CODEEDITOR_HEIGHT", cfgMap, desc, tt, applyButton))
        desc = "Background colour:"
        tt = "Code editor normal background colour"
        l = multiFrame(l, colConfig("EDITOR_BG", cfgMap, desc, tt, applyButton))
        desc = "Background colour (music mode):"
        tt = "Code editor background colour used in music mode"
        r = multiFrame(r, colConfig("EDITOR_MUSIC_BG", cfgMap, desc, tt, applyButton))
        desc = "Background colour (bound):"
        tt = "Code editor background colour used for bound stats"
        l = multiFrame(l, colConfig("EDITOR_BIND_BG", cfgMap, desc, tt, applyButton))
        desc = "Selection colour:"
        tt = "Colour to use for the selection highlight"
        r = multiFrame(r, colConfig("EDITOR_SELECTION_COL", cfgMap, desc, tt, applyButton))
        desc = "Selected text colour:"
        tt = "Colour to use for the selected text"
        l = multiFrame(l, colConfig("EDITOR_SELECTED_TEXT_COL", cfgMap, desc, tt, applyButton))
        desc = "Cursor colour:"
        tt = "Colour to use for the code editor cursor"
        r = multiFrame(r, colConfig("EDITOR_CARET_COL", cfgMap, desc, tt, applyButton))

        desc = "Enable syntax highlighting of ZZT-OOP"
        tt =
            "Enables syntax highlighting. This includes indicating the length of #play statements, #char tooltips and long lines."
        val toggleSyntax = chkConfig("SYNTAX_HIGHLIGHTING", cfgMap, desc, tt, applyButton)
        toggleSyntax.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        ctd = multiFrame(ctd, toggleSyntax)

        val syntaxPanel = JPanel(BorderLayout())
        syntaxPanel.border = BorderFactory.createTitledBorder("Syntax highlighter colours")

        val syntax = arrayOf(
            "HL_HASH", "Command symbol",
            "HL_COMMAND", "Command",
            "HL_COLON", "Label symbol",
            "HL_LABEL", "Label",
            "HL_ZAPPED", "Zapped label symbol",
            "HL_ZAPPEDLABEL", "Zapped label",
            "HL_CONDITION", "Conditional",
            "HL_COUNTER", "Counter",
            "HL_DIRECTION", "Direction",
            "HL_EXCLAMATION", "Message box choice",
            "HL_FLAG", "Flag",
            "HL_GOTOLABEL", "Label destination",
            "HL_MUSICNOTE", "#play (notes)",
            "HL_MUSICDRUM", "#play (drums)",
            "HL_MUSICOCTAVE", "#play (octave changes)",
            "HL_MUSICREST", "#play (rests)",
            "HL_MUSICSHARPFLAT", "#play (sharps/flats)",
            "HL_MUSICTIMING", "#play (durations)",
            "HL_MUSICTIMINGMOD", "#play (triplets/dots)",
            "HL_NUMBER", "Integer literal",
            "HL_OBJECTLABELSEPARATOR", "Object-label separator",
            "HL_OBJECTNAME", "Object name",
            "HL_SLASH", "Movement symbol",
            "HL_TEXT", "Message text",
            "HL_DOLLAR", "Centered text symbol",
            "HL_CENTEREDTEXT", "Centered text",
            "HL_ERROR", "ZZT-OOP error",
            "HL_TEXTWARN", "Warning",
            "HL_TEXTWARNLIGHT", "Warning (mild)",
            "HL_NOOP", "Operation with no effect",
            "HL_THING", "Element type",
            "HL_BLUE", "Blue",
            "HL_GREEN", "Green",
            "HL_CYAN", "Cyan",
            "HL_RED", "Red",
            "HL_PURPLE", "Purple",
            "HL_YELLOW", "Yellow",
            "HL_WHITE", "White"
        )

        val syntaxPanelGrid = JPanel(GridLayout(0, 2))

        var i = 0
        while (i < syntax.size) {
            val cfgString = syntax[i]
            val cfgName = syntax[i + 1]
            val cfg = colConfig(cfgString, cfgMap, cfgName, cfgName, applyButton)
            cfg.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            syntaxPanelGrid.add(cfg)
            i += 2
        }
        val syntaxPanelScroll = JScrollPane(syntaxPanelGrid)
        syntaxPanelScroll.preferredSize = Dimension(300, 220)

        syntaxPanel.add(syntaxPanelScroll, BorderLayout.CENTER)
        ctd.add(syntaxPanel, BorderLayout.CENTER)

        return mainPanel(editPanel, okButton, cancelButton, applyButton)
    }

    private fun testConfig(): Component {
        val ge = editor.globalEditor
        val testChangeBoard =
            JCheckBox("Start the tested world on the current board",
                GlobalEditor.getBoolean("TEST_SWITCH_BOARD", false)
            )
        testChangeBoard.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        val configPanel = multiFrame(null, null)
        var configCtd = multiFrame(configPanel, testChangeBoard)

        val cfgMap = HashMap<String, Any>()

        val okButton = JButton("OK")
        val cancelButton = JButton("Cancel")
        val applyButton = JButton("Apply")

        testChangeBoard.addItemListener { e: ItemEvent? ->
            applyButton.isEnabled = true
            cfgMap["TEST_SWITCH_BOARD"] = testChangeBoard.isSelected
        }

        okButton.addActionListener { e: ActionEvent? ->
            writeToConfig(cfgMap)
            dialog.dispose()
        }
        cancelButton.addActionListener { e: ActionEvent? -> dialog.dispose() }
        applyButton.addActionListener { e: ActionEvent? ->
            writeToConfig(cfgMap)
            applyButton.isEnabled = false
        }
        val zztPanel = JPanel(GridLayout(0, 1))
        zztPanel.border = BorderFactory.createTitledBorder("ZZT world testing settings")
        var desc = "Test directory:"
        var tt =
            "Directory where ZZT and your ZZT engine (e.g. zeta) is found. This is also where the test files will be created, so ensure you have write access."
        zztPanel.add(dirConfig("ZZT_TEST_PATH", cfgMap, desc, tt, applyButton))
        desc = "Command to run:"
        tt = "This is the command that will be run to launch ZZT."
        zztPanel.add(textConfig("ZZT_TEST_COMMAND", cfgMap, desc, tt, applyButton))
        desc = "Command parameters:"
        tt = "Parameters to be passed to the command before the filename"
        zztPanel.add(textConfig("ZZT_TEST_PARAMS", cfgMap, desc, tt, applyButton))
        desc = "Base name:"
        tt =
            "The base name used for naming temporary .ZZT (and optionally .CHR and .PAL) files for testing. May be clipped to fit DOS filename limits."
        zztPanel.add(textConfig("ZZT_TEST_FILENAME", cfgMap, desc, tt, applyButton))
        desc = "P"
        tt =
            "Optionally inject the 'P' character after a delay. Useful in conjunction with Zeta '-t' argument to begin playing the test world automatically. May not be 100% reliable."
        zztPanel.add(injectConfig("ZZT_TEST_INJECT_P", cfgMap, desc, tt, applyButton))
        desc = "Use charset and palette, if modified"
        tt =
            "This appends Zeta charset and palette arguments to load whatever charset/palette the world is currently using if not the default."
        zztPanel.add(chkConfig("ZZT_TEST_USE_CHARPAL", cfgMap, desc, tt, applyButton))
        desc = "If blinking is disabled, pass -b"
        tt = "This appends Zeta's 'disable blinking' argument if blinking is disabled."
        zztPanel.add(chkConfig("ZZT_TEST_USE_BLINK", cfgMap, desc, tt, applyButton))

        configCtd = multiFrame(configCtd, zztPanel)

        val szztPanel = JPanel(GridLayout(0, 1))
        szztPanel.border = BorderFactory.createTitledBorder("Super ZZT world testing settings")
        desc = "Test directory:"
        tt =
            "Directory where Super ZZT and your ZZT engine (e.g. zeta) is found. This is also where the test files will be created, so ensure you have write access."
        szztPanel.add(dirConfig("SZZT_TEST_PATH", cfgMap, desc, tt, applyButton))
        desc = "Command to run:"
        tt = "This is the command that will be run to launch Super ZZT."
        szztPanel.add(textConfig("SZZT_TEST_COMMAND", cfgMap, desc, tt, applyButton))
        desc = "Command parameters:"
        tt = "Parameters to be passed to the command before the filename"
        szztPanel.add(textConfig("SZZT_TEST_PARAMS", cfgMap, desc, tt, applyButton))
        desc = "Base name:"
        tt =
            "The base name used for naming temporary .SZT (and optionally .CHR and .PAL) files for testing. May be clipped to fit DOS filename limits."
        szztPanel.add(textConfig("SZZT_TEST_FILENAME", cfgMap, desc, tt, applyButton))
        desc = "P"
        tt =
            "Optionally inject the 'P' character after a delay. Useful in conjunction with Zeta '-t' argument and the below option to begin playing the test world automatically. May not be 100% reliable."
        szztPanel.add(injectConfig("SZZT_TEST_INJECT_P", cfgMap, desc, tt, applyButton))
        desc = "Enter"
        tt =
            "Optionally inject the 'Enter' character after a further delay. Useful in conjunction with Zeta '-t' argument and the above option to begin playing the test world automatically. May not be 100% reliable."
        szztPanel.add(injectConfig("SZZT_TEST_INJECT_ENTER", cfgMap, desc, tt, applyButton))
        desc = "Use charset and palette, if modified"
        tt =
            "This appends Zeta charset and palette arguments to load whatever charset/palette the world is currently using if not the default."
        szztPanel.add(chkConfig("SZZT_TEST_USE_CHARPAL", cfgMap, desc, tt, applyButton))
        desc = "If blinking is disabled, pass -b"
        tt = "This appends Zeta's 'disable blinking' argument if blinking is disabled."
        szztPanel.add(chkConfig("SZZT_TEST_USE_BLINK", cfgMap, desc, tt, applyButton))
        configCtd = multiFrame(configCtd, szztPanel)

        return mainPanel(configPanel, okButton, cancelButton, applyButton)
    }

    private fun writeToConfig(cfgMap: HashMap<String, Any>) {
        val ge = editor.globalEditor
        for (cfgKey in cfgMap.keys) {
            val cfgVal = cfgMap[cfgKey]
            if (cfgVal is Int) GlobalEditor.setInt(cfgKey, (cfgVal as Int?)!!)
            else if (cfgVal is Boolean) GlobalEditor.setBoolean(cfgKey, (cfgVal as Boolean?)!!)
            else if (cfgVal is String) GlobalEditor.setString(cfgKey, (cfgVal as String?)!!)
            else throw RuntimeException("Invalid config field")
        }
        cfgMap.clear()
    }

    private fun colIcon(col: Color): Icon {
        val w = 48
        val h = 16
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.graphics
        g.color = col
        g.fillRect(0, 0, w, h)
        return ImageIcon(img)
    }

    private fun colConfig(
        cfgString: String,
        cfgMap: HashMap<String, Any>,
        desc: String,
        tt: String,
        ea: JButton
    ): JPanel {
        val ge = editor.globalEditor
        val panel = JPanel(BorderLayout())
        val label = JLabel(desc)
        label.border = BorderFactory.createEmptyBorder(0, 0, 0, 12)
        val btn = JButton()

        val col = Color(GlobalEditor.getString(cfgString, "000000").toInt(16))
        btn.icon = colIcon(col)
        btn.addActionListener { e: ActionEvent? ->
            val returnedCol = JColorChooser.showDialog(dialog, desc, col)
            if (returnedCol != null) {
                val colString = String.format("%06X", returnedCol.rgb and 0xFFFFFF)
                btn.icon = colIcon(returnedCol)
                cfgMap[cfgString] = colString
                ea.isEnabled = true
            }
        }

        panel.add(label, BorderLayout.WEST)
        val panel2 = JPanel(BorderLayout())
        panel2.add(btn, BorderLayout.EAST)
        panel.add(panel2, BorderLayout.CENTER)
        label.toolTipText = tt
        btn.toolTipText = tt
        return panel
    }

    private fun spinConfig(
        cfgString: String,
        cfgMap: HashMap<String, Any>,
        desc: String,
        tt: String,
        ea: JButton
    ): Component {
        val ge = editor.globalEditor
        val panel = JPanel(BorderLayout())
        val label = JLabel(desc)
        label.border = BorderFactory.createEmptyBorder(0, 0, 0, 12)
        val spin = JSpinner(SpinnerNumberModel(GlobalEditor.getInt(cfgString, 0), 1, 9999, 1))
        spin.addChangeListener { e: ChangeEvent? ->
            ea.isEnabled = true
            cfgMap[cfgString] = spin.value
        }
        panel.add(label, BorderLayout.WEST)
        val panel2 = JPanel(BorderLayout())
        panel2.add(spin, BorderLayout.EAST)
        panel.add(panel2, BorderLayout.CENTER)
        label.toolTipText = tt
        spin.toolTipText = tt
        return panel
    }

    private fun chkConfig(
        cfgString: String,
        cfgMap: HashMap<String, Any>,
        desc: String,
        tt: String,
        ea: JButton
    ): JPanel {
        val ge = editor.globalEditor
        val panel = JPanel(BorderLayout())

        val isSelected = GlobalEditor.getBoolean(cfgString, false)

        val lbl = JLabel(desc)
        lbl.border = BorderFactory.createEmptyBorder(0, 0, 0, 12)

        val a = JCheckBox()
        a.isSelected = isSelected
        a.border = BorderFactory.createEmptyBorder()
        a.addItemListener { e: ItemEvent? ->
            ea.isEnabled = true
            cfgMap[cfgString] = a.isSelected
        }

        lbl.toolTipText = tt
        a.toolTipText = tt

        panel.add(lbl, BorderLayout.WEST)
        panel.add(a, BorderLayout.CENTER)
        return panel
    }

    private fun textConfig(
        cfgString: String,
        cfgMap: HashMap<String, Any>,
        desc: String,
        tt: String,
        ea: JButton
    ): Component {
        val ge = editor.globalEditor
        val panel = JPanel(BorderLayout())
        val label = JLabel(desc)
        label.border = BorderFactory.createEmptyBorder(0, 0, 0, 12)
        val tf = JTextField(GlobalEditor.getString(cfgString, ""))
        tf.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                upd()
            }

            override fun removeUpdate(e: DocumentEvent) {
                upd()
            }

            override fun changedUpdate(e: DocumentEvent) {
                upd()
            }

            private fun upd() {
                ea.isEnabled = true
                cfgMap[cfgString] = tf.text
            }
        })
        panel.add(label, BorderLayout.WEST)
        panel.add(tf, BorderLayout.CENTER)
        label.toolTipText = tt
        tf.toolTipText = tt
        return panel
    }

    private fun dirConfig(
        cfgString: String,
        cfgMap: HashMap<String, Any>,
        desc: String,
        tt: String,
        ea: JButton
    ): Component {
        val ge = editor.globalEditor
        val panel = JPanel(BorderLayout())
        val label = JLabel(desc)
        label.border = BorderFactory.createEmptyBorder(0, 0, 0, 12)
        val path = GlobalEditor.getString(cfgString, "")
        val file = File(path)
        val dirOk = file.isDirectory
        val tf = JTextField(path)
        tf.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                upd()
            }

            override fun removeUpdate(e: DocumentEvent) {
                upd()
            }

            override fun changedUpdate(e: DocumentEvent) {
                upd()
            }

            private fun upd() {
                ea.isEnabled = true
                cfgMap[cfgString] = tf.text
            }
        })
        val chdir = JButton("\uD83D\uDCC2")
        chdir.addActionListener { e: ActionEvent? ->
            val fc = JFileChooser()
            fc.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            if (dirOk) {
                fc.currentDirectory = file
                fc.selectedFile = file
            } else {
                fc.currentDirectory = GlobalEditor.defaultDirectory
            }
            val r = fc.showOpenDialog(editor.frameForRelativePositioning)
            if (r == JFileChooser.APPROVE_OPTION) {
                tf.text = fc.selectedFile.toString()
            }
        }

        panel.add(label, BorderLayout.WEST)
        panel.add(tf, BorderLayout.CENTER)
        panel.add(chdir, BorderLayout.EAST)
        label.toolTipText = tt
        tf.toolTipText = tt
        return panel
    }

    private fun injectConfig(
        cfgString: String,
        cfgMap: HashMap<String, Any>,
        desc: String,
        tt: String,
        ea: JButton
    ): Component {
        val ge = editor.globalEditor
        val panel = JPanel(BorderLayout())

        val isSelected = GlobalEditor.getBoolean(cfgString, false)
        val delay = GlobalEditor.getInt(cfgString + "_DELAY", 0)

        val a = JCheckBox("After", isSelected)
        a.border = BorderFactory.createEmptyBorder()
        a.addItemListener { e: ItemEvent? ->
            ea.isEnabled = true
            cfgMap[cfgString] = a.isSelected
        }
        val b = JSpinner(SpinnerNumberModel(delay, 0, 999999, 10))
        //b.setBorder(BorderFactory.createEmptyBorder(1, 1, 1,1));
        b.addChangeListener { e: ChangeEvent? ->
            ea.isEnabled = true
            cfgMap[cfgString + "_DELAY"] = b.value
        }
        val c = JLabel(String.format("milliseconds, inject '%s' into the input stream", desc))
        a.toolTipText = tt
        b.toolTipText = tt
        c.toolTipText = tt

        panel.add(a, BorderLayout.WEST)
        val mid = JPanel(BorderLayout())
        mid.add(b, BorderLayout.WEST)
        panel.add(mid, BorderLayout.CENTER)
        val right = JPanel(BorderLayout())
        right.add(c, BorderLayout.WEST)
        mid.add(right, BorderLayout.CENTER)

        return panel
    }

    private fun multiFrame(panel: JPanel?, addTo: Component?): JPanel {
        if (panel == null) return JPanel(BorderLayout())
        panel.add(addTo, BorderLayout.NORTH)
        val extra = JPanel(BorderLayout())
        panel.add(extra, BorderLayout.CENTER)
        return extra
    }

    private fun mainPanel(
        contentPanel: Component,
        okButton: JButton,
        cancelButton: JButton,
        applyButton: JButton
    ): Component {
        val emptyList = ArrayList<JButton>()
        return mainPanel(contentPanel, okButton, cancelButton, applyButton, emptyList)
    }

    private fun mainPanel(
        contentPanel: Component,
        okButton: JButton,
        cancelButton: JButton,
        applyButton: JButton,
        extraButtons: ArrayList<JButton>
    ): Component {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        mainPanel.add(contentPanel, BorderLayout.CENTER)
        val buttonPanel = JPanel(BorderLayout())
        buttonPanel.add(JPanel(), BorderLayout.CENTER)
        val buttonGrid = JPanel(GridLayout(1, 3))
        buttonGrid.border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        buttonPanel.add(buttonGrid, BorderLayout.EAST)

        val extraButtonGrid = JPanel(GridLayout(1, 0))
        buttonPanel.add(extraButtonGrid, BorderLayout.WEST)
        extraButtonGrid.border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        for (button in extraButtons) {
            extraButtonGrid.add(button)
        }

        buttonGrid.add(okButton)
        buttonGrid.add(cancelButton)
        buttonGrid.add(applyButton)
        okButton.isEnabled = true
        cancelButton.isEnabled = true
        applyButton.isEnabled = false
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        return mainPanel
    }

    private fun keystrokeConfig(): Component {
        val panel = JPanel(GridLayout(0, 2))
        panel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        val scrollPane = JScrollPane(panel)
        scrollPane.preferredSize = Dimension(540, 360)

        val keymappings = arrayOf(
            "Escape", "Cancel operation / Close editor",
            "Up", "Move cursor up",
            "Down", "Move cursor down",
            "Left", "Move cursor left",
            "Right", "Move cursor right",
            "Alt-Up", "Move cursor up 10 tiles",
            "Alt-Down", "Move cursor down 10 tiles",
            "Alt-Left", "Move cursor left 10 tiles",
            "Alt-Right", "Move cursor right 10 tiles",
            "Shift-Up", "Switch to board at north exit",
            "Shift-Down", "Switch to board at south exit",
            "Shift-Left", "Switch to board at west exit",
            "Shift-Right", "Switch to board at east exit",
            "Ctrl-Shift-Up", "Create board at north exit",
            "Ctrl-Shift-Down", "Create board at south exit",
            "Ctrl-Shift-Left", "Create board at west exit",
            "Ctrl-Shift-Right", "Create board at east exit",
            "Tab", "Toggle drawing mode",
            "Home", "Move cursor to top-left",
            "End", "Move cursor to bottom-right",
            "PgUp", "Select previous stat (in tile editor)",
            "PgDn", "Select next stat (in tile editor)",
            "Insert", "Store in buffer",
            "Space", "Place from buffer to cursor location",
            "Delete", "Delete tile at cursor",
            "Enter", "Grab and modify tile at cursor / Finish marking block",
            "Ctrl-Enter", "Grab and modify tile at cursor (advanced)",
            "Ctrl-=", "Zoom in",
            "Ctrl--", "Zoom out",
            "A", "Add board",
            "B", "Switch board",
            "C", "Select colour",
            "D", "Delete board",
            "F", "Flood-fill",
            "G", "World settings",
            "I", "Board settings",
            "L", "Load world",
            "P", "Modify tile in buffer",
            "S", "Save as",
            "X", "Board exits",
            "Ctrl-A", "Create atlas / remove current atlas",
            "Ctrl-B", "Open buffer manager",
            "Ctrl-D", "Duplicate line",
            "Ctrl-E", "Erase player from board",
            "Ctrl-F", "Find",
            "Ctrl-H", "Replace",
            "Ctrl-S", "Save",
            "Ctrl-P", "Modify tile in buffer (advanced)",
            "Ctrl-R", "Remove current board from atlas",
            "Ctrl-V", "Import image from clipboard",
            "Ctrl-X", "Exchange buffer's fg/bg colours",
            "Ctrl-Y", "Redo",
            "Ctrl-Z", "Undo",
            "Ctrl--", "Transpose down (in code editor)",
            "Ctrl-=", "Transpose up (in code editor)",
            "Alt-B", "Begin block operation",
            "Alt-F", "Gradient fill",
            "Alt-I", "Import board",  //"Alt-M", "Modify tile under cursor / Music mode (in code editor)",
            "Alt-M", "Modify tile under cursor",
            "Alt-S", "Stat list",
            "Alt-T", "Test world",
            "Alt-X", "Export board",
            "Shift-B", "Board list",
            "Ctrl-Alt-M", "Modify tile under cursor (advanced)",
            "F1", "Help",
            "F2", "Enter text",
            "F3", "Access F3 elements menu, insert char in code editor",
            "F4", "Access F4 elements menu",
            "F5", "Access F5 elements menu",
            "F6", "Access F6 elements menu",
            "F7", "Access F7 elements menu",
            "F8", "Access F8 elements menu",
            "F9", "Access F9 elements menu",
            "F10", "Access F10 elements menu",
            "F12", "Take screenshot (to disk)",
            "Alt-F12", "Take screenshot (to clipboard)",
            "Shift-F1", "Show stats",
            "Shift-F2", "Show objects",
            "Shift-F3", "Show invisibles",
            "Shift-F4", "Show empties",
            "Shift-F5", "Show fakes",
            "Shift-F6", "Show empties as text",
            "0", "Load from buffer slot 0",
            "1", "Load from buffer slot 1",
            "2", "Load from buffer slot 2",
            "3", "Load from buffer slot 3",
            "4", "Load from buffer slot 4",
            "5", "Load from buffer slot 5",
            "6", "Load from buffer slot 6",
            "7", "Load from buffer slot 7",
            "8", "Load from buffer slot 8",
            "9", "Load from buffer slot 9",
            "Ctrl-0", "Store in buffer slot 0",
            "Ctrl-1", "Store in buffer slot 1",
            "Ctrl-2", "Store in buffer slot 2",
            "Ctrl-3", "Store in buffer slot 3",
            "Ctrl-4", "Store in buffer slot 4",
            "Ctrl-5", "Store in buffer slot 5",
            "Ctrl-6", "Store in buffer slot 6",
            "Ctrl-7", "Store in buffer slot 7",
            "Ctrl-8", "Store in buffer slot 8",
            "Ctrl-9", "Store in buffer slot 9",
            "COMMA", "Move stat up in stats table",
            "PERIOD", "Move stat down in stats table",
        )
        val ge = editor.globalEditor

        val keyMap = HashMap<String, KeyStroke?>()
        run {
            var i = 0
            while (i < keymappings.size) {
                val actionName = keymappings[i]
                val keyStroke = Util.getKeyStroke(ge, actionName)
                keyMap[actionName] = keyStroke
                i += 2
            }
        }

        val okButton = JButton("OK")
        val resetButton = JButton("Reset")
        val cancelButton = JButton("Cancel")
        val applyButton = JButton("Apply")
        okButton.addActionListener { e: ActionEvent? ->
            applyKeymap(keyMap)
            dialog.dispose()
            resetButton.isEnabled = false
            applyButton.isEnabled = false
        }
        cancelButton.addActionListener { e: ActionEvent? -> dialog.dispose() }
        applyButton.addActionListener { e: ActionEvent? ->
            applyKeymap(keyMap)
            resetButton.isEnabled = false
            applyButton.isEnabled = false
        }

        val btnFocus: FocusListener = object : FocusListener {
            var storedBg: Color = Color.MAGENTA
            override fun focusGained(e: FocusEvent) {
                val tf = e.source as JTextField
                storedBg = tf.background
                tf.background = Color.WHITE
            }

            override fun focusLost(e: FocusEvent) {
                val tf = e.source as JTextField
                tf.background = storedBg
            }
        }

        var i = 0
        while (i < keymappings.size) {
            val actionName = keymappings[i]
            val keyDescription = keymappings[i + 1]
            val keyDescriptionLabel = JLabel(keyDescription)
            val keyStrokeName = Util.keyStrokeString(Util.getKeyStroke(ge, actionName))
            val keyBind = JPanel(BorderLayout())
            val clearButton = JButton("Clear")

            val keyBinder = JTextField(keyStrokeName)
            clearButton.addActionListener { e: ActionEvent? ->
                keystrokeSet(actionName, null, keyBinder, keyMap)
                resetButton.isEnabled = true
                applyButton.isEnabled = true
            }
            val keyListener: KeyListener = object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    var mods = e.modifiersEx
                    mods = mods and InputEvent.ALT_GRAPH_DOWN_MASK.inv()
                    val ks = KeyStroke.getKeyStroke(e.keyCode, mods)
                    resetButton.isEnabled = true
                    applyButton.isEnabled = true
                    keystrokeSet(actionName, ks, keyBinder, keyMap)
                    e.consume()
                }
            }

            keyBinder.isEditable = false
            keyBinder.addKeyListener(keyListener)
            keyBinder.isFocusable = true
            keyBinder.addFocusListener(btnFocus)

            keyBind.add(keyBinder, BorderLayout.CENTER)
            keyBind.add(clearButton, BorderLayout.EAST)

            panel.add(keyDescriptionLabel)
            panel.add(keyBind)
            i += 2
        }

        return mainPanel(scrollPane, okButton, cancelButton, applyButton)
    }

    private fun applyKeymap(keyMap: HashMap<String, KeyStroke?>) {
        val ge = editor.globalEditor
        for (actionName in keyMap.keys) {
            val keyStrokeString = keyMap[actionName]
            Util.setKeyStroke(ge, actionName, keyStrokeString)
        }
        editor.refreshKeymapping()
    }

    private fun keystrokeSet(
        actionName: String,
        keyStroke: KeyStroke?,
        keyBinder: JTextField,
        keyMap: HashMap<String, KeyStroke?>
    ) {
        for (ks in keyMap.values) {
            if (ks != null && ks == keyStroke) return
        }

        val keyStrokeString = Util.keyStrokeString(keyStroke)
        keyBinder.text = keyStrokeString
        keyMap[actionName] = keyStroke
    }

    companion object {
        private val REVERSE_EXITS = intArrayOf(1, 0, 3, 2)
        private val preset_KevEdit = arrayOf(
            arrayOf(
                "Items",
                "Player(Cycle=1,StatId=0,IsPlayer=true)",
                "Ammo",
                "Torch",
                "Gem",
                "Key",
                "Door",
                "Scroll()",
                "Passage(Cycle=0)",
                "Duplicator(Cycle=2)",
                "Bomb(Cycle=6)",
                "Energizer",
                "Clockwise(Cycle=3)",
                "Counter(Cycle=2)"
            ),
            arrayOf(
                "Creatures",
                "Bear(Cycle=3)",
                "Ruffian(Cycle=1)",
                "Object(Cycle=3,P1=1)",
                "Slime(Cycle=3)",
                "Shark(Cycle=3)",
                "SpinningGun(Cycle=2)",
                "Pusher(Cycle=4)",
                "Lion(Cycle=2)",
                "Tiger(Cycle=2)",
                "Bullet()",
                "Star()",
                "Head(Cycle=2)",
                "Segment(Cycle=2)"
            ),
            arrayOf(
                "Terrain",
                "Water",
                "Forest",
                "Solid",
                "Normal",
                "Breakable",
                "Boulder",
                "SliderNS",
                "SliderEW",
                "Fake",
                "Invisible",
                "BlinkWall()",
                "Transporter(Cycle=2)",
                "Ricochet",
                "BoardEdge",
                "Monitor()",
                "HBlinkRay",
                "VBlinkRay",
                "Player!Dead Smiley"
            ),
            arrayOf(
                "Others",
                "Empty",
                "Floor",
                "Lava",
                "Web",
                "Line",
                "Stone(Cycle=1)",
                "Roton(Cycle=1)",
                "DragonPup(Cycle=2)",
                "Pairer(Cycle=2)",
                "Spider(Cycle=1)",
                "WaterN",
                "WaterS",
                "WaterE",
                "WaterW",
                "Messenger()"
            ),
            arrayOf(""),
            arrayOf(""),
            arrayOf(""),
            arrayOf("")
        )
        val preset_ZEdit: Array<Array<String>> = arrayOf(
            arrayOf(
                "Terrain",
                "Empty",
                "Solid",
                "Normal",
                "Breakable",
                "Water",
                "Floor",
                "Fake",
                "Invisible",
                "Line",
                "Forest",
                "Web",
                "Lava"
            ),
            arrayOf("Items", "Ammo", "Torch", "Gem", "Key", "Door", "Energizer", "Stone(Cycle=1)"),
            arrayOf(
                "Creatures",
                "Bear(Cycle=3)",
                "Ruffian(Cycle=1)",
                "Slime(Cycle=3)",
                "Shark(Cycle=3)",
                "SpinningGun(Cycle=2)",
                "Lion(Cycle=2)",
                "Tiger(Cycle=2)",
                "Head(Cycle=2)",
                "Segment(Cycle=2)",
                "Roton(Cycle=1)",
                "DragonPup(Cycle=2)",
                "Pairer(Cycle=2)",
                "Spider(Cycle=1)"
            ),
            arrayOf(
                "Puzzle pieces",
                "Boulder",
                "SliderNS",
                "SliderEW",
                "Ricochet",
                "Pusher(Cycle=4)",
                "Duplicator(Cycle=2)",
                "Bomb(Cycle=6)",
                "BlinkWall()"
            ),
            arrayOf(
                "Transport",
                "Passage(Cycle=0)",
                "Transporter(Cycle=2)",
                "Clockwise(Cycle=3)",
                "Counter(Cycle=2)",
                "WaterN",
                "WaterS",
                "WaterE",
                "WaterW",
                "BoardEdge"
            ),
            arrayOf(
                "Text",
                "CustomText!Custom Text",
                "BlackText!Black Text",
                "BlueText!Blue Text",
                "GreenText!Green Text",
                "CyanText!Cyan Text",
                "RedText!Red Text",
                "PurpleText!Purple Text",
                "BrownText!Brown Text"
            ),
            arrayOf("Miscellaneous", "Messenger()", "Monitor()", "HBlinkRay", "VBlinkRay"),
            arrayOf(
                "Objects",
                "Object(Cycle=1,P1=1)",
                "Player(Cycle=1,StatId=0,IsPlayer=true)",
                "Player(Cycle=1)Player Clone",
                "Scroll()",
                "Star()",
                "Bullet()"
            )
        )
        private val preset_ZZT = arrayOf(
            arrayOf(
                "Items",
                "Player(Cycle=1,StatId=0,IsPlayer=true)",
                "Ammo",
                "Torch",
                "Gem",
                "Key",
                "Door",
                "Scroll()",
                "Passage(Cycle=0)",
                "Duplicator(Cycle=2)",
                "Bomb(Cycle=6)",
                "Energizer",
                "Clockwise(Cycle=3)",
                "Counter(Cycle=2)"
            ),
            arrayOf(
                "Creatures",
                "Bear(Cycle=3)",
                "Ruffian(Cycle=1)",
                "Object(Cycle=3,P1=1)",
                "Slime(Cycle=3)",
                "Shark(Cycle=3)",
                "SpinningGun(Cycle=2)",
                "Pusher(Cycle=4)",
                "Lion(Cycle=2)",
                "Tiger(Cycle=2)",
                "Head(Cycle=2)",
                "Segment(Cycle=2)"
            ),
            arrayOf(
                "Terrain",
                "Water",
                "Lava",
                "Forest",
                "Solid",
                "Normal",
                "Breakable",
                "Boulder",
                "SliderNS",
                "SliderEW",
                "Fake",
                "Invisible",
                "BlinkWall()",
                "Transporter(Cycle=2)",
                "Ricochet"
            ),
            arrayOf("Uglies (SZZT)", "Roton(Cycle=1)", "DragonPup(Cycle=2)", "Pairer(Cycle=2)", "Spider(Cycle=1)"),
            arrayOf("Terrain (SZZT)", "Floor", "WaterN", "WaterS", "WaterW", "WaterE", "Web", "Stone(Cycle=1)"),
            arrayOf(
                "Others",
                "Empty",
                "Line",
                "BoardEdge",
                "Messenger()",
                "Monitor()",
                "HBlinkRay",
                "VBlinkRay",
                "Player(Cycle=1)Player Clone",
                "Star()",
                "Bullet()"
            ),
            arrayOf(""),
            arrayOf("")
        )

        @JvmStatic
        fun board(frame: JFrame?, currentBoard: Board?, worldData: WorldData) {
            if (currentBoard == null) return

            val settings = JDialog()
            Util.addEscClose(settings, settings.rootPane)
            Util.addKeyClose(settings, settings.rootPane, KeyEvent.VK_ENTER, 0)
            settings.isResizable = false
            settings.title = "Board Settings"
            settings.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            settings.modalityType = ModalityType.APPLICATION_MODAL
            settings.contentPane.layout = BorderLayout()
            val cp = JPanel(GridLayout(0, 1))
            cp.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            settings.contentPane.add(cp, BorderLayout.CENTER)

            val boardNameLimit = if (currentBoard.isSuperZZT) 60 else 50
            val boardNameLabel = JLabel()
            boardNameLabel.text = "Board name: " + currentBoard.getName().size + "/" + boardNameLimit

            cp.add(boardNameLabel)
            val boardNameField = JTextField(toUnicode(currentBoard.getName()))
            boardNameField.font = font
            boardNameField.toolTipText = "Board name"

            (boardNameField.document as AbstractDocument).documentFilter = LimitDocFilter(boardNameLimit)
            boardNameField.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    upd()
                }

                override fun removeUpdate(e: DocumentEvent) {
                    upd()
                }

                override fun changedUpdate(e: DocumentEvent) {
                    upd()
                }

                private fun upd() {
                    currentBoard.setName(toBytes(boardNameField.text))
                    boardNameLabel.text = "Board name: " + currentBoard.getName().size + "/" + boardNameLimit
                }
            })
            cp.add(boardNameField)

            val shotsPanel = JPanel(BorderLayout())
            val shotsSpinner = JSpinner(SpinnerNumberModel(currentBoard.getShots(), 0, 255, 1))
            shotsPanel.add(JLabel("Max player shots:    "), BorderLayout.WEST)
            shotsPanel.add(shotsSpinner, BorderLayout.EAST)
            shotsSpinner.addChangeListener { e: ChangeEvent? ->
                currentBoard.setShots(
                    (shotsSpinner.value as Int)
                )
            }
            cp.add(shotsPanel)

            val timePanel = JPanel(BorderLayout())
            val timeSpinner = JSpinner(SpinnerNumberModel(currentBoard.getTimeLimit(), -32768, 32767, 1))
            timePanel.add(JLabel("Time limit (secs):    "), BorderLayout.WEST)
            timePanel.add(timeSpinner, BorderLayout.EAST)
            timeSpinner.addChangeListener { e: ChangeEvent? ->
                currentBoard.setTimeLimit(
                    (timeSpinner.value as Int)
                )
            }
            cp.add(timePanel)

            val xPanel = JPanel(BorderLayout())
            val xSpinner = JSpinner(SpinnerNumberModel(currentBoard.getPlayerX(), 0, 255, 1))
            xPanel.add(JLabel("Player entry X:    "), BorderLayout.WEST)
            xPanel.add(xSpinner, BorderLayout.EAST)
            xSpinner.addChangeListener { e: ChangeEvent? -> currentBoard.setPlayerX((xSpinner.value as Int)) }
            cp.add(xPanel)

            val yPanel = JPanel(BorderLayout())
            val ySpinner = JSpinner(SpinnerNumberModel(currentBoard.getPlayerY(), 0, 255, 1))
            yPanel.add(JLabel("Player entry Y:    "), BorderLayout.WEST)
            yPanel.add(ySpinner, BorderLayout.EAST)
            ySpinner.addChangeListener { e: ChangeEvent? -> currentBoard.setPlayerY((ySpinner.value as Int)) }
            cp.add(yPanel)

            if (!worldData.isSuperZZT) {
                val darkPanel = JPanel(BorderLayout())
                val darkBox = JCheckBox()
                darkBox.isSelected = currentBoard.isDark
                darkPanel.add(JLabel("Dark:"), BorderLayout.WEST)
                darkPanel.add(darkBox, BorderLayout.EAST)
                darkBox.addChangeListener { e: ChangeEvent? -> currentBoard.isDark = darkBox.isSelected }
                cp.add(darkPanel)
            } else {
                val cxPanel = JPanel(BorderLayout())
                val cxSpinner = JSpinner(SpinnerNumberModel(currentBoard.cameraX, -32768, 32767, 1))
                cxPanel.add(JLabel("Camera X:    "), BorderLayout.WEST)
                cxPanel.add(cxSpinner, BorderLayout.EAST)
                cxSpinner.addChangeListener { e: ChangeEvent? -> currentBoard.cameraX = (cxSpinner.value as Int) }
                cp.add(cxPanel)

                val cyPanel = JPanel(BorderLayout())
                val cySpinner = JSpinner(SpinnerNumberModel(currentBoard.cameraY, -32768, 32767, 1))
                cyPanel.add(JLabel("Camera Y:    "), BorderLayout.WEST)
                cyPanel.add(cySpinner, BorderLayout.EAST)
                cySpinner.addChangeListener { e: ChangeEvent? -> currentBoard.cameraY = (cySpinner.value as Int) }
                cp.add(cyPanel)
            }

            val restartPanel = JPanel(BorderLayout())
            val restartBox = JCheckBox()
            restartBox.isSelected = currentBoard.isRestartOnZap()
            restartPanel.add(JLabel("Restart if hurt:"), BorderLayout.WEST)
            restartPanel.add(restartBox, BorderLayout.EAST)
            restartBox.addChangeListener { e: ChangeEvent? -> currentBoard.setRestartOnZap(restartBox.isSelected) }
            cp.add(restartPanel)

            settings.pack()
            settings.setLocationRelativeTo(frame)
            settings.isVisible = true
        }

        fun boardExits(frame: JFrame?, currentBoard: Board?, boards: List<Board>, currentBoardIdx: Int) {
            if (currentBoard == null) return

            val settings = JDialog()
            Util.addEscClose(settings, settings.rootPane)
            Util.addKeyClose(settings, settings.rootPane, KeyEvent.VK_ENTER, 0)
            settings.isResizable = false
            settings.title = "Board Exits"
            settings.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            settings.modalityType = ModalityType.APPLICATION_MODAL
            settings.contentPane.layout = GridLayout(0, 1)

            val boardNames = generateBoardSelectArray(boards, false)

            for (exit in intArrayOf(0, 1, 3, 2)) {
                val exitName = BoardManager.EXIT_NAMES[exit]
                val checkbox = JCheckBox("Reciprocated")
                val boardPanel = JPanel(BorderLayout())
                //exitPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
                boardPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
                val exitLabel = JLabel("Exit to $exitName:")
                exitLabel.verticalAlignment = SwingConstants.BOTTOM
                val boardSelect = JComboBox(boardNames)
                val exitBoard = currentBoard.getExit(exit)
                reciprocalCheckboxStatus(checkbox, boards, exitBoard, exit, currentBoardIdx)
                if (exitBoard < boards.size) {
                    boardSelect.setSelectedIndex(exitBoard)
                } else {
                    boardSelect.isEditable = true
                    boardSelect.editor.item = String.format("(invalid reference: %d)", exitBoard)
                }
                boardSelect.font = font
                boardSelect.addActionListener { e: ActionEvent? ->
                    var newDestination = boardSelect.selectedIndex
                    if (newDestination != -1) {
                        currentBoard.setExit(exit, newDestination)
                        reciprocalCheckboxStatus(checkbox, boards, newDestination, exit, currentBoardIdx)
                    } else {
                        try {
                            newDestination = (boardSelect.editor.item as String).toInt()
                            if (newDestination >= 0 && newDestination <= 255) {
                                currentBoard.setExit(exit, newDestination)
                                reciprocalCheckboxStatus(checkbox, boards, newDestination, exit, currentBoardIdx)
                            }
                        } catch (ignored: NumberFormatException) {
                        }
                    }
                }

                boardPanel.add(exitLabel, BorderLayout.NORTH)
                boardPanel.add(boardSelect, BorderLayout.CENTER)

                checkbox.toolTipText = "Links the destination board to this one in the opposite direction"
                boardPanel.add(checkbox, BorderLayout.SOUTH)

                settings.contentPane.add(boardPanel)
            }

            settings.pack()
            settings.setLocationRelativeTo(frame)
            settings.isVisible = true
        }

        private fun setReciprocated(selected: Boolean, otherBoard: Board, exit: Int, currentBoardIdx: Int) {
            val re = REVERSE_EXITS[exit]
            if (selected) {
                otherBoard.setExit(re, currentBoardIdx)
            } else {
                if (otherBoard.getExit(re) == currentBoardIdx) {
                    otherBoard.setExit(re, 0)
                }
            }
        }

        private fun reciprocalCheckboxStatus(
            checkbox: JCheckBox, boards: List<Board>,
            exitBoard: Int, exit: Int, boardIdx: Int
        ) {
            while (checkbox.changeListeners.size > 0) {
                checkbox.removeChangeListener(checkbox.changeListeners[0])
            }
            if (exitBoard == 0 || boardIdx == 0 || exitBoard >= boards.size) {
                checkbox.isEnabled = false
            } else {
                checkbox.isEnabled = true
                val isReciprocated = boards[exitBoard].getExit(REVERSE_EXITS[exit]) == boardIdx
                checkbox.isSelected = isReciprocated

                checkbox.addChangeListener { e: ChangeEvent? ->
                    setReciprocated(
                        checkbox.isSelected,
                        boards[exitBoard],
                        exit,
                        boardIdx
                    )
                }
            }
        }


        fun world(frame: JFrame?, boards: List<Board>, worldData: WorldData, canvas: DosCanvas) {
            val settings = JDialog()
            Util.addEscClose(settings, settings.rootPane)
            Util.addKeyClose(settings, settings.rootPane, KeyEvent.VK_ENTER, 0)
            settings.isResizable = false
            settings.title = "World Settings"
            settings.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            settings.modalityType = ModalityType.APPLICATION_MODAL
            settings.contentPane.layout = BorderLayout()

            val topPanel = JPanel(GridLayout(2, 1))
            topPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            val startingBoardPanel = JPanel(BorderLayout())
            val startingBoardLabel = JLabel("Starting board:   ")
            val boardNames = arrayOfNulls<String>(boards.size)
            for (i in boards.indices) {
                boardNames[i] = toUnicode(boards[i].getName())
            }

            val startingBoardDropdown = JComboBox(boardNames)
            startingBoardDropdown.font = font
            startingBoardDropdown.toolTipText = "Select the starting board (or current board, for a saved game)"
            if (worldData.currentBoard < boards.size) {
                startingBoardDropdown.selectedIndex = worldData.currentBoard
            }
            startingBoardDropdown.addActionListener { e: ActionEvent? ->
                worldData.currentBoard = startingBoardDropdown.selectedIndex
            }

            val worldNamePanel = JPanel(BorderLayout())
            val worldNameLabel = JLabel("World name:   ")
            val worldNameBox = JTextField(toUnicode(worldData.name))
            worldNameBox.font = font
            worldNameBox.toolTipText =
                "This world name field is used to reload the world after exiting.\nNormally it should be the same as the filename."
            worldNameBox.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    upd()
                }

                override fun removeUpdate(e: DocumentEvent) {
                    upd()
                }

                override fun changedUpdate(e: DocumentEvent) {
                    upd()
                }

                private fun upd() {
                    worldData.name = toBytes(worldNameBox.text)
                }
            })

            startingBoardPanel.add(startingBoardLabel, BorderLayout.WEST)
            startingBoardPanel.add(startingBoardDropdown, BorderLayout.CENTER)
            worldNamePanel.add(worldNameLabel, BorderLayout.WEST)
            worldNamePanel.add(worldNameBox, BorderLayout.CENTER)
            topPanel.add(startingBoardPanel)
            topPanel.add(worldNamePanel)
            settings.contentPane.add(topPanel, BorderLayout.NORTH)

            val eastPanel = JPanel(GridLayout(0, 1))
            eastPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            for (i in 0 until worldData.numFlags) {
                val flagNum = i
                val flagPanel = JPanel(BorderLayout())
                val flagLabel = JLabel(String.format("Flag #%d:", flagNum))
                val flagBox = JTextField(toUnicode(worldData.getFlag(flagNum)))
                flagLabel.preferredSize = Dimension(50, flagLabel.preferredSize.height)
                flagBox.preferredSize = Dimension(100, flagBox.preferredSize.height)
                flagBox.font = font
                flagBox.toolTipText =
                    "Edit flag #$flagNum (note: ZZT can only read/write flags with the characters A-Z 0-9 : and _)"
                flagBox.document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent) {
                        upd()
                    }

                    override fun removeUpdate(e: DocumentEvent) {
                        upd()
                    }

                    override fun changedUpdate(e: DocumentEvent) {
                        upd()
                    }

                    private fun upd() {
                        worldData.setFlag(flagNum, toBytes(flagBox.text))
                    }
                })

                flagPanel.add(flagLabel, BorderLayout.WEST)
                flagPanel.add(flagBox, BorderLayout.EAST)
                eastPanel.add(flagPanel)
            }
            settings.contentPane.add(eastPanel, BorderLayout.EAST)

            val westPanel = JPanel(GridLayout(0, 1))
            westPanel.border = BorderFactory.createEmptyBorder(4, 8, 8, 8)

            val healthPanel = JPanel(BorderLayout())
            val healthSpinner = JSpinner(SpinnerNumberModel(worldData.health, -32768, 32767, 1))
            healthPanel.add(JLabel("Health:"), BorderLayout.WEST)
            healthPanel.add(healthSpinner, BorderLayout.EAST)
            healthSpinner.addChangeListener { e: ChangeEvent? -> worldData.health = (healthSpinner.value as Int) }
            westPanel.add(healthPanel)

            val ammoPanel = JPanel(BorderLayout())
            val ammoSpinner = JSpinner(SpinnerNumberModel(worldData.ammo, -32768, 32767, 1))
            ammoPanel.add(JLabel("Ammo:"), BorderLayout.WEST)
            ammoPanel.add(ammoSpinner, BorderLayout.EAST)
            ammoSpinner.addChangeListener { e: ChangeEvent? -> worldData.ammo = (ammoSpinner.value as Int) }
            westPanel.add(ammoPanel)

            val gemsPanel = JPanel(BorderLayout())
            val gemsSpinner = JSpinner(SpinnerNumberModel(worldData.gems, -32768, 32767, 1))
            gemsPanel.add(JLabel("Gems:"), BorderLayout.WEST)
            gemsPanel.add(gemsSpinner, BorderLayout.EAST)
            gemsSpinner.addChangeListener { e: ChangeEvent? -> worldData.gems = (gemsSpinner.value as Int) }
            westPanel.add(gemsPanel)

            val scorePanel = JPanel(BorderLayout())
            val scoreSpinner = JSpinner(SpinnerNumberModel(worldData.score, -32768, 32767, 1))
            scorePanel.add(JLabel("Score:"), BorderLayout.WEST)
            scorePanel.add(scoreSpinner, BorderLayout.EAST)
            scoreSpinner.addChangeListener { e: ChangeEvent? -> worldData.score = (scoreSpinner.value as Int) }
            westPanel.add(scorePanel)

            if (!worldData.isSuperZZT) {
                val torchesPanel = JPanel(BorderLayout())
                val torchesSpinner = JSpinner(SpinnerNumberModel(worldData.torches, -32768, 32767, 1))
                torchesPanel.add(JLabel("Torches:"), BorderLayout.WEST)
                torchesPanel.add(torchesSpinner, BorderLayout.EAST)
                torchesSpinner.addChangeListener { e: ChangeEvent? ->
                    worldData.torches = (torchesSpinner.value as Int)
                }
                westPanel.add(torchesPanel)

                val torchTimerPanel = JPanel(BorderLayout())
                val torchTimerSpinner = JSpinner(SpinnerNumberModel(worldData.torchTimer, -32768, 32767, 1))
                torchTimerPanel.add(JLabel("Torch cycles:"), BorderLayout.WEST)
                torchTimerPanel.add(torchTimerSpinner, BorderLayout.EAST)
                torchTimerSpinner.addChangeListener { e: ChangeEvent? ->
                    worldData.torchTimer = (torchTimerSpinner.value as Int)
                }
                westPanel.add(torchTimerPanel)
            } else {
                val zPanel = JPanel(BorderLayout())
                val zSpinner = JSpinner(SpinnerNumberModel(worldData.z, -32768, 32767, 1))
                zPanel.add(JLabel("Z:"), BorderLayout.WEST)
                zPanel.add(zSpinner, BorderLayout.EAST)
                zSpinner.addChangeListener { e: ChangeEvent? -> worldData.z = (zSpinner.value as Int) }
                westPanel.add(zPanel)
            }

            val energiserPanel = JPanel(BorderLayout())
            val energiserSpinner = JSpinner(SpinnerNumberModel(worldData.energiser, -32768, 32767, 1))
            energiserPanel.add(JLabel("Energiser:"), BorderLayout.WEST)
            energiserPanel.add(energiserSpinner, BorderLayout.EAST)
            energiserSpinner.addChangeListener { e: ChangeEvent? ->
                worldData.energiser = (energiserSpinner.value as Int)
            }
            westPanel.add(energiserPanel)

            val timePanel = JPanel(BorderLayout())
            val timeSpinner = JSpinner(SpinnerNumberModel(worldData.timeSeconds, -32768, 32767, 1))
            timePanel.add(JLabel("Time elapsed:  "), BorderLayout.WEST)
            timePanel.add(timeSpinner, BorderLayout.EAST)
            timeSpinner.addChangeListener { e: ChangeEvent? -> worldData.timeSeconds = (timeSpinner.value as Int) }
            westPanel.add(timePanel)

            val subsecsPanel = JPanel(BorderLayout())
            val subsecsSpinner = JSpinner(SpinnerNumberModel(worldData.timeTicks, -32768, 32767, 1))
            subsecsPanel.add(JLabel("Subseconds:"), BorderLayout.WEST)
            subsecsPanel.add(subsecsSpinner, BorderLayout.EAST)
            subsecsSpinner.addChangeListener { e: ChangeEvent? -> worldData.timeTicks = (subsecsSpinner.value as Int) }
            westPanel.add(subsecsPanel)

            val savegamePanel = JPanel(BorderLayout())
            val savegameBox = JCheckBox()
            savegameBox.isSelected = worldData.locked
            savegamePanel.add(JLabel("Savegame / Locked:"), BorderLayout.WEST)
            savegamePanel.add(savegameBox, BorderLayout.EAST)
            savegameBox.addChangeListener { e: ChangeEvent? -> worldData.locked = savegameBox.isSelected }
            westPanel.add(savegamePanel)

            val centerPanel = JPanel(BorderLayout())

            val keysPanel = JPanel(GridLayout(2, 7))
            keysPanel.border = BorderFactory.createEmptyBorder(4, 8, 8, 8)
            for (i in 0..6) {
                val ico = ImageIcon(canvas.extractCharImage(12, i + 9, 1, 1, false, "_\$_"))
                val keyLabel = JLabel(ico)
                keyLabel.border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
                keyLabel.horizontalAlignment = SwingConstants.CENTER
                keyLabel.verticalAlignment = SwingConstants.CENTER
                keysPanel.add(keyLabel)
            }
            for (i in 0..6) {
                val keyIdx = i
                val keyCheckbox = JCheckBox()
                keyCheckbox.isSelected = worldData.getKey(keyIdx)
                keyCheckbox.addChangeListener { e: ChangeEvent? -> worldData.setKey(keyIdx, keyCheckbox.isSelected) }
                keyCheckbox.horizontalAlignment = SwingConstants.CENTER
                keyCheckbox.verticalAlignment = SwingConstants.CENTER
                keysPanel.add(keyCheckbox)
            }

            centerPanel.add(westPanel, BorderLayout.CENTER)
            centerPanel.add(keysPanel, BorderLayout.SOUTH)

            settings.contentPane.add(centerPanel, BorderLayout.CENTER)

            settings.pack()
            settings.setLocationRelativeTo(frame)
            settings.isVisible = true
        }
    }
}
