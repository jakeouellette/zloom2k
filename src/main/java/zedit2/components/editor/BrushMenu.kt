package zedit2.components.editor

import net.miginfocom.swing.MigLayout
import zedit2.components.editor.world.operationBlockEnd
import zedit2.model.SelectionModeConfiguration
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

class BrushMenuPanel(
    shouldShowViewCode: Boolean,
    onSelectionModeSelected: (ActionEvent) -> Unit,
    val onSelectionModeConfigured: (SelectionModeConfiguration) -> Unit,
    onEditModeSelected: () -> Unit,
    onEditBrushSelected: (ActionEvent) -> Unit,
    onEditColorSelected: (ActionEvent) -> Unit,
    onColorSwapSelected: (ActionEvent) -> Unit,
    onCodeSaved: (ActionEvent) -> Unit,
    val initialSelectionMode: SelectionModeConfiguration?
) : JPanel() {
    init {
        val currentBrush = JPanel()

        val brushSelector = JPanel()
        val brushPicker = JPanel()

        val colorOne = JPanel()
        val colorTwo = JPanel()

        val editorButton = JPanel()

        val container = object : JPanel() {
            init {
                this.layout = MigLayout("al center center, wrap")

                val blockButton = JButton("Block Select")
                blockButton.addActionListener(onSelectionModeSelected)
                this.add(blockButton)
                val configureSelectionButton = JButton("Configure Block Select")
                configureSelectionButton.addActionListener(object : ActionListener {
                    override fun actionPerformed(e: ActionEvent?) {
                        createPopupSelectorPicker(configureSelectionButton)
                    }
                })
                this.add(configureSelectionButton)

                val brushButton = JButton("Brush")
                brushButton.addActionListener(object : ActionListener {
                    override fun actionPerformed(e: ActionEvent?) {
                        onEditModeSelected()
                    }
                })
                this.add(brushButton)
                val editButton = JButton("Edit Brush")
                editButton.addActionListener(onEditBrushSelected)
                this.add(editButton)
                val swapColorsButton = JButton("Swap Colors")
                swapColorsButton.addActionListener(onColorSwapSelected)
                this.add(swapColorsButton)
                val selectColorButton = JButton("Select Color")
                selectColorButton.addActionListener(onEditColorSelected)
                this.add(selectColorButton)

                if (shouldShowViewCode) {
                    val editCodeButton = JButton("View Code")
                    editCodeButton.addActionListener(onCodeSaved)
                    this.add(editCodeButton)
                }
            }
        }
        this.add(container)
    }


    fun createPopupSelectorPicker( button : JComponent) {
        val popupMenu = JPopupMenu("Choose block command")
        popupMenu.addPopupMenuListener(object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {

            }

            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {

            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {

            }

        } )
        val menuItems = SelectionModeConfiguration.entries.map { it.description }
        val listener = ActionListener { e: ActionEvent ->
            val menuItem = (e.source as JMenuItem).text
            var selected = SelectionModeConfiguration.entries.find {
                it.description == menuItem
            }
            if (selected != null) {
                onSelectionModeConfigured(selected)
            }
        }

        for (item in menuItems) {
            val menuItem = JMenuItem(item)
            menuItem.addActionListener(listener)
            popupMenu.add(menuItem)
        }
        popupMenu.show(
            button, button.x, button.y)
//                (frame.width - popupMenu.preferredSize.width) / 2,
//            (frame.height - popupMenu.preferredSize.height) / 2


        // From https://stackoverflow.com/a/7754567
//        SwingUtilities.invokeLater {
//            popupMenu.dispatchEvent(
//                KeyEvent(popupMenu, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DOWN, '\u0000')
//            )
//        }
    }

}