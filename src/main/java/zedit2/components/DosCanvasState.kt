package zedit2.components

import zedit2.model.Atlas
import zedit2.model.Board
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import java.awt.image.BufferedImage

interface DosCanvasState {
    val mouseCursorPos: Pos
    val blockStartPos: Pos
    val indicatePos: Array<Pos>?
    val placingBlockDim: Dim
    val dim: Dim
    val isFocused: Boolean
    val cursorPos: Pos
    val textEntry: Boolean
    val drawing: Boolean
    val boardDim: Dim
    val drawAtlasLines: Boolean
    val atlas: Atlas?
    val boardBuffers: Array<BufferedImage?>
    val blinkState: Boolean
    val zoomy: Double
    val zoomx: Double
    val boards : ArrayList<Board>
}