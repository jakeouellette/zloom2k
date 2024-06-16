package zedit2.components

import net.miginfocom.swing.MigLayout
import zedit2.*
import zedit2.components.*
import zedit2.components.GlobalEditor.updateTimestamp
import zedit2.components.Settings.Companion.board
import zedit2.components.Settings.Companion.world
import zedit2.components.Util.addKeybind
import zedit2.components.Util.clamp
import zedit2.components.Util.evalConfigDir
import zedit2.components.Util.getExtensionless
import zedit2.components.Util.getKeyStroke
import zedit2.components.Util.keyStrokeString
import zedit2.components.editor.BrushMenuPanel
import zedit2.components.editor.TileInfoPanel
import zedit2.components.editor.code.CodeEditor
import zedit2.components.editor.code.CodeEditorFactory
import zedit2.components.editor.world.*
import zedit2.event.KeyActionReceiver
import zedit2.event.OnBoardUpdatedCallback
import zedit2.event.TileEditorCallback
import zedit2.model.*
import zedit2.model.SZZTWorldData.Companion.createWorld
import zedit2.model.WorldData.Companion.loadWorld
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import zedit2.util.*
import zedit2.util.CP437.toBytes
import zedit2.util.CP437.toUnicode
import zedit2.util.Constants.EDITOR_FONT
import zedit2.util.FileUtil.getFileChooser
import zedit2.util.Logger.TAG
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.Timer
import java.util.regex.Pattern
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.event.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class WorldEditor @JvmOverloads constructor(
    val globalEditor: GlobalEditor, path: File?, var worldData: WorldData = loadWorld(
        path!!
    )
) : OnBoardUpdatedCallback, KeyActionReceiver, KeyListener, WindowFocusListener, PopupMenuListener, MenuListener {
    internal lateinit var boardSelectorComponent: BoardSelector
    internal var path: File? = null
    var boardIdx: Int = 0
    internal var currentBoard: Board? = null
    var boards: ArrayList<Board> = ArrayList()
        private set

    private var zoom: Double
    lateinit var lastFocusedElement : Component
    internal var undoHandler: UndoHandler = UndoHandler(this)
    internal var selectionModeConfiguration : SelectionModeConfiguration? = null
    internal var cursorPos = Pos.ZERO
    private var centreView = false
    internal var blockStartPos = Pos.NEG_ONE
    // TODO(jakeouellettE): Should these be negative, or 0
    internal var moveBlockDim = Dim(0, 0)
    internal var moveBlockPos = Pos.ZERO
    internal var boardDim = Dim(0, 0)
    internal val atlases = HashMap<Int, Atlas>()
    internal var currentAtlas: Atlas? = null
    internal var gridDim = Dim(0, 0)
    internal lateinit var grid: Array<IntArray>
    var dim : Dim = Dim(0,0)
    internal var drawing = false
        set(value) {
            field = value
            canvas.setDrawing(value)
        }

    // FIXME(jakeouellette): Right now edit type just switches
    // between editing and selecting, but more types are represented
    // by it as an enum
    internal var editType : EditType = EditType.EDITING
        set(value) {
            field = value
            // TODO(jakeouellette): Set something in the canvas?
        }
    internal var textEntry = false
        set(value) {
            field = value
            canvas.setTextEntry(value)
        }
    internal var textEntryX = 0
    internal var fancyFillDialog = false

    private val voidsDrawn = HashSet<Pos>()
    private var redraw = false
    private var redrawPos = Pos.ZERO
    private var redrawPos2 = Pos.ZERO
    private var redrawDim = Dim(0, 0)
    private val deleteOnClose = HashSet<File>()

    lateinit var canvas: DosCanvas
        private set

    private lateinit var boardListPane: JPanel
    private lateinit var brushesPane: JPanel
    private lateinit var cursorInfoPane: JPanel
    internal lateinit var boardSelector: JScrollPane
    private lateinit var bufferPane: JPanel
    private lateinit var bufferPaneContents: JPanel
    private lateinit var infoBox: JTextArea
    lateinit var frame: JFrame
    private lateinit var canvasScrollPane: JScrollPane
    private lateinit var menuBar: JMenuBar
    internal lateinit var editingModePane: EditingModePane
    private lateinit var onBufferTileUpdated: JComponent.(Tile?) -> Unit
    internal var currentlyShowing = SHOW_NOTHING

    private val blinkingImageIcons = ArrayList<BlinkingImageIcon>()

    private var mouseScreenPos = Pos.ZERO
    private var mouseCoord = Pos.ZERO
    private var mousePos = Pos.ZERO
    private var oldMouseCoord = Pos.NEG_ONE
    private val fmenus = HashMap<Int, JMenu>()
    private var recentFilesMenu: JMenu? = null

    private val testThreads = ArrayList<Thread>()

    internal lateinit var currentBufferManager: BufferManager
    private val bufferOperation = BufferOperationImpl(this@WorldEditor)
    internal var mouseState = 0

    private var popupOpen = false

    constructor(globalEditor: GlobalEditor, szzt: Boolean) : this(
        globalEditor,
        null,
        if (szzt) createWorld() else ZZTWorldData.createWorld()
    )

    init {
        GlobalEditor.editorOpened()
        this.zoom = GlobalEditor.getDouble("ZOOM")
        createGUI()
        loadWorld(path, worldData)
        afterUpdate()
    }

    @Throws(IOException::class, WorldCorruptedException::class)
    private fun openWorld(path: File) {
        if (oneWorldAtATime()) {
            if (promptOnClose()) {
                loadWorld(path, loadWorld(path))
                invalidateCache()
                afterModification()
            }
        } else {
            WorldEditor(globalEditor, path)
        }
    }

    @Throws(IOException::class)
    private fun newWorld(szzt: Boolean) {
        try {
            if (oneWorldAtATime()) {
                if (promptOnClose()) {
                    val newWorld = if (szzt) createWorld() else ZZTWorldData.createWorld()
                    loadWorld(null, newWorld)
                    invalidateCache()
                    afterModification()
                }
            } else {
                WorldEditor(globalEditor, szzt)
            }
        } catch (e: WorldCorruptedException) {
            throw RuntimeException("This should not happen.")
        }
    }


    @Throws(WorldCorruptedException::class)
    private fun loadWorld(path: File?, worldData: WorldData) {
        if (path != null) {
            GlobalEditor.defaultDirectory = path
        }
        this.path = path
        GlobalEditor.addToRecent(path)
        this.worldData = worldData
        boards.clear()
        atlases.clear()
        try {
            canvas.setCP(null, null)
        } catch (ignored: IOException) {
        }

        for (i in 0..worldData.numBoards) {
            boards.add(worldData.getBoard(i))
        }
        onBoardsUpdated(boards)

        val cb = worldData.currentBoard
        boardDim = boards[0].dim
        cursorPos = boards[cb].getStat(0)!!.pos - 1
        centreView = true

        updateMenu()
        changeBoard(cb)
    }

    private fun changeToIndividualBoard(newBoardIdx: Int) {
        setCurrentBoard(newBoardIdx)
        atlases.remove(newBoardIdx)
        currentAtlas = null
        gridDim = Dim.ONE_BY_ONE
        cursorPos %= boardDim
        canvas.setCursor(cursorPos)
        Logger.i(TAG) { "bd: $boardDim, gd: $gridDim $dim"}
        dim = boardDim * gridDim
        Logger.i(TAG) { "bd: $boardDim, gd: $gridDim $dim"}
        grid = Array(1) { IntArray(1) }
        grid[0][0] = newBoardIdx
        invalidateCache()
        afterModification()
        canvas.revalidate()
    }

    private fun setCurrentBoard(newBoardIdx: Int) {
        boardIdx = newBoardIdx
        currentBoard = if (boardIdx == -1) {
            null
        } else {
            boards[boardIdx]
        }
    }

    fun changeBoard(newBoardIdx: Int) {
        // Search the atlas for this board
        val atlas = atlases[newBoardIdx]
        if (atlas != null) {
            val gridPos = checkNotNull(atlas.search(newBoardIdx))
            val pos = gridPos
            cursorPos = (cursorPos % boardDim) + (pos * boardDim)
            canvas.setCursor(cursorPos)
            setCurrentBoard(newBoardIdx)
            if (atlas != currentAtlas) {
                gridDim = atlas.dim
                grid = atlas.grid
                dim = boardDim * gridDim

                currentAtlas = atlas
                invalidateCache()
                afterModification()
                canvas.revalidate()
            }

            val rect = Rectangle(
                canvas.getCharW(pos.x * boardDim.w),
                canvas.getCharH(pos.y * boardDim.h),
                canvas.getCharW(boardDim.w),
                canvas.getCharH(boardDim.h)
            )
            canvas.scrollRectToVisible(rect)
        } else {
            // No atlas, switch to individual board
            changeToIndividualBoard(newBoardIdx)
        }
    }

    internal fun invalidateCache() {
        voidsDrawn.clear()
        redrawDim = Dim.EMPTY
    }

    internal fun addRedraw(pos : Pos, pos2 : Pos) {
        // Expand the range by 1 to handle lines
        val expandedPos = pos - 1
        val expandedPos2 = pos2 + 1

        if (!redraw) {
            redrawPos = expandedPos
            redrawPos2 = expandedPos2
            redraw = true
        } else {
            redrawPos = redrawPos.min(expandedPos)
            redrawPos2 = redrawPos2.max(expandedPos2)
        }
    }

    private fun drawBoard() {
        if (dim != redrawDim) {
            canvas.setDimensions(dim)
            redrawDim = dim
            redrawPos = Pos.ZERO
            redrawPos2 = (dim.asPos - 1)
            redraw = true
        }
        canvas.setZoom(if (worldData.isSuperZZT) zoom * 2 else zoom)
        canvas.setAtlas(currentAtlas, boardDim, GlobalEditor.getBoolean("ATLAS_GRID", true))
        if (redraw) {
            for (y in 0 until gridDim.h) {
                for (x in 0 until gridDim.w) {
                    val boardIdx = grid[y][x]
                    val xyPos = Pos(x,y)
                    if (boardIdx != -1) {
                        val pos = (xyPos * boardDim).max(redrawPos)
                        val pos2 = ((xyPos * boardDim) + (boardDim - Dim.ONE_BY_ONE)).min(redrawPos2)

                        if (pos2.x >= pos.x && pos2.y >= pos.y) {
                            boards[boardIdx].drawToCanvas(
                                canvas, xyPos * boardDim,
                                pos - (xyPos * boardDim),
                                pos2 - (xyPos * boardDim),
                                currentlyShowing
                            )
                        }
                    } else {
                        if (!voidsDrawn.contains(xyPos)) {
                            canvas.drawVoid(xyPos * boardDim, boardDim)
                            voidsDrawn.add(xyPos)
                        }
                    }
                }
            }
        }
        canvas.repaint()
        redraw = false
    }

    @Throws(IOException::class)
    private fun createGUI() {
        frame = object : JFrame() {
            init {
                disableAlt()

                /**/ //frame.setUndecorated(true);
                this.addKeyListener(this@WorldEditor)
                this.addWindowFocusListener(this@WorldEditor)
                this.addWindowFocusListener(object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent?) {
                        Logger.i(TAG) {"Window focus gained."}
                        this@WorldEditor.lastFocusedElement.requestFocusInWindow()
                    }

                    override fun windowLostFocus(e: WindowEvent?) {
                        Logger.i(TAG) {"Window focus lost."}
                    }
                })
                //var ico = ImageIO.read(new File("zediticon.png"));
                val ico = ImageIO.read(ByteArrayInputStream(Data.ZEDITICON_PNG))
                this.iconImage = ico
                this.defaultCloseOperation = DO_NOTHING_ON_CLOSE
                this.addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent) {
                        GlobalEditor.editorClosed()
                    }

                    override fun windowClosing(e: WindowEvent) {
                        tryClose()
                    }
                })

                val menuBar = JMenuBar()
                this@WorldEditor.menuBar = menuBar
                this.jMenuBar = menuBar
                createMenu()

                run {
                    // Remove F10
                    val im = menuBar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    for (k in im.allKeys()) {
                        if (k.keyCode == KeyEvent.VK_F10) {
                            im.remove(k)
                        }
                    }
                }
                canvas = DosCanvas(this@WorldEditor, zoom,zoom)
                canvas.isRequestFocusEnabled = true
                Logger.i(TAG) {"Requesting Focus."}
                canvas.requestFocusInWindow()
                lastFocusedElement = canvas
                canvas.setBlinkMode(GlobalEditor.getBoolean("BLINKING", true))
                // change(jakeouellette): was this.layeredPane
                this.addKeybinds(canvas)

                //drawBoard();
                canvasScrollPane = object : JScrollPane(canvas) {
                    init {
                        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_ALWAYS
                        verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS
                    }
                }

                infoBox = object : JTextArea(3, 20) {
                    init {
//                        border = BorderFactory.createBevelBorder(BevelBorder.LOWERED)
//                        font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                        //infoBox.setFont(CP437.INSTANCE.getFont());
//                        this.background = Color(0x0000AA)
//                        this.foreground = Color(0xFFFFFF)
                        this.font = EDITOR_FONT
                        isEditable = false
                        isFocusable = false
                    }
                }

                bufferPane = JPanel(BorderLayout())

                editingModePane = object : EditingModePane() {
                    init {
                        this.isOpaque = false
                    }
                }
                infoBox.setPreferredSize(Dimension(80, 40))
                val controlScrollPane = object : JScrollPane(bufferPane) {
                    init {
                        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
                    }
                }

