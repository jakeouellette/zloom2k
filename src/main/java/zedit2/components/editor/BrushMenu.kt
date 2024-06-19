package zedit2.components.editor

import net.miginfocom.swing.MigLayout
import zedit2.components.WorldEditor.ToolType
import zedit2.model.SelectionModeConfiguration
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener


class BrushMenuPanel(
    shouldShowViewCode: Boolean,
    onSelectionModeSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    val onSelectionModeConfigured: (SelectionModeConfiguration, BrushMenuPanel) -> Unit,
    onEditModeSelected: (BrushMenuPanel) -> Unit,
    onEditBrushSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    onEditColorSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    onColorSwapSelected: (ActionEvent, BrushMenuPanel) -> Unit,
    onCodeSaved: (ActionEvent, BrushMenuPanel) -> Unit,
    val initialSelectionMode: SelectionModeConfiguration?,
    val initialToolType : ToolType
) : JPanel() {


    private var brushButton: JButton
    private var blockButton: JButton

    init {

        val currentBrush = JPanel()

        val brushSelector = JPanel()
        val brushPicker = JPanel()

        val colorOne = JPanel()
        val colorTwo = JPanel()

        val editorButton = JPanel()

        val container = object : JPanel() {
            init {
                this.layout = MigLayout("")

                blockButton = JButton(BLOCK_SELECT_TEXT)
                blockButton.addActionListener { e -> onSelectionModeSelected(e, this@BrushMenuPanel) }
                this.add(blockButton, "align right")
                val configureSelectionButton = JButton("▼")
                val popupMenu = createPopupSelectorPicker()
                configureSelectionButton.addActionListener(object : ActionListener {
                    override fun actionPerformed(e: ActionEvent?) {
                        Logger.i(TAG) { "New brush menu"}
                        popupMenu.show(configureSelectionButton, configureSelectionButton.x, configureSelectionButton.y)
                    }
                })
                this.add(configureSelectionButton, "wrap")

                brushButton = JButton(BRUSH_TEXT)
                brushButton.addActionListener(object : ActionListener {
                    override fun actionPerformed(e: ActionEvent?) {
                        onEditModeSelected(this@BrushMenuPanel)
                    }
                })
                this.add(brushButton, "align right")
                val editButton = JButton("▼")
                editButton.addActionListener({e -> onEditBrushSelected(e, this@BrushMenuPanel)})
                this.add(editButton, "wrap")
                val swapColorsButton = JButton("Swap Colors")
                swapColorsButton.addActionListener({e -> onColorSwapSelected(e, this@BrushMenuPanel)})
                this.add(swapColorsButton, "align right")
                val selectColorButton = JButton("▼")
                selectColorButton.addActionListener({e -> onEditColorSelected(e, this@BrushMenuPanel)})
                this.add(selectColorButton, "wrap")

                if (shouldShowViewCode) {
                    val editCodeButton = JButton("View Code")
                    editCodeButton.addActionListener({e -> onCodeSaved(e, this@BrushMenuPanel)})
                    this.add(editCodeButton, "align right")
                }
                onBrushUpdated(initialToolType, initialSelectionMode)
            }
        }
        this.add(container)
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

//                (frame.width - popupMenu.preferredSize.width) / 2,
//            (frame.height - popupMenu.preferredSize.height) / 2


        // From https://stackoverflow.com/a/7754567
//        SwingUtilities.invokeLater {
//            popupMenu.dispatchEvent(
//                KeyEvent(popupMenu, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DOWN, '\u0000')
//            )
//        }
    }

    fun onBrushUpdated(mode : ToolType, config: SelectionModeConfiguration?) {
        when (mode) {
            ToolType.SELECTION_TOOL -> {
                this.brushButton.text = BRUSH_TEXT
                this.blockButton.text = "* $BLOCK_SELECT_TEXT (${config?.short ?: "-"})"
            }
            ToolType.DRAWING,
            ToolType.EDITING -> {
                this.brushButton.text = "* $BRUSH_TEXT"
                this.blockButton.text = BLOCK_SELECT_TEXT
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

}