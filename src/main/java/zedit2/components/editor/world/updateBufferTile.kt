package zedit2.components.editor.world

import net.miginfocom.swing.MigLayout
import zedit2.components.ColourSelector
import zedit2.components.Menu
import zedit2.components.PopoverDialog
import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.ToolType
import zedit2.components.editor.BrushMenuPanel
import zedit2.components.editor.TileInfoPanel
import zedit2.components.editor.code.CodeEditor
import zedit2.components.editor.code.CodeEditorFactory
import zedit2.model.*
import zedit2.model.spatial.Pos
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import zedit2.util.ZType
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame.TOP_ALIGNMENT
import javax.swing.JMenu
import javax.swing.JPanel

fun WorldEditor.updateBufferTile(tile: Tile?, frameForRelativePositioning: Component) {
        val board = this.currentBoard
        northPane.removeAll()

        if (tile != null && board != null) {
            // Configure the "Brush" section on the right hand side.
            toolInfoPane.removeAll()
            val brushInfoPane = TileInfoPanel(
                dosCanvas = canvas,
                this.worldData,
                "Brush",
                tile,
                board,
                onBlinkingImageIconAdded = {})

            val onCodeSaved = { e: ActionEvent ->
                CodeEditorFactory.create(
                    Pos.NEG_ONE,
                    true,
                    frameForRelativePositioning,
                    this,
                    IconFactory.getIcon(worldData.isSuperZZT, tile, this),
                    board,
                    tile.stats.getOrNull(0)
                )
            }

            if (tile.id == ZType.OBJECT || tile.id == ZType.SCROLL) {
                val editButton = JButton("Edit Code")

                editButton.addActionListener(onCodeSaved)
                brushInfoPane.add(editButton, BorderLayout.SOUTH)
            }
            toolInfoPane.add(brushInfoPane)

            val undoMenu = JPanel()
            undoMenu.layout = MigLayout("ins 0")
            val undoButton = JButton("↩")
            undoButton.addActionListener { e ->
                undoHandler.operationUndo()
            }
            undoMenu.add(undoButton, "gapleft 10")
            val redoButton = JButton("↪")
            redoButton.addActionListener { e ->
                undoHandler.operationRedo()
            }
            undoMenu.add(redoButton)
            northPane.add(undoMenu)

            val bgcol = canvas.colorColor(tile.col / 16)
            val fgcol = canvas.colorColor(tile.col % 16)

//            val (bgcol, fgcol) =Pair(canvas.colorColor(tile.col / 16), canvas.colorColor(tile.col % 16))


            val brushMenu = BrushMenuPanel(
                bgColor = bgcol,
                fgColor = fgcol,
                onSelectionModeSelected = { e, source ->
                    toolType = ToolType.SELECTION_TOOL
                    source.onBrushUpdated(toolType, this.selectionModeConfiguration, this.paintBucketModeConfiguration)

                },
                onEyedropperSelected = { source ->
                    toolType = ToolType.EYEDROPPER_TOOL
                    source.onBrushUpdated(toolType, this.selectionModeConfiguration, this.paintBucketModeConfiguration)

                },
                onSelectionModeConfigured = { mode : SelectionModeConfiguration, source : BrushMenuPanel ->
                    this.selectionModeConfiguration = mode
                },
                onEditModeSelected = { source ->
                    if (brushModeConfiguration == BrushModeConfiguration.DRAW) {
                        toolType = ToolType.DRAWING
                    } else {
                        toolType = ToolType.EDITING
                    }

                    source.onBrushUpdated(toolType, this.selectionModeConfiguration, this.paintBucketModeConfiguration)
                },
                onBrushConfigured = { mode : BrushModeConfiguration, source ->
                    this.brushModeConfiguration = mode
                },
                onEditBrushSelected = { e, source ->

                    operationModifyBuffer(true)
                },
                onEditColorSelected = { e, source -> operationColour() },
                onColorSwapSelected = { e, source -> operationBufferSwapColour() },
                onFloodFillSelected = { e, source ->
                    toolType = ToolType.PAINT_BUCKET
                    source.onBrushUpdated(toolType, this.selectionModeConfiguration, this.paintBucketModeConfiguration)

                },
                onFloodFillConfigured = { mode : FloodFillConfiguration, source : BrushMenuPanel ->
                    this.paintBucketModeConfiguration = mode
                },
                onTextCaretSelected = {e, source ->
                    toolType = ToolType.TEXT_ENTRY
                    source.onBrushUpdated(toolType, this.selectionModeConfiguration, this.paintBucketModeConfiguration)
                },
                initialSelectionMode = this.selectionModeConfiguration,
                initialBrushMode = this.brushModeConfiguration,
                initialPaintBucketMode = this.paintBucketModeConfiguration,
                initialToolType = toolType
            )
            northPane.add(brushMenu)
        }
        currentBufferManager.alignmentY = TOP_ALIGNMENT

}