//                val brushSelectorPane = JPanel()
                this@WorldEditor.currentBufferManager = BufferManager(
                    this@WorldEditor.bufferOperation,
                    this@WorldEditor.prefix(),
                    this@WorldEditor.canvas,
                    this@WorldEditor.globalEditor
                )
                val frameThis = this
                this@WorldEditor.onBufferTileUpdated = { tile: Tile? ->
                    this.removeAll()

                    val leftHandPanel = JPanel(BorderLayout())
                    val editsPanel = JPanel()

                    val undoButton = JButton("↩")
                    undoButton.addActionListener { e ->
                        undoHandler.operationUndo()
                    }
                    editsPanel.add(undoButton)
                    val redoButton = JButton("↪")
                    redoButton.addActionListener { e ->
                        undoHandler.operationRedo()
                    }
                    editsPanel.add(redoButton)
                    leftHandPanel.add(editsPanel, BorderLayout.NORTH)
                    leftHandPanel.add(infoBox, BorderLayout.SOUTH)

                    this.add(leftHandPanel, "west")
                    infoBox.alignmentY = TOP_ALIGNMENT
                    val board = this@WorldEditor.currentBoard
                    if (tile != null && board != null) {
                        val infoTile = TileInfoPanel(
                            dosCanvas = canvas,
                            this@WorldEditor.worldData,
                            "Brush",
                            tile,
                            board,
                            onBlinkingImageIconAdded = {})
                        infoTile.minimumSize = Dimension(200, 220)
                        val onCodeSaved = { e: ActionEvent ->
                            // TODO(jakeouellette): I dunno why it is always stats[0]
                            CodeEditorFactory.create(
                                Pos.NEG_ONE,
                                true,
                                frameThis,
                                this@WorldEditor,
                                IconFactory.getIcon(worldData.isSuperZZT, tile, this@WorldEditor),
                                board,
                                tile.stats[0]
                            )
                        }

                        this.add(infoTile, "push")
                        val shouldShowViewCode = tile.id == ZType.OBJECT || tile.id == ZType.SCROLL

                        val brushMenu = BrushMenuPanel(
                            shouldShowViewCode,
                            { editType = EditType.SELECTING
                                operationBlockStart()
                            },
                            { mode : SelectionModeConfiguration ->
                                this@WorldEditor.selectionModeConfiguration = mode
                            },
                            {
                                editType = EditType.DRAWING
                            },
                            {

                                operationModifyBuffer(true)
                            },
                            { operationColour() },
                            { operationBufferSwapColour() },
                            onCodeSaved,
                            this@WorldEditor.selectionModeConfiguration
                        )
                        this.add(brushMenu)
                    }
                    this.add(currentBufferManager, "east")
                    currentBufferManager.alignmentY = TOP_ALIGNMENT
                }

                this@WorldEditor.brushesPane = JPanel()

                val layout = MigLayout()
                this@WorldEditor.brushesPane.layout = layout
                this@WorldEditor.brushesPane.onBufferTileUpdated(bufferTile)

                this@WorldEditor.cursorInfoPane = object : JPanel() {
                    init {
                        this.add(controlScrollPane)
                    }
                }
                this@WorldEditor.boardSelectorComponent = createBoardSelector()
                this@WorldEditor.boardSelector = JScrollPane(boardSelectorComponent)
                this@WorldEditor.boardListPane = object : JPanel(BorderLayout()) {
                    init {
                        this.add(editingModePane, BorderLayout.SOUTH)
                        this.add(boardSelector, BorderLayout.CENTER)
                    }
                }

