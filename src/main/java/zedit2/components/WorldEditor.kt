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
import zedit2.components.Util.pair
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
import zedit2.util.*
import zedit2.util.CP437.toBytes
import zedit2.util.CP437.toUnicode
import zedit2.util.Constants.EDITOR_FONT
import zedit2.util.FileUtil.getFileChooser
import zedit2.util.Logger.TAG
import java.awt.*
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    internal var cursorX = 0
    internal var cursorY = 0
    private var centreView = false
    internal var blockStartX = -1
    internal var blockStartY = -1
    internal var moveBlockW = 0
    private var moveBlockH = 0
    private var moveBlockX = 0
    private var moveBlockY = 0
    internal var boardW = 0
    internal var boardH = 0
    internal val atlases = HashMap<Int, Atlas>()
    internal var currentAtlas: Atlas? = null
    internal var gridW = 0
    internal var gridH = 0
    internal lateinit var grid: Array<IntArray>
    var width: Int = 0
        private set
    var height: Int = 0
        private set
    internal var drawing = false
        set(value) {
            field = value
            canvas.setDrawing(value)
        }
    internal var textEntry = false
        set(value) {
            field = value
            canvas.setTextEntry(value)
        }
    internal var textEntryX = 0
    internal var fancyFillDialog = false

    private val voidsDrawn = HashSet<ArrayList<Int>>()
    private var redraw = false
    private var redraw_x1 = 0
    private var redraw_x2 = 0
    private var redraw_y1 = 0
    private var redraw_y2 = 0
    private var redraw_width = 0
    private var redraw_height = 0
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

    private var mouseScreenX = 0
    private var mouseScreenY = 0
    private var mousePosX = 0
    private var mousePosY = 0
    private var mouseX = 0
    private var mouseY = 0
    private var oldMousePosX = -1
    private var oldMousePosY = -1
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
        boardW = boards[0].width
        boardH = boards[0].height
        cursorX = boards[cb].getStat(0)!!.x - 1
        cursorY = boards[cb].getStat(0)!!.y - 1
        centreView = true

        updateMenu()
        changeBoard(cb)
    }

    private fun changeToIndividualBoard(newBoardIdx: Int) {
        setCurrentBoard(newBoardIdx)
        atlases.remove(newBoardIdx)
        currentAtlas = null
        gridW = 1
        gridH = 1
        cursorX %= boardW
        cursorY %= boardH
        canvas.setCursor(cursorX, cursorY)
        width = boardW * gridW
        height = boardH * gridH
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
            val x = gridPos[0]
            val y = gridPos[1]
            cursorX = (cursorX % boardW) + x * boardW
            cursorY = (cursorY % boardH) + y * boardH
            canvas.setCursor(cursorX, cursorY)
            setCurrentBoard(newBoardIdx)
            if (atlas != currentAtlas) {
                gridW = atlas.w
                gridH = atlas.h
                grid = atlas.grid
                width = boardW * gridW
                height = boardH * gridH

                currentAtlas = atlas
                invalidateCache()
                afterModification()
                canvas.revalidate()
            }

            val rect = Rectangle(
                canvas.getCharW(x * boardW),
                canvas.getCharH(y * boardH),
                canvas.getCharW(boardW),
                canvas.getCharH(boardH)
            )
            canvas.scrollRectToVisible(rect)
        } else {
            // No atlas, switch to individual board
            changeToIndividualBoard(newBoardIdx)
        }
    }

    internal fun invalidateCache() {
        voidsDrawn.clear()
        redraw_width = 0
        redraw_height = 0
    }

    internal fun addRedraw(x1: Int, y1: Int, x2: Int, y2: Int) {
        // Expand the range by 1 to handle lines
        var x1 = x1
        var y1 = y1
        var x2 = x2
        var y2 = y2
        x1--
        y1--
        x2++
        y2++

        if (!redraw) {
            redraw_x1 = x1
            redraw_y1 = y1
            redraw_x2 = x2
            redraw_y2 = y2
            redraw = true
        } else {
            redraw_x1 = min(redraw_x1.toDouble(), x1.toDouble()).toInt()
            redraw_y1 = min(redraw_y1.toDouble(), y1.toDouble()).toInt()
            redraw_x2 = max(redraw_x2.toDouble(), x2.toDouble()).toInt()
            redraw_y2 = max(redraw_y2.toDouble(), y2.toDouble()).toInt()
        }
    }

    private fun drawBoard() {
        if (width != redraw_width || height != redraw_height) {
            canvas.setDimensions(width, height)
            redraw_width = width
            redraw_height = height
            redraw_x1 = 0
            redraw_y1 = 0
            redraw_x2 = width - 1
            redraw_y2 = height - 1
            redraw = true
        }
        canvas.setZoom(if (worldData.isSuperZZT) zoom * 2 else zoom, zoom)
        canvas.setAtlas(currentAtlas, boardW, boardH, GlobalEditor.getBoolean("ATLAS_GRID", true))
        if (redraw) {
            for (y in 0 until gridH) {
                for (x in 0 until gridW) {
                    val boardIdx = grid[y][x]
                    if (boardIdx != -1) {
                        val x1 = max((x * boardW).toDouble(), redraw_x1.toDouble()).toInt()
                        val x2 = min((x * boardW + (boardW - 1)).toDouble(), redraw_x2.toDouble())
                            .toInt()
                        val y1 = max((y * boardH).toDouble(), redraw_y1.toDouble()).toInt()
                        val y2 = min((y * boardH + (boardH - 1)).toDouble(), redraw_y2.toDouble())
                            .toInt()
                        if (x2 >= x1 && y2 >= y1) {
                            boards[boardIdx].drawToCanvas(
                                canvas, x * boardW, y * boardH,
                                x1 - x * boardW, y1 - y * boardH, x2 - x * boardW, y2 - y * boardH,
                                currentlyShowing
                            )
                        }
                    } else {
                        val voidCoord = pair(x, y)
                        if (!voidsDrawn.contains(voidCoord)) {
                            canvas.drawVoid(x * boardW, y * boardH, boardW, boardH)
                            voidsDrawn.add(voidCoord)
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
                canvas = DosCanvas(this@WorldEditor, zoom)
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
                                -1,
                                -1,
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
                            { operationModifyBuffer(true) },
                            { operationColour() },
                            { operationBufferSwapColour() },
                            onCodeSaved
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
                    this@WorldEditor.canvas.setIndicate(null, null)
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
            m.add("Flood fill", "F") { e: ActionEvent? -> operationFloodfill(cursorX, cursorY, false) }
            m.add("Gradient fill", "Alt-F") { e: ActionEvent? -> operationFloodfill(cursorX, cursorY, true) }
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
                val img = canvas.extractCharImageWH(chr, col, if (szzt) 2 else 1, 1, false, "____\$____", 3, 3)
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
        val board = getBoardAt(cursorX, cursorY)

        if (board != null) {
            putTileAt(cursorX, cursorY, tile, PUT_DEFAULT)
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
            "Up" -> operationCursorMove(0, -1, true)
            "Down" -> operationCursorMove(0, 1, true)
            "Left" -> operationCursorMove(-1, 0, true)
            "Right" -> operationCursorMove(1, 0, true)
            "Alt-Up" -> operationCursorMove(0, -10, true)
            "Alt-Down" -> operationCursorMove(0, 10, true)
            "Alt-Left" -> operationCursorMove(-10, 0, true)
            "Alt-Right" -> operationCursorMove(10, 0, true)
            "Shift-Up" -> operationExitJump(0)
            "Shift-Down" -> operationExitJump(1)
            "Shift-Left" -> operationExitJump(2)
            "Shift-Right" -> operationExitJump(3)
            "Ctrl-Shift-Up" -> operationExitCreate(0)
            "Ctrl-Shift-Down" -> operationExitCreate(1)
            "Ctrl-Shift-Left" -> operationExitCreate(2)
            "Ctrl-Shift-Right" -> operationExitCreate(3)
            "Tab" -> operationToggleDrawing()
            "Home" -> operationCursorMove(-999999999, -999999999, false)
            "End" -> operationCursorMove(999999999, 999999999, false)
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
                "F" -> operationFloodfill(cursorX, cursorY, false)
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
                "Alt-F" -> operationFloodfill(cursorX, cursorY, true)
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

        val centreOnX: Int
        val centreOnY: Int
        if (mouse) {
            centreOnX = mouseX
            centreOnY = mouseY
        } else {
            centreOnX = cursorX
            centreOnY = cursorY
        }

        invalidateCache()
        afterModification()
        canvas.revalidate()
        if (frame.extendedState == Frame.NORMAL) {
            frame.pack()
        }
        centreOn(centreOnX, centreOnY)
        canvas.recheckMouse()
    }

    private fun centreOn(x: Int, y: Int) {
        var xPos = canvas.getCharW(x)
        var yPos = canvas.getCharH(y)
        var xSize = canvas.getCharW(1)
        var ySize = canvas.getCharH(1)
        val xAdd = canvas.visibleRect.width / 2
        val yAdd = canvas.visibleRect.height / 2

        xSize += xAdd * 2
        ySize += yAdd * 2
        xPos -= xAdd
        yPos -= yAdd

        canvas.scrollRectToVisible(Rectangle(xPos, yPos, xSize, ySize))
    }

    internal fun setBlockStart(x: Int, y: Int) {
        blockStartX = x
        blockStartY = y
        canvas.setSelectionBlock(blockStartX, blockStartY)
    }

    internal fun setBlockBuffer(w: Int, h: Int, ar: Array<Tile>?, r: Boolean) {
        GlobalEditor.setBlockBuffer(w, h, ar, r, worldData.isSuperZZT)
        canvas.repaint()
    }

    private val blockX1: Int
        get() = min(cursorX.toDouble(), blockStartX.toDouble()).toInt()
    private val blockY1: Int
        get() = min(cursorY.toDouble(), blockStartY.toDouble()).toInt()
    private val blockX2: Int
        get() = max(cursorX.toDouble(), blockStartX.toDouble()).toInt()
    private val blockY2: Int
        get() = max(cursorY.toDouble(), blockStartY.toDouble()).toInt()

    private fun afterBlockOperation(modified: Boolean) {
        setBlockStart(-1, -1)
        if (modified) afterModification()
        else afterUpdate()
    }

    internal fun blockClear() {
        val tile = Tile(0, 0)
        addRedraw(blockX1, blockY1, blockX2, blockY2)
        for (y in blockY1..blockY2) {
            for (x in blockX1..blockX2) {
                putTileAt(x, y, tile, PUT_REPLACE_BOTH)
            }
        }

        afterBlockOperation(true)
    }

    internal fun blockPaint() {
        val paintCol = getTileColour(bufferTile!!)
        addRedraw(blockX1, blockY1, blockX2, blockY2)
        for (y in blockY1..blockY2) {
            for (x in blockX1..blockX2) {
                val tile = checkNotNull(getTileAt(x, y, false))
                paintTile(tile, paintCol)
                putTileAt(x, y, tile, PUT_REPLACE_BOTH)
            }
        }
        afterBlockOperation(true)
    }

    internal fun blockCopy(repeated: Boolean) {
        val w = blockX2 + 1 - blockX1
        val h = blockY2 + 1 - blockY1
        val blockBuffer = arrayOfNulls<Tile>(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val xPos = x + blockX1
                val yPos = y + blockY1
                val idx = y * w + x
                blockBuffer[idx] = getTileAt(xPos, yPos, true)
            }
        }
        // TODO(jakeouellette): Cleanup null check cast.
        setBlockBuffer(w, h, blockBuffer.map { tile: Tile? -> tile!! }.toTypedArray(), repeated)
        afterBlockOperation(false)
    }

    internal fun setMoveBlock(w: Int, h: Int) {
        moveBlockW = w
        moveBlockH = h
        canvas.setPlacingBlock(w, h)
    }

    internal fun blockMove() {
        moveBlockX = blockX1
        moveBlockY = blockY1
        val w = blockX2 + 1 - blockX1
        val h = blockY2 + 1 - blockY1
        setMoveBlock(w, h)
        afterBlockOperation(false)
    }

    internal fun blockFinishMove() {
        // Move from moveBlockX, moveBlockY, moveBlockW, moveBlockH, cursorX, cursorY

        val blockMap = LinkedHashMap<ArrayList<Board?>, LinkedHashMap<ArrayList<Int>?, ArrayList<Int>?>>()
        addRedraw(cursorX, cursorY, cursorX + moveBlockW - 1, cursorY + moveBlockH - 1)
        addRedraw(moveBlockX, moveBlockY, moveBlockX + moveBlockW - 1, moveBlockY + moveBlockH - 1)
        for (vy in 0 until moveBlockH) {
            for (vx in 0 until moveBlockW) {
                // The move order depends on the relationship between the two blocks, to avoid double moving
                val x = if (moveBlockX >= cursorX) vx else moveBlockW - 1 - vx
                val y = if (moveBlockY >= cursorY) vy else moveBlockH - 1 - vy
                val xFrom = x + moveBlockX
                val yFrom = y + moveBlockY
                val xTo = x + cursorX
                val yTo = y + cursorY
                if (xFrom < width && yFrom < height) {
                    if (xTo < width && yTo < height) {
                        val boardKey = ArrayList<Board?>(2)
                        boardKey.add(getBoardAt(xFrom, yFrom))
                        boardKey.add(getBoardAt(xTo, yTo))
                        val from = pair(xFrom, yFrom)
                        val to = pair(xTo, yTo)

                        if (!blockMap.containsKey(boardKey)) {
                            blockMap[boardKey] = LinkedHashMap()
                        }
                        blockMap[boardKey]!![from] = to
                    }
                }
            }
        }

        blockTileMove(blockMap, false)

        setMoveBlock(0, 0)
        afterBlockOperation(true)
    }

    internal fun blockFlip(horizontal: Boolean) {
        val hx = (blockX1 + blockX2) / 2
        val hy = (blockY1 + blockY2) / 2
        val blockMap = LinkedHashMap<ArrayList<Board?>, LinkedHashMap<ArrayList<Int>?, ArrayList<Int>?>>()
        addRedraw(blockX1, blockY1, blockX2, blockY2)
        for (y in blockY1..blockY2) {
            for (x in blockX1..blockX2) {
                if ((horizontal && x <= hx) || (!horizontal && y <= hy)) {
                    val xTo = if (horizontal) blockX2 - (x - blockX1) else x
                    val yTo = if (!horizontal) blockY2 - (y - blockY1) else y

                    val boardKey = ArrayList<Board?>(2)
                    boardKey.add(getBoardAt(x, y))
                    boardKey.add(getBoardAt(xTo, yTo))
                    val from = pair(x, y)
                    val to = pair(xTo, yTo)

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
        blockMap: LinkedHashMap<ArrayList<Board?>, LinkedHashMap<ArrayList<Int>?, ArrayList<Int>?>>,
        swap: Boolean
    ) {
        val blankTile = Tile(0, 0)
        for (boardKey in blockMap.keys) {
            val tileMoves = blockMap[boardKey]!!
            val fromBoard = boardKey[0]
            val toBoard = boardKey[1]
            if (fromBoard == null || toBoard == null) continue

            val firstFrom = tileMoves.keys.iterator().next()
            val fromBoardXOffset = firstFrom!![0] / boardW * boardW
            val fromBoardYOffset = firstFrom[1] / boardH * boardH

            if (fromBoard === toBoard) {
                if (!swap) {
                    // If stat0 is being overwritten and isn't being moved itself, don't move it
                    val stat0x = toBoard.getStat(0)!!.x - 1 + fromBoardXOffset
                    val stat0y = toBoard.getStat(0)!!.y - 1 + fromBoardYOffset
                    if (!tileMoves.containsKey(pair(stat0x, stat0y))) {
                        for (from in tileMoves.keys) {
                            val to = tileMoves[from]
                            if (to!![0] == stat0x && to[1] == stat0y) {
                                tileMoves.remove(from)
                                break
                            }
                        }
                    }
                }

                val reverseTileMoves = HashMap<ArrayList<Int>?, ArrayList<Int>?>()
                for (from in tileMoves.keys) {
                    val to = tileMoves[from]
                    reverseTileMoves[to] = from
                }

                // Same board
                val deleteStats = ArrayList<Int>()
                for (i in 0 until toBoard.statCount) {
                    val stat = toBoard.getStat(i)
                    var from: ArrayList<Int>? = pair(stat!!.x - 1 + fromBoardXOffset, stat.y - 1 + fromBoardYOffset)
                    var to = tileMoves[from]
                    if (to != null) {
                        //System.out.printf("Stat %d moving from %d,%d to %d,%d\n", i, stat.getX(), stat.getY(), to.get(0) % boardW + 1, to.get(1) % boardH + 1);
                        stat.x = to[0] % boardW + 1
                        stat.y = to[1] % boardH + 1
                        continue
                    }
                    to = from
                    from = reverseTileMoves[to]
                    if (from != null) {
                        if (swap) {
                            //System.out.printf("Stat %d moving from %d,%d to %d,%d !\n", i, stat.getX(), stat.getY(), from.get(0) % boardW + 1, from.get(1) % boardH + 1);
                            stat.x = from[0] % boardW + 1
                            stat.y = from[1] % boardH + 1
                        } else {
                            if (i != 0) deleteStats.add(i)
                        }
                    }
                }

                toBoard.directDeleteStats(deleteStats)

                val changingTiles = HashMap<ArrayList<Int>?, ArrayList<Int>>()
                for (from in tileMoves.keys) {
                    val to = tileMoves[from]
                    val id = toBoard.getTileId(from!![0] % boardW, from[1] % boardH)
                    val col = toBoard.getTileCol(from[0] % boardW, from[1] % boardH)
                    changingTiles[to] = pair(id, col)
                    if (swap) {
                        val tid = toBoard.getTileId(to!![0] % boardW, to[1] % boardH)
                        val tcol = toBoard.getTileCol(to[0] % boardW, to[1] % boardH)
                        changingTiles[from] = pair(tid, tcol)
                    } else {
                        toBoard.setTileRaw(from[0] % boardW, from[1] % boardH, blankTile.id, blankTile.col)
                    }
                }
                for (to in changingTiles.keys) {
                    val tile = changingTiles[to]!!
                    toBoard.setTileRaw(to!![0] % boardW, to[1] % boardH, tile[0], tile[1])
                }
                toBoard.finaliseStats()

                // Copy to buffer
            } else {
                // Different board

                for (from in tileMoves.keys) {
                    val to = tileMoves[from]
                    val tile = getTileAt(from!![0], from[1], true)
                    if (swap) {
                        val otherTile = getTileAt(to!![0], to[1], true)
                        putTileAt(from[0], from[1], otherTile, PUT_REPLACE_BOTH)
                    } else {
                        putTileAt(from[0], from[1], blankTile, PUT_REPLACE_BOTH)
                    }
                    putTileAt(to!![0], to[1], tile, PUT_REPLACE_BOTH)
                }
            }
        }
    }

    internal fun blockPaste() {
        val w = GlobalEditor.blockBufferW
        val h = GlobalEditor.blockBufferH
        val blockBuffer = GlobalEditor.getBlockBuffer(worldData.isSuperZZT)
        addRedraw(cursorX, cursorY, cursorX + w - 1, cursorY + h - 1)

        // Find the player
        var px = -1
        var py = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                val xPos = x + cursorX
                val yPos = y + cursorY
                if (xPos < width && yPos < height) {
                    val idx = y * w + x
                    val t = blockBuffer[idx]
                    val st: List<Stat> = t.stats
                    if (!st.isEmpty()) {
                        if (st[0].isPlayer) {
                            // Player has been found. Record the player's location
                            // then place
                            px = x
                            py = y
                            putTileAt(xPos, yPos, blockBuffer[idx], PUT_REPLACE_BOTH)
                        }
                    }
                }
            }
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (x == px && y == py) {
                    continue  // We have already placed the player
                }
                val xPos = x + cursorX
                val yPos = y + cursorY
                if (xPos < width && yPos < height) {
                    val idx = y * w + x
                    putTileAt(xPos, yPos, blockBuffer[idx], PUT_REPLACE_BOTH)
                }
            }
        }

        if (!GlobalEditor.blockBufferRepeated) {
            setBlockBuffer(0, 0, null, false)
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
        val cornerX = board!!.width + 1
        val cornerY = 0

        val stat0 = board.getStat(0)

        // If the player is already in the corner, do nothing
        if (stat0!!.x == cornerX && stat0.y == cornerY) return

        val oldStat0x = stat0.x - 1
        val oldStat0y = stat0.y - 1

        // Replace player with under tile
        val under = Tile(stat0.uid, stat0.uco)
        stat0.x = cornerX
        stat0.y = cornerY

        board.setTile(oldStat0x, oldStat0y, under)

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
        if (oldMousePosX == -1) {
            mousePlot(mouseX, mouseY, dirty)
        } else {
            var cx = -1
            var cy = -1
            val dx = mousePosX - oldMousePosX
            val dy = mousePosY - oldMousePosY
            val dist = max(abs(dx.toDouble()), abs(dy.toDouble())).toInt()
            if (dist == 0) return
            val plotSet = HashSet<ArrayList<Int>>()
            //int cw = canvas.getCharW(), ch = canvas.getCharH();
            for (i in 0..dist) {
                val x = dx * i / dist + oldMousePosX
                val y = dy * i / dist + oldMousePosY
                val ncx = canvas.toCharX(x)
                val ncy = canvas.toCharY(y)
                if (ncx != cx || ncy != cy) {
                    cx = ncx
                    cy = ncy
                    plotSet.add(pair(cx, cy))
                }
            }
            for (plot in plotSet) {
                mousePlot(plot[0], plot[1], dirty)
            }
        }
        for (board in dirty) {
            board.finaliseStats()
        }
        afterModification()
        canvas.setCursor(cursorX, cursorY)
    }

    private fun mouseMove(): Boolean {
        val x = mouseX
        val y = mouseY
        if (x >= 0 && y >= 0 && x < width && y < height) {
            cursorX = x
            cursorY = y
            canvas.setCursor(cursorX, cursorY)
            afterUpdate()
            return true
        }
        return false
    }

    private fun mouseGrab() {
        if (mouseMove()) {
            bufferTile = getTileAt(cursorX, cursorY, true)
            afterUpdate()
        }
    }

    private fun mouseCharX(x: Int): Int {
        return canvas.toCharX(x)
    }

    private fun mouseCharY(y: Int): Int {
        return canvas.toCharY(y)
    }

    private fun mousePlot(x: Int, y: Int, dirty: HashSet<Board>) {
        if (x >= 0 && y >= 0 && x < width && y < height) {
            cursorX = x
            cursorY = y
            canvas.setCursor(cursorX, cursorY)
            val board = putTileDeferred(cursorX, cursorY, bufferTile, PUT_DEFAULT)
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
            x = -1,
            y = -1,
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
        x: Int,
        y: Int,
        stats: List<Stat>? = null,
        tile: Tile? = null,
        selected: Int = -1,
    ) =
        TileEditor(
            this,
            this.boardXOffset,
            this.boardYOffset,
            this.boardIdx,
            this.worldData.isSuperZZT,
            this.boards,
            this.canvas,
            this.frameForRelativePositioning,
            board!!,
            tile,
            stats,
            callback,
            x,
            y,
            advanced,
            selected,
            exempt,
            { x, y -> this.canvas.setIndicate(x, y) },
            this::getKeystroke
        )


    internal fun setStats(board: Board, bx: Int, by: Int, x: Int, y: Int, stats: List<Stat>) {
        // If any stats move, invalidate the entire board
        var invalidateAll = false

        val destStats = board.getStatsAt(x, y)
        val statsToDelete = ArrayList<Int>()
        val statsAdded = BooleanArray(stats.size)
        var mustFinalise = false

        for (destStat in destStats) {
            var replacementStat: Stat? = null
            var replacementStatMoved = false
            for (i in stats.indices) {
                val stat = stats[i]
                if (stat.statId == destStat.statId && stat.statId != -1) {
                    if (stat.x != destStat.x || stat.y != destStat.y) {
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
                if (stat.x != x + 1 || stat.y != y + 1) invalidateAll = true
                board.directAddStat(stat)
                mustFinalise = true
            }
        }



        if (mustFinalise) {
            board.finaliseStats()
        }
        if (invalidateAll) {
            addRedraw(bx + 1, by + 1, bx + boardW - 2, by + boardH - 2)
        }
    }

    internal fun getBoardAt(x: Int, y: Int): Board? {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw IndexOutOfBoundsException("Attempted to getBoardAt() coordinate off map")
        }
        val gridX = x / boardW
        val gridY = y / boardH
        val boardIdx = grid[gridY][gridX]
        if (boardIdx == -1) return null
        return boards[boardIdx]
    }

    /**
     *
     * @param putMode PUT_DEFAULT or PUT_PUSH_DOWN or PUT_REPLACE_BOTH
     */
    internal fun putTileAt(x: Int, y: Int, tile: Tile?, putMode: Int) {
        val board = putTileDeferred(x, y, tile, putMode)
        board?.finaliseStats()
    }

    /**
     *
     * @param putMode PUT_DEFAULT or PUT_PUSH_DOWN or PUT_REPLACE_BOTH
     */
    internal fun putTileDeferred(x: Int, y: Int, tile: Tile?, putMode: Int): Board? {
        var putMode = putMode
        val board = getBoardAt(x, y)
        var currentTile = getTileAt(x, y, false)
        if (board != null && currentTile != null && tile != null) {
            val placingTile = tile.clone()
            // First, we will not allow putTileAt to erase stat 0
            val currentTileStats: List<Stat> = currentTile.stats
            if (currentTileStats.size > 0) {
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
            if (!placingTile.stats.isEmpty()) {
                if (placingTile.stats[0].statId == 0 ||
                    placingTile.stats[0].isPlayer
                ) {
                    // Find the stat 0 on this board
                    val stat0 = board.getStat(0)
                    val oldStat0x = stat0!!.x - 1
                    val oldStat0y = stat0.y - 1
                    // If stat 0 isn't on the board, nevermind!
                    if (oldStat0x >= 0 && oldStat0x < boardW && oldStat0y >= 0 && oldStat0y < boardH) {
                        // See what other stats are there
                        val oldStat0TileStats = board.getStatsAt(oldStat0x, oldStat0y)
                        if (oldStat0TileStats.size == 1) {
                            // Once we move stat0 there will be no other stats, so erase this
                            board.setTileRaw(oldStat0x, oldStat0y, stat0.uid, stat0.uco)
                            val bx = x / boardW * boardW
                            val by = y / boardH * boardH
                            addRedraw(oldStat0x + bx, oldStat0y + by, oldStat0x + bx, oldStat0y + by)
                        } // Otherwise there are stats left, so leave the tile alone
                    }
                    // Fix uid/uco
                    stat0.uid = placingTile.stats[0].uid
                    stat0.uco = placingTile.stats[0].uco
                    // Place the tile here, but without stat0
                    placingTile.stats.removeAt(0)
                    addRedraw(x, y, x, y)
                    board.setTileDirect(x % boardW, y % boardH, placingTile)
                    // Then move stat0 to the cursor
                    stat0.x = x % boardW + 1
                    stat0.y = y % boardH + 1

                    return board
                }
            }
            addRedraw(x, y, x, y)
            board.setTileDirect(x % boardW, y % boardH, placingTile)
        }
        return board
    }

    private fun updateCurrentBoard() {
        cursorX = clamp(cursorX, 0, width - 1)
        cursorY = clamp(cursorY, 0, height - 1)
        val gridX = cursorX / boardW
        val gridY = cursorY / boardH
        setCurrentBoard(grid[gridY][gridX])
        val worldName = if (path == null) "new world" else path!!.name
        val boardInfo = if (currentBoard != null) {
            "Board #" + boardIdx + " :: " + toUnicode(currentBoard!!.getName())
        } else {
            "(no board)"
        }
        frame.title = "zloom2 [" + worldName + "] :: " + boardInfo + (if (isDirty) "*" else "")
    }

    internal fun getTileAt(x: Int, y: Int, copy: Boolean): Tile? {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw IndexOutOfBoundsException("Attempted to read coordinate off map")
        }
        val boardX = x % boardW
        val boardY = y % boardH
        val board = getBoardAt(x, y)
        return board?.getTile(boardX, boardY, copy)
    }

    internal fun afterModification() {
        drawBoard()
        undoHandler.afterModification()
        afterUpdate()
    }

    private fun scrollToCursor() {
        var w: Int
        var h: Int
        var x: Int
        var y: Int
        if (centreView) {
            canvas.revalidate()
            val visibleRect = canvas.visibleRect
            w = clamp(visibleRect.width, 0, canvas.getCharW(width))
            h = clamp(visibleRect.height, 0, canvas.getCharH(height))
            val charW = canvas.getCharW(1)
            val charH = canvas.getCharH(1)
            x = max(0.0, (canvas.getCharW(cursorX) + (charW - w) / 2).toDouble()).toInt()
            y = max(0.0, (canvas.getCharH(cursorY) + (charH - h) / 2).toDouble()).toInt()
            centreView = false
        } else {
            w = canvas.getCharW(1)
            h = canvas.getCharH(1)
            x = canvas.getCharW(cursorX)
            y = canvas.getCharH(cursorY)

            // Expand this slightly
            val EXPAND_X = 4
            val EXPAND_Y = 4
            x -= canvas.getCharW(EXPAND_X)
            y -= canvas.getCharH(EXPAND_Y)
            w += canvas.getCharW(EXPAND_X * 2)
            h += canvas.getCharH(EXPAND_Y * 2)
        }
        val rect = Rectangle(x, y, w, h)
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
        val boardX = cursorX % boardW
        val boardY = cursorY % boardH

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
                boardX + 1, boardY + 1, currentBoard!!.currentSize
            )
        }

        s += String.format("W.Mem: %6d", worldSize)

        infoBox.text = s

        updateEditingMode()

        bufferPane.removeAll()
        bufferPaneContents = bufferPane
        blinkingImageIcons.clear()
        val cursorTile = getTileAt(cursorX, cursorY, false)
        addCursorTileInfoDisplay("Cursor", cursorTile, boardX, boardY, cursorX, cursorY)
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
        } else if (GlobalEditor.isBlockBuffer() || moveBlockW != 0) {
            return EditType.SELECTING
        }

        return EditType.EDITING
    }

    private fun updateEditingMode() {
        val enter = keyStrokeString(getKeyStroke(globalEditor, "Enter"))
        val brushMode = getBrushMode()
        if (brushMode == EditType.TEXT_ENTRY) editingModePane.display(Color.YELLOW, "Type to place text")
        else if (blockStartX != -1) editingModePane.display(
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
        boardX: Int,
        boardY: Int,
        cursorX: Int,
        cursorY: Int
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
                    -1, -1, true, frame, this@WorldEditor,
                    IconFactory.getIcon(worldData.isSuperZZT, cursorTile, this@WorldEditor), cb, cursorTile.stats[0]
                ) { e ->
                    if (e!!.actionCommand == "update") {
                        val source = e.source as CodeEditor

                        val cloneOfFirst = cursorTile.stats[0].clone()
                        cloneOfFirst.code = source.code

                        val mutableStats = mutableListOf<Stat>()
                        mutableStats.addAll(cursorTile.stats)
                        mutableStats.set(0, cloneOfFirst)

                        setStats(board, boardX, boardY, cursorX, cursorY, mutableStats)
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

    val boardXOffset: Int
        get() = cursorX / boardW * boardW

    val boardYOffset: Int
        get() = cursorY / boardH * boardH

    fun replaceBoardList(newBoardList: List<Board>) {
        atlases.clear()
        currentAtlas = null
        // TODO(jakeouellette): Update board selector
        onBoardsUpdated(boards)
    }

    fun mouseMotion(e: MouseEvent, heldDown: Int) {
        mouseState = heldDown
        mousePosX = e.x
        mousePosY = e.y
        mouseX = mouseCharX(mousePosX)
        mouseY = mouseCharY(mousePosY)

        // Translate into local space
        mouseScreenX = e.xOnScreen - frame.locationOnScreen.x
        mouseScreenY = e.yOnScreen - frame.locationOnScreen.y
        if (heldDown == 1) {
            mouseDraw()
            oldMousePosX = mousePosX
            oldMousePosY = mousePosY
        } else {
            if (heldDown == 2) mouseGrab()
            else if (heldDown == 3) mouseMove()
            oldMousePosX = -1
            oldMousePosY = -1
        }

        undoHandler.afterUpdate()
    }

    private fun removeAtlas() {
        val x = cursorX / boardW
        val y = cursorY / boardH
        var changeTo = grid[y][x]
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
        val map = HashMap<ArrayList<Int>, Board?>()
        map[pair(0, 0)] = currentBoard
        val stack = ArrayDeque<Any?>()
        stack.add(0)
        stack.add(0)
        stack.add(currentBoard)
        while (true) {
            // Board exits go NORTH, SOUTH, WEST, EAST
            val dir = arrayOf(intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(1, 0))
            if (stack.isEmpty()) break
            val x = stack.pop() as Int
            val y = stack.pop() as Int
            val board = stack.pop() as Board

            for (exit in 0..3) {
                val dest = board.getExit(exit)
                if (dest > 0 && dest < boards.size) {
                    val destBoard = boards[dest]
                    if (!boardsSeen.contains(destBoard)) {
                        val dx = x + dir[exit][0]
                        val dy = y + dir[exit][1]
                        if (!map.containsKey(pair(dx, dy))) {
                            map[pair(dx, dy)] = destBoard
                            boardsSeen.add(destBoard)
                            stack.add(dx)
                            stack.add(dy)
                            stack.add(destBoard)
                        }
                    }
                }
            }
        }

        var minX = 0
        var minY = 0
        var maxX = 0
        var maxY = 0
        for (loc in map.keys) {
            val x = loc[0]
            val y = loc[1]
            minX = min(minX.toDouble(), x.toDouble()).toInt()
            minY = min(minY.toDouble(), y.toDouble()).toInt()
            maxX = max(maxX.toDouble(), x.toDouble()).toInt()
            maxY = max(maxY.toDouble(), y.toDouble()).toInt()
            //System.out.printf("%d,%d: %s\n", loc.get(0), loc.get(1), CP437.INSTANCE.toUnicode(map.get(loc).getName()));
        }
        gridW = maxX - minX + 1
        gridH = maxY - minY + 1
        grid = Array(gridH) { IntArray(gridW) }
        val boardIdLookup = HashMap<Board?, Int>()
        for (i in boards.indices) {
            boardIdLookup[boards[i]] = i
        }
        for (y in 0 until gridH) {
            Arrays.fill(grid[y], -1)
        }
        val atlas = Atlas(gridW, gridH, grid)
        currentAtlas = atlas
        for (loc in map.keys) {
            val x = loc[0] - minX
            val y = loc[1] - minY
            val board = map[loc]
            val boardIdx = boardIdLookup[board]!!
            grid[y][x] = boardIdx
            atlases[boardIdx] = atlas
        }
        cursorX = (cursorX % boardW) + boardW * -minX
        cursorY = (cursorY % boardH) + boardH * -minY
        width = boardW * gridW
        height = boardH * gridH
        canvas.setCursor(cursorX, cursorY)
        invalidateCache()
        afterModification()
        canvas.revalidate()
        val rect = Rectangle(
            canvas.getCharW(-minX * boardW),
            canvas.getCharH(-minY * boardH),
            canvas.getCharW(boardW),
            canvas.getCharH(boardH)
        )
        canvas.scrollRectToVisible(rect)
        undoHandler.resetUndoList()
    }

    private fun atlasRemoveBoard() {
        if (currentBoard == null) return
        if (currentAtlas == null) return
        atlases.remove(grid[cursorY / boardH][cursorX / boardW])
        grid[cursorY / boardH][cursorX / boardW] = -1
        var notEmpty = false
        for (y in 0 until gridH) {
            for (x in 0 until gridW) {
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
                    cursorX = textEntryX
                    cursorY = clamp(cursorY + 1, 0, height - 1)
                } else {
                    if (ch.code == 8) { // bksp
                        cursorX = clamp(cursorX - 1, 0, width - 1)
                    }
                    var col = ch.code
                    if (ch.code == 8 || ch.code == 127) { // bksp or del
                        col = ' '.code
                    }

                    val id = (getTileColour(bufferTile!!) % 128) + 128
                    val textTile = Tile(id, col)
                    putTileAt(cursorX, cursorY, textTile, PUT_DEFAULT)

                    if (ch.code != 8 && ch.code != 127) { // not bksp or del
                        cursorX = clamp(cursorX + 1, 0, width - 1)
                    }

                    afterModification()
                }
                canvas.setCursor(cursorX, cursorY)
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
