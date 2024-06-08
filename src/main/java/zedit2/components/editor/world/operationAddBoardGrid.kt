package zedit2.components.editor.world

import zedit2.components.WorldEditor
import zedit2.util.CP437.font
import java.awt.BorderLayout
import java.awt.Dialog
import java.awt.GridLayout
import java.awt.event.ActionEvent
import javax.swing.*


internal fun WorldEditor.operationAddBoardGrid() {
    val dlg = JDialog()
    //Util.addEscClose(settings, settings.getRootPane());
    //Util.addKeyClose(settings, settings.getRootPane(), KeyEvent.VK_ENTER, 0);
    dlg.isResizable = false
    dlg.title = "Add Boards (*x* grid)"
    dlg.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dlg.modalityType = Dialog.ModalityType.APPLICATION_MODAL
    dlg.contentPane.layout = BorderLayout()
    val cp = JPanel(GridLayout(0, 1))
    cp.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    dlg.contentPane.add(cp, BorderLayout.CENTER)

    val widthSpinner = JSpinner(SpinnerNumberModel(1, 1, 100, 1))
    val heightSpinner = JSpinner(SpinnerNumberModel(1, 1, 100, 1))
    val nameField = JTextField("Board {x},{y}")
    nameField.font = font
    nameField.toolTipText =
        "Board name template. {x} and {y} are replaced with the grid location of the board (1-based)."
    val hidePlayerChk = JCheckBox("Erase player from boards", false)
    hidePlayerChk.toolTipText =
        "Erase the player from each board. This will place the player's stat in a corner of the board's border, keeping it off the board. You should place the player later if you want to actually use this board."
    val openAtlasChk = JCheckBox("Open board grid in Atlas view.", true)
    openAtlasChk.toolTipText = "After creating the board grid, load it in Atlas view."
    val createButton = JButton("Create boards")
    val cancelButton = JButton("Cancel")

    val widthPanel = JPanel(BorderLayout())
    val heightPanel = JPanel(BorderLayout())
    val namePanel = JPanel(BorderLayout())
    val btnsPanel = JPanel(BorderLayout())
    widthPanel.add(JLabel("Grid width: "), BorderLayout.WEST)
    heightPanel.add(JLabel("Grid height: "), BorderLayout.WEST)
    namePanel.add(JLabel("Name template: "), BorderLayout.WEST)
    widthPanel.add(widthSpinner, BorderLayout.EAST)
    heightPanel.add(heightSpinner, BorderLayout.EAST)
    namePanel.add(nameField, BorderLayout.EAST)
    btnsPanel.add(createButton, BorderLayout.WEST)
    btnsPanel.add(cancelButton, BorderLayout.EAST)
    cp.add(widthPanel)
    cp.add(heightPanel)
    cp.add(namePanel)
    cp.add(hidePlayerChk)
    cp.add(openAtlasChk)
    cp.add(btnsPanel)

    cancelButton.addActionListener { e: ActionEvent? -> dlg.dispose() }
    createButton.addActionListener { e: ActionEvent? ->
        val width = widthSpinner.value as Int
        val height = heightSpinner.value as Int
        val nameTemplate = nameField.text

        val startIdx = boards.size
        for (y in 0 until height) {
            for (x in 0 until width) {
                var name = nameTemplate.replace("{x}", (x + 1).toString())
                name = name.replace("{y}", (y + 1).toString())

                val newBoard = blankBoard(name)

                val currentIdx = y * width + x + startIdx

                // Create connections
                // North
                if (y > 0) newBoard.setExit(0, currentIdx - width)
                // South
                if (y < height - 1) newBoard.setExit(1, currentIdx + width)
                // West
                if (x > 0) newBoard.setExit(2, currentIdx - 1)
                // East
                if (x < width - 1) newBoard.setExit(3, currentIdx + 1)

                if (hidePlayerChk.isSelected) {
                    erasePlayer(newBoard)
                }

                boards.add(newBoard)
            }
        }

        if (openAtlasChk.isSelected) {
            changeBoard(startIdx)
            atlas()
        }
        dlg.dispose()
    }
    onBoardsUpdated(boards)
    dlg.pack()
    dlg.setLocationRelativeTo(frame)
    dlg.isVisible = true
}