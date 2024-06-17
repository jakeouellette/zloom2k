package zedit2.event

import zedit2.components.DosCanvas
import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.PutTypes.PUT_DEFAULT
import zedit2.components.editor.world.operationBlockEnd
import zedit2.components.editor.world.operationBlockStart
import zedit2.components.editor.world.operationGrabAndModify
import zedit2.model.Board
import zedit2.model.MouseState
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.event.*
import java.util.HashSet
import javax.swing.SwingUtilities

class CanvasMouseListener(val onFocusNeeded : () -> Unit, val editor: WorldEditor, private val dosCanvas : DosCanvas) : MouseListener,
    MouseMotionListener, MouseWheelListener {

    override fun mouseClicked(e: MouseEvent) {
        Logger.i(TAG) { "Requesting Focus." }
        onFocusNeeded()
        mouseMoveCommon(e)
    }

    override fun mousePressed(e: MouseEvent) {
        mouseMoveCommon(e, getButton(e))

        val isMovingNow = editor.moveBlockPos.isPositive
        val isSelectingNow = dosCanvas.blockStartPos.isPositive
        val isInSelectingMode = editor.editType == WorldEditor.EditType.SELECTING
        // Either, if when you click you always want a new selection,
        // or you are already selecting and selection should be refreshed
        var triggerOperationStart = false
        if ((isInSelectingMode && !(isMovingNow || isSelectingNow)) || (isSelectingNow)) {
            triggerOperationStart = true
            editor.operationBlockStart()
        }
        val isMovingNow2 = editor.moveBlockPos.isPositive
        val isSelectingNow2 = dosCanvas.blockStartPos.isPositive
        var inside = false
        if (isInSelectingMode && isMovingNow) {

            val placingBlockPos = editor.moveBlockPos + dosCanvas.placingBlockDim

            if (dosCanvas.cursorPos.inside(
                    editor.moveBlockPos.x,
                    editor.moveBlockPos.y,
                    placingBlockPos.x,
                    placingBlockPos.y
                )
            ) {
                inside = true
            }
        }
        Logger.i(TAG) {
            "MP: IMov: $isMovingNow, $isMovingNow2 Sel: $isSelectingNow, $isSelectingNow2 Mode: $isInSelectingMode" +
                    "Ins: $inside, [bs, mb, c]: [$dosCanvas.blockStartPos ${editor.moveBlockPos} $dosCanvas.cursorPos]"
        }
    }

    override fun mouseReleased(e: MouseEvent) {
        // TODO(jakeouellette): separate this event out from operationBlockEnd:
        // Make it so that this one uses whatever is the default behavior
        // of the current selection brush
        Logger.i(TAG) { "TODO: Trigger block operation $dosCanvas.blockStartPos" }
        val isMovingNow = editor.moveBlockPos.isPositive
        val isSelectingNow = dosCanvas.blockStartPos.isPositive
        val isInSelectingMode = editor.editType == WorldEditor.EditType.SELECTING

        // TODO(jakeouellette) do this a very different way
        if (editor.moveBlockPos.isPositive) {
            editor.operationGrabAndModify(
                grab = true,
                advanced = false)
        }
        if (dosCanvas.blockStartPos.isPositive) {
            editor.operationBlockEnd()
        }
        val isMovingNow2 = editor.moveBlockPos.isPositive
        val isSelectingNow2 = dosCanvas.blockStartPos.isPositive
        Logger.i(TAG) {
            "mouseReleased Event: state: ${editor.mouseState} IsMoving: $isMovingNow, $isMovingNow2 " +
                    "Selecting: $isSelectingNow, $isSelectingNow2, " +
                    "SelectingMode: $isInSelectingMode" +
                    "[bsX, bsY, mbX, mbY cX, cY]: [$dosCanvas.blockStartPos ${editor.moveBlockPos} $dosCanvas.cursorPos]"
        }
        mouseMoveCommon(e, MouseState.NONE)
        if (editor.editType == WorldEditor.EditType.SELECTING && !(editor.mouseState == MouseState.GRAB || editor.mouseState == MouseState.MOVE)) { Logger.i(TAG) { "Unexpected draw mode ${editor.editType} $editor.mouseState"} }
        if (editor.editType == WorldEditor.EditType.EDITING && editor.mouseState != MouseState.NONE) { Logger.i(TAG) { "Unexpected edit mode ${editor.editType} $editor.mouseState"} }
        if (editor.editType == WorldEditor.EditType.DRAWING && editor.mouseState != MouseState.DRAW) { Logger.i(TAG) { "Unexpected draw mode ${editor.editType} $editor.mouseState"} }
    }

    private fun getButton(e: MouseEvent): MouseState {
        return if (SwingUtilities.isLeftMouseButton(e)) MouseState.DRAW
        else if (SwingUtilities.isRightMouseButton(e)) MouseState.GRAB
        else if (SwingUtilities.isMiddleMouseButton(e)) MouseState.MOVE
        else MouseState.NONE
    }

    override fun mouseEntered(e: MouseEvent) {
        mouseMoveCommon(e)
    }

    override fun mouseExited(e: MouseEvent) {
        if (dosCanvas.mouseCursorPos.isPositive) {
            dosCanvas.mouseCursorPos = Pos.NEG_ONE
        }
    }

    override fun mouseDragged(e: MouseEvent) {
        mouseMoveCommon(e)
    }

    override fun mouseMoved(e: MouseEvent) {
        mouseMoveCommon(e)
    }

    private fun mouseMoveCommon(e: MouseEvent, state: MouseState = editor.mouseState) {
        editor.lastMouseEvent = e
        editor.mouseState = state
        val mouseCoord = Pos(e.x, e.y)
        editor.mouseCoord = mouseCoord
        editor.mousePos = dosCanvas.toChar(mouseCoord)

        // Translate into local space
        val screenLoc = editor.frame.locationOnScreen
        val mouseScreenPos = Pos(
            e.xOnScreen - screenLoc.x,
            e.yOnScreen - screenLoc.y
        )
        editor.mouseScreenPos = mouseScreenPos
        if (state != MouseState.NONE) {
            Logger.i(TAG) { "mouseMotion $state ${editor.oldMouseCoord} $mouseScreenPos $mouseCoord ${editor.mousePos} $mouseCoord" }
        }
        when (state) {
            MouseState.DRAW -> {
                mouseDraw()
                editor.oldMouseCoord = mouseCoord
            }
            MouseState.GRAB -> {
                mouseGrab()
                editor.oldMouseCoord = Pos.NEG_ONE
            }
            MouseState.MOVE -> {
                mouseMove()
                editor.oldMouseCoord = Pos.NEG_ONE
            }
            MouseState.NONE -> {
                editor.oldMouseCoord = Pos.NEG_ONE
            }
        }

        editor.undoHandler.afterUpdate()

        val pos = dosCanvas.getMousePos(e.point)
        val dim = Dim(dosCanvas.width, dosCanvas.height)
        val newMouseCursorPos = if (pos.outside(dim)) {
            Pos.NEG_ONE
        } else {
            Pos(dosCanvas.x, dosCanvas.y)
        }

        if (newMouseCursorPos != dosCanvas.mouseCursorPos) {
            dosCanvas.mouseCursorPos = newMouseCursorPos
        }
    }

    private fun mouseDraw() {
        val dirty = HashSet<Board>()
        if (!editor.oldMouseCoord.isPositive) {
            mousePlot(editor.mousePos, dirty)
        } else {
            var cxy = Pos.NEG_ONE
            val dxy = editor.mouseCoord - editor.oldMouseCoord
            val dist = dxy.distInt()
            if (dist == 0) return
            val plotSet = HashSet<Pos>()
            //int cw = canvas.getCharW(), ch = canvas.getCharH();
            for (i in 0..dist) {
                val xy = dxy * i / dist + editor.oldMouseCoord
                val ncxy = dosCanvas.toChar(xy)
                if (ncxy != cxy) {
                    cxy = ncxy
                    plotSet.add(cxy)
                }
            }
            Logger.i(TAG) {"plotset: ${plotSet.size} ${editor.mouseState}"}
            for (plot in plotSet) {
                mousePlot(plot, dirty)
            }
        }
        Logger.i(TAG) {"mouseDraw: $editor.oldMouseCoord $editor.mouseCoord"}
        for (board in dirty) {
            board.finaliseStats()
        }
        editor.afterModification()
        dosCanvas.setCursor(editor.cursorPos)
    }


    private fun mouseMove(): Boolean {
        val pos = editor.mousePos
        if (pos.inside(editor.dim)) {
            editor.cursorPos = pos
            editor.canvas.setCursor(editor.cursorPos)
            editor.afterUpdate()
            return true
        }
        return false
    }

    private fun mouseGrab() {
        if (mouseMove()) {
            editor.bufferTile = editor.getTileAt(editor.cursorPos, true)
            editor.afterUpdate()
        }
    }

    private fun mousePlot(xy: Pos, dirty: HashSet<Board>) {
        Logger.i(TAG) {"Mouse Plot2 $xy, ${dirty.size} ${editor.dim}"}
        if (xy.inside(editor.dim)) {
            editor.cursorPos = xy
            dosCanvas.setCursor(editor.cursorPos)
            Logger.i(TAG) {"Mouse Plot $xy, ${dirty.size}"}
            val board = editor.putTileDeferred(editor.cursorPos, editor.bufferTile, PUT_DEFAULT)
            if (board != null) dirty.add(board)
        }
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (e.wheelRotation < 0) {
            editor.wheelUp(e)
        } else {
            editor.wheelDown(e)
        }
    }
}