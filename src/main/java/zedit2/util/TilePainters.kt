package zedit2.util

import zedit2.components.DosCanvas.Companion.CHAR_H
import zedit2.components.DosCanvas.Companion.CHAR_W
import java.awt.Color
import java.awt.Graphics
import java.awt.image.BufferedImage

object TilePainters {

    fun drawTile(
        g: Graphics?,
        chr: Int,
        col: Int,
        x: Int,
        y: Int,
        zoomx: Int,
        zoomy: Int,
        blink: Boolean,
        blinkingTime: Boolean,
        palette: IntArray,
        charBuffer: BufferedImage?
    ) {
        var col = col
        if (blink && col >= 128) {
            // If blinking is on, for cXY and X >= 8, colours alternate between c(X-8)Y and c(X-8)(X-8)
            val bg = ((col and 0xF0) shr 4) - 8
            val fg = col and 0x0F
            col = if (!blinkingTime) {
                bg shl 4 or fg
            } else {
                bg shl 4 or bg
            }
        }
        val sx1 = chr * CHAR_W
        val sy1 = (col and 0x0F) * CHAR_H
        val sx2 = sx1 + CHAR_W
        val sy2 = sy1 + CHAR_H
        val dx1 = x * CHAR_W * zoomx
        val dy1 = y * CHAR_H * zoomy
        val dx2 = dx1 + CHAR_W * zoomx
        val dy2 = dy1 + CHAR_H * zoomy
        val bgColor = Color(palette[col and 0xF0 shr 4], true)

        g!!.drawImage(charBuffer, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, bgColor, null)
    }
}