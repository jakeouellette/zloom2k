package zedit2.util

import zedit2.components.DosCanvas.Companion.CHAR_H
import zedit2.components.DosCanvas.Companion.CHAR_W
import zedit2.util.TilePainters.drawTile
import java.awt.image.BufferedImage

object ImageExtractors {
    public class ExtractionResponse( val blinkState: Boolean, val image: BufferedImage)

    /**
     * Format of pattern is:
     * @ - character
     * $ - character, but no blink
     * - blank
     * _ - col (bg)
     * # - col (fg)
     * >255 - 0xAABB (A = chr, B = col)
     *
     */
    fun extractCharImage(
        chr: Int,
        col: Int,
        zoomx: Int,
        zoomy: Int,
        blinkingTime: Boolean,
        pattern: String,
        blink: Boolean,
        palette: IntArray,
        charBuffer: BufferedImage?
    ): ExtractionResponse {
        return extractCharImageWH(chr, col, zoomx, zoomy, blinkingTime, pattern, pattern.length, 1, blink, palette, charBuffer)
    }

    fun extractCharImageWH(
        chr: Int,
        col: Int,
        zoomx: Int,
        zoomy: Int,
        blinkingTime: Boolean,
        pattern: String,
        w: Int,
        h: Int,
        blink: Boolean,
        palette: IntArray,
        charBuffer: BufferedImage?
    ): ExtractionResponse {
        val img = BufferedImage(CHAR_W * zoomx * w, CHAR_H * zoomy * h, BufferedImage.TYPE_INT_ARGB)
        val blinkSaved = blink
        var blinkState = blink
        for (i in 0 until pattern.length) {
            val c = pattern[i]
            when (c) {
                '@' -> TilePainters.drawTile(
                    img.graphics,
                    chr,
                    col,
                    i % w,
                    i / w,
                    zoomx,
                    zoomy,
                    blink=blinkSaved,
                    blinkingTime=blinkingTime,
                    palette,
                    charBuffer
                )
                '$' -> {
                    blinkState = false
                    TilePainters.drawTile(
                        img.graphics,
                        chr,
                        col,
                        i % w,
                        i / w,
                        zoomx,
                        zoomy,
                        blink=blinkSaved,
                        blinkingTime=blinkingTime,
                        palette,
                        charBuffer
                    )
                    blinkState = blinkSaved
                }

                '_' -> {
                    blinkState = false
                    TilePainters.drawTile(img.graphics, 32, col, i % w, i / w, zoomx, zoomy,  blink=blinkSaved, blinkingTime=blinkingTime, palette,
                        charBuffer)
                    blinkState = blinkSaved
                }

                '#' -> {
                    blinkState = false
                    drawTile(img.graphics, 254, col, i % w, i / w, zoomx, zoomy, blink=blinkSaved, blinkingTime=blinkingTime, palette,
                        charBuffer)
                    blinkState = blinkSaved
                }

                ' ' -> {}
                else -> {
                    val cVal = c.code
                    blinkState = false
                    drawTile(
                        img.graphics,
                        (cVal and 0xFF00) shr 8,
                        cVal and 0xFF,
                        i % w,
                        i / w,
                        zoomx,
                        zoomy,
                        blink=blinkSaved, blinkingTime=blinkingTime, palette, charBuffer)

                    blinkState = blinkSaved
                }
            }
        }
        return ExtractionResponse(blinkState, img)
    }
}