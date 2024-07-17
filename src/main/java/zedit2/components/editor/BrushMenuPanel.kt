package zedit2.components.editor

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.DefaultParserProvider
import com.github.weisj.jsvg.parser.DomProcessor
import com.github.weisj.jsvg.parser.SVGLoader
import net.miginfocom.swing.MigLayout
import zedit2.components.Main
import zedit2.components.WorldEditor.ToolType
import zedit2.model.BrushModeConfiguration
import zedit2.model.FloodFillConfiguration
import zedit2.model.SelectionModeConfiguration
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.image.CustomColorsProcessor
import zedit2.util.image.DynamicAWTSvgPaint
import zedit2.util.image.SvgJButton
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener


class BrushMenuPanel(
    bgColor: Color,
    fgColor : Color,
    onSelectionModeSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    val onSelectionModeConfigured: (SelectionModeConfiguration, BrushMenuPanel) -> Unit,
    onEditModeSelected: (BrushMenuPanel) -> Unit,
    onEditBrushSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    onEditColorSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    onColorSwapSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    onFloodFillSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    onTextCaretSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    val onFloodFillConfigured: (FloodFillConfiguration, BrushMenuPanel) -> Unit,
    val onBrushConfigured: (BrushModeConfiguration, BrushMenuPanel) -> Unit,
    val initialSelectionMode: SelectionModeConfiguration?,
    val initialBrushMode : BrushModeConfiguration?,
    val initialPaintBucketMode : FloodFillConfiguration?,
    val initialToolType : ToolType
) : JPanel() {


    private var brushButton: SvgJButton
    private var blockButton: SvgJButton
    private var paintBucketButton: SvgJButton
    private var textCaretButton: SvgJButton
    private var brushStrokeColor : DynamicAWTSvgPaint
    private var brushFillColor : DynamicAWTSvgPaint


    init {
        val loader = SVGLoader()

        val textColor = UIManager.getLookAndFeelDefaults().get("Panel.foreground") as Color

        val iconColorProcessor = CustomColorsProcessor(listOf("colorable"))
        val swapColorProcessor = CustomColorsProcessor(listOf("colorable1", "colorable2"))
        val processor = CustomColorsProcessor(listOf("colorable"))
        val brushSvg: SVGDocument = loader.loadColorable("icons/brush.svg", iconColorProcessor)!!
        val selectorSvg: SVGDocument = loader.loadColorable("icons/selector.svg", iconColorProcessor)!!
        val paintbucketSvg: SVGDocument = loader.loadColorable("icons/paint-bucket.svg", iconColorProcessor)!!
        val swapSvg: SVGDocument = loader.loadColorable("icons/swap.svg", swapColorProcessor)!!
        val dropletSvg: SVGDocument = loader.loadColorable("icons/droplet.svg", processor)!!
        val caretSvg: SVGDocument = loader.loadColorable("icons/text-caret.svg", iconColorProcessor)!!
        val cubeSvg: SVGDocument = loader.loadColorable("icons/cube.svg", iconColorProcessor)!!


        iconColorProcessor.customColorStrokeForId("colorable")!!.color = textColor
        iconColorProcessor.customColorFillForId("colorable")!!.color = Color(0,0,0,0)
        swapColorProcessor.customColorStrokeForId("colorable1")!!.color = bgColor
        swapColorProcessor.customColorStrokeForId("colorable2")!!.color = fgColor
        swapColorProcessor.customColorFillForId("colorable1")!!.color= Color(0,0,0,0)
        swapColorProcessor.customColorFillForId("colorable2")!!.color = Color(0,0,0,0)

        brushFillColor = processor.customColorFillForId("colorable")!!
        brushStrokeColor = processor.customColorStrokeForId("colorable")!!
        brushStrokeColor.color = bgColor
        brushFillColor.color = fgColor
        val container = object : JPanel() {
            init {
                this.layout = MigLayout("ins 0", "0[]", "")

                blockButton = SvgJButton(selectorSvg)
                blockButton.addActionListener { e -> onSelectionModeSelected(e, this@BrushMenuPanel) }
                this.add(blockButton, "align right")
                val configureSelectionButton = DownArrowJButton()
                val popupMenu = createPopupSelectorPicker(
                    initialSelectionMode?.description,
                    SelectionModeConfiguration.entries.filter {it.inUi}.map { Pair(it.short, it.description)}
                ) { nameAndDescription ->
                    val selectedMode =
                        SelectionModeConfiguration.entries.find({ it.description == nameAndDescription.second })!!
                    onSelectionModeConfigured(selectedMode, this@BrushMenuPanel)
                }
                configureSelectionButton.addActionListener(object : ActionListener {
                    override fun actionPerformed(e: ActionEvent?) {
                        Logger.i(TAG) { "New brush menu, ${configureSelectionButton.x}, ${configureSelectionButton.y}"}
                        popupMenu.show(configureSelectionButton, configureSelectionButton.alignmentX.toInt(), configureSelectionButton.alignmentY.toInt())
                    }
                })
                this.add(configureSelectionButton, "gap right 10")
                val brushSize = brushSvg.size()
                brushButton = SvgJButton(brushSvg)
                brushButton.addActionListener(object : ActionListener {
                    override fun actionPerformed(e: ActionEvent?) {
                        onEditModeSelected(this@BrushMenuPanel)
                    }
                })
                this.add(brushButton, "gap right 10")
//                brushButton.maximumSize = Dimension(brushSize.width.toInt(), brushSize.height.toInt())
//                brushButton.preferredSize = Dimension(brushSize.width.toInt(), brushSize.height.toInt())

                val brushPopupMenu = createPopupSelectorPicker(
                    initialBrushMode?.description,
                    BrushModeConfiguration.entries.filter {it.inUi}.map { Pair(it.short, it.description)}
                ) { nameAndDescription ->
                    val selectedMode =
                        BrushModeConfiguration.entries.find({ it.description == nameAndDescription.second })!!
                    onBrushConfigured(selectedMode, this@BrushMenuPanel)
                }
                val editButton = DownArrowJButton()
                editButton.addActionListener({e ->
                    brushPopupMenu.show(editButton, editButton.alignmentX.toInt(), editButton.alignmentY.toInt())
                })
//                this.add(editButton, "gap right 10")
                paintBucketButton = SvgJButton(paintbucketSvg)
                paintBucketButton.addActionListener { e -> onFloodFillSelected(e, this@BrushMenuPanel) }
                this.add(paintBucketButton, "align right")
                val paintBucketConfigureSelectionButton = DownArrowJButton()
                val paintBucketPopupMenu = createPopupSelectorPicker(
                    initialPaintBucketMode?.description,
                    FloodFillConfiguration.entries.filter {it.inUi}.map { Pair(it.short, it.description)}
                ) { nameAndDescription ->
                    val fillMode =
                        FloodFillConfiguration.entries.find { it.description == nameAndDescription.second }!!
                    onFloodFillConfigured(fillMode, this@BrushMenuPanel)
                }
                paintBucketConfigureSelectionButton.addActionListener(object : ActionListener {
                    override fun actionPerformed(e: ActionEvent?) {
                        Logger.i(TAG) { "New paint bucket menu ${paintBucketConfigureSelectionButton.x}, ${paintBucketConfigureSelectionButton.y}"}
                        paintBucketPopupMenu.show(paintBucketConfigureSelectionButton, paintBucketConfigureSelectionButton.alignmentX.toInt(), paintBucketConfigureSelectionButton.alignmentY.toInt())
                    }
                })
                this.add(paintBucketConfigureSelectionButton, "gap right 10")

                textCaretButton = SvgJButton(caretSvg)
                textCaretButton.addActionListener { e -> onTextCaretSelected(e, this@BrushMenuPanel) }
                this.add(textCaretButton, "gap right 50")

                val swapColorsButton = SvgJButton(swapSvg)
//                swapColorsButton.maximumSize = Dimension(brushSize.width.toInt(), brushSize.height.toInt())
//                swapColorsButton.preferredSize = Dimension(brushSize.width.toInt(), brushSize.height.toInt())

                swapColorsButton.addActionListener { e -> onColorSwapSelected(e, this@BrushMenuPanel) }

                val selectColorButton = SvgJButton(dropletSvg)
                selectColorButton.addActionListener { e -> onEditColorSelected(e, this@BrushMenuPanel) }
                this.add(selectColorButton, "gap right 10")
                this.add(swapColorsButton, "gap right 10")
                val brushSelector = SvgJButton(cubeSvg)
                brushSelector.addActionListener({e -> onEditBrushSelected(e, this@BrushMenuPanel)})
                this.add(brushSelector, "gap right 10")

//                if (shouldShowViewCode) {
//                    val editCodeButton = SvgJButton(codeSvg)
//                    editCodeButton.addActionListener({e -> onCodeSaved(e, this@BrushMenuPanel)})
//                    this.add(editCodeButton, "align right")
//                }


                onBrushUpdated(initialToolType, initialSelectionMode, initialPaintBucketMode)
            }
        }
        this.add(container)
    }

    private fun SVGLoader.load(s: String): SVGDocument? {
        return this.load(Main::class.java.classLoader.getResource(s))
    }

    private fun SVGLoader.loadColorable(s: String, processor: CustomColorsProcessor) : SVGDocument? {
        return this.load(Main::class.java.classLoader.getResource(s),
    object : DefaultParserProvider() {
            override fun createPreProcessor(): DomProcessor {
                return processor
            }
        })
    }

    fun createPopupSelectorPicker(
        initialSelected : String?,
        namesAndDescriptions: List<Pair<String, String>>,
        onSelected: (Pair<String, String>) -> Unit ): JPopupMenu {
        val popupMenu = JPopupMenu("Choose block command")
        popupMenu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {

            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {

            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {

            }

        } )
        val menuItems = namesAndDescriptions.map { it.second }
        val listener = ActionListener { e: ActionEvent ->
            val menuItem = (e.source as JRadioButtonMenuItem)
            Logger.i(TAG) { "$menuItem ${menuItem.isSelected} ${menuItem.text}"}
            val text = menuItem.text

            var selected = namesAndDescriptions.find {
                it.second == text
            }
            if (selected != null) {
                onSelected(selected)
            }
        }
        val group = ButtonGroup()
        for (item in menuItems) {
            val menuItem = JRadioButtonMenuItem(item)
            menuItem.isSelected = initialSelected != null && initialSelected == item
            menuItem.isEnabled = true
            group.add(menuItem)
            menuItem.addActionListener(listener)
            popupMenu.add(menuItem)
        }
        return popupMenu
    }

    fun onBrushUpdated(mode : ToolType, config: SelectionModeConfiguration?, paintBucketConfig : FloodFillConfiguration?) {
        this.brushButton.picked = false
        this.blockButton.picked = false
        this.paintBucketButton.picked = false
        this.textCaretButton.picked = false

        when (mode) {
            ToolType.SELECTION_TOOL -> {
                this.blockButton.picked = true
            }
            ToolType.DRAWING,
            ToolType.EDITING -> {
                this.brushButton.picked = true
            }
            ToolType.TEXT_ENTRY -> {
                this.textCaretButton.picked = true
            }
            ToolType.PAINT_BUCKET -> {
                this.paintBucketButton.picked = true
            }
        }
        this.brushButton.toolTipText = BRUSH_TEXT
        this.blockButton.toolTipText = "$BLOCK_SELECT_TEXT (${config?.short ?: "-"})"
        this.paintBucketButton.toolTipText = "$PAINT_BUCKET_TEXT (${paintBucketConfig?.short ?: "-"}"
        this.textCaretButton.toolTipText = "Enter Text"
        this.blockButton.repaint()
        this.brushButton.repaint()
        this.paintBucketButton.repaint()
        this.textCaretButton.repaint()
    }

    companion object {
        const val BLOCK_SELECT_TEXT = "Select"
        const val BRUSH_TEXT = "Brush"
        const val PAINT_BUCKET_TEXT = "Paint Bucket"
    }

    class DownArrowJButton : JButton("▼") {
        init {
            this.isBorderPainted = false
            this.preferredSize = Dimension(18, this.preferredSize.height)
            this.maximumSize = Dimension(18, this.maximumSize.height)
            this.margin = Insets(0, 0, 0, 0)
            this.background = UIManager.getLookAndFeelDefaults().get("Panel.background") as Color
        }
        override fun paintComponent(g: Graphics?) {
            super.paintComponent(g)

        }
    }

}