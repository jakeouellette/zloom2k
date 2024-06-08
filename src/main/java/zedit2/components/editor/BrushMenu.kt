package zedit2.components.editor

import net.miginfocom.swing.MigLayout
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JPanel

class BrushMenuPanel(
    shouldShowViewCode: Boolean,
    onEditBrushSelected: (ActionEvent) -> Unit,
    onEditColorSelected: (ActionEvent) -> Unit,
    onColorSwapSelected: (ActionEvent) -> Unit,
    onCodeSaved: (ActionEvent) -> Unit
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

}