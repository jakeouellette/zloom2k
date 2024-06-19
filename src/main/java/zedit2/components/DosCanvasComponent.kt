package zedit2.components

import zedit2.components.DosCanvas.Companion.CHAR_H
import zedit2.components.DosCanvas.Companion.CHAR_W
import zedit2.model.spatial.Dim
import zedit2.model.spatial.Pos
import zedit2.model.spatial.Rec
import zedit2.model.spatial.SpatialUtil
import java.awt.*
import javax.swing.JPanel
import kotlin.math.abs
import kotlin.math.max

class DosCanvasComponent(val state : DosCanvasState) : JPanel() {

    public override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        g.color = Color(0x7F7F7F)
        // TODO(jakeouellette): Is this supposed to be dim.w / dim.h?
        // ( I don't think so, but leaving a note )
        g.fillRect(0, 0, getWidth(), getHeight())

        if (g is Graphics2D) {
            val interpMode: Any

            // Select an appropriate rendering mode
            val xerror = abs(Math.round(state.zoomx) - state.zoomx)
            val yerror = abs(Math.round(state.zoomy) - state.zoomy)
            val error = max(xerror, yerror)
            interpMode = if (error < 0.001) {
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            } else {
                if (xerror < 0.001 || yerror < 0.001) {
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                } else {
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
                }
            }

            val rh = RenderingHints(
                RenderingHints.KEY_INTERPOLATION,
                interpMode
            )
            g.setRenderingHints(rh)
        }

        val blinkImage = if (state.blinkState) 1 else 0
        drawImg(g, state.boardBuffers[blinkImage])
        val atlas = state.atlas
        if (atlas != null && state.drawAtlasLines) {
            val lcBad = Color(1.0f, 0.4f, 0.2f)
            val lcGood = Color(0.2f, 1.0f, 0.4f, 0.5f)
            val lcNormal = Color(1.0f, 1.0f, 1.0f)
            val lcVoid = Color(1.0f, 1.0f, 1.0f, 0.2f)
            val gridDim = atlas.dim
            val grid = atlas!!.grid
            val boards = state.boards
            val boardPixelOff = state.boardDim.tile(state.zoomx, state.zoomy)
            val dirs = arrayOf(Pos.UP, Pos.DOWN, Pos.LEFT, Pos.RIGHT)
            // TODO(jakeouellette): Simplify math
            val wallsThick = arrayOf(
                intArrayOf(0, 0, boardPixelOff.w, 2),
                intArrayOf(0, boardPixelOff.h - 2, boardPixelOff.w, 2),
                intArrayOf(0, 0, 2, boardPixelOff.h),
                intArrayOf(boardPixelOff.w - 2, 0, 2, boardPixelOff.h)
            )
            val wallsThin = arrayOf(
                intArrayOf(0, 0, boardPixelOff.w, 1),
                intArrayOf(0, boardPixelOff.h - 1, boardPixelOff.w, 1),
                intArrayOf(0, 0, 1, boardPixelOff.h),
                intArrayOf(boardPixelOff.w - 1, 0, 1, boardPixelOff.h)
            )

            for (y in 0 until gridDim.h) {
                val py = tileY(y * state.boardDim.h)
                for (x in 0 until gridDim.w) {
                    val xy = Pos(x, y)
                    val px = tileX(x * state.boardDim.w)
                    val boardIdx = grid[y][x]
                    if (boardIdx != -1) {
                        val board = boards[boardIdx]

                        for (exit in 0..3) {
                            val exitd = board.getExit(exit)
                            val nxy = xy + dirs[exit]
                            var walls = wallsThick[exit]
                            if (exitd == 0) {
                                g.color = lcNormal
                            } else {
                                g.color = lcBad
                            }
                            if (nxy.inside(gridDim)) {
                                if (exitd == grid[nxy.y][nxy.x]) {
                                    g.color = lcGood
                                    walls = wallsThin[exit]
                                }
                            }
                            g.fillRect(
                                walls[0] + px, walls[1] + py,
                                walls[2], walls[3]
                            )
                        }
                    } else {
                        g.color = lcVoid
                        for (exit in 0..3) {
                            val walls = wallsThin[exit]
                            g.fillRect(
                                walls[0] + px, walls[1] + py,
                                walls[2], walls[3]
                            )
                        }
                    }

                    //g.drawLine(px, 0, px, ysize);
                    //g.drawLine(0, py, xsize, py);
                }
            }
        }


