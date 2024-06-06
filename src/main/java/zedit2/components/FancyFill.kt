package zedit2.components

import zedit2.model.Clip.Companion.decode
import zedit2.model.Tile
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.event.*
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.*

class FancyFill(editor: WorldEditor, listener: ActionListener, filled: Array<ByteArray>) :
    JDialog(editor.frame, "Gradient Fill") {
    private val rng = Random()
    private val vertLabel: JLabel
    private val horizLabel: JLabel
    private val angleLabel: JLabel
    private val repeatLabel: JLabel
    private val xsLabel: JLabel
    private val ysLabel: JLabel
    private val diffLabel: JLabel
    private val tsengLabel: JLabel
    private val invertLabel: JLabel
    private val horizInput: JSpinner
    private val vertInput: JSpinner
    private val angleInput: JSpinner
    private val repeatInput: JSpinner
    private val xsInput: JSpinner
    private val ysInput: JSpinner
    private val diffInput: JSpinner
    private val invertChk: JCheckBox
    private val tsengInput: JSpinner
    private val sliderStart: JSpinner
    private val sliderEnd: JSpinner
    private val gradientCombo: JComboBox<String>
    private val btnLinear: JRadioButton
    private val btnBox: JRadioButton
    private val btnRadial: JRadioButton
    private val btnConic: JRadioButton
    private val btnSlime: JRadioButton
    private val filled: Array<ByteArray>
    private val listener: ActionListener
    private val editor: WorldEditor

    lateinit var xs: IntArray
    lateinit var ys: IntArray
    lateinit var tiles: Array<Tile?>
    lateinit private var gradientTiles: Array<Tile>
    private var minX = 0
    private var minY = 0
    private var maxX = 0
    private var maxY = 0

    private var fillMode = 0

    private var linearMin = 0.0
    private var linearMax = 0.0
    private var closeCmd: String
    private var tseng = 0.0
    private var tsengMatr: Array<DoubleArray>? = null

    private var tsengAvail = false

    init {
        tsengCheck()

        //gradRev();
        Util.addEscClose(this, getRootPane())

        this.editor = editor
        this.filled = filled
        this.listener = listener
        genList(filled)
        closeCmd = "undo"
        modalityType = ModalityType.APPLICATION_MODAL
        defaultCloseOperation = DISPOSE_ON_CLOSE
        val cp = contentPane
        cp.layout = BorderLayout()
        btnLinear = JRadioButton("Linear")
        btnBox = JRadioButton("Box")
        btnRadial = JRadioButton("Radial")
        btnConic = JRadioButton("Conical")
        btnSlime = JRadioButton("Slime")
        val styleGroup = object : ButtonGroup() {
            init {
                this.add(btnLinear)
                this.add(btnBox)
                this.add(btnRadial)
                this.add(btnConic)
                this.add(btnSlime)
            }
        }
        val gradientStyleBox = object : JPanel(GridLayout(1, 0)) {
            init {
                this.border = BorderFactory.createTitledBorder("Gradient style")
                this.add(btnLinear)
                this.add(btnBox)
                this.add(btnRadial)
                this.add(btnConic)
                this.add(btnSlime)
            }
        }


        btnLinear.isSelected = true
        cp.add(gradientStyleBox, BorderLayout.NORTH)

        val gradientBox = object : JPanel(BorderLayout()) {
            init {
                this.border = BorderFactory.createTitledBorder("Gradient")
            }
        }

        val comboBoxRenderer = FancyFillRenderer(editor)
        gradientCombo = JComboBox()

        val ge = editor.globalEditor
        val preSelectedKey = preselectKey
        var preSelectedIdx = -1
        val preSelectedGrad = GlobalEditor.getString(preSelectedKey)

        val grads = ArrayList<String>()
        run {
            var i = 0
            while (true) {
                val key = String.format(editor.prefix() + "GRAD_%d", i)
                if (GlobalEditor.isKey(key)) {
                    val grad = GlobalEditor.getString(key)
                    if (preSelectedGrad != null && preSelectedGrad == grad) {
                        preSelectedIdx = grads.size
                    }
                    if (grad == null) {
                        throw RuntimeException("Unexpected null grad for $key")
                    }
                    grads.add(grad)
                } else {
                    break
                }
                i++
            }
        }
        val bufMax = GlobalEditor.getInt(editor.prefix() + "BUF_MAX", 0)
        for (i in 0..bufMax) {
            val encodedBuffer = GlobalEditor.getString(String.format("%sBUF_%d", editor.prefix(), i)) ?: continue
            val clip = decode(encodedBuffer)
            if (clip.h != 1) continue
            if (clip.w == 1) continue
            grads.add(encodedBuffer)
        }

        if (preSelectedIdx == -1) {
            if (preSelectedGrad != null) {
                gradientCombo.addItem(preSelectedGrad)
            }
            preSelectedIdx = 0
        }
        for (grad in grads) {
            gradientCombo.addItem(grad)
        }
        gradientCombo.selectedIndex = preSelectedIdx

        gradientCombo.setRenderer(comboBoxRenderer)
        gradientBox.add(gradientCombo)

        gradientCombo.toolTipText = "Which gradient pattern to use. # x 1 blocks in your buffer can also be used."

        val middleLeftPanel = JPanel(BorderLayout())
        middleLeftPanel.add(gradientBox, BorderLayout.NORTH)

        val gradOptionsLeft = JPanel(GridLayout(1, 2))
        sliderStart = object : JSpinner(SpinnerNumberModel(0.0, -10.0, 10.0, 0.01)) {
            init {
                this.preferredSize = this.minimumSize
                this.model.value = 0.0
                this.toolTipText = "How far into the gradient to start (negative expands the space at the start)"
            }
        }
        sliderEnd = object : JSpinner(SpinnerNumberModel(1.0, -10.0, 10.0, 0.01)) {
            init {
                this.preferredSize = this.minimumSize
                this.model.value = 1.0
                this.toolTipText = "How far into the gradient to finish (>1 expands the space at the end)"

            }
        }

        val sliderStartBox = object : JPanel(BorderLayout()) {
            init {
                this.add(sliderStart, BorderLayout.CENTER)
                this.border = BorderFactory.createTitledBorder("Start")
            }
        }
        gradOptionsLeft.add(sliderStartBox)
        val sliderEndBox = object : JPanel(BorderLayout()) {
            init {
                this.add(sliderEnd, BorderLayout.CENTER)
                this.border = BorderFactory.createTitledBorder("Finish")

            }
        }
        gradOptionsLeft.add(sliderEndBox)
        middleLeftPanel.add(gradOptionsLeft, BorderLayout.SOUTH)
        cp.add(middleLeftPanel, BorderLayout.WEST)

        val gradOptionsRight = JPanel(GridLayout(0, 4, 4, 4))
        horizLabel = JLabel("Horizontal:")
        vertLabel = JLabel("Vertical:")
        angleLabel = JLabel("Angle:")
        repeatLabel = JLabel("Repeats:")
        xsLabel = JLabel("X-Weight:")
        ysLabel = JLabel("Y-Weight:")
        diffLabel = JLabel("Diffusion:")
        tsengLabel = JLabel("Tseng:")
        invertLabel = JLabel("Invert:")
        horizLabel.horizontalAlignment = SwingConstants.RIGHT
        vertLabel.horizontalAlignment = SwingConstants.RIGHT
        angleLabel.horizontalAlignment = SwingConstants.RIGHT
        repeatLabel.horizontalAlignment = SwingConstants.RIGHT
        xsLabel.horizontalAlignment = SwingConstants.RIGHT
        ysLabel.horizontalAlignment = SwingConstants.RIGHT
        diffLabel.horizontalAlignment = SwingConstants.RIGHT
        tsengLabel.horizontalAlignment = SwingConstants.RIGHT
        invertLabel.horizontalAlignment = SwingConstants.RIGHT

        horizInput = JSpinner(SpinnerNumberModel(0.5, 0.0, 1.0, 0.01))
        vertInput = JSpinner(SpinnerNumberModel(0.5, 0.0, 1.0, 0.01))
        angleInput = JSpinner(SpinnerNumberModel(0, 0, 360, 1))
        repeatInput = JSpinner(SpinnerNumberModel(0, 0, 100, 1))
        xsInput = JSpinner(SpinnerNumberModel(0.8, 0.0, 10.0, 0.1))
        ysInput = JSpinner(SpinnerNumberModel(1.4, 0.0, 10.0, 0.1))
        diffInput = JSpinner(SpinnerNumberModel(0.0, 0.0, 1.0, 0.002))
        invertChk = JCheckBox()
        tsengInput = JSpinner(SpinnerNumberModel(0.1, 0.0, 1.0, 0.05))

        setTT(horizLabel, horizInput, "Horizontal epicentre of gradient fill")
        setTT(vertLabel, vertInput, "Vertical epicentre of gradient fill")
        setTT(angleLabel, angleInput, "Angle of linear fill, angle offset of conical fill")
        setTT(repeatLabel, repeatInput, "Number of times to repeat the gradient")
        setTT(xsLabel, xsInput, "Horizontal cost of fill operation")
        setTT(ysLabel, ysInput, "Vertical cost of fill operation")
        setTT(diffLabel, diffInput, "Amount of random variance to apply to the gradient")
        setTT(invertLabel, invertChk, "Whether to invert (reverse) the gradient pattern")
        setTT(tsengLabel, tsengInput, "Amount to Tseng-ify this gradient")

        configOpts

        gradOptionsRight.add(horizLabel)
        gradOptionsRight.add(horizInput)
        gradOptionsRight.add(xsLabel)
        gradOptionsRight.add(xsInput)
        gradOptionsRight.add(vertLabel)
        gradOptionsRight.add(vertInput)
        gradOptionsRight.add(ysLabel)
        gradOptionsRight.add(ysInput)
        gradOptionsRight.add(angleLabel)
        gradOptionsRight.add(angleInput)
        gradOptionsRight.add(diffLabel)
        gradOptionsRight.add(diffInput)
        gradOptionsRight.add(repeatLabel)
        gradOptionsRight.add(repeatInput)
        gradOptionsRight.add(invertLabel)
        gradOptionsRight.add(invertChk)
        if (tsengAvail) {
            gradOptionsRight.add(tsengLabel)
            gradOptionsRight.add(tsengInput)
        }

        cp.add(gradOptionsRight, BorderLayout.EAST)

        setIconImage(editor.canvas.extractCharImage(176, 0x6e, 1, 1, false, "$"))

        val bottomPane = JPanel(BorderLayout())
        val buttonPane = JPanel(GridLayout(1, 2, 4, 0))
        val okBtn = JButton("OK")
        getRootPane().defaultButton = okBtn
        val cancelBtn = JButton("Cancel")
        okBtn.addActionListener { e: ActionEvent? ->
            closeCmd = "done"
            setConfigOpts()
            dispose()
        }
        cancelBtn.addActionListener { e: ActionEvent? ->
            closeCmd = "undo"
            dispose()
        }
        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) {
                updateListener(closeCmd)
            }
        })
        buttonPane.add(okBtn)
        buttonPane.add(cancelBtn)
        buttonPane.border = BorderFactory.createEmptyBorder(4, 0, 4, 4)
        bottomPane.add(buttonPane, BorderLayout.EAST)

        cp.add(bottomPane, BorderLayout.SOUTH)
        isResizable = false

        pack()

        setLocationRelativeTo(editor.frameForRelativePositioning)

        val il = ItemListener { e: ItemEvent? -> updateContents() }
        val cl = ChangeListener { e: ChangeEvent? -> updateContents() }
        btnLinear.addItemListener(il)
        btnBox.addItemListener(il)
        btnRadial.addItemListener(il)
        btnConic.addItemListener(il)
        btnSlime.addItemListener(il)
        invertChk.addItemListener(il)
        gradientCombo.addItemListener(il)
        sliderStart.addChangeListener(cl)
        sliderEnd.addChangeListener(cl)
        horizInput.addChangeListener(cl)
        vertInput.addChangeListener(cl)
        angleInput.addChangeListener(cl)
        repeatInput.addChangeListener(cl)
        xsInput.addChangeListener(cl)
        ysInput.addChangeListener(cl)
        ysInput.addChangeListener(cl)
        diffInput.addChangeListener(cl)
        tsengInput.addChangeListener(cl)

        updateContents()

        isVisible = true
    }

    private fun tsengCheck() {
        tsengAvail = GlobalEditor.getBoolean("AGH_MORE_DOBERMANS", false)

        val calendar = Calendar.getInstance()
        if (calendar[Calendar.MONTH] != Calendar.APRIL) return
        if (calendar[Calendar.DAY_OF_MONTH] != 1) return
        // April fools!
        tsengAvail = true
    }

    private val preselectKey: String
        get() = String.format("FILL_%s_SELECTED", if (editor.worldData.isSuperZZT) "SZZT" else "ZZT")

    private fun setTT(component1: JComponent, component2: JComponent, ttText: String) {
        component1.toolTipText = ttText
        component2.toolTipText = ttText
    }

    private fun setConfigOpts() {
        val ge = editor.globalEditor
        GlobalEditor.setString("FILL_MODE", selectedFillMode)
        GlobalEditor.setDouble("FILL_START", sliderStart.value as Double)
        GlobalEditor.setDouble("FILL_FINISH", sliderEnd.value as Double)
        GlobalEditor.setDouble("FILL_HORIZ", horizInput.value as Double)
        GlobalEditor.setDouble("FILL_VERT", vertInput.value as Double)
        GlobalEditor.setInt("FILL_ANGLE", angleInput.value as Int)
        GlobalEditor.setInt("FILL_REPEATS", repeatInput.value as Int)
        GlobalEditor.setDouble("FILL_XWEIGHT", xsInput.value as Double)
        GlobalEditor.setDouble("FILL_YWEIGHT", ysInput.value as Double)
        GlobalEditor.setDouble("FILL_DIFFUSE", diffInput.value as Double)
        GlobalEditor.setBoolean("FILL_INVERT", invertChk.isSelected)
        if (tsengAvail) GlobalEditor.setDouble("FILL_TSENG", tsengInput.value as Double)
        GlobalEditor.setString(preselectKey, gradientCombo.selectedItem as String)
    }

    private val configOpts: Unit
        get() {
            val ge = editor.globalEditor
            selectedFillMode = GlobalEditor.getString("FILL_MODE", "LINEAR")
            sliderStart.value = GlobalEditor.getDouble("FILL_START", 0.0)
            sliderEnd.value = GlobalEditor.getDouble("FILL_FINISH", 1.0)
            horizInput.value = GlobalEditor.getDouble("FILL_HORIZ", 0.5)
            vertInput.value = GlobalEditor.getDouble("FILL_VERT", 0.5)
            angleInput.value = GlobalEditor.getInt("FILL_ANGLE", 0)
            repeatInput.value = GlobalEditor.getInt("FILL_REPEATS", 0)
            xsInput.value = GlobalEditor.getDouble("FILL_XWEIGHT", 0.8)
            ysInput.value = GlobalEditor.getDouble("FILL_YWEIGHT", 1.4)
            diffInput.value = GlobalEditor.getDouble("FILL_DIFFUSE", 0.0)
            tsengInput.value = GlobalEditor.getDouble("FILL_TSENG", 0.1)
            invertChk.isSelected = GlobalEditor.getBoolean("FILL_INVERT", false)
        }

    private var selectedFillMode: String
        get() {
            if (btnLinear.isSelected) return "LINEAR"
            if (btnBox.isSelected) return "BOX"
            if (btnRadial.isSelected) return "RADIAL"
            if (btnConic.isSelected) return "CONICAL"
            if (btnSlime.isSelected) return "SLIME"
            throw UnsupportedOperationException()
        }
        private set(fillMode) {
            when (fillMode) {
                "LINEAR" -> btnLinear.isSelected = true
                "BOX" -> btnBox.isSelected = true
                "RADIAL" -> btnRadial.isSelected = true
                "CONICAL" -> btnConic.isSelected = true
                "SLIME" -> btnSlime.isSelected = true
                else -> btnLinear.isSelected = true
            }
        }

    private fun genList(filled: Array<ByteArray>) {
        val width = filled[0].size
        val height = filled.size
        var count = 0
        for (bytes in filled) {
            for (b in bytes) {
                count += b.toInt()
            }
        }
        xs = IntArray(count)
        ys = IntArray(count)
        tiles = arrayOfNulls(count)
        var i = 0
        maxX = Int.MIN_VALUE
        maxY = Int.MIN_VALUE
        minX = Int.MAX_VALUE
        minY = Int.MAX_VALUE
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (filled[y][x].toInt() == 1) {
                    minX = min(minX.toDouble(), x.toDouble()).toInt()
                    minY = min(minY.toDouble(), y.toDouble()).toInt()
                    maxX = max(maxX.toDouble(), x.toDouble()).toInt()
                    maxY = max(maxY.toDouble(), y.toDouble()).toInt()
                    xs[i] = x
                    ys[i] = y
                    i++
                }
            }
        }
    }

    private fun updateContents() {
        updateFillMode()
        updateAccess()
        updateFill()
        updateListener("updateFill")
    }

    private fun updateListener(command: String) {
        val e = ActionEvent(this, ActionEvent.ACTION_PERFORMED, command)
        listener.actionPerformed(e)
    }

    private fun updateFillMode() {
        if (btnLinear.isSelected) {
            fillMode = LINEAR
        } else if (btnBox.isSelected) {
            fillMode = BOX
        } else if (btnRadial.isSelected) {
            fillMode = RADIAL
        } else if (btnConic.isSelected) {
            fillMode = CONIC
        } else if (btnSlime.isSelected) {
            fillMode = SLIME
        }
    }

    private fun calcTseng() {
        tseng = 0.0
        tsengMatr = null
        if (tsengAvail) {
            tseng = tsengInput.value as Double
            tsengMatr = arrayOf(
                doubleArrayOf(0.4, 0.0, 0.1, 0.0, 0.3, 0.0, 0.1, 0.0),
                doubleArrayOf(0.0, 0.9, 0.0, 0.5, 0.0, 0.9, 0.0, 0.6),
                doubleArrayOf(0.1, 0.0, 0.2, 0.0, 0.1, 0.0, 0.2, 0.0),
                doubleArrayOf(0.0, 0.8, 0.0, 1.0, 0.0, 0.7, 0.0, 0.9),
                doubleArrayOf(0.3, 0.0, 0.1, 0.0, 0.4, 0.0, 0.1, 0.0),
                doubleArrayOf(0.0, 0.9, 0.0, 0.6, 0.0, 0.9, 0.0, 0.5),
                doubleArrayOf(0.1, 0.0, 0.2, 0.0, 0.1, 0.0, 0.2, 0.0),
                doubleArrayOf(0.0, 0.7, 0.0, 0.9, 0.0, 0.8, 0.0, 1.0)
            )
        }
    }

    private fun updateFill() {
        updateGradientTiles()

        calcTseng()

        if (fillMode == SLIME) {
            // Special handling for slime fill
            slimeFill()
            return
        }
        if (fillMode == LINEAR) {
            // Precompute min/max for this angle
            precomputeLinear()
        }

        for (i in xs.indices) {
            val x = xs[i]
            val y = ys[i]
            val fx = doublePos(x, minX, maxX)
            val fy = doublePos(y, minY, maxY)

            val activationValue = translateXY(i, fx, fy)
            val tsenged = false

            fillTile(i, activationValue, x, y)
        }
    }

    private fun precomputeLinear() {
        val intAng = angleInput.value as Int
        var min = Double.MAX_VALUE * 1.0
        var max = Double.MAX_VALUE * -1.0
        var t = linearTranslate(0.0, 0.0, intAng)
        min = min(min, t)
        max = max(max, t)
        t = linearTranslate(0.0, 1.0, intAng)
        min = min(min, t)
        max = max(max, t)
        t = linearTranslate(1.0, 0.0, intAng)
        min = min(min, t)
        max = max(max, t)
        t = linearTranslate(1.0, 1.0, intAng)
        min = min(min, t)
        max = max(max, t)
        linearMin = min
        linearMax = max
    }

    private fun updateGradientTiles() {
        val encodedBuffer = gradientCombo.selectedItem as String
        gradientTiles = decode(encodedBuffer).tiles
    }

    private fun translateXY(i: Int, fx: Double, fy: Double): Double {
        // Translate x and y into an activation value
        val horizontal = horizInput.value as Double
        val vertical = vertInput.value as Double
        val intAng = angleInput.value as Int
        val normAng = 1.0 * intAng / 360.0
        when (fillMode) {
            BOX -> {
                val x = 1.0 - abs(fx - horizontal) * 2.0
                val y = 1.0 - abs(fy - vertical) * 2.0
                val v = min(x, y)
                return v
            }

            RADIAL -> {
                val x = fx - horizontal
                val y = fy - vertical
                val v = 1.0 - sqrt(x * x + y * y)
                return v
            }

            CONIC -> {
                val x = fx - horizontal
                val y = fy - vertical

                val v = (((atan2(y, x) + Math.PI) / (Math.PI * 2.0)) + normAng) % 1.0
                return v
            }

            LINEAR -> {
                var v = linearTranslate(fx, fy, intAng)
                val diff = linearMax - linearMin
                v = if (diff < 0.000001) {
                    0.5
                } else {
                    (v - linearMin) / diff
                }

                return v
            }
        }
        throw RuntimeException("Unsupported fill mode")
    }

    private fun linearTranslate(fx: Double, fy: Double, angAdd: Int): Double {
        when (angAdd) {
            0, 360 -> return fx
            90 -> return 1.0 - fy
            180 -> return 1.0 - fx
            270 -> return fy
            else -> {
                val linearAng = (Math.PI * angAdd / 180.0)
                val dx = fx - 0.5
                val dy = fy - 0.5
                val mag = sqrt(dx * dx + dy * dy)
                var ang = atan2(dy, dx)
                ang += linearAng
                return cos(ang) * mag + 0.5
            }
        }
    }

    private fun fillTile(i: Int, value: Double, x: Int, y: Int) {
        // For now, compress the value into the gradient range and pick something
        var value = value
        value = Util.clamp(value, 0.0, 1.0)
        val diffusion = diffInput.value as Double
        value += rng.nextGaussian() * diffusion

        // 0 repeats: 0.0 -> 1.0
        // 1 repeats: 0.0 -> 1.0 -> 0.0
        // 2 repeats: 0.0 -> 1.0 -> 0.0 -> 1.0
        value *= (repeatInput.value as Int + 1).toDouble()
        value = 1.0 - abs((value % 2.0) - 1.0)

        val min = sliderStart.value as Double
        val max = sliderEnd.value as Double
        val rmin = min(min, max)
        val rmax = max(min, max)
        val rdiff = rmax - rmin
        value = value * rdiff + rmin
        value = Util.clamp(value, 0.0, 1.0)

        var tsenged = false
        if (tsengMatr != null) {
            val r = rng.nextDouble() * 0.1 + tseng
            if (tsengMatr!![x % 8][y % 8] > 1.1 - r) {
                tsenged = true
            }
        }

        if (invertChk.isSelected xor tsenged) value = 1.0 - value

        var idx = (value * gradientTiles.size).toInt()
        if (idx == gradientTiles.size) idx--
        tiles[i] = gradientTiles[idx]
    }

    private fun doublePos(value: Int, min: Int, max: Int): Double {
        if (value == min && value == max) return 0.5
        return 1.0 * (value - min) / (max - min)
    }

    private fun slimeFill() {
        val width = filled[0].size
        val height = filled.size
        val slimeArea = Array(height) { FloatArray(width) }
        for (slimeRow in slimeArea) {
            Arrays.fill(slimeRow, Float.MAX_VALUE)
        }
        val queue = ArrayDeque<ArrayList<Int>>()
        // Initialise all edges at 0.0f
        for (i in xs.indices) {
            val x = xs[i]
            val y = ys[i]
            if (isEdge(x, y, width, height)) {
                slimeArea[y][x] = 0.0f
                queue.add(Util.pair(x, y))
            }
        }

        val xScale = (xsInput.value as Double).toFloat()
        val yScale = (ysInput.value as Double).toFloat()
        var max = 0.0f

        while (!queue.isEmpty()) {
            val pos = queue.pop()
            val x = pos[0]
            val y = pos[1]
            val value = slimeArea[y][x]
            max = max(max.toDouble(), slimeExpand(x + 1, y, width, height, value + xScale, queue, slimeArea).toDouble())
                .toFloat()
            max = max(max.toDouble(), slimeExpand(x - 1, y, width, height, value + xScale, queue, slimeArea).toDouble())
                .toFloat()
            max = max(max.toDouble(), slimeExpand(x, y + 1, width, height, value + yScale, queue, slimeArea).toDouble())
                .toFloat()
            max = max(max.toDouble(), slimeExpand(x, y - 1, width, height, value + yScale, queue, slimeArea).toDouble())
                .toFloat()
        }
        for (i in xs.indices) {
            val x = xs[i]
            val y = ys[i]
            var v = slimeArea[y][x]
            v = if (max >= 0.000001) {
                v / max
            } else {
                0.0f
            }
            fillTile(i, v.toDouble(), x, y)
        }
    }

    private fun slimeExpand(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        v: Float,
        queue: ArrayDeque<ArrayList<Int>>,
        slimeArea: Array<FloatArray>
    ): Float {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0.0f
        if (filled[y][x].toInt() == 0) return 0.0f
        if (v < slimeArea[y][x]) {
            slimeArea[y][x] = v
            queue.add(Util.pair(x, y))
            return v
        }
        return 0.0f
    }

    private fun isEdge(x: Int, y: Int, width: Int, height: Int): Boolean {
//        if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
//            return true;
//        }
        if (y < height - 1 && filled[y + 1][x].toInt() == 0) return true
        if (y > 0 && filled[y - 1][x].toInt() == 0) return true
        if (x > 0 && filled[y][x - 1].toInt() == 0) return true
        if (x < width - 1 && filled[y][x + 1].toInt() == 0) return true
        return false
    }


    private fun updateAccess() {
        when (fillMode) {
            LINEAR -> {
                setState(horizLabel, horizInput, false)
                setState(vertLabel, vertInput, false)
                setState(angleLabel, angleInput, true)
                setState(xsLabel, xsInput, false)
                setState(ysLabel, ysInput, false)
            }

            BOX, RADIAL -> {
                setState(horizLabel, horizInput, true)
                setState(vertLabel, vertInput, true)
                setState(angleLabel, angleInput, false)
                setState(xsLabel, xsInput, false)
                setState(ysLabel, ysInput, false)
            }

            CONIC -> {
                setState(horizLabel, horizInput, true)
                setState(vertLabel, vertInput, true)
                setState(angleLabel, angleInput, true)
                setState(xsLabel, xsInput, false)
                setState(ysLabel, ysInput, false)
            }

            SLIME -> {
                setState(horizLabel, horizInput, false)
                setState(vertLabel, vertInput, false)
                setState(angleLabel, angleInput, false)
                setState(xsLabel, xsInput, true)
                setState(ysLabel, ysInput, true)
            }
        }
    }

    private fun setState(lbl: JLabel, spin: JSpinner, state: Boolean) {
        lbl.isEnabled = state
        spin.isEnabled = state
    }

    companion object {
        private const val LINEAR = 0
        private const val BOX = 1
        private const val RADIAL = 2
        private const val CONIC = 3
        private const val SLIME = 4
    }
}