//                val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, brushesPane, canvasScrollPane)
                val splitPane = object : JPanel(BorderLayout()) {
                    init {
                        this.add(brushesPane, BorderLayout.NORTH)
                        this.add(canvasScrollPane, BorderLayout.CENTER)
                        this.add(boardListPane, BorderLayout.WEST)
                        this.add(cursorInfoPane, BorderLayout.EAST)
                    }
                }

                val timer = Timer(true)
                timer.schedule(object : TimerTask() {
                    private var blinkState = false
                    override fun run() {
                        SwingUtilities.invokeLater {
                            // TODO(jakeouellette): I commented this out as it was causing surprising focus behaviors.

//                            if (!popupOpen) {
//                                Logger.i(TAG) {"558: Requesting Focus."}
//                                lastFocusedElement.requestFocusInWindow()
//                            }
                            blinkState = !blinkState
                            canvas.setBlink(blinkState)
                            for (icon in blinkingImageIcons) {
                                icon.blink(blinkState)
                            }
                            bufferPane.repaint()
                        }
                    }
                }, BLINK_DELAY.toLong(), BLINK_DELAY.toLong())

                contentPane.add(splitPane)
                pack()
                setLocationRelativeTo(null)
                /**/ //frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                isVisible = true
            }
        }

    }

    private fun closeEditor() {
        frame.dispose()
    }

    internal fun tryClose() {
        if (promptOnClose()) {
            closeEditor()
        }
    }

    private val isDirty: Boolean
        get() {
            if (worldData.isDirty) return true
            for (board in boards) {
                if (board.isDirty) {
                    return true
                }
            }
            return false
        }

    private fun promptOnClose(): Boolean {
        if (isDirty) {
            val result = JOptionPane.showConfirmDialog(
                frame, "Save changes before closing?",
                "Close world", JOptionPane.YES_NO_CANCEL_OPTION
            )
            if (result == JOptionPane.YES_OPTION) {
                return menuSaveAs()
            }
            return result == JOptionPane.NO_OPTION
        } else {
            if (!GlobalEditor.getBoolean("PROMPT_ON_CLOSE", true)) return true
            val result = JOptionPane.showConfirmDialog(
                frame, "Close world?",
                "Close world", JOptionPane.YES_NO_OPTION
            )
            return result == JOptionPane.YES_OPTION
        }
    }

    internal fun saveTo(path: File): Boolean {
        val worldCopy = worldData.clone()
        if (saveGame(path, worldCopy)) {
            for (board in boards) {
                board.clearDirty()
            }
            worldCopy.isDirty = false
            worldData = worldCopy
            return true
        }
        return false
    }

    private fun saveGame(path: File, worldCopy: WorldData): Boolean {
        val warning = CompatWarning(worldData.isSuperZZT)

        // Update world name, if necessary
        val oldWorldName = String(worldCopy.name)
        val oldFileName = if (this.path == null) "" else getExtensionless(this.path.toString())
        if (oldWorldName.isEmpty() || oldWorldName.equals(oldFileName, ignoreCase = true)) {
            val newWorldName = getExtensionless(path.toString()).uppercase(Locale.getDefault())
            worldCopy.name = newWorldName.toByteArray()
        }

        if (boards.size > 254 && !worldData.isSuperZZT) {
            warning.warn(1, "World has >254 boards, which may cause problems in Weave 3.")
        } else if (boards.size > 33 && worldData.isSuperZZT) {
            warning.warn(1, "World has >33 boards, which may cause problems in vanilla Super ZZT.")
        }

        for (boardIdx in boards.indices) {
            val board = boards[boardIdx]
            warning.setPrefix(String.format("Board %d (%s) ", boardIdx, toUnicode(board.getName())))
            if (board.isDirty) {
                worldCopy.setBoard(warning, boardIdx, board)
            }
        }
        worldCopy.terminateWorld(boards.size)
        warning.setPrefix("")
        if (worldCopy.size > 450 * 1024) {
            warning.warn(1, "World is over 450kb, which may cause memory problems in ZZT.")
        }

        if (warning.warningLevel == 2) {
            JOptionPane.showMessageDialog(
                frame, warning.getMessages(2),
                "Compatibility error", JOptionPane.ERROR_MESSAGE
            )
            return false
        } else if (warning.warningLevel == 1) {
            val result = JOptionPane.showConfirmDialog(
                frame, warning.getMessages(1),
                "Compatibility warning", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE
            )
            if (result != JOptionPane.OK_OPTION) return false
        }

        try {
            worldCopy.write(path)
        } catch (o: IOException) {
            JOptionPane.showMessageDialog(
                frame, "Failed to write to file",
                "File error", JOptionPane.ERROR_MESSAGE
            )
        }

        return true
    }

    private fun menuOpenWorld() {
        val fileChooser = getFileChooser(arrayOf("zzt", "szt", "sav"), "ZZT/Super ZZT world and save files")

        val result = fileChooser.showOpenDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                updateDefaultDir(file)
                openWorld(file)
            } catch (e: IOException) {
                JOptionPane.showMessageDialog(frame, e, "Error loading world", JOptionPane.ERROR_MESSAGE)
            } catch (e: WorldCorruptedException) {
                JOptionPane.showMessageDialog(frame, e, "Error loading world", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun menuImportWorld() {
        val fileChooser = getFileChooser(arrayOf("zzt", "szt", "sav"), "ZZT/Super ZZT world and save files")

        val result = fileChooser.showOpenDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                updateDefaultDir(file)
                val worldData = loadWorld(file)
                if (worldData.isSuperZZT != this.worldData.isSuperZZT) {
                    throw RuntimeException("Error: ZZT / Super ZZT mismatch")
                }
                for (i in 0..worldData.numBoards) {
                    val board = worldData.getBoard(i)
                    board.setDirty()
                    boards.add(board)
                }
                onBoardsUpdated(boards)
            } catch (e: IOException) {
                JOptionPane.showMessageDialog(frame, e, "Error importing world", JOptionPane.ERROR_MESSAGE)
            } catch (e: RuntimeException) {
                JOptionPane.showMessageDialog(frame, e, "Error importing world", JOptionPane.ERROR_MESSAGE)
            } catch (e: WorldCorruptedException) {
                JOptionPane.showMessageDialog(frame, e, "Error importing world", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun menuLoadCharset() {
        val fileChooser = getFileChooser(arrayOf("chr", "com"), "Character set")
        val result = fileChooser.showOpenDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                canvas.setCharset(file)
                updateDefaultDir(file)
            } catch (e: IOException) {
                JOptionPane.showMessageDialog(frame, e, "Error loading char set", JOptionPane.ERROR_MESSAGE)
            } catch (e: RuntimeException) {
                JOptionPane.showMessageDialog(frame, e, "Error loading char set", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun menuLoadPalette() {
        val fileChooser = getFileChooser(arrayOf("pal"), "Palette")
        val result = fileChooser.showOpenDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                canvas.setPalette(file)
                updateDefaultDir(file)
            } catch (e: IOException) {
                JOptionPane.showMessageDialog(frame, e, "Error loading palette", JOptionPane.ERROR_MESSAGE)
            } catch (e: RuntimeException) {
                JOptionPane.showMessageDialog(frame, e, "Error loading palette", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun menuDefaultCharsetPalette() {
        try {
            canvas.setCharset(null)
            canvas.setPalette(null)
        } catch (ignored: IOException) {
        }
    }

    internal fun updateDefaultDir(file: File?) {
        if (file != null) {
            GlobalEditor.defaultDirectory = file
        }
    }

    internal fun menuBoardList(deleteBoard: Int? = null) {
        BoardManager(this.canvas,
            object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    this@WorldEditor.canvas.setIndicate(null)
                }
            },
            this.frameForRelativePositioning,
            this,
            worldData,
            boards,
            boardIdx,
            deleteBoard = deleteBoard,
            modal = deleteBoard == null
        )

    }

    private fun menuNewWorld(szzt: Boolean) {
        try {
            newWorld(szzt)
        } catch (e: IOException) {
            JOptionPane.showMessageDialog(frame, e, "Error creating new world", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun menuExportBoard() {
        if (currentBoard == null) return
        val fileChooser = getFileChooser(arrayOf("brd"), "ZZT/Super ZZT .BRD files")
        val result = fileChooser.showSaveDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                updateDefaultDir(file)
                currentBoard!!.saveTo(file)
            } catch (e: IOException) {
                JOptionPane.showMessageDialog(frame, e, "Error exporting board", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun menuImportBoard() {
        val fileChooser = getFileChooser(arrayOf("brd"), "ZZT/Super ZZT .BRD files")
        val result = fileChooser.showOpenDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                currentBoard!!.loadFrom(file)
                updateDefaultDir(file)
                changeBoard(boardIdx)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(frame, e, "Error loading board", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    private fun menuImportBoards() {
        val fileChooser = getFileChooser(
            arrayOf("brd"),
            "ZZT/Super ZZT .BRD files (Files that begin with numbers will be loaded to that index)"
        )
        fileChooser.isMultiSelectionEnabled = true
        val result = fileChooser.showOpenDialog(frame)
        if (result == JFileChooser.APPROVE_OPTION) {
            val files = fileChooser.selectedFiles

            val boardsToLoad = ArrayList<File?>()
            for (ignored in boards) {
                boardsToLoad.add(null)
            }

            for (file in files) {
                var boardNum = -1
                try {
                    boardNum =
                        file.name.split("[^0-9]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0].toInt()
                } catch (ignore: NumberFormatException) {
                } catch (ignore: ArrayIndexOutOfBoundsException) {
                }
                if (boardNum < 0 || boardNum > 255) {
                    // Load this into a null slot that's >= the current board count
                    var placed = false
                    for (idx in boards.size until boardsToLoad.size) {
                        if (boardsToLoad[idx] == null) {
                            boardsToLoad[idx] = file
                            placed = true
                            break
                        }
                    }
                    if (!placed && boardsToLoad.size < 256) boardsToLoad.add(file)
                } else {
                    while (boardNum >= boardsToLoad.size) {
                        if (boardsToLoad.size < 255) {
                            boardsToLoad.add(null)
                        }
                    }
                    boardsToLoad[boardNum] = file
                }
            }

            for (idx in boardsToLoad.indices) {
                val file = boardsToLoad[idx]
                if (idx >= boards.size) {
                    // Insert blank board here
                    boards.add(blankBoard("-untitled"))
                }
                if (file != null) {
                    try {
                        boards[idx].loadFrom(file)
                        if (idx == boardIdx) {
                            changeBoard(boardIdx)
                        }
                    } catch (e: Exception) {
                        JOptionPane.showMessageDialog(frame, e, "Error loading board", JOptionPane.ERROR_MESSAGE)
                    }
                }
                onBoardsUpdated(boards)
            }
        }
    }

    private fun handleCheckbox(key: String, setting: Boolean?): Boolean {
        if (setting == null) {
            return if (GlobalEditor.isKey(key)) {
                GlobalEditor.getBoolean(key)
            } else {
                false
            }
        } else {
            GlobalEditor.setBoolean(key, setting)
            return setting
        }
    }

    private fun oneWorldAtATime(): Boolean {
        val key = "ONE_WORLD_AT_A_TIME"
        return GlobalEditor.isKey(key) && GlobalEditor.getBoolean(key)
    }

    fun createMenu() {
        menuBar.removeAll()
        // | File     | World    |          |          |          |          |          |
        // | New ZZT  | World Set|          |          |          |          |          |
        // | New Super| Board Set|          |          |          |          |          |
        // | Open worl|          |          |          |          |          |          |
        // | Save     |          |          |          |          |          |          |
        // | Save As  |          |          |          |          |          |          |
        val menus = ArrayList<Menu>()
        run {
            var m = Menu("File")
            m.add("New ZZT world", null) { e: ActionEvent? -> menuNewWorld(false) }
            m.add("New Super ZZT world", null) { e: ActionEvent? -> menuNewWorld(true) }
            m.add("Open world", "L") { e: ActionEvent? -> menuOpenWorld() }
            recentFilesMenu = JMenu("Recent files")
            m.add(recentFilesMenu!!, "")

            m.add()
            m.add(
                "One world at a time",
                { e: ChangeEvent -> handleCheckbox("ONE_WORLD_AT_A_TIME", (e.source as JCheckBoxMenuItem).isSelected) },
                handleCheckbox("ONE_WORLD_AT_A_TIME", null)
            )
            m.add()
            m.add("Save", "Ctrl-S") { e: ActionEvent? -> menuSave() }
            m.add("Save as", "S") { e: ActionEvent? -> menuSaveAs() }
            m.add()
            m.add("ZLoom2 settings...", null) { e: ActionEvent? ->
                Settings(
                    {
                        this.createMenu()
                        this.updateMenu()
                    },
                    { this.refreshKeymapping() },
                    this@WorldEditor.frameForRelativePositioning,
                    this@WorldEditor.globalEditor
                )
            }
            m.add()
            m.add("Close world", "Escape") { e: ActionEvent? ->
                if (promptOnClose()) closeEditor()
            }
            menus.add(m)

            m = Menu("World")
            m.add("World settings...", "G") { e: ActionEvent? -> world(frame, boards, worldData, canvas) }
            m.add()
            m.add("Import world", null) { e: ActionEvent? -> menuImportWorld() }
            m.add()
            m.add("Load charset", null) { e: ActionEvent? -> menuLoadCharset() }
            m.add("Load palette", null) { e: ActionEvent? -> menuLoadPalette() }
            m.add("Default charset and palette", null) { e: ActionEvent? -> menuDefaultCharsetPalette() }
            m.add()
            m.add("Blinking", { e: ChangeEvent ->
                handleCheckbox("BLINKING", (e.source as JCheckBoxMenuItem).isSelected)
                afterBlinkToggle()
            }, handleCheckbox("BLINKING", null))
            m.add()
            m.add("Test world", "Alt-T") { e: ActionEvent? -> operationTestWorld() }
            m.add()
            m.add("Atlas", "Ctrl-A") { e: ActionEvent? -> atlas() }
            menus.add(m)

            m = Menu("Board")
            m.add("Board settings...", "I") { e: ActionEvent? -> board(frame, currentBoard, worldData) }
            m.add("Board exits", "X") { e: ActionEvent? -> operationBoardExits() }
            m.add()
//            m.add("Switch board", "B") { e: ActionEvent? -> createBoardSelector() }
            m.add("Add board", "A") { e: ActionEvent? -> operationAddBoard() }
            m.add("Add boards (*x* grid)", null) { e: ActionEvent? -> operationAddBoardGrid() }
            m.add("Board list", "Shift-B") { e: ActionEvent? -> menuBoardList() }
            m.add()
            m.add("Import board", "Alt-I") { e: ActionEvent? -> menuImportBoard() }
            m.add("Import boards", null) { e: ActionEvent? -> menuImportBoards() }
            m.add("Export board", "Alt-X") { e: ActionEvent? -> menuExportBoard() }
            m.add()
            m.add("Remove from atlas", "Ctrl-R") { e: ActionEvent? -> atlasRemoveBoard() }
            m.add()
            m.add("Stats list", "Alt-S") { e: ActionEvent? -> operationStatList() }
            menus.add(m)

            m = Menu("Edit")
            m.add("Undo", "Ctrl-Z") { e: ActionEvent? -> undoHandler.operationUndo() }
            m.add("Redo", "Ctrl-Y") { e: ActionEvent? -> undoHandler.operationRedo() }
            m.add()

            m.add("Convert image", null) { e: ActionEvent? -> operationLoadImage() }
            m.add("Convert image from clipboard", "Ctrl-V") { e: ActionEvent? -> operationPasteImage() }
            m.add()
            m.add("Erase player from board", "Ctrl-E") { e: ActionEvent? -> operationErasePlayer() }

            menus.add(m)
//            m.add("Buffer manager", "Ctrl-B") { e: ActionEvent? -> operationOpenBufferManager() }

            m = Menu("Brush")
            m.add("Select colour", "C") { e: ActionEvent? -> operationColour() }
            m.add("Modify buffer tile", "P") { e: ActionEvent? -> operationModifyBuffer(false) }
            m.add("Modify buffer tile (advanced)", "Ctrl-P") { e: ActionEvent? -> operationModifyBuffer(true) }
            m.add("Modify tile under cursor", "Alt-M") { e: ActionEvent? -> operationGrabAndModify(false, false) }
            m.add(
                "Modify tile under cursor (advanced)",
                "Ctrl-Alt-M"
            ) { e: ActionEvent? -> operationGrabAndModify(false, true) }
            m.add("Exchange buffer fg/bg colours", "Ctrl-X") { e: ActionEvent? -> operationBufferSwapColour() }
            m.add()
            m.add("Start block operation", "Alt-B") { e: ActionEvent? -> operationBlockStart() }
            m.add()
            m.add("Enter text", "F2") { e: ActionEvent? -> operationToggleText() }
            m.add("Toggle drawing", "Tab") { e: ActionEvent? -> operationToggleDrawing() }
            m.add("Flood fill", "F") { e: ActionEvent? -> operationFloodfill(cursorPos, false) }
            m.add("Gradient fill", "Alt-F") { e: ActionEvent? -> operationFloodfill(cursorPos, true) }
            m.add()
            for (f in 3..10) {
                val fMenuName = getFMenuName(f)
                if (fMenuName != null && !fMenuName.isEmpty()) {
                    val elementMenu = JMenu(fMenuName)
                    fmenus[f] = elementMenu
                    m.add(elementMenu, "F$f")
                }
            }
            menus.add(m)
            m = Menu("View")
            m.add("Zoom in", "Ctrl-=") { e: ActionEvent? -> operationZoomIn(false) }
            m.add("Zoom out", "Ctrl--") { e: ActionEvent? -> operationZoomOut(false) }
            m.add("Reset zoom", null) { e: ActionEvent? -> operationResetZoom() }
            m.add()
            m.add(
                "Show grid in atlas", { e: ChangeEvent ->
                    handleCheckbox("ATLAS_GRID", (e.source as JCheckBoxMenuItem).isSelected)
                    afterModification()
                },
                handleCheckbox("ATLAS_GRID", null)
            )
            m.add()
            m.add("Show stats", "Shift-F1") { e: ActionEvent? -> operationShowTileTypes(SHOW_STATS) }
            m.add("Show objects", "Shift-F2") { e: ActionEvent? -> operationShowTileTypes(SHOW_OBJECTS) }
            m.add("Show invisibles", "Shift-F3") { e: ActionEvent? -> operationShowTileTypes(SHOW_INVISIBLES) }
            m.add("Show empties", "Shift-F4") { e: ActionEvent? -> operationShowTileTypes(SHOW_EMPTIES) }
            m.add("Show fakes", "Shift-F5") { e: ActionEvent? -> operationShowTileTypes(SHOW_FAKES) }
            m.add("Show empties as text", "Shift-F6") { e: ActionEvent? -> operationShowTileTypes(SHOW_EMPTEXTS) }
            m.add("Show nothing", null) { e: ActionEvent? -> operationShowTileTypes(SHOW_NOTHING) }
            m.add()
            m.add("Take screenshot", "F12") { e: ActionEvent? -> takeScreenshot() }
            m.add("Take screenshot to clipboard", "Alt-F12") { e: ActionEvent? -> takeScreenshotToClipboard() }
            menus.add(m)

            m = Menu("Help")
            m.add("Help", "F1") { e: ActionEvent? -> menuHelp() }
            m.add("About", null) { e: ActionEvent? -> menuAbout() }
            menus.add(m)
        }

        /*
        ActionListener listener = e -> {
            var menuItem = (JMenuItem)e.getSource();
            menuSelection(menuItem.getText());
        };
        ChangeListener checkboxListener = e -> {
            var menuItem = (JCheckBoxMenuItem)e.getSource();
            menuCheckbox(menuItem.getText(), menuItem.isSelected());
        };
         */
        for (m in menus) {
            val menu = JMenu(m.title)
            menu.addMenuListener(this@WorldEditor)
            for (mEntry in m) {
                mEntry.addToJMenu(globalEditor, menu)
            }

            menuBar.add(menu)
        }
        menuBar.revalidate()
        /*
                var mi = menuList[i];
                if (mi.equals("|")) {
                    menu.addSeparator();
                } else if (mi.equals("__ELEMENT_CATEGORIES__")) {
                    // F3 to F10
                    for (int f = 3; f <= 10; f++) {
                        var fMenuName = getFMenuName(f);
                        if (fMenuName != null) {
                            var elementMenu = new JMenu(fMenuName);
                            fmenus.put(f, elementMenu);
                            menu.add(elementMenu);
                        }
                    }

                } else if (mi.startsWith("[ ]")) {
                    var menuItemName = mi.substring(3);
                    var menuItem = new JCheckBoxMenuItem(menuItemName);
                    menuItem.setSelected(menuCheckbox(menuItemName, null));
                    menuItem.addChangeListener(checkboxListener);
                    menu.add(menuItem);
                    //mi.substring(3);
                } else {
                    var menuItem = new JMenuItem(menuList[i]);
                    menuItem.addActionListener(listener);
                    menu.add(menuItem);
                }
            }
            menuBar.add(menu);
        }
         */
    }

    private fun afterBlinkToggle() {
        canvas.setBlinkMode(GlobalEditor.getBoolean("BLINKING", true))
        invalidateCache()
    }

    fun updateMenu() {
        for (f in fmenus.keys) {
            val fMenuItems = getFMenuItems(f)
            fmenus[f]!!.removeAll()
            for (fMenuItem in fMenuItems) {
                fmenus[f]!!.add(fMenuItem)
            }
        }

        recentFilesMenu!!.removeAll()
        val recentMax = GlobalEditor.getInt("RECENT_MAX", 10)
        for (i in 9 downTo 0) {
            val recentFileName = GlobalEditor.getString(String.format("RECENT_%d", i))
            if (recentFileName != null) {
                val recentFile = File(evalConfigDir(recentFileName))
                val menuItem = getjMenuItem(recentFile, recentFileName)
                recentFilesMenu!!.add(menuItem)
            }
        }
        recentFilesMenu!!.isEnabled = recentFilesMenu!!.menuComponentCount != 0
    }

    private fun getjMenuItem(recentFile: File, recentFileName: String): JMenuItem {
        val menuEntry = recentFile.name
        val menuItem = JMenuItem(menuEntry)
        menuItem.addActionListener { e: ActionEvent? ->
            try {
                openWorld(recentFile)
                updateDefaultDir(recentFile)
            } catch (ex: IOException) {
                GlobalEditor.removeRecentFile(recentFileName)
                recentFilesMenu!!.remove(menuItem)
                if (recentFilesMenu!!.menuComponentCount == 0) recentFilesMenu!!.isEnabled = false
                JOptionPane.showMessageDialog(frame, ex, "Error loading world", JOptionPane.ERROR_MESSAGE)
            } catch (ex: WorldCorruptedException) {
                GlobalEditor.removeRecentFile(recentFileName)
                recentFilesMenu!!.remove(menuItem)
                if (recentFilesMenu!!.menuComponentCount == 0) recentFilesMenu!!.isEnabled = false
                JOptionPane.showMessageDialog(frame, ex, "Error loading world", JOptionPane.ERROR_MESSAGE)
            }
        }
        return menuItem
    }

    internal fun getFMenuItems(f: Int): Array<JMenuItem> {
        val szzt = worldData.isSuperZZT
        val menuItems = ArrayList<JMenuItem>()
        var i = 0
        while (true) {
            val prop = String.format("F%d_MENU_%d", f, i)
            val element = GlobalEditor.getString(prop)
            if (element == null || element.isEmpty()) {
                break
            } else {
                var elementName: String?
                var displayName: String?
                var starred = false
                val parenPos = element.indexOf('(')
                if (parenPos == -1) {
                    val exclPos = element.indexOf('!')
                    if (exclPos != -1) {
                        elementName = element.substring(0, exclPos)
                        displayName = element.substring(exclPos + 1)
                    } else {
                        elementName = element
                        displayName = elementName
                    }
                } else {
                    elementName = element.substring(0, parenPos)
                    val endParenPos = element.lastIndexOf(')')
                    if (endParenPos == -1) throw RuntimeException("Malformed element: $element")
                    if (endParenPos == element.length - 1) {
                        displayName = elementName
                    } else {
                        val starPos = element.lastIndexOf('*')
                        if (starPos == -1 || starPos < endParenPos) {
                            displayName = element.substring(endParenPos + 1)
                        } else {
                            starred = true
                            displayName = element.substring(endParenPos + 1, starPos)
                        }
                    }
                }
                val editOnPlace = !starred
                if (ZType.getId(szzt, elementName) == -1) {
                    i++
                    continue
                }
                val tile = getTileFromElement(element, 0xF0)
                val chr = ZType.getChar(szzt, tile)
                //byte[] glyph = new byte[1];
                //glyph[0] = (byte) chr;
                //var menuItem = new JMenuItem(CP437.INSTANCE.toUnicode(glyph) + " " + displayName);
                val menuItem = JMenuItem(displayName)
                //menuItem.setFont(CP437.INSTANCE.getFont());
                menuItem.addActionListener { e: ActionEvent? -> setBufferToElement(element, editOnPlace) }

                val col = ZType.getColour(szzt, tile)
                // TODO(jakeouellette): SZZT doubled one of the zooms here.
                // I removed it, but restore it a different way.
                // if (szzt) 2 else 1
                val img = canvas.extractCharImageWH(chr, col, 1, 1,false, "____\$____", Dim(3, 3))
                val side = 20
                val img2 = BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB)
                val g = img2.graphics
                g.drawImage(img, (side - img.width) / 2, (side - img.height) / 2, null)
                val icon = ImageIcon(img2)
                menuItem.icon = icon
                menuItems.add(menuItem)
            }
            i++
        }
        return menuItems.toTypedArray<JMenuItem>()
    }

    internal fun getTileFromElement(element: String, col: Int): Tile {
        var vanilla = false
        var parenPos = element.indexOf('(')
        if (parenPos == -1) {
            parenPos = element.length
            vanilla = true
        }
        var elementName = element.substring(0, parenPos)
        val exclPos = elementName.indexOf('!')
        if (exclPos != -1) {
            elementName = element.substring(0, exclPos)
        }
        val elementId = ZType.getId(worldData.isSuperZZT, elementName)
        if (elementId == -1) {
            throw RuntimeException(
                String.format(
                    "\"%s\" is not a valid %s element",
                    elementName,
                    if (worldData.isSuperZZT) "SuperZZT" else "ZZT"
                )
            )
        }
        val tile = Tile(elementId, 0)
        if (!ZType.isText(worldData.isSuperZZT, tile.id)) {
            paintTile(tile, col)
        }
        if (!vanilla) {
            val lastParenPos = element.lastIndexOf(')')
            if (lastParenPos == -1) throw RuntimeException("Malformed element: $element")
            var elementStatInfo = element.substring(parenPos + 1, lastParenPos)
            // | , =, ", \n are escaped. Convert the escaped forms to Unicode PUA U+E00{0,1,2,3,4} respectively so they don't interfere with splitting
            elementStatInfo = elementStatInfo.replace("\\|", "\uE000")
            elementStatInfo = elementStatInfo.replace("\\,", "\uE001")
            elementStatInfo = elementStatInfo.replace("\\=", "\uE002")
            elementStatInfo = elementStatInfo.replace("\\\"", "\uE003")
            elementStatInfo = elementStatInfo.replace("\\n", "\uE004")

            // Split into stats
            val statList = elementStatInfo.split(Pattern.quote("|").toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            val stats = ArrayList<Stat>()
            for (statString in statList) {
                val stat = Stat(worldData.isSuperZZT)
                // Split into params
                val paramList = statString.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (paramString in paramList) {
                    if (paramString.isEmpty()) continue
                    // Split into key=value
                    val kvPair = paramString.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (kvPair.size != 2) throw RuntimeException("Invalid key=value pair in $paramString")
                    val param = kvPair[0].uppercase(Locale.getDefault())
                    val value = kvPair[1]
                    when (param) {
                        "STATID" -> stat.statId = value.toInt()
                        "CYCLE" -> stat.cycle = value.toInt()
                        "P1" -> stat.p1 = value.toInt()
                        "P2" -> stat.p2 = value.toInt()
                        "P3" -> stat.p3 = value.toInt()
                        "UID" -> stat.uid = value.toInt()
                        "UCO" -> stat.uco = value.toInt()
                        "IP" -> stat.ip = value.toInt()
                        "STEPX" -> stat.stepX = value.toInt()
                        "STEPY" -> stat.stepY = value.toInt()
                        "POINTER" -> stat.pointer = value.toInt()
                        "AUTOBIND" -> stat.isAutobind = value.toBoolean()
                        "SPECIFYID" -> stat.isSpecifyId = value.toBoolean()
                        "ISPLAYER" -> stat.isPlayer = value.toBoolean()
                        "CODE" -> {
                            var code = value
                            code = code.replace("\uE000", "|")
                            code = code.replace("\uE001", ",")
                            code = code.replace("\uE002", "=")
                            code = code.replace("\uE003", "\"")
                            code = code.replace("\uE004", "\n")
                            stat.code = toBytes(code, false)
                        }

                        else -> throw RuntimeException("Stat property not supported: $param")
                    }
                }
                stats.add(stat)
            }
            tile.stats = stats
        }
        return tile
    }

    internal var bufferTile: Tile?
        get() = GlobalEditor.getBufferTile(worldData.isSuperZZT)
        internal set(tile) {
            GlobalEditor.setBufferTile(tile, worldData.isSuperZZT)
        }

    internal fun elementPlaceAtCursor(tile: Tile) {
        bufferTile = tile
        val board = getBoardAt(cursorPos)

        if (board != null) {
            putTileAt(cursorPos, tile, PUT_DEFAULT)
            afterModification()
        } else {
            afterUpdate()
        }
    }

    internal fun paintTile(tile: Tile, col: Int) {
        // TODO(jakeouellette): Decide if this was ever needed.
        // val backupTile = tile.clone()
        if (isText(tile)) {
            tile.id = (col % 128) + 128
        } else {
            tile.col = col
        }
    }

    private fun isText(bufferTile: Tile): Boolean {
        return ZType.isText(worldData.isSuperZZT, bufferTile.id)
    }

    internal fun getTileColour(bufferTile: Tile): Int {
        var col = bufferTile.col
        val id = bufferTile.id
        val tcol = ZType.getTextColour(worldData.isSuperZZT, id)
        if (tcol != -1) {
            col = tcol
        }
        return col
    }

    internal fun getFMenuName(f: Int): String {
        val firstItem = GlobalEditor.getString(String.format("F%d_MENU_0", f), "")
        if (firstItem.isEmpty()) return ""
        return GlobalEditor.getString(String.format("F%d_MENU", f), "")
    }

    private fun JFrame.addKeybinds(component: JComponent) {
        this.focusTraversalKeysEnabled = false
        component.actionMap.clear()
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).clear()
        listOf(
            "Escape",
            "Up",
            "Down",
            "Left",
            "Right",
            "Alt-Up",
            "Alt-Down",
            "Alt-Left",
            "Alt-Right",
            "Shift-Up",
            "Shift-Down",
            "Shift-Left",
            "Shift-Right",
            "Ctrl-Shift-Up",
            "Ctrl-Shift-Down",
            "Ctrl-Shift-Left",
            "Ctrl-Shift-Right",
            "Tab",
            "Home",
            "End",
            "Insert",
            "Space",
            "Delete",
            "Enter",
            "Ctrl-Enter",
            "Ctrl-=",
            "Ctrl--",
            "A",
            "B",
            "C",
            "D",
            "F",
            "G",
            "I",
            "L",
            "P",
            "S",
            "X",
            "Ctrl-A",
            "Ctrl-B",
            "Ctrl-E",
            "Ctrl-P",
            "Ctrl-R",
            "Ctrl-S",
            "Ctrl-V",
            "Ctrl-X",
            "Ctrl-Y",
            "Ctrl-Z",
            "Alt-B",
            "Alt-F",
            "Alt-I",
            "Alt-M",
            "Alt-S",
            "Alt-T",
            "Alt-X",
            "Shift-B",
            "Ctrl-Alt-M",
            "F1",
            "F2",
            "F3",
            "F4",
            "F5",
            "F6",
            "F7",
            "F8",
            "F9",
            "F10",
            "F12",
            "Shift-F1",
            "Shift-F2",
            "Shift-F3",
            "Shift-F4",
            "Shift-F5",
            "Shift-F6",
            "Alt-F12",
            "0",
            "1",
            "2",
            "3",
            "4",
            "5",
            "6",
            "7",
            "8",
            "9",
            "Ctrl-0",
            "Ctrl-1",
            "Ctrl-2",
            "Ctrl-3",
            "Ctrl-4",
            "Ctrl-5",
            "Ctrl-6",
            "Ctrl-7",
            "Ctrl-8",
            "Ctrl-9"
        ).forEach { addKeybind(globalEditor, this@WorldEditor, component, it) }
    }

    override fun keyAction(actionName: String?, e: ActionEvent?) {
        // These actions activate whether textEntry is set or not
        when (actionName) {
            "Escape" -> operationEscape()
            "Up" -> operationCursorMove(Pos.UP, true)
            "Down" -> operationCursorMove(Pos.DOWN, true)
            "Left" -> operationCursorMove(Pos.LEFT, true)
            "Right" -> operationCursorMove(Pos.RIGHT, true)
            "Alt-Up" -> operationCursorMove(Pos.ALT_UP, true)
            "Alt-Down" -> operationCursorMove(Pos.ALT_DOWN, true)
            "Alt-Left" -> operationCursorMove(Pos.ALT_LEFT, true)
            "Alt-Right" -> operationCursorMove(Pos.ALT_RIGHT, true)
            "Shift-Up" -> operationExitJump(0)
            "Shift-Down" -> operationExitJump(1)
            "Shift-Left" -> operationExitJump(2)
            "Shift-Right" -> operationExitJump(3)
            "Ctrl-Shift-Up" -> operationExitCreate(0)
            "Ctrl-Shift-Down" -> operationExitCreate(1)
            "Ctrl-Shift-Left" -> operationExitCreate(2)
            "Ctrl-Shift-Right" -> operationExitCreate(3)
            "Tab" -> operationToggleDrawing()
            "Home" -> operationCursorMove(Pos.HOME, false)
            "End" -> operationCursorMove(Pos.END, false)
            "Insert" -> operationBufferGrab()
            "Ctrl-=" -> operationZoomIn(false)
            "Ctrl--" -> operationZoomOut(false)
            "Ctrl-X" -> operationBufferSwapColour()
            "Ctrl-Y" -> undoHandler.operationRedo()
            "Ctrl-Z" -> undoHandler.operationUndo()
            "F1" -> menuHelp()
            "F2" -> operationToggleText()
            "F3" -> operationF(3)
            "F4" -> operationF(4)
            "F5" -> operationF(5)
            "F6" -> operationF(6)
            "F7" -> operationF(7)
            "F8" -> operationF(8)
            "F9" -> operationF(9)
            "F10" -> operationF(10)
            "F12" -> takeScreenshot()
            "Shift-F1" -> operationShowTileTypes(SHOW_STATS)
            "Shift-F2" -> operationShowTileTypes(SHOW_OBJECTS)
            "Shift-F3" -> operationShowTileTypes(SHOW_INVISIBLES)
            "Shift-F4" -> operationShowTileTypes(SHOW_EMPTIES)
            "Shift-F5" -> operationShowTileTypes(SHOW_FAKES)
            "Shift-F6" -> operationShowTileTypes(SHOW_EMPTEXTS)
            "Alt-F12" -> takeScreenshotToClipboard()
            else -> {}
        }
        if (!textEntry) {
            when (actionName) {
                "Space" -> operationBufferPut()
                "Delete" -> operationDelete()
                "Enter" -> operationGrabAndModify(true, false)
                "Ctrl-Enter" -> operationGrabAndModify(true, true)
                "A" -> operationAddBoard()
                "B" -> operationFocusOnBoardSelector()
                "C" -> operationColour()
                "D" -> operationDeleteBoard()
                "F" -> operationFloodfill(cursorPos, false)
                "G" -> world(frame, boards, worldData, canvas)
                "I" -> board(frame, currentBoard, worldData)
                "L" -> menuOpenWorld()
                "P" -> operationModifyBuffer(false)
                "S" -> menuSaveAs()
                "X" -> operationBoardExits()
                "Ctrl-A" -> atlas()
//                "Ctrl-B" -> operationOpenBufferManager()
                "Ctrl-E" -> operationErasePlayer()
                "Ctrl-P" -> operationModifyBuffer(true)
                "Ctrl-R" -> atlasRemoveBoard()
                "Ctrl-S" -> menuSave()
                "Ctrl-V" -> operationPasteImage()
                "Alt-B" -> operationBlockStart()
                "Alt-F" -> operationFloodfill(cursorPos, true)
                "Alt-I" -> menuImportBoard()
                "Alt-M" -> operationGrabAndModify(false, false)
                "Alt-S" -> operationStatList()
                "Alt-T" -> operationTestWorld()
                "Alt-X" -> menuExportBoard()
                "Shift-B" -> menuBoardList()
                "Ctrl-Alt-M" -> operationGrabAndModify(false, true)
                "0" -> bufferOperation.operationGetFromBuffer(0)
                "1" -> bufferOperation.operationGetFromBuffer(1)
                "2" -> bufferOperation.operationGetFromBuffer(2)
                "3" -> bufferOperation.operationGetFromBuffer(3)
                "4" -> bufferOperation.operationGetFromBuffer(4)
                "5" -> bufferOperation.operationGetFromBuffer(5)
                "6" -> bufferOperation.operationGetFromBuffer(6)
                "7" -> bufferOperation.operationGetFromBuffer(7)
                "8" -> bufferOperation.operationGetFromBuffer(8)
                "9" -> bufferOperation.operationGetFromBuffer(9)
                "Ctrl-0" -> operationSaveToBuffer(0)
                "Ctrl-1" -> operationSaveToBuffer(1)
                "Ctrl-2" -> operationSaveToBuffer(2)
                "Ctrl-3" -> operationSaveToBuffer(3)
                "Ctrl-4" -> operationSaveToBuffer(4)
                "Ctrl-5" -> operationSaveToBuffer(5)
                "Ctrl-6" -> operationSaveToBuffer(6)
                "Ctrl-7" -> operationSaveToBuffer(7)
                "Ctrl-8" -> operationSaveToBuffer(8)
                "Ctrl-9" -> operationSaveToBuffer(9)
                else -> {}
            }
        }
    }

    internal val clipboardImage: Image?
        get() {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null)
            return try {
                contents.getTransferData(DataFlavor.imageFlavor) as Image
            } catch (e: UnsupportedFlavorException) {
                null
            } catch (e: IOException) {
                null
            }
        }

    fun prefix(): String {
        return if (worldData.isSuperZZT) "SZZT_" else "ZZT_"
    }


    internal fun changeZoomLevel(zoomChange: Int, mouse: Boolean) {
        val zoomFactor = GlobalEditor.getDouble("ZOOM_FACTOR", sqrt(2.0))
        val minZoom = GlobalEditor.getDouble("MIN_ZOOM", 0.0625)
        val maxZoom = GlobalEditor.getDouble("MAX_ZOOM", 8.0)
        var newZoom = zoom

        if (zoomChange == 1) {
            newZoom *= zoomFactor
        } else if (zoomChange == -1) {
            newZoom /= zoomFactor
        } else if (zoomChange == 0) {
            newZoom = 1.0
        }

        // Find nearest zoom level
        var iterZoom = 1.0
        var iterZoomFactor = zoomFactor
        if (newZoom < iterZoom) iterZoomFactor = 1.0 / iterZoomFactor

        while (true) {
            val iterZoomNew = iterZoom * iterZoomFactor
            if (abs(iterZoom - newZoom) < abs(iterZoomNew - newZoom)) {
                zoom = iterZoom
                break
            }
            iterZoom = iterZoomNew
        }
        newZoom = clamp(newZoom, minZoom, maxZoom)
        val zoomDiff = newZoom - zoom
        if (abs(zoomDiff) > 0.0001) {
            zoom = newZoom
        }

        val centreOnPos: Pos
        if (mouse) {
            centreOnPos = mousePos
        } else {
            centreOnPos = cursorPos
        }

        invalidateCache()
        afterModification()
        canvas.revalidate()
        if (frame.extendedState == Frame.NORMAL) {
            frame.pack()
        }
        centreOn(centreOnPos)
        canvas.recheckMouse()
    }

    private fun centreOn(pos: Pos) {
        var xyPos = Pos(
            canvas.getCharW(pos.x),
            canvas.getCharH(pos.y))
        var xySize = Pos(
            canvas.getCharW(1),
            canvas.getCharH(1))
        val xyAdd = Pos(
            canvas.visibleRect.width / 2,
            canvas.visibleRect.height / 2)

        xySize += xyAdd * 2
        xyPos -= xyAdd

        canvas.scrollRectToVisible(Rectangle(xyPos.x, xyPos.y, xySize.x, xySize.y))
    }

    internal fun setBlockStart(pos: Pos) {
        blockStartPos = pos
        canvas.setSelectionBlock(blockStartPos)
    }

    internal fun setBlockBuffer(dim : Dim, ar: Array<Tile>?, r: Boolean) {
        GlobalEditor.setBlockBuffer(dim, ar, r, worldData.isSuperZZT)
        canvas.repaint()
    }

    private val blockPos: Pos
        get() = cursorPos.min(blockStartPos)
    private val blockPos2: Pos
        get() = cursorPos.max(blockStartPos)

    private fun afterBlockOperation(modified: Boolean) {
        Logger.i(TAG) { "block operation $cursorPos $blockStartPos"}
        cursorPos = cursorPos.min(blockStartPos)
        canvas.setCursor(cursorPos)
        setBlockStart(Pos.NEG_ONE)
        if (modified) afterModification()
        else afterUpdate()
    }

    internal fun blockClear() {
        val tile = Tile(0, 0)
        addRedraw(blockPos, blockPos2)
        for (y in blockPos.y..blockPos2.y) {
            for (x in blockPos.x..blockPos2.x) {
                putTileAt(Pos(x,y), tile, PUT_REPLACE_BOTH)
            }
        }

        afterBlockOperation(true)
    }

    internal fun blockPaint() {
        val paintCol = getTileColour(bufferTile!!)
        addRedraw(blockPos, blockPos2)
        for (y in blockPos.y..blockPos2.y) {
            for (x in blockPos.x..blockPos2.x) {
                val tile = checkNotNull(getTileAt(Pos(x,y), false))
                paintTile(tile, paintCol)
                putTileAt(Pos(x,y), tile, PUT_REPLACE_BOTH)
            }
        }
        afterBlockOperation(true)
    }

    internal fun blockCopy(repeated: Boolean) {
        Logger.i(TAG) { "blockCopy, $blockPos, $blockPos2"}
        val dim = (blockPos2 + 1 - blockPos).dim
        val blockBuffer = arrayOfNulls<Tile>(dim.w * dim.h)
        for (y in 0 until dim.h) {
            for (x in 0 until dim.w) {
                val pos = Pos(x,y) + blockPos
                val idx = pos.arrayPos(dim.w)
                blockBuffer[idx] = getTileAt(pos, true)
            }
        }
        // TODO(jakeouellette): Cleanup null check cast.
        setBlockBuffer(dim, blockBuffer.map { tile: Tile? -> tile!! }.toTypedArray(), repeated)
        afterBlockOperation(false)
    }

    internal fun setMoveBlock(dim : Dim) {
        Logger.i(TAG) { "setMoveBlock [w, h, mbw, mbh, mbx, mby] : $dim $moveBlockDim, $moveBlockPos"}
        moveBlockDim = dim
        canvas.setPlacingBlock(dim)
    }

    internal fun blockMove() {
        moveBlockPos = blockPos
        val dim = (blockPos2 + 1 - blockPos).dim
        setMoveBlock(dim)
        afterBlockOperation(false)
    }

    internal fun blockFinishMove() {
        // Move from moveBlockX, moveBlockY, moveBlockW, moveBlockH, cursorX, cursorY
        Logger.i(TAG) { "blockMove! $blockPos $blockPos2"}
        val blockMap = LinkedHashMap<ArrayList<Board?>, LinkedHashMap<Pos?, Pos?>>()
        addRedraw(cursorPos, cursorPos + moveBlockDim - 1)
        addRedraw(moveBlockPos, moveBlockPos + moveBlockDim - 1)
        for (vy in 0 until moveBlockDim.h) {
            for (vx in 0 until moveBlockDim.w) {
                // The move order depends on the relationship between the two blocks, to avoid double moving
                val pos = Pos(
                    if (moveBlockPos.x >= cursorPos.x) vx else moveBlockDim.w - 1 - vx,
                    if (moveBlockPos.y >= cursorPos.y) vy else moveBlockDim.h - 1 - vy)
                val xyFrom = pos + moveBlockPos
                val xyTo = pos + cursorPos
                if (xyFrom.lt(dim)) {
                    if (xyTo.lt(dim)) {
                        val boardKey = ArrayList<Board?>(2)
                        boardKey.add(getBoardAt(xyFrom))
                        boardKey.add(getBoardAt(xyTo))
                        if (!blockMap.containsKey(boardKey)) {
                            blockMap[boardKey] = LinkedHashMap()
                        }
                        blockMap[boardKey]!![xyFrom] = xyTo
                    }
                }
            }
        }

        blockTileMove(blockMap, false)

        setMoveBlock(Dim(0, 0))
        afterBlockOperation(true)
    }

    internal fun blockFlip(horizontal: Boolean) {
        val hxy = (blockPos + blockPos2) / 2
        val blockMap = LinkedHashMap<ArrayList<Board?>, LinkedHashMap<Pos?, Pos?>>()
        addRedraw(blockPos, blockPos2)
        for (y in blockPos.y..blockPos2.y) {
            for (x in blockPos.x..blockPos2.x) {
                val xy = Pos(x,y)
                if ((horizontal && x <= hxy.x) || (!horizontal && y <= hxy.y)) {
                    val xyTo = if (horizontal) blockPos2 - (xy - blockPos) else xy

                    val boardKey = ArrayList<Board?>(2)
                    boardKey.add(getBoardAt(xy))
                    boardKey.add(getBoardAt(xyTo))
                    val from = xy
                    val to = xyTo

                    if (!blockMap.containsKey(boardKey)) {
                        blockMap[boardKey] = LinkedHashMap()
                    }
                    blockMap[boardKey]!![from] = to
                }
            }
        }

        blockTileMove(blockMap, true)
        afterBlockOperation(true)
    }

    private fun blockTileMove(
        blockMap: LinkedHashMap<ArrayList<Board?>, LinkedHashMap<Pos?, Pos?>>,
        swap: Boolean
    ) {
        val blankTile = Tile(0, 0)
        for (boardKey in blockMap.keys) {
            val tileMoves = blockMap[boardKey]!!
            val fromBoard = boardKey[0]
            val toBoard = boardKey[1]
            if (fromBoard == null || toBoard == null) continue

            val firstFrom = tileMoves.keys.iterator().next()
            val fromBoardXYOffset = firstFrom!! / boardDim

            if (fromBoard === toBoard) {
                if (!swap) {
                    // If stat0 is being overwritten and isn't being moved itself, don't move it
                    val stat0xy = toBoard.getStat(0)!!.pos - 1 + fromBoardXYOffset
                    if (!tileMoves.containsKey(stat0xy)) {
                        for (from in tileMoves.keys) {
                            val to = tileMoves[from]
                            if (to!! == stat0xy) {
                                tileMoves.remove(from)
                                break
                            }
                        }
                    }
                }

                val reverseTileMoves = HashMap<Pos?, Pos?>()
                for (from in tileMoves.keys) {
                    val to = tileMoves[from]
                    reverseTileMoves[to] = from
                }

                // Same board
                val deleteStats = ArrayList<Int>()
                for (i in 0 until toBoard.statCount) {
                    val stat = toBoard.getStat(i)
                    var from: Pos? = stat!!.pos - 1 + fromBoardXYOffset
                    var to = tileMoves[from]
                    if (to != null) {
                        //System.out.printf("Stat %d moving from %d,%d to %d,%d\n", i, stat.getX(), stat.getY(), to.get(0) % boardW + 1, to.get(1) % boardH + 1);
                        stat.pos = to % boardDim + 1
                        continue
                    }
                    to = from
                    from = reverseTileMoves[to]
                    if (from != null) {
                        if (swap) {
                            //System.out.printf("Stat %d moving from %d,%d to %d,%d !\n", i, stat.getX(), stat.getY(), from.get(0) % boardW + 1, from.get(1) % boardH + 1);
                            stat.pos = from % boardDim + 1
                        } else {
                            if (i != 0) deleteStats.add(i)
                        }
                    }
                }

                toBoard.directDeleteStats(deleteStats)

                val changingTiles = HashMap<Pos?, Pos>()
                for (from in tileMoves.keys) {
                    val to = tileMoves[from]
                    val id = toBoard.getTileId(from!! % boardDim)
                    val col = toBoard.getTileCol(from % boardDim)
                    changingTiles[to] = Pos(id, col)
                    if (swap) {
                        val tid = toBoard.getTileId(to!! % boardDim)
                        val tcol = toBoard.getTileCol(to!! % boardDim)
                        changingTiles[from] = Pos(tid, tcol)
                    } else {
                        toBoard.setTileRaw(from % boardDim, blankTile.id, blankTile.col)
                    }
                }
                for (to in changingTiles.keys) {
                    val tile = changingTiles[to]!!
                    toBoard.setTileRaw(to!! % boardDim, tile.x, tile.y)
                }
                toBoard.finaliseStats()

                // Copy to buffer
            } else {
                // Different board

                for (from in tileMoves.keys) {
                    val to = tileMoves[from]
                    val tile = getTileAt(from!!, true)
                    if (swap) {
                        val otherTile = getTileAt(to!!, true)
                        putTileAt(from, otherTile, PUT_REPLACE_BOTH)
                    } else {
                        putTileAt(from, blankTile, PUT_REPLACE_BOTH)
                    }
                    putTileAt(to!!, tile, PUT_REPLACE_BOTH)
                }
            }
        }
    }

    internal fun blockPaste() {
        val dim = GlobalEditor.blockBufferDim
        val blockBuffer = GlobalEditor.getBlockBuffer(worldData.isSuperZZT)
        addRedraw(cursorPos, cursorPos + dim - 1)

        // Find the player
        var px = -1
        var py = -1
        for (y in 0 until dim.h) {
            for (x in 0 until dim.w) {
                val xyPos = Pos(x,y) + cursorPos
                if (xyPos.lt(dim)) {
                    val idx = xyPos.arrayPos(dim.w)
                    val t = blockBuffer[idx]
                    val st: List<Stat> = t.stats
                    if (!st.isEmpty()) {
                        if (st[0].isPlayer) {
                            // Player has been found. Record the player's location
                            // then place
                            px = x
                            py = y
                            putTileAt(xyPos, blockBuffer[idx], PUT_REPLACE_BOTH)
                        }
                    }
                }
            }
        }

        for (y in 0 until dim.h) {
            for (x in 0 until dim.w) {
                if (x == px && y == py) {
                    continue  // We have already placed the player
                }
                val xyPos = Pos(x, y) + cursorPos
                if (xyPos.lt(dim)) {
                    val idx = xyPos.arrayPos(dim.w)
                    putTileAt(xyPos, blockBuffer[idx], PUT_REPLACE_BOTH)
                }
            }
        }

        if (!GlobalEditor.blockBufferRepeated) {
            setBlockBuffer(Dim(0, 0), null, false)
        }
        afterBlockOperation(true)
    }


    @Throws(IOException::class)
    fun testCharsetPalette(dir: File, basename: String?, unlinkList: ArrayList<File?>, argList: ArrayList<String?>) {
        // todo(jakeouellette): replaced getPalette with get palette data, may
        // have introduced a bug, check it.
        val palette = canvas.paletteData
        if (palette != Data.DEFAULT_PALETTE) {
            val palFile = Paths.get(dir.path, "$basename.PAL").toFile()
            Files.write(palFile.toPath(), palette)
            unlinkList.add(palFile)
            argList.add("-l")
            argList.add("palette:pal:$basename.PAL")
        }
        val charset = canvas.charset
        if (charset != Data.DEFAULT_CHARSET) {
            val chrFile = Paths.get(dir.path, "$basename.CHR").toFile()
            Files.write(chrFile.toPath(), charset)
            unlinkList.add(chrFile)
            argList.add("-l")
            argList.add("charset:chr:$basename.CHR")
        }
    }

    internal fun launchTest(
        argList: ArrayList<String?>, dir: File, testFile: File, unlinkList: ArrayList<File?>, testBoard: Int,
        inject_P: Boolean, delay_P: Int, inject_Enter: Boolean, delay_Enter: Int
    ) {
        /*
        synchronized (deleteOnClose) {
            for (var unlinkFile : unlinkList) {
                deleteOnClose.add(unlinkFile);
            }
        }
        */
        val testThread = Thread {
            try {
                val worldCopy = worldData.clone()
                worldCopy.currentBoard = testBoard
                val pb = ProcessBuilder(argList)

                if (saveGame(testFile, worldCopy)) {
                    pb.directory(dir)
                    val p = pb.start()
                    if (inject_P || inject_Enter) {
                        val r = Robot()
                        if (inject_P) {
                            r.delay(delay_P)
                            r.keyPress(KeyEvent.VK_P)
                            r.keyRelease(KeyEvent.VK_P)
                        }
                        if (inject_Enter) {
                            r.delay(delay_Enter)
                            r.keyPress(KeyEvent.VK_ENTER)
                            r.keyRelease(KeyEvent.VK_ENTER)
                        }
                    }
                    p.waitFor()
                } else {
                    throw IOException("Error creating test file")
                }


                for (unlinkFile in unlinkList) {
                    if (unlinkFile!!.exists()) {
                        unlinkFile.delete()
                        /*
                        synchronized (deleteOnClose) {
                            deleteOnClose.remove(unlinkFile);
                        }
                        */
                    }
                }
            } catch (e: IOException) {
                JOptionPane.showMessageDialog(frame, e)
            } catch (e: AWTException) {
                JOptionPane.showMessageDialog(frame, e)
            } catch (ignored: InterruptedException) {
            }
        }
        testThreads.add(testThread)
        testThread.start()
    }


    internal fun blankBoard(name: String): Board {
        return if (worldData.isSuperZZT) {
            SZZTBoard(name)
        } else {
            ZZTBoard(name)
        }
    }


    internal fun erasePlayer(board: Board?) {
        val cornerPos = Pos(board!!.dim.w + 1,0)

        val stat0 = board.getStat(0)

        // If the player is already in the corner, do nothing
        if (stat0!!.pos == cornerPos) return

        val oldStat0 = stat0.pos - 1

        // Replace player with under tile
        val under = Tile(stat0.uid, stat0.uco)
        stat0.pos = cornerPos

        board.setTile(oldStat0, under)

        /*
        // Fix uid/uco
        stat0.setUid(placingTile.getStats().get(0).getUid());
        stat0.setUco(placingTile.getStats().get(0).getUco());
        // Place the tile here, but without stat0
        placingTile.getStats().remove(0);
        addRedraw(x, y, x, y);
        board.setTileDirect(x % boardW, y % boardH, placingTile);
        // Then move stat0 to the cursor
        stat0.setX(x % boardW + 1);
        stat0.setY(y % boardH + 1);
        */
    }

    override fun onBoardUpdated(newWorldData: WorldData, newBoardList: List<Board>, newCurrentBoardIdx: Int) {
        this.worldData = newWorldData
        this.replaceBoardList(newBoardList)
        this.changeBoard(newCurrentBoardIdx)
    }

    // TODO(jakeouellette): Make this behave a bit more reactive
    internal fun onBoardsUpdated(boards: ArrayList<Board>) {
        Logger.i(TAG) { "Boards updated. ${boards.size}" }
        this.boardListPane.remove(boardSelector)
        this.boards = boards
        this.boardSelectorComponent = createBoardSelector()
        this.boardSelector = JScrollPane(boardSelectorComponent)
        this.boardListPane.add(boardSelector, BorderLayout.CENTER)
    }

    private fun createBoardSelector(): BoardSelector {
        return BoardSelector(
                this.canvas,
                this.boardIdx,
                this.frameForRelativePositioning,
                { this.operationAddBoard() },
            {this.operationFocusOnBoardSelector()},
                boards,
                { e: ActionEvent ->
                    val newBoardIdx = e.actionCommand.toInt()
                    changeBoard(newBoardIdx)
                })
    }

    private fun mouseDraw() {
        val dirty = HashSet<Board>()
        // TODO(jakeouellette): change to .isPositive
        if (!oldMouseCoord.isPositive) {
            mousePlot(mouseCoord, dirty)
        } else {
            var cxy = Pos.NEG_ONE
            val dxy = mouseCoord - oldMouseCoord
            val dist = dxy.distInt()
            if (dist == 0) return
            val plotSet = HashSet<Pos>()
            //int cw = canvas.getCharW(), ch = canvas.getCharH();
            for (i in 0..dist) {
                val xy = dxy * i / dist + oldMouseCoord
                val ncxy = canvas.toChar(xy)
                if (ncxy != cxy) {
                    cxy = ncxy
                    plotSet.add(cxy)
                }
            }
            for (plot in plotSet) {
                mousePlot(plot, dirty)
            }
        }
        for (board in dirty) {
            board.finaliseStats()
        }
        afterModification()
        canvas.setCursor(cursorPos)
    }

    private fun mouseMove(): Boolean {
        val pos = mousePos
        if (pos.inside(dim)) {
            cursorPos = pos
            canvas.setCursor(cursorPos)
            afterUpdate()
            return true
        }
        return false
    }

    private fun mouseGrab() {
        if (mouseMove()) {
            bufferTile = getTileAt(cursorPos, true)
            afterUpdate()
        }
    }

    private fun mouseCharX(x: Int): Int {
        return canvas.toCharX(x)
    }

    private fun mouseCharY(y: Int): Int {
        return canvas.toCharY(y)
    }

    private fun mousePlot(xy: Pos, dirty: HashSet<Board>) {
        if (xy.inside(dim)) {
            cursorPos = xy
            canvas.setCursor(cursorPos)
            val board = putTileDeferred(cursorPos, bufferTile, PUT_DEFAULT)
            if (board != null) dirty.add(board)
        }
    }


    private fun getKeystroke(stroke: String): KeyStroke = getKeyStroke(this.globalEditor, stroke)
    internal fun moveStatTo(board: Board, src: Int, destination: Int): Boolean {
        var destination = destination
        if (src < 1) {
            return false
        }
        if (destination < 1) {
            destination = 1
        }
        val length = board.statCount
        if (destination >= length) {
            destination = length - 1
        }
        if (destination == src) {
            return false
        }
        board.moveStatTo(src, destination)
        return true
    }

    internal fun openCurrentTileEditor(
        callback: TileEditorCallback,
        advanced: Boolean,
        exempt: Boolean,
        tile: Tile,
    ) =
        createTileEditor(
            board = currentBoard,
            pos = Pos(x = -1, y = -1),
            stats = null,
            tile = tile,
            advanced = advanced,
            exempt = exempt,
            callback = callback,
            selected = -1
        )

    internal fun createTileEditor(
        callback: TileEditorCallback,
        advanced: Boolean,
        exempt: Boolean,
        board: Board?,
        pos : Pos,
        stats: List<Stat>? = null,
        tile: Tile? = null,
        selected: Int = -1,
    ) =
        TileEditor(
            this,
            this.boardPosOffset,
            this.boardIdx,
            this.worldData.isSuperZZT,
            this.boards,
            this.canvas,
            this.frameForRelativePositioning,
            board!!,
            tile,
            stats,
            callback,
            pos,
            advanced,
            selected,
            exempt,
            { xys -> this.canvas.setIndicate(xys) },
            this::getKeystroke
        )


    internal fun setStats(board: Board, boardPos : Pos, xy: Pos, stats: List<Stat>) {
        // If any stats move, invalidate the entire board
        var invalidateAll = false

        val destStats = board.getStatsAt(xy)
        val statsToDelete = ArrayList<Int>()
        val statsAdded = BooleanArray(stats.size)
        var mustFinalise = false

        for (destStat in destStats) {
            var replacementStat: Stat? = null
            var replacementStatMoved = false
            for (i in stats.indices) {
                val stat = stats[i]
                if (stat.statId == destStat.statId && stat.statId != -1) {
                    if (stat.pos != destStat.pos) {
                        replacementStatMoved = true
                    }
                    replacementStat = stat
                    statsAdded[i] = true
                    break
                }
            }
            if (replacementStat != null) {
                if (board.directReplaceStat(destStat.statId, replacementStat)) {
                    if (replacementStatMoved) invalidateAll = true
                    mustFinalise = true
                }
            } else {
                statsToDelete.add(destStat.statId)
            }
        }

        if (board.directDeleteStats(statsToDelete)) {
            mustFinalise = true
        }

        for (i in stats.indices) {
            if (!statsAdded[i]) {
                val stat = stats[i]
                if (stat.pos != (xy + 1)) invalidateAll = true
                board.directAddStat(stat)
                mustFinalise = true
            }
        }



        if (mustFinalise) {
            board.finaliseStats()
        }
        if (invalidateAll) {
            addRedraw(boardPos + 1, boardPos + boardDim - 2)
        }
    }

    internal fun getBoardAt(pos: Pos): Board? {
        if (pos.outside(dim)) {
            throw IndexOutOfBoundsException("Attempted to getBoardAt() coordinate off map")
        }
        val gridPos = pos / boardDim
        val boardIdx = grid[gridPos.y][gridPos.x]
        if (boardIdx == -1) return null
        return boards[boardIdx]
    }

    /**
     *
     * @param putMode PUT_DEFAULT or PUT_PUSH_DOWN or PUT_REPLACE_BOTH
     */
    internal fun putTileAt(pos : Pos, tile: Tile?, putMode: Int) {
        val board = putTileDeferred(pos, tile, putMode)
        board?.finaliseStats()
    }

    /**
     *
     * @param putMode PUT_DEFAULT or PUT_PUSH_DOWN or PUT_REPLACE_BOTH
     */
    internal fun putTileDeferred(pos : Pos, tile: Tile?, putMode: Int): Board? {
        var putMode = putMode
        val board = getBoardAt(pos)
        var currentTile = getTileAt(pos, false)
        if (board != null && currentTile != null && tile != null) {
            val placingTile = tile.clone()
            // First, we will not allow putTileAt to erase stat 0
            val currentTileStats: List<Stat> = currentTile.stats
            if (currentTileStats.isNotEmpty()) {
                if (currentTileStats[0].statId == 0) {
                    return board
                }
            }

            if (putMode == PUT_DEFAULT || putMode == PUT_PUSH_DOWN) {
                val tileStats: List<Stat> = placingTile.stats
                // Only check if we have exactly 1 stat
                if (tileStats.size == 1) {
                    val tileStat = tileStats[0]

                    if (putMode == PUT_DEFAULT) {
                        // If the tile currently there is floor, we will push it down
                        if (ZType.isFloor(worldData.isSuperZZT, currentTile)) {
                            putMode = PUT_PUSH_DOWN
                        } else {
                            // Not a floor, so does it have one stat? If so, still push down (we will use its uid/uco)
                            if (currentTileStats.size == 1) {
                                putMode = PUT_PUSH_DOWN
                                // replace currentTile with what was under it
                                currentTile = Tile(currentTileStats[0].uid, currentTileStats[0].uco)
                            }
                        }
                    }
                    if (putMode == PUT_PUSH_DOWN) {
                        if (placingTile.col < 16) {
                            placingTile.col = (currentTile.col and 0x70) or placingTile.col
                        }
                        tileStat.uid = currentTile.id
                        tileStat.uco = currentTile.col
                    }
                }
            }
            // Are we placing stat 0?
            if (placingTile.stats.isNotEmpty()) {
                if (placingTile.stats[0].statId == 0 ||
                    placingTile.stats[0].isPlayer
                ) {
                    // Find the stat 0 on this board
                    val stat0 = board.getStat(0)
                    val oldStat0pos = Pos(stat0!!.x - 1,stat0.y - 1)
                    // If stat 0 isn't on the board, nevermind!

                    if (oldStat0pos.inside(0, 0, boardDim.w-1, boardDim.h-1)) {
                        // See what other stats are there
                        val oldStat0TileStats = board.getStatsAt(oldStat0pos)
                        if (oldStat0TileStats.size == 1) {
                            // Once we move stat0 there will be no other stats, so erase this
                            board.setTileRaw(oldStat0pos, stat0.uid, stat0.uco)
                            // TODO(jakeouellette): Not sure I understood the math here.
                            val bPos = pos / boardDim * boardDim
                            val redrawPos = oldStat0pos + bPos
                            addRedraw(redrawPos, redrawPos)
                        } // Otherwise there are stats left, so leave the tile alone
                    }
                    // Fix uid/uco
                    stat0.uid = placingTile.stats[0].uid
                    stat0.uco = placingTile.stats[0].uco
                    // Place the tile here, but without stat0
                    placingTile.stats.removeAt(0)
                    addRedraw(pos, pos)
                    board.setTileDirect(pos % boardDim, placingTile)
                    // Then move stat0 to the cursor
                    stat0.pos = pos % boardDim + 1

                    return board
                }
            }
            addRedraw(pos, pos)
            board.setTileDirect(pos % boardDim, placingTile)
        }
        return board
    }

    private fun updateCurrentBoard() {
        cursorPos = cursorPos.clamp(0, dim.asPos - 1)
        val gridPos = cursorPos / boardDim

        setCurrentBoard(grid[gridPos.y][gridPos.x])
        val worldName = if (path == null) "new world" else path!!.name
        val boardInfo = if (currentBoard != null) {
            "Board #" + boardIdx + " :: " + toUnicode(currentBoard!!.getName())
        } else {
            "(no board)"
        }
        frame.title = "zloom2 [" + worldName + "] :: " + boardInfo + (if (isDirty) "*" else "")
    }

    internal fun getTileAt(pos : Pos, copy: Boolean): Tile? {
        if (pos.outside(dim)) {
            throw IndexOutOfBoundsException("Attempted to read coordinate off map, pos: $pos, dim: $dim")
        }
        val boardPos = pos % boardDim
        val board = getBoardAt(pos)
        return board?.getTile(boardPos, copy)
    }

    internal fun afterModification() {
        drawBoard()
        undoHandler.afterModification()
        afterUpdate()
    }

    private fun scrollToCursor() {
        var dim : Dim
        var pos : Pos
        if (centreView) {
            canvas.revalidate()
            val visibleRect = canvas.visibleRect

            // TODO(jakeouellette): Convert to convienence
            dim = Dim(clamp(visibleRect.width, 0, canvas.getCharW(this@WorldEditor.dim.w)),
                clamp(visibleRect.height, 0, canvas.getCharH(this@WorldEditor.dim.h)))
            val charDim = Dim(
                canvas.getCharW(1),
                canvas.getCharH(1))
            pos =
               Pos(max(0,
                    (canvas.getCharW(cursorPos.x) + (charDim.w - dim.w) / 2)),
                   max(0,canvas.getCharH(cursorPos.y) + (charDim.h - dim.h) / 2))
            centreView = false
        } else {
            dim = Dim(canvas.getCharW(1), canvas.getCharH(1))
            pos = Pos(canvas.getCharW(cursorPos.x),canvas.getCharH(cursorPos.y))

            // FIXME(jakeouellette): This math can allow a scroll to slightly below / above the view.
            // Expand this slightly
            val EXPAND_X = 4
            val EXPAND_Y = 4
            pos -= Pos(canvas.getCharW(EXPAND_X), canvas.getCharH(EXPAND_Y))
            dim += Dim(canvas.getCharW(EXPAND_X * 2), canvas.getCharH(EXPAND_Y * 2))
        }
        val rect = Rectangle(pos.x, pos.y, dim.w, dim.h)
        canvas.scrollRectToVisible(rect)
    }

    internal fun afterChangeShowing() {
        invalidateCache()
        afterModification()
    }

    internal fun afterUpdate() {
        updateTimestamp()
        undoHandler.afterUpdate()

        updateCurrentBoard()
        scrollToCursor()
        val boardPos = cursorPos % boardDim

        var s = ""
        if (currentBoard != null) {
            var boardNameFieldLen = 22
            if (boardIdx < 100) boardNameFieldLen++
            if (boardIdx < 10) boardNameFieldLen++
            val boardName = toUnicode(currentBoard!!.getName())
            boardNameFieldLen = min(boardNameFieldLen.toDouble(), boardName.length.toDouble()).toInt()
            val limitedName = boardName.substring(0, boardNameFieldLen)

            s = String.format(
                """
    Stat:  %3d/%d
    X/Y:   %d,%d
    B.Mem: %5d
    
    """.trimIndent(),
                currentBoard!!.statCount - 1, if (!worldData.isSuperZZT) 150 else 128,
                boardPos.x + 1, boardPos.y + 1, currentBoard!!.currentSize
            )
        }

        s += String.format("W.Mem: %6d", worldSize)

        infoBox.text = s

        updateEditingMode()

        bufferPane.removeAll()
        bufferPaneContents = bufferPane
        blinkingImageIcons.clear()
        val cursorTile = getTileAt(cursorPos, false)
        addCursorTileInfoDisplay("Cursor", cursorTile, boardPos, cursorPos)
        brushesPane.onBufferTileUpdated(bufferTile)
        bufferPane.repaint()
    }

    enum class EditType {
        TEXT_ENTRY, DRAWING, SELECTING, EDITING
    }

    private fun getBrushMode(): EditType {
        if (textEntry) {
            return EditType.TEXT_ENTRY
        } else if (drawing) {
            return EditType.DRAWING
        } else if (GlobalEditor.isBlockBuffer() || moveBlockDim.w != 0) {
            return EditType.SELECTING
        }

        return EditType.EDITING
    }

    private fun updateEditingMode() {
        val enter = keyStrokeString(getKeyStroke(globalEditor, "Enter"))
        val brushMode = getBrushMode()
        if (brushMode == EditType.TEXT_ENTRY) editingModePane.display(Color.YELLOW, "Type to place text")
        // TODO(jakeouellette): Replace to .isPositive
        else if (blockStartPos.x != -1) editingModePane.display(
            arrayOf(Color(0, 127, 255), Color.CYAN),
            "$enter on other corner"
        )
        else if (brushMode == EditType.DRAWING) editingModePane.display(Color.GREEN, "Drawing")
        else if (brushMode == EditType.SELECTING) editingModePane.display(
            arrayOf(
                Color.ORANGE,
                Color.RED
            ), "$enter to place block"
        )
        else if (currentlyShowing == SHOW_STATS) editingModePane.display(
            arrayOf(Color.YELLOW, Color.RED),
            "Showing Stats"
        )
        else if (currentlyShowing == SHOW_OBJECTS) editingModePane.display(
            arrayOf(Color.GREEN, Color.BLUE),
            "Showing Objects"
        )
        else if (currentlyShowing == SHOW_INVISIBLES) editingModePane.display(
            arrayOf(Color.CYAN, Color.MAGENTA),
            "Showing Invisibles"
        )
        else if (currentlyShowing == SHOW_EMPTIES) editingModePane.display(
            arrayOf(Color.LIGHT_GRAY, Color.GRAY),
            "Showing Empties"
        )
        else if (currentlyShowing == SHOW_FAKES) editingModePane.display(
            arrayOf(Color.LIGHT_GRAY, Color.GRAY),
            "Showing Fakes"
        )
        else if (currentlyShowing == SHOW_EMPTEXTS) editingModePane.display(
            arrayOf(Color.LIGHT_GRAY, Color.GRAY),
            "Showing Empties as Text"
        )
        else editingModePane.display(Color.BLUE, "Editing")
    }


    private fun addCursorTileInfoDisplay(
        title: String,
        cursorTile: Tile?,
        boardPos : Pos,
        cursorPos : Pos
    ) {
        val cb = this.currentBoard
        if (cursorTile == null || cb == null) return
        val tileInfoPanel = TileInfoPanel(dosCanvas = canvas, worldData, title, cursorTile, cb, { b -> })

        //int w = bufferPane.getWidth() - 16;
        //tileInfoPanel.setPreferredSize(new Dimension(w, tileInfoPanel.getPreferredSize().getHeight()));
        bufferPaneContents.add(tileInfoPanel, BorderLayout.NORTH)
        val childPanel = JPanel(BorderLayout())
        bufferPaneContents.add(childPanel, BorderLayout.CENTER)

        if (cb != null && (cursorTile.id == ZType.OBJECT || cursorTile.id == ZType.SCROLL)) {
            val board = cb
            val editButton = JButton("Edit Code")

            editButton.addActionListener {
                CodeEditorFactory.create(
                    Pos.NEG_ONE, true, frame, this@WorldEditor,
                    IconFactory.getIcon(worldData.isSuperZZT, cursorTile, this@WorldEditor), cb, cursorTile.stats[0]
                ) { e ->
                    if (e!!.actionCommand == "update") {
                        val source = e.source as CodeEditor

                        val cloneOfFirst = cursorTile.stats[0].clone()
                        cloneOfFirst.code = source.code

                        val mutableStats = mutableListOf<Stat>()
                        mutableStats.addAll(cursorTile.stats)
                        mutableStats.set(0, cloneOfFirst)

                        setStats(board, boardPos, cursorPos, mutableStats)
                        // TODO(jakeouellette): Decide if this is needed
                        // (e.source as StatSelector).dataChanged()
                        afterModification()
                    }
                }
            }
            bufferPaneContents.add(editButton, BorderLayout.SOUTH)
        }
        bufferPaneContents = childPanel
    }

    private val worldSize: Int
        get() {
            var size = worldData.boardListOffset()
            for (board in boards) {
                size += board.currentSize
            }
            return size
        }

    val boardPosOffset: Pos
        get() = cursorPos / boardDim * boardDim

    fun replaceBoardList(newBoardList: List<Board>) {
        atlases.clear()
        currentAtlas = null
        // TODO(jakeouellette): Update board selector
        onBoardsUpdated(boards)
    }

    fun mouseMotion(e: MouseEvent, heldDown: Int) {
        mouseState = heldDown
        mouseCoord = Pos(e.x, e.y)
        mousePos = canvas.toChar(mouseCoord)

        // Translate into local space
        mouseScreenPos = Pos(
            e.xOnScreen - frame.locationOnScreen.x,
            e.yOnScreen - frame.locationOnScreen.y
        )
        when (heldDown) {
            1 -> {
                mouseDraw()
                oldMouseCoord = mouseCoord
            }
            2 -> {
                mouseGrab()
                oldMouseCoord = Pos.NEG_ONE
            }
            3 -> {
                mouseMove()
                oldMouseCoord = Pos.NEG_ONE
            }
        }

        undoHandler.afterUpdate()
    }

    private fun removeAtlas() {
        val pos = cursorPos / boardDim
        var changeTo = grid[pos.y][pos.x]
        if (changeTo == -1) {
            for (row in grid) {
                for (brd in row) {
                    if (brd != -1) {
                        changeTo = brd
                        break
                    }
                }
                if (changeTo != -1) break
            }
        }
        val atlas = atlases[changeTo]
        val removeThese = ArrayList<Int>()
        for (i in atlases.keys) {
            if (atlases[i] == atlas) removeThese.add(i)
        }
        for (i in removeThese) {
            atlases.remove(i)
        }
        currentAtlas = null
        changeBoard(changeTo)
    }

    internal fun atlas() {
        if (currentBoard == null) return
        if (currentAtlas != null) {
            removeAtlas()
            return
        }

        val boardsSeen = HashSet<Board?>()
        boardsSeen.add(currentBoard)
        val map = HashMap<Pos, Board?>()
        map[Pos(0, 0)] = currentBoard
        val stack = ArrayDeque<Any?>()
        // TODO(jakeouellette): Can this stack be, instead, of pos?
        stack.add(0)
        stack.add(0)
        stack.add(currentBoard)
        while (true) {
            // Board exits go NORTH, SOUTH, WEST, EAST
            val dir = arrayOf(Pos.UP, Pos.DOWN,Pos.LEFT, Pos.RIGHT)
            if (stack.isEmpty()) break
            val pos = Pos(stack.pop() as Int, stack.pop() as Int)
            val board = stack.pop() as Board

            for (exit in 0..3) {
                val dest = board.getExit(exit)
                if (dest > 0 && dest < boards.size) {
                    val destBoard = boards[dest]
                    if (!boardsSeen.contains(destBoard)) {
                        val dxy = pos + dir[exit]
                        if (!map.containsKey(dxy)) {
                            map[dxy] = destBoard
                            boardsSeen.add(destBoard)
                            stack.add(dxy.x)
                            stack.add(dxy.y)
                            stack.add(destBoard)
                        }
                    }
                }
            }
        }

        var minPos = Pos.ZERO
        var maxPos = Pos.ZERO
        for (loc in map.keys) {
            minPos = minPos.min(loc)
            maxPos = maxPos.max(loc)
            //System.out.printf("%d,%d: %s\n", loc.get(0), loc.get(1), CP437.INSTANCE.toUnicode(map.get(loc).getName()));
        }
        gridDim = (maxPos - minPos + 1).dim
        grid = Array(gridDim.h) { IntArray(gridDim.w) }
        val boardIdLookup = HashMap<Board?, Int>()
        for (i in boards.indices) {
            boardIdLookup[boards[i]] = i
        }
        for (y in 0 until gridDim.h) {
            Arrays.fill(grid[y], -1)
        }
        val atlas = Atlas(gridDim, grid)
        currentAtlas = atlas
        for (loc in map.keys) {
            val xy = loc - minPos
            val board = map[loc]
            val boardIdx = boardIdLookup[board]!!
            grid[xy.y][xy.x] = boardIdx
            atlases[boardIdx] = atlas
        }
        cursorPos = (cursorPos % boardDim) + boardDim * -minPos
        dim = boardDim * gridDim
        canvas.setCursor(cursorPos)
        invalidateCache()
        afterModification()
        canvas.revalidate()
        val rect = Rectangle(
            canvas.getCharW(-minPos.x * boardDim.w),
            canvas.getCharH(-minPos.y * boardDim.h),
            canvas.getCharW(boardDim.w),
            canvas.getCharH(boardDim.h)
        )
        canvas.scrollRectToVisible(rect)
        undoHandler.resetUndoList()
    }

    private fun atlasRemoveBoard() {
        if (currentBoard == null) return
        if (currentAtlas == null) return
        atlases.remove(grid[cursorPos.y / boardDim.h][cursorPos.x / boardDim.w])
        grid[cursorPos.y / boardDim.h][cursorPos.x / boardDim.w] = -1
        var notEmpty = false
        for (y in 0 until gridDim.h) {
            for (x in 0 until gridDim.w) {
                if (grid[y][x] != -1) notEmpty = true
            }
        }
        if (!notEmpty) {
            removeAtlas()
            return
        }
        invalidateCache()
        afterModification()
        canvas.revalidate()
    }

    override fun keyTyped(e: KeyEvent) {
        if (textEntry) {
            val ch = e.keyChar
            if (ch.code < 256) {
                if (ch == '\n') {
                    // TODO(jakeouellette): Convert to convienence
                    cursorPos = Pos(
                        textEntryX,
                        clamp(cursorPos.y + 1, 0, this@WorldEditor.dim.h - 1))
                } else {
                    if (ch.code == 8) { // bksp
                        cursorPos = Pos(
                            clamp(cursorPos.x - 1, 0, this@WorldEditor.dim.w - 1),
                            cursorPos.y)
                    }
                    var col = ch.code
                    if (ch.code == 8 || ch.code == 127) { // bksp or del
                        col = ' '.code
                    }

                    val id = (getTileColour(bufferTile!!) % 128) + 128
                    val textTile = Tile(id, col)
                    putTileAt(cursorPos, textTile, PUT_DEFAULT)

                    if (ch.code != 8 && ch.code != 127) { // not bksp or del
                        // TODO(jakeouellette): Convert to convienence
                        cursorPos = Pos(
                            clamp(cursorPos.x + 1, 0, this@WorldEditor.dim.w - 1),
                            cursorPos.y)
                    }

                    afterModification()
                }
                canvas.setCursor(cursorPos)
            }
        }
    }

    override fun keyPressed(e: KeyEvent) {}
    override fun keyReleased(e: KeyEvent) {}

    private fun JFrame.disableAlt() {
        // From https://stackoverflow.com/a/3994002
        this.addFocusListener(object : FocusListener {
            private val altDisabler = KeyEventDispatcher { e: KeyEvent -> e.keyCode == 18 }

            override fun focusGained(e: FocusEvent) {
                Logger.i(TAG) {"DA: Focus Gained ... $e"}
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(altDisabler)
            }

            override fun focusLost(e: FocusEvent) {
                Logger.i(TAG) {"DA: Focus Lost ... $e"}
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(altDisabler)
            }
        })
    }

    val frameForRelativePositioning: Component
        get() = canvasScrollPane

    fun wheelUp(e: MouseWheelEvent) {
        operationZoomIn(true)
        e.consume()
    }

    fun wheelDown(e: MouseWheelEvent) {
        operationZoomOut(true)
        e.consume()
    }

    //
//    fun removeBufferManager() {
//        currentBufferManager = null
//    }
//
    override fun windowGainedFocus(e: WindowEvent) {
//        if (currentBufferManager != null) currentBufferManager!!.isAlwaysOnTop = true
    }

    override fun windowLostFocus(e: WindowEvent) {
//        if (currentBufferManager != null) currentBufferManager!!.isAlwaysOnTop = false
    }

    fun refreshKeymapping() {
        // change(jakeouellette): was this.layeredpane
        frame.addKeybinds(canvas)
    }

    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
        popupOpen = true
    }

    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) {
        popupOpen = false
        Logger.i(TAG) {"popupMenuWillBecomeInvisible: Requesting Focus."}
        lastFocusedElement.requestFocusInWindow()
    }

    override fun popupMenuCanceled(e: PopupMenuEvent) {
        popupOpen = false
        Logger.i(TAG) {"popupMenuCanceled: Requesting Focus."}
        lastFocusedElement.requestFocusInWindow()
    }

    override fun menuSelected(e: MenuEvent) {
        popupOpen = true
    }

    override fun menuDeselected(e: MenuEvent) {
        popupOpen = false
        Logger.i(TAG) {"menuDeselected: Requesting Focus."}
        lastFocusedElement.requestFocusInWindow()
    }

    override fun menuCanceled(e: MenuEvent) {
        popupOpen = false
        Logger.i(TAG) {"menuCanceled: Requesting Focus."}
        lastFocusedElement.requestFocusInWindow()
    }

    companion object {
        const val BLINK_DELAY: Int = 267

        internal const val PUT_DEFAULT = 1
        private const val PUT_PUSH_DOWN = 2
        internal const val PUT_REPLACE_BOTH = 3

        const val SHOW_NOTHING: Int = 0
        const val SHOW_STATS: Int = 1
        const val SHOW_OBJECTS: Int = 2
        const val SHOW_INVISIBLES: Int = 3
        const val SHOW_EMPTIES: Int = 4
        const val SHOW_EMPTEXTS: Int = 5
        const val SHOW_FAKES: Int = 6
    }
}
