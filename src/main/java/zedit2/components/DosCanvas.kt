package zedit2.components

import zedit2.components.editor.world.operationBlockEnd
import zedit2.components.editor.world.operationBlockStart
import zedit2.components.editor.world.operationGrabAndModify
import zedit2.util.Data
import zedit2.model.Atlas
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import zedit2.model.spatial.Rec
import zedit2.model.spatial.SpatialUtil
import zedit2.util.ImageExtractors
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.TilePainters
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.*
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DosCanvas(private val editor: WorldEditor, private var zoomx: Double, private var zoomy : Double) : JPanel(), MouseListener, FocusListener,
    MouseMotionListener, MouseWheelListener, ImageRetriever {
    lateinit var charset: ByteArray
        private set

    var paletteData: ByteArray? = null

    private lateinit var palette: IntArray
    private var charBuffer: BufferedImage? = null
    private var dim : Dim = Dim(0, 0)
    private val boardBuffers = arrayOfNulls<BufferedImage>(2)

    private var chars: ByteArray? = null
    private var cols: ByteArray? = null
    private var blink = true
    private var blinkState = false
    private var mouseState = 0

    private var cursorPos = Pos(-1, -1)
    private var indicatePos: List<Pos>? = null
    private var mouseCursorPos = Pos(-1, -1)
    private var blockStartPos = Pos(-1, -1)
    private var placingBlockOff = Dim(0, 0)
    private var drawing = false
    private var textEntry = false

    private var boardDim = Dim(0, 0)
    private var atlas: Atlas? = null

    private val globalEditor: GlobalEditor = editor.globalEditor
    private var drawAtlasLines = false

    private var lastMouseEvent: MouseEvent? = null
    private var show: ByteArray? = null

    init {
        initialiseBuffers()

        addMouseListener(this)
        addMouseMotionListener(this)
        addMouseWheelListener(this)
        this.isFocusable = true
        addFocusListener(this)
    }

    fun setBlinkMode(state: Boolean) {
        if (blink != state) {
            blink = state
            refreshBuffer()
            resetData()
        }
    }

    @Throws(IOException::class)
    private fun initialiseBuffers() {
        loadCharset(null)
        loadPalette(null)
        refreshBuffer()
    }

    // todo(jakeouellette): Move to a separate factory.
    override fun extractCharImage(
        chr: Int,
        col: Int,
        zoomx: Int,
        zoomy: Int,
        blinkingTime: Boolean,
        pattern: String
    ): BufferedImage =
        ImageExtractors.extractCharImage(
            chr,
            col,
            zoomx,
            zoomy,
            blinkingTime,
            pattern,
            blink,
            palette = palette,
            charBuffer = charBuffer
        ).image

    public fun extractCharImageWH(
        chr: Int,
        col: Int,
        zoomx: Int,
        zoomy: Int,
        blinkingTime: Boolean,
        pattern: String, dim : Dim,
    ): BufferedImage =
        ImageExtractors.extractCharImageWH(
            chr,
            col,
            zoomx,
            zoomy,
            blinkingTime,
            pattern,
            dim,
            blink,
            palette = palette,
            charBuffer = charBuffer
        ).image

    private fun refreshBuffer() {
        val hashes = intArrayOf(palette.contentHashCode(), charset.contentHashCode())
        val charBufferHash = hashes.contentHashCode()

        if (charBufferCache.containsKey(charBufferHash)) {
            charBuffer = charBufferCache[charBufferHash]
        } else {
            generateCharBuffer()
            charBufferCache[charBufferHash] = charBuffer
        }
    }

    private fun generateCharBuffer() {
        val dim = Dim(CHAR_W * CHAR_COUNT, CHAR_H * PALETTE_SIZE)
        charBuffer = BufferedImage(dim.w, dim.h, BufferedImage.TYPE_INT_ARGB)
        val raster = charBuffer!!.raster
        val ar = IntArray(dim.w * dim.h)

        for (col in 0 until PALETTE_SIZE) {
            for (chr in 0 until CHAR_COUNT) {
                for (y in 0 until CHAR_H) {
                    for (x in 0 until CHAR_W) {
                        val px = chr * CHAR_W + x
                        val py = col * CHAR_H + y
                        val p = py * dim.w + px

                        // Is this pixel set in this char?
                        val charRow = charset[chr * CHAR_H + y]
                        val charMask = (128 shr x).toByte()
                        if ((charRow.toInt() and charMask.toInt()) != 0) {
                            ar[p] = palette[col]
                        } else {
                            ar[p] = TRANSPARENT
                        }
                    }
                }
            }
        }

        raster.setDataElements(0, 0, dim.w, dim.h, ar)
    }

    @Throws(IOException::class)
    private fun loadPalette(path: File?) {
        var pdata: ByteArray;
        if (path != null) {
            pdata = Files.readAllBytes(path.toPath())
            if (pdata.size != 16 * 3) {
                throw RuntimeException("Invalid palette size")
            }
        } else {
            pdata = Data.DEFAULT_PALETTE
        }

        palette = IntArray(PALETTE_SIZE)
        for (i in 0 until PALETTE_SIZE) {
            val r = pdata[i * 3 + 0] * 255 / 63
            val g = pdata[i * 3 + 1] * 255 / 63
            val b = pdata[i * 3 + 2] * 255 / 63
            var color = -0x1000000
            color = color or (r shl 16)
            color = color or (g shl 8)
            color = color or b
            palette[i] = color
        }
        paletteData = pdata
    }

    @Throws(IOException::class)
    private fun loadCharset(path: File?) {
        if (path != null) {
            val charsetBytes = Files.readAllBytes(path.toPath())
            if (charsetBytes.size == CHAR_COUNT * CHAR_H) {
                charset = charsetBytes
            } else if (charsetBytes.size == 5027) {
                charset = Arrays.copyOfRange(charsetBytes, 1442, 5026)
            }
        } else {
            charset = Data.DEFAULT_CHARSET
        }
        if (charset.size != CHAR_COUNT * CHAR_H) {
            throw RuntimeException("Invalid charset size")
        }
    }

    override fun getPreferredSize(): Dimension {
        var d = dim
        if (d.w == 0 || d.h == 0) {
            d = Dim(60, 25)
        }

        return d.tile(zoomx, zoomy).asDimension
    }

    fun setDimensions(d : Dim) {
        if (dim != d) {
            dim = d
            fullRefresh()
        }
    }

    private fun fullRefresh() {
        this.cols = ByteArray(dim.w * dim.h)
        this.chars = ByteArray(dim.w * dim.h)
        this.show = ByteArray(dim.w * dim.h)

        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val device = env.defaultScreenDevice
        val config = device.defaultConfiguration

        for (i in 0..1) {
            boardBuffers[i] = config.createCompatibleImage(dim.w * CHAR_W, dim.h * CHAR_H)
            //boardBuffers[i] = new BufferedImage(w * CHAR_W, h * CHAR_H, BufferedImage.TYPE_INT_RGB);
        }
    }

    fun setZoom(zoom: Double) {
        this.zoomx = zoom
        this.zoomy = zoom
    }

    fun setData(
        dim : Dim,
        cols: ByteArray,
        chars: ByteArray,
        offset: Pos,
        showing: Int,
        show: ByteArray?
    ) {
        val redrawAll = false
        setData(dim, cols, chars, offset, redrawAll, show)
    }

    fun setData(
        dim : Dim,
        cols: ByteArray,
        chars: ByteArray,
        offset : Pos,
        redrawAll: Boolean,
        show: ByteArray?
    ) {
        if (cols.size != dim.w * dim.h) throw RuntimeException("Dimensions do not match colour array size")
        if (chars.size != dim.w * dim.h) throw RuntimeException("Dimensions do not match char array size")

        val boardBufferGraphics = arrayOfNulls<Graphics>(2)
        for (i in 0..1) {
            boardBufferGraphics[i] = boardBuffers[i]!!.graphics
        }
        for (dy in 0 until dim.h) {
            for (dx in 0 until dim.w) {
                val dxy = Pos(dx, dy)
                val dpos = dxy.arrayPos(dim.w)
                val xy = dxy + offset
                val pos = xy.arrayPos(dim.w)
                var tshow = if (show != null) {
                    show[dpos]
                } else {
                    0
                }
                if ((redrawAll || this.cols!![pos] != cols[dpos]) || this.chars!![pos] != chars[dpos] || this.show!![pos] != tshow) {
                    // TODO(jakeouellette): Clean up this state machine.
                    this.chars!![pos] = chars[dpos]
                    this.cols!![pos] = cols[dpos]
                    this.show!![pos] = tshow
                    val chr = chars[dpos].toInt() and 0xFF
                    val col = cols[dpos].toInt() and 0xFF

                    TilePainters.drawTile(
                        boardBufferGraphics[0],
                        chr,
                        col,
                        x,
                        y,
                        1,
                        1,
                        blink,
                        false,
                        palette,
                        charBuffer
                    )
                    if (tshow.toInt() != 0) {
                        drawShow(boardBufferGraphics[1], chr, col, x, y, 1, 1, tshow)
                    } else {
                        TilePainters.drawTile(
                            boardBufferGraphics[1],
                            chr,
                            col,
                            x,
                            y,
                            1,
                            1,
                            blink,
                            true,
                            palette,
                            charBuffer
                        )
                    }
                }
            }
        }

        //System.arraycopy(cols, 0, this.cols, 0, cols.length);
        //System.arraycopy(chars, 0, this.chars, 0, chars.length);
    }

    fun getBoardBuffer(doubleWidth: Boolean): BufferedImage? {
        if (!doubleWidth) return boardBuffers[0]
        val w = boardBuffers[0]!!.width * 2
        val h = boardBuffers[0]!!.height
        val tmpBuffer = boardBuffers[0]!!.getScaledInstance(w, h, Image.SCALE_REPLICATE)
        val newBuffer = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        newBuffer.graphics.drawImage(tmpBuffer, 0, 0, null)
        return newBuffer
    }

    private fun drawShow(g: Graphics?, chr: Int, col: Int, x: Int, y: Int, zoomx: Int, zoomy: Int, show: Byte) {
        val chrs = intArrayOf(' '.code, '#'.code, '!'.code, 178, 15, col, '#'.code)
        var rcol = col
        if (rcol == 0x00) rcol = 0x80
        else if (rcol % 16 == rcol / 16) rcol = rcol and 0xF0
        val cols = intArrayOf(rcol, rcol, rcol, rcol, rcol, 7, rcol)
        if (chr == chrs[show.toInt()]) {
            chrs[show.toInt()] = ' '.code
        }
        TilePainters.drawTile(
            g,
            chrs[show.toInt()],
            cols[show.toInt()],
            x,
            y,
            zoomx,
            zoomy,
            false,
            false,
            palette,
            charBuffer
        )
    }

    private fun tileX(x : Int)  = SpatialUtil.tileX(x, zoomx)
    private fun tileY(y : Int)  = SpatialUtil.tileY(y, zoomy)



    public override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        /*
        var mc = getMouseCoords(getMousePosition());
        if (mc != null) {
            mouseCursorX = mc.x;
            mouseCursorY = mc.y;
            if (mouseCursorX < 0 || mouseCursorY < 0 || mouseCursorX >= width || mouseCursorY >= height) {
                mouseCursorX = -1;
                mouseCursorY = -1;
            }
        }
        */
        g.color = Color(0x7F7F7F)
        g.fillRect(0, 0, getWidth(), getHeight())

        if (g is Graphics2D) {
            val interpMode: Any

            // Select an appropriate rendering mode
            val xerror = abs(Math.round(zoomx) - zoomx)
            val yerror = abs(Math.round(zoomy) - zoomy)
            val error = max(xerror, yerror)
            interpMode = if (error < 0.001) {
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            } else {
                if (xerror < 0.001 || yerror < 0.001) {
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                } else {
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
                }
            }

            val rh = RenderingHints(
                RenderingHints.KEY_INTERPOLATION,
                interpMode
            )
            g.setRenderingHints(rh)
        }

        val blinkImage = if (blinkState) 1 else 0
        drawImg(g, boardBuffers[blinkImage])

        if (atlas != null && drawAtlasLines) {
            val lcBad = Color(1.0f, 0.4f, 0.2f)
            val lcGood = Color(0.2f, 1.0f, 0.4f, 0.5f)
            val lcNormal = Color(1.0f, 1.0f, 1.0f)
            val lcVoid = Color(1.0f, 1.0f, 1.0f, 0.2f)
            val gridW = atlas!!.w
            val gridH = atlas!!.h
            val grid = atlas!!.grid
            val boards = editor.boards
            val boardPixelOff = boardDim.tile(zoomx, zoomy)
            val dirs = arrayOf(intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(1, 0))
            val walls_thick = arrayOf(
                intArrayOf(0, 0, boardPixelOff.w, 2),
                intArrayOf(0, boardPixelOff.h - 2, boardPixelOff.w, 2),
                intArrayOf(0, 0, 2, boardPixelOff.h),
                intArrayOf(boardPixelOff.w - 2, 0, 2, boardPixelOff.h)
            )
            val walls_thin = arrayOf(
                intArrayOf(0, 0, boardPixelOff.w, 1),
                intArrayOf(0, boardPixelOff.h - 1, boardPixelOff.w, 1),
                intArrayOf(0, 0, 1, boardPixelOff.h),
                intArrayOf(boardPixelOff.w - 1, 0, 1, boardPixelOff.h)
            )

            for (y in 0 until gridH) {
                val py = tileY(y * boardDim.h)
                for (x in 0 until gridW) {
                    val px = tileX(x * boardDim.w)
                    val boardIdx = grid[y][x]
                    if (boardIdx != -1) {
                        val board = boards[boardIdx]

                        for (exit in 0..3) {
                            val exitd = board.getExit(exit)
                            val nx = x + dirs[exit][0]
                            val ny = y + dirs[exit][1]
                            var walls = walls_thick[exit]
                            if (exitd == 0) {
                                g.color = lcNormal
                            } else {
                                g.color = lcBad
                            }
                            if (nx >= 0 && ny >= 0 && nx < gridW && ny < gridH) {
                                if (exitd == grid[ny][nx]) {
                                    g.color = lcGood
                                    walls = walls_thin[exit]
                                }
                            }
                            g.fillRect(
                                walls[0] + px, walls[1] + py,
                                walls[2], walls[3]
                            )
                        }
                    } else {
                        g.color = lcVoid
                        for (exit in 0..3) {
                            val walls = walls_thin[exit]
                            g.fillRect(
                                walls[0] + px, walls[1] + py,
                                walls[2], walls[3]
                            )
                        }
                    }

                    //g.drawLine(px, 0, px, ysize);
                    //g.drawLine(0, py, xsize, py);
                }
            }
        }


        //volatileBuffers[i] = config.createCompatibleVolatileImage(w * CHAR_W, h * CHAR_H);

        //var boardBuffer = boardBuffers[blinkState ? 1 : 0];
        if (drawing) {
            g.color = Color.GREEN
        } else if (textEntry) {
            g.color = Color.YELLOW
        } else {
            g.color = Color.LIGHT_GRAY
        }
        val tile = cursorPos.tile(zoomx, zoomy)
        val tile1 = Pos(1, 1).tile(zoomx, zoomy)
        g.draw3DRect(tile.x - 1, tile.y - 1, tile1.x + 1, tile1.y + 1, blinkState)

        if (mouseCursorPos.isPositive && editor.frame.isFocused) {
            val tile = mouseCursorPos.tile(zoomx, zoomy)
            val tile1 = Pos(1, 1).tile(zoomx, zoomy)
            g.color = Color(0x7FFFFFFF, true)
            g.drawRect(
                tile.x - 1, tile.y - 1,
                tile1.x + 1, tile1.y + 1
            )
        }

        if (indicatePos != null) {
            g.color = Color(0x3399FF)
            for (i in indicatePos!!.indices) {
                val pos = indicatePos!![i]
                if (!pos.isPositive) continue
                val dim1 = Dim(1,1).tile(zoomx, zoomy)
                val tile = pos.tile(zoomx, zoomy)
                g.drawRect(tile.x, tile.y, dim1.w- 1, dim1.h - 1)
            }
        }

        if (blockStartPos.isPositive) {
            g.color = Color(0x7F3399FF, true)
            val rect = Rec.companion.from(blockStartPos, cursorPos)
            val (minPos, maxPos) = rect.toPos
            val w = ((minPos.x - x + 1).toInt())
            val h = ((maxPos.y - y + 1).toInt())
            g.fillRect(tileX(x), tileY(y), tileX(w), tileY(h))
        }

        if (GlobalEditor.isBlockBuffer()) {
            g.color = Color(0x5FFF8133, true)
            val xy2 = (dim.asPos - 1).min(cursorPos + GlobalEditor.blockBufferDim - 1)
            val newDim = (xy2 - cursorPos + 1).dim
            g.fillRect(tileX(cursorPos.x), tileY(cursorPos.y), tileX(newDim.w), tileY(newDim.h))
        }

        if (placingBlockOff.w != -1) {
            g.color = Color(0x5F33ff99, true)
            val xy2 = (dim.asPos - 1).min(cursorPos + placingBlockOff - 1)
            val newDim = (xy2 - cursorPos + 1).dim
            g.fillRect(tileX(cursorPos.x), tileY(cursorPos.y), tileX(newDim.w), tileY(newDim.h))
        }
        //g.drawImage(charBuffer, 0, 0, new Color(palette[7], true), null);
        //g.drawImage(charBuffer, 0, 0, 16, 14, 8, 14, 16, 28, bgColor, null);
    }

    private fun drawImg(g: Graphics, image: Image?) {
        g.drawImage(image, 0, 0, tileX(dim.w), tileY(dim.h), 0, 0, dim.w * CHAR_W, dim.h * CHAR_H, null)
    }

    override fun mouseClicked(e: MouseEvent) {
        Logger.i(TAG) {"Requesting Focus."}
        this.requestFocusInWindow()
        mouseMoveCommon(e)
    }

    private fun getMouseCoords(p: Point?): Point? {
        if (p == null) {
            return null
        }
        val x = (p.x / zoomx / CHAR_W).toInt()
        val y = (p.y / zoomy / CHAR_H).toInt()
        return Point(x, y)
    }

    override fun mousePressed(e: MouseEvent) {

        mouseMoveCommon(e, getButton(e))
        Logger.i(TAG) { "Starting new block operation $blockStartPos"}
        val isMovingNow = editor.moveBlockPos.isPositive
        val isSelectingNow = blockStartPos.isPositive
        val isInSelectingMode = editor.editType == WorldEditor.EditType.SELECTING
        // Either, if when you click you always want a new selection,
        // or you are already selecting and selection should be refreshed
        var triggerOperationStart = false
        if ((isInSelectingMode && !(isMovingNow || isSelectingNow)) || (isSelectingNow)) {
            triggerOperationStart = true
            editor.operationBlockStart()
        }
        val isMovingNow2 = editor.moveBlockPos.isPositive
        val isSelectingNow2 = blockStartPos.isPositive
        var inside = false
        if (isInSelectingMode && isMovingNow) {

            val placingBlockPos = editor.moveBlockPos + placingBlockOff

            if (cursorPos.inside(
                    editor.moveBlockPos.x,
                    editor.moveBlockPos.y,
                    placingBlockPos.x,
                    placingBlockPos.y)) {
                inside = true
            }
        }
        Logger.i(TAG) { "MousePressed Event: IsMoving: $isMovingNow, $isMovingNow2 Selecting: $isSelectingNow, $isSelectingNow2 SelectingMode: $isInSelectingMode" +
                "Inside: $inside, [blockstart, moveblock, cursor]: [$blockStartPos ${editor.moveBlockPos} ${editor.moveBlockPos} $cursorPos $cursorPos]"}
    }

    override fun mouseReleased(e: MouseEvent) {
        // TODO(jakeouellette): separate this event out from operationBlockEnd:
        // Make it so that this one uses whatever is the default behavior
        // of the current selection brush
        Logger.i(TAG) { "TODO: Trigger block operation $blockStartPos"}
        val isMovingNow = editor.moveBlockPos.isPositive
        val isSelectingNow = blockStartPos.isPositive
        val isInSelectingMode = editor.editType == WorldEditor.EditType.SELECTING

        // TODO(jakeouellette) do this a very different way
        if (editor.moveBlockPos.isPositive) {
            editor.operationGrabAndModify(true, false)
        }
        if (blockStartPos.isPositive) {
            editor.operationBlockEnd()
        }
        val isMovingNow2 = editor.moveBlockPos.isPositive
        val isSelectingNow2 = blockStartPos.isPositive
        Logger.i(TAG) { "mouseReleased Event: IsMoving: $isMovingNow, $isMovingNow2 Selecting: $isSelectingNow, $isSelectingNow2, SelectingMode: $isInSelectingMode" +
                 "[bsX, bsY, mbX, mbY cX, cY]: [$blockStartPos ${editor.moveBlockPos} $cursorPos]"}
        mouseMoveCommon(e, 0)
    }

    private fun getButton(e: MouseEvent): Int {
        return if (SwingUtilities.isLeftMouseButton(e)) 1
        else if (SwingUtilities.isRightMouseButton(e)) 2
        else if (SwingUtilities.isMiddleMouseButton(e)) 3
        else 0
    }

    override fun mouseEntered(e: MouseEvent) {

        mouseMoveCommon(e)
    }

    override fun mouseExited(e: MouseEvent) {
        if (mouseCursorPos.isPositive) {
            mouseCursorPos = Pos(-1, -1)
            repaint()
        }
    }

    override fun mouseDragged(e: MouseEvent) {
        mouseMoveCommon(e)
    }

    override fun mouseMoved(e: MouseEvent) {
        mouseMoveCommon(e)
    }

    private fun mouseMoveCommon(e: MouseEvent, heldDown: Int = mouseState) {
        lastMouseEvent = e
        mouseState = heldDown
        editor.mouseMotion(e, heldDown)
        val newMouseCursorPos : Pos
        val mc = getMouseCoords(e.point)
        val pos = Pos(mc!!.x, mc.y)
        if (pos.outside(0,0,getWidth()-1, getHeight()-1)) {
            newMouseCursorPos = Pos(-1, -1)
        } else {
            newMouseCursorPos = Pos(x, y)
        }

        if (newMouseCursorPos != mouseCursorPos) {
            mouseCursorPos = newMouseCursorPos
            repaint()
        }
    }

    fun setBlink(b: Boolean) {
        blinkState = b
        repaint()
    }

    fun setCursor(pos : Pos) {
        cursorPos = pos
        repaint()
    }

    fun setIndicate(x: IntArray?, y: IntArray?) {
        // TODO(jakeouellettE):
        indicatePos = x!!.zip(y!!).map { (x, y) -> Pos(x, y) }
        repaint()
    }

    fun htmlColour(colIdx: Int): String {
        val r = (palette[colIdx] and 0x00FF0000) shr 16
        val g = (palette[colIdx] and 0x0000FF00) shr 8
        val b = (palette[colIdx] and 0x000000FF) shr 0
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun setSelectionBlock(blockStartPos: Pos) {
        this.blockStartPos = blockStartPos
        repaint()
    }

    fun setPlacingBlock(dim : Dim) {
        Logger.i(TAG) { "setPlacingBlock $dim"}
        this.placingBlockOff = dim
        repaint()
    }

    fun setTextEntry(state: Boolean) {
        textEntry = state
        repaint()
    }

    fun setDrawing(state: Boolean) {
        drawing = state
        repaint()
    }

    fun getCharW(x: Int): Int {
        return getCharW(x, zoomx)
    }

    fun getCharH(y: Int): Int {
        return getCharH(y, zoomy)
    }

    fun getCharW(x: Int, zoomx: Double): Int {
        return Math.round(CHAR_W * x * zoomx).toInt()
    }

    fun toCharX(x: Int): Int {
        return (1.0 * x / CHAR_W / zoomx).toInt()
    }

    fun getCharH(y: Int, zoomy: Double): Int {
        return Math.round(CHAR_H * y * zoomy).toInt()
    }

    fun toCharY(y: Int): Int {
        return (1.0 * y / CHAR_H / zoomy).toInt()
    }

    fun toChar(xy: Pos): Pos {
        return Pos(toCharX(xy.x), toCharY(xy.y))
    }

    // TODO: HOT FUNCTION (4%)
    fun drawVoid(pos : Pos, dim : Dim) {
        for (i in 0..1) {
            val graphics = boardBuffers[i]!!.graphics
            graphics.color = Color(0x7F7F7F)
            graphics.fillRect(x * CHAR_W, y * CHAR_H, dim.w * CHAR_W, dim.h * CHAR_H)
        }
    }

    @Throws(IOException::class)
    fun setPalette(file: File?) {
        loadPalette(file)
        refreshBuffer()
        resetData()
        repaint()
    }

    @Throws(IOException::class)
    fun setCharset(file: File?) {
        loadCharset(file)
        refreshBuffer()
        resetData()
        repaint()
    }

    @Throws(IOException::class)
    fun setCP(charset: File?, palette: File?) {
        loadCharset(charset)
        loadPalette(palette)
        refreshBuffer()
        resetData()
        repaint()
    }

    private fun resetData() {
        if (cols != null && chars != null) {
            setData(dim, cols!!, chars!!, Pos.ZERO, true, show)
        }
    }

    fun setAtlas(atlas: Atlas?, boardW: Int, boardH: Int, drawAtlasLines: Boolean) {
        this.atlas = atlas
        this.boardDim = Dim(boardW, boardH)
        this.drawAtlasLines = drawAtlasLines
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (e.wheelRotation < 0) {
            editor.wheelUp(e)
        } else {
            editor.wheelDown(e)
        }
    }

    fun recheckMouse() {
        if (lastMouseEvent != null) {
            // TODO
            // TODO
            // TODO
            // TODO
            // TODO
        }
    }

    companion object {
        const val CHAR_W: Int = 8
        const val CHAR_H: Int = 14
        const val CHAR_COUNT: Int = 256
        const val PALETTE_SIZE: Int = 16
        private const val TRANSPARENT = 0x00000000

        private val charBufferCache = WeakHashMap<Int, BufferedImage?>()
    }

    override fun focusGained(e: FocusEvent?) {
        Logger.i(this@DosCanvas.TAG) {"Focus gained, $e"}
    }

    override fun focusLost(e: FocusEvent?) {
        Logger.i(this@DosCanvas.TAG) {"Focus lost, $e"}
    }


}
