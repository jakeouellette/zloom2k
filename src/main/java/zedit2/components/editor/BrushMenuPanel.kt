package zedit2.components.editor

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.DefaultParserProvider
import com.github.weisj.jsvg.parser.DomProcessor
import com.github.weisj.jsvg.parser.SVGLoader
import net.miginfocom.swing.MigLayout
import zedit2.components.Main
import zedit2.components.WorldEditor.ToolType
import zedit2.model.SelectionModeConfiguration
import zedit2.model.Tile
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.image.CustomColorsProcessor
import zedit2.util.image.DynamicAWTSvgPaint
import zedit2.util.image.SVGPanel
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
    val initialSelectionMode: SelectionModeConfiguration?,
    val initialToolType : ToolType
) : JPanel() {


    private var brushButton: SvgJButton
    private var blockButton: SvgJButton
    private var brushStrokeColor : DynamicAWTSvgPaint
    private var brushFillColor : DynamicAWTSvgPaint


    init {
        val loader = SVGLoader()

        val textColor = UIManager.getLookAndFeelDefaults().get("Panel.foreground") as Color

        val textColorProcessor = CustomColorsProcessor(listOf("colorable"))
        val swapColorProcessor = CustomColorsProcessor(listOf("colorable1", "colorable2"))
        val processor = CustomColorsProcessor(listOf("colorable"))
        val brushSvg: SVGDocument = loader.loadColorable("icons/brush.svg", textColorProcessor)!!
        val selectorSvg: SVGDocument = loader.loadColorable("icons/selector.svg", textColorProcessor)!!
        val swapSvg: SVGDocument = loader.loadColorable("icons/swap.svg", swapColorProcessor)!!
        val dropletSvg: SVGDocument = loader.loadColorable("icons/droplet.svg", processor)!!

        textColorProcessor.customColorStrokeForId("colorable")!!.color = textColor
        textColorProcessor.customColorFillForId("colorable")!!.color = Color(0,0,0,0)
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
                val popupMenu = createPopupSelectorPicker()
                configureSelectionButton.addActionListener(object : ActionListener {
                    override fun actionPerformed(e: ActionEvent?) {
                        Logger.i(TAG) { "New brush menu"}
                        popupMenu.show(configureSelectionButton, configureSelectionButton.x, configureSelectionButton.y)
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
                this.add(brushButton, "align right")
//                brushButton.maximumSize = Dimension(brushSize.width.toInt(), brushSize.height.toInt())
//                brushButton.preferredSize = Dimension(brushSize.width.toInt(), brushSize.height.toInt())
                val editButton = DownArrowJButton()
                editButton.addActionListener({e -> onEditBrushSelected(e, this@BrushMenuPanel)})
                this.add(editButton, "gap right 10")
                val swapColorsButton = SvgJButton(swapSvg)
//                swapColorsButton.maximumSize = Dimension(brushSize.width.toInt(), brushSize.height.toInt())
//                swapColorsButton.preferredSize = Dimension(brushSize.width.toInt(), brushSize.height.toInt())

                swapColorsButton.addActionListener({e -> onColorSwapSelected(e, this@BrushMenuPanel)})

                val selectColorButton = SvgJButton(dropletSvg)
                selectColorButton.addActionListener({e -> onEditColorSelected(e, this@BrushMenuPanel)})
                this.add(selectColorButton, "gap right 10")
                this.add(swapColorsButton, "align right")


//                if (shouldShowViewCode) {
//                    val editCodeButton = SvgJButton(codeSvg)
//                    editCodeButton.addActionListener({e -> onCodeSaved(e, this@BrushMenuPanel)})
//                    this.add(editCodeButton, "align right")
//                }
                onBrushUpdated(initialToolType, initialSelectionMode)
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

    fun createPopupSelectorPicker() : JPopupMenu {
        val popupMenu = JPopupMenu("Choose block command")
        popupMenu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {

            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {

            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {

            }

        } )
        val menuItems = SelectionModeConfiguration.entries.filter {it.inUi}.map { it.description }
        val listener = ActionListener { e: ActionEvent ->
            val menuItem = (e.source as JRadioButtonMenuItem)
            Logger.i(TAG) { "$menuItem ${menuItem.isSelected} ${menuItem.text}"}
            val text = menuItem.text

            var selected = SelectionModeConfiguration.entries.find {
                it.description == text
            }
            if (selected != null) {
                onSelectionModeConfigured(selected, this@BrushMenuPanel)
            }
        }
        val group = ButtonGroup()
        for (item in menuItems) {
            val menuItem = JRadioButtonMenuItem(item)
            menuItem.isSelected = initialSelectionMode != null && initialSelectionMode.description == item
            menuItem.isEnabled = true
            group.add(menuItem)
            menuItem.addActionListener(listener)
            popupMenu.add(menuItem)
        }
        return popupMenu
    }

    fun onBrushUpdated(mode : ToolType, config: SelectionModeConfiguration?) {
        when (mode) {
            ToolType.SELECTION_TOOL -> {
                this.brushButton.picked = false
                this.blockButton.picked = true
                this.brushButton.toolTipText = BRUSH_TEXT
                this.blockButton.toolTipText = "$BLOCK_SELECT_TEXT (${config?.short ?: "-"})"
                this.blockButton.repaint()
                this.brushButton.repaint()
            }
            ToolType.DRAWING,
            ToolType.EDITING -> {
                this.brushButton.picked = true
                this.blockButton.picked = false
                this.brushButton.toolTipText = BRUSH_TEXT
                this.blockButton.toolTipText = BLOCK_SELECT_TEXT
                this.brushButton.repaint()
                this.blockButton.repaint()
            }

            ToolType.TEXT_ENTRY -> {
                TODO()
            }
        }
    }

    companion object {
        const val BLOCK_SELECT_TEXT = "Select"
        const val BRUSH_TEXT = "Brush"
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