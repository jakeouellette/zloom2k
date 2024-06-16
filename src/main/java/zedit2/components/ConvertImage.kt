package zedit2.components

import zedit2.event.Converter
import zedit2.event.ConverterCallback
import zedit2.model.Stat
import zedit2.model.Tile
import zedit2.model.spatial.Dim
import zedit2.util.ZType
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import java.util.*
import javax.swing.*
import javax.swing.Timer
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.max

class ConvertImage(private val editor: WorldEditor, sourceImage: Image) : JDialog() {
    private lateinit var image: BufferedImage
    private var matcherEnabled = true

    private lateinit var sourceImageLabel: JLabel
    private lateinit var destImageLabel: JLabel
    private var previewTimer: Timer? = null

    private lateinit var srcTop: JSpinner
    private lateinit var srcBottom: JSpinner
    private lateinit var srcLeft: JSpinner
    private lateinit var srcRight: JSpinner
    private lateinit var sizeW: JSpinner
    private lateinit var sizeH: JSpinner
    private lateinit var macroW: JSpinner
    private lateinit var macroH: JSpinner
    private lateinit var objCount: JSpinner
    private lateinit var changeListener: ChangeListener
    private var convertSetup : Boolean = false
    private lateinit var elementCheckboxes: ArrayList<JCheckBox>
    private var converter: Converter? = null
    private lateinit var ok: JButton
    private lateinit var livePreviewChk: JCheckBox

    private var checkingValue = 0

    private var bufferDim = Dim(0,0)
    private var buffer: Array<Tile?>? = null

    private val ditherOnly = HashSet(mutableListOf("Empty", "Water", "Floor", "Solid", "Normal", "Breakable"))
    private val allGfx = HashSet(
        mutableListOf(
            "Player",
            "Ammo",
            "Torch",
            "Gem",
            "Key",
            "Door",
            "Scroll",
            "Passage",
            "Duplicator",
            "Bomb",
            "Energizer",
            "Bullet",
            "Water",
            "Floor",
            "Solid",
            "Normal",
            "Breakable",
            "Boulder",
            "SliderNS",
            "SliderEW",
            "BlinkWall",
            "Ricochet",
            "HBlinkRay",
            "Bear",
            "Ruffian",
            "Slime",
            "Shark",
            "Pusher",
            "Lion",
            "Tiger",
            "VBlinkRay",
            "Head",
            "Segment",
            "BlueText",
            "GreenText",
            "CyanText",
            "RedText",
            "PurpleText",
            "BrownText",
            "BlackText",
            "CustomText"
        )
    )

    init {
        getBufferedImage(sourceImage) { e: ActionEvent? -> createGUI() }
    }

    private fun createGUI() {
        title = "Convert Image"
        setIconImage(editor.canvas.extractCharImage(20, 110, 1, 1, false, "$"))
        Util.addEscClose(this, getRootPane())
        modalityType = ModalityType.APPLICATION_MODAL
        defaultCloseOperation = DISPOSE_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                terminateConverter()
            }
        })
        val cp = contentPane
        val ge = editor.globalEditor
        cp.layout = BorderLayout()

        changeListener = ChangeListener { e: ChangeEvent? -> convert() }

        previewTimer = null
        sourceImageLabel = JLabel()
        destImageLabel = JLabel()
        sourceImageLabel!!.horizontalAlignment = SwingConstants.CENTER
        sourceImageLabel!!.verticalAlignment = SwingConstants.CENTER
        destImageLabel!!.horizontalAlignment = SwingConstants.CENTER
        destImageLabel!!.verticalAlignment = SwingConstants.CENTER

        val l = JScrollPane(sourceImageLabel)
        val r = JScrollPane(destImageLabel)
        l.preferredSize = Dimension(256, 256)
        r.preferredSize = Dimension(256, 256)
        val adjListenerH = AdjustmentListener { e: AdjustmentEvent -> scrollMatch(e, l, r, true) }
        val adjListenerV = AdjustmentListener { e: AdjustmentEvent -> scrollMatch(e, l, r, false) }
        l.horizontalScrollBar.addAdjustmentListener(adjListenerH)
        l.verticalScrollBar.addAdjustmentListener(adjListenerV)
        r.horizontalScrollBar.addAdjustmentListener(adjListenerH)
        r.verticalScrollBar.addAdjustmentListener(adjListenerV)
        val imgPane = JPanel(GridLayout(1, 2, 4, 4))
        imgPane.add(l)
        imgPane.add(r)

        val cfgPane = JPanel(BorderLayout())

        //var splitPane = new JPanel(JSplitPane.VERTICAL_SPLIT, imgPane, cfgPane);
        val splitPane = JPanel(BorderLayout())
        splitPane.add(imgPane, BorderLayout.CENTER)
        splitPane.add(cfgPane, BorderLayout.SOUTH)

        val optionsBox = JPanel(GridLayout(0, 1, 0, 0))

        val w = image!!.width
        val h = image!!.height
        srcLeft = addSpinner(optionsBox, "Left:", SpinnerNumberModel(0, 0, w - 1, 1))
        srcRight = addSpinner(optionsBox, "Right:", SpinnerNumberModel(w - 1, 0, w - 1, 1))
        srcTop = addSpinner(optionsBox, "Top:", SpinnerNumberModel(0, 0, h - 1, 1))
        srcBottom = addSpinner(optionsBox, "Bottom:", SpinnerNumberModel(h - 1, 0, h - 1, 1))
        macroW = addSpinner(optionsBox, "Macroblock W:", SpinnerListModel(arrayOf(1, 2, 4, 8)))
        macroH = addSpinner(optionsBox, "Macroblock H:", SpinnerListModel(arrayOf(1, 2, 7, 14)))
        macroW!!.value = GlobalEditor.getInt("CONVERT_MACROW", 8)
        macroH!!.value = GlobalEditor.getInt("CONVERT_MACROH", 14)

        var defaultCharW = max(1.0, ((w + 4) / 8).toDouble()).toInt()
        var defaultCharH = max(1.0, ((h + 7) / 14).toDouble()).toInt()
        // TODO(jakeouellette): Clean up this comparison slightly
        if (defaultCharW > editor.dim.w) {
            defaultCharW = editor.dim.w
            defaultCharH = max(1.0, ((h * 8 * editor.dim.w / w + 7) / 14).toDouble()).toInt()
        }
        if (defaultCharH > editor.dim.h) {
            defaultCharH = editor.dim.h
            defaultCharW = max(1.0, ((w * 14 * editor.dim.h / h + 4) / 8).toDouble()).toInt()
        }

        sizeW = addSpinner(optionsBox, "Output Width:", SpinnerNumberModel(defaultCharW, 1, editor.dim.w, 1))
        sizeH = addSpinner(optionsBox, "Output Height:", SpinnerNumberModel(defaultCharH, 1, editor.dim.h, 1))
        objCount = addSpinner(optionsBox, "Max Stats:", SpinnerNumberModel(0, 0, 32767, 1))

        objCount!!.value = GlobalEditor.getInt("CONVERT_MAXSTATS", 0)

        val usePanel = JPanel(GridLayout(0, 5))

        val leftPane = JPanel(BorderLayout())
        leftPane.add(optionsBox, BorderLayout.CENTER)

        cfgPane.add(leftPane, BorderLayout.WEST)
        cfgPane.add(usePanel, BorderLayout.CENTER)

        val buttonBar = JPanel(BorderLayout())
        val buttonGroup = JPanel(GridLayout(1, 0, 4, 0))
        buttonGroup.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        leftPane.add(buttonBar, BorderLayout.SOUTH)
        buttonBar.add(buttonGroup, BorderLayout.EAST)

        livePreviewChk = JCheckBox("Preview Output Image", GlobalEditor.getBoolean("CONVERT_LIVEPREVIEW", true))

        buttonBar.add(livePreviewChk, BorderLayout.NORTH)

        ok = JButton("OK")
        getRootPane().defaultButton = ok
        ok.isEnabled = false
        ok.addActionListener { e: ActionEvent? ->
            val szzt = editor.worldData.isSuperZZT
            // TODO(jakeouellette): Confirm buffer is never null here.
            // TODO(jakeouellette): Consider a different initialization strategy
            val nonNullTiles : Array<Tile> = buffer!!.map { tile : Tile? ->
                tile ?: throw RuntimeException("Tile uninitialized past initialization phase, unexpected.")
            }.toTypedArray()
            GlobalEditor.setBlockBuffer(bufferDim, nonNullTiles!!, false, szzt)
            GlobalEditor.setInt("CONVERT_MACROW", macroW!!.value as Int)
            GlobalEditor.setInt("CONVERT_MACROH", macroH!!.value as Int)
            GlobalEditor.setInt("CONVERT_MAXSTATS", objCount!!.value as Int)
            GlobalEditor.setString(if (szzt) "CONVERT_SZZT_TYPES" else "CONVERT_ZZT_TYPES", elementsString)
            GlobalEditor.setBoolean("CONVERT_LIVEPREVIEW", livePreviewChk!!.isSelected)
            dispose()
        }
        val cancel = JButton("Cancel")
        cancel.addActionListener { e: ActionEvent? -> dispose() }
        buttonGroup.add(ok)
        buttonGroup.add(cancel)

        val szzt = editor.worldData.isSuperZZT
        elementCheckboxes = ArrayList()
        val itemListener = ItemListener { e: ItemEvent? -> convert() }
        val alreadySelected = HashSet<String>()

        for (i in 0..254) {
            val name = ZType.getName(szzt, i)
            if (name.startsWith("Unknown")) continue
            val chkb = JCheckBox(name)
            chkb.isSelected = alreadySelected.contains(name)
            val minSize = chkb.minimumSize
            minSize.height -= 8
            minSize.width -= 8
            chkb.preferredSize = minSize
            chkb.addItemListener(itemListener)
            elementCheckboxes!!.add(chkb)
            usePanel.add(chkb)
        }
        // TODO(jakeouellette): Confirm non-nullable
        elementsString = GlobalEditor.getString(if (szzt) "CONVERT_SZZT_TYPES" else "CONVERT_ZZT_TYPES")!!

        addButton(usePanel, "Select all") { e: ActionEvent? ->
            for (chkb in elementCheckboxes!!) chkb.isSelected = true
        }
        addButton(usePanel, "Deselect all") { e: ActionEvent? ->
            for (chkb in elementCheckboxes!!) chkb.isSelected = false
        }
        addButton(usePanel, "Dither only") { e: ActionEvent? ->
            for (chkb in elementCheckboxes!!) chkb.isSelected = ditherOnly.contains(chkb.text)
        }
        addButton(usePanel, "All gfx") { e: ActionEvent? ->
            for (chkb in elementCheckboxes!!) chkb.isSelected = allGfx.contains(chkb.text)
        }
        usePanel.border = BorderFactory.createTitledBorder("Element use")

        // Crop Source N / S / E / W
        // Destination W / H
        // Macroblock W (1, 2, 4, 8) / H (1, 2, 7, 14)
        // Objects
        // Types to use
        cp.add(splitPane, BorderLayout.CENTER)

        pack()
        setLocationRelativeTo(editor.frameForRelativePositioning)
        convertSetup = true
        convert()
        isVisible = true
    }

    private var elementsString: String
        get() {
            val str = StringBuilder(elementCheckboxes!!.size)
            for (chkb in elementCheckboxes!!) {
                str.append(if (chkb.isSelected) 'X' else '.')
            }
            return str.toString()
        }
        private set(string) {
            if (string == null) return
            if (string.length != elementCheckboxes!!.size) return
            for (i in 0 until string.length) {
                elementCheckboxes!![i].isSelected = string[i] == 'X'
            }
        }

    private fun addButton(usePanel: JPanel, buttonText: String, listener: ActionListener) {
        val button = JButton(buttonText)
        usePanel.add(button)

        button.addActionListener(listener)
        val minSize = button.minimumSize
        minSize.height -= 8
        button.preferredSize = minSize
    }


    private fun convert() {
        if (!convertSetup) return
        terminateConverter()
        ok!!.isEnabled = false
        buffer = null

        val left = srcLeft!!.value as Int
        val right = srcRight!!.value as Int
        val top = srcTop!!.value as Int
        val bottom = srcBottom!!.value as Int
        val mw = macroW!!.value as Int
        val mh = macroH!!.value as Int
        val outw = sizeW!!.value as Int
        val outh = sizeH!!.value as Int
        val maxObjs = objCount!!.value as Int


        val croppedSourceImage = cropImage(image, left, right, top, bottom)
        val scaledImage = scaleImage(croppedSourceImage, outw * DosCanvas.CHAR_W, outh * DosCanvas.CHAR_H)
        updateSourceImage(scaledImage)
        val outputImage = BufferedImage(outw * DosCanvas.CHAR_W, outh * DosCanvas.CHAR_H, BufferedImage.TYPE_INT_RGB)
        val outg = outputImage.graphics
        outg.color = Color(0x7F7F7F)
        outg.fillRect(0, 0, outputImage.width, outputImage.height)
        updateDestImage(outputImage)

        val szzt = editor.worldData.isSuperZZT
        converter = Converter(szzt, outw, outh, mw, mh, maxObjs, scaledImage!!)
        checkingValue++
        converter!!.setCheckVal(checkingValue)
        //var usableElements = new ArrayList<Integer>();
        var anySelected = false
        for (chkb in elementCheckboxes!!) {
            if (chkb.isSelected) {
                val elementName = chkb.text
                val id = ZType.getId(szzt, elementName)
                //usableElements.add(id);
                val tile = Tile(id, 15)
                val chr = ZType.getChar(szzt, tile)
                converter!!.addElement(id, chr)
                anySelected = true
            }
        }

        val output = arrayOfNulls<Tile>(outw * outh)
        val outputChr = IntArray(outw * outh)
        val outputVcol = IntArray(outw * outh)
        Arrays.fill(outputChr, -1)
        converter!!.setBlink(GlobalEditor.getBoolean("BLINKING", true))
        converter!!.setGfx(editor.canvas)
        converter!!.setCallback(object : ConverterCallback {
            override fun converted(checkVal: Int, x: Int, y: Int, id: Int, col: Int, chr: Int, vcol: Int) {
                //System.out.println("Converted called from: " + Thread.currentThread());
                if (checkVal != checkingValue) return
                val t: Tile
                if (id == ZType.OBJECT && chr != 32) {
                    val stat = Stat(szzt)
                    stat.cycle = 3
                    stat.p1 = chr
                    t = Tile(id, col, stat)
                } else {
                    t = Tile(id, col)
                }
                output[y * outw + x] = t
                outputChr[y * outw + x] = chr
                outputVcol[y * outw + x] = vcol
            }

            override fun finished(checkVal: Int) {
                if (checkVal != checkingValue) return
                //System.out.println("finished called from: " + Thread.currentThread());
                bufferDim = Dim(outw, outh)
                buffer = output
                ok.isEnabled = true
                SwingUtilities.invokeLater { updatePreview(outw, outh, outputChr, outputVcol, outputImage) }
                previewTimer!!.stop()
                previewTimer = null
            }
        })

        if (anySelected) {
            converter!!.beginConvert()
            previewTimer = createPreviewTimer(outw, outh, outputChr, outputVcol, outputImage)
            previewTimer!!.start()
        }

        //(outw, outh, mw, mh, maxObjs, usableElements, scaledImage);
    }

    private fun updatePreview(
        outw: Int,
        outh: Int,
        outputChr: IntArray,
        outputVcol: IntArray,
        outputImage: BufferedImage
    ) {
        if (!livePreviewChk!!.isSelected) return
        //System.out.println("updatePreview called from: " + Thread.currentThread());
        val g = outputImage.graphics
        val canvas = editor.canvas
        for (y in 0 until outh) {
            for (x in 0 until outw) {
                val i = y * outw + x
                val chr = outputChr[i]
                if (chr != -1) {
                    val vcol = outputVcol[i]
                    val tileGfx = canvas.extractCharImage(chr, vcol, 1, 1, false, "$")
                    g.drawImage(tileGfx, x * DosCanvas.CHAR_W, y * DosCanvas.CHAR_H, null)
                }
            }
        }
        updateDestImage(outputImage)
    }

    private fun createPreviewTimer(
        outw: Int,
        outh: Int,
        outputChr: IntArray,
        outputVcol: IntArray,
        outputImage: BufferedImage
    ): Timer {
        val timer = Timer(250) { e: ActionEvent? -> updatePreview(outw, outh, outputChr, outputVcol, outputImage) }
        timer.isRepeats = true
        timer.isCoalesce = true
        return timer
    }

    private fun updateSourceImage(scaledImage: BufferedImage?) {
        val image = superZZTScale(scaledImage)

        sourceImageLabel!!.icon = ImageIcon(image)
    }

    private fun superZZTScale(scaledImage: BufferedImage?): Image? {
        if (editor.worldData.isSuperZZT) {
            return scaledImage!!.getScaledInstance(scaledImage.width * 2, scaledImage.height, Image.SCALE_REPLICATE)
        }
        return scaledImage
    }

    private fun updateDestImage(outputImage: BufferedImage) {
        val image = superZZTScale(outputImage)
        destImageLabel!!.icon = ImageIcon(image)
    }

    private fun terminateConverter() {
        val converter = this.converter
        if (converter != null) {
            converter.stop()
            this.converter = null
        }
    }


    private fun scaleImage(croppedSourceImage: BufferedImage?, w: Int, h: Int): BufferedImage? {
        if (croppedSourceImage!!.width == w && croppedSourceImage.height == h) return croppedSourceImage
        val newImg = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = newImg.graphics
        val g2 = g as Graphics2D
        val rh = RenderingHints(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC
        )
        g2.setRenderingHints(rh)
        g.drawImage(
            croppedSourceImage, 0, 0, newImg.width, newImg.height,
            0, 0, croppedSourceImage.width, croppedSourceImage.height, null
        )
        return newImg
    }

    private fun cropImage(image: BufferedImage?, left: Int, right: Int, top: Int, bottom: Int): BufferedImage? {
        if (left == 0 && top == 0 && right == image!!.width - 1 && bottom == image.height - 1) return image

        val width = max(1.0, (right - left).toDouble()).toInt()
        val height = max(1.0, (bottom - top).toDouble()).toInt()
        val newImg = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = newImg.graphics
        g.drawImage(image, -left, -top, null)
        return newImg
    }

    private fun getBufferedImage(sourceImage: Image, act: ActionListener) {
        val w = sourceImage.getWidth(null)
        val h = sourceImage.getHeight(null)
        if (w == -1 || h == -1) {
            waitForBufferedImage(sourceImage, act)
        }
        image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val drawnImage = image!!.graphics.drawImage(sourceImage, 0, 0, null)
        if (!drawnImage) {
            waitForBufferedImage(sourceImage, act)
        }
        val e = ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Buffered image")
        act.actionPerformed(e)
    }

    private fun waitForBufferedImage(sourceImage: Image, act: ActionListener) {
        val timer = Timer(10) { e: ActionEvent? -> getBufferedImage(sourceImage, act) }
        timer.start()
    }

    private fun addSpinner(optionsBox: JPanel, label: String, model: SpinnerModel): JSpinner {
        val indivBox = JPanel(GridLayout(1, 2, 4, 0))
        indivBox.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        val lbl = JLabel(label)
        lbl.horizontalAlignment = SwingConstants.RIGHT
        indivBox.add(lbl)
        val spinner = JSpinner(model)
        spinner.addChangeListener(changeListener)
        indivBox.add(spinner)
        optionsBox.add(indivBox)
        return spinner
    }

    private fun scrollMatch(e: AdjustmentEvent, l: JScrollPane, r: JScrollPane, horiz: Boolean) {
        if (matcherEnabled) {
            matcherEnabled = false
            val scrollBar = e.source
            if (horiz) {
                l.horizontalScrollBar.value = e.value
                r.horizontalScrollBar.value = e.value
            } else {
                l.verticalScrollBar.value = e.value
                r.verticalScrollBar.value = e.value
            }

            matcherEnabled = true
        }
    }
}
