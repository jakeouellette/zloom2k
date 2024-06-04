package zedit2.components

import zedit2.util.Data
import zedit2.model.Atlas
import zedit2.util.ImageExtractors
import zedit2.util.ImageExtractors.ExtractionResponse
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

class DosCanvas(private val editor: WorldEditor, private var zoomx: Double) : JPanel(), MouseListener,
    MouseMotionListener, MouseWheelListener, ImageRetriever {
    lateinit var charset: ByteArray
        private set

    var paletteData: ByteArray? = null

    private lateinit var palette: IntArray
    private var charBuffer: BufferedImage? = null
    private var width = 0
    private var height = 0
    private val boardBuffers = arrayOfNulls<BufferedImage>(2)

    private var chars: ByteArray? = null
    private var cols: ByteArray? = null
    private var zoomy: Double
    private var blink = true
    private var blinkState = false
    private var mouseState = 0

    private var cursorX = 0
    private var cursorY = 0
    private var indicateX: IntArray? = null
    private var indicateY: IntArray? = null
    private var mouseCursorX = -1
    private var mouseCursorY = -1
    private var blockStartX = -1
    private var blockStartY = -1
    private var placingBlockW = 0
    private var placingBlockH = 0
    private var drawing = false
    private var textEntry = false

    private var boardW = 0
    private var boardH = 0
    private var atlas: Atlas? = null

    private val globalEditor: GlobalEditor = editor.globalEditor
    private var drawAtlasLines = false

    private var lastMouseEvent: MouseEvent? = null
    private var show: ByteArray? = null

    init {
        this.zoomy = zoomx

        initialiseBuffers()

        addMouseListener(this)
        addMouseMotionListener(this)
        addMouseWheelListener(this)
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
        pattern: String, w: Int, h: Int
    ): BufferedImage =
        ImageExtractors.extractCharImageWH(
            chr,
            col,
            zoomx,
            zoomy,
            blinkingTime,
            pattern,
            w,
            h,
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
        val w = CHAR_W * CHAR_COUNT
        val h = CHAR_H * PALETTE_SIZE
        charBuffer = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val raster = charBuffer!!.raster
        val ar = IntArray(w * h)

        for (col in 0 until PALETTE_SIZE) {
            for (chr in 0 until CHAR_COUNT) {
                for (y in 0 until CHAR_H) {
                    for (x in 0 until CHAR_W) {
                        val px = chr * CHAR_W + x
                        val py = col * CHAR_H + y
                        val p = py * w + px

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

        raster.setDataElements(0, 0, w, h, ar)
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
        var w = width
        var h = height
        if (w == 0 || h == 0) {
            w = 60
            h = 25
        }

        return Dimension(
            Math.round(w * CHAR_W * zoomx).toInt(),
            Math.round(h * CHAR_H * zoomy).toInt()
        )
    }

    fun setDimensions(w: Int, h: Int) {
        if (width != w || height != h) {
            width = w
            height = h
            fullRefresh()
        }
    }

    private fun fullRefresh() {
        this.cols = ByteArray(width * height)
        this.chars = ByteArray(width * height)
        this.show = ByteArray(width * height)

        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val device = env.defaultScreenDevice
        val config = device.defaultConfiguration

        for (i in 0..1) {
            boardBuffers[i] = config.createCompatibleImage(width * CHAR_W, height * CHAR_H)
            //boardBuffers[i] = new BufferedImage(w * CHAR_W, h * CHAR_H, BufferedImage.TYPE_INT_RGB);
        }
    }

    fun setZoom(zoomx: Double, zoomy: Double) {
        this.zoomx = zoomx
        this.zoomy = zoomy
    }

    fun setData(
        w: Int,
        h: Int,
        cols: ByteArray,
        chars: ByteArray,
        offsetX: Int,
        offsetY: Int,
        showing: Int,
        show: ByteArray?
    ) {
        val redrawAll = false
        setData(w, h, cols, chars, offsetX, offsetY, redrawAll, show)
    }

    fun setData(
        w: Int,
        h: Int,
        cols: ByteArray,
        chars: ByteArray,
        offsetX: Int,
        offsetY: Int,
        redrawAll: Boolean,
        show: ByteArray?
    ) {
        if (cols.size != w * h) throw RuntimeException("Dimensions do not match colour array size")
        if (chars.size != w * h) throw RuntimeException("Dimensions do not match char array size")

        val boardBufferGraphics = arrayOfNulls<Graphics>(2)
        for (i in 0..1) {
            boardBufferGraphics[i] = boardBuffers[i]!!.graphics
        }
        for (dy in 0 until h) {
            for (dx in 0 until w) {
                val dpos = dy * w + dx
                val x = dx + offsetX
                val y = dy + offsetY
                val pos = y * width + x
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


    private fun tileX(x: Int): Int {
        return Math.round(x * CHAR_W * zoomx).toInt()
    }

    private fun tileY(y: Int): Int {
        return Math.round(y * CHAR_H * zoomy).toInt()
    }

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
            val boardPixelW = tileX(boardW)
            val boardPixelH = tileY(boardH)
            val dirs = arrayOf(intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, 0), intArrayOf(1, 0))
            val walls_thick = arrayOf(
                intArrayOf(0, 0, boardPixelW, 2),
                intArrayOf(0, boardPixelH - 2, boardPixelW, 2),
                intArrayOf(0, 0, 2, boardPixelH),
                intArrayOf(boardPixelW - 2, 0, 2, boardPixelH)
            )
            val walls_thin = arrayOf(
                intArrayOf(0, 0, boardPixelW, 1),
                intArrayOf(0, boardPixelH - 1, boardPixelW, 1),
                intArrayOf(0, 0, 1, boardPixelH),
                intArrayOf(boardPixelW - 1, 0, 1, boardPixelH)
            )

            for (y in 0 until gridH) {
                val py = tileY(y * boardH)
                for (x in 0 until gridW) {
                    val px = tileX(x * boardW)
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

        g.draw3DRect(tileX(cursorX) - 1, tileY(cursorY) - 1, tileX(1) + 1, tileY(1) + 1, blinkState)

        if (mouseCursorX != -1 && editor.frame.isFocused) {
            g.color = Color(0x7FFFFFFF, true)
            g.drawRect(
                tileX(mouseCursorX) - 1, tileY(mouseCursorY) - 1,
                tileX(1) + 1, tileY(1) + 1
            )
        }

        if (indicateX != null) {
            g.color = Color(0x3399FF)
            for (i in indicateX!!.indices) {
                val x = indicateX!![i]
                val y = indicateY!![i]
                if (x == -1) continue
                g.drawRect(tileX(x), tileY(y), tileX(1) - 1, tileY(1) - 1)
            }
        }

        if (blockStartX != -1) {
            g.color = Color(0x7F3399FF, true)
            val x = min(cursorX.toDouble(), blockStartX.toDouble()).toInt()
            val y = min(cursorY.toDouble(), blockStartY.toDouble()).toInt()
            val w = ((max(cursorX.toDouble(), blockStartX.toDouble()) - x + 1).toInt())
            val h = ((max(cursorY.toDouble(), blockStartY.toDouble()) - y + 1).toInt())
            g.fillRect(tileX(x), tileY(y), tileX(w), tileY(h))
        }

        if (GlobalEditor.isBlockBuffer()) {
            g.color = Color(0x5FFF8133, true)
            val x2 = min((width - 1).toDouble(), (cursorX + GlobalEditor.blockBufferW - 1).toDouble())
                .toInt()
            val y2 = min((height - 1).toDouble(), (cursorY + GlobalEditor.blockBufferH - 1).toDouble())
                .toInt()
            val w = x2 - cursorX + 1
            val h = y2 - cursorY + 1
            g.fillRect(tileX(cursorX), tileY(cursorY), tileX(w), tileY(h))
        }

        if (placingBlockW != -1) {
            g.color = Color(0x5F33ff99, true)
            val x2 = min((width - 1).toDouble(), (cursorX + placingBlockW - 1).toDouble()).toInt()
            val y2 = min((height - 1).toDouble(), (cursorY + placingBlockH - 1).toDouble()).toInt()
            val w = x2 - cursorX + 1
            val h = y2 - cursorY + 1
            g.fillRect(tileX(cursorX), tileY(cursorY), tileX(w), tileY(h))
        }
        //g.drawImage(charBuffer, 0, 0, new Color(palette[7], true), null);
        //g.drawImage(charBuffer, 0, 0, 16, 14, 8, 14, 16, 28, bgColor, null);
    }

    private fun drawImg(g: Graphics, image: Image?) {
        g.drawImage(image, 0, 0, tileX(width), tileY(height), 0, 0, width * CHAR_W, height * CHAR_H, null)
    }

    override fun mouseClicked(e: MouseEvent) {
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
    }

    override fun mouseReleased(e: MouseEvent) {
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
        if (mouseCursorX != -1) {
            mouseCursorX = -1
            mouseCursorY = -1
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
        val newMouseCursorX: Int
        val newMouseCursorY: Int
        val mc = getMouseCoords(e.point)
        val x = mc!!.x
        val y = mc.y
        if (x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) {
            newMouseCursorX = -1
            newMouseCursorY = -1
        } else {
            newMouseCursorX = x
            newMouseCursorY = y
        }
        if (newMouseCursorX != mouseCursorX || newMouseCursorY != mouseCursorY) {
            mouseCursorX = newMouseCursorX
            mouseCursorY = newMouseCursorY
            repaint()
        }
    }

    fun setBlink(b: Boolean) {
        blinkState = b
        repaint()
    }

    fun setCursor(x: Int, y: Int) {
        cursorX = x
        cursorY = y
        repaint()
    }

    fun setIndicate(x: IntArray?, y: IntArray?) {
        indicateX = x
        indicateY = y
        repaint()
    }

    fun htmlColour(colIdx: Int): String {
        val r = (palette[colIdx] and 0x00FF0000) shr 16
        val g = (palette[colIdx] and 0x0000FF00) shr 8
        val b = (palette[colIdx] and 0x000000FF) shr 0
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun setSelectionBlock(blockStartX: Int, blockStartY: Int) {
        this.blockStartX = blockStartX
        this.blockStartY = blockStartY
        repaint()
    }

    fun setPlacingBlock(w: Int, h: Int) {
        this.placingBlockW = w
        this.placingBlockH = h
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

    // TODO: HOT FUNCTION (4%)
    fun drawVoid(x: Int, y: Int, w: Int, h: Int) {
        for (i in 0..1) {
            val graphics = boardBuffers[i]!!.graphics
            graphics.color = Color(0x7F7F7F)
            graphics.fillRect(x * CHAR_W, y * CHAR_H, w * CHAR_W, h * CHAR_H)
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
            setData(width, height, cols!!, chars!!, 0, 0, true, show)
        }
    }

    fun setAtlas(atlas: Atlas?, boardW: Int, boardH: Int, drawAtlasLines: Boolean) {
        this.atlas = atlas
        this.boardW = boardW
        this.boardH = boardH
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
}
