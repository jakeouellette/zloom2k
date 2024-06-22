package zedit2.event

import zedit2.components.DosCanvas
import zedit2.components.GlobalEditor
import zedit2.components.WorldEditor
import zedit2.components.WorldEditor.Companion.PutTypes.PUT_DEFAULT
import zedit2.components.editor.world.operationBlockEnd
import zedit2.components.editor.world.operationBlockStart
import zedit2.components.editor.world.operationGrabAndModify
import zedit2.model.Board
import zedit2.model.MouseState
import zedit2.model.SelectionModeConfiguration
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import zedit2.util.Logger
import zedit2.util.Logger.TAG
import java.awt.event.*
import java.util.HashSet
import javax.swing.SwingUtilities


class CanvasMouseListener(val onFocusNeeded : () -> Unit, val editor: WorldEditor, private val dosCanvas : DosCanvas) : MouseListener,
    MouseMotionListener, MouseWheelListener {

    /**
     * State Transition event description for mouse event.
     * Makes mouse events more verbose, but fundamentally simpler to debug.
     */
    enum class MouseEventDescription {
        DRAWING_OR_EDITING,
        SELECTING,
        COPYING_SELECTION,
        MOVING_SELECTION,
        OTHER, // secondary, middle, text entry
        HOVERING,
        TO_DRAWING_OR_EDITING,
        FROM_DRAWING_OR_EDITING,
        TO_SELECTING,
        SELECTED_TO_MOVING,
        SELECTED_TO_COPYING,
        SELECTED_TO_ACTION,
        MOVED,
        MOVING_TO_NOT,
        COPIED,
        COPYING_TO_NOT,
        TO_TEXT_EDITING,
        TO_NON_PRIMARY
    }

    /**
     * The position of the mouse coordinate the last time the mouse was moved / pressed
     * Updated only once per mouse press / move event, at the end of it.
     */
    private var lastMouseCoord: Pos? = null
    private var lastMouseState: MouseState = editor.mouseState // MouseState.RELEASED
    private var lastToolType: WorldEditor.ToolType = editor.toolType

    override fun mouseClicked(e: MouseEvent) {
    }

    override fun mousePressed(e: MouseEvent) {
        Logger.i(TAG) {"Strealing"}
        onFocusNeeded()
        editor.mouseState = getButton(e)
        mouseMoveCommon(e)
    }

    private fun getStateTransition(
        lastMouseState: MouseState,
        state: MouseState,
        lastToolType: WorldEditor.ToolType,
        toolType: WorldEditor.ToolType,
        wasCopying: Boolean,
        wasMoving: Boolean,
        wasSelecting: Boolean,
        selectedInsideBox: Boolean
    ): MouseEventDescription = when (Pair(lastMouseState, state)) {
        Pair(MouseState.PRIMARY, MouseState.PRIMARY) -> {
            when (toolType) {
                WorldEditor.ToolType.SELECTION_TOOL -> {
                    if (wasCopying) {
                        MouseEventDescription.COPYING_SELECTION
                    } else if (wasMoving) {
                        MouseEventDescription.MOVING_SELECTION
                    } else {
                        MouseEventDescription.SELECTING
                    }
                }

                WorldEditor.ToolType.EDITING,
                WorldEditor.ToolType.DRAWING -> MouseEventDescription.DRAWING_OR_EDITING

                else -> MouseEventDescription.OTHER
            }
        }

        Pair(MouseState.SECONDARY, MouseState.SECONDARY) -> MouseEventDescription.OTHER
        Pair(MouseState.MIDDLE, MouseState.MIDDLE) -> MouseEventDescription.OTHER
        Pair(MouseState.PRIMARY, MouseState.RELEASED) -> {
            when (toolType) {
                WorldEditor.ToolType.SELECTION_TOOL -> {
                    if (wasCopying) {
                        MouseEventDescription.COPIED
                    } else if (wasMoving) {
                        MouseEventDescription.MOVED
                    } else if (wasSelecting) {
                        when (editor.selectionModeConfiguration) {
                            SelectionModeConfiguration.MOVE -> MouseEventDescription.SELECTED_TO_MOVING
                            SelectionModeConfiguration.COPY -> MouseEventDescription.SELECTED_TO_COPYING
                            SelectionModeConfiguration.CLEAR,
                            SelectionModeConfiguration.MIRROR,
                            SelectionModeConfiguration.PAINT,
                            SelectionModeConfiguration.FLIP -> MouseEventDescription.SELECTED_TO_ACTION

                            SelectionModeConfiguration.COPY_REPEATED -> {
                                throw UnsupportedOperationException("Copy repeated is not currently supported")
                            }
                        }
                    } else {
                        MouseEventDescription.OTHER
                    }
                }

                WorldEditor.ToolType.DRAWING,
                WorldEditor.ToolType.EDITING -> {
                    MouseEventDescription.FROM_DRAWING_OR_EDITING
                }

                else -> {
                    MouseEventDescription.OTHER
                }
            }
        }

        Pair(MouseState.RELEASED, MouseState.PRIMARY) -> {
            when (toolType) {
                WorldEditor.ToolType.SELECTION_TOOL -> {
                    if (wasCopying && selectedInsideBox) {
                        MouseEventDescription.COPYING_SELECTION
                    } else if (wasMoving && selectedInsideBox) {
                        MouseEventDescription.MOVING_SELECTION
                    } else if (wasMoving && !selectedInsideBox) {
                        MouseEventDescription.MOVING_TO_NOT
                    } else if (wasCopying && !selectedInsideBox) {
                        MouseEventDescription.COPYING_TO_NOT
                    } else if (wasSelecting) {
                        MouseEventDescription.SELECTING
                    } else {
                        MouseEventDescription.TO_SELECTING
                    }
                }

                WorldEditor.ToolType.DRAWING,
                WorldEditor.ToolType.EDITING ->
                    MouseEventDescription.TO_DRAWING_OR_EDITING

                else -> {
                    MouseEventDescription.OTHER
                }
            }
        }

        Pair(MouseState.RELEASED, MouseState.SECONDARY),
        Pair(MouseState.RELEASED, MouseState.MIDDLE) -> {
            MouseEventDescription.TO_NON_PRIMARY
        }

        Pair(MouseState.RELEASED, MouseState.RELEASED) -> MouseEventDescription.HOVERING
        else -> {
            Logger.i(TAG) { "Unhandled State Transition. ${Pair(lastMouseState, state)}" }
            MouseEventDescription.OTHER
        }
    }


    override fun mouseReleased(e: MouseEvent) {
        editor.mouseState = MouseState.RELEASED
        mouseMoveCommon(e)
    }

    private fun getButton(e: MouseEvent): MouseState {
        return if (SwingUtilities.isLeftMouseButton(e)) MouseState.PRIMARY
        else if (SwingUtilities.isRightMouseButton(e)) MouseState.SECONDARY
        else if (SwingUtilities.isMiddleMouseButton(e)) MouseState.MIDDLE
        else MouseState.RELEASED
    }

    override fun mouseEntered(e: MouseEvent) {
        // TODO(jakeouellette): Consider bringing cursor back.
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

    private fun mouseMoveCommon(e: MouseEvent) {
        val wasMoving = editor.moveBlockPos.isPositive
        var wasSelecting = dosCanvas.blockStartPos.isPositive
        val wasCopying = GlobalEditor.isBlockBuffer()
        if (wasCopying) {
            wasSelecting = false
        }
        val state = editor.mouseState
        val mouseCoord = Pos(e.x, e.y)
        val mouseCursorPos = dosCanvas.getMousePos(e.point)
        moveMouseCursor(mouseCursorPos)
        val selectedInsideBox =
            if (wasMoving) {
                dosCanvas.mouseCursorPos.inside(editor.caretPos.x, editor.caretPos.y, editor.caretPos.x + editor.moveBlockDim.w-1, editor.caretPos.y + editor.moveBlockDim.h-1)
            } else if (wasCopying) {
                dosCanvas.mouseCursorPos.inside(editor.caretPos.x, editor.caretPos.y, editor.caretPos.x + GlobalEditor.blockBufferDim.w-1, editor.caretPos.y + GlobalEditor.blockBufferDim.h-1)
            } else {
                false
            }

        if (wasMoving && wasSelecting) {
            Logger.i(TAG) {"UH OH!${ editor.moveBlockPos} ${dosCanvas.blockStartPos} ${editor.moveBlockDim} ${dosCanvas.mouseCursorPos}"}
        }
        if (lastToolType != editor.toolType) {
            Logger.i(TAG) { "TODO: Handle tool type transition." }
        }
        val transition = getStateTransition(lastMouseState, state, lastToolType, editor.toolType, wasCopying, wasMoving, wasSelecting, selectedInsideBox)
        var logging = true
        when (transition) {
            MouseEventDescription.DRAWING_OR_EDITING,
            MouseEventDescription.TO_DRAWING_OR_EDITING -> {
                Logger.i(TAG) { "$transition drawing"}
                mouseDraw(mouseCoord)
            }
            MouseEventDescription.TO_SELECTING -> {
                Logger.i(TAG) { "$transition Beginning Selection."}
                // Note: this is basically a copy of operationBlockStart, but it uses the mouse
                updateCaretPosition()
                editor.operationBlockStart()
            }
            MouseEventDescription.SELECTED_TO_ACTION,
            MouseEventDescription.SELECTED_TO_COPYING,
            MouseEventDescription.SELECTED_TO_MOVING -> {
                editor.operationBlockEnd()
            }
            MouseEventDescription.MOVED,
            MouseEventDescription.MOVING_TO_NOT -> {
                Logger.i(TAG) { "$transition Committing Move."}
                editor.anchorDelta = null
                editor.operationGrabAndModify(
                    grab = true,
                    advanced = false)
            }
            MouseEventDescription.MOVING_SELECTION  -> {
                if (editor.anchorDelta == null) {
                    editor.anchorDelta = (dosCanvas.mouseCursorPos - editor.caretPos).dim
                    Logger.i(TAG) { "$transition Beginning Move. ${editor.anchorDelta}" }
                }
                relativeUpdateCaretPosition()
            }
            MouseEventDescription.SELECTING -> {
                Logger.v(TAG) { "$transition Unexpected State"}
                updateCaretPosition()
            }
            MouseEventDescription.FROM_DRAWING_OR_EDITING -> {
                Logger.v(TAG) { "$transition moving caret."}
                        updateCaretPosition()
                    }
            MouseEventDescription.TO_NON_PRIMARY,
            MouseEventDescription.OTHER,
            MouseEventDescription.TO_TEXT_EDITING -> {
                Logger.i(TAG) { "$transition Not handled." }
            }
            MouseEventDescription.HOVERING -> {
                // No Action Needed.
                logging = false
            }

            MouseEventDescription.COPYING_SELECTION ->
            {
                if (editor.anchorDelta == null) {
                    editor.anchorDelta = (dosCanvas.mouseCursorPos - editor.caretPos).dim
                    Logger.i(TAG) { "$transition Beginning copy. ${editor.anchorDelta}" }
                }
                relativeUpdateCaretPosition()
            }
            MouseEventDescription.COPIED,
            MouseEventDescription.COPYING_TO_NOT -> {
                editor.anchorDelta = null
                editor.operationGrabAndModify(
                    grab = true,
                    advanced = false)
            }
        }
        if (logging){
            Logger.i(TAG) { "$transition ran. $wasMoving $wasSelecting $wasCopying $selectedInsideBox ${editor.caretPos} ${GlobalEditor.blockBufferDim} ${editor.selectionBlockPos} ${editor.selectionBlockPos2} ${editor.anchorDelta} ${editor.moveBlockPos} ${dosCanvas.blockStartPos} ${editor.moveBlockDim} ${dosCanvas.mouseCursorPos}"}
        }
        editor.undoHandler.afterUpdate()

        lastMouseCoord = mouseCoord
        lastMouseState = editor.mouseState
        lastToolType = editor.toolType
    }

    private fun moveMouseCursor(pos : Pos) {
        val dim = dosCanvas.viewDim
        val newMouseCursorPos = if (pos.outside(dim)) {
            Pos.NEG_ONE
        } else {
            pos
        }

        if (newMouseCursorPos != dosCanvas.mouseCursorPos) {
            dosCanvas.mouseCursorPos = newMouseCursorPos
        }
    }

    private fun mouseDraw(mouseCoord: Pos) {
        val dirty = HashSet<Board>()
        val lastMouseCoord = lastMouseCoord
        if (lastMouseCoord == null || !lastMouseCoord.isPositive || lastMouseCoord == mouseCoord) {
            mousePlot(dosCanvas.mouseCursorPos, dirty)
        } else {
            var cxy = Pos.NEG_ONE
            val dxy = mouseCoord - lastMouseCoord
            val dist = dxy.distInt()
            if (dist == 0) return
            val plotSet = HashSet<Pos>()
            //int cw = canvas.getCharW(), ch = canvas.getCharH();
            for (i in 0..dist) {
                val xy = dxy * i / dist + lastMouseCoord
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
        Logger.i(TAG) {"mouseDraw: $lastMouseCoord $mouseCoord"}
        for (board in dirty) {
            board.finaliseStats()
        }
        editor.afterModification()
        dosCanvas.setCaret(editor.caretPos)
    }

    private fun relativeUpdateCaretPosition(): Boolean {
//        Logger.i(TAG) {"Relative update ${editor.anchorDelta}"}
        val delta = (editor.anchorDelta ?: Dim.EMPTY)
        val pos = dosCanvas.mouseCursorPos
        if (pos.inside(editor.dim)) {
            editor.caretPos = pos - delta
            editor.canvas.setCaret(editor.caretPos)
            editor.afterUpdate()
            return true
        } else {
            Logger.i(TAG) { "Pos outside editor, $pos, ${editor.dim}"}
        }
        return false
    }

    private fun updateCaretPosition(): Boolean {
        val pos = dosCanvas.mouseCursorPos
        if (pos.inside(editor.dim)) {
            editor.caretPos = pos
            editor.canvas.setCaret(editor.caretPos)
            editor.afterUpdate()
            return true
        } else {
            Logger.i(TAG) { "Pos outside editor, $pos, ${editor.dim}"}
        }
        return false
    }

    private fun mouseGrab() {
        if (relativeUpdateCaretPosition()) {
            editor.bufferTile = editor.getTileAt(editor.caretPos, true)
            editor.afterUpdate()
        }
    }

    private fun mousePlot(xy: Pos, dirty: HashSet<Board>) {
        val earlyLog = "Mouse Plot $xy, ${dirty.size} ${editor.dim}"
        Logger.i(TAG) { "${earlyLog}"}
        if (xy.inside(editor.dim)) {
            editor.caretPos = xy
            dosCanvas.setCaret(editor.caretPos)
            Logger.i(TAG) {"$ | After: $xy, ${dirty.size}"}
            val board = editor.putTileDeferred(editor.caretPos, editor.bufferTile, PUT_DEFAULT)
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