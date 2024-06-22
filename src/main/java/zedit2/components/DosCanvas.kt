package zedit2.components

import zedit2.components.Util.clamp
import zedit2.components.WorldEditor.Companion.EXPAND_X
import zedit2.components.WorldEditor.Companion.EXPAND_Y
import zedit2.event.CanvasMouseListener
import zedit2.util.Data
import zedit2.model.Atlas
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
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
import javax.swing.JScrollPane
import kotlin.math.max

class DosCanvas(private val editor: WorldEditor, override var zoomx: Double, override var zoomy: Double):
   FocusListener,  ImageRetriever, DosCanvasState {

       // TODO(jakeouellette): Abstract the callsite of this away to make it harder to reach into the view.
    val visibleRect: Rectangle
        get() = this.view.visibleRect
    var centreView : Boolean = false
    val viewDim: Dim
        get() = Dim(view.width, view.height)
    val viewPos: Pos
        get() = Pos(view.x, view.y)
    override var dim: Dim = Dim(0, 0)
    override val boardBuffers = arrayOfNulls<BufferedImage>(2)
    override val boards
        get() = editor.boards
    override var blinkState = false
    override val placingBlockDim
        get() = editor.moveBlockDim
    override val isFocused
        get() = editor.frame.isFocused
    override var caretPos = Pos(0, 0)
//        set(value) {
//            Logger.i(TAG) {"caret updated"}
//            RuntimeException("s").printStackTrace()
//            field = value
//            view.repaint()
//        }
    override var indicatePos: Array<Pos>? = null
    override var mouseCursorPos = Pos(-1, -1)
        set(value) {
            field = value
            view.repaint()
        }
    override var blockStartPos = Pos(-1, -1)
    override var drawing = false
        set(value) {
            field = value
            view.repaint()
        }
    override var textEntry = false
        set(value) {
            field = value
            view.repaint()
        }
    override var boardDim = Dim(0, 0)
    override var atlas: Atlas? = null
    override var drawAtlasLines = false
    private var show: ByteArray? = null
    private var chars: ByteArray? = null
    private var cols: ByteArray? = null
    private var blink = true
    lateinit var charset: ByteArray
        private set

    var paletteData: ByteArray? = null
    private val view = DosCanvasComponent(this)
    private lateinit var palette: IntArray
    private var charBuffer: BufferedImage? = null

    init {
        initialiseBuffers()
        val mouseListener : CanvasMouseListener = CanvasMouseListener(
            { view.requestFocusInWindow()},
            editor,
            this)
        view.addMouseListener(mouseListener)
        view.addMouseMotionListener(mouseListener)
        view.addMouseWheelListener(mouseListener)
        view.isFocusable = true
        view.addFocusListener(this)
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

    fun extractCharImageWH(
        chr: Int,
        col: Int,
        zoomx: Int,
        zoomy: Int,
        blinkingTime: Boolean,
        pattern: String, dim: Dim,
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
        val ar = IntArray(dim.arrSize)

        for (col in 0 until PALETTE_SIZE) {
            for (chr in 0 until CHAR_COUNT) {
                for (y in 0 until CHAR_H) {
                    for (x in 0 until CHAR_W) {
                        val pxy = Pos(
                            chr * CHAR_W + x,
                            col * CHAR_H + y
                        )
                        val pIdx = pxy.arrayIdx(dim.w)

                        // Is this pixel set in this char?
                        val charRow = charset[chr * CHAR_H + y]
                        val charMask = (128 shr x).toByte()
                        if ((charRow.toInt() and charMask.toInt()) != 0) {
                            ar[pIdx] = palette[col]
                        } else {
                            ar[pIdx] = TRANSPARENT
                        }
                    }
                }
            }
        }

        raster.setDataElements(0, 0, dim.w, dim.h, ar)
    }

    @Throws(IOException::class)
    private fun loadPalette(path: File?) {
        var pdata: ByteArray
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

    fun setDimensions(d: Dim) {
        if (dim != d) {
            dim = d
            fullRefresh()
        }
    }

    private fun fullRefresh() {
        this.cols = ByteArray(dim.arrSize)
        this.chars = ByteArray(dim.arrSize)
        this.show = ByteArray(dim.arrSize)

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
        dim: Dim,
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
        dim: Dim,
        cols: ByteArray,
        chars: ByteArray,
        offset: Pos,
        redrawAll: Boolean,
        show: ByteArray?
    ) {
        if (cols.size != dim.arrSize) {
            throw RuntimeException("Dimensions do not match colour array size ${cols.size} ${dim.arrSize}")
        }
        if (chars.size != dim.arrSize) {
            throw RuntimeException("Dimensions do not match char array size ${chars.size} ${dim.arrSize}")
        }

        val boardBufferGraphics = arrayOfNulls<Graphics>(2)
        for (i in 0..1) {
            boardBufferGraphics[i] = boardBuffers[i]!!.graphics
        }
        for (dy in 0 until dim.h) {
            for (dx in 0 until dim.w) {
                val dxy = Pos(dx, dy)
                val dxyIdx = dxy.arrayIdx(dim.w)
                val oxy = dxy + offset
                val oxyIdx = oxy.arrayIdx(this@DosCanvas.dim.w)
                var tshow = if (show != null) {
                    show[dxyIdx]
                } else {
                    0
                }
                if ((redrawAll || this.cols!![oxyIdx] != cols[dxyIdx]) || this.chars!![oxyIdx] != chars[dxyIdx] || this.show!![oxyIdx] != tshow) {
                    // TODO(jakeouellette): Clean up this state machine.
                    this.chars!![oxyIdx] = chars[dxyIdx]
                    this.cols!![oxyIdx] = cols[dxyIdx]
                    this.show!![oxyIdx] = tshow
                    val chr = chars[dxyIdx].toInt() and 0xFF
                    val col = cols[dxyIdx].toInt() and 0xFF

                    TilePainters.drawTile(
                        boardBufferGraphics[0],
                        chr,
                        col,
                        oxy.x,
                        oxy.y,
                        1,
                        1,
                        blink,
                        false,
                        palette,
                        charBuffer
                    )
                    if (tshow.toInt() != 0) {
                        drawShow(boardBufferGraphics[1], chr, col, view.x, view.y, 1, 1, tshow)
                    } else {
                        TilePainters.drawTile(
                            boardBufferGraphics[1],
                            chr,
                            col,
                            oxy.x,
                            oxy.y,
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

    fun setBlink(b: Boolean) {
        blinkState = b
        view.repaint()
    }

    fun setCaret(pos: Pos) {
        caretPos = pos
        view.repaint()
    }

    fun setIndicate(xys: Array<Pos>?) {
        indicatePos = xys
        view.repaint()
    }

    fun colorColor(colIdx: Int): Color {
        return Color.decode(htmlColour(colIdx))
    }

    fun htmlColour(colIdx: Int): String {
        val r = (palette[colIdx] and 0x00FF0000) shr 16
        val g = (palette[colIdx] and 0x0000FF00) shr 8
        val b = (palette[colIdx] and 0x000000FF) shr 0
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun setSelectionBlock(blockStartPos: Pos) {
        this.blockStartPos = blockStartPos
        view.repaint()
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
    fun drawVoid(pos: Pos, dim: Dim) {
        for (i in 0..1) {
            val graphics = boardBuffers[i]!!.graphics
            graphics.color = Color(0x7F7F7F)
            graphics.fillRect(pos.x * CHAR_W, pos.y * CHAR_H, dim.w * CHAR_W, dim.h * CHAR_H)
        }
    }

    @Throws(IOException::class)
    fun setPalette(file: File?) {
        loadPalette(file)
        refreshBuffer()
        resetData()
        view.repaint()
    }

    @Throws(IOException::class)
    fun setCharset(file: File?) {
        loadCharset(file)
        refreshBuffer()
        resetData()
        view.repaint()
    }

    @Throws(IOException::class)
    fun setCP(charset: File?, palette: File?) {
        loadCharset(charset)
        loadPalette(palette)
        refreshBuffer()
        resetData()
        view.repaint()
    }

    private fun resetData() {
        if (cols != null && chars != null) {
            setData(dim, cols!!, chars!!, Pos.ZERO, true, show)
        }
    }

    fun setAtlas(atlas: Atlas?, boardDim: Dim, drawAtlasLines: Boolean) {
        this.atlas = atlas
        this.boardDim = boardDim
        this.drawAtlasLines = drawAtlasLines
    }

    override fun focusGained(e: FocusEvent?) {
        Logger.i(this@DosCanvas.TAG) { "Focus gained, $e" }
    }

    override fun focusLost(e: FocusEvent?) {
        Logger.i(this@DosCanvas.TAG) { "Focus lost, $e" }
    }

    fun getMousePos(p: Point): Pos {
            val x = (p.x / zoomx / CHAR_W).toInt()
            val y = (p.y / zoomy / CHAR_H).toInt()
           return Pos(x, y)
    }

    fun repaint() {
        // TODO(jakeouellette): places where repaint is needed should likely be decided inside DosCanvas
        view.repaint()
    }

    fun revalidate() {
        // TODO(jakeouellette): places where revalidate is needed should likely be decided inside DosCanvas
        view.revalidate()
    }

    fun scrollRectToVisible(worldDim: Dim) {
        // TODO(jakeouellette): places where scrollRectToVisible is needed should likely be decided inside DosCanvas
        var dim : Dim
        var pos : Pos
        if(centreView) {
            view.revalidate()
            val visibleRect = view.visibleRect

            // TODO(jakeouellette): Convert to convienence
            dim = Dim(
                clamp(visibleRect.width, 0, this.getCharW(worldDim.w)),
                clamp(visibleRect.height, 0, this.getCharH(worldDim.h))
            )
            val charDim = Dim(
                this.getCharW(1),
                this.getCharH(1))
            pos =
                Pos(
                    max(0,
                    (this.getCharW(caretPos.x) + (charDim.w - dim.w) / 2)),
                    max(0,this.getCharH(caretPos.y) + (charDim.h - dim.h) / 2)
                )
            centreView = false
        } else {
            dim = Dim(this.getCharW(1), this.getCharH(1))
            pos = Pos(this.getCharW(caretPos.x),this.getCharH(caretPos.y))

            // FIXME(jakeouellette): This math can allow a scroll to slightly below / above the view.
            // Expand this slightly

            pos -= Pos(this.getCharW(EXPAND_X), this.getCharH(EXPAND_Y))
            dim += Dim(this.getCharW(EXPAND_X * 2), this.getCharH(EXPAND_Y * 2))
        }
        val rect = Rectangle(pos.x, pos.y, dim.w, dim.h)
        view.scrollRectToVisible(rect)
    }

    fun createScrollPane(editor: WorldEditor) : JScrollPane {
        Logger.i(TAG) {"Requesting Focus."}
        this.view.isRequestFocusEnabled = true
        this.view.requestFocusInWindow()
        editor.lastFocusedElement = this.view
        this.setBlinkMode(GlobalEditor.getBoolean("BLINKING", true))
        // change(jakeouellette): was this.layeredPane
        refreshKeymapping()

        //drawBoard();
        return object : JScrollPane(this.view) {
            init {
                horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_ALWAYS
                verticalScrollBarPolicy = VERTICAL_SCROLLBAR_ALWAYS
            }
        }
    }

    // TODO(jakeouellette): cleanup this bit of indirection
    fun refreshKeymapping() {
        editor.addKeybinds(this.view)
    }
    fun scrollRectToVisible(worldRect: Rectangle) {
        // TODO(jakeouellette): Move the callsite more into the canvas
        this.view.scrollRectToVisible(worldRect)
    }

    // TODO(jakeouellette): I don't love this abstraction.
    fun requestFocusInWindow(): Component {
        this.view.requestFocusInWindow()
        return this.view
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