        //volatileBuffers[i] = config.createCompatibleVolatileImage(w * CHAR_W, h * CHAR_H);

        //var boardBuffer = boardBuffers[blinkState ? 1 : 0];
        if (state.drawing) {
            g.color = Color.GREEN
        } else if (state.textEntry) {
            g.color = Color.YELLOW
        } else {
            g.color = Color.LIGHT_GRAY
        }

        val tilePos = state.caretPos.tile(state.zoomx, state.zoomy) - 1
        val tileDim = Dim.ONE_BY_ONE.tile(state.zoomx, state.zoomy) + 1
        g.draw3DRect(tilePos.x, tilePos.y, tileDim.w, tileDim.h, state.blinkState)

        if (state.mouseCursorPos.isPositive && state.isFocused) {
            g.color = Color(0x7FFFFFFF, true)
            val mouseTilePos = state.mouseCursorPos.tile(state.zoomx, state.zoomy) - 1
            val mouseTileDim = Dim.ONE_BY_ONE.tile(state.zoomx, state.zoomy) + 1
            g.drawRect(
                mouseTilePos.x, mouseTilePos.y,
                mouseTileDim.w, mouseTileDim.h
            )
        }

        val indicatePos = state.indicatePos
        if (indicatePos != null) {
            g.color = Color(0x3399FF)
            for (i in indicatePos.indices) {
                val pos = indicatePos[i]
                if (!pos.isPositive) continue
                val indicateTilePos = pos.tile(state.zoomx, state.zoomy)
                val indicateTileDim = Dim.ONE_BY_ONE.tile(state.zoomx, state.zoomy) - 1
                g.drawRect(indicateTilePos.x, indicateTilePos.y, indicateTileDim.w, indicateTileDim.h)
            }
        }

        // Render selection rect
        if (state.blockStartPos.isPositive) {
            g.color = Color(0x7F3399FF, true)
            val rect = Rec.companion.from(state.blockStartPos, state.caretPos)
            val (minPos, maxPos) = rect.toPos
            val blockStartTilePos = minPos.tile(state.zoomx, state.zoomy)
            val blockStartTileDim = (maxPos - minPos + 1).dim.tile(state.zoomx, state.zoomy)
            g.fillRect(blockStartTilePos.x, blockStartTilePos.y, blockStartTileDim.w, blockStartTileDim.h)
        }

        // Render copy rect
        if (GlobalEditor.isBlockBuffer()) {
            g.color = Color(0x5FFF8133, true)
            val xy2 = (state.dim.asPos - 1).min(state.caretPos + GlobalEditor.blockBufferDim - 1)
            val newTilePos = state.caretPos.tile(state.zoomx, state.zoomy)
            val newTileDim = (xy2 - state.caretPos + 1).dim.tile(state.zoomx, state.zoomy)
            g.fillRect(newTilePos.x, newTilePos.y, newTileDim.w, newTileDim.h)
        }

        // Render move rect
        if (state.placingBlockDim.w != -1) {
            g.color = Color(0x5F33ff99, true)
            val xy2 = (state.dim.asPos - 1).min(state.caretPos + state.placingBlockDim - 1)
            val newTilePos = state.caretPos.tile(state.zoomx, state.zoomy)
            val newTileDim = (xy2 - state.caretPos + 1).dim.tile(state.zoomx, state.zoomy)
            g.fillRect(newTilePos.x, newTilePos.y, newTileDim.w, newTileDim.h)
        }
        //g.drawImage(charBuffer, 0, 0, new Color(palette[7], true), null);
        //g.drawImage(charBuffer, 0, 0, 16, 14, 8, 14, 16, 28, bgColor, null);
    }

    private fun drawImg(g: Graphics, image: Image?) {
        g.drawImage(image, 0, 0, tileX(state.dim.w), tileY(state.dim.h), 0, 0, state.dim.w * CHAR_W, state.dim.h * CHAR_H, null)
    }

    private fun tileX(x: Int) = SpatialUtil.tileX(x, state.zoomx)
    private fun tileY(y: Int) = SpatialUtil.tileY(y, state.zoomy)

    override fun getPreferredSize(): Dimension {
        var d = state.dim
        if (d.w == 0 || d.h == 0) {
            d = Dim(60, 25)
        }

        return d.tile(state.zoomx, state.zoomy).asDimension
    }

